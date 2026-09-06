package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * B4 离线进度回写队列：把离线/本地阅读期间未能回传到服务器的进度
 * 持久化为 filesDir/progress_pending.json（arcid -> page，page 从 1 起），
 * 待下次 App 启动（[flush]）或联网后重试补推。
 */
class PendingProgressStore(private val context: Context) {

    private val file = File(context.filesDir, "progress_pending.json")

    /** 记入一条待同步进度（覆盖同 arcid 的旧值）。 */
    suspend fun record(arcid: String, page: Int) {
        val map = read().toMutableMap()
        map[arcid] = page.coerceAtLeast(1)
        persist(map)
    }

    /**
     * 逐条补推待同步进度：成功的移除并回写；失败的保留（下次再试）。
     * 单个失败不中断其余；取消异常直接向上抛出，不吞。
     */
    suspend fun flush(repository: LanraragiRepository) {
        val map = read().toMutableMap()
        if (map.isEmpty()) return
        for ((arcid, page) in map.toList()) {
            try {
                repository.setProgress(arcid, page)
                map.remove(arcid)
                persist(map)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 保留该条，下次启动再试。
            }
        }
    }

    private fun read(): Map<String, Int> = runCatching {
        if (file.exists()) {
            ApiClient.json.decodeFromString<Map<String, Int>>(file.readText())
        } else {
            emptyMap()
        }
    }.getOrDefault(emptyMap())

    private fun persist(map: Map<String, Int>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ApiClient.json.encodeToString(map))
        }
    }
}
