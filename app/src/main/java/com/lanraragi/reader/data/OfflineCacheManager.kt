package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.api.LanraragiApi
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.CachedArchive
import com.lanraragi.reader.data.model.OfflineIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** 离线缓存占用统计：总字节数 + 已索引档案数量。 */
data class OfflineUsage(val totalBytes: Long = 0, val count: Int = 0)

/**
 * 离线缓存：把档案的封面 + 每一页图片下载到应用私有目录，
 * 从而在无网络时也能阅读。索引持久化到 filesDir/offline/index.json。
 */
class OfflineCacheManager(
    context: android.content.Context,
    private val api: LanraragiApi,
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
) {

    private val offlineDir = File(context.filesDir, "offline")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _index = MutableStateFlow(OfflineIndex())
    val index: StateFlow<OfflineIndex> = _index.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _usage = MutableStateFlow(OfflineUsage())
    val usage: StateFlow<OfflineUsage> = _usage.asStateFlow()

    /** 最近一次被 LRU 淘汰的 arcid，供详情页提示“缓存已被替换”。 */
    val lastEvicted = MutableStateFlow<String?>(null)

    private var lastTouchPersistAt = 0L

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
        recomputeUsage()
    }

    fun isCached(arcid: String): Boolean = _index.value.items.any { it.arcid == arcid }

    fun cached(arcid: String): CachedArchive? {
        val item = _index.value.items.firstOrNull { it.arcid == arcid }
        if (item != null) touch(arcid)
        return item
    }

    fun progressFor(arcid: String): Float? = _progress.value[arcid]

    fun pageFile(arcid: String, page: Int): File {
        touch(arcid)
        return File(File(offlineDir, arcid), "pages/${page.toString().padStart(4, '0')}.img")
    }

    fun coverFile(arcid: String): File = File(offlineDir, "$arcid/cover.img")

    fun archiveFile(arcid: String): File = File(offlineDir, "$arcid/original.archive")

    /** 启动离线缓存任务：提交到统一下载队列，下载原始文件 + 元数据。 */
    fun startCache(archive: Archive, repository: LanraragiRepository) {
        val arcid = archive.arcid
        if (arcid.isEmpty()) return
        val existing = downloadManager.taskFor(DownloadTaskType.OFFLINE_CACHE, arcid)
        if (existing != null && existing.isActive) return
        downloadManager.enqueue(DownloadTaskType.OFFLINE_CACHE, arcid, archive.title) { onProgress ->
            performCache(archive, repository, onProgress)
        }
    }

    /** 实际执行整本缓存：下载封面 + 原始文件 + 写 meta.json + 加入索引。 */
    private suspend fun performCache(
        archive: Archive,
        repository: LanraragiRepository,
        onProgress: (Float?) -> Unit,
    ) {
        val arcid = archive.arcid
        _progress.update { it + (arcid to 0f) }
        try {
            val dir = File(offlineDir, arcid)
            dir.mkdirs()

            // 1. 下载封面
            val coverOk = download(ApiClient.thumbnailUrl(arcid), coverFile(arcid))

            // 2. 下载原始文件
            repository.downloadArchive(arcid, archiveFile(arcid)) { written, total ->
                val p = if (total != null && total > 0) written.toFloat() / total else null
                if (p != null) _progress.update { it + (arcid to p) }
                onProgress(p)
            }

            // 3. 保存完整元数据
            val cached = CachedArchive(
                arcid = arcid,
                title = archive.title,
                pageCount = archive.pagecount,
                coverExists = coverOk,
                metadata = archive,
                isLocal = false,
                lastAccess = System.currentTimeMillis(),
            )
            File(dir, "meta.json").writeText(ApiClient.json.encodeToString(cached))
            addToIndex(cached)
            enforceLimit()
        } finally {
            _progress.update { it - arcid }
            recomputeUsage()
        }
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
        recomputeUsage()
    }

    private fun persistIndex() {
        runCatching {
            offlineDir.mkdirs()
            File(offlineDir, "index.json").writeText(ApiClient.json.encodeToString(_index.value))
        }
    }

    /** 记录一次访问（读缓存/读页），更新最后访问时间并节流持久化索引。 */
    fun touch(arcid: String) {
        if (_index.value.items.none { it.arcid == arcid }) return
        val now = System.currentTimeMillis()
        _index.update { idx ->
            OfflineIndex(idx.items.map { if (it.arcid == arcid) it.copy(lastAccess = now) else it })
        }
        if (now - lastTouchPersistAt >= 800) {
            lastTouchPersistAt = now
            scope.launch { persistIndex() }
        }
    }

    /** 重新统计离线目录磁盘占用（除 index.json 外所有文件）与索引数量。 */
    private fun recomputeUsage() {
        val totalBytes = if (offlineDir.exists()) {
            offlineDir.walkTopDown().filter { it.isFile && it.name != "index.json" }.sumOf { it.length() }
        } else {
            0L
        }
        _usage.value = OfflineUsage(totalBytes = totalBytes, count = _index.value.items.size)
    }

    /** 超出上限时按 lastAccess 升序（最早访问优先）淘汰，直到落回上限内。 */
    private suspend fun enforceLimit() {
        val limitGb = settingsRepository.settings.first().offlineCacheLimitGb
        if (limitGb <= 0) return
        val limitBytes = limitGb.toLong() * 1024L * 1024L * 1024L
        recomputeUsage()
        while (_usage.value.totalBytes > limitBytes) {
            val victim = _index.value.items.minByOrNull { it.lastAccess } ?: break
            deleteSync(victim.arcid)
            lastEvicted.value = victim.arcid
        }
    }

    fun delete(arcid: String) {
        scope.launch { deleteSync(arcid) }
    }

    private fun deleteSync(arcid: String) {
        downloadManager.cancelTasks(DownloadTaskType.OFFLINE_CACHE, arcid)
        _index.update { OfflineIndex(it.items.filter { a -> a.arcid != arcid }) }
        persistIndex()
        File(offlineDir, arcid).deleteRecursively()
        recomputeUsage()
    }

    fun clearAll() {
        downloadManager.cancelTasks(DownloadTaskType.OFFLINE_CACHE, null)
        scope.launch {
            _index.value = OfflineIndex()
            persistIndex()
            offlineDir.listFiles()?.forEach { if (it.name != "index.json") it.deleteRecursively() }
            recomputeUsage()
        }
    }
}
