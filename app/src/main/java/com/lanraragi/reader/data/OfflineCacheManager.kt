package com.lanraragi.reader.data

import android.net.Uri
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
import com.lanraragi.reader.data.storage.StorageRoot
import com.lanraragi.reader.data.storage.StorageRootState
import com.lanraragi.reader.data.storage.storageUnavailableReason
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
import java.util.concurrent.ConcurrentHashMap

/** 离线缓存占用统计：总字节数 + 已索引档案数量。 */
data class OfflineUsage(val totalBytes: Long = 0, val count: Int = 0)

/**
 * 离线缓存：把档案的封面 + 原始文件保存到可配置的存储根（C6），从而在无网络时也能阅读。
 *
 * - 批量产物（原档/封面/元数据）经 [StorageRoot] 写入：默认根 = `filesDir/offline`（历史路径不变），
 *   自定义根 = 用户授权的 SAF tree。
 * - 索引 `index.json` 仍是元数据，永远存放在默认内部目录（保证可写、不占用户空间）。
 * - 入队前有存储门禁：授权失效/介质移除/目录不可写时拒绝写入并报告"存储不可用"。
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

    private companion object {
        const val INDEX_FILE_NAME = "index.json"
        const val ARCHIVE_FILE_NAME = "original.archive"
        const val COVER_FILE_NAME = "cover.img"
        const val META_FILE_NAME = "meta.json"
        const val PENDING_META_FILE_NAME = "meta.pending.json"
    }

    /** 默认内部目录：index.json 与历史版本数据的落点，路径保持不变。 */
    private val defaultOfflineDir = File(context.filesDir, "offline")
    private val indexFile = File(defaultOfflineDir, INDEX_FILE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _index = MutableStateFlow(OfflineIndex())
    val index: StateFlow<OfflineIndex> = _index.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _usage = MutableStateFlow(OfflineUsage())
    val usage: StateFlow<OfflineUsage> = _usage.asStateFlow()

    /** 最近一次被 LRU 淘汰的 arcid，供详情页提示“缓存已被替换”。 */
    val lastEvicted = MutableStateFlow<String?>(null)

    /** 存储根单一权威状态（degraded / lastError 观察点），直接转发 SettingsRepository。 */
    val storageState: StateFlow<StorageRootState> get() = settingsRepository.storageRootState

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
            if (indexFile.exists()) {
                _index.value = ApiClient.json.decodeFromString(indexFile.readTextAtomically())
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

    /**
     * 页图文件的默认目录路径（历史 API；当前页图不单独落盘）。
     * 仅默认存储根下可能存在真实文件；自定义 SAF 根时应改用 [archiveUri] 等访问器。
     */
    fun pageFile(arcid: String, page: Int): File {
        touch(arcid)
        return File(File(defaultOfflineDir, arcid), "pages/${page.toString().padStart(4, '0')}.img")
    }

    /**
     * 封面文件的默认目录路径（历史 API，保持语义不变：默认根下指向真实文件）。
     * 自定义 SAF 根时文件在 SAF 目录内，调用方应迁移到 [coverUri]（UI 批次处理）。
     */
    fun coverFile(arcid: String): File = File(defaultOfflineDir, "$arcid/$COVER_FILE_NAME")

    /**
     * 原档文件的默认目录路径（历史 API，保持语义不变：默认根下指向真实文件）。
     * 自定义 SAF 根时文件在 SAF 目录内，调用方应迁移到 [archiveUri] / [archiveReady]。
     */
    fun archiveFile(arcid: String): File = File(defaultOfflineDir, "$arcid/$ARCHIVE_FILE_NAME")

    // ---- URI 访问器：供 PageSource / 封面加载在自定义存储根下迁移使用（下一 UI 批次接线） ----

    /** 原档可读（存在且非空），自动兼顾默认根与自定义根。 */
    suspend fun archiveReady(arcid: String): Boolean {
        val root = settingsRepository.storageRootState.value.root
        val rel = "$arcid/$ARCHIVE_FILE_NAME"
        if (root.exists(rel) && root.sizeOf(rel) > 0L) return true
        val legacy = File(defaultOfflineDir, rel)
        return legacy.isFile && legacy.length() > 0L
    }

    /** 原档引用：自定义根返回 `content://` 文档 URI，默认根返回 `file://` URI；不可用返回 `null`。 */
    suspend fun archiveUri(arcid: String): Uri? = storageUriOf("$arcid/$ARCHIVE_FILE_NAME")

    /** 封面引用：自定义根返回 `content://` 文档 URI，默认根返回 `file://` URI；不可用返回 `null`。 */
    suspend fun coverUri(arcid: String): Uri? = storageUriOf("$arcid/$COVER_FILE_NAME")

    private suspend fun storageUriOf(relPath: String): Uri? {
        val path = settingsRepository.storageRootState.value.root.pathOf(relPath)
            ?: File(defaultOfflineDir, relPath).takeIf(File::isFile)?.absolutePath
            ?: return null
        return if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
    }

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
                // C6 存储门禁：授权失效/目录不可写时不产生任何批量产物。
                val state = settingsRepository.storageRootState.value
                val rejection = state.storageUnavailableReason()
                if (rejection != null) {
                    settingsRepository.reportStorageFailure(rejection)
                    return@launch
                }
                val root = state.root
                val destinationPath = root.ensureFile("$arcid/$ARCHIVE_FILE_NAME")
                if (destinationPath == null) {
                    settingsRepository.reportStorageFailure("无法在存储根创建离线档案文件")
                    return@launch
                }
                root.writeFile(
                    "$arcid/$PENDING_META_FILE_NAME",
                    ApiClient.json.encodeToString(archive).byteInputStream(),
                )
                val source = DownloadSourceIdentity(arcid, ApiClient.config.baseUrl)
                val saved = savedArtifactRepository.findBySource(source)
                val reused = saved?.takeIf { artifactUsable(it) }?.let { artifact ->
                    try {
                        com.lanraragi.reader.data.download.SavedArtifactExporter(context.contentResolver)
                            .export(artifact, DownloadDestination(destinationPath))
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
                            destination = DownloadDestination(destinationPath),
                            label = archive.title.ifBlank { arcid },
                        ),
                    )
                }
            } finally {
                startingCaches.remove(arcid)
            }
        }
    }

    /** 已保存原档仍可用（默认根 = 本地文件；自定义根 = 可打开的 SAF 文档）。 */
    private suspend fun artifactUsable(artifact: SavedArtifact): Boolean {
        val path = artifact.path
        if (path.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { it.read() >= 0 } == true
            }.getOrDefault(false)
        }
        val file = File(path)
        return file.isFile && file.length() > 0L
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
                    .forEach { task ->
                        if (finalizingTasks.add(task.id)) {
                            scope.launch {
                                try {
                                    val root = settingsRepository.storageRootState.value.root
                                    val hasPending = root.exists("${task.arcid}/$PENDING_META_FILE_NAME")
                                    if (hasPending || !isCached(task.arcid)) {
                                        finalizeDurableCache(task.arcid)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // 收尾失败（如存储不可写）不使进程崩溃；索引保持原状，等下次触发重试。
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
        val root = settingsRepository.storageRootState.value.root
        val archiveRel = "$arcid/$ARCHIVE_FILE_NAME"
        val archiveSize = root.sizeOf(archiveRel)
        require(root.exists(archiveRel) && archiveSize > 0L) { "离线档案文件不可用" }
        val pendingRel = "$arcid/$PENDING_META_FILE_NAME"
        val pendingJson = root.readFile(pendingRel)?.use { it.bufferedReader().readText() }
        val archive = runCatching {
            ApiClient.json.decodeFromString<Archive>(pendingJson ?: "")
        }.getOrNull() ?: Archive(arcid = arcid, title = arcid)
        val readyCover = when (val state = thumbnailRepository.awaitCover(arcid)) {
            is CoverState.Ready -> state
            is CoverState.Failed -> state.lastGood
            is CoverState.Generating -> state.lastGood
            is CoverState.Unrequested -> null
        }
        val coverOk = readyCover?.let { downloadTo(root, it.resource.value, "$arcid/$COVER_FILE_NAME") } == true
        val cached = CachedArchive(
            arcid = arcid,
            title = archive.title,
            pageCount = archive.pagecount,
            coverExists = coverOk,
            metadata = archive,
            isLocal = false,
            lastAccess = System.currentTimeMillis(),
        )
        root.writeFile("$arcid/$META_FILE_NAME", ApiClient.json.encodeToString(cached).byteInputStream())
        val source = DownloadSourceIdentity(arcid, ApiClient.config.baseUrl)
        val existingArtifact = savedArtifactRepository.findBySource(source)
        savedArtifactRepository.put(
            SavedArtifact(
                artifactKey = artifactKey(arcid),
                source = source,
                path = root.pathOf(archiveRel) ?: archiveRel,
                revision = "offline:$archiveSize:${root.lastModifiedOf(archiveRel)}",
                byteSize = archiveSize,
                pinned = existingArtifact?.pinned == true,
                lastAccessAtEpochMs = cached.lastAccess,
            ),
        )
        addToIndex(cached)
        root.delete(pendingRel)
        enforceLimit()
    }

    private fun artifactKey(arcid: String): String = com.lanraragi.reader.data.download.SavedArtifactIdentity.key(
        DownloadSourceIdentity(arcid, ApiClient.config.baseUrl),
    )

    /** 通过流式响应把封面等小文件写入存储根。 */
    private suspend fun downloadTo(root: StorageRoot, url: String, relPath: String): Boolean = try {
        val req = Request.Builder().url(url).build()
        ApiClient.okHttpClient.newCall(req).execute().use { resp ->
            val body = resp.body
            when {
                !resp.isSuccessful || body == null -> false
                else -> root.writeFile(relPath, body.byteStream())
            }
        }
    } catch (e: CancellationException) {
        // 协程取消必须向上传播，不能被 runCatching 折叠成 false。
        throw e
    } catch (_: Exception) {
        false
    }

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
                    val root = settingsRepository.storageRootState.value.root
                    val facts = snapshot.items.map { item -> artifactFacts(root, item.arcid) }
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

    /** 原档事实：优先当前存储根，回退默认目录中的历史数据（自定义根切换后的兼容路径）。 */
    private suspend fun artifactFacts(root: StorageRoot, arcid: String): OfflineIndexLegacyImporter.ArtifactFacts {
        val rel = "$arcid/$ARCHIVE_FILE_NAME"
        val size = root.sizeOf(rel)
        if (size > 0L) {
            return OfflineIndexLegacyImporter.ArtifactFacts(
                arcid = arcid,
                filePath = root.pathOf(rel) ?: rel,
                exists = true,
                byteSize = size,
                lastModified = root.lastModifiedOf(rel),
            )
        }
        val legacy = File(defaultOfflineDir, rel)
        return OfflineIndexLegacyImporter.ArtifactFacts(
            arcid = arcid,
            filePath = legacy.absolutePath,
            exists = legacy.isFile,
            byteSize = if (legacy.isFile) legacy.length() else 0L,
            lastModified = if (legacy.isFile) legacy.lastModified() else 0L,
        )
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
            indexFile.writeTextAtomically(ApiClient.json.encodeToString(_index.value))
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

    /**
     * 重新统计离线产物磁盘占用（当前存储根的递归大小；默认根扣除 index.json 本身）与索引数量。
     */
    private fun recomputeUsage() {
        scope.launch {
            val root = settingsRepository.storageRootState.value.root
            val totalBytes = runCatching {
                val total = root.sizeOf("")
                if (root.isCustom) total else total - indexFile.length()
            }.getOrDefault(0L).coerceAtLeast(0L)
            _usage.value = OfflineUsage(totalBytes = totalBytes, count = _index.value.items.size)
        }
    }

    /** 超出上限时按 lastAccess 升序（最早访问优先）淘汰，直到落回上限内。 */
    private suspend fun enforceLimit() {
        val limitBytes = settingsRepository.settings.first().offlineCacheLimitBytes
        if (limitBytes <= 0L) return
        val cacheKeys = _index.value.items.mapTo(linkedSetOf()) { artifactKey(it.arcid) }
        when (val result = savedArtifactRepository.evictToCapacity(limitBytes, cacheKeys)) {
            is EvictionResult.Removed -> if (result.artifacts.isNotEmpty()) {
                val root = settingsRepository.storageRootState.value.root
                val removedIds = result.artifacts.map { it.source.archiveId }.toSet()
                result.artifacts.forEach { artifact ->
                    runCatching { root.deleteDir(artifact.source.archiveId) }
                    // 兼容历史数据：产物可能仍落在默认目录；只删产物文件本身，不再递归删除父目录。
                    File(defaultOfflineDir, artifact.source.archiveId).takeIf(File::isDirectory)?.deleteRecursively()
                    if (!artifact.path.startsWith("content://")) {
                        File(artifact.path).takeIf(File::isFile)?.delete()
                    }
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
        val root = settingsRepository.storageRootState.value.root
        runCatching { root.deleteDir(arcid) }
        // 自定义根时同时清理默认目录里的历史遗留数据。
        if (root.isCustom) {
            File(defaultOfflineDir, arcid).takeIf(File::isDirectory)?.deleteRecursively()
        }
        recomputeUsage()
    }

    fun clearAll() {
        downloadManager.cancelTasks(DownloadTaskType.OFFLINE_CACHE, null)
        scope.launch {
            _index.value.items.forEach { savedArtifactRepository.remove(artifactKey(it.arcid)) }
            _index.value = OfflineIndex()
            persistIndex()
            val root = settingsRepository.storageRootState.value.root
            runCatching {
                root.walkFiles("")
                    .filter { it != INDEX_FILE_NAME }
                    .map { it.substringBefore('/') }
                    .toList()
                    .forEach { root.deleteDir(it) }
            }
            if (root.isCustom) {
                // 同时清理默认目录中的历史遗留数据（index.json 除外）。
                defaultOfflineDir.listFiles()?.forEach { if (it.name != INDEX_FILE_NAME) it.deleteRecursively() }
            }
            recomputeUsage()
        }
    }
}
