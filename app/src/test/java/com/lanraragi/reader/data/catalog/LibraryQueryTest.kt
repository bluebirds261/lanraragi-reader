package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryTest {
    @Test
    fun normalizedRemoteRequestIsDeterministic() {
        val request = LibraryQuery(text = "  cat ", tags = setOf(" artist ", "language:ja"), categoryId = " c ").remoteRequest()
        assertEquals("cat,artist$,language:ja$", request.filter)
        assertEquals("c", request.categoryId)
    }

    @Test
    fun mixedRepositoryFiltersDeduplicatesAndPagesDeterministically() = runTest {
        val remote = row("r1", "Beta", listOf("artist:a"))
        val local = row("local", "Alpha", isLocal = true, saved = true)
        val repository = MixedLibraryRepository(
            object : LibraryRemoteGateway { override suspend fun fetch(query: LibraryQuery) = listOf(remote) },
            object : LibraryLocalGateway { override suspend fun fetch() = listOf(local, remote.copy(title = "duplicate")) },
        )
        val page = repository.load(LibraryQuery(source = LibrarySource.ALL), page = 0, pageSize = 2)
        assertEquals(listOf("Alpha", "Beta"), page.items.map { it.title })
        assertEquals(2, page.total)
    }

    @Test
    fun localSourceNeverCallsRemote() = runTest {
        var calls = 0
        val repository = MixedLibraryRepository(
            object : LibraryRemoteGateway { override suspend fun fetch(query: LibraryQuery): List<LibraryEntry> { calls++; return emptyList() } },
            object : LibraryLocalGateway { override suspend fun fetch(): List<LibraryEntry> = emptyList() },
        )
        repository.load(LibraryQuery(source = LibrarySource.LOCAL))
        assertEquals(0, calls)
    }

    @Test
    fun capabilityFilterExcludesLocalRowsFromServerMutationSet() = runTest {
        val remote = row("remote", "remote")
        val local = row("local", "local", isLocal = true)
        val repository = MixedLibraryRepository(
            object : LibraryRemoteGateway { override suspend fun fetch(query: LibraryQuery) = listOf(remote) },
            object : LibraryLocalGateway { override suspend fun fetch() = listOf(local) },
        )
        val result = repository.load(LibraryQuery(requiredCapabilities = setOf(LibraryCapability.DELETE_SERVER_COPY)))
        assertEquals(listOf("remote"), result.items.map { it.title })
    }

    private fun row(id: String, title: String, tags: List<String> = emptyList(), isLocal: Boolean = false, saved: Boolean = false) =
        LibraryEntry(if (isLocal) ArchiveIdentity.LocalSaf("content://$id") else ArchiveIdentity.Remote(id), title, tags, isSaved = saved)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRequestCoordinatorTest {
    @Test
    fun staleGenerationCannotOverwriteNewQuery() = runTest {
        val repository = object : LibraryRepository {
            override suspend fun load(query: LibraryQuery, page: Int, pageSize: Int): LibraryPage {
                if (query.text == "old") delay(100)
                return LibraryPage(listOf(LibraryEntry(ArchiveIdentity.Remote(query.text), query.text)), 1, page, pageSize, false)
            }
        }
        val coordinator = LibraryRequestCoordinator(this, repository)
        coordinator.refresh(LibraryQuery(text = "old"))
        coordinator.refresh(LibraryQuery(text = "new"))
        advanceUntilIdle()
        assertEquals(listOf("new"), coordinator.state.value.items.map { it.title })
    }

    @Test
    fun loadMoreAppendsStablePages() = runTest {
        val repository = object : LibraryRepository {
            override suspend fun load(query: LibraryQuery, page: Int, pageSize: Int) =
                LibraryPage(listOf(LibraryEntry(ArchiveIdentity.Remote("$page"), "$page")), 2, page, 1, page == 0)
        }
        val coordinator = LibraryRequestCoordinator(this, repository, pageSize = 1)
        coordinator.refresh(LibraryQuery())
        advanceUntilIdle()
        coordinator.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("0", "1"), coordinator.state.value.items.map { it.title })
    }

    @Test
    fun emptyResultExposesEmptyContractAndCancelInvalidatesGeneration() = runTest {
        val coordinator = LibraryRequestCoordinator(
            this,
            object : LibraryRepository {
                override suspend fun load(query: LibraryQuery, page: Int, pageSize: Int) =
                    LibraryPage(emptyList(), 0, page, pageSize, false)
            },
            debounceMillis = 0,
        )
        val generation = coordinator.refresh(LibraryQuery(text = "none"))
        advanceUntilIdle()
        assertEquals(generation, coordinator.state.value.generation)
        assertEquals(true, coordinator.state.value.isEmpty)
        coordinator.cancel()
        assertEquals(false, coordinator.state.value.loading)
        assertEquals(null, coordinator.loadMore())
    }
}
