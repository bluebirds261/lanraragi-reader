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

/** 下载失败分类（C3/C6）：供 UI 显示中文标签；存储不可用归入 [STORAGE]。 */
enum class DownloadFailureCategory(val label: String) {
    TIMEOUT("连接超时"),
    SERVER("服务器错误"),
    NETWORK("网络错误"),
    STORAGE("存储不可用"),
    OTHER("其他错误"),
}

/**
 * 从持久化的错误消息反推失败分类（C3）：durable 层只保留 message 字符串，
 * UI 用与 [retryOrFail] 一致的消息特征做分类兜底。
 */
fun downloadFailureCategoryFromMessage(message: String?): DownloadFailureCategory {
    if (message.isNullOrBlank()) return DownloadFailureCategory.OTHER
    val m = message.lowercase()
    return when {
        STORAGE_MESSAGE_PATTERNS.any(m::contains) -> DownloadFailureCategory.STORAGE
        m.contains("timeout") || m.contains("timed out") || m.contains("408") || m.contains("超时") ->
            DownloadFailureCategory.TIMEOUT
        Regex("(^|[^0-9])(5[0-9]{2}|429)([^0-9]|$)").containsMatchIn(m) -> DownloadFailureCategory.SERVER
        m.contains("network") || m.contains("interrupt") || m.contains("connect") ||
            m.contains("socket") || m.contains("unreachable") || m.contains("网络") ->
            DownloadFailureCategory.NETWORK
        else -> DownloadFailureCategory.OTHER
    }
}

/** 把结构化失败映射到 UI 分类。 */
fun DownloadFailure.category(): DownloadFailureCategory = when (this) {
    is DownloadFailure.Network -> DownloadFailureCategory.NETWORK
    is DownloadFailure.Http -> when {
        statusCode == 408 -> DownloadFailureCategory.TIMEOUT
        statusCode >= 500 || statusCode == 429 -> DownloadFailureCategory.SERVER
        else -> DownloadFailureCategory.OTHER
    }
    is DownloadFailure.Storage -> DownloadFailureCategory.STORAGE
    is DownloadFailure.SourceMissing -> DownloadFailureCategory.OTHER
    is DownloadFailure.Unknown -> DownloadFailureCategory.OTHER
}

private val STORAGE_MESSAGE_PATTERNS = listOf(
    "enospc", "no space left",
    "eacces", "eperm", "permission denied", "permissions",
    "erofs", "read-only",
    "eio", "i/o error",
    "storage", "saf",
    "disk",
)

/** 存储类 IO/安全异常识别：SAF 授权被回收（SecurityException）、介质移除（文件丢失）、磁盘满等。 */
fun isStorageFailure(error: Throwable): Boolean = when (error) {
    is SecurityException -> true
    is java.io.FileNotFoundException -> true // 介质移除后文件与目录直接消失
    is IOException -> {
        val message = (error.message ?: "").lowercase()
        STORAGE_MESSAGE_PATTERNS.any(message::contains)
    }
    else -> false
}

private fun storageFailureMessage(error: Throwable): String =
    error.message?.takeIf(String::isNotBlank)
        ?: "存储不可用（${error.javaClass.simpleName}）"

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

/**
 * Retry policy is intentionally conservative: credentials and a missing source require user action.
 *
 * C3 要求「重试上限可配（JHenTai：5 次）」：超过 [maxRetries] 次后不再排重试，
 * 任务落到 FAILED 由用户手动重试，避免永久失败的任务无限弹跳、持续占用队列与网络。
 *
 * 上限在运行期可由 [DownloadCoordinator.setMaxRetries] 按设置单一来源更新（无需重建协调器）：
 * [maxRetries] 是单个 Int，读取发生在下载线程，故用 `@Volatile` 保证可见性；
 * 构造参数与 [DownloadCoordinator.setMaxRetries] 都在写入前 coerceIn 到
 * [MIN_DOWNLOAD_MAX_RETRIES]..[MAX_DOWNLOAD_MAX_RETRIES]，因此不存在「策略半更新」的中间态。
 */
