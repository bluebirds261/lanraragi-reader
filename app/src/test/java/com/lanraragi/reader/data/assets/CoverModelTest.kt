package com.lanraragi.reader.data.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverModelTest {
    @Test
    fun remoteServerKeyNormalizationKeepsEquivalentBasesTogether() {
        val first = CoverModel.RemoteCover(" HTTPS://Example.COM/library/ ", "arc-1", 0L)
        val second = CoverModel.RemoteCover("https://example.com/library", "arc-1", 0L)

        assertEquals("https://example.com/library", first.normalizedServerKey)
        assertEquals(first.cacheKey, second.cacheKey)
        assertTrue(first.cacheKey.contains("https://example.com/library"))
    }

    @Test
    fun serverKeyStripsCredentialsTokensFragmentsAndDefaultPorts() {
        val cover = CoverModel.RemoteCover(
            "https://user:pass@Example.com:443/library/?token=secret#x",
            "arc-1",
            0L,
        )

        assertEquals("https://example.com/library", cover.normalizedServerKey)
        assertTrue(cover.cacheKey.contains("https://example.com/library"))
        assertTrue("user credentials must not enter cache keys", !cover.cacheKey.contains("user"))
        assertTrue("passwords must not enter cache keys", !cover.cacheKey.contains("pass"))
        assertTrue("tokens must not enter cache keys", !cover.cacheKey.contains("secret"))
        assertTrue("fragments must not enter cache keys", !cover.cacheKey.contains("#x"))
    }

    @Test
    fun remoteServerAndRevisionArePartOfTheCacheIdentity() {
        val base = CoverModel.RemoteCover("https://one.example", "arc-1", 0L)

        assertNotEquals(
            base.cacheKey,
            base.copy(serverKey = "https://two.example").cacheKey,
        )
        assertNotEquals(base.cacheKey, base.copy(revision = 1L).cacheKey)
        assertTrue(base.cacheKey.contains("arc-1"))
        assertTrue(base.cacheKey.contains("revision=0"))
    }

    @Test
    fun localCoverKindsAndComponentsRemainDistinct() {
        val archiveEntry = CoverModel.ArchiveEntryCover("content://archive", "cover.jpg", "mtime=1;size=2")
        val otherEntry = archiveEntry.copy(entry = "page-1.jpg")
        val otherFingerprint = archiveEntry.copy(fingerprint = "mtime=2;size=2")
        val folder = CoverModel.FolderCover("content://archive", "mtime=1;size=2")

        assertNotEquals(archiveEntry.cacheKey, otherEntry.cacheKey)
        assertNotEquals(archiveEntry.cacheKey, otherFingerprint.cacheKey)
        assertNotEquals(archiveEntry.cacheKey, folder.cacheKey)
    }

    @Test
    fun localPathNormalizationMakesSeparatorsStable() {
        val first = CoverModel.SavedCover("  C:\\covers\\arc-1.jpg  ", 4L)
        val second = CoverModel.SavedCover("C:/covers/arc-1.jpg", 4L)

        assertEquals("C:/covers/arc-1.jpg", first.normalizedPath)
        assertEquals(first.cacheKey, second.cacheKey)
        assertEquals(first.cacheKey, first.cacheKey)
    }
}
