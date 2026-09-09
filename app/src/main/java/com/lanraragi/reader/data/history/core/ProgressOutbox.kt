package com.lanraragi.reader.data.history.core

import com.lanraragi.reader.domain.model.ArchiveCapabilities
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.CancellationException

/** A pending server progress update. page is zero based at the app boundary. */
data class ProgressTask(
    val identity: ArchiveIdentity,
    val page: Int,
    val pageCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val sourceKey: String get() = identity.sourceKey
}

/** Durable task boundary. Production implementations can back this with Room; tests can use InMemoryTaskStore. */
interface ProgressTaskStore {
    suspend fun load(): List<ProgressTask>
    suspend fun upsert(task: ProgressTask)
    suspend fun remove(sourceKey: String)
}

class InMemoryProgressTaskStore(initial: Iterable<ProgressTask> = emptyList()) : ProgressTaskStore {
    private val values = LinkedHashMap<String, ProgressTask>()
    init { initial.forEach { values[it.sourceKey] = it } }
    override suspend fun load(): List<ProgressTask> = values.values.toList()
    override suspend fun upsert(task: ProgressTask) { values[task.sourceKey] = task }
    override suspend fun remove(sourceKey: String) { values.remove(sourceKey) }
}

/** Transport boundary. Implementations perform the API's zero-based to one-based conversion. */
fun interface ProgressTransport {
    suspend fun write(identity: ArchiveIdentity.Remote, page: Int, pageCount: Int)
}

data class ProgressFlushReport(
    val attempted: Int,
    val succeeded: Int,
    val retained: Int,
    val skipped: Int,
)

/**
 * Local-first progress writer and retry queue. Enqueue is idempotent per sourceKey and keeps
 * the maximum page, preventing rapid page changes from producing a request for every page.
 */
class ProgressOutbox(
    private val store: ProgressTaskStore,
    private val capability: (ArchiveIdentity) -> ArchiveCapabilities = ArchiveCapabilities.Companion::forIdentity,
) {
    suspend fun pending(): List<ProgressTask> = store.load()

    suspend fun enqueue(identity: ArchiveIdentity, page: Int, pageCount: Int = 0, at: Long = System.currentTimeMillis()): ProgressTask? {
        if (identity !is ArchiveIdentity.Remote || !canSync(identity)) return null
        val candidate = ProgressTask(identity, page.coerceAtLeast(0), pageCount.coerceAtLeast(0), at)
        val old = store.load().firstOrNull { it.sourceKey == candidate.sourceKey }
        val merged = if (old == null) candidate else candidate.copy(
            page = maxOf(old.page, candidate.page),
            pageCount = maxOf(old.pageCount, candidate.pageCount),
            updatedAt = maxOf(old.updatedAt, candidate.updatedAt),
        )
        store.upsert(merged)
        return merged
    }

    suspend fun mergeRemote(identity: ArchiveIdentity, localPage: Int, remotePage: Int, pageCount: Int = 0, at: Long = System.currentTimeMillis()): ProgressTask? =
        enqueue(identity, maxOf(localPage, remotePage), pageCount, at)

    suspend fun flush(transport: ProgressTransport): ProgressFlushReport {
        var attempted = 0; var succeeded = 0; var skipped = 0
        for (task in store.load()) {
            val remote = task.identity as? ArchiveIdentity.Remote
            if (remote == null || !canSync(task.identity)) { skipped++; continue }
            attempted++
            try {
                transport.write(remote, task.page, task.pageCount)
                store.remove(task.sourceKey)
                succeeded++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep failed tasks for the next retry.
            }
        }
        val retained = store.load().size
        return ProgressFlushReport(attempted, succeeded, retained, skipped)
    }

    private fun canSync(identity: ArchiveIdentity): Boolean = when (identity) {
        is ArchiveIdentity.Remote -> capability(identity).canRead
        else -> false
    }
}
