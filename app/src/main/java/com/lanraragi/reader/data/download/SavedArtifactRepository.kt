package com.lanraragi.reader.data.download

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** Durable resource record. `artifactKey` is content/source identity, not a UI task id. */
data class SavedArtifact(
    val artifactKey: String,
    val source: DownloadSourceIdentity,
    val path: String,
    val revision: String? = null,
    val byteSize: Long = 0L,
    val pinned: Boolean = false,
    val lastAccessAtEpochMs: Long = 0L,
    val leaseCount: Int = 0,
)

interface SavedArtifactStore {
    suspend fun loadAll(): List<SavedArtifact>
    suspend fun upsert(artifact: SavedArtifact)
    suspend fun delete(artifactKey: String)
}

sealed interface EvictionResult {
    data class Removed(val artifacts: List<SavedArtifact>, val bytesFreed: Long) : EvictionResult
    data class CapacityBlocked(val requiredBytes: Long, val availableBytes: Long, val protectedArtifacts: List<SavedArtifact>) : EvictionResult
}

/**
 * Deduplicates saved archive/cache tasks and enforces pin/lease-aware LRU eviction.  Leases are
 * reference counted and represented by a closeable handle to make release reliable on exceptions.
 */
class SavedArtifactRepository(
    private val store: SavedArtifactStore,
    private val clock: DownloadClock = SystemDownloadClock,
) {
    private val mutex = Mutex()
    private val leases = ConcurrentHashMap<String, Int>()

    suspend fun find(artifactKey: String): SavedArtifact? = store.loadAll().firstOrNull { it.artifactKey == artifactKey }

    suspend fun findBySource(source: DownloadSourceIdentity): SavedArtifact? {
        val rows = store.loadAll().filter { it.source.archiveId.equals(source.archiveId, ignoreCase = true) }
        return rows.filter { it.source.sameArchive(source) }.maxByOrNull { it.lastAccessAtEpochMs }
            ?: rows.singleOrNull { it.source.serverScope.isNullOrBlank() }
    }

    suspend fun put(artifact: SavedArtifact) = mutex.withLock {
        val canonical = artifact.copy(
            artifactKey = SavedArtifactIdentity.key(artifact.source),
            leaseCount = 0,
            lastAccessAtEpochMs = clock.nowEpochMs(),
        )
        store.loadAll()
            .filter { it.artifactKey != canonical.artifactKey && it.source.sameArchive(canonical.source) }
            .forEach { store.delete(it.artifactKey) }
        store.upsert(canonical)
    }

    suspend fun acquire(artifactKey: String): ArtifactLease? = mutex.withLock {
        val artifact = store.loadAll().firstOrNull { it.artifactKey == artifactKey } ?: return@withLock null
        val count = (leases[artifactKey] ?: 0) + 1
        leases[artifactKey] = count
        // Leases are process-owned and intentionally never persisted; process death releases them.
        store.upsert(artifact.copy(leaseCount = 0, lastAccessAtEpochMs = clock.nowEpochMs()))
        ArtifactLease { releaseLease(artifactKey) }
    }

    suspend fun pin(artifactKey: String, pinned: Boolean): Boolean = mutex.withLock {
        val artifact = store.loadAll().firstOrNull { it.artifactKey == artifactKey } ?: return@withLock false
        store.upsert(artifact.copy(pinned = pinned, lastAccessAtEpochMs = clock.nowEpochMs()))
        true
    }

    suspend fun remove(artifactKey: String) = mutex.withLock {
        leases.remove(artifactKey)
        store.delete(artifactKey)
    }

    suspend fun evictToCapacity(
        maxBytes: Long,
        includedArtifactKeys: Set<String>? = null,
    ): EvictionResult = mutex.withLock {
        require(maxBytes >= 0L)
        val all = store.loadAll()
        val scoped = if (includedArtifactKeys == null) all else all.filter { it.artifactKey in includedArtifactKeys }
        var total = scoped.sumOf { it.byteSize }
        if (total <= maxBytes) return@withLock EvictionResult.Removed(emptyList(), 0L)
        val candidates = scoped.filter { !it.pinned && (leases[it.artifactKey] ?: 0) == 0 }
            .sortedBy { it.lastAccessAtEpochMs }
        val removed = mutableListOf<SavedArtifact>()
        for (candidate in candidates) {
            if (total <= maxBytes) break
            store.delete(candidate.artifactKey)
            removed += candidate
            total -= candidate.byteSize
        }
        if (total <= maxBytes) EvictionResult.Removed(removed, removed.sumOf { it.byteSize })
        else EvictionResult.CapacityBlocked(maxBytes.coerceAtLeast(0L), total, scoped.filter { it !in removed })
    }

    private fun releaseLease(artifactKey: String) {
        leases.computeIfPresent(artifactKey) { _, count ->
            (count - 1).takeIf { it > 0 }
        }
    }
}

object SavedArtifactIdentity {
    fun key(source: DownloadSourceIdentity): String {
        val scope = source.serverScope?.trim()?.trimEnd('/').orEmpty()
        val scopeHash = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return "archive:$scopeHash:${source.archiveId.trim().lowercase()}"
    }
}

private fun DownloadSourceIdentity.sameArchive(other: DownloadSourceIdentity): Boolean =
    archiveId.equals(other.archiveId, ignoreCase = true) &&
        serverScope?.trim()?.trimEnd('/').orEmpty().equals(
            other.serverScope?.trim()?.trimEnd('/').orEmpty(),
            ignoreCase = true,
        )

fun interface ArtifactLease : AutoCloseable {
    override fun close()
}

/** Atomic destination writer used by archive/page runners. */
object AtomicDownloadFile {
    fun partPath(destination: java.io.File): java.io.File = java.io.File(destination.path + ".part")

    /** A 206 response may append; a 200 response always replaces the partial file. */
    fun openForResponse(destination: java.io.File, statusCode: Int): java.io.OutputStream {
        require(statusCode == 200 || statusCode == 206) { "Unexpected download response: $statusCode" }
        destination.parentFile?.mkdirs()
        val partial = partPath(destination)
        return java.io.FileOutputStream(partial, statusCode == 206 && partial.exists())
    }

    fun complete(destination: java.io.File) {
        val partial = partPath(destination)
        require(partial.exists()) { "Missing partial file" }
        if (destination.exists() && !destination.delete()) error("Unable to replace destination")
        check(partial.renameTo(destination)) { "Unable to atomically publish download" }
    }
}
