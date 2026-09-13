package com.lanraragi.reader.data.diagnostics

import android.content.Context
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志抓取：开启后，当应用发生未捕获异常时，把异常堆栈与当前进程的 Logcat 输出
 * 一并写入应用私有目录 logs 文件夹下的 crash-*.log，并保留最近 [KEEP_CRASH_LOGS] 份。
 *
 * 默认关闭；开关由 [com.lanraragi.reader.data.SettingsRepository] 的 captureLogcat 单一来源驱动。
 */
class LogcatCrashCapture(private val context: Context) {

    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Volatile
    private var enabled: Boolean = false

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (enabled) {
                runCatching { capture(thread, throwable) }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    private fun capture(thread: Thread, throwable: Throwable) {
        val dir = logsDir().apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "crash-$stamp.log")
        file.writeText(
            buildString {
                appendLine("=== LANraragi Reader crash ===")
                appendLine("time: $stamp")
                appendLine("thread: ${thread.name}")
                appendLine()
                appendLine("--- exception ---")
                appendLine(throwable.stackTraceToString())
                appendLine()
                appendLine("--- logcat ---")
                append(readLogcat())
            },
        )
        pruneOldLogs(dir)
    }

    private fun readLogcat(): String = try {
        val process = ProcessBuilder(
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "--pid",
            Process.myPid().toString(),
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
    } catch (t: Throwable) {
        "logcat unavailable: ${t.message}"
    }

    private fun logsDir(): File = File(context.filesDir, "logs")

    private fun pruneOldLogs(dir: File) {
        dir.listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_CRASH_LOGS)
            ?.forEach { it.delete() }
    }

    companion object {
        private const val KEEP_CRASH_LOGS = 10
    }
}
