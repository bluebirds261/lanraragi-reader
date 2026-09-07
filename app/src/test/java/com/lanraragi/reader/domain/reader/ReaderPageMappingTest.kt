package com.lanraragi.reader.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageMappingTest {
    @Test
    fun convertsBetweenUiAndApiPages() {
        assertEquals(1, ReaderPageMapping.uiPageToApiPage(0))
        assertEquals(0, ReaderPageMapping.apiPageToUiPage(1))
        assertEquals(8, ReaderPageMapping.apiPageToUiPage(9))
    }

    @Test
    fun mapsOddDoublePageBookWithoutDroppingLastPage() {
        assertEquals(3, ReaderPageMapping.screenCount(5, 2))
        assertEquals(4..4, ReaderPageMapping.pagesForScreen(2, 5, 2))
        assertEquals(2, ReaderPageMapping.pageToScreen(4, 5, 2))
    }

    @Test
    fun keepsCoverAloneInDoublePageMode() {
        assertEquals(4, ReaderPageMapping.screenCount(6, 2, firstPageAlone = true))
        assertEquals(0..0, ReaderPageMapping.pagesForScreen(0, 6, 2, firstPageAlone = true))
        assertEquals(1..2, ReaderPageMapping.pagesForScreen(1, 6, 2, firstPageAlone = true))
        assertEquals(3, ReaderPageMapping.pageToScreen(5, 6, 2, firstPageAlone = true))
    }
}
