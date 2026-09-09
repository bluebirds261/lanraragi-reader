package com.lanraragi.reader.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.lanraragi.reader.data.download.DownloadCoordinator
import com.lanraragi.reader.data.download.DownloadTaskSpec
import com.lanraragi.reader.data.download.DurableDownloadState
import com.lanraragi.reader.data.download.DurableDownloadTask
import com.lanraragi.reader.ui.DownloadForegroundService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Compatibility labels retained for existing screens while Room owns every task fact. */
enum class DownloadTaskType { OFFLINE_CACHE, ARCHIVE_FILE, PAGE }

enum class TaskState { WAITING, RUNNING, PAUSED, FAILED, DONE }

data class DownloadTask(
    val id: String,
    val type: DownloadTaskType,
    val arcid: String = "",
    val title: String = "",
    val state: TaskState = TaskState.WAITING,
    val progress: Float? = null,
    val error: String? = null,
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val retryAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

/** Waiting, running and delayed-retry tasks require the process-level foreground owner. */
val DownloadTask.isActive: Boolean
    get() = state == TaskState.WAITING || state == TaskState.RUNNING

/**
 * UI compatibility facade over the durable coordinator. It deliberately owns no queue, callbacks,
 * files or JSON state; every mutation is persisted by [DownloadCoordinator] before execution.
 */
class DownloadManager(
    private val scope: CoroutineScope,
    private val context: Context,
    private val coordinator: DownloadCoordinator,
) {
    val tasks: StateFlow<List<DownloadTask>> = coordinator.tasks
        .map { rows -> rows.map(DurableDownloadTask::toCompatibilityTask) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeCount: StateFlow<Int> = tasks
        .map { rows -> rows.count(DownloadTask::isActive) }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
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

    fun enqueue(spec: DownloadTaskSpec, id: String = UUID.randomUUID().toString()): String {
        val now = System.currentTimeMillis()
        scope.launch {
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

    fun setMaxConcurrent(value: Int) {
        scope.launch { coordinator.setMaxConcurrent(value) }
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
        is DownloadTaskSpec.Page -> DownloadTaskType.PAGE
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
        completedBytes = completedBytes,
        totalBytes = totalBytes,
        retryAtEpochMs = retryAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
