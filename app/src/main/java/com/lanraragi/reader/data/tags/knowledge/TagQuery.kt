package com.lanraragi.reader.data.tags.knowledge

import com.lanraragi.reader.data.tags.TagNamespaceRegistry

enum class TagQueryModifier { INCLUDE, EXCLUDE, FUZZY }

data class TagQueryToken(
    val text: String,
    val namespace: String? = null,
    val modifier: TagQueryModifier = TagQueryModifier.INCLUDE,
    val quoted: Boolean = false,
    val start: Int = 0,
    val end: Int = 0,
)

data class TagQuery(val original: String, val tokens: List<TagQueryToken>) {
    val isEmpty: Boolean get() = tokens.none { it.text.isNotBlank() }
    val positiveTokens: List<TagQueryToken> get() = tokens.filter { it.modifier != TagQueryModifier.EXCLUDE }
}

/** English and Chinese token parser. Namespace aliases include registry labels (作者, 角色, ...). */
object TagQueryParser {
    fun parse(input: String): TagQuery {
        val result = mutableListOf<TagQueryToken>()
        var i = 0
        while (i < input.length) {
            while (i < input.length && input[i].isWhitespace()) i++
            if (i >= input.length) break
            val start = i
            var modifier = TagQueryModifier.INCLUDE
            if (input[i] == '-' || input[i] == '~') {
                modifier = if (input[i] == '-') TagQueryModifier.EXCLUDE else TagQueryModifier.FUZZY
                i++
            }
            val contentStart = i
            var inQuotes = false
            while (i < input.length) {
                when {
                    input[i] == '"' -> inQuotes = !inQuotes
                    input[i].isWhitespace() && !inQuotes -> break
                }
                i++
            }
            val raw = normalizeText(input.substring(contentStart, i))
            if (raw.isBlank()) continue
            val colon = raw.indexOf(':')
            val rawNamespace = colon.takeIf { it > 0 }?.let { raw.substring(0, it) }
            val rawText = if (colon > 0) raw.substring(colon + 1) else raw
            val quoted = rawText.length >= 2 && rawText.first() == '"' && rawText.last() == '"'
            val text = rawText.removeSurrounding("\"")
            result += TagQueryToken(text = text, namespace = canonicalNamespace(rawNamespace), modifier = modifier, quoted = quoted, start = start, end = i)
        }
        return TagQuery(input, result)
    }

    private fun canonicalNamespace(raw: String?): String? {
        if (raw == null) return null
        val normalized = normalizeText(raw)
        return TagNamespaceRegistry.allDescriptors().firstOrNull { it.labelZh == normalized }?.name
            ?: TagNamespaceRegistry.canonicalNamespace(normalized)
            ?: normalized.takeIf(String::isNotBlank)
    }
}
