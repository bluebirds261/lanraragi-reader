package com.lanraragi.reader.data.assets

import com.lanraragi.reader.data.diagnostics.DiagnosticProducers
import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Coordinates one thumbnail generation flow per archive.
 *
 * A revision changes only after a queued server job reaches [ThumbnailJobState.Finished]
 * and a subsequent probe returns a ready resource. This invariant makes a revision
 * suitable as an image-cache key while failures continue to expose the last good image.
 */
class ThumbnailRepository(
    private val gateway: ThumbnailGateway,
    private val scope: CoroutineScope,
    private val retryPolicy: ThumbnailRetryPolicy = ThumbnailRetryPolicy(),
    private val wait: suspend (Long) -> Unit = { delay(it) },
    /** Stable server/profile identity used to isolate in-memory state after a server switch. */
    private val serverKeyProvider: () -> String = { "" },
    private val maxConcurrentRequests: Int = 4,
    private val maxEntries: Int = 512,
    private val diagnostics: DiagnosticsFacade? = null,
) {
    private data class EntryKey(val serverKey: String, val arcid: String)

    private class Entry(val key: EntryKey) {
        val state = MutableStateFlow<CoverState>(CoverState.Unrequested())
        var running: Job? = null
        var generation: Long = 0L
        var leases: Int = 0
        var lastAccess: Long = 0L
    }

    init {
        require(maxConcurrentRequests > 0) { "maxConcurrentRequests must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val entries = ConcurrentHashMap<EntryKey, Entry>()
    private val entriesLock = Any()
    private val requestSlots = Semaphore(maxConcurrentRequests)
    private val accessSequence = AtomicLong()

    fun cover(arcid: String): StateFlow<CoverState> =
        entryFor(arcid).state.asStateFlow()

    /** Retains an entry while a UI surface is actively collecting it. */
    fun retainCover(arcid: String): StateFlow<CoverState> {
        return synchronized(entriesLock) {
            val entry = entryForLocked(arcid)
            synchronized(entry) {
                entry.leases += 1
                entry.lastAccess = accessSequence.incrementAndGet()
            }
            entry.state.asStateFlow()
        }
    }

    /** Releases the matching server/archive entry and prunes only idle LRU state. */
    fun releaseCover(arcid: String, serverKey: String = serverKeyProvider()) {
        val key = EntryKey(normalizeServerKey(serverKey), arcid)
        synchronized(entriesLock) {
            entries[key]?.let { entry ->
                synchronized(entry) {
                    entry.leases = (entry.leases - 1).coerceAtLeast(0)
                    entry.lastAccess = accessSequence.incrementAndGet()
                }
            }
            pruneEntriesLocked()
        }
    }

    /** Starts a request unless the same archive already has a request in flight. */
    fun requestCover(arcid: String) {
        startRequest(arcid, forceRevision = false)
    }

    /** Waits for the process-scoped request without transferring its ownership to the caller. */
    suspend fun awaitCover(arcid: String): CoverState {
        val (entry, job) = startRequest(arcid, forceRevision = false)
        job?.join()
        return entry.state.value
    }

    /**
     * Revalidates a cover after a successful server-side cover mutation.
     *
     * The revision changes only after a real image is observed. This keeps a
     * failed mutation from evicting a valid Coil cache entry.
     */
    fun refreshCover(arcid: String) {
        startRequest(arcid, forceRevision = true)
    }

    private fun startRequest(arcid: String, forceRevision: Boolean): Pair<Entry, Job?> {
        val entry = entryFor(arcid)
        val job = synchronized(entry) {
            if (forceRevision) {
                entry.generation += 1L
                entry.running?.cancel()
            } else if (entry.running?.isActive == true) {
                return@synchronized entry.running!!
            } else if (entry.state.value is CoverState.Ready) {
                diagnostics?.thumbnailCacheHit(hit = true)
                return@synchronized null
            }
            // Only a newly started request is a miss. Single-flight waiters above
            // reuse work already in progress and must not skew the hit rate.
            diagnostics?.thumbnailCacheHit(hit = false)
            val generation = entry.generation
            scope.launch {
                val startedAt = System.nanoTime()
                try {
                    requestSlots.withPermit {
                        refresh(arcid, entry, generation, forceRevision)
                    }
                    diagnostics?.let { facade ->
                        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                        when (val state = entry.state.value) {
                            is CoverState.Ready -> {
                                facade.thumbnailReady(elapsedMs)
                                DiagnosticProducers.thumbnail(facade).success("ready", elapsedMs)
                            }
                            is CoverState.Failed -> DiagnosticProducers.thumbnail(facade).event(
                                com.lanraragi.reader.data.diagnostics.DiagnosticLevel.WARN,
                                "failed",
                                mapOf("status" to (state.status?.toString() ?: "unknown")),
                            )
                            else -> Unit
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    diagnostics?.let { DiagnosticProducers.thumbnail(it).failure("request", error) }
                    throw error
                } finally {
                    synchronized(entry) {
                        if (entry.generation == generation) {
                            entry.running = null
                        }
                    }
                    pruneEntries()
                }
            }.also { entry.running = it }
        }
        return entry to job
    }

    private fun entryFor(arcid: String): Entry {
        return synchronized(entriesLock) { entryForLocked(arcid) }
    }

    private fun entryForLocked(arcid: String): Entry {
        val entry = entries.computeIfAbsent(currentKey(arcid)) { Entry(it) }
        synchronized(entry) {
            entry.lastAccess = accessSequence.incrementAndGet()
        }
        pruneEntriesLocked(entry)
        return entry
    }

    private fun currentKey(arcid: String): EntryKey =
        EntryKey(normalizeServerKey(runCatching { serverKeyProvider() }.getOrDefault("")), arcid)

    private suspend fun refresh(
        arcid: String,
        entry: Entry,
        generation: Long,
        forceRevision: Boolean,
    ) {
        if (!isCurrent(entry, generation)) return
        if (arcid.isBlank()) {
            publishFailure(entry, generation, "Archive id is required")
            return
        }

        val previous = entry.state.value.lastGood
        when (val probe = probeSafely(arcid)) {
            is ThumbnailProbe.Ready -> publishReady(
                entry,
                generation,
                probe.resource,
                (previous?.revision ?: 0L) + if (forceRevision && previous != null) 1L else 0L,
            )
            is ThumbnailProbe.Failed -> publishFailure(entry, generation, probe.message, probe.status)
            is ThumbnailProbe.Queued -> refreshQueued(arcid, entry, generation, probe.jobId, previous)
        }
    }

    private suspend fun refreshQueued(
        arcid: String,
        entry: Entry,
        generation: Long,
        jobId: String,
        previous: CoverState.Ready?,
    ) {
        if (jobId.isBlank()) {
            publishFailure(entry, generation, "Thumbnail generation returned an empty job id")
            return
        }
        var currentJobId = jobId
        var chain = 0
        while (chain <= retryPolicy.maxChainedJobs) {
            if (!isCurrent(entry, generation)) return
            publishGenerating(entry, generation, currentJobId, previous)

            var finished = false
            var lastTransientFailure: ThumbnailJobState.Failed? = null
            for (pollNumber in 0 until retryPolicy.maxPolls) {
                if (!isCurrent(entry, generation)) return
                wait(retryPolicy.delayMillis(pollNumber + 1))
                when (val job = jobSafely(currentJobId)) {
                    ThumbnailJobState.Active -> Unit
                    ThumbnailJobState.Finished -> {
                        finished = true
                        break
                    }
                    is ThumbnailJobState.Failed -> {
                        if (isRetryable(job.status) && pollNumber + 1 < retryPolicy.maxPolls) {
                            lastTransientFailure = job
                        } else {
                            publishFailure(entry, generation, job.message, job.status)
                            return
                        }
                    }
                }
            }
            if (!finished) {
                publishFailure(
                    entry,
                    generation,
                    lastTransientFailure?.message ?: "Thumbnail generation did not finish in time",
                    lastTransientFailure?.status,
                )
                return
            }

            when (val probe = probeSafely(arcid)) {
                is ThumbnailProbe.Ready -> {
                    if (isCurrent(entry, generation)) {
                        publishReady(entry, generation, probe.resource, (previous?.revision ?: 0L) + 1L)
                    }
                    return
                }
                is ThumbnailProbe.Failed -> {
                    publishFailure(entry, generation, probe.message, probe.status)
                    return
                }
                is ThumbnailProbe.Queued -> {
                    if (probe.jobId.isBlank()) {
                        publishFailure(entry, generation, "Thumbnail generation returned an empty job id")
                        return
                    }
                    currentJobId = probe.jobId
                    chain += 1
                }
            }
        }
        publishFailure(entry, generation, "Thumbnail generation was re-queued too many times")
    }

    private suspend fun probeSafely(arcid: String): ThumbnailProbe =
        try {
            gateway.probeCover(arcid)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ThumbnailProbe.Failed(message = "Unable to load thumbnail")
        }

    private suspend fun jobSafely(jobId: String): ThumbnailJobState =
        try {
            gateway.job(jobId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ThumbnailJobState.Failed(message = "Unable to check thumbnail generation")
        }

    private fun isCurrent(entry: Entry, generation: Long): Boolean =
        synchronized(entry) {
            entry.generation == generation &&
                entry.key.serverKey == normalizeServerKey(
                    runCatching { serverKeyProvider() }.getOrDefault(""),
                )
        }

    private fun isRetryable(status: Int?): Boolean =
        status == null || status == 408 || status == 425 || status == 429 || status in 500..599

    private fun pruneEntries(protected: Entry? = null) {
        synchronized(entriesLock) { pruneEntriesLocked(protected) }
    }

    private fun pruneEntriesLocked(protected: Entry? = null) {
        val overflow = entries.size - maxEntries
        if (overflow <= 0) return
        entries.values
            .asSequence()
            .mapNotNull { entry ->
                synchronized(entry) {
                    if (entry !== protected && entry.leases == 0 && entry.running?.isActive != true) {
                        entry to entry.lastAccess
                    } else {
                        null
                    }
                }
            }
            .sortedBy { (_, lastAccess) -> lastAccess }
            .take(overflow)
            .forEach { (entry, _) -> entries.remove(entry.key, entry) }
    }

    private fun publishGenerating(
        entry: Entry,
        generation: Long,
        jobId: String,
        previous: CoverState.Ready?,
    ) {
        if (!isCurrent(entry, generation)) return
        entry.state.value = CoverState.Generating(
            jobId,
            previous?.revision ?: 0L,
            previous,
        )
    }

    private fun publishReady(
        entry: Entry,
        generation: Long,
        resource: ThumbnailResource,
        revision: Long,
    ) {
        if (!isCurrent(entry, generation)) return
        entry.state.value = CoverState.Ready(resource, revision)
    }

    private fun publishFailure(entry: Entry, generation: Long, message: String, status: Int? = null) {
        if (!isCurrent(entry, generation)) return
        val current = entry.state.value
        entry.state.value = CoverState.Failed(
            message = message.ifBlank { "Thumbnail request failed" },
            status = status,
            revision = current.revision,
            lastGood = current.lastGood,
        )
    }
}
