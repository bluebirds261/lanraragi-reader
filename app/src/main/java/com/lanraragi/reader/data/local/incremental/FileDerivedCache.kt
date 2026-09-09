package com.lanraragi.reader.data.local.incremental

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/** Stable revision for a local document/entry; changing any field invalidates the artifact. */
data class LocalArtifactRevision(
    val uri: String,
    val lastModified: Long,
    val size: Long,
    val entry: String? = null,
    val pathRevision: String = "0",
) {
    val value: String = listOf(uri, lastModified, size, entry.orEmpty(), pathRevision)
        .joinToString("|")
}

/** Production facade for local cover/page artifacts backed by an atomic, bounded disk LRU. */
class LocalDerivedCacheFacade(
    directory: File,
    private val maxBytes: Long = 64L * 1024L * 1024L,
) {
    private val root = directory.apply { mkdirs() }
    private val lock = Any()
    private val leases = mutableMapOf<String, Int>()
    private val sizes = LinkedHashMap<String, Long>(16, .75f, true)
    private var bytes = 0L
    private val generation = AtomicLong()

    init {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        root.listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.name.endsWith(".bin") }
            .sortedBy(File::lastModified)
            .forEach { sizes[it.name] = it.length(); bytes += it.length() }
        synchronized(lock) { evictLocked() }
    }

    suspend fun get(key: LocalArtifactRevision, variant: String = "default"): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(key, variant)
        retain(file.name)
        val value = try {
            try {
                if (file.exists()) FileInputStream(file).use { it.readBytes() } else null
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        } finally {
            release(file.name)
        }
        if (value != null) synchronized(lock) { touch(file.name, value.size.toLong()) }
        value
    }

    suspend fun put(key: LocalArtifactRevision, value: ByteArray, variant: String = "default") = withContext(Dispatchers.IO) {
        if (value.size.toLong() > maxBytes) return@withContext
        val file = fileFor(key, variant)
        val tmp = File(root, ".${file.name}.tmp-${System.nanoTime()}")
        try {
            FileOutputStream(tmp).use { stream -> stream.write(value); stream.fd.sync() }
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { tmp.delete() }
        synchronized(lock) {
            val old = sizes.put(file.name, value.size.toLong()) ?: 0L
            bytes += value.size - old
            evictLocked()
        }
    }

    suspend fun invalidate(key: LocalArtifactRevision, variant: String? = null) = withContext(Dispatchers.IO) {
        val prefix = digest(key.value + "|")
        val files = root.listFiles().orEmpty().filter { it.name.startsWith(prefix) && (variant == null || it.name.endsWith("-${digest(variant)}.bin")) }
        synchronized(lock) { files.filter { (leases[it.name] ?: 0) == 0 }.forEach { removeLocked(it) }; generation.incrementAndGet() }
    }

    suspend fun acquire(key: LocalArtifactRevision, variant: String = "default"): AutoCloseable = withContext(Dispatchers.IO) {
        val name = fileFor(key, variant).name
        retain(name)
        AutoCloseable { release(name) }
    }

    /** Removes idle artifacts while allowing an in-flight reader to finish from its lease. */
    fun clear() {
        synchronized(lock) {
            root.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".bin") && (leases[it.name] ?: 0) == 0 }
                .forEach(::removeLocked)
            generation.incrementAndGet()
        }
    }

    fun generation(): Long = generation.get()
    fun currentBytes(): Long = synchronized(lock) { bytes }

    private fun fileFor(key: LocalArtifactRevision, variant: String): File =
        File(root, "${digest(key.value)}-${digest(variant)}.bin")

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun touch(name: String, size: Long) {
        if (!sizes.containsKey(name)) { sizes[name] = size; bytes += size }
        sizes[name] = size
    }

    private fun retain(name: String) = synchronized(lock) {
        leases[name] = (leases[name] ?: 0) + 1
    }

    private fun release(name: String) = synchronized(lock) {
        val remaining = ((leases[name] ?: 1) - 1).coerceAtLeast(0)
        if (remaining == 0) leases.remove(name) else leases[name] = remaining
        evictLocked()
    }

    private fun evictLocked() {
        val it = sizes.entries.iterator()
        while (bytes > maxBytes && it.hasNext()) {
            val entry = it.next()
            if ((leases[entry.key] ?: 0) > 0) continue
            File(root, entry.key).delete(); bytes -= entry.value; it.remove()
        }
    }

    private fun removeLocked(file: File) {
        bytes -= sizes.remove(file.name) ?: file.length()
        file.delete()
    }
}

class LocalCoverDerivedCache(private val cache: LocalDerivedCacheFacade) {
    suspend fun get(revision: LocalArtifactRevision): ByteArray? = cache.get(revision, variant = "cover")
    suspend fun put(revision: LocalArtifactRevision, bytes: ByteArray) = cache.put(revision, bytes, variant = "cover")
}

class LocalArchivePageDerivedCache(private val cache: LocalDerivedCacheFacade) {
    suspend fun get(revision: LocalArtifactRevision): ByteArray? = cache.get(revision, variant = "page")
    suspend fun put(revision: LocalArtifactRevision, bytes: ByteArray) = cache.put(revision, bytes, variant = "page")
}
