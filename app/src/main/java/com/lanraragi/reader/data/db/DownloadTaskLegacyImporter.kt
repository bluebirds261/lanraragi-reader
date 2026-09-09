package com.lanraragi.reader.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToLong

/**
 * Pure decoder for the pre-Room filesDir/download_manager/tasks.json format.
 *
 * Legacy tasks only persisted UI metadata. Their work closures cannot survive a
 * process restart, so in-flight and paused records are deliberately imported as
 * terminal failures instead of being resumed with an unknown operation.
 */
class DownloadTaskLegacyImporter(
    private val json: Json = defaultJson,
) {

    data class DecodeResult(
        val entries: List<DownloadTaskEntity>,
        val validDocument: Boolean,
        val skippedEntries: Int = 0,
        val error: String? = null,
    )

    fun decode(tasksJson: String, importedAt: Long): List<DownloadTaskEntity> =
        decodeResult(tasksJson, importedAt).entries

    /**
     * Parses only supported task documents and never reads files, Room, or the
     * network. [importedAt] is the sole timestamp source, keeping retries and
     * migration tests deterministic.
     */
    fun decodeResult(tasksJson: String, importedAt: Long): DecodeResult {
        val root = runCatching { json.parseToJsonElement(tasksJson) }.getOrElse {
            return DecodeResult(emptyList(), validDocument = false, error = "malformed_json")
        }
        // `isLenient` accepts a bare token such as `not-json` as a primitive.
        // Classify it from the original text because its `JsonPrimitive` flags
        // differ across kotlinx.serialization versions.
        if (root is JsonPrimitive && !isValidTopLevelScalar(tasksJson.trim())) {
            return DecodeResult(emptyList(), validDocument = false, error = "malformed_json")
        }
        val values = taskValues(root) ?: return DecodeResult(
            emptyList(),
            validDocument = false,
            error = "unsupported_format",
        )

        val rows = linkedMapOf<String, DownloadTaskEntity>()
        var skipped = 0
        values.forEach { value ->
            val task = value as? JsonObject
            val row = task?.toEntityOrNull(importedAt.coerceAtLeast(0L))
            if (row == null) {
                skipped++
            } else {
                // First writer wins, so duplicate documents cannot make output
                // ordering or persisted task state depend on map iteration.
                rows.putIfAbsent(row.taskId, row)
            }
        }
        return DecodeResult(
            entries = rows.values.toList(),
            validDocument = true,
            skippedEntries = skipped,
        )
    }

    private fun taskValues(root: JsonElement): JsonArray? = when (root) {
        is JsonArray -> root
        is JsonObject -> sequenceOf("tasks", "downloadTasks", "download_tasks", "items", "entries", "data")
            .mapNotNull { root[it] as? JsonArray }
            .firstOrNull()
            ?: if (root.hasTaskIdentity()) JsonArray(listOf(root)) else null
        else -> null
    }

    private fun JsonObject.toEntityOrNull(importedAt: Long): DownloadTaskEntity? {
        val taskId = string("id") ?: string("taskId") ?: string("task_id")
        if (taskId.isNullOrBlank()) return null
        val type = normalizeType(string("type") ?: string("taskType") ?: string("task_type"))
            ?: return null
        val arcid = (string("arcid") ?: string("archiveId") ?: string("archive_id")).orEmpty().trim()
        val title = (string("title") ?: string("name")).orEmpty().trim()
        val rawState = (string("state") ?: string("status")).orEmpty()
        val state = normalizeState(rawState)
        val originalError = safeError(string("error") ?: string("message"))
        val error = when (state) {
            "FAILED" -> when {
                isNonRecoverableLegacyState(rawState) -> LEGACY_SPEC_UNAVAILABLE
                else -> originalError ?: LEGACY_TASK_FAILED
            }
            else -> null
        }
        val byteProgress = byteProgress()
        val normalizedProgress = normalizedProgress()
        val retryCount = (long("retryCount") ?: long("retry_count") ?: long("retries") ?: 0L)
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val priority = (long("priority") ?: 0L)
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val payload = buildJsonObject {
            put("type", type)
            put("arcid", arcid)
            put("title", title)
            putJsonObject("legacy") {
                put("taskId", taskId.trim())
                put("state", rawState.trim().uppercase())
                normalizedProgress?.let { put("progress", it) }
                byteProgress.totalBytes?.let { put("totalBytes", it) }
                if (byteProgress.completedBytes > 0L) put("completedBytes", byteProgress.completedBytes)
            }
        }
        return DownloadTaskEntity(
            taskId = taskId.trim(),
            type = type,
            archiveId = arcid.ifBlank { null },
            payloadJson = json.encodeToString(JsonObject.serializer(), payload),
            state = state,
            completedBytes = byteProgress.completedBytes,
            totalBytes = byteProgress.totalBytes,
            retryCount = retryCount,
            priority = priority,
            error = error,
            createdAt = importedAt,
            updatedAt = importedAt,
        )
    }

    private fun JsonObject.byteProgress(): ByteProgress {
        val completed = (long("completedBytes")
            ?: long("completed_bytes")
            ?: long("bytesDownloaded")
            ?: long("downloadedBytes")
            ?: 0L).coerceAtLeast(0L)
        val requestedTotal = long("totalBytes")
            ?: long("total_bytes")
            ?: long("expectedBytes")
            ?: long("sizeBytes")
        val total = requestedTotal?.takeIf { it > 0L }
        return ByteProgress(
            completedBytes = total?.let { completed.coerceAtMost(it) } ?: completed,
            totalBytes = total,
        )
    }

    /** The original `progress: Float` has no byte unit, so it is payload-only. */
    private fun JsonObject.normalizedProgress(): Double? {
        val raw = double("progress") ?: double("percent") ?: return null
        if (!raw.isFinite()) return null
        return when {
            raw <= 1.0 -> raw.coerceIn(0.0, 1.0)
            else -> (raw / 100.0).coerceIn(0.0, 1.0)
        }.let { (it * 10_000.0).roundToLong() / 10_000.0 }
    }

    private fun normalizeType(raw: String?): String? = when (
        raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_')
    ) {
        "OFFLINE_CACHE", "OFFLINE", "CACHE", "OFFLINECACHE" -> "OFFLINE_CACHE"
        "ARCHIVE_FILE", "ARCHIVE", "FILE", "ARCHIVEFILE" -> "ARCHIVE_FILE"
        "PAGE", "SINGLE_PAGE" -> "PAGE"
        else -> null
    }

    private fun normalizeState(raw: String): String = when (
        raw.trim().uppercase().replace('-', '_').replace(' ', '_')
    ) {
        "DONE", "COMPLETE", "COMPLETED", "SUCCESS" -> "DONE"
        "FAILED", "FAIL", "ERROR" -> "FAILED"
        // A valid old record without a state was a WAITING task by default.
        "", "WAITING", "RUNNING", "PAUSED", "PENDING", "QUEUED" -> "FAILED"
        else -> "FAILED"
    }

    private fun isNonRecoverableLegacyState(raw: String): Boolean = raw.trim().uppercase() in setOf(
        "", "WAITING", "RUNNING", "PAUSED", "PENDING", "QUEUED",
    )

    private fun safeError(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_ERROR_LENGTH) ?: return null
        return if (secretMarker.containsMatchIn(value)) LEGACY_TASK_FAILED else value
    }

    private fun JsonObject.hasTaskIdentity(): Boolean =
        containsKey("id") || containsKey("taskId") || containsKey("task_id")

    private fun isValidTopLevelScalar(text: String): Boolean =
        text.startsWith('"') || text == "null" || text == "true" || text == "false" ||
            JSON_NUMBER.matches(text)

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private data class ByteProgress(
        val completedBytes: Long,
        val totalBytes: Long?,
    )

    companion object {
        const val LEGACY_SPEC_UNAVAILABLE = "LEGACY_SPEC_UNAVAILABLE"
        const val LEGACY_TASK_FAILED = "LEGACY_TASK_FAILED"

        private const val MAX_ERROR_LENGTH = 1_024
        private val secretMarker = Regex("(?i)\\b(api[_ -]?key|authorization|bearer|token)\\b")
        private val JSON_NUMBER = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
