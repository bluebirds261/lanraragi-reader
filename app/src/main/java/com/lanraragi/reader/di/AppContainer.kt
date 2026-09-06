package com.lanraragi.reader.di

import android.content.Context
import com.lanraragi.reader.data.CheckinRepository
import com.lanraragi.reader.data.DownloadManager
import com.lanraragi.reader.data.FavoritesRepository
import com.lanraragi.reader.data.HistoryRepository
import com.lanraragi.reader.data.JobTracker
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.LocalScanManager
import com.lanraragi.reader.data.OfflineCacheManager
import com.lanraragi.reader.data.PendingProgressStore
import com.lanraragi.reader.data.SearchHistoryRepository
import com.lanraragi.reader.data.SettingsRepository
import com.lanraragi.reader.data.TagTranslationRepository
import com.lanraragi.reader.data.UsageRepository
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.ui.TagColorStore
import com.lanraragi.reader.ui.parseHexColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 简单的服务定位容器（无 Hilt），由 Application 持有。 */
class AppContainer(val context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(context)
    val favoritesRepository = FavoritesRepository(context)
    val repository = LanraragiRepository(ApiClient.api)
    val downloadManager = DownloadManager(applicationScope, context)
    val offlineCache = OfflineCacheManager(context, ApiClient.api, downloadManager, settingsRepository)
    val tagTranslationRepository = TagTranslationRepository(context)
    val searchHistoryRepository = SearchHistoryRepository(context)
    val historyRepository = HistoryRepository(context)
    val checkinRepository = CheckinRepository(context)
    val usageRepository = UsageRepository(context)
    val localScanManager = LocalScanManager(context)
    val jobTracker = JobTracker(repository, applicationScope)
    val pendingProgress = PendingProgressStore(context)

    init {
        // 启动时加载本地缓存的标签翻译数据。
        tagTranslationRepository.load()

        // 把持久化的自定义标签颜色同步到全局可观察状态，供着色组件实时读取。
        applicationScope.launch {
            settingsRepository.settings.collect { s ->
                TagColorStore.overrides.value = s.tagColors.mapNotNull { (ns, hex) ->
                    parseHexColor(hex)?.let { ns to it }
                }.toMap()
                localScanManager.scan(s.extraScanDirUris)
            }
        }
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
            pendingProgress.flush(repository)
        }
        // F4: 启动时执行收藏仓库的版本迁移（settings 的迁移由 applyToRuntime 触发）。
        applicationScope.launch {
            favoritesRepository.migrateIfNeeded()
        }
    }
}
