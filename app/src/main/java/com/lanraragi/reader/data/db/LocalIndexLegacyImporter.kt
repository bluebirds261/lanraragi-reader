package com.lanraragi.reader.data.db

import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Pure decoder for the pre-Room filesDir/local/index.json file.
 *
 * The old LocalScanManager persisted `{rootSignatures, archives}` where each
 * archive is the regular [com.lanraragi.reader.data.model.Archive] shape.
 * Decoding is deliberately independent of Context, files and Room IO so the
 * migration coordinator can decide when and how to persist the rows.
 */
class LocalIndexLegacyImporter(
    private val json: Json = defaultJson,
) {

    data class DecodeResult(
        val entries: List<LocalArchiveEntity>,
        val validDocument: Boolean,
        val skippedEntries: Int = 0,
        val error: String? = null,
    )

    fun decode(indexJson: String): List<LocalArchiveEntity> = decodeResult(indexJson).entries

    /**
     * Decode a legacy index without throwing on malformed or unsupported data.
     * A syntactically valid document with no usable archive rows is still
     * reported as valid; callers may use [skippedEntries] for diagnostics.
     */
    fun decodeResult(indexJson: String): DecodeResult {
        val root = runCatching { json.parseToJsonElement(indexJson) }.getOrElse {
            return DecodeResult(emptyList(), validDocument = false, error = "malformed_json")
        }
        val values = archiveValues(root) ?: return DecodeResult(
            emptyList(),
            validDocument = false,
            error = "unsupported_format",
        )

        val entries = linkedMapOf<String, LocalArchiveEntity>()
        var skipped = 0
        values.forEach { element ->
            val archive = element as? JsonObject
            val row = archive?.toEntityOrNull()
            if (row == null) {
                skipped++
            } else {
                // Keep the first occurrence deterministic when malformed
                // indexes contain duplicate archive records.
                entries.putIfAbsent(row.sourceKey, row)
            }
        }
        return DecodeResult(
            entries = entries.values.toList(),
            validDocument = true,
            skippedEntries = skipped,
        )
    }

    private fun archiveValues(root: JsonElement): JsonArray? = when (root) {
        is JsonArray -> root
        is JsonObject -> sequenceOf("archives", "items", "entries")
            .mapNotNull { root[it] as? JsonArray }
            .firstOrNull()
            ?: if (root.hasArchiveIdentity()) JsonArray(listOf(root)) else null
        else -> null
    }

    private fun JsonObject.toEntityOrNull(): LocalArchiveEntity? {
        // LocalScanManager stores the SAF/file URI in Archive.summary. Accept
        // aliases as a compatibility courtesy for hand-written old indexes.
        val uri = string("summary")
            ?: string("localUri")
            ?: string("uri")
            ?: string("path")
        if (uri.isNullOrBlank()) return null
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return null

        val title = string("title") ?: string("name") ?: ""
        val pageCount = int("pagecount") ?: int("pageCount") ?: 0
        val fingerprint = string("fingerprint") ?: ""
        val coverEntry = string("coverEntry") ?: string("cover") ?: string("coverUri")
        val availability = string("availability")?.takeIf { it.isNotBlank() } ?: "AVAILABLE"
        val lastVerifiedAt = long("lastVerifiedAt") ?: long("lastVerified") ?: 0L
        return runCatching {
            LocalArchiveEntity(
                sourceKey = ArchiveIdentity.LocalSaf(normalizedUri).sourceKey,
                uri = normalizedUri,
                title = title,
                fingerprint = fingerprint,
                pageCount = pageCount.coerceAtLeast(0),
                coverEntry = coverEntry,
                availability = availability,
                lastVerifiedAt = lastVerifiedAt.coerceAtLeast(0L),
            )
        }.getOrNull()
    }

    private fun JsonObject.hasArchiveIdentity(): Boolean =
        containsKey("summary") || containsKey("localUri") || containsKey("uri") || containsKey("path")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
