package com.lanraragi.reader.domain.metadata

import java.text.Normalizer

enum class TagSource { LANRARAGI, EHENTAI, NHENTAI, USER, UNKNOWN }

data class CanonicalTag(
    val namespace: String?,
    val key: String,
    val raw: String,
    val displayNameZh: String? = null,
    val source: TagSource = TagSource.UNKNOWN,
    val confidence: Float? = null,
) {
    val full: String = namespace?.let { "$it:$key" } ?: key

    init {
        require(key.isNotBlank()) { "tag key must not be blank" }
        require(confidence == null || confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }

    companion object {
        fun parse(
            raw: String,
            source: TagSource = TagSource.UNKNOWN,
            confidence: Float? = null,
        ): CanonicalTag {
            val normalizedRaw = normalize(raw)
            val colon = normalizedRaw.indexOf(':')
            val namespace = if (colon > 0) normalize(normalizedRaw.substring(0, colon)).lowercase() else null
            val key = normalize(if (colon > 0) normalizedRaw.substring(colon + 1) else normalizedRaw).lowercase()
            return CanonicalTag(namespace, key, raw, source = source, confidence = confidence)
        }

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
