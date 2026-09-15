package com.lanraragi.reader.di

import android.content.Context
import com.lanraragi.reader.data.DownloadManager
import com.lanraragi.reader.data.FavoritesRepository
import com.lanraragi.reader.data.HistoryRepository
import com.lanraragi.reader.data.JobTracker
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.LocalScanManager
import com.lanraragi.reader.data.local.LocalLibraryIndexer
import com.lanraragi.reader.data.metadata.LanraragiMetadataGateway
import com.lanraragi.reader.data.metadata.MetadataRepository
import com.lanraragi.reader.data.metadata.RoomMetadataStateStore
import com.lanraragi.reader.data.metadata.plugins.LanraragiServerMetadataPluginGateway
import com.lanraragi.reader.data.metadata.plugins.RoomMetadataPluginJobStore
import com.lanraragi.reader.data.metadata.plugins.ServerMetadataPluginCoordinator
import com.lanraragi.reader.data.metadata.providers.HttpNativeMetadataGateway
import com.lanraragi.reader.data.metadata.providers.NativeMetadataFetchCoordinator
import com.lanraragi.reader.data.OfflineCacheManager
import com.lanraragi.reader.data.PendingProgressStore
import com.lanraragi.reader.data.SearchHistoryRepository
import com.lanraragi.reader.data.SettingsRepository
import com.lanraragi.reader.data.SplashCoverStore
import com.lanraragi.reader.data.TagTranslationRepository
import com.lanraragi.reader.data.tags.knowledge.RoomTagKnowledgeStore
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeRepository
import com.lanraragi.reader.data.UsageRepository
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.refreshServerInfo
import com.lanraragi.reader.data.assets.OkHttpThumbnailGateway
import com.lanraragi.reader.data.assets.ThumbnailRepository
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.db.AppDataBootstrapper
import com.lanraragi.reader.data.download.RoomDownloadTaskStore
import com.lanraragi.reader.data.download.RoomSavedArtifactStore
import com.lanraragi.reader.data.download.SavedArtifactRepository
import com.lanraragi.reader.data.download.DownloadCoordinator
import com.lanraragi.reader.data.download.LanraragiDownloadRunnerFactory
import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import com.lanraragi.reader.data.diagnostics.LogcatCrashCapture
import com.lanraragi.reader.data.history.RoomProgressTaskStore
import com.lanraragi.reader.data.reader.OutboxProgressWriter
import com.lanraragi.reader.data.ServerCapabilities
import com.lanraragi.reader.data.reader.ProgressWriter
import com.lanraragi.reader.data.reader.RepositoryProgressTransport
import com.lanraragi.reader.data.catalog.LanraragiLibraryRemoteGateway
import com.lanraragi.reader.data.catalog.MixedLibraryRepository
import com.lanraragi.reader.data.catalog.RoomLibraryLocalGateway
import com.lanraragi.reader.data.catalog.LibraryRequestCoordinator
import com.lanraragi.reader.ui.TagColorStore
import com.lanraragi.reader.ui.parseHexColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 简单的服务定位容器（无 Hilt），由 Application 持有。 */
class AppContainer(val context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Room is created once for the process; feature repositories own their DAOs. */
    val readerDatabase = ReaderDatabase.build(context)
    val appDataBootstrapper = AppDataBootstrapper(context, readerDatabase)
    val appDataBootstrap = applicationScope.async(Dispatchers.IO) {
        appDataBootstrapper.run()
    }

    val settingsRepository = SettingsRepository(context)
    val runtimeSettingsReady = applicationScope.async(Dispatchers.IO) {
        settingsRepository.applyToRuntime()
    }
    val favoritesRepository = FavoritesRepository(context)
    val repository = LanraragiRepository(ApiClient.api)
    val metadataRepository = MetadataRepository(
        remoteGateway = LanraragiMetadataGateway(repository),
        stateStore = RoomMetadataStateStore(readerDatabase),
    )
    val metadataPluginCoordinator = ServerMetadataPluginCoordinator(
        gateway = LanraragiServerMetadataPluginGateway(repository),
        store = RoomMetadataPluginJobStore(readerDatabase),
        wakeScope = applicationScope,
    )
    val nativeMetadataFetch = NativeMetadataFetchCoordinator(HttpNativeMetadataGateway())
    val tagKnowledgeStore = RoomTagKnowledgeStore(readerDatabase)
    val tagKnowledgeRepository = TagKnowledgeRepository(tagKnowledgeStore)
    val durableDownloadTaskStore = RoomDownloadTaskStore(readerDatabase)
    val savedArtifactRepository = SavedArtifactRepository(RoomSavedArtifactStore(readerDatabase))
    val diagnostics = DiagnosticsFacade()
    /** 崩溃日志抓取：注册全局未捕获异常处理器，开关由 captureLogcat 设置驱动。 */
    val logcatCrashCapture = LogcatCrashCapture(context)
    /** Durable coordinator is process-scoped; runners are registered by feature adapters. */
    val durableDownloadCoordinator = DownloadCoordinator(
        scope = applicationScope,
        store = durableDownloadTaskStore,
        registry = LanraragiDownloadRunnerFactory(repository, context.contentResolver).registry(),
        maxConcurrent = 2,
        diagnostics = diagnostics,
        onCompleted = { task, result ->
            if (task.spec is com.lanraragi.reader.data.download.DownloadTaskSpec.Archive &&
                !task.spec.destination.value.startsWith("content://")
            ) {
                savedArtifactRepository.put(
                    com.lanraragi.reader.data.download.SavedArtifact(
                        artifactKey = com.lanraragi.reader.data.download.SavedArtifactIdentity.key(task.spec.source),
                        source = task.spec.source,
                        path = task.spec.destination.value,
                        revision = result.entityTag ?: result.lastModified,
                        byteSize = result.completedBytes,
                        pinned = true,
                    ),
                )
            }
        },
    )
    val progressWriter: ProgressWriter = OutboxProgressWriter(
        store = RoomProgressTaskStore(readerDatabase),
        transport = RepositoryProgressTransport(repository),
        scope = applicationScope,
        // A3 能力门控：服务端未开启进度记录时不回传（/info 未就绪时保持乐观）。
        progressSupported = { ServerCapabilities(ApiClient.config.serverInfo.value).supportsProgress },
    )
    val libraryRepository = MixedLibraryRepository(
        remote = LanraragiLibraryRemoteGateway(
            repository,
            serverConfigured = { ApiClient.config.isConfigured },
        ),
        local = RoomLibraryLocalGateway(readerDatabase),
    )
    val libraryRequests = LibraryRequestCoordinator(applicationScope, libraryRepository)
    /** Process-scoped owner for remote cover generation and per-archive cache revisions. */
    val thumbnailRepository = ThumbnailRepository(
        gateway = OkHttpThumbnailGateway(),
        scope = applicationScope,
        serverKeyProvider = { ApiClient.config.baseUrl },
        diagnostics = diagnostics,
    )
    val downloadManager = DownloadManager(
        applicationScope,
        context,
        durableDownloadCoordinator,
        settingsRepository,
    )
    val offlineCache = OfflineCacheManager(
        context,
        ApiClient.api,
        downloadManager,
        settingsRepository,
        thumbnailRepository,
        savedArtifactRepository,
        readerDatabase,
        appDataBootstrap,
    )
    val tagTranslationRepository = TagTranslationRepository(context, tagKnowledgeRepository)
    /** 开屏封面：用户在设置里从图库选的一张图片（私有目录副本）。 */
    val splashCoverStore = SplashCoverStore(context)
    val searchHistoryRepository = SearchHistoryRepository(context)
    val searchDiscoveryRepository = com.lanraragi.reader.data.SearchDiscoveryRepository(context)
    val historyRepository = HistoryRepository(
        context,
        readerDatabase,
        applicationScope,
        appDataBootstrap,
    )
    val usageRepository = UsageRepository(context)
    val localLibraryIndexer = LocalLibraryIndexer(context, readerDatabase)
    val localScanManager = LocalScanManager(
        context,
        localLibraryIndexer,
        applicationScope,
        appDataBootstrap,
        diagnostics,
    )
    val jobTracker = JobTracker(repository, applicationScope)
    val pendingProgress = PendingProgressStore(context)

    init {
        // A3 启动时读一次 `/api/info` 做能力探测：服务器名/版本/server_tracks_progress/
        // total_pages_read 供统计页展示与后续能力门控使用。此前只在向导与设置页的连接动作里刷新，
        // 冷启动后 serverInfo 一直是 null，统计页的「累计阅读页数」永远显示「未知」。
        applicationScope.launch {
            runtimeSettingsReady.await()
            if (ApiClient.config.isConfigured) refreshServerInfo(repository)
        }

        // 所有服务器 Minion 异步任务（A4 整本页缩略图 / A7 重复检测 / A8 插件批量 / A10 备份恢复）
        // 入队后统一登记到 JobTracker，作为下载页「服务器任务」区的唯一数据源。
        // 此前没有任何地方调用 track()，该区域永远为空。
        repository.onJobQueued = { jobid, label -> jobTracker.track(jobid, label) }

        // 启动时加载本地缓存的标签翻译数据。
        tagTranslationRepository.load()
        applicationScope.launch {
            appDataBootstrap.await()
            tagTranslationRepository.loadKnowledge()
        }

        // 标签颜色只依赖 tagColors；这里只处理该状态，不再触发本地文件扫描。
        applicationScope.launch {
            settingsRepository.settings
                .map { it.tagColors }
                .distinctUntilChanged()
                .collect { tagColors ->
                    TagColorStore.overrides.value = tagColors.mapNotNull { (ns, hex) ->
                        parseHexColor(hex)?.let { ns to it }
                    }.toMap()
                }
        }

        // 下载并发限制由 DataStore 单一来源驱动，变更只影响后续派发的任务。
        applicationScope.launch {
            settingsRepository.settings
                .map { it.downloadConcurrency }
                .distinctUntilChanged()
                .collect { concurrency ->
                    downloadManager.setMaxConcurrent(concurrency)
                    durableDownloadCoordinator.setMaxConcurrent(concurrency)
                }
        }

        // C3 重试上限同样由 DataStore 单一来源驱动：变更实时应用到协调器，无需重建。
        applicationScope.launch {
            settingsRepository.settings
                .map { it.downloadMaxRetries }
                .distinctUntilChanged()
                .collect { retries ->
                    durableDownloadCoordinator.setMaxRetries(retries)
                }
        }

        // 崩溃日志抓取由 DataStore 单一来源驱动，实时跟随开关。
        applicationScope.launch {
            settingsRepository.settings
                .map { it.captureLogcat }
                .distinctUntilChanged()
                .collect { enabled ->
                    logcatCrashCapture.setEnabled(enabled)
                }
        }

        // 本地图库只在扫描根目录集合发生变化时重新扫描。
        applicationScope.launch {
            settingsRepository.settings
                .map { it.extraScanDirUris }
                .distinctUntilChanged()
                .collect { uris ->
                    localScanManager.scan(uris)
                }
        }

        // B4: App 启动时补推一次离线期间积压的进度回写。
        applicationScope.launch {
            runtimeSettingsReady.await()
            pendingProgress.flush(repository)
        }
        applicationScope.launch {
            appDataBootstrap.await()
            runtimeSettingsReady.await()
            durableDownloadCoordinator.recover()
        }
        applicationScope.launch {
            appDataBootstrap.await()
            runtimeSettingsReady.await()
            metadataPluginCoordinator.recover()
        }
        applicationScope.launch {
            appDataBootstrap.await()
            runtimeSettingsReady.await()
            progressWriter.flush()
        }
        // F4: 启动时执行收藏仓库的版本迁移（settings 的迁移由 applyToRuntime 触发）。
        applicationScope.launch {
            favoritesRepository.migrateIfNeeded()
        }
    }
}
