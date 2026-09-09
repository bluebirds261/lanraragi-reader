package com.lanraragi.reader.data.tags.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TagKnowledgeTransactionTest {
    private fun snapshot(version: String, key: String = "Alice") = TagKnowledgeSnapshot(
        version = version,
        dictionary = listOf(TagDictionaryRecord(namespace = "artists", tagKey = key, translatedName = "爱丽丝", dataVersion = version)),
        frequencies = listOf(TagFrequencyRecord(namespace = "artist", tagKey = key, source = "global", count = 2, dataVersion = version)),
    )

    @Test fun normalizesAliasesAndDuplicateRowsDeterministically() {
        val result = snapshot("v1").copy(dictionary = listOf(
            TagDictionaryRecord("artists", " Alice ", translatedName = null, dataVersion = "v1"),
            TagDictionaryRecord("artist", "alice", translatedName = "爱丽丝", dataVersion = "v1"),
        )).normalized()
        assertEquals(1, result.dictionary.size)
        assertEquals("artist", result.dictionary.single().namespace)
        assertEquals("alice", result.dictionary.single().tagKey)
        assertEquals("爱丽丝", result.dictionary.single().translatedName)
    }

    @Test fun malformedCandidateRollsBackToPreviousSnapshot() = runBlocking {
        val old = snapshot("v1")
        val store = InMemoryTagKnowledgeStore(old)
        val result = TagKnowledgeUpdater(store).update(snapshot("v2", key = " "))
        assertTrue(result is TagKnowledgeUpdate.Rejected)
        assertEquals(old.normalized(), store.current())
    }

    @Test fun mismatchedRowVersionIsRejectedWithoutMutation() = runBlocking {
        val old = snapshot("v1")
        val store = InMemoryTagKnowledgeStore(old)
        val bad = snapshot("v2").copy(dictionary = listOf(TagDictionaryRecord("artist", "alice", dataVersion = "v1")))
        assertTrue(TagKnowledgeUpdater(store).update(bad) is TagKnowledgeUpdate.Rejected)
        assertEquals("v1", store.current()!!.version)
    }

    @Test fun activeVersionCannotBeReplacedBySameVersion() = runBlocking {
        val store = InMemoryTagKnowledgeStore(snapshot("v1"))
        assertTrue(TagKnowledgeUpdater(store).update(snapshot("v1")) is TagKnowledgeUpdate.Rejected)
    }
}
