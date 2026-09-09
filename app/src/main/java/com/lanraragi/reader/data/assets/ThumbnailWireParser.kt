package com.lanraragi.reader.data.assets

import com.lanraragi.reader.data.api.ApiClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Parses the small, version-tolerant JSON envelopes used by thumbnail requests. */
object ThumbnailWireParser {
    /** Returns a non-blank queued job id, or null for a malformed 202 response. */
    fun queuedJobId(body: String): String? = runCatching {
        val root = ApiClient.json.parseToJsonElement(body)
        val objectValue = root as? JsonObject ?: return@runCatching null
        listOf("job", "jobid", "job_id", "id")
            .asSequence()
            .mapNotNull { key -> objectValue[key]?.asScalarText() }
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
    }.getOrNull()

    /** Converts a successful minion response into the repository's polling state. */
    fun jobState(body: String): ThumbnailJobState = runCatching {
        val root = ApiClient.json.parseToJsonElement(body)
        val objectValue = root as? JsonObject
            ?: return@runCatching ThumbnailJobState.Failed(message = "Malformed thumbnail job response")
        val state = objectValue["state"]?.asScalarText()?.trim()?.lowercase()
        val error = objectValue["error"]?.asScalarText()?.trim().orEmpty()
        val notes = objectValue["notes"]?.asScalarText()?.trim().orEmpty()
        if (error.isNotEmpty()) {
            return@runCatching ThumbnailJobState.Failed(message = error)
        }
        when (state) {
            "finished", "complete", "completed", "done", "success", "succeeded", "successful" ->
                ThumbnailJobState.Finished
            "inactive", "active", "running", "pending", "queued", "processing", "started", "waiting",
            "in_progress", "in-progress" -> ThumbnailJobState.Active
            "failed", "failure", "error", "dead", "cancelled", "canceled" ->
                ThumbnailJobState.Failed(message = notes.ifBlank { "Thumbnail job failed" })
            else -> ThumbnailJobState.Failed(message = "Unknown thumbnail job state")
        }
    }.getOrElse { ThumbnailJobState.Failed(message = "Malformed thumbnail job response") }

    private fun JsonElement.asScalarText(): String? =
        (this as? JsonPrimitive)?.let { primitive ->
            if (primitive.toString() == "null") null else primitive.content
        }
}
