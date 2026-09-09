package com.lanraragi.reader.data

import com.lanraragi.reader.ui.settings.parseCacheLimitBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsProjectionTest {
    @Test
    fun orientationReaderPreferencesFallBackToGlobalValues() {
        val settings = Settings(
            readerMode = "single",
            readingDirection = "ltr",
            readerFitMode = "fitWidth",
            readerModeLandscape = "multi",
            doublePageFirstPageAlone = false,
            doublePageFirstPageAloneLandscape = true,
        )

        val portrait = settings.readerPreferences(ReaderOrientationProfile.PORTRAIT)
        val landscape = settings.readerPreferences(ReaderOrientationProfile.LANDSCAPE)

        assertEquals("single", portrait.mode)
        assertEquals("ltr", portrait.readingDirection)
        assertEquals("multi", landscape.mode)
        assertEquals("ltr", landscape.readingDirection)
        assertEquals(false, portrait.firstPageAlone)
        assertEquals(true, landscape.firstPageAlone)
    }

    @Test
    fun cacheLimitParserSupportsMbGbAndRejectsOverflow() {
        assertEquals(768L * 1024 * 1024, parseCacheLimitBytes("768 MB"))
        assertEquals(2L * 1024 * 1024 * 1024, parseCacheLimitBytes("2 GB"))
        assertEquals(1024L * 1024 * 1024, parseCacheLimitBytes("1"))
        assertNull(parseCacheLimitBytes("-1 GB"))
        assertNull(parseCacheLimitBytes("999999999999999999 GB"))
    }
}
