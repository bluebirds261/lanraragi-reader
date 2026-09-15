package com.lanraragi.reader.data.catalog

import org.junit.Assert.*
import org.junit.Test

class SearchQueryCodecTest {
    @Test fun lanraragiClausesPreserveSpacesAndSeparateOnCommas() {
        val terms = SearchQueryCodec.parse("artist:sample creator$,-language:english$")
        assertEquals(listOf("artist:sample creator", "language:english"), terms.map { it.value })
        assertTrue(terms.all { it.exact })
        assertTrue(terms.last().excluded)
        assertEquals("artist:sample creator", SearchQueryCodec.parse("\"artist:sample creator\"").single().value)
    }
    @Test fun generatedTagsUseServerSyntaxIncludingNoNamespace() {
        assertEquals("artist:sample creator$", SearchQueryCodec.exactTag("artist:sample creator"))
        assertEquals("-full color$", SearchQueryCodec.exactTag("full color", true))
        assertNotNull(SearchQueryCodec.validationError("artist:\"sample$"))
        assertNotNull(SearchQueryCodec.validationError("artist:\"sample$\""))
        assertNotNull(SearchQueryCodec.validationError("~artist:sample"))
    }
    @Test fun cursorInMiddleReplacesOnlyCurrentClauseAndRetainsExclusion() {
        val query = "language:chinese$,-art,pages:>100"
        val result = SearchQueryCodec.replace(query, query.indexOf("art") + 2, query.indexOf("art") + 2, "artist:sample")
        assertEquals("language:chinese$,-artist:sample$,pages:>100", result.text)
        assertEquals(result.text.indexOf(",pages"), result.cursor)
    }
    @Test fun repeatSelectionDoesNotAccumulateDuplicateTags() {
        val first = SearchQueryCodec.replace("art", 3, 3, "artist:sample")
        val second = SearchQueryCodec.replace(first.text, first.cursor, first.cursor, "artist:sample")
        assertEquals(first.text, second.text)
    }
    @Test fun oppositeTagSelectionRequiresExplicitRemoval() {
        val query = "artist:sample$,"
        try {
            SearchQueryCodec.replace(query, query.length, query.length, "artist:sample", true)
            fail("Must not silently reverse an existing condition")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("相反条件"))
        }
    }
    @Test fun explicitSelectionPreservesOutsideText() {
        assertEquals("artist:sample$,language:chinese$", SearchQueryCodec.replace("art,language:chinese$", 0, 3, "artist:sample").text)
    }
    @Test fun localSearchEvaluatesConditionsInsteadOfEntireQuerySubstring() {
        val tags = listOf("artist:sample creator", "language:chinese", "full color")
        assertTrue(SearchQueryCodec.matches("artist:sample creator$,-language:english$,pages:>100", "Title", tags, 120))
        assertFalse(SearchQueryCodec.matches("language:english$", "Title", tags))
        assertFalse(SearchQueryCodec.matches("artist:sample$", "Title", tags))
        assertTrue(SearchQueryCodec.matches("artist:sample*", "Title", tags))
        assertTrue(SearchQueryCodec.matches("full color$", "Title", tags))
        assertTrue(SearchQueryCodec.matches("标题", "中文标题", emptyList()))
    }
}
