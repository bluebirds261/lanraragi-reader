package com.lanraragi.reader.data.history.core

import com.lanraragi.reader.domain.model.ArchiveCapabilities
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressOutboxTest {
    @Test fun mergesByMaxPageAndSeparatesServerIdentity() = runBlocking {
        val store = InMemoryProgressTaskStore()
        val outbox = ProgressOutbox(store)
        val a = ArchiveIdentity.Remote("x", "https://a")
        val b = ArchiveIdentity.Remote("x", "https://b")
        outbox.enqueue(a, 2)
        outbox.enqueue(a, 1)
        outbox.enqueue(b, 4)
        assertEquals(listOf(2, 4), outbox.pending().sortedBy { it.page }.map { it.page })
    }

    @Test fun localIsNeverEnqueuedAndCapabilityBlocks() = runBlocking {
        val local = ArchiveIdentity.LocalSaf("content://x")
        val blocked = ArchiveIdentity.Remote("blocked")
        val outbox = ProgressOutbox(InMemoryProgressTaskStore()) { identity ->
            if (identity == blocked) ArchiveCapabilities(canRead = false) else ArchiveCapabilities.forIdentity(identity)
        }
        assertNull(outbox.enqueue(local, 3))
        assertNull(outbox.enqueue(blocked, 3))
        assertEquals(0, outbox.pending().size)
    }

    @Test fun successfulFlushClearsAndFailureRetains() = runBlocking {
        val store = InMemoryProgressTaskStore()
        val outbox = ProgressOutbox(store)
        val ok = ArchiveIdentity.Remote("ok")
        val bad = ArchiveIdentity.Remote("bad")
        outbox.enqueue(ok, 3)
        outbox.enqueue(bad, 5)
        val report = outbox.flush { identity, _, _ ->
            if (identity.arcid == "bad") error("offline")
        }
        assertEquals(1, report.succeeded)
        assertEquals(1, report.retained)
        assertEquals("bad", outbox.pending().single().identity.let { (it as ArchiveIdentity.Remote).arcid })
    }
}
