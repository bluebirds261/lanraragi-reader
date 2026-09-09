package com.lanraragi.reader.data.local

import com.lanraragi.reader.data.db.LocalArchiveEntity
import com.lanraragi.reader.domain.model.ArchiveIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAvailabilityLogicTest {
    @Test
    fun availabilityValuesAreStableForPersistence() {
        assertEquals("AVAILABLE", Availability.AVAILABLE.value)
        assertEquals("UNAVAILABLE", Availability.UNAVAILABLE.value)
        assertEquals(Availability.AVAILABLE, Availability.valueOf("AVAILABLE"))
        assertEquals(Availability.UNAVAILABLE, Availability.valueOf("UNAVAILABLE"))
    }

    @Test
    fun reauthorizeResultCarriesLocalIdentityAndOptionalEntity() {
        val uri = "content://reader/re-authorized"
        val identity = ArchiveIdentity.LocalSaf(uri)
        val entity = LocalArchiveEntity(identity.sourceKey, uri, "Book", "fp", lastVerifiedAt = 7)
        val available = ReauthorizeResult(identity.sourceKey, available = true, entity = entity)
        val unavailable = ReauthorizeResult(identity.sourceKey, available = false, entity = null)

        assertTrue(available.available)
        assertEquals(identity.sourceKey, available.sourceKey)
        assertEquals(entity, available.entity)
        assertFalse(unavailable.available)
        assertEquals(identity.sourceKey, unavailable.sourceKey)
        assertEquals(null, unavailable.entity)
    }
}