class DownloadRetryPolicy(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 5 * 60_000L,
    initialMaxRetries: Int = DEFAULT_DOWNLOAD_MAX_RETRIES,
) {
    /** C3 自动重试上限（默认 5，可配区间 1..10）；调小只影响之后的判定，不回溯取消已排队的那次重试。 */
    @Volatile
    var maxRetries: Int = initialMaxRetries.coerceIn(MIN_DOWNLOAD_MAX_RETRIES, MAX_DOWNLOAD_MAX_RETRIES)

    fun retryAt(nowEpochMs: Long, retryCount: Int, failure: DownloadFailure): Long? {
        if (retryCount >= maxRetries) return null
        return when (failure) {
            is DownloadFailure.Network -> nowEpochMs + backoff(retryCount)
            is DownloadFailure.Http -> when (failure.statusCode) {
                429 -> nowEpochMs + (failure.retryAfterMs ?: backoff(retryCount))
                502, 503, 504 -> nowEpochMs + backoff(retryCount)
                else -> null
            }
            else -> null
        }
    }

    private fun backoff(retryCount: Int): Long {
        val shifts = retryCount.coerceIn(0, 16)
        return (baseDelayMs * (1L shl shifts)).coerceAtMost(maxDelayMs)
    }
}

/** C3 默认重试上限（与 JHenTai 对齐）。 */
const val DEFAULT_DOWNLOAD_MAX_RETRIES = 5

/** C3 重试上限的可配区间：设置读写、导入导出与 UI 都按此区间收敛。 */
const val MIN_DOWNLOAD_MAX_RETRIES = 1
const val MAX_DOWNLOAD_MAX_RETRIES = 10

/** C1 速率限制的边界：每秒最多启动的任务数，1..10（与并发上限叠加生效）。 */
const val DEFAULT_DOWNLOAD_RATE_PER_SECOND = 2
const val MAX_DOWNLOAD_RATE_PER_SECOND = 10

private const val RATE_WINDOW_MS = 1_000L

