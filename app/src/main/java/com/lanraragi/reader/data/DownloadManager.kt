package com.lanraragi.reader.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.lanraragi.reader.data.download.DownloadCoordinator
import com.lanraragi.reader.data.download.DownloadFailureCategory
import com.lanraragi.reader.data.download.downloadFailureCategoryFromMessage
import com.lanraragi.reader.data.download.DownloadTaskSpec
import com.lanraragi.reader.data.download.DurableDownloadState
import com.lanraragi.reader.data.download.DurableDownloadTask
import com.lanraragi.reader.data.storage.StorageRootState
import com.lanraragi.reader.data.storage.storageUnavailableReason
import com.lanraragi.reader.ui.DownloadForegroundService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Compatibility labels retained for existing screens while Room owns every task fact. */
enum class DownloadTaskType { OFFLINE_CACHE, ARCHIVE_FILE }

enum class TaskState { WAITING, RUNNING, PAUSED, FAILED, DONE }

data class DownloadTask(
    val id: String,
    val type: DownloadTaskType,
    val arcid: String = "",
    val title: String = "",
    val state: TaskState = TaskState.WAITING,
    val progress: Float? = null,
    val error: String? = null,
    /** 失败分类（C3）：由持久化的错误消息反推（见 downloadFailureCategoryFromMessage）。 */
    val category: DownloadFailureCategory? = null,
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val retryAtEpochMs: Long? = null,
    /** 已自动重试次数（等待重试/失败时展示）。 */
    val retryCount: Int = 0,
    /** 队列优先级（高/中/低映射值，来自 DownloadTaskSpec.priority）。 */
    val priority: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

/** Waiting, running and delayed-retry tasks require the process-level foreground owner. */
val DownloadTask.isActive: Boolean
    get() = state == TaskState.WAITING || state == TaskState.RUNNING

/** 因存储门禁被拒绝的入队请求；UI 批次用它渲染「存储不可用」横幅。 */
data class EnqueueRejection(
    val taskId: String,
    val label: String,
    val category: DownloadFailureCategory,
    val reason: String,
    val atEpochMs: Long = System.currentTimeMillis(),
)

/**
 * UI compatibility facade over the durable coordinator. It deliberately owns no queue, callbacks,
 * files or JSON state; every mutation is persisted by [DownloadCoordinator] before execution.
 */
class DownloadManager(
    private val scope: CoroutineScope,
    private val context: Context,
    private val coordinator: DownloadCoordinator,
    private val settingsRepository: SettingsRepository,
) {
    val tasks: StateFlow<List<DownloadTask>> = coordinator.tasks
        .map { rows -> rows.map(DurableDownloadTask::toCompatibilityTask) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeCount: StateFlow<Int> = tasks
        .map { rows -> rows.count(DownloadTask::isActive) }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** 存储根单一权威状态（degraded / lastError 观察点），直接转发 SettingsRepository。 */
    val storageRootState: StateFlow<StorageRootState> get() = settingsRepository.storageRootState

    private val _lastEnqueueRejection = MutableStateFlow<EnqueueRejection?>(null)

    /** 最近一次因存储不可用被拒绝的入队请求；`null` = 无待展示的拒绝。 */
    val lastEnqueueRejection: StateFlow<EnqueueRejection?> = _lastEnqueueRejection.asStateFlow()

    fun dismissEnqueueRejection() {
        _lastEnqueueRejection.value = null
    }

    init {
        // C1 速率限制由 DataStore 单一来源驱动：设置页/下载页滑杆改动实时应用到协调器。
        scope.launch {
            settingsRepository.settings
                .map { it.downloadRatePerSecond }
                .distinctUntilChanged()
                .collect { rate -> coordinator.setRatePerSecond(rate) }
        }
        scope.launch {
            activeCount.map { it > 0 }.distinctUntilChanged().collect { active ->
                val intent = Intent(context, DownloadForegroundService::class.java)
                if (active) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.stopService(intent)
                }
            }
        }
    }

    /**
     * 入队（C6 存储门禁）：所有下载任务都是批量产物（离线缓存 / 原档保存），
     * 存储不可用（自定义根授权失效 / 降级 / 写探测失败）时拒绝入队并记录拒绝原因。
     * 返回值仍是任务 id；被拒绝时该 id 不会出现在 [tasks] 中，改由 [lastEnqueueRejection] 观察。
     */
    fun enqueue(spec: DownloadTaskSpec, id: String = UUID.randomUUID().toString()): String {
        val now = System.currentTimeMillis()
        scope.launch {
            val rejectionReason = checkStorageAvailability()
            if (rejectionReason != null) {
                settingsRepository.reportStorageFailure(rejectionReason)
                _lastEnqueueRejection.value = EnqueueRejection(
                    taskId = id,
                    label = spec.label.ifBlank { spec.source.archiveId },
                    category = DownloadFailureCategory.STORAGE,
                    reason = rejectionReason,
                )
                return@launch
            }
            settingsRepository.reportStorageFailure(null)
            coordinator.enqueue(
                DurableDownloadTask(
                    id = id,
                    spec = spec,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        return id
    }

    /** 存储可用性检查（C6 门禁统一判定）：降级即不可用，否则对当前根做可写探测。返回拒绝原因或 `null`。 */
    private suspend fun checkStorageAvailability(): String? =
        settingsRepository.storageRootState.value.storageUnavailableReason()

    fun setMaxConcurrent(value: Int) {
        scope.launch { coordinator.setMaxConcurrent(value) }
    }

    /** C1 每秒任务启动速率上限（1..10）；一般由设置流驱动，此方法供即时调整。 */
    fun setRatePerSecond(value: Int) {
        scope.launch { coordinator.setRatePerSecond(value) }
    }

    /** 长按任务卡的优先级菜单：高/中/低落库到任务优先级。 */
    fun setPriority(taskId: String, priority: Int) {
        scope.launch { coordinator.setPriority(taskId, priority) }
    }

    fun pause(id: String) {
        scope.launch { coordinator.pause(id) }
    }

    fun resume(id: String) {
        scope.launch { coordinator.resume(id) }
    }

    fun retry(id: String) = resume(id)

    fun pauseAll() {
        val ids = tasks.value.filter(DownloadTask::isActive).map(DownloadTask::id)
        scope.launch { ids.forEach { coordinator.pause(it) } }
    }

    fun resumeAll() {
        val ids = tasks.value.filter { it.state == TaskState.PAUSED }.map(DownloadTask::id)
        scope.launch { ids.forEach { coordinator.resume(it) } }
    }

    fun clearFinished() {
        scope.launch { coordinator.clearFinished() }
    }

    fun remove(id: String) {
        scope.launch { coordinator.remove(id) }
    }

    fun cancelTasks(type: DownloadTaskType, arcid: String?) {
        val ids = tasks.value.filter { it.type == type && (arcid == null || it.arcid == arcid) }
            .map(DownloadTask::id)
        scope.launch { ids.forEach { coordinator.remove(it) } }
    }

    fun taskFor(type: DownloadTaskType, arcid: String): DownloadTask? =
        tasks.value.lastOrNull { it.type == type && it.arcid == arcid }
}

private fun DurableDownloadTask.toCompatibilityTask(): DownloadTask {
    val type = when (spec) {
        is DownloadTaskSpec.Archive -> DownloadTaskType.ARCHIVE_FILE
        is DownloadTaskSpec.Cache -> DownloadTaskType.OFFLINE_CACHE
    }
    val compatibilityState = when (state) {
        DurableDownloadState.WAITING, DurableDownloadState.RETRYABLE -> TaskState.WAITING
        DurableDownloadState.RUNNING -> TaskState.RUNNING
        DurableDownloadState.PAUSED -> TaskState.PAUSED
        DurableDownloadState.FAILED -> TaskState.FAILED
        DurableDownloadState.DONE -> TaskState.DONE
    }
    val ratio = totalBytes?.takeIf { it > 0L }?.let { total ->
        (completedBytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    return DownloadTask(
        id = id,
        type = type,
        arcid = spec.source.archiveId,
        title = spec.label.ifBlank { spec.source.archiveId },
        state = compatibilityState,
        progress = ratio,
        error = error,
        category = error?.let { downloadFailureCategoryFromMessage(it) },
        completedBytes = completedBytes,
        totalBytes = totalBytes,
        retryAtEpochMs = retryAtEpochMs,
        retryCount = retryCount,
        priority = spec.priority,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
