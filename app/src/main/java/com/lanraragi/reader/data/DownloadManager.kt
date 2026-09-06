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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** 真正占用下载队列/并发槽位的活动任务：等待或执行中。暂停任务不算活动。 */
val DownloadTask.isActive: Boolean
    get() = state == TaskState.WAITING || state == TaskState.RUNNING

typealias DownloadWork = suspend (onProgress: (Float?) -> Unit) -> Unit

/**
 * 统一下载任务管理器：把离线缓存、原档下载、收藏单页收拢成同一个队列。
 * 并发数通过构造参数注入，默认 2；可在运行时安全更新。
 * UI 可以订阅 [activeCount] 做真实的活动任务徽标。
 */
class DownloadManager(
    private val scope: CoroutineScope,
    private val context: Context,
    maxConcurrent: Int = 2,
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    /** 活动任务数量：WAITING/RUNNING。PAUSED 不计入。 */
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private var maxConcurrent = maxConcurrent.coerceIn(1, 8)
    private val dispatchMutex = Mutex()
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

    fun enqueue(type: DownloadTaskType, arcid: String, title: String, work: DownloadWork): String {
        val id = UUID.randomUUID().toString()
        works[id] = work
        _tasks.update { it + DownloadTask(id = id, type = type, arcid = arcid, title = title) }
        refreshActiveCount()
        persist()
        requestDispatch()
        syncService()
        return id
    }

    /** 更新并发上限；已运行任务不中断，新任务会按新上限派发。 */
    fun setMaxConcurrent(value: Int) {
        maxConcurrent = value.coerceIn(1, 8)
        requestDispatch()
    }

    fun pause(id: String) {
        val task = find(id) ?: return
        if (task.state != TaskState.RUNNING && task.state != TaskState.WAITING) return
        update(id) { it.copy(state = TaskState.PAUSED) }
        persist()
        jobs[id]?.cancel()
        syncService()
    }

    fun resume(id: String) {
        val task = find(id) ?: return
        if (task.state != TaskState.PAUSED) return
        retriedIds.remove(id)
        update(id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
        persist()
        requestDispatch()
        syncService()
    }

    fun retry(id: String) {
        val task = find(id) ?: return
        if (task.state != TaskState.FAILED) return
        retriedIds.remove(id)
        update(id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
        persist()
        requestDispatch()
        syncService()
    }

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

    fun resumeAll() {
        val targets = _tasks.value.filter { it.state == TaskState.PAUSED }
        if (targets.isEmpty()) return
        targets.forEach { task ->
            retriedIds.remove(task.id)
            update(task.id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
        }
        requestDispatch()
        refreshActiveCount()
        persist()
        syncService()
    }

    fun clearFinished() {
        val targets = _tasks.value.filter { it.state == TaskState.DONE || it.state == TaskState.FAILED }
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

    fun taskFor(type: DownloadTaskType, arcid: String): DownloadTask? =
        _tasks.value.lastOrNull { it.type == type && it.arcid == arcid }

    private fun serviceIntent() = Intent(context, DownloadForegroundService::class.java)

    private fun maybeStartService() {
        if (_tasks.value.any { it.isActive }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent())
            } else {
                context.startService(serviceIntent())
            }
        }
    }

    private fun maybeStopService() {
        if (_tasks.value.none { it.isActive }) context.stopService(serviceIntent())
    }

    private fun syncService() {
        maybeStartService()
        maybeStopService()
    }

    private fun requestDispatch() {
        scope.launch {
            dispatchMutex.withLock { dispatchWaitingTasks() }
        }
    }

    private fun dispatchWaitingTasks() {
        val availableSlots = (maxConcurrent - jobs.size).coerceAtLeast(0)
        if (availableSlots == 0) return
        _tasks.value
            .asSequence()
            .filter {
                it.state == TaskState.WAITING &&
                    !jobs.containsKey(it.id) &&
                    works.containsKey(it.id)
            }
            .take(availableSlots)
            .forEach { task ->
                val job = scope.launch { runWorker(task.id) }
                jobs[task.id] = job
            }
    }

    private suspend fun runWorker(id: String) {
        try {
            val task = find(id) ?: return
            if (task.state != TaskState.WAITING) return
            update(id) { it.copy(state = TaskState.RUNNING, error = null) }
            persist()
            val work = works[id]
            if (work == null) {
                markFailed(id, "任务不可用，请重新发起")
                return
            }
            try {
                work { p -> updateProgress(id, p) }
                markDone(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "下载失败"
                if (retriedIds.add(id)) {
                    update(id) { it.copy(state = TaskState.WAITING, error = null, progress = null) }
                    persist()
                } else {
                    markFailed(id, msg)
                }
            }
        } finally {
            jobs.remove(id)
            requestDispatch()
        }
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
                    TaskState.RUNNING, TaskState.WAITING ->
                        task.copy(state = TaskState.WAITING, error = null, progress = null)
                    else -> task
                }
            }
        }
    }
}
