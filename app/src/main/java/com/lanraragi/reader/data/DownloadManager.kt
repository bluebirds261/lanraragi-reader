package com.lanraragi.reader.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.ui.DownloadForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 下载任务类型：离线缓存 / 原档下载 / 收藏单页。 */
@Serializable
enum class DownloadTaskType { OFFLINE_CACHE, ARCHIVE_FILE, PAGE }

/** 任务状态：等待 / 进行 / 暂停 / 失败 / 完成。 */
@Serializable
enum class TaskState { WAITING, RUNNING, PAUSED, FAILED, DONE }

/** 统一下载任务模型。 */
@Serializable
data class DownloadTask(
    val id: String,
    val type: DownloadTaskType,
    val arcid: String = "",
    val title: String = "",
    val state: TaskState = TaskState.WAITING,
    val progress: Float? = null,
    val error: String? = null,
)

/** 非终态（等待/进行/暂停）都算“进行中”。 */
val DownloadTask.isActive: Boolean
    get() = state == TaskState.WAITING || state == TaskState.RUNNING || state == TaskState.PAUSED

/** 一段可执行的下载工作：onProgress 上报 0..1 进度（未知时为 null）。 */
typealias DownloadWork = suspend (onProgress: (Float?) -> Unit) -> Unit

/**
 * 统一下载任务管理器：把离线缓存、原档下载、收藏单页收拢成同一个队列。
 * 并发数通过构造参数注入，默认仍为 2；UI 可以直接订阅 [activeCount]，
 * 并使用 pauseAll/resumeAll/clearFinished 进行批量控制。
 */
