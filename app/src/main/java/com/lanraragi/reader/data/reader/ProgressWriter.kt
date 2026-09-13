package com.lanraragi.reader.data.reader

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.history.core.ProgressFlushReport
import com.lanraragi.reader.data.history.core.ProgressOutbox
import com.lanraragi.reader.data.history.core.ProgressTask
import com.lanraragi.reader.data.history.core.ProgressTaskStore
import com.lanraragi.reader.data.history.core.ProgressTransport
import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.domain.reader.ReaderPageMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reader-facing progress boundary. Local/SAF identities are intentionally ignored. */
interface ProgressWriter {
    suspend fun record(identity: ArchiveIdentity, page: Int, pageCount: Int = 0): ProgressTask?
    suspend fun pending(): List<ProgressTask>

    /**
     * 立即回传一次。调用方**不需要**自己安排重试：实现内部会对仍被保留的任务做有界的
     * 后台退避重试（见 [OutboxProgressWriter]），因此这里没有额外的重试方法暴露给 UI。
     */
    suspend fun flush(): ProgressFlushReport

    /**
     * 请求一次「合并窗口内最多一次」的后台回传。翻页时调用即可，不必等待结果；
     * 不实现合并策略的写法保持默认空实现（例如预览/本地专用实例）。
     */
    fun scheduleFlush() = Unit
}

/**
 * 本地优先 + 后台合并回传的进度写入器。
 *
 * [record] 只落本地 outbox（即时、离线可用）；[scheduleFlush] 把服务器回传收敛到
 * [mergeWindowMillis] 的合并窗口内（文档 B8：批量回传、5 秒合并），窗口到期后在
 * 传入的应用级 [scope] 上执行，因此页面退出/重组不会取消这次回传。
 *
 * ## 有界指数退避重试
 * 此前失败的进度只能等下次翻页或下次启动才重传；若用户就此停止阅读，进度会在本地滞留很久。
 * 现在一次 [flush] 结束后只要**确有任务写失败**（`attempted > succeeded`，通常意味着服务端抖动、
 * 瞬时网络/鉴权失败或 App 在后台；被跳过的任务不计入，它们重试也不会成功），
 * 就在应用级 [scope] 上排队一次静默重试，
 * 时间表为：首次 [retryInitialDelayMillis]（默认 30 秒）→ 每次翻倍 → 上限 [retryMaxDelayMillis]
 * （默认 15 分钟），连续尝试最多 [maxRetryAttempts] 次（默认 8 次，合计约 1 小时）后彻底停下，
 * 避免服务器长期不可达时无限重试。重试不产生任何用户可见文案，行为与既有的自动重传一致。
 *
 * - **单飞**：同一时刻最多只有一个退避重试任务（`retryJob` 与 `retryAttempts` 只在 [retryLock] 内读写）；
 *   重试任务不递归调度自己，而是在自己的循环里推进阶梯，且所有回传共用 [flushMutex]，不会并发跑两次 flush。
 * - **正常阅读路径不变**：任何一次成功回传、或用户侧新动作（[flush] / [scheduleFlush]，例如退出阅读器、
 *   App 切后台、翻页）都会取消等待中的重试并把连续尝试计数清零；进程重启则由启动时的一次 [flush] 接管，
 *   退避状态是进程内的，无需持久化。
 * - 退避参数可注入，便于单元测试用极小延迟驱动，无需真实等待。
 */
class OutboxProgressWriter(
    store: ProgressTaskStore,
    private val transport: ProgressTransport,
    private val scope: CoroutineScope? = null,
    private val mergeWindowMillis: Long = 5_000L,
    private val retryInitialDelayMillis: Long = DEFAULT_RETRY_INITIAL_DELAY_MILLIS,
    private val retryMaxDelayMillis: Long = DEFAULT_RETRY_MAX_DELAY_MILLIS,
    private val maxRetryAttempts: Int = DEFAULT_MAX_RETRY_ATTEMPTS,
    /**
     * A3 能力门控：服务端是否记录阅读进度（`/info` 的 `server_tracks_progress`）。
     * 为 false 时不入队、也不发送——服务端开了「本地进度」模式时该端点必然被拒，
     * 发了只会浪费请求并堆积永远失败的任务。默认 true（未探测到能力信息时保持乐观）。
     */
    private val progressSupported: () -> Boolean = { true },
) : ProgressWriter {
    private val outbox = ProgressOutbox(store)
    private val flushMutex = Mutex()
    private var scheduled: Job? = null

    /** 退避重试状态；`retryJob`、`retryAttempts` 一律在 [retryLock] 内读写，保证单飞。 */
    private val retryLock = Any()
    private var retryJob: Job? = null
    private var retryAttempts = 0

    override suspend fun record(identity: ArchiveIdentity, page: Int, pageCount: Int): ProgressTask? {
        // 服务端不记录进度：本地历史照常写入（阅读器另有 recordProgress 落库），
        // 但不入队任何回传任务。
        if (!progressSupported()) return null
        return outbox.enqueue(identity, page, pageCount)
    }

    override suspend fun pending(): List<ProgressTask> = outbox.pending()

    /**
     * 立即回传一次。成功（outbox 已清空）则取消等待中的退避重试并清零计数；
     * 仍有任务被保留时排一次有界退避重试。
     */
    override suspend fun flush(): ProgressFlushReport {
        // 能力不允许时不尝试任何回传：返回全 0 报告（attempted == succeeded），
        // 因此既不会排重试，也不会把任务丢掉——将来服务器开启进度后仍可补传。
        if (!progressSupported()) return ProgressFlushReport(attempted = 0, succeeded = 0, retained = outbox.pending().size, skipped = 0)
        val report = try {
            flushOutbox()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 单条任务的失败已被 ProgressOutbox 吸收；这里兜住存储层等意外异常：
            // 先排一次重试再向上抛，避免尚未提交的任务彻底失去重试机会。
            scheduleRetry()
            throw e
        }
        // 只有「确实尝试过但没写成功」才排重试：retained 里还包含被跳过（能力不允许同步）的任务，
        // 那种情况重试多少次都不会成功，不该占用重试预算。
        if (report.attempted > report.succeeded) scheduleRetry() else clearRetry()
        return report
    }

    override fun scheduleFlush() {
        val target = scope ?: return
        // 翻页说明用户仍在阅读：先取消等待中的退避重试（计数同时清零），
        // 让紧接着的合并回传重新判定是否需要重试，避免两个定时器叠加。
        clearRetry()
        synchronized(this) {
            if (scheduled?.isActive == true) return
            scheduled = target.launch {
                delay(mergeWindowMillis)
                try {
                    flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 失败任务保留在 outbox，由 flush() 排出的有界退避重试接手，直到成功或到达尝试上限。
                }
            }
        }
    }

    /** 真正的一次回传；所有调用方共用 [flushMutex]，因此不会有两处同时回传。 */
    private suspend fun flushOutbox(): ProgressFlushReport = flushMutex.withLock { outbox.flush(transport) }

    /**
     * 单飞排队：已有活跃的退避重试时直接返回（不叠加定时器）；新一轮阶梯开始时清零连续尝试计数，
     * 使「用户产生新动作后又失败」能重新获得完整的重试预算。
     */
    private fun scheduleRetry() {
        val target = scope ?: return
        synchronized(retryLock) {
            if (retryJob?.isActive == true) return
            retryAttempts = 0
            retryJob = target.launch { runRetryLoop() }
        }
    }

    /** 取消等待中的退避重试并清零计数（成功回传、或用户侧新动作时调用）。 */
    private fun clearRetry() {
        val pending = synchronized(retryLock) {
            val job = retryJob
            retryJob = null
            retryAttempts = 0
            job
        }
        pending?.cancel()
    }

    /**
     * 退避重试主体：在应用级作用域内循环推进阶梯，最多 [maxRetryAttempts] 次，不做递归调度
     * （递归会让「任务仍在运行」与「已有待重试任务」自相矛盾）。达到上限即结束，
     * 之后的重新排队只能由新的 flush / scheduleFlush 触发。
     */
    private suspend fun runRetryLoop() {
        while (true) {
            val attempt = beginRetryAttempt() ?: return
            delay(retryDelayForAttempt(attempt))
            val report = try {
                flushOutbox()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 存储层等意外异常按「仍然失败」处理，继续下一阶梯（上限依然生效）。
                null
            }
            if (report != null && report.retained == 0) {
                // 成功：清零计数后退出循环；retryJob 引用留给自己结束，避免短时间内出现第二个重试任务。
                synchronized(retryLock) { retryAttempts = 0 }
                return
            }
        }
    }

    /** 取得下一次尝试的序号；到达上限返回 null 表示不再重试。 */
    private fun beginRetryAttempt(): Int? = synchronized(retryLock) {
        if (retryAttempts >= maxRetryAttempts) return@synchronized null
        retryAttempts += 1
        retryAttempts
    }

    /** 第 [attempt] 次尝试前的等待：首次 [retryInitialDelayMillis]，之后翻倍，上限 [retryMaxDelayMillis]。 */
    private fun retryDelayForAttempt(attempt: Int): Long {
        val initial = retryInitialDelayMillis.coerceAtLeast(0L)
        val cap = retryMaxDelayMillis.coerceAtLeast(initial)
        if (attempt <= 1) return initial.coerceAtMost(cap)
        var millis = initial
        repeat(attempt - 1) {
            if (millis >= cap) return cap
            millis = if (millis > cap / 2) cap else millis * 2
        }
        return millis.coerceAtMost(cap)
    }

    companion object {
        /**
         * 默认退避策略：30 秒 → 60 → 120 → 240 → 480 → 900 → 900 → 900（8 次，共约 60.5 分钟）。
         * 上限保证服务器长期不可达时不会无限重试；初始值保证瞬时故障能较快恢复。
         */
        const val DEFAULT_RETRY_INITIAL_DELAY_MILLIS: Long = 30_000L
        const val DEFAULT_RETRY_MAX_DELAY_MILLIS: Long = 15 * 60_000L
        const val DEFAULT_MAX_RETRY_ATTEMPTS: Int = 8
    }
}

/** Production transport; conversion to LANraragi's one-based progress endpoint stays here. */
class RepositoryProgressTransport(
    private val repository: LanraragiRepository,
) : ProgressTransport {
    override suspend fun write(identity: ArchiveIdentity.Remote, page: Int, pageCount: Int) {
        repository.setProgress(identity.arcid, ReaderPageMapping.uiPageToApiPage(page))
    }
}

/** Useful for local-only sessions and previews; it never performs any network work. */
object NoopProgressWriter : ProgressWriter {
    override suspend fun record(identity: ArchiveIdentity, page: Int, pageCount: Int): ProgressTask? = null
    override suspend fun pending(): List<ProgressTask> = emptyList()
    override suspend fun flush(): ProgressFlushReport = ProgressFlushReport(0, 0, 0, 0)
}
