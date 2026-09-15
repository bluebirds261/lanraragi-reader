package com.lanraragi.reader.data

import org.junit.Assert.*
import org.junit.Test

class SearchCountsTest {
    @Test fun matchedCountNeverUsesLibraryCount() {
        val counts = JsonHelpers.parseSearchCounts("""{"data":[],"recordsFiltered":42,"recordsTotal":1000}""")
        assertEquals(42, counts.matched)
        assertEquals(1000, counts.library)
        assertEquals(1000, JsonHelpers.parseArchiveList("""{"data":[],"recordsFiltered":42,"recordsTotal":1000}""").second)
    }
    @Test fun missingCountsStayUnknown() {
        assertNull(JsonHelpers.parseSearchCounts("[]").matched)
        assertNull(JsonHelpers.parseSearchCounts("""{"data":[],"recordsTotal":1000}""").matched)
    }
}