class DownloadManager(
    private val scope: CoroutineScope,
    private val context: Context,
    maxConcurrent: Int = 2,
) {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    /** 活动任务数量：WAITING/RUNNING/PAUSED 均计入徽标。 */
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))
    private val jobs = ConcurrentHashMap<String, Job>()
    private val works = ConcurrentHashMap<String, DownloadWork>()
    private val retriedIds = ConcurrentHashMap.newKeySet<String>()

    private val persistDir = File(context.filesDir, "download_manager")
    private val persistFile = File(persistDir, "tasks.json")
    private var lastProgressPersistAt = 0L

    init {
        loadPersisted()
        refreshActiveCount()
    }

    /** 入队一个新任务并返回任务 id；work 由队列调度后在 [scope] 中执行。 */
    fun enqueue(type: DownloadTaskType, arcid: String, title: String, work: DownloadWork): String {
        val id = UUID.randomUUID().toString()
        works[id] = work
        _tasks.update { it + DownloadTask(id = id, type = type, arcid = arcid, title = title) }
        refreshActiveCount()
        persist()
        startWorker(id)
        syncService()
        return id
    }

    /** 暂停任务：取消正在执行的协程并把状态置为 PAUSED。 */
    fun pause(id: String) {
        val task = find(id) ?: return
        if (task.state != TaskState.RUNNING && task.state != TaskState.WAITING) return
        update(id) { it.copy(state = TaskState.PAUSED) }
        persist()
        jobs[id]?.cancel()
        syncService()
    }

    /** 恢复暂停的任务，重新入队。 */
    fun resume(id: String) {
        val task = find(id) ?: return
        if (task.state != TaskState.PAUSED) return
        retriedIds.remove(id)
        update(id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
        persist()
        startWorker(id)
        syncService()
    }

    /** 失败任务手动重试，重新入队（重置自动重试计数）。 */
    fun retry(id: String) {
        val task = find(id) ?: return
        if (task.state != TaskState.FAILED) return
        retriedIds.remove(id)
        update(id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
        persist()
        startWorker(id)
        syncService()
    }

    /** 批量暂停所有等待/进行中的任务。 */
    fun pauseAll() {
        val targets = _tasks.value.filter {
            it.state == TaskState.WAITING || it.state == TaskState.RUNNING
        }
        if (targets.isEmpty()) return
        targets.forEach { task ->
            update(task.id) { it.copy(state = TaskState.PAUSED) }
            jobs[task.id]?.cancel()
            jobs.remove(task.id)
        }
        refreshActiveCount()
        persist()
        syncService()
    }

    /** 批量恢复所有暂停任务。 */
    fun resumeAll() {
        val targets = _tasks.value.filter { it.state == TaskState.PAUSED }
        if (targets.isEmpty()) return
        targets.forEach { task ->
            retriedIds.remove(task.id)
            update(task.id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
            startWorker(task.id)
        }
        refreshActiveCount()
        persist()
        syncService()
    }

    /** 清理已完成和失败任务，不影响仍在队列中的任务。 */
    fun clearFinished() {
        val targets = _tasks.value.filter {
            it.state == TaskState.DONE || it.state == TaskState.FAILED
        }
        if (targets.isEmpty()) return
        targets.forEach { task ->
            works.remove(task.id)
            jobs.remove(task.id)
            retriedIds.remove(task.id)
        }
        _tasks.update { list -> list.filterNot { it.state == TaskState.DONE || it.state == TaskState.FAILED } }
        refreshActiveCount()
        persist()
        syncService()
    }

    /** 移除任务；运行中则先取消协程。 */
    fun remove(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        works.remove(id)
        retriedIds.remove(id)
        _tasks.update { list -> list.filterNot { it.id == id } }
        refreshActiveCount()
        persist()
        syncService()
    }

    /** 取消并移除匹配的任务；arcid 为 null 时匹配该类型所有任务。 */
    fun cancelTasks(type: DownloadTaskType, arcid: String?) {
        val targets = _tasks.value.filter { it.type == type && (arcid == null || it.arcid == arcid) }
        if (targets.isEmpty()) return
        targets.forEach { t ->
            jobs[t.id]?.cancel()
            jobs.remove(t.id)
            works.remove(t.id)
            retriedIds.remove(t.id)
        }
        val ids = targets.map { it.id }.toSet()
        _tasks.update { list -> list.filterNot { it.id in ids } }
        refreshActiveCount()
        persist()
        syncService()
    }

    /** 查询某类型 + arcid 的最新任务（供去重与 UI 派生状态）。 */
    fun taskFor(type: DownloadTaskType, arcid: String): DownloadTask? =
        _tasks.value.lastOrNull { it.type == type && it.arcid == arcid }

    private fun serviceIntent() = Intent(context, DownloadForegroundService::class.java)

    /** 有活动任务时拉起前台服务，保持后台下载与通知进度。 */
    private fun maybeStartService() {
        if (_tasks.value.any { it.isActive }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent())
            } else {
                context.startService(serviceIntent())
            }
        }
    }

    /** 无活动任务时停止前台服务（状态唯一源仍是本 manager，服务不另存状态）。 */
    private fun maybeStopService() {
        if (_tasks.value.none { it.isActive }) {
            context.stopService(serviceIntent())
        }
    }

    /** 状态变化后同步前台服务生命周期。 */
    private fun syncService() {
        maybeStartService()
        maybeStopService()
    }

    private fun startWorker(id: String) {
        val job = scope.launch {
            semaphore.withPermit {
                val task = find(id) ?: return@withPermit
                if (task.state != TaskState.WAITING) return@withPermit
                update(id) { it.copy(state = TaskState.RUNNING, error = null) }
                persist()
                val work = works[id]
                if (work == null) {
                    markFailed(id, "任务不可用，请重新发起")
                    return@withPermit
                }
                try {
                    work { p -> updateProgress(id, p) }
                    markDone(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = e.message ?: "下载失败"
                    if (retriedIds.add(id)) {
                        // 失败自动重试一次：放回等待队列，重新抢占并发槽位。
                        update(id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
                        persist()
                        startWorker(id)
                    } else {
                        markFailed(id, msg)
                    }
                }
            }
        }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id, job) }
    }

    private fun updateProgress(id: String, p: Float?) {
        val task = find(id) ?: return
        if (task.state != TaskState.RUNNING) return
        update(id) { it.copy(progress = p) }
        persistThrottled()
    }

    private fun markDone(id: String) {
        update(id) { it.copy(state = TaskState.DONE, error = null, progress = 1f) }
        persist()
        syncService()
        works.remove(id)
        retriedIds.remove(id)
    }

    private fun markFailed(id: String, msg: String) {
        update(id) { it.copy(state = TaskState.FAILED, error = msg) }
        persist()
        syncService()
        retriedIds.remove(id)
    }

    private fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
        refreshActiveCount()
    }

    private fun refreshActiveCount() {
        _activeCount.value = _tasks.value.count { it.isActive }
    }

    private fun persistThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastProgressPersistAt < 800) return
        lastProgressPersistAt = now
        persist()
    }

    private fun persist() {
        runCatching {
            persistDir.mkdirs()
            val tmp = File(persistDir, "tasks.json.tmp")
            tmp.writeText(ApiClient.json.encodeToString(_tasks.value))
            if (persistFile.exists()) persistFile.delete()
            tmp.renameTo(persistFile)
        }
    }

    private fun loadPersisted() {
        runCatching {
            if (!persistFile.exists()) return
            val list = ApiClient.json.decodeFromString<List<DownloadTask>>(persistFile.readText())
            _tasks.value = list.map { task ->
                when (task.state) {
                    // 上次进程中断时处于等待/进行中的任务，恢复到等待队列展示（实际下载需重新发起）。
                    TaskState.RUNNING, TaskState.WAITING ->
                        task.copy(state = TaskState.WAITING, error = null, progress = null)
                    else -> task
                }
            }
        }
    }
}