package com.lanraragi.reader.data.download

import com.lanraragi.reader.data.diagnostics.DiagnosticLevel
import com.lanraragi.reader.data.diagnostics.DiagnosticProducers
import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Room adapters implement this as transactional conditional updates. */
interface DownloadTaskStore {
    suspend fun loadAll(): List<DurableDownloadTask>
    fun observeAll(): Flow<List<DurableDownloadTask>> = flow { emit(loadAll()) }
    suspend fun insert(task: DurableDownloadTask)
    suspend fun replace(task: DurableDownloadTask)
    suspend fun delete(taskId: String) = Unit

    /** Returns null unless the persisted state was one of [expected]. */
    suspend fun transition(
        taskId: String,
        expected: Set<DurableDownloadState>,
        transform: (DurableDownloadTask) -> DurableDownloadTask,
    ): DurableDownloadTask?
}

fun interface DownloadClock {
    fun nowEpochMs(): Long
}

object SystemDownloadClock : DownloadClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

/** A runner owns the protocol-specific implementation for one serialized task shape. */
fun interface DownloadTaskRunner {
    suspend fun run(task: DurableDownloadTask, reporter: DownloadProgressReporter): DownloadRunResult
}

fun interface DownloadProgressReporter {
    suspend fun report(completedBytes: Long, totalBytes: Long?, entityTag: String?, lastModified: String?)
}

sealed interface DownloadRunResult {
    data class Completed(
        val completedBytes: Long,
        val totalBytes: Long?,
        val entityTag: String? = null,
        val lastModified: String? = null,
    ) : DownloadRunResult

    data class Retryable(val failure: DownloadFailure) : DownloadRunResult
    data class Failed(val failure: DownloadFailure) : DownloadRunResult
}

sealed interface DownloadFailure {
    val message: String

    data class Network(override val message: String) : DownloadFailure
    data class Http(val statusCode: Int, override val message: String, val retryAfterMs: Long? = null) : DownloadFailure
    data class Storage(override val message: String) : DownloadFailure
    data class SourceMissing(override val message: String = "Source no longer exists") : DownloadFailure
    data class Unknown(override val message: String) : DownloadFailure
}

/** Registry stays independent of DI: the Android layer may populate it from AppContainer. */
class DownloadRunnerRegistry(private val runners: Map<Class<out DownloadTaskSpec>, DownloadTaskRunner>) {
    @Suppress("UNCHECKED_CAST")
    fun runnerFor(spec: DownloadTaskSpec): DownloadTaskRunner? =
        runners.entries.firstOrNull { (kind, _) -> kind.isInstance(spec) }?.value

    class Builder {
        private val runners = linkedMapOf<Class<out DownloadTaskSpec>, DownloadTaskRunner>()

        fun <T : DownloadTaskSpec> register(type: Class<T>, runner: DownloadTaskRunner) = apply {
            check(type !in runners) { "Runner already registered for ${type.name}" }
            runners[type] = runner
        }

        fun build(): DownloadRunnerRegistry = DownloadRunnerRegistry(runners.toMap())
    }
}

/** Retry policy is intentionally conservative: credentials and a missing source require user action. */
class DownloadRetryPolicy(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 5 * 60_000L,
) {
    fun retryAt(nowEpochMs: Long, retryCount: Int, failure: DownloadFailure): Long? = when (failure) {
        is DownloadFailure.Network -> nowEpochMs + backoff(retryCount)
        is DownloadFailure.Http -> when (failure.statusCode) {
            429 -> nowEpochMs + (failure.retryAfterMs ?: backoff(retryCount))
            502, 503, 504 -> nowEpochMs + backoff(retryCount)
            else -> null
        }
        else -> null
    }

    private fun backoff(retryCount: Int): Long {
        val shifts = retryCount.coerceIn(0, 16)
        return (baseDelayMs * (1L shl shifts)).coerceAtMost(maxDelayMs)
    }
}

/**
 * Process-local coordinator over a durable store.  Its only in-memory facts are active jobs; all
 * recoverable state is persisted before a runner is invoked.
 */
