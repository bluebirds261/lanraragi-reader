package com.lanraragi.reader.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lanraragi.reader.LanraragiApplication
import com.lanraragi.reader.MainActivity
import com.lanraragi.reader.R
import com.lanraragi.reader.data.DownloadTask
import com.lanraragi.reader.data.TaskState
import com.lanraragi.reader.data.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 下载前台服务：DownloadManager 是状态的唯一来源，本服务只做「执行器」，
 * 把进行中的任务名与百分比渲染到通知栏，并在任务完成时发一条可点击通知。
 * 进程被杀后由系统按 START_STICKY 重启；AppContainer 会从 Room 恢复可序列化任务，
 * runner 根据 .part 文件和 Range 协议继续执行，本服务只渲染持久队列状态。
 */
class DownloadForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var notificationManager: NotificationManager
    private var collectionJob: Job? = null

    /** 已经发过「下载完成」通知的任务 id（内存态），用 diff 避免重启服务时重发历史完成通知。 */
    private var previousDoneIds: Set<String> = emptySet()
    private var baselineEstablished = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as LanraragiApplication).container.downloadManager

        // 先以当前快照进入前台，避免后台拉起前台服务超时。
        startForegroundCompat(buildNotification(manager.tasks.value.filter { it.isActive }))

        if (collectionJob == null) {
            collectionJob = manager.tasks
                .onEach { updateNotification(it) }
                .launchIn(scope)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(tasks: List<DownloadTask>) {
        // 完成通知：识别「刚变为 DONE」的任务后单发一条可点击通知。
        val doneIds = tasks.filter { it.state == TaskState.DONE }.map { it.id }.toSet()
        if (baselineEstablished) {
            (doneIds - previousDoneIds).forEach { id ->
                tasks.firstOrNull { it.id == id }?.let { notifyComplete(it) }
            }
        } else {
            baselineEstablished = true
        }
        previousDoneIds = doneIds

        val active = tasks.filter { it.isActive }
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        notificationManager.notify(NOTIFICATION_ID, buildNotification(active))
    }

    private fun buildNotification(active: List<DownloadTask>): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        when {
            active.isEmpty() -> {
                builder.setContentTitle("下载任务")
                builder.setContentText("准备中…")
                builder.setProgress(0, 0, true)
            }

            active.size == 1 -> {
                val task = active.first()
                builder.setContentTitle(task.title.ifBlank { "下载任务" })
                when (task.state) {
                    TaskState.PAUSED -> {
                        builder.setContentText("已暂停")
                        builder.setProgress(0, 0, false)
                    }

                    TaskState.WAITING -> {
                        builder.setContentText("等待中…")
                        builder.setProgress(0, 0, true)
                    }

                    else -> {
                        val progress = task.progress
                        if (progress != null) {
                            val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
                            builder.setContentText("$percent%")
                            builder.setProgress(100, percent, false)
                        } else {
                            builder.setContentText("下载中…")
                            builder.setProgress(0, 0, true)
                        }
                    }
                }
            }

            else -> {
                builder.setContentTitle("下载任务")
                builder.setContentText("${active.size} 个任务进行中")
                builder.setProgress(0, 0, true)
            }
        }

        return builder.build()
    }

    private fun notifyComplete(task: DownloadTask) {
        val contentIntent = PendingIntent.getActivity(
            this,
            task.id.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("下载完成")
            .setContentText(task.title.ifBlank { "下载任务" })
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(task.id.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "下载任务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示后台下载任务进度"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "CHANNEL_DOWNLOADS"
        const val NOTIFICATION_ID = 1001
    }
}
