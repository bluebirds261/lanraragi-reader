package com.lanraragi.reader.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 服务器后台任务（Minion Job）的展示快照。 */
data class ServerTask(
    val jobid: String,
    val label: String = "",
    val state: String = "",
    val note: String = "",
    val result: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** 首次观测到终态的时刻；用于到期清理，避免长时间运行的任务一结束就被丢掉。 */
    val finishedAt: Long? = null,
) {
    /** 终态判定：与 LanraragiRepository.pollJobUntilDone 保持一致。 */
    val isFinished: Boolean
        get() {
            val s = state.lowercase()
            return s.contains("finish") || s.contains("done") || s.contains("fail") ||
                s.contains("error") || s.contains("inactive") || s == "dead" || s.isBlank()
        }

    val isFailed: Boolean
        get() {
            val s = state.lowercase()
            return s.contains("fail") || s.contains("error") || s == "dead"
        }
}

/**
 * 服务器任务轮询器：挂在 App 级 [CoroutineScope] 上（单例），页面切换/退后台轮询继续；
 * 同一 jobid 只保留一条记录、只起一个轮询协程，避免重复叠加与协程泄漏。
 */
class JobTracker(
    private val repository: LanraragiRepository,
    private val scope: CoroutineScope,
) {

    private val _tasks = MutableStateFlow<List<ServerTask>>(emptyList())
    val tasks: StateFlow<List<ServerTask>> = _tasks.asStateFlow()

    /** 登记一个任务：已存在则只更新名称，不重复叠加、不重复起协程。 */
    fun track(jobid: String, label: String = "") {
        if (jobid.isBlank()) return
        pruneFinished()
        val idx = _tasks.value.indexOfFirst { it.jobid == jobid }
        if (idx >= 0) {
            if (label.isNotBlank() && _tasks.value[idx].label != label) {
                _tasks.value = _tasks.value.toMutableList().also { it[idx] = it[idx].copy(label = label) }
            }
            return
        }
        _tasks.value = _tasks.value + ServerTask(jobid = jobid, label = label)
        scope.launch { poll(jobid) }
    }

    /**
     * 丢弃早已结束的任务：任务卡只在有进度时才有意义，
     * 保留会随着每次缩略图/插件任务无上限增长。
     */
    private fun pruneFinished(now: Long = System.currentTimeMillis()) {
        _tasks.value = _tasks.value.filter { task ->
            val finishedAt = task.finishedAt ?: return@filter true
            now - finishedAt <= FINISHED_RETENTION_MS
        }
    }

    private suspend fun poll(jobid: String) {
        var consecutiveFailures = 0
        val deadline = System.currentTimeMillis() + MAX_POLL_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val job = try {
                repository.getMinionJob(jobid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (job != null) {
                consecutiveFailures = 0
                val now = System.currentTimeMillis()
                _tasks.value = _tasks.value.map { task ->
                    if (task.jobid != jobid) {
                        task
                    } else {
                        val updated = task.copy(state = job.state, note = job.note, result = job.result)
                        if (updated.isFinished && updated.finishedAt == null) updated.copy(finishedAt = now) else updated
                    }
                }
                if (_tasks.value.firstOrNull { it.jobid == jobid }?.isFinished == true) return
            } else {
                // 查询持续失败（任务被服务器清理、或长时间离线）：收敛为终态，
                // 否则每个任务都会留下一个永不退出的轮询协程。
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    markFailed(jobid, "任务查询失败，已停止跟踪")
                    return
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        markFailed(jobid, "任务超时未完成，已停止跟踪")
    }

    private fun markFailed(jobid: String, note: String) {
        val now = System.currentTimeMillis()
        _tasks.value = _tasks.value.map {
            if (it.jobid == jobid) {
                it.copy(state = "failed", note = note, finishedAt = it.finishedAt ?: now)
            } else {
                it
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_POLL_MILLIS = 60L * 60 * 1000
        const val MAX_CONSECUTIVE_FAILURES = 5
        const val FINISHED_RETENTION_MS = 5L * 60 * 1000
    }
}