/** 返回带新优先级的任务描述副本（spec 为不可变 data class，优先级更新必须整体替换）。 */
private fun DownloadTaskSpec.withPriority(priority: Int): DownloadTaskSpec = when (this) {
    is DownloadTaskSpec.Archive -> copy(priority = priority)
    is DownloadTaskSpec.Cache -> copy(priority = priority)
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
    ratePerSecond: Int = DEFAULT_DOWNLOAD_RATE_PER_SECOND,
    private val diagnostics: DiagnosticsFacade? = null,
    private val onCompleted: suspend (DurableDownloadTask, DownloadRunResult.Completed) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private var retryWakeupJob: Job? = null
    private var maxConcurrent = maxConcurrent.coerceIn(1, 8)

    /** C1 每秒任务启动速率上限；滑动 1s 窗口内的启动数达到上限时暂停派发。 */
    private var ratePerSecond = ratePerSecond.coerceIn(1, MAX_DOWNLOAD_RATE_PER_SECOND)

    /** 滑动窗口内的任务启动时刻（mutex 保护，仅 dispatch 内读写）。 */
    private val recentTaskStarts = ArrayDeque<Long>()
    private var rateWakeupJob: Job? = null

    private val mutableTasks = MutableStateFlow<List<DurableDownloadTask>>(emptyList())
    val tasks: StateFlow<List<DurableDownloadTask>> = mutableTasks.asStateFlow()

    private val mutablePauseNotice = MutableStateFlow<String?>(null)

    /**
     * C3「特定错误自动暂停整队」的中文提示（401/403）：`null` = 无待展示提示。
     * 同一轮自动暂停只发布一次（已有未关闭的提示时不覆盖，并发 401/403 共享同一条）；
     * UI 渲染后调用 [dismissPauseNotice] 清除，之后新的认证失败才会再次提示。
     */
    val pauseNotice: StateFlow<String?> = mutablePauseNotice.asStateFlow()

    fun dismissPauseNotice() {
        mutablePauseNotice.value = null
    }

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

    /** C1 调整每秒任务启动速率上限（1..10）；调整后立即按新速率尝试派发。 */
    suspend fun setRatePerSecond(value: Int) {
        ratePerSecond = value.coerceIn(1, MAX_DOWNLOAD_RATE_PER_SECOND)
        dispatch()
    }

    /**
     * C3 调整自动重试上限（1..10）：由设置单一来源在运行期推送，无需重建协调器。
     * 只影响之后的判定——重试等待中的任务若重试次数已达新上限，重新运行失败后直接落 FAILED
     * （不回溯取消已排队的那次重试，避免设置改动静默丢弃任务状态）。
     */
    suspend fun setMaxRetries(value: Int) {
        retryPolicy.maxRetries = value.coerceIn(MIN_DOWNLOAD_MAX_RETRIES, MAX_DOWNLOAD_MAX_RETRIES)
    }

    /** 更新任务优先级（高/中/低，见 DownloadTaskSpec.priority）；等待中的任务按新优先级参与下一轮派发。 */
    suspend fun setPriority(taskId: String, priority: Int) {
        store.transition(
            taskId,
            setOf(
                DurableDownloadState.WAITING,
                DurableDownloadState.RUNNING,
                DurableDownloadState.PAUSED,
                DurableDownloadState.RETRYABLE,
                DurableDownloadState.FAILED,
            ),
        ) {
            it.copy(spec = it.spec.withPriority(priority), updatedAtEpochMs = clock.nowEpochMs())
        }
        dispatch()
        armRetryWakeup()
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

    /**
     * 暂停整队：与下载页「暂停全部」及 C3 认证失败自动暂停共用同一条路径——逐任务走 [pause]，
     * 把等待中 / 重试等待中 / 进行中的任务统一转为 PAUSED（进行中的任务协程被取消，
     * 由 [execute] 的 CancellationException 分支承接，不产生重试）。
     * DONE 与 FAILED 不被改写：失败任务保留错误原因，供用户排障后手动重试。
     */
    suspend fun pauseAll() {
        pauseAllInternal()
    }

    /** 逐一暂停可暂停任务，返回本轮筛选出的任务数（实际生效数由 [pause] 的条件更新决定，故为上限）。 */
    private suspend fun pauseAllInternal(): Int {
        val pausable = store.loadAll().filter {
            it.state == DurableDownloadState.WAITING ||
                it.state == DurableDownloadState.RUNNING ||
                it.state == DurableDownloadState.RETRYABLE
        }
        pausable.forEach { pause(it.id) }
        return pausable.size
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

    /** Starts every eligible queued task up to the current capacity and rate limit. */
    suspend fun dispatch() = mutex.withLock {
        val now = clock.nowEpochMs()
        val candidates = store.loadAll()
            .asSequence()
            .filter { it.state == DurableDownloadState.WAITING || (it.state == DurableDownloadState.RETRYABLE && (it.retryAtEpochMs ?: Long.MAX_VALUE) <= now) }
            .sortedWith(compareByDescending<DurableDownloadTask> { it.spec.priority }.thenBy { it.createdAtEpochMs })
            .toList()

        pruneRecentStarts(now)
        var capacity = (maxConcurrent - jobs.values.count { it.isActive }).coerceAtLeast(0)
        for (candidate in candidates) {
            if (capacity == 0) break
            if (jobs[candidate.id]?.isActive == true) continue
            // C1 速率限制：滑动 1s 窗口内启动数达到上限则本轮停止派发，
            // 并安排窗口滑动后的自动重派（与并发上限叠加生效）。
            if (recentTaskStarts.size >= ratePerSecond) {
                scheduleRateWakeupLocked((recentTaskStarts.first() + RATE_WINDOW_MS - now).coerceAtLeast(1L))
                break
            }
            val running = store.transition(candidate.id, setOf(DurableDownloadState.WAITING, DurableDownloadState.RETRYABLE)) {
                it.copy(state = DurableDownloadState.RUNNING, retryAtEpochMs = null, error = null, updatedAtEpochMs = now)
            } ?: continue
            capacity -= 1
            recentTaskStarts.addLast(now)
            jobs[running.id] = scope.launch { execute(running) }
        }
    }

    private fun pruneRecentStarts(now: Long) {
        while (recentTaskStarts.isNotEmpty() && recentTaskStarts.first() + RATE_WINDOW_MS <= now) {
            recentTaskStarts.removeFirst()
        }
    }

    /** 仅在持有 [mutex] 时调用：安排窗口滑动后的一次重派；已有未触发定时则复用（最早触发者胜出）。 */
    private fun scheduleRateWakeupLocked(delayMs: Long) {
        if (rateWakeupJob?.isActive == true) return
        rateWakeupJob = scope.launch {
            delay(delayMs)
            dispatch()
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
            if (isStorageFailure(network)) {
                // C6 运行时兜底：授权被回收 / 介质移除 / 磁盘满 → 存储不可用，不自动重试。
                finish(task.id, DownloadRunResult.Failed(DownloadFailure.Storage(storageFailureMessage(network))))
            } else {
                finish(task.id, DownloadRunResult.Retryable(DownloadFailure.Network(network.message ?: "Network interrupted")))
            }
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
        val finished = store.transition(taskId, setOf(DurableDownloadState.RUNNING)) { current ->
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
        // C3「特定错误自动暂停整队」：仅当任务确实落到 FAILED（未被用户暂停/删除覆盖）且失败是 401/403 时，
        // 暂停整队并提示一次。429 / 5xx 仍是单任务退避重试，网络/存储失败与凭据无关，都不触发整队暂停。
        if (finished?.state == DurableDownloadState.FAILED) {
            authenticationFailure(result)?.let { pauseAllForAuthentication(it) }
        }
    }

    /** 只有 HTTP 401/403 属于凭据/权限失败，其余失败（含 429、5xx）返回 `null`。 */
    private fun authenticationFailure(result: DownloadRunResult): DownloadFailure.Http? {
        val failure = when (result) {
            is DownloadRunResult.Completed -> return null
            is DownloadRunResult.Retryable -> result.failure
            is DownloadRunResult.Failed -> result.failure
        }
        return (failure as? DownloadFailure.Http)?.takeIf { it.statusCode == 401 || it.statusCode == 403 }
    }

    /**
     * 认证失败后暂停整队：整队继续只会对同一服务器重复发送注定失败的请求。
     * 提示文本仅在当前没有未关闭提示时发布（见 [pauseNotice]），保证同一轮暂停事件只提示一次。
     */
    private suspend fun pauseAllForAuthentication(failure: DownloadFailure.Http) {
        val pausedCount = pauseAllInternal()
        if (mutablePauseNotice.value == null) {
            val reason = if (failure.statusCode == 403) {
                "服务器拒绝访问（HTTP 403）"
            } else {
                "服务器要求重新登录（HTTP 401）"
            }
            val scopeNote = if (pausedCount > 0) "（$pausedCount 个任务）" else "（当前没有其他排队任务）"
            mutablePauseNotice.value = "$reason，已自动暂停整队$scopeNote；" +
                "整队继续只会重复同样的失败，请检查服务器地址与 API Key，修正后手动恢复下载。"
        }
        diagnostics?.let {
            DiagnosticProducers.download(it).event(
                DiagnosticLevel.WARN,
                "auto-paused",
                mapOf("status" to failure.statusCode.toString(), "paused" to pausedCount.toString()),
            )
        }
    }

    private fun retryOrFail(task: DurableDownloadTask, failure: DownloadFailure, now: Long): DurableDownloadTask {
        // Runner 可能把磁盘 IO 错误包装成可重试的网络失败；按消息特征归回存储不可用（C6）。
        val effective = if (failure is DownloadFailure.Network || failure is DownloadFailure.Unknown) {
            val message = failure.message.lowercase()
            if (STORAGE_MESSAGE_PATTERNS.any(message::contains)) DownloadFailure.Storage(failure.message) else failure
        } else failure
        val retryAt = retryPolicy.retryAt(now, task.retryCount, effective)
        return if (retryAt == null) {
            task.copy(state = DurableDownloadState.FAILED, error = effective.message, updatedAtEpochMs = now)
        } else {
            task.copy(state = DurableDownloadState.RETRYABLE, retryCount = task.retryCount + 1, retryAtEpochMs = retryAt, error = effective.message, updatedAtEpochMs = now)
        }
    }
}
