package com.lanraragi.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression contract for the legacy repository's pure recent-list semantics. */
class HistoryRepositoryTest {
    @Test
    fun historyEntryDefaultsRepresentZeroBasedProgress() {
        val entry = HistoryEntry("arc-1", "Title", timestamp = 1L)
        assertEquals(0, entry.page)
        assertEquals(0, entry.pageCount)
    }
}
