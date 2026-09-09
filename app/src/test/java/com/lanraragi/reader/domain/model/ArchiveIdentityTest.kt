package com.lanraragi.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArchiveIdentityTest {
    @Test
    fun sourceKeysAreStableAndSourceSpecific() {
        val local = ArchiveIdentity.LocalSaf("content://library/book.cbz")
        assertEquals(local.sourceKey, ArchiveIdentity.LocalSaf("content://library/book.cbz").sourceKey)
        assertNotEquals(local.sourceKey, ArchiveIdentity.Remote("book").sourceKey)
        assertEquals("tank:TANK_1234567890", ArchiveIdentity.Tankoubon("tank_1234567890").sourceKey)
    }

    @Test
    fun remoteServerScopesIsolateSameArcidWhileLegacyKeyRemainsCompatible() {
        val firstServer = ArchiveIdentity.Remote("Arc-42", " https://first.example/ ")
        val firstServerNormalized = ArchiveIdentity.Remote("arc-42", "https://first.example")
        val secondServer = ArchiveIdentity.Remote("arc-42", "https://second.example")
        val legacy = ArchiveIdentity.Remote("Arc-42")
        val blankScope = ArchiveIdentity.Remote("Arc-42", "   ")

        assertEquals(firstServer.sourceKey, firstServerNormalized.sourceKey)
        assertNotEquals(firstServer.sourceKey, secondServer.sourceKey)
        assertEquals("remote:arc-42", legacy.sourceKey)
        assertEquals(legacy.sourceKey, blankScope.sourceKey)
        assertEquals(legacy.sourceKey, ArchiveIdentity.fromArchiveId("Arc-42").sourceKey)
    }

    @Test
    fun localArchivesNeverGainServerCapabilitiesByDefault() {
        val capabilities = ArchiveCapabilities.forIdentity(ArchiveIdentity.LocalSaf("content://book"))
        assertFalse(capabilities.canUpload)
        assertFalse(capabilities.canEditServerMetadata)
        assertFalse(capabilities.canEditServerToc)
        assertFalse(capabilities.canDeleteServerCopy)
    }
}
