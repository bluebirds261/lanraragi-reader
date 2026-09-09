package com.lanraragi.reader.data.download

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SavedArtifactRepositoryTest {
    @Test
    fun leaseBlocksEvictionUntilReaderReleasesArtifact() = runTest {
        val store = MemorySavedArtifactStore()
        val repository = SavedArtifactRepository(store, DownloadClock { 10L })
        val artifact = SavedArtifact(
            artifactKey = "offline:arc-1",
            source = DownloadSourceIdentity("arc-1", "server"),
            path = "offline/arc-1/original.archive",
            byteSize = 100L,
        )
        repository.put(artifact)
        val canonicalKey = SavedArtifactIdentity.key(artifact.source)
        val lease = requireNotNull(repository.acquire(canonicalKey))

        assertTrue(repository.evictToCapacity(0) is EvictionResult.CapacityBlocked)
        assertEquals(SavedArtifactIdentity.key(artifact.source), store.rows.single().artifactKey)

        lease.close()
        val evicted = repository.evictToCapacity(0)
        assertTrue(evicted is EvictionResult.Removed)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun putDeduplicatesSameServerArchiveToCanonicalIdentity() = runTest {
        val store = MemorySavedArtifactStore().apply {
            rows += SavedArtifact("legacy-one", DownloadSourceIdentity("ARC", "https://server"), "old")
            rows += SavedArtifact("other-server", DownloadSourceIdentity("ARC", "https://other"), "other")
        }
        val repository = SavedArtifactRepository(store)
        val current = SavedArtifact("temporary", DownloadSourceIdentity("arc", "https://server/"), "current")

        repository.put(current)

        assertEquals(2, store.rows.size)
        assertTrue(store.rows.any { it.artifactKey == SavedArtifactIdentity.key(current.source) && it.path == "current" })
        assertTrue(store.rows.any { it.artifactKey == "other-server" })
    }

    @Test
    fun exporterPublishesLocalCopyWithoutChangingCanonicalSource() = runTest {
        val directory = Files.createTempDirectory("saved-artifact-export").toFile()
        try {
            val source = directory.resolve("source.archive").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val target = directory.resolve("export.archive")
            val artifact = SavedArtifact(
                "key",
                DownloadSourceIdentity("arc", "server"),
                source.absolutePath,
                byteSize = source.length(),
            )

            SavedArtifactExporter().export(artifact, DownloadDestination(target.absolutePath))

            assertEquals(listOf<Byte>(1, 2, 3), target.readBytes().toList())
            assertEquals(listOf<Byte>(1, 2, 3), source.readBytes().toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    private class MemorySavedArtifactStore : SavedArtifactStore {
        val rows = mutableListOf<SavedArtifact>()

        override suspend fun loadAll(): List<SavedArtifact> = rows.toList()

        override suspend fun upsert(artifact: SavedArtifact) {
            rows.removeAll { it.artifactKey == artifact.artifactKey }
            rows += artifact
        }

        override suspend fun delete(artifactKey: String) {
            rows.removeAll { it.artifactKey == artifactKey }
        }
    }
}
