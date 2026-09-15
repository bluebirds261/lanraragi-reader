package com.lanraragi.reader.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SearchHistoryRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun concurrentAddsRemainAtomicAndScopesAreIndependent() = runTest {
        val repository = SearchHistoryRepository(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "atomic.preferences_pb") }))
        (1..15).map { async { repository.add("query $it", "server:a") } }.awaitAll()
        assertEquals(15, repository.history("server:a").first().size)
        repository.add("other", "server:b")
        repository.add("legacy")
        repository.clear("server:a")
        assertTrue(repository.history("server:a").first().isEmpty())
        assertEquals(listOf("other"), repository.history("server:b").first())
        assertEquals(listOf("legacy"), repository.history.first())
    }

    @Test fun pausedHistoryCanBeRestoredAndHiddenDoesNotStopRecording() = runTest {
        val repository = SearchHistoryRepository(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "preferences.preferences_pb") }))
        repository.setHidden(true)
        repository.add("recorded", "local")
        assertEquals(listOf("recorded"), repository.history("local").first())
        repository.setPaused(true)
        repository.add("ignored", "local")
        repository.remove("recorded", "local")
        repository.add("recorded", "local", restoring = true)
        assertEquals(listOf("recorded"), repository.history("local").first())
        assertTrue(repository.paused.first())
        assertTrue(repository.hidden.first())
    }

    @Test fun capacityAndRecentDeduplicationSurvivePersistence() = runTest {
        val repository = SearchHistoryRepository(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "capacity.preferences_pb") }))
        (1..25).forEach { repository.add("query $it", "local") }
        repository.add("query 10", "local")
        val history = repository.history("local").first()
        assertEquals(20, history.size)
        assertEquals("query 10", history.first())
        assertEquals(1, history.count { it == "query 10" })
        assertFalse("query 1" in history)
    }
}
