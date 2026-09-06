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

    private suspend fun poll(jobid: String) {
        while (true) {
            val job = try {
                repository.getMinionJob(jobid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (job != null) {
                _tasks.value = _tasks.value.map {
                    if (it.jobid == jobid) it.copy(state = job.state, note = job.note, result = job.result) else it
                }
                if (_tasks.value.firstOrNull { it.jobid == jobid }?.isFinished == true) return
            }
            delay(2000)
        }
    }
}
