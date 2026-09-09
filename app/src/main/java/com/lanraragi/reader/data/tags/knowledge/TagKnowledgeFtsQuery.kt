package com.lanraragi.reader.data.tags.knowledge

/**
 * Emits only a deliberately small FTS MATCH grammar. Anything requiring quoting, punctuation, or
 * substring matching falls back to the authoritative in-memory ranker.
 */
internal object TagKnowledgeFtsQuery {
    fun build(query: TagQuery): String? {
        val terms = query.positiveTokens.map { it.text.trim() }
        if (terms.isEmpty() || terms.any { it.length < 2 || it.any { char -> !char.isLetterOrDigit() } }) return null
        return terms.joinToString(" AND ") { "\"$it\"*" }
    }
}

/** Optional optimization boundary; every [TagKnowledgeStore] remains usable without FTS. */
interface TagKnowledgeSearchStore {
    suspend fun searchCandidates(match: String, limit: Int): List<TagDictionaryRecord>
}
