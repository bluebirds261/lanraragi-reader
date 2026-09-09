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
 * Pure decoder/mapping for the pre-Room filesDir/history.json format.
 *
 * The optional local index is intentionally supplied as text (or through an
 * injected reader by callers), keeping this class independent of Android
 * Context, files and SAF APIs.
 */
class HistoryLegacyImporter(
    private val json: Json = defaultJson,
) {

    data class DecodeResult(
        val entries: List<ReadingHistoryEntity>,
        val validDocument: Boolean,
    )

    /** Decode and merge legacy entries without touching a database. */
    fun decode(historyJson: String, localIndexJson: String? = null): List<ReadingHistoryEntity> =
        decodeResult(historyJson, localIndexJson).entries

    /** Distinguishes a valid empty history document from malformed JSON. */
    fun decodeResult(historyJson: String, localIndexJson: String? = null): DecodeResult {
        val root = runCatching { json.parseToJsonElement(historyJson) }.getOrNull()
            ?: return DecodeResult(emptyList(), validDocument = false)
        val values = historyValues(root)
        if (values == null) return DecodeResult(emptyList(), validDocument = false)
        val localUris = parseLocalUris(localIndexJson)
        val merged = linkedMapOf<String, ReadingHistoryEntity>()
        parseHistoryEntries(values).forEach { raw ->
            val arcid = raw.arcid.trim()
            if (arcid.isEmpty()) return@forEach
            val localUri = if (arcid.startsWith("local_", ignoreCase = true)) {
                localUris[arcid] ?: localUris.entries.firstOrNull { it.key.equals(arcid, true) }?.value
            } else null
            val sourceKey = when {
                localUri != null -> ArchiveIdentity.LocalSaf(localUri).sourceKey
                arcid.startsWith("local_", ignoreCase = true) -> "legacy-local:$arcid"
                else -> ArchiveIdentity.Remote(arcid).sourceKey
            }
            val timestamp = raw.timestamp
            val title = raw.title.ifBlank { arcid }
            val candidate = ReadingHistoryEntity(
                sourceKey = sourceKey,
                // Keep the legacy ID as a display/navigation alias. Identity and
                // capability decisions continue to use sourceKey, so local rows
                // never become remote merely because this compatibility field is set.
                archiveId = arcid,
                title = title,
                page = raw.page.coerceAtLeast(0),
                pageCount = raw.pageCount.coerceAtLeast(0),
                firstReadAt = timestamp,
                lastReadAt = timestamp,
            )
            merged[sourceKey] = merge(merged[sourceKey], candidate)
        }
        return DecodeResult(merged.values.sortedByDescending { it.lastReadAt }, validDocument = true)
    }

    /** Convenience bridge for coordinators; callers remain responsible for transaction scope. */
    suspend fun import(
        database: ReaderDatabase,
        historyJson: String,
        localIndexJson: String? = null,
    ) {
        val entries = decode(historyJson, localIndexJson)
        if (entries.isNotEmpty()) database.readingHistoryDao().upsertAll(entries)
    }

    private fun merge(previous: ReadingHistoryEntity?, incoming: ReadingHistoryEntity): ReadingHistoryEntity {
        if (previous == null) return incoming
        val latest = when {
            incoming.lastReadAt >= previous.lastReadAt -> incoming
            else -> previous
        }
        return latest.copy(
            sourceKey = previous.sourceKey,
            archiveId = previous.archiveId ?: incoming.archiveId,
            firstReadAt = minOf(previous.firstReadAt, incoming.firstReadAt),
            lastReadAt = maxOf(previous.lastReadAt, incoming.lastReadAt),
        )
    }

    private fun parseHistoryEntries(values: JsonArray): List<LegacyEntry> {
        return values.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val arcid = obj.string("arcid") ?: obj.string("archiveId") ?: obj.string("id") ?: return@mapNotNull null
            LegacyEntry(
                arcid = arcid,
                title = obj.string("title") ?: obj.string("name").orEmpty(),
                timestamp = obj.long("timestamp") ?: obj.long("lastReadAt") ?: obj.long("time") ?: 0L,
                page = obj.int("page") ?: obj.int("progress") ?: 0,
                pageCount = obj.int("pageCount") ?: obj.int("pagecount") ?: 0,
            )
        }
    }

    private fun historyValues(root: JsonElement): JsonArray? = when (root) {
        is JsonArray -> root
        is JsonObject -> sequenceOf("entries", "history", "items", "records")
            .mapNotNull { root[it] as? JsonArray }
            .firstOrNull()
            ?: if (root.containsKey("arcid") || root.containsKey("archiveId") || root.containsKey("id")) {
                JsonArray(listOf(root))
            } else {
                null
            }
        else -> null
    }

    private fun parseLocalUris(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        val arrays = listOf("archives", "items", "entries").mapNotNull { root[it] as? JsonArray }
        arrays.flatten().forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = obj.string("arcid") ?: obj.string("archiveId") ?: obj.string("id") ?: return@forEach
            val uri = obj.string("localUri") ?: obj.string("uri") ?: obj.string("path") ?: obj.string("summary")
            if (!uri.isNullOrBlank() && (uri.startsWith("content:") || uri.startsWith("file:") || uri.startsWith("/"))) {
                result[id] = uri
            }
        }
        return result
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private data class LegacyEntry(
        val arcid: String,
        val title: String,
        val timestamp: Long,
        val page: Int,
        val pageCount: Int,
    )

    private companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
