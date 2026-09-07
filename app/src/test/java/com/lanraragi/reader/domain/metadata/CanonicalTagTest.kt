package com.lanraragi.reader.domain.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalTagTest {
    @Test
    fun normalizesUnicodeWhitespaceAndCaseWithoutLosingRawValue() {
        val raw = " Artist：  John　Doe "
        val tag = CanonicalTag.parse(raw, TagSource.EHENTAI)
        assertEquals("artist", tag.namespace)
        assertEquals("john doe", tag.key)
        assertEquals("artist:john doe", tag.full)
        assertEquals(raw, tag.raw)
    }

    @Test
    fun translationDoesNotParticipateInCanonicalIdentity() {
        val a = CanonicalTag("artist", "john doe", "artist:John Doe", "约翰")
        val b = a.copy(displayNameZh = "另一译名")
        assertNotEquals(a, b)
        assertEquals(a.full, b.full)
    }
}
