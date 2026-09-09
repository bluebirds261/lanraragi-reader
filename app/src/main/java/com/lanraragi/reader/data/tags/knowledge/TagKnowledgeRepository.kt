package com.lanraragi.reader.data.tags.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Application-facing facade; callers can stage/update/search without knowing Room details. */
class TagKnowledgeRepository(
    private val store: TagKnowledgeStore,
) {
    suspend fun replace(snapshot: TagKnowledgeSnapshot): TagKnowledgeUpdate = withContext(Dispatchers.IO) {
        store.replaceAtomically(snapshot)
    }

    suspend fun suggestions(
        query: String,
        personalFrequency: Map<TagKnowledgeKey, Long> = emptyMap(),
        limit: Int = 10,
    ): List<TagSuggestion> = withContext(Dispatchers.IO) {
        val snapshot = store.current() ?: return@withContext emptyList()
        val parsed = TagQueryParser.parse(query)
        val ftsMatch = TagKnowledgeFtsQuery.build(parsed)
        val candidates = if (ftsMatch != null && store is TagKnowledgeSearchStore) {
            store.searchCandidates(ftsMatch, (limit.coerceAtLeast(1) * 20).coerceAtMost(200))
        } else {
            emptyList()
        }
        TagSuggestionRanker.rank(
            // An FTS miss must not change completion semantics: ranking remains the authority.
            entries = candidates.ifEmpty { snapshot.dictionary },
            query = parsed,
            frequencies = snapshot.frequencies,
            personalFrequency = personalFrequency,
            limit = limit,
        )
    }

    suspend fun current(): TagKnowledgeSnapshot? = withContext(Dispatchers.IO) { store.current() }

    suspend fun clear() = withContext(Dispatchers.IO) { store.clear() }
}
