package com.lanraragi.reader.domain.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        assertEquals(a.identity, b.identity)
    }

    @Test
    fun identityExcludesPresentationAndProvenance() {
        val fromServer = CanonicalTag.parse("Artist:John Doe", TagSource.LANRARAGI, confidence = 0.2f)
        val translated = CanonicalTag(
            namespace = "artist",
            key = "john doe",
            raw = "artist:JOHN DOE",
            displayNameZh = "约翰",
            source = TagSource.EHENTAI,
            confidence = 0.9f,
        )

        assertNotEquals(fromServer, translated)
        assertEquals(fromServer.identity, translated.identity)
    }

    @Test
    fun identityKeepsNamespaceAndKeyDistinct() {
        val artist = CanonicalTag.parse("artist:john doe")
        val group = CanonicalTag.parse("group:john doe")
        val otherArtist = CanonicalTag.parse("artist:jane doe")

        assertNotEquals(artist.identity, group.identity)
        assertNotEquals(artist.identity, otherArtist.identity)
    }

    @Test
    fun unnamespacedIdentityIsStableAndFullRemainsCanonical() {
        val tag = CanonicalTag.parse("  Mixed　 Tag  ", TagSource.NHENTAI)

        assertNull(tag.identity.namespace)
        assertEquals("mixed tag", tag.identity.key)
        assertEquals("mixed tag", tag.full)
        assertEquals(TagSource.NHENTAI, tag.source)
    }

    @Test
    fun directConstructorCanonicalizesIdentityAndFull() {
        val direct = CanonicalTag(
            namespace = "  ARTIST  ",
            key = " John　Doe ",
            raw = "untrusted display value",
        )
        val parsed = CanonicalTag.parse("artist:john doe")

        assertEquals(parsed.identity, direct.identity)
        assertEquals("artist:john doe", direct.full)
    }
}
