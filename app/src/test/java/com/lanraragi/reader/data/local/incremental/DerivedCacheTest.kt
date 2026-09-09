package com.lanraragi.reader.data.local.incremental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

class DerivedCacheTest {
    @Test fun keyIncludesIdentityAndRevision() {
        val a = DerivedCacheKey("archive", "v1", "cover")
        val b = DerivedCacheKey("archive", "v2", "cover")
        assertEquals("derived|7:archive|2:v1|5:cover", a.toString())
        kotlin.test.assertNotEquals(a.toString(), b.toString())
    }

    @Test fun leaseProtectsEntryDuringInvalidation() {
        val cache = MemoryDerivedCache<String>(); val key = DerivedCacheKey("a", "1")
        cache.put(key, "value")
        val lease = cache.acquire(key)
        cache.invalidate("a", "1")
        assertEquals("value", cache.get(key))
        lease.close(); cache.invalidate("a", "1")
        assertNull(cache.get(key))
    }

    @Test fun diskCacheRevisionIncludesUriMetadataAndEntry() {
        val base = LocalArtifactRevision("content://library/book.cbz", 10, 100, "001.jpg", "path-a")
        val changedSize = base.copy(size = 101)
        val changedEntry = base.copy(entry = "002.jpg")
        kotlin.test.assertNotEquals(base.value, changedSize.value)
        kotlin.test.assertNotEquals(base.value, changedEntry.value)
    }

    @Test fun diskCacheEvictsLeastRecentlyUsedArtifact() = runBlocking {
        val directory = Files.createTempDirectory("local-derived-cache-test").toFile()
        try {
            val cache = LocalDerivedCacheFacade(directory, maxBytes = 3)
            val old = LocalArtifactRevision("content://a", 1, 1)
            val current = LocalArtifactRevision("content://b", 1, 1)
            cache.put(old, byteArrayOf(1, 2))
            cache.put(current, byteArrayOf(3, 4))
            assertNull(cache.get(old))
            assertEquals(byteArrayOf(3, 4).toList(), cache.get(current)?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun diskCacheClearDoesNotDeleteLeasedArtifact() = runBlocking {
        val directory = Files.createTempDirectory("local-derived-cache-lease-test").toFile()
        try {
            val cache = LocalDerivedCacheFacade(directory, maxBytes = 16)
            val revision = LocalArtifactRevision("content://library/book.cbz", 1, 4, "001.jpg")
            cache.put(revision, byteArrayOf(1, 2, 3, 4), variant = "page")
            val lease = cache.acquire(revision, variant = "page")

            cache.clear()
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), cache.get(revision, "page")?.toList())

            lease.close()
            cache.clear()
            assertNull(cache.get(revision, "page"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
