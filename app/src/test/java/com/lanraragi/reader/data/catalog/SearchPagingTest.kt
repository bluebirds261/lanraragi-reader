package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SearchPagingTest {
    private fun row(id: Int) = LibraryEntry(ArchiveIdentity.Remote("$id"), "Title $id", listOf("language:chinese"))
    private fun local() = LibraryEntry(ArchiveIdentity.LocalSaf("content://one"), "Local", listOf("language:chinese"))
    private class Remote(val make: (Int) -> RemoteLibraryPage) : PagedLibraryRemoteGateway {
        val offsets = mutableListOf<Int>()
        override suspend fun fetch(query: LibraryQuery): List<LibraryEntry> = error("Must page")
        override suspend fun fetchPage(query: LibraryQuery, offset: Int): RemoteLibraryPage { offsets += offset; return make(offset) }
    }
    private fun locals(vararg rows: LibraryEntry) = object : LibraryLocalGateway { override suspend fun fetch() = rows.toList() }

    @Test fun firstPageDoesNotExhaustServerAndAppendUsesActualOffset() = runTest {
        val remote = Remote { offset -> RemoteLibraryPage((offset until offset + 40).map(::row), 1000, offset + 40, true) }
        val repository = MixedLibraryRepository(remote, locals())
        val query = LibraryQuery(source = LibrarySource.REMOTE, text = "language:chinese$", categoryId = "dynamic")
        val first = repository.load(query, 0, 30)
        assertEquals(30, first.items.size)
        assertEquals(1000, first.total)
        assertEquals(listOf(0), remote.offsets)
        val second = repository.load(query, 1, 30)
        assertEquals(30, second.items.size)
        assertEquals(listOf(0, 40), remote.offsets)
        assertEquals("30", (second.items.first().identity as ArchiveIdentity.Remote).arcid)
    }
    @Test fun refreshStartsNewSnapshotAndSourceScopeDoesNotReuseOldPages() = runTest {
        val remote = Remote { offset -> RemoteLibraryPage((offset until offset + 30).map(::row), 100, offset + 30, true) }
        val repository = MixedLibraryRepository(remote, locals())
        val q = LibraryQuery(source = LibrarySource.REMOTE, serverScope = "one")
        repository.load(q, 0, 30)
        repository.load(q, 0, 30)
        repository.load(q.copy(serverScope = "two"), 0, 30)
        assertEquals(listOf(0, 0, 0), remote.offsets)
    }
    @Test fun mixedResultsAreExplicitlyGroupedAndDoNotRepeatRemoteFetch() = runTest {
        val remote = Remote { RemoteLibraryPage((0..39).map(::row), 40, 40, false) }
        val repository = MixedLibraryRepository(remote, locals(local()))
        val query = LibraryQuery(text = "language:chinese$")
        val first = repository.load(query, 0, 30)
        val second = repository.load(query, 1, 30)
        assertEquals(LibrarySource.LOCAL, first.items.first().source)
        assertEquals(41, first.total)
        assertEquals(11, second.items.size)
        assertEquals(listOf(0), remote.offsets)
    }
    @Test fun remoteFailureStillShowsLocalAndMarksCountIncomplete() = runTest {
        val repository = MixedLibraryRepository(Remote { error("offline") }, locals(local()))
        val result = repository.load(LibraryQuery(), 0, 30)
        assertEquals(1, result.items.size)
        assertNull(result.total)
        assertTrue(result.warning!!.contains("offline"))
    }
    @Test fun failedAppendRetriesTheSameServerOffset() = runTest {
        var failing = true
        val remote = Remote { offset ->
            if (offset == 30 && failing) error("offline")
            RemoteLibraryPage((offset until offset + 30).map(::row), 60, offset + 30, offset == 0)
        }
        val repository = MixedLibraryRepository(remote, locals())
        val q = LibraryQuery(source = LibrarySource.REMOTE)
        repository.load(q, 0, 30)
        try { repository.load(q, 1, 30); fail("Expected failure") } catch (_: IllegalStateException) { }
        failing = false
        assertEquals(30, repository.load(q, 1, 30).items.size)
        assertEquals(listOf(0, 30, 30), remote.offsets)
    }

    @Test fun smallServerPagesAreShownImmediatelyWithoutSkippingNextRows() = runTest {
        val remote = Remote { offset -> RemoteLibraryPage((offset until offset + 10).map(::row), 10000, offset + 10, true) }
        val repository = MixedLibraryRepository(remote, locals())
        val q = LibraryQuery(source = LibrarySource.REMOTE)
        val first = repository.load(q, 0, 30)
        assertEquals(10, first.items.size)
        assertEquals(listOf(0), remote.offsets)
        val next = repository.load(q, 1, 30)
        assertEquals("10", (next.items.first().identity as ArchiveIdentity.Remote).arcid)
        assertEquals(listOf(0, 10), remote.offsets)
    }
}
