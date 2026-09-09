package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeKey
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeRepository
import com.lanraragi.reader.data.tags.knowledge.TagSuggestion

/** Small boundary so library/search UI does not depend on the tag knowledge store implementation. */
interface TagKnowledgeQueryFacade {
    suspend fun suggestions(
        query: String,
        personalFrequency: Map<TagKnowledgeKey, Long> = emptyMap(),
        limit: Int = 10,
    ): List<TagSuggestion>
}

class RepositoryTagKnowledgeQueryFacade(
    private val repository: TagKnowledgeRepository,
) : TagKnowledgeQueryFacade {
    override suspend fun suggestions(
        query: String,
        personalFrequency: Map<TagKnowledgeKey, Long>,
        limit: Int,
    ): List<TagSuggestion> = repository.suggestions(query, personalFrequency, limit)
}
