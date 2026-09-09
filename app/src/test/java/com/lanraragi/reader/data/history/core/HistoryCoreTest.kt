package com.lanraragi.reader.data.history.core

import com.lanraragi.reader.domain.model.ArchiveIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryCoreTest {
    private val remote = ArchiveIdentity.Remote("abc", "https://one")

    @Test fun keepsCompleteHistoryAndRecentIsLimit3() {
        val index = HistoryIndex()
        repeat(5) { i ->
            index.recordOpen(HistoryRecord(ArchiveIdentity.Remote("$i"), "t$i", firstReadAt = i.toLong(), lastReadAt = i.toLong()))
        }
        assertEquals(5, index.all().size)
        assertEquals(listOf("4", "3", "2"), index.recent().map { it.archiveId })
    }

    @Test fun dedupesByStableSourceKeyAndMergesBounds() {
        val first = HistoryRecord(remote, "old", page = 2, pageCount = 5, firstReadAt = 1, lastReadAt = 2)
        val second = HistoryRecord(remote, "new", page = 7, pageCount = 9, firstReadAt = 3, lastReadAt = 8)
        val result = HistoryQueries.dedupe(listOf(first, second))
        assertEquals(1, result.size)
        assertEquals(7, result.single().page)
        assertEquals(1, result.single().firstReadAt)
        assertEquals(9, result.single().pageCount)
    }

    @Test fun localAndRemoteAndServersRemainSeparate() {
        val a = ArchiveIdentity.Remote("same", "https://one")
        val b = ArchiveIdentity.Remote("same", "https://two")
        val local = ArchiveIdentity.LocalSaf("content://same")
        assertEquals(3, setOf(a.sourceKey, b.sourceKey, local.sourceKey).size)
    }

    @Test fun removeAndClear() {
        val index = HistoryIndex()
        index.recordOpen(HistoryRecord(remote, "x", firstReadAt = 1, lastReadAt = 1))
        index.remove(remote)
        assertNull(index.get(remote))
        index.recordOpen(HistoryRecord(remote, "x", firstReadAt = 1, lastReadAt = 1))
        index.clear()
        assertEquals(0, index.all().size)
    }
}
