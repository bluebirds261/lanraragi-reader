package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.api.LanraragiApi
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.CachedArchive
import com.lanraragi.reader.data.model.OfflineIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 离线缓存：把档案的封面 + 每一页图片下载到应用私有目录，
 * 从而在无网络时也能阅读。索引持久化到 filesDir/offline/index.json。
 */
class OfflineCacheManager(context: android.content.Context, private val api: LanraragiApi) {

    private val offlineDir = File(context.filesDir, "offline")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _index = MutableStateFlow(OfflineIndex())
    val index: StateFlow<OfflineIndex> = _index.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    init {
        loadIndex()
    }

    fun loadIndex() {
        runCatching {
            val f = File(offlineDir, "index.json")
            if (f.exists()) {
                _index.value = ApiClient.json.decodeFromString(f.readText())
            }
        }
    }

    fun isCached(arcid: String): Boolean = _index.value.items.any { it.arcid == arcid }

    fun cached(arcid: String): CachedArchive? = _index.value.items.firstOrNull { it.arcid == arcid }

    fun progressFor(arcid: String): Float? = _progress.value[arcid]

    fun pageFile(arcid: String, page: Int): File =
        File(File(offlineDir, arcid), "pages/${page.toString().padStart(4, '0')}.img")

    fun coverFile(arcid: String): File = File(offlineDir, "$arcid/cover.img")

    fun archiveFile(arcid: String): File = File(offlineDir, "$arcid/original.archive")

    /** 启动离线缓存任务：下载原始文件 + 元数据。 */
    fun startCache(archive: Archive, repository: LanraragiRepository) {
        val arcid = archive.arcid
        if (arcid.isEmpty() || activeJobs.containsKey(arcid)) return
        _progress.update { it + (arcid to 0f) }

        val job = scope.launch {
            try {
                val dir = File(offlineDir, arcid)
                dir.mkdirs()

                // 1. 下载封面
                val coverOk = download(ApiClient.thumbnailUrl(arcid), coverFile(arcid))
                
                // 2. 下载原始文件
                repository.downloadArchive(arcid, archiveFile(arcid)) { written, total ->
                    if (total != null && total > 0) {
                        _progress.update { it + (arcid to written.toFloat() / total) }
                    }
                }

                // 3. 保存完整元数据
                val cached = CachedArchive(
                    arcid = arcid,
                    title = archive.title,
                    pageCount = archive.pagecount,
                    coverExists = coverOk,
                    metadata = archive,
                    isLocal = false
                )
                File(dir, "meta.json").writeText(ApiClient.json.encodeToString(cached))
                addToIndex(cached)
            } catch (e: Exception) {
                // 下载失败逻辑
            } finally {
                _progress.update { it - arcid }
                activeJobs.remove(arcid)
            }
        }
        activeJobs[arcid] = job
    }

    /** 通过 `/api/archives/{id}/files` 获取分页图片绝对 URL 列表。 */
    private suspend fun fetchPageUrls(arcid: String): List<String> = try {
        val resp = api.getFiles(arcid)
        if (!resp.isSuccessful) {
            emptyList()
        } else {
            val body = resp.body()?.string()
            if (body == null) emptyList()
            else JsonHelpers.parsePageUrls(body).map { ApiClient.toAbsoluteUrl(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun download(url: String, dest: File): Boolean = runCatching {
        val req = Request.Builder().url(url).build()
        ApiClient.okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching false
            dest.parentFile?.mkdirs()
            val body = resp.body ?: return@runCatching false
            body.byteStream().use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
            true
        }
    }.getOrDefault(false)

    private fun addToIndex(item: CachedArchive) {
        _index.update { idx ->
            OfflineIndex((idx.items.filter { it.arcid != item.arcid } + item).sortedBy { it.title.lowercase() })
        }
        persistIndex()
    }

    private fun persistIndex() {
        runCatching {
            offlineDir.mkdirs()
            File(offlineDir, "index.json").writeText(ApiClient.json.encodeToString(_index.value))
        }
    }

    fun delete(arcid: String) {
        scope.launch {
            activeJobs[arcid]?.cancel()
            _index.update { OfflineIndex(it.items.filter { a -> a.arcid != arcid }) }
            persistIndex()
            File(offlineDir, arcid).deleteRecursively()
        }
    }

    fun clearAll() {
        scope.launch {
            activeJobs.values.forEach { it.cancel() }
            _index.value = OfflineIndex()
            persistIndex()
            offlineDir.listFiles()?.forEach { if (it.name != "index.json") it.deleteRecursively() }
        }
    }
}
