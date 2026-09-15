package com.lanraragi.reader.data.tags.knowledge

import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import kotlin.math.ln
import java.util.Locale

enum class TagMatchQuality(val baseScore: Int) {
    NAMESPACE_EXACT(500),
    NAMESPACE_PREFIX(450),
    CANONICAL_PREFIX(400),
    TRANSLATED_PREFIX(320),
    CANONICAL_CONTAINS(220),
    TRANSLATED_CONTAINS(140),
    NONE(0),
}

data class TagSuggestion(
    val entry: TagDictionaryRecord,
    val quality: TagMatchQuality,
    val score: Double,
    val frequency: Long,
    val personalFrequency: Long,
)

object TagSuggestionRanker {
    fun rank(
        entries: Iterable<TagDictionaryRecord>,
        query: TagQuery,
        frequencies: Iterable<TagFrequencyRecord> = emptyList(),
        personalFrequency: Map<TagKnowledgeKey, Long> = emptyMap(),
        limit: Int = 10,
        checkpoint: () -> Unit = {},
    ): List<TagSuggestion> {
        if (limit <= 0) return emptyList()
        val freq = frequencies.groupBy { TagKnowledgeKey(it.namespace, it.tagKey) }
            .mapValues { (_, rows) -> rows.sumOf { it.count.coerceAtLeast(0L) } }
        val positive = query.positiveTokens
        var visited = 0
        return entries.asSequence().mapNotNull { entry ->
            if (visited++ % 128 == 0) checkpoint()
            // 词库的元数据伪命名空间（rows 对照表）不是标签；旧快照里可能还留着，
            // 在这里挡一次，否则它会占掉候选名额甚至被用户点中。
            if (isPseudoDictionaryNamespace(entry.namespace)) return@mapNotNull null
            val key = TagKnowledgeKey(entry.namespace, entry.tagKey)
            val total = freq[key] ?: 0L
            val personal = personalFrequency[key] ?: 0L
            val matches = positive.map { match(entry, it) }
            val match = matches.filterNotNull().minByOrNull { it.baseScore }
            if (matchesExcluded(entry, query.tokens.filter { it.modifier == TagQueryModifier.EXCLUDE })) return@mapNotNull null
            if (matches.any { it == null }) return@mapNotNull null
            val score = (match?.baseScore ?: 0) + ln(1.0 + total.toDouble()) * 2.0 + ln(1.0 + personal.toDouble()) * 3.0
            TagSuggestion(entry, match ?: TagMatchQuality.NONE, score, total, personal)
        }.sortedWith(
            compareByDescending<TagSuggestion> { it.quality.baseScore }
                .thenByDescending { it.score }
                .thenByDescending { it.frequency }
                .thenByDescending { it.personalFrequency }
                .thenBy { it.entry.namespace }
                .thenBy { it.entry.tagKey }
                .thenBy { it.entry.translatedName.orEmpty() },
        ).take(limit).toList()
    }

    private fun match(entry: TagDictionaryRecord, token: TagQueryToken): TagMatchQuality? {
        val namespace = TagNamespaceRegistry.canonicalNamespace(entry.namespace).orEmpty()
        if (token.namespace != null && token.namespace != namespace) return null
        val q = normalizeText(token.text).lowercase(Locale.ROOT)
        if (q.isBlank()) return null
        val canonical = normalizeText(entry.tagKey).lowercase(Locale.ROOT)
        val translated = normalizeText(entry.translatedName.orEmpty()).lowercase(Locale.ROOT)
        val fullName = normalizeText(entry.fullName.orEmpty()).lowercase(Locale.ROOT)
        val intro = normalizeText(entry.intro.orEmpty()).lowercase(Locale.ROOT)
        val namespaceTerms = namespaceTerms(namespace)
        return when {
            namespaceTerms.any { it == q } -> TagMatchQuality.NAMESPACE_EXACT
            namespaceTerms.any { it.startsWith(q) } -> TagMatchQuality.NAMESPACE_PREFIX
            canonical.startsWith(q) -> TagMatchQuality.CANONICAL_PREFIX
            translated.startsWith(q) || fullName.startsWith(q) -> TagMatchQuality.TRANSLATED_PREFIX
            canonical.contains(q) -> TagMatchQuality.CANONICAL_CONTAINS
            translated.contains(q) || fullName.contains(q) ||
                (token.modifier == TagQueryModifier.FUZZY && intro.contains(q)) -> TagMatchQuality.TRANSLATED_CONTAINS
            else -> null
        }
    }

    private fun matchesExcluded(entry: TagDictionaryRecord, tokens: List<TagQueryToken>): Boolean =
        tokens.any { match(entry, it) != null }

    private fun namespaceTerms(namespace: String): Set<String> {
        val descriptor = TagNamespaceRegistry.descriptor(namespace)
        return buildSet {
            add(namespace.lowercase(Locale.ROOT))
            descriptor?.let {
                add(it.labelZh.lowercase(Locale.ROOT))
                addAll(it.aliases.map { alias -> alias.lowercase(Locale.ROOT) })
            }
        }
    }
}
