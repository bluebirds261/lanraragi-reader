package com.lanraragi.reader.di

import android.content.Context
import com.lanraragi.reader.data.CheckinRepository
import com.lanraragi.reader.data.DownloadManager
import com.lanraragi.reader.data.FavoritesRepository
import com.lanraragi.reader.data.FeatureFlags
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
import com.lanraragi.reader.data.TagTranslationRepository
import com.lanraragi.reader.data.tags.knowledge.RoomTagKnowledgeStore
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeRepository
import com.lanraragi.reader.data.UsageRepository
import com.lanraragi.reader.data.api.ApiClient
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
import com.lanraragi.reader.data.favorites.EhFavoriteCredentialStore
import com.lanraragi.reader.data.favorites.EhFavoriteCategorySyncService
import com.lanraragi.reader.data.favorites.EhFavoritesRepository
import com.lanraragi.reader.data.favorites.EhentaiFavoriteGateway
import com.lanraragi.reader.data.favorites.LanraragiEhFavoriteGateway
import com.lanraragi.reader.data.favorites.LanraragiEhFavoriteArchiveLinkGateway
import com.lanraragi.reader.data.favorites.RoomEhFavoriteStore
import com.lanraragi.reader.data.history.RoomProgressTaskStore
import com.lanraragi.reader.data.reader.OutboxProgressWriter
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
    val thumbnailEnabled = settingsRepository.settings
        .map { FeatureFlags.isEnabled(it, FeatureFlags.THUMBNAILS) }
        .distinctUntilChanged()
        .stateIn(applicationScope, SharingStarted.Eagerly, false)
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
    )
    val libraryRepository = MixedLibraryRepository(
        remote = LanraragiLibraryRemoteGateway(repository),
        local = RoomLibraryLocalGateway(readerDatabase),
    )
    val libraryRequests = LibraryRequestCoordinator(applicationScope, libraryRepository)
    val ehFavoriteStore = RoomEhFavoriteStore(readerDatabase.ehFavoriteDao())
    val ehFavoriteCredentials = EhFavoriteCredentialStore(context)
    val ehFavoritesRepository = EhFavoritesRepository(
        store = ehFavoriteStore,
        accountConnector = ehFavoriteCredentials,
        gateway = EhentaiFavoriteGateway(),
    )
    val ehFavoriteCategorySync = EhFavoriteCategorySyncService(
        mappings = ehFavoriteStore,
        snapshots = ehFavoriteStore,
        categoryGateway = LanraragiEhFavoriteGateway(repository),
        archiveLinks = LanraragiEhFavoriteArchiveLinkGateway(repository),
    )
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
    val searchHistoryRepository = SearchHistoryRepository(context)
    val historyRepository = HistoryRepository(
        context,
        readerDatabase,
        applicationScope,
        appDataBootstrap,
    )
    val checkinRepository = CheckinRepository(context)
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
        applicationScope.launch {
            appDataBootstrap.await()
            ehFavoritesRepository.loadCached()
        }
    }
}
