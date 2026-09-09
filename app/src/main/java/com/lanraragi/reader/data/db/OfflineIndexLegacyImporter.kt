package com.lanraragi.reader.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Pure decoder for the pre-Room filesDir/offline/index.json file.
 *
 * The legacy index may contain a complete [Archive] metadata snapshot. This
 * importer deliberately reads only identity, page-count, and access-time
 * projections. The caller supplies the already-observed original.archive
 * facts, so this class never reads files or performs Room/network I/O.
 */
class OfflineIndexLegacyImporter(
    private val json: Json = defaultJson,
) {

    /** Facts collected by the migration coordinator for offline/<arcid>/original.archive. */
    data class ArtifactFacts(
        val arcid: String,
        val filePath: String,
        val exists: Boolean,
        val byteSize: Long,
        val lastModified: Long = 0L,
        val isOriginalArchive: Boolean = true,
    )

    data class DecodeResult(
        val entries: List<SavedArtifactEntity>,
        val validDocument: Boolean,
        val skippedEntries: Int = 0,
        val error: String? = null,
    )

    fun decode(indexJson: String, artifactFacts: Iterable<ArtifactFacts>): List<SavedArtifactEntity> =
        decodeResult(indexJson, artifactFacts).entries

    /**
     * Decode and merge legacy cache rows. Valid empty documents are accepted;
     * malformed and unsupported documents are distinguishable through [error].
     */
    fun decodeResult(indexJson: String, artifactFacts: Iterable<ArtifactFacts>): DecodeResult {
        val root = runCatching { json.parseToJsonElement(indexJson) }.getOrElse {
            return DecodeResult(emptyList(), validDocument = false, error = "malformed_json")
        }
        val values = itemValues(root) ?: return DecodeResult(
            emptyList(),
            validDocument = false,
            error = "unsupported_format",
        )
        val factsByArchive = validFacts(artifactFacts)
        val merged = linkedMapOf<String, SavedArtifactEntity>()
        var skipped = 0

        values.forEach { element ->
            val raw = element as? JsonObject
            val legacy = raw?.toLegacyEntryOrNull()
            if (legacy == null || legacy.isLocal || legacy.arcid.isLocalArchiveId()) {
                skipped++
                return@forEach
            }
            val normalizedId = legacy.arcid.normalizedArchiveId()
            val facts = factsByArchive[normalizedId]
            if (facts == null) {
                skipped++
                return@forEach
            }
            val incoming = SavedArtifactEntity(
                artifactId = "offline:$normalizedId",
                archiveId = facts.arcid,
                filePath = facts.filePath,
                revision = "legacy-offline:${facts.byteSize}:${facts.lastModified}",
                byteSize = facts.byteSize,
                pageCount = legacy.pageCount,
                pinned = false,
                lastAccessAt = legacy.lastAccess,
            )
            merged[incoming.artifactId] = merge(merged[incoming.artifactId], incoming)
        }

        return DecodeResult(
            entries = merged.values.sortedBy { it.artifactId },
            validDocument = true,
            skippedEntries = skipped,
        )
    }

    private fun validFacts(facts: Iterable<ArtifactFacts>): Map<String, CanonicalFacts> {
        val result = linkedMapOf<String, CanonicalFacts>()
        facts.forEach { fact ->
            val arcid = fact.arcid.trim()
            val filePath = fact.filePath.trim()
            if (arcid.isEmpty() || arcid.isLocalArchiveId() || filePath.isEmpty() ||
                !fact.isOriginalArchive || !fact.exists || fact.byteSize <= 0L
            ) return@forEach

            val candidate = CanonicalFacts(
                arcid = arcid,
                filePath = filePath,
                byteSize = fact.byteSize,
                lastModified = fact.lastModified.coerceAtLeast(0L),
            )
            val key = arcid.normalizedArchiveId()
            val previous = result[key]
            if (previous == null || candidate.isPreferredTo(previous)) result[key] = candidate
        }
        return result
    }

    private fun itemValues(root: JsonElement): JsonArray? = when (root) {
        is JsonArray -> root
        is JsonObject -> sequenceOf("items", "archives", "entries")
            .mapNotNull { root[it] as? JsonArray }
            .firstOrNull()
            ?: if (root.containsKey("arcid") || root.containsKey("archiveId") || root.containsKey("id")) {
                JsonArray(listOf(root))
            } else {
                null
            }
        else -> null
    }

    private fun JsonObject.toLegacyEntryOrNull(): LegacyEntry? {
        val arcid = (string("arcid") ?: string("archiveId") ?: string("id"))?.trim().orEmpty()
        if (arcid.isEmpty()) return null
        val metadata = this["metadata"] as? JsonObject
        val pageCount = (int("pageCount") ?: int("pagecount")
            ?: metadata?.int("pagecount") ?: metadata?.int("pageCount") ?: 0).coerceAtLeast(0)
        val lastAccess = (long("lastAccess") ?: long("lastAccessAt") ?: long("timestamp") ?: 0L)
            .coerceAtLeast(0L)
        return LegacyEntry(
            arcid = arcid,
            isLocal = boolean("isLocal") ?: false,
            pageCount = pageCount,
            lastAccess = lastAccess,
        )
    }

    private fun merge(previous: SavedArtifactEntity?, incoming: SavedArtifactEntity): SavedArtifactEntity {
        if (previous == null) return incoming
        val canonical = minOf(previous, incoming, compareBy<SavedArtifactEntity> { it.filePath }
            .thenBy { it.archiveId })
        return canonical.copy(
            pageCount = maxOf(previous.pageCount, incoming.pageCount),
            lastAccessAt = maxOf(previous.lastAccessAt, incoming.lastAccessAt),
        )
    }

    private fun String.normalizedArchiveId(): String = trim().lowercase()

    private fun String.isLocalArchiveId(): Boolean = trim().startsWith("local_", ignoreCase = true)

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private data class LegacyEntry(
        val arcid: String,
        val isLocal: Boolean,
        val pageCount: Int,
        val lastAccess: Long,
    )

    private data class CanonicalFacts(
        val arcid: String,
        val filePath: String,
        val byteSize: Long,
        val lastModified: Long,
    ) {
        fun isPreferredTo(other: CanonicalFacts): Boolean = compareValuesBy(
            this,
            other,
            CanonicalFacts::lastModified,
            CanonicalFacts::byteSize,
        ) > 0 || (lastModified == other.lastModified && byteSize == other.byteSize && filePath < other.filePath)
    }

    private companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
