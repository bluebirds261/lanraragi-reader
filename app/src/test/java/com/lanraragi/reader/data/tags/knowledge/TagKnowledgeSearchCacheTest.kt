package com.lanraragi.reader.data.tags.knowledge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TagKnowledgeSearchCacheTest {
    @Test fun successiveKeystrokesReuseSnapshotAndReplacementInvalidatesIt() = runTest {
        val base = InMemoryTagKnowledgeStore(TagKnowledgeSnapshot("1", listOf(TagDictionaryRecord("artist", "alice", dataVersion = "1"))))
        var reads = 0
        val store = object : TagKnowledgeStore by base {
            override suspend fun current(): TagKnowledgeSnapshot? { reads++; return base.current() }
        }
        val repository = TagKnowledgeRepository(store)
        assertEquals("alice", repository.suggestions("ali").single().entry.tagKey)
        assertEquals("alice", repository.suggestions("alic").single().entry.tagKey)
        assertEquals(1, reads)
        repository.replace(TagKnowledgeSnapshot("2", listOf(TagDictionaryRecord("artist", "bob", dataVersion = "2"))))
        assertEquals("bob", repository.suggestions("bob").single().entry.tagKey)
        assertEquals(2, reads)
        repository.clear()
        assertTrue(repository.suggestions("bob").isEmpty())
    }
}
