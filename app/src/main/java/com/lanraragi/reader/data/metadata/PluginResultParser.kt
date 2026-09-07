package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.data.api.ApiClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class PluginMetadataResult(
    val newTags: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val error: String? = null,
    val raw: JsonObject,
)

object PluginResultParser {
    fun parseSyncResponse(body: String): PluginMetadataResult {
        val root = parseObject(body)
        val data = root["data"].asObjectOrNull() ?: JsonObject(emptyMap())
        return parseMetadata(data, envelopeError = root.string("error"))
    }

    fun parseJobDetail(body: String): PluginMetadataResult {
        val root = parseObject(body)
        val result = root["result"].asObjectOrNull()
            ?: root["job"].asObjectOrNull()?.get("result").asObjectOrNull()
            ?: JsonObject(emptyMap())
        val envelopeError = root.string("error")
            ?: root["job"].asObjectOrNull()?.string("error")
        return parseMetadata(result, envelopeError)
    }

    private fun parseMetadata(value: JsonObject, envelopeError: String?): PluginMetadataResult = PluginMetadataResult(
        newTags = value.string("new_tags") ?: value.string("tags"),
        title = value.string("title"),
        summary = value.string("summary"),
        error = value.string("error") ?: envelopeError,
        raw = value,
    )

    private fun parseObject(body: String): JsonObject =
        (ApiClient.json.parseToJsonElement(body) as? JsonObject)
            ?: throw IllegalArgumentException("plugin response must be a JSON object")

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? {
        val value = this[key]
        if (value == null || value === JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
