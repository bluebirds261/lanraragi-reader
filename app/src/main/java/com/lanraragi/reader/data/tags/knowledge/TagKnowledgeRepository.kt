package com.lanraragi.reader.data.tags.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-facing facade; callers can stage/update/search without knowing Room details. */
class TagKnowledgeRepository(
    private val store: TagKnowledgeStore,
) {
    private val mutex = Mutex()
    private var loaded = false
    private var snapshotCache: TagKnowledgeSnapshot? = null

    private suspend fun snapshot(): TagKnowledgeSnapshot? {
        if (!loaded) { snapshotCache = store.current(); loaded = true }
        return snapshotCache
    }

    suspend fun replace(snapshot: TagKnowledgeSnapshot): TagKnowledgeUpdate = withContext(Dispatchers.IO) {
        mutex.withLock {
            val result = store.replaceAtomically(snapshot)
            loaded = false
            snapshotCache = null
            result
        }
    }

    suspend fun suggestions(
        query: String,
        personalFrequency: Map<TagKnowledgeKey, Long> = emptyMap(),
        limit: Int = 10,
    ): List<TagSuggestion> = withContext(Dispatchers.IO) {
        mutex.withLock {
        val snapshot = snapshot() ?: return@withLock emptyList()
        val context = currentCoroutineContext()
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
            checkpoint = { context.ensureActive() },
        )
        }
    }

    suspend fun current(): TagKnowledgeSnapshot? = withContext(Dispatchers.IO) { mutex.withLock { snapshot() } }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { store.clear(); snapshotCache = null; loaded = false }
    }
}