class DownloadCoordinator(
    private val scope: CoroutineScope,
    private val store: DownloadTaskStore,
    private val registry: DownloadRunnerRegistry,
    private val clock: DownloadClock = SystemDownloadClock,
    private val retryPolicy: DownloadRetryPolicy = DownloadRetryPolicy(),
    maxConcurrent: Int = 2,
    private val diagnostics: DiagnosticsFacade? = null,
    private val onCompleted: suspend (DurableDownloadTask, DownloadRunResult.Completed) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private var retryWakeupJob: Job? = null
    private var maxConcurrent = maxConcurrent.coerceIn(1, 8)
    private val mutableTasks = MutableStateFlow<List<DurableDownloadTask>>(emptyList())
    val tasks: StateFlow<List<DurableDownloadTask>> = mutableTasks.asStateFlow()

    init {
        scope.launch { store.observeAll().collect { mutableTasks.value = it } }
    }

    suspend fun recover() {
        val now = clock.nowEpochMs()
        store.loadAll().filter { it.state == DurableDownloadState.RUNNING }.forEach { stale ->
            store.transition(stale.id, setOf(DurableDownloadState.RUNNING)) {
                it.copy(state = DurableDownloadState.WAITING, error = null, updatedAtEpochMs = now)
            }
        }
        dispatch()
        armRetryWakeup()
    }

    suspend fun enqueue(task: DurableDownloadTask) {
        store.insert(task)
        diagnostics?.let { DiagnosticProducers.download(it).event(DiagnosticLevel.INFO, "enqueued", mapOf("type" to task.spec::class.java.simpleName)) }
        dispatch()
        armRetryWakeup()
    }

    /** Decreasing this value never cancels an already-running task. */
    suspend fun setMaxConcurrent(value: Int) {
        maxConcurrent = value.coerceIn(1, 8)
        dispatch()
    }

    suspend fun pause(taskId: String) {
        val paused = store.transition(taskId, setOf(DurableDownloadState.WAITING, DurableDownloadState.RUNNING, DurableDownloadState.RETRYABLE)) {
            it.copy(state = DurableDownloadState.PAUSED, retryAtEpochMs = null, updatedAtEpochMs = clock.nowEpochMs())
        }
        if (paused != null) jobs[taskId]?.cancel()
        armRetryWakeup()
    }

    suspend fun resume(taskId: String) {
        store.transition(taskId, setOf(DurableDownloadState.PAUSED, DurableDownloadState.FAILED, DurableDownloadState.RETRYABLE)) {
            it.copy(state = DurableDownloadState.WAITING, retryAtEpochMs = null, error = null, updatedAtEpochMs = clock.nowEpochMs())
        }
        dispatch()
        armRetryWakeup()
    }

    suspend fun remove(taskId: String) {
        jobs[taskId]?.cancel()
        jobs.remove(taskId)
        store.delete(taskId)
        armRetryWakeup()
    }

    suspend fun clearFinished() {
        store.loadAll().filter { it.state == DurableDownloadState.DONE || it.state == DurableDownloadState.FAILED }
            .forEach { store.delete(it.id) }
    }

    /** Starts every eligible queued task up to the current capacity. */
    suspend fun dispatch() = mutex.withLock {
        val now = clock.nowEpochMs()
        val candidates = store.loadAll()
            .asSequence()
            .filter { it.state == DurableDownloadState.WAITING || (it.state == DurableDownloadState.RETRYABLE && (it.retryAtEpochMs ?: Long.MAX_VALUE) <= now) }
            .sortedWith(compareByDescending<DurableDownloadTask> { it.spec.priority }.thenBy { it.createdAtEpochMs })
            .toList()

        var capacity = (maxConcurrent - jobs.values.count { it.isActive }).coerceAtLeast(0)
        for (candidate in candidates) {
            if (capacity == 0) break
            if (jobs[candidate.id]?.isActive == true) continue
            val running = store.transition(candidate.id, setOf(DurableDownloadState.WAITING, DurableDownloadState.RETRYABLE)) {
                it.copy(state = DurableDownloadState.RUNNING, retryAtEpochMs = null, error = null, updatedAtEpochMs = now)
            } ?: continue
            capacity -= 1
            jobs[running.id] = scope.launch { execute(running) }
        }
    }

    /** Arms exactly one cancellable timer for the earliest persisted retry. */
    private suspend fun armRetryWakeup(): Unit = mutex.withLock {
        val now = clock.nowEpochMs()
        val next = store.loadAll().asSequence()
            .filter { it.state == DurableDownloadState.RETRYABLE && it.retryAtEpochMs != null }
            .mapNotNull { it.retryAtEpochMs }
            .minOrNull()
        val currentJob = coroutineContext[Job]
        if (retryWakeupJob !== currentJob) retryWakeupJob?.cancel()
        retryWakeupJob = next?.let { due ->
            scope.launch {
                delay((due - now).coerceAtLeast(0L))
                dispatch()
                armRetryWakeup()
            }
        }
    }

    private suspend fun execute(task: DurableDownloadTask) {
        val startedAt = System.nanoTime()
        try {
            val runner = registry.runnerFor(task.spec)
            val result = runner?.run(task, DownloadProgressReporter { completed, total, etag, modified ->
                store.transition(task.id, setOf(DurableDownloadState.RUNNING)) {
                    it.copy(completedBytes = completed, totalBytes = total, entityTag = etag, lastModified = modified, updatedAtEpochMs = clock.nowEpochMs())
                }
            }) ?: DownloadRunResult.Failed(DownloadFailure.Unknown("No runner registered for ${task.spec::class.java.simpleName}"))
            finish(task.id, result)
            if (result is DownloadRunResult.Completed) {
                try {
                    onCompleted(task, result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    diagnostics?.let { DiagnosticProducers.download(it).failure("catalog", error) }
                }
            }
            diagnostics?.let { facade ->
                val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
                when (result) {
                    is DownloadRunResult.Completed -> {
                        facade.downloadThroughput(result.completedBytes * 1_000.0 / elapsedMs)
                        DiagnosticProducers.download(facade).success(
                            "completed",
                            elapsedMs,
                            mapOf("bytes" to result.completedBytes.toString()),
                        )
                    }
                    is DownloadRunResult.Retryable -> {
                        facade.downloadRetry()
                        DiagnosticProducers.download(facade).event(DiagnosticLevel.WARN, "retryable")
                    }
                    is DownloadRunResult.Failed -> DiagnosticProducers.download(facade).event(DiagnosticLevel.ERROR, "failed")
                }
            }
        } catch (cancelled: CancellationException) {
            // A pause/delete owns the persisted state. Never turn cancellation into a retry.
            throw cancelled
        } catch (network: IOException) {
            diagnostics?.let {
                it.downloadRetry()
                DiagnosticProducers.download(it).failure("network", network)
            }
            finish(task.id, DownloadRunResult.Retryable(DownloadFailure.Network(network.message ?: "Network interrupted")))
        } catch (error: Exception) {
            diagnostics?.let { DiagnosticProducers.download(it).failure("runner", error) }
            finish(task.id, DownloadRunResult.Failed(DownloadFailure.Unknown(error.message ?: error.javaClass.simpleName)))
        } finally {
            jobs.remove(task.id)
            dispatch()
            armRetryWakeup()
        }
    }

    private suspend fun finish(taskId: String, result: DownloadRunResult) {
        val now = clock.nowEpochMs()
        store.transition(taskId, setOf(DurableDownloadState.RUNNING)) { current ->
            when (result) {
                is DownloadRunResult.Completed -> current.copy(
                    state = DurableDownloadState.DONE,
                    completedBytes = result.completedBytes,
                    totalBytes = result.totalBytes ?: current.totalBytes,
                    entityTag = result.entityTag ?: current.entityTag,
                    lastModified = result.lastModified ?: current.lastModified,
                    error = null,
                    updatedAtEpochMs = now,
                )
                is DownloadRunResult.Retryable -> retryOrFail(current, result.failure, now)
                is DownloadRunResult.Failed -> current.copy(state = DurableDownloadState.FAILED, error = result.failure.message, updatedAtEpochMs = now)
            }
        }
    }

    private fun retryOrFail(task: DurableDownloadTask, failure: DownloadFailure, now: Long): DurableDownloadTask {
        val retryAt = retryPolicy.retryAt(now, task.retryCount, failure)
        return if (retryAt == null) {
            task.copy(state = DurableDownloadState.FAILED, error = failure.message, updatedAtEpochMs = now)
        } else {
            task.copy(state = DurableDownloadState.RETRYABLE, retryCount = task.retryCount + 1, retryAtEpochMs = retryAt, error = failure.message, updatedAtEpochMs = now)
        }
    }
}
