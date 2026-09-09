package com.lanraragi.reader.data

import androidx.room.withTransaction
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.api.LanraragiApi
import com.lanraragi.reader.data.assets.CoverState
import com.lanraragi.reader.data.assets.ThumbnailRepository
import com.lanraragi.reader.data.db.AppDataBootstrapReport
import com.lanraragi.reader.data.db.AppDataBootstrapper
import com.lanraragi.reader.data.db.OfflineIndexLegacyImporter
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.download.DownloadDestination
import com.lanraragi.reader.data.download.DownloadSourceIdentity
import com.lanraragi.reader.data.download.DownloadTaskSpec
import com.lanraragi.reader.data.download.EvictionResult
import com.lanraragi.reader.data.download.SavedArtifact
import com.lanraragi.reader.data.download.SavedArtifactRepository
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.CachedArchive
import com.lanraragi.reader.data.model.OfflineIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** 离线缓存占用统计：总字节数 + 已索引档案数量。 */
data class OfflineUsage(val totalBytes: Long = 0, val count: Int = 0)

/**
 * 离线缓存：把档案的封面 + 每一页图片下载到应用私有目录，
 * 从而在无网络时也能阅读。索引持久化到 filesDir/offline/index.json。
 */
class OfflineCacheManager(
    private val context: android.content.Context,
    private val api: LanraragiApi,
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
    private val thumbnailRepository: ThumbnailRepository,
    private val savedArtifactRepository: SavedArtifactRepository,
    private val database: ReaderDatabase? = null,
    private val appDataBootstrap: Deferred<AppDataBootstrapReport>? = null,
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
    private val roomMirrorRevision = MutableStateFlow(0L)
    private val finalizingTasks = ConcurrentHashMap.newKeySet<String>()
    private val startingCaches = ConcurrentHashMap.newKeySet<String>()

    init {
        loadIndex()
        startRoomMirror()
        observeDurableCacheTasks()
    }

    fun loadIndex() {
        val loaded = runCatching {
            val f = File(offlineDir, "index.json")
            if (f.exists()) {
                _index.value = ApiClient.json.decodeFromString(f.readTextAtomically())
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (loaded) roomMirrorRevision.value += 1L
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
        // 本地扫描结果从不进入任何服务器资源任务，避免将用户文件带入网络路径。
        if (arcid.isEmpty() || arcid.startsWith("local_")) return
        val existing = downloadManager.taskFor(DownloadTaskType.OFFLINE_CACHE, arcid)
        if (existing?.isActive == true || (existing?.state == TaskState.DONE && isCached(arcid))) return
        if (!startingCaches.add(arcid)) return
        scope.launch {
            try {
                val destination = archiveFile(arcid)
                destination.parentFile?.mkdirs()
                pendingMetadataFile(arcid).writeTextAtomically(ApiClient.json.encodeToString(archive))
                val source = DownloadSourceIdentity(arcid, ApiClient.config.baseUrl)
                val saved = savedArtifactRepository.findBySource(source)
                val reused = saved?.takeIf { File(it.path).isFile && File(it.path).length() > 0L }?.let { artifact ->
                    try {
                        com.lanraragi.reader.data.download.SavedArtifactExporter(context.contentResolver)
                            .export(artifact, DownloadDestination(destination.absolutePath))
                        finalizeDurableCache(arcid)
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                } == true
                if (!reused) {
                    downloadManager.enqueue(
                        DownloadTaskSpec.Cache(
                            source = source,
                            destination = DownloadDestination(destination.absolutePath),
                            label = archive.title.ifBlank { arcid },
                        ),
                    )
                }
            } finally {
                startingCaches.remove(arcid)
            }
        }
    }

    /** Completes cache metadata/index work after the durable archive runner publishes the file. */
    private fun observeDurableCacheTasks() {
        scope.launch {
            downloadManager.tasks.collect { tasks ->
                _progress.value = tasks.asSequence()
                    .filter { it.type == DownloadTaskType.OFFLINE_CACHE && it.isActive }
                    .mapNotNull { task -> task.progress?.let { task.arcid to it } }
                    .toMap()
                tasks.asSequence()
                    .filter { it.type == DownloadTaskType.OFFLINE_CACHE && it.state == TaskState.DONE }
                    .filter { pendingMetadataFile(it.arcid).isFile || !isCached(it.arcid) }
                    .forEach { task ->
                        if (finalizingTasks.add(task.id)) {
                            scope.launch {
                                try {
                                    finalizeDurableCache(task.arcid)
                                } finally {
                                    finalizingTasks.remove(task.id)
                                }
                            }
                        }
                    }
            }
        }
    }

    private suspend fun finalizeDurableCache(arcid: String) {
        val archiveFile = archiveFile(arcid)
        require(archiveFile.isFile && archiveFile.length() > 0L) { "离线档案文件不可用" }
        val pending = pendingMetadataFile(arcid)
        val archive = runCatching {
            ApiClient.json.decodeFromString<Archive>(pending.readTextAtomically())
        }.getOrNull() ?: Archive(arcid = arcid, title = arcid)
        val readyCover = when (val state = thumbnailRepository.awaitCover(arcid)) {
            is CoverState.Ready -> state
            is CoverState.Failed -> state.lastGood
            is CoverState.Generating -> state.lastGood
            is CoverState.Unrequested -> null
        }
        val coverOk = readyCover?.let { download(it.resource.value, coverFile(arcid)) } == true
        val cached = CachedArchive(
            arcid = arcid,
            title = archive.title,
            pageCount = archive.pagecount,
            coverExists = coverOk,
            metadata = archive,
            isLocal = false,
            lastAccess = System.currentTimeMillis(),
        )
        File(archiveFile.parentFile, "meta.json")
            .writeTextAtomically(ApiClient.json.encodeToString(cached))
        val source = DownloadSourceIdentity(arcid, ApiClient.config.baseUrl)
        val existingArtifact = savedArtifactRepository.findBySource(source)
        savedArtifactRepository.put(
            SavedArtifact(
                artifactKey = artifactKey(arcid),
                source = source,
                path = archiveFile.absolutePath,
                revision = "offline:${archiveFile.length()}:${archiveFile.lastModified()}",
                byteSize = archiveFile.length(),
                pinned = existingArtifact?.pinned == true,
                lastAccessAtEpochMs = cached.lastAccess,
            ),
        )
        addToIndex(cached)
        pending.delete()
        enforceLimit()
    }

    private fun pendingMetadataFile(arcid: String): File = File(offlineDir, "$arcid/meta.pending.json")
    private fun artifactKey(arcid: String): String = com.lanraragi.reader.data.download.SavedArtifactIdentity.key(
        DownloadSourceIdentity(arcid, ApiClient.config.baseUrl),
    )

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
            val readyCover = when (val state = thumbnailRepository.awaitCover(arcid)) {
                is CoverState.Ready -> state
                is CoverState.Failed -> state.lastGood
                is CoverState.Generating -> state.lastGood
                is CoverState.Unrequested -> null
            }
            val coverOk = readyCover?.let { download(it.resource.value, coverFile(arcid)) } == true

            // 2. 完成下载前只写入临时文件，阅读器不会把半个压缩包误认为有效离线资源。
            val archiveFile = archiveFile(arcid)
            val partialArchiveFile = File(archiveFile.parentFile, "${archiveFile.name}.part")
            partialArchiveFile.delete()
            repository.downloadArchive(arcid, partialArchiveFile) { written, total ->
                val p = if (total != null && total > 0) written.toFloat() / total else null
                if (p != null) _progress.update { it + (arcid to p) }
                onProgress(p)
            }
            if (!partialArchiveFile.exists() || partialArchiveFile.length() <= 0L) {
                throw IllegalStateException("离线档案下载不完整")
            }
            if (archiveFile.exists() && !archiveFile.delete()) {
                throw IllegalStateException("无法替换旧的离线档案")
            }
            if (!partialArchiveFile.renameTo(archiveFile)) {
                throw IllegalStateException("无法完成离线档案写入")
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
            File(File(offlineDir, arcid), "original.archive.part").delete()
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

    /** Keep saved_artifact current while offline/index.json remains the rollback source. */
    private fun startRoomMirror() {
        val db = database ?: return
        scope.launch {
            val syncInitial = try {
                val source = appDataBootstrap?.await()
                    ?.source(AppDataBootstrapper.OFFLINE_INDEX_KEY)
                source == null || source.status == com.lanraragi.reader.data.db.LegacySourceStatus.IMPORTED ||
                    source.status == com.lanraragi.reader.data.db.LegacySourceStatus.ALREADY_IMPORTED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            val importer = OfflineIndexLegacyImporter()
            roomMirrorRevision.collectLatest { revision ->
                    if (!syncInitial && revision == 0L) return@collectLatest
                    delay(800)
                    val snapshot = _index.value
                    val facts = snapshot.items.map { item ->
                        val original = archiveFile(item.arcid)
                        OfflineIndexLegacyImporter.ArtifactFacts(
                            arcid = item.arcid,
                            filePath = original.absolutePath,
                            exists = original.isFile,
                            byteSize = if (original.isFile) original.length() else 0L,
                            lastModified = if (original.isFile) original.lastModified() else 0L,
                        )
                    }
                    val decoded = importer.decode(
                        ApiClient.json.encodeToString(snapshot),
                        facts,
                    )
                    db.withTransaction {
                        db.savedArtifactDao().clearLegacyOffline()
                        if (decoded.isNotEmpty()) db.savedArtifactDao().upsertAll(decoded)
                    }
            }
        }
    }

    private fun addToIndex(item: CachedArchive) {
        _index.update { idx ->
            OfflineIndex((idx.items.filter { it.arcid != item.arcid } + item).sortedBy { it.title.lowercase() })
        }
        persistIndex()
        recomputeUsage()
    }

    @Synchronized
    private fun persistIndex() {
        val persisted = runCatching {
            File(offlineDir, "index.json")
                .writeTextAtomically(ApiClient.json.encodeToString(_index.value))
            true
        }.getOrDefault(false)
        if (persisted) roomMirrorRevision.value += 1L
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
            scope.launch {
                persistIndex()
                savedArtifactRepository.find(artifactKey(arcid))?.let { savedArtifactRepository.put(it) }
            }
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
        val limitBytes = settingsRepository.settings.first().offlineCacheLimitBytes
        if (limitBytes <= 0L) return
        val cacheKeys = _index.value.items.mapTo(linkedSetOf()) { artifactKey(it.arcid) }
        when (val result = savedArtifactRepository.evictToCapacity(limitBytes, cacheKeys)) {
            is EvictionResult.Removed -> if (result.artifacts.isNotEmpty()) {
                val removedIds = result.artifacts.map { it.source.archiveId }.toSet()
                result.artifacts.forEach { artifact ->
                    File(artifact.path).parentFile?.deleteRecursively()
                    lastEvicted.value = artifact.source.archiveId
                }
                _index.update { current -> OfflineIndex(current.items.filterNot { it.arcid in removedIds }) }
                persistIndex()
                recomputeUsage()
            }
            is EvictionResult.CapacityBlocked -> recomputeUsage()
        }
    }

    suspend fun enforceConfiguredLimit() = enforceLimit()

    fun delete(arcid: String) {
        scope.launch { deleteSync(arcid) }
    }

    private suspend fun deleteSync(arcid: String) {
        downloadManager.cancelTasks(DownloadTaskType.OFFLINE_CACHE, arcid)
        savedArtifactRepository.remove(artifactKey(arcid))
        _index.update { OfflineIndex(it.items.filter { a -> a.arcid != arcid }) }
        persistIndex()
        File(offlineDir, arcid).deleteRecursively()
        recomputeUsage()
    }

    fun clearAll() {
        downloadManager.cancelTasks(DownloadTaskType.OFFLINE_CACHE, null)
        scope.launch {
            _index.value.items.forEach { savedArtifactRepository.remove(artifactKey(it.arcid)) }
            _index.value = OfflineIndex()
            persistIndex()
            offlineDir.listFiles()?.forEach { if (it.name != "index.json") it.deleteRecursively() }
            recomputeUsage()
        }
    }
}
