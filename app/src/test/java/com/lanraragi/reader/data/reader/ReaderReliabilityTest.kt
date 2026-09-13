package com.lanraragi.reader.data.reader

import com.lanraragi.reader.data.history.core.InMemoryProgressTaskStore
import com.lanraragi.reader.data.history.core.ProgressTransport
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderReliabilityTest {
    @Test
    fun sessionClosesPageSourceWhenSwitchingAndWhenReaderExits() = runTest {
        val first = CloseTrackingPageSource(ArchiveIdentity.Remote("first"))
        val second = CloseTrackingPageSource(ArchiveIdentity.Remote("second"))
        val session = ReaderSession(
            resolver = ReaderSourceResolver { identity, _ ->
                if ((identity as ArchiveIdentity.Remote).arcid == "first") first else second
            },
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        session.open(first.identity)
        advanceUntilIdle()
        session.open(second.identity)
        advanceUntilIdle()
        assertEquals(1, first.closeCount)

        session.close()
        assertEquals(1, second.closeCount)
    }

    @Test
    fun progressWriterCoalescesRemoteAndNeverQueuesLocal() = runTest {
        val sent = mutableListOf<Int>()
        val writer = OutboxProgressWriter(
            InMemoryProgressTaskStore(),
            ProgressTransport { _, page, _ -> sent += page },
        )
        val remote = ArchiveIdentity.Remote("remote-1")

        writer.record(remote, 2, 10)
        writer.record(remote, 7, 10)
        assertNull(writer.record(ArchiveIdentity.LocalSaf("content://provider/local"), 9, 10))
        assertEquals(7, writer.pending().single().page)

        writer.flush()
        assertEquals(listOf(7), sent)
        assertTrue(writer.pending().isEmpty())
    }

    @Test
    fun gesturePanZoomConsumesUntilZoomIsReset() {
        val arbiter = ReaderGestureArbiter()
        assertFalse(arbiter.begin().consume)
        assertFalse(arbiter.move(40f, 1f).consume)
        assertTrue(arbiter.applyZoom(1.5f).consume)
        assertTrue(arbiter.move(2f, 20f).consume)
        assertFalse(arbiter.resetZoom().consume)
    }

    @Test
    fun newPrefetchWindowCancelsWorkOutsideCurrentWindow() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val loaded = mutableListOf<Int>()
        val coordinator = PrefetchCoordinator(scope, radius = 1)

        coordinator.update(center = 1, pageCount = 10) { index ->
            delay(if (index == 0) 100 else 1)
            loaded += index
        }
        coordinator.update(center = 7, pageCount = 10) { index -> loaded += index }
        testScheduler.advanceUntilIdle()

        assertEquals(setOf(6, 7, 8), loaded.toSet())
        coordinator.cancel()
        scope.cancel()
    }

    @Test
    fun modelPrefetchClosesPreviousWindowAndUsesRequestedRadius() = runTest {
        val coordinator = PrefetchCoordinator(this, radius = 1)
        val requested = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        val prefetcher = PagePrefetcher { model ->
            requested += model.index
            AutoCloseable { closed += model.index }
        }
        val model: (Int) -> PageModel = { index ->
            PageModel.RemotePage("https://example.test/$index", index, false, "r1")
        }

        coordinator.updateModels(3, 10, radius = 2, model = model, prefetcher = prefetcher)
        assertEquals(listOf(1, 2, 3, 4, 5), requested)

        coordinator.updateModels(8, 10, radius = 1, model = model, prefetcher = prefetcher)
        assertEquals(listOf(1, 2, 3, 4, 5), closed)
        assertEquals(listOf(7, 8, 9), requested.takeLast(3))

        coordinator.cancel()
        assertEquals(listOf(7, 8, 9), closed.takeLast(3))
    }

    private class CloseTrackingPageSource(
        override val identity: ArchiveIdentity.Remote,
    ) : PageSource {
        var closeCount = 0
        override val pageCount: Int = 1
        override val revision: String = identity.sourceKey

        override suspend fun refresh(): PageSource = this

        override fun pageModel(index: Int): PageModel =
            PageModel.RemotePage("https://example.test/$index", index, false, revision)

        override fun thumbnailModel(index: Int): PageModel =
            PageModel.RemotePage("https://example.test/thumb/$index", index, true, revision)

        override fun close() {
            closeCount += 1
        }
    }
}
