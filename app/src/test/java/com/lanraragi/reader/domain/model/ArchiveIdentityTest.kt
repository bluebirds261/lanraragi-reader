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
    fun localArchivesNeverGainServerCapabilitiesByDefault() {
        val capabilities = ArchiveCapabilities.forIdentity(ArchiveIdentity.LocalSaf("content://book"))
        assertFalse(capabilities.canUpload)
        assertFalse(capabilities.canEditServerMetadata)
        assertFalse(capabilities.canEditServerToc)
        assertFalse(capabilities.canDeleteServerCopy)
    }
}
