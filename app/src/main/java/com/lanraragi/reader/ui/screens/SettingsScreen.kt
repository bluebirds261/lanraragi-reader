package com.lanraragi.reader.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanraragi.reader.R
import com.lanraragi.reader.data.AUTO_SCROLL_MAX_SECONDS
import com.lanraragi.reader.data.AUTO_SCROLL_MIN_SECONDS
import com.lanraragi.reader.data.ReaderOrientationProfile
import com.lanraragi.reader.data.ConfigImportPreview
import com.lanraragi.reader.data.DuplicateDetectionQueue
import com.lanraragi.reader.data.OfflineUsage
import com.lanraragi.reader.data.autoScrollSeconds
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.ServerInfo
import com.lanraragi.reader.data.model.ServerProfile
import com.lanraragi.reader.data.model.ServerStats
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.data.normalizeBaseUrl
import com.lanraragi.reader.data.normalizeAutoScrollSpeed
import com.lanraragi.reader.data.refreshServerInfo
import com.lanraragi.reader.data.SplashCoverStore
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.ColorPickerDialog
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.parseHexColor
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.reader.TapZoneAction
import com.lanraragi.reader.ui.reader.TapZoneGrid
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.toHexString
import com.lanraragi.reader.ui.settings.OfflineCacheLimitEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import kotlin.math.roundToInt

private fun themeLabel(t: String) = when (t) { "dark" -> "深色"; "light" -> "浅色"; else -> "跟随系统" }
private fun viewModeLabel(v: String) = when (v) {
    "list" -> "列表"
    "compact" -> "紧凑网格"
    else -> "松散网格"
}
private fun readerModeLabel(m: String) = when (m) { "multi" -> "多页"; "continuous" -> "连续"; else -> "单页" }
private fun directionLabel(d: String) = when (d) {
    "rtl" -> "右→左"
    "ttb" -> "上→下"
    else -> "左→右"
}
private fun fitModeLabel(f: String) = when (f) {
    "fitHeight" -> "适应高度"; "fitScreen" -> "适应屏幕"; "original" -> "原始尺寸"; else -> "适应宽度"
}
private fun backgroundLabel(b: String) = when (b) {
    "auto" -> "自动"
    "dark" -> "深灰"
    "gray" -> "灰"
    "white" -> "白"
    else -> "黑"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    }
}

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    MAIN("设置", Icons.Filled.Settings),
    CONNECT("连接", Icons.Filled.Link),
    READER("阅读", Icons.Filled.AutoStories),
    TAP_ZONE("点击分区自定义", Icons.Filled.TouchApp),
    INTERFACE("外观", Icons.Filled.Palette),
    TOOLS("工具", Icons.Filled.Handyman),
    DATA("数据", Icons.Filled.Folder),
    SECURITY("安全", Icons.Filled.Lock),
    PERMISSIONS("权限", Icons.Filled.AdminPanelSettings),
    DEBUG("调试", Icons.Filled.BugReport),
    ABOUT("关于", Icons.Filled.Info),
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    var importPreview by mutableStateOf<ConfigImportPreview?>(null)
        private set

    data class UiState(
        val url: String = "",
        val key: String = "",
        val name: String = "",
        val readerMode: String = "single",
        val readerOrientationProfile: ReaderOrientationProfile = ReaderOrientationProfile.GLOBAL,
        val multiPageCount: Int = 2,
        val autoDoublePageLandscape: Boolean = true,
        val preloadOnlineCount: Int = 3,
        val preloadLocalCount: Int = 5,
        val readingDirection: String = "ltr",
        val autoScrollSpeed: String = "3",
        val downloadDirUri: String? = null,
        val galleryColumns: Int = 3,
        val previewColumns: Int = 4,
        val previewCount: Int = 12,
        val coverPrefetchCount: Int = 12,
        val theme: String = "system",
        val readerFitMode: String = "fitWidth",
        val readerModePortrait: String? = null,
        val readerModeLandscape: String? = null,
        val readingDirectionPortrait: String? = null,
        val readingDirectionLandscape: String? = null,
        val readerFitModePortrait: String? = null,
        val readerFitModeLandscape: String? = null,
        val doublePageFirstPageAlone: Boolean = false,
        val doublePageFirstPageAlonePortrait: Boolean? = null,
        val doublePageFirstPageAloneLandscape: Boolean? = null,
        val readerBackground: String = "black",
        val tapZonesEnabled: Boolean = true,
        val tapZoneGrid: TapZoneGrid = TapZoneGrid.default(),
        val keepScreenOn: Boolean = true,
        val volumeKeysEnabled: Boolean = true,
        val clearNewOnOpen: Boolean = true,
        val readerPageOverlay: Boolean = true,
        val galleryViewMode: String = "grid",
        val tagColors: Map<String, String> = emptyMap(),
        val galleryTitle: String = "图库",
        val galleryTitleMode: String = "default",
        val showArchiveCount: Boolean = true,
        /** Android 12+ 动态取色（Material You）；由 MainActivity 应用到主题。 */
        val dynamicColor: Boolean = false,
        val showFloatingButton: Boolean = true,
        val floatingButtonColor: String = "#E94560",
        val bottomBarColor: String = "#1E2835",
        val bottomBarActiveColor: String = "#0084FF",
        val authEnabled: Boolean = false,
        val biometricEnabled: Boolean = false,
        val screenshotProtectionEnabled: Boolean = false,
        val hideInGallery: Boolean = false,
        val blurInRecents: Boolean = false,
        val captureLogcat: Boolean = false,
        val extraScanDirUris: Set<String> = emptySet(),
        val offlineCacheLimitBytes: Long = 0L,
        val downloadConcurrency: Int = 2,
        val downloadRatePerSecond: Int = 2,
        /** C3 单个任务的自动重试上限（1..10，默认 5）。 */
        val downloadMaxRetries: Int = 5,
        val profiles: List<ServerProfile> = emptyList(),
        val activeProfileIndex: Int = 0,
        val storageRootLabel: String = "",
        val storageRootCustom: Boolean = false,
        val storageDegradedReason: String? = null,
        val scrapeSource: String = "ehentai",
        val scrapeConfidence: Int = 70,
        val scrapeSkipTagged: Boolean = true,
        val tagTranslationAutoUpdate: Boolean = false,
        val offlineUsage: OfflineUsage = OfflineUsage(),
        /** 开屏封面版本：0 = 未设置，非 0 = 已设置（值同时用作 Coil 的缓存键版本）。 */
        val splashCoverVersion: Long = 0L,
        val isScanning: Boolean = false,
        val translationUpdating: Boolean = false,
        val duplicateCheckLoading: Boolean = false,
        val testing: Boolean = false,
        val saving: Boolean = false,
        val shinobuLoading: Boolean = false,
        val shinobuAlive: Boolean? = null,
        val regenThumbsLoading: Boolean = false,
        val backupLoading: Boolean = false,
        val restoreLoading: Boolean = false,
        val message: String? = null,
        val success: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.localScanManager.isScanning.collect { s ->
                _state.update { it.copy(isScanning = s) }
            }
        }
        viewModelScope.launch {
            container.offlineCache.usage.collect { u ->
                _state.update { it.copy(offlineUsage = u) }
            }
        }
        viewModelScope.launch {
            container.splashCoverStore.version.collect { v ->
                _state.update { it.copy(splashCoverVersion = v) }
            }
        }
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            _state.update {
                it.copy(
                    url = s.baseUrl,
                    key = s.apiKey,
                    name = s.serverName,
                    readerMode = s.readerMode,
                    multiPageCount = s.multiPageCount,
                    autoDoublePageLandscape = s.autoDoublePageLandscape,
                    preloadOnlineCount = s.preloadOnlineCount,
                    preloadLocalCount = s.preloadLocalCount,
                    readingDirection = s.readingDirection,
                    autoScrollSpeed = s.autoScrollSpeed,
                    downloadDirUri = s.downloadDirUri,
                    galleryColumns = s.galleryColumns,
                    previewColumns = s.previewColumns,
                    previewCount = s.previewCount,
                    coverPrefetchCount = s.coverPrefetchCount,
                    theme = s.theme,
                    readerFitMode = s.readerFitMode,
                    readerModePortrait = s.readerModePortrait,
                    readerModeLandscape = s.readerModeLandscape,
                    readingDirectionPortrait = s.readingDirectionPortrait,
                    readingDirectionLandscape = s.readingDirectionLandscape,
                    readerFitModePortrait = s.readerFitModePortrait,
                    readerFitModeLandscape = s.readerFitModeLandscape,
                    doublePageFirstPageAlone = s.doublePageFirstPageAlone,
                    doublePageFirstPageAlonePortrait = s.doublePageFirstPageAlonePortrait,
                    doublePageFirstPageAloneLandscape = s.doublePageFirstPageAloneLandscape,
                    readerBackground = s.readerBackground,
                    tapZonesEnabled = s.tapZonesEnabled,
                    tapZoneGrid = TapZoneGrid.deserialize(s.tapZoneConfig),
                    keepScreenOn = s.keepScreenOn,
                    volumeKeysEnabled = s.volumeKeysEnabled,
                    clearNewOnOpen = s.clearNewOnOpen,
                    readerPageOverlay = s.readerPageOverlay,
                    galleryViewMode = s.galleryViewMode,
                    tagColors = s.tagColors,
                    galleryTitle = s.galleryTitle,
                    galleryTitleMode = s.galleryTitleMode,
                    showArchiveCount = s.showArchiveCount,
                    showFloatingButton = s.showFloatingButton,
                    floatingButtonColor = s.floatingButtonColor,
                    bottomBarColor = s.bottomBarColor,
                    bottomBarActiveColor = s.bottomBarActiveColor,
                    authEnabled = s.authEnabled,
                    biometricEnabled = s.biometricEnabled,
                    screenshotProtectionEnabled = s.screenshotProtectionEnabled,
                    hideInGallery = s.hideInGallery,
                    blurInRecents = s.blurInRecents,
                    captureLogcat = s.captureLogcat,
                    extraScanDirUris = s.extraScanDirUris,
                    offlineCacheLimitBytes = s.offlineCacheLimitBytes,
                    downloadConcurrency = s.downloadConcurrency,
                )
            }
        }
        // 服务器 profile 与下载速率/刮削策略随 DataStore 变化实时刷新（不覆盖 url/key 等编辑中字段）。
        viewModelScope.launch {
            container.settingsRepository.settings.collect { s ->
                _state.update {
                    it.copy(
                        profiles = s.profiles,
                        activeProfileIndex = s.activeProfileIndex,
                        downloadRatePerSecond = s.downloadRatePerSecond,
                        downloadMaxRetries = s.downloadMaxRetries,
                        scrapeSource = s.scrapeSource,
                        scrapeConfidence = s.scrapeConfidence,
                        scrapeSkipTagged = s.scrapeSkipTagged,
                        tagTranslationAutoUpdate = s.tagTranslationAutoUpdate,
                        // 动态取色也可能被向导改写，这里跟随 DataStore 单一来源实时刷新。
                        dynamicColor = s.dynamicColor,
                    )
                }
            }
        }
        // 存储根状态（C6 单一权威流）：展示名 + 降级原因。
        viewModelScope.launch {
            container.settingsRepository.storageRootState.collect { s ->
                _state.update {
                    it.copy(
                        storageRootLabel = s.root.displayName(),
                        storageRootCustom = s.root.isCustom,
                        storageDegradedReason = s.degradedReason,
                    )
                }
            }
        }
    }

    fun onUrlChange(v: String) = _state.update { it.copy(url = v, success = false, message = null) }
    fun onKeyChange(v: String) = _state.update { it.copy(key = v, success = false, message = null) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v, success = false, message = null) }

    fun setReaderMode(mode: String) {
        _state.update { it.copy(readerMode = mode) }
        viewModelScope.launch { container.settingsRepository.setReaderModeForOrientation(_state.value.readerOrientationProfile, mode) }
    }

    fun setReaderOrientationProfile(profile: ReaderOrientationProfile) {
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            val mode: String
            val direction: String
            val fitMode: String
            val firstPageAlone: Boolean
            when (profile) {
                ReaderOrientationProfile.GLOBAL -> {
                    mode = s.readerMode
                    direction = s.readingDirection
                    fitMode = s.readerFitMode
                    firstPageAlone = s.doublePageFirstPageAlone
                }
                ReaderOrientationProfile.PORTRAIT -> {
                    mode = s.readerModePortrait ?: s.readerMode
                    direction = s.readingDirectionPortrait ?: s.readingDirection
                    fitMode = s.readerFitModePortrait ?: s.readerFitMode
                    firstPageAlone = s.doublePageFirstPageAlonePortrait ?: s.doublePageFirstPageAlone
                }
                ReaderOrientationProfile.LANDSCAPE -> {
                    mode = s.readerModeLandscape ?: s.readerMode
                    direction = s.readingDirectionLandscape ?: s.readingDirection
                    fitMode = s.readerFitModeLandscape ?: s.readerFitMode
                    firstPageAlone = s.doublePageFirstPageAloneLandscape ?: s.doublePageFirstPageAlone
                }
            }
            _state.update {
                it.copy(
                    readerOrientationProfile = profile,
                    readerMode = mode,
                    readingDirection = direction,
                    readerFitMode = fitMode,
                    doublePageFirstPageAlone = firstPageAlone,
                )
            }
        }
    }

    fun clearReaderOrientationOverrides(profile: ReaderOrientationProfile) {
        if (profile == ReaderOrientationProfile.GLOBAL) return
        viewModelScope.launch {
            container.settingsRepository.setReaderModeForOrientation(profile, null)
            container.settingsRepository.setReadingDirectionForOrientation(profile, null)
            container.settingsRepository.setReaderFitModeForOrientation(profile, null)
            container.settingsRepository.setFirstPageAloneForOrientation(profile, null)
            val s = container.settingsRepository.settings.first()
            _state.update {
                it.copy(
                    readerMode = s.readerMode,
                    readingDirection = s.readingDirection,
                    readerFitMode = s.readerFitMode,
                    doublePageFirstPageAlone = s.doublePageFirstPageAlone,
                )
            }
        }
    }

    fun setAutoScrollSpeed(s: String) {
        val normalized = normalizeAutoScrollSpeed(s)
        _state.update { it.copy(autoScrollSpeed = normalized) }
        viewModelScope.launch { container.settingsRepository.setAutoScrollSpeed(normalized) }
    }

    fun setMultiPageCount(n: Int) {
        _state.update { it.copy(multiPageCount = n) }
        viewModelScope.launch { container.settingsRepository.setMultiPageCount(n) }
    }

    fun setAutoDoublePageLandscape(enabled: Boolean) {
        _state.update { it.copy(autoDoublePageLandscape = enabled) }
        viewModelScope.launch { container.settingsRepository.setAutoDoublePageLandscape(enabled) }
    }

    fun setPreloadOnlineCount(n: Int) {
        _state.update { it.copy(preloadOnlineCount = n) }
        viewModelScope.launch { container.settingsRepository.setPreloadOnlineCount(n) }
    }

    fun setPreloadLocalCount(n: Int) {
        _state.update { it.copy(preloadLocalCount = n) }
        viewModelScope.launch { container.settingsRepository.setPreloadLocalCount(n) }
    }

    fun setReadingDirection(dir: String) {
        _state.update { it.copy(readingDirection = dir) }
        viewModelScope.launch { container.settingsRepository.setReadingDirectionForOrientation(_state.value.readerOrientationProfile, dir) }
    }

    fun setDownloadDirUri(uri: String?) {
        _state.update { it.copy(downloadDirUri = uri) }
        viewModelScope.launch { container.settingsRepository.setDownloadDirUri(uri) }
    }

    fun setGalleryColumns(n: Int) {
        _state.update { it.copy(galleryColumns = n) }
        viewModelScope.launch { container.settingsRepository.setGalleryColumns(n) }
    }

    fun setPreviewColumns(n: Int) {
        _state.update { it.copy(previewColumns = n) }
        viewModelScope.launch { container.settingsRepository.setPreviewColumns(n) }
    }

    fun setPreviewCount(n: Int) {
        _state.update { it.copy(previewCount = n) }
        viewModelScope.launch { container.settingsRepository.setPreviewCount(n) }
    }

    fun setCoverPrefetchCount(n: Int) {
        _state.update { it.copy(coverPrefetchCount = n) }
        viewModelScope.launch { container.settingsRepository.setCoverPrefetchCount(n) }
    }

    fun setTheme(theme: String) {
        _state.update { it.copy(theme = theme) }
        viewModelScope.launch { container.settingsRepository.setTheme(theme) }
    }

    /** 动态取色（Material You）：仅 Android 12+ 生效，写入后由 MainActivity 观察 settings.dynamicColor 应用主题。 */
    fun setDynamicColor(enabled: Boolean) {
        _state.update { it.copy(dynamicColor = enabled) }
        viewModelScope.launch { container.settingsRepository.saveDynamicColor(enabled) }
    }

    fun setReaderFitMode(mode: String) {
        _state.update { it.copy(readerFitMode = mode) }
        viewModelScope.launch { container.settingsRepository.setReaderFitModeForOrientation(_state.value.readerOrientationProfile, mode) }
    }

    fun setReaderBackground(bg: String) {
        _state.update { it.copy(readerBackground = bg) }
        viewModelScope.launch { container.settingsRepository.setReaderBackground(bg) }
    }

    fun setTapZonesEnabled(enabled: Boolean) {
        _state.update { it.copy(tapZonesEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(enabled) }
    }

    fun setTapZoneGrid(grid: TapZoneGrid) {
        _state.update { it.copy(tapZoneGrid = grid) }
        viewModelScope.launch { container.settingsRepository.saveTapZoneConfig(grid.serialized()) }
    }

    fun cycleTapZoneCell(index: Int) {
        if (index !in 0..8) return
        val entries = TapZoneAction.entries
        val grid = _state.value.tapZoneGrid
        if (index >= grid.cells.size) return
        val current = entries.getOrElse(grid.cells[index]) { TapZoneAction.NONE }
        val next = entries[(current.ordinal + 1) % entries.size]
        setTapZoneGrid(grid.copy(cells = grid.cells.toMutableList().also { it[index] = next.ordinal }))
    }

    fun resetTapZoneGrid() {
        _state.update { it.copy(tapZoneGrid = TapZoneGrid.default()) }
        viewModelScope.launch { container.settingsRepository.saveTapZoneConfig(null) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _state.update { it.copy(keepScreenOn = enabled) }
        viewModelScope.launch { container.settingsRepository.setKeepScreenOn(enabled) }
    }

    fun setVolumeKeysEnabled(enabled: Boolean) {
        _state.update { it.copy(volumeKeysEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setVolumeKeysEnabled(enabled) }
    }

    fun setClearNewOnOpen(enabled: Boolean) {
        _state.update { it.copy(clearNewOnOpen = enabled) }
        viewModelScope.launch { container.settingsRepository.setClearNewOnOpen(enabled) }
    }

    fun setReaderPageOverlay(enabled: Boolean) {
        _state.update { it.copy(readerPageOverlay = enabled) }
        viewModelScope.launch { container.settingsRepository.saveReaderPageOverlay(enabled) }
    }

    fun setGalleryViewMode(mode: String) {
        _state.update { it.copy(galleryViewMode = mode) }
        viewModelScope.launch { container.settingsRepository.setGalleryViewMode(mode) }
    }

    fun setTagColor(ns: String, hex: String) {
        val normalized = hex.uppercase()
        _state.update { it.copy(tagColors = it.tagColors + (ns to normalized)) }
        viewModelScope.launch { container.settingsRepository.setTagColor(ns, normalized) }
    }

    fun resetTagColor(ns: String) {
        _state.update { it.copy(tagColors = it.tagColors - ns) }
        viewModelScope.launch { container.settingsRepository.resetTagColor(ns) }
    }

    fun setGalleryTitle(title: String) {
        _state.update { it.copy(galleryTitle = title) }
        viewModelScope.launch { container.settingsRepository.setGalleryTitle(title) }
    }

    fun setGalleryTitleMode(mode: String) {
        _state.update { it.copy(galleryTitleMode = mode) }
        viewModelScope.launch { container.settingsRepository.setGalleryTitleMode(mode) }
    }

    fun setShowArchiveCount(show: Boolean) {
        _state.update { it.copy(showArchiveCount = show) }
        viewModelScope.launch { container.settingsRepository.setShowArchiveCount(show) }
    }

    fun setShowFloatingButton(show: Boolean) {
        _state.update { it.copy(showFloatingButton = show) }
        viewModelScope.launch { container.settingsRepository.setShowFloatingButton(show) }
    }

    fun setFloatingButtonColor(hex: String) {
        val normalized = hex.uppercase()
        _state.update { it.copy(floatingButtonColor = normalized) }
        viewModelScope.launch { container.settingsRepository.setFloatingButtonColor(normalized) }
    }

    fun setBottomBarColor(hex: String) {
        val normalized = hex.uppercase()
        _state.update { it.copy(bottomBarColor = normalized) }
        viewModelScope.launch { container.settingsRepository.setBottomBarColor(normalized) }
    }

    fun setBottomBarActiveColor(hex: String) {
        val normalized = hex.uppercase()
        _state.update { it.copy(bottomBarActiveColor = normalized) }
        viewModelScope.launch { container.settingsRepository.setBottomBarActiveColor(normalized) }
    }

    fun setAuthEnabled(enabled: Boolean) {
        _state.update { it.copy(authEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setAuthEnabled(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && BiometricManager.from(container.context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            _state.update { it.copy(message = "此设备未配置可用的强生物认证，未启用生物认证", success = false) }
            return
        }
        _state.update { it.copy(biometricEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setBiometricEnabled(enabled) }
    }

    fun setDoublePageFirstPageAlone(enabled: Boolean) {
        _state.update { it.copy(doublePageFirstPageAlone = enabled) }
        viewModelScope.launch {
            container.settingsRepository.setFirstPageAloneForOrientation(_state.value.readerOrientationProfile, enabled)
        }
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        _state.update { it.copy(screenshotProtectionEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setScreenshotProtectionEnabled(enabled) }
    }

    fun exportConfig(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = container.settingsRepository.exportNonSensitiveConfig()
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    ?: throw IOException("无法写入配置文件")
                _state.update { it.copy(message = "配置导出成功", success = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "配置导出失败", success = false) }
            }
        }
    }

    fun importConfig(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IOException("无法读取配置文件")
                importPreview = container.settingsRepository.previewNonSensitiveConfig(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "配置导入失败", success = false) }
            }
        }
    }

    fun confirmConfigImport() {
        val preview = importPreview ?: return
        importPreview = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.settingsRepository.importNonSensitiveConfig(preview.payload)
                val imported = container.settingsRepository.settings.first()
                _state.update {
                    it.copy(
                        readerMode = imported.readerMode,
                        theme = imported.theme,
                        biometricEnabled = imported.biometricEnabled,
                        blurInRecents = imported.blurInRecents,
                        screenshotProtectionEnabled = imported.screenshotProtectionEnabled,
                        message = "配置导入成功",
                        success = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "配置导入失败", success = false) }
            }
        }
    }

    fun cancelConfigImport() {
        importPreview = null
    }

    fun setHideInGallery(enabled: Boolean) {
        _state.update { it.copy(hideInGallery = enabled) }
        viewModelScope.launch { container.settingsRepository.setHideInGallery(enabled) }
    }

    fun setBlurInRecents(enabled: Boolean) {
        _state.update { it.copy(blurInRecents = enabled) }
        viewModelScope.launch { container.settingsRepository.setBlurInRecents(enabled) }
    }

    fun setCaptureLogcat(enabled: Boolean) {
        _state.update { it.copy(captureLogcat = enabled) }
        viewModelScope.launch { container.settingsRepository.setCaptureLogcat(enabled) }
    }

    /**
     * 导入开屏封面。选图后立刻把图片拷进私有目录（详见 [SplashCoverStore]），
     * 因此这里不需要 takePersistableUriPermission；失败时保留原封面并给出中文提示。
     */
    fun setSplashCover(uri: Uri) {
        viewModelScope.launch {
            container.splashCoverStore.importFrom(uri)
                .onSuccess { _state.update { it.copy(message = "开屏封面已更新", success = true) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            message = "开屏封面设置失败：${error.message ?: "无法读取所选图片"}",
                            success = false,
                        )
                    }
                }
        }
    }

    fun clearSplashCover() {
        viewModelScope.launch {
            container.splashCoverStore.clear()
            _state.update { it.copy(message = "已移除开屏封面", success = true) }
        }
    }

    fun setOfflineCacheLimitBytes(limit: Long) {
        if (limit < 0L) return
        val usable = container.context.filesDir.usableSpace.coerceAtLeast(0L)
        val current = _state.value.offlineUsage.totalBytes.coerceAtLeast(0L)
        val capacity = saturatedAdd(usable, current)
        if (limit > 0L && capacity > 0L && limit > capacity) {
            _state.update { it.copy(message = "缓存上限超过当前可用磁盘容量", success = false) }
            return
        }
        _state.update { it.copy(offlineCacheLimitBytes = limit, message = null) }
        viewModelScope.launch {
            container.settingsRepository.setOfflineCacheLimitBytes(limit)
            container.offlineCache.enforceConfiguredLimit()
        }
    }

    fun setDownloadConcurrency(n: Int) {
        _state.update { it.copy(downloadConcurrency = n.coerceIn(1, 8)) }
        viewModelScope.launch { container.settingsRepository.setDownloadConcurrency(n) }
    }

    fun addExtraScanDirUri(uri: String) {
        viewModelScope.launch {
            container.settingsRepository.addExtraScanDirUri(uri)
        }
    }

    fun removeExtraScanDirUri(uri: String) {
        viewModelScope.launch {
            container.settingsRepository.removeExtraScanDirUri(uri)
        }
    }

    fun updateTranslations() {
        viewModelScope.launch {
            _state.update { it.copy(translationUpdating = true) }
            try {
                val (namespaces, tags) = container.tagTranslationRepository.update()
                _state.update {
                    it.copy(translationUpdating = false, message = "翻译更新完成：$namespaces 个命名空间，$tags 条标签", success = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(translationUpdating = false, message = e.message ?: "翻译更新失败") }
            }
        }
    }

    fun clearTranslations() {
        viewModelScope.launch {
            try {
                container.tagTranslationRepository.clearCache()
                _state.update { it.copy(message = "已清除标签翻译", success = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "清除翻译失败", success = false) }
            }
        }
    }

    /** D3 翻译词库自动更新开关：即存即生效，词库刷新调度由 TagTranslationStore/仓库层读取。 */
    fun setTagTranslationAutoUpdate(enabled: Boolean) {
        _state.update { it.copy(tagTranslationAutoUpdate = enabled) }
        viewModelScope.launch { container.settingsRepository.saveTagTranslationAutoUpdate(enabled) }
    }

    fun test() {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, message = null, success = false) }
            ApiClient.config.baseUrl = normalizeBaseUrl(_state.value.url)
            ApiClient.config.apiKey = _state.value.key.trim()
            try {
                val stats = container.repository.testConnection()
                refreshServerInfo(container.repository)
                _state.update {
                    it.copy(testing = false, success = true, message = "连接成功：共 ${stats.total_archives} 个档案")
                }
            } catch (e: Exception) {
                ApiClient.config.serverInfo.value = null
                _state.update { it.copy(testing = false, message = e.message ?: "连接失败") }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            container.settingsRepository.saveServer(_state.value.url, _state.value.key, _state.value.name)
            refreshServerInfo(container.repository)
            // 换了服务器就必须让图库重新拉一次：否则页面上的列表还是旧服务器的（或连接前留下的空列表）。
            LibraryRefreshBus.tick.value++
            _state.update { it.copy(saving = false, success = true, message = "已保存") }
        }
    }

    /** 切换活跃 profile：生效配置与连接状态一并刷新，成功后 Snackbar 提示。 */
    fun switchProfile(index: Int) {
        viewModelScope.launch {
            try {
                container.settingsRepository.setActiveProfile(index)
                refreshActiveServerState()
                ApiClient.config.serverInfo.value = null
                refreshServerInfo(container.repository)
                // 切换服务器同样要让图库重新走一遍请求链路（换的是整份档案来源）。
                LibraryRefreshBus.tick.value++
                val label = _state.value.name.ifBlank { _state.value.url }
                _state.update { it.copy(success = true, message = "已切换服务器${if (label.isNotBlank()) "：$label" else ""}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "切换服务器失败", success = false) }
            }
        }
    }

    /** 新增服务器 profile（保存后自动切换为活跃并刷新连接状态）。 */
    fun addProfile(name: String, url: String, key: String) {
        viewModelScope.launch {
            try {
                container.settingsRepository.addProfile(name, url, key)
                refreshActiveServerState()
                ApiClient.config.serverInfo.value = null
                refreshServerInfo(container.repository)
                // 新增即切换为活跃，图库需要按新服务器重新加载。
                LibraryRefreshBus.tick.value++
                _state.update { it.copy(success = true, message = "已添加服务器${if (name.isNotBlank()) "：${name.trim()}" else ""}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "新增服务器失败", success = false) }
            }
        }
    }

    /** 删除指定 profile（活跃项不可删，至少保留一个）。 */
    fun removeProfile(index: Int) {
        viewModelScope.launch {
            try {
                val target = _state.value.profiles.getOrNull(index)
                container.settingsRepository.removeProfile(index)
                refreshActiveServerState()
                ApiClient.config.serverInfo.value = null
                refreshServerInfo(container.repository)
                val label = target?.name?.takeIf { it.isNotBlank() }
                _state.update { it.copy(success = true, message = "已删除服务器配置${if (label != null) "：$label" else ""}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "删除服务器配置失败", success = false) }
            }
        }
    }

    /** profile 切换/增删后，把当前生效配置同步回表单字段。 */
    private suspend fun refreshActiveServerState() {
        val s = container.settingsRepository.settings.first()
        _state.update { it.copy(url = s.baseUrl, key = s.apiKey, name = s.serverName) }
    }

    /** 保存图库存储根目录；传 null 恢复默认目录（SAF 授权由调用方先 takePersistableUriPermission）。 */
    fun saveStorageRoot(uri: String?) {
        viewModelScope.launch { container.settingsRepository.saveStorageRoot(uri) }
    }

    fun setDownloadRatePerSecond(n: Int) {
        _state.update { it.copy(downloadRatePerSecond = n.coerceIn(1, 10)) }
        viewModelScope.launch { container.settingsRepository.saveDownloadRatePerSecond(n) }
    }

    /** C3 下载重试上限（1..10）：写 DataStore 单一来源，运行时由 AppContainer 推送到协调器。 */
    fun setDownloadMaxRetries(n: Int) {
        _state.update { it.copy(downloadMaxRetries = n.coerceIn(1, 10)) }
        viewModelScope.launch { container.settingsRepository.saveDownloadMaxRetries(n) }
    }

    fun setScrapeSource(source: String) {
        _state.update { it.copy(scrapeSource = source) }
        viewModelScope.launch { container.settingsRepository.saveScrapeSource(source) }
    }

    fun setScrapeConfidence(value: Int) {
        _state.update { it.copy(scrapeConfidence = value.coerceIn(0, 100)) }
        viewModelScope.launch { container.settingsRepository.saveScrapeConfidence(value) }
    }

    fun setScrapeSkipTagged(enabled: Boolean) {
        _state.update { it.copy(scrapeSkipTagged = enabled) }
        viewModelScope.launch { container.settingsRepository.saveScrapeSkipTagged(enabled) }
    }

    fun loadShinobuStatus() {
        if (_state.value.shinobuLoading) return
        viewModelScope.launch {
            _state.update { it.copy(shinobuLoading = true) }
            val alive = try {
                val el = ApiClient.json.parseToJsonElement(container.repository.getShinobuStatus())
                val p = (el as? JsonObject)?.get("is_alive") as? JsonPrimitive
                when (p?.content?.toIntOrNull()) {
                    1 -> true
                    0 -> false
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { it.copy(shinobuLoading = false, shinobuAlive = alive) }
        }
    }

    fun rescanShinobu() {
        if (_state.value.shinobuLoading) return
        viewModelScope.launch {
            _state.update { it.copy(shinobuLoading = true) }
            try {
                container.repository.shinobuRescan()
                _state.update {
                    it.copy(message = "已触发服务器重扫，稍后下拉刷新图库即可", success = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "重扫失败", success = false) }
            } finally {
                _state.update { it.copy(shinobuLoading = false) }
            }
        }
    }

    // diskCache 在 Coil 2.x 仍标注为实验 API：这里明确 opt-in，避免把告警留给后续版本升级。
    @OptIn(ExperimentalCoilApi::class)
    fun clearImageCache(context: Context) {
        val loader = context.imageLoader
        loader.memoryCache?.clear()
        viewModelScope.launch(Dispatchers.IO) {
            loader.diskCache?.clear()
            _state.update { it.copy(message = "图片缓存已清除", success = true) }
        }
    }

    fun clearOfflineCache() {
        container.offlineCache.clearAll()
        _state.update { it.copy(message = "离线缓存已清空", success = true) }
    }

    fun regenAllThumbnails() {
        if (_state.value.regenThumbsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(regenThumbsLoading = true) }
            try {
                container.repository.regenAllThumbnails()
                _state.update { it.copy(message = "已提交重建全部缩略图任务", success = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "重建全部缩略图失败", success = false) }
            } finally {
                _state.update { it.copy(regenThumbsLoading = false) }
            }
        }
    }

    /**
     * A7 重复检测：向服务器入队 find_duplicates 任务（不等待完成，结果在服务器端查看）。
     * 成功/失败均经 Snackbar 提示；失败展示仓库层给出的用户可读原因。
     */
    fun queueDuplicateDetection() {
        if (_state.value.duplicateCheckLoading) return
        viewModelScope.launch {
            _state.update { it.copy(duplicateCheckLoading = true) }
            when (val result = container.repository.queueDuplicateDetection()) {
                is DuplicateDetectionQueue.Queued -> _state.update {
                    it.copy(
                        duplicateCheckLoading = false,
                        message = "重复检测任务已提交，完成后可在服务器端查看结果",
                        success = true,
                    )
                }
                is DuplicateDetectionQueue.Failed -> _state.update {
                    it.copy(
                        duplicateCheckLoading = false,
                        message = result.message ?: "重复检测任务提交失败",
                        success = false,
                    )
                }
            }
        }
    }

    fun backupDatabase(context: Context, treeUri: Uri) {
        if (_state.value.backupLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(backupLoading = true) }
            try {
                val jobid = container.repository.queueBackup()
                val job = container.repository.pollJobUntilDone(jobid)
                    ?: throw IOException("备份任务超时未完成，请稍后重试")
                if (job.isFailed) throw IOException("服务器备份任务失败：${job.note.ifBlank { job.state }}")
                val json = container.repository.downloadBackup(jobid)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val name = "lanraragi-backup-" +
                    java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault())
                        .format(java.util.Date()) + ".json"
                val fileUri = DocumentsContract.createDocument(
                    context.contentResolver, parentUri, "application/json", name,
                ) ?: throw IOException("写入所选目录失败")
                val out = context.contentResolver.openOutputStream(fileUri)
                    ?: throw IOException("写入所选目录失败")
                out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                _state.update { it.copy(message = "备份已保存", success = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "备份失败", success = false) }
            } finally {
                _state.update { it.copy(backupLoading = false) }
            }
        }
    }

    fun restoreFromBackup(context: Context, uri: Uri) {
        if (_state.value.restoreLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(restoreLoading = true) }
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("无法读取备份文件")
                // 恢复是服务器端 Minion 排队的重操作：POST 返回时整库还没被动，
                // 必须等任务到终态才能报成功，否则失败也会显示「恢复成功」。
                val jobid = container.repository.restoreBackup(json)
                val job = container.repository.pollJobUntilDone(jobid, maxMillis = 15 * 60 * 1000L)
                    ?: throw IOException("恢复任务仍在服务器排队/执行中，请稍后在服务器端确认结果")
                if (job.isFailed) throw IOException("服务器恢复任务失败：${job.note.ifBlank { job.state }}")
                container.offlineCache.clearAll()
                LibraryRefreshBus.tick.value++
                _state.update { it.copy(message = "恢复成功，已重置整库", success = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "恢复失败", success = false) }
            } finally {
                _state.update { it.copy(restoreLoading = false) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
    onOpenDiagnostics: () -> Unit = {},
    onOpenWriteback: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenWizard: () -> Unit = {},
    // 原「导航」页合并进设置后迁入的入口（统计/历史/分类/单行本）。
    onOpenStatistics: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenCategory: () -> Unit = {},
    onOpenTankoubons: () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var section by remember { mutableStateOf(SettingsSection.MAIN) }

    val downloadDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setDownloadDirUri(uri.toString())
        }
    }
    val exportConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportConfig(context, uri)
    }
    val importConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importConfig(context, uri)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    /*
     * 设置作为顶级 Tab 渲染后没有可出栈的路由，系统返回键需要自己逐级返回：
     * 子分区 → 设置首页（再按一次由 MainScreen 的 BackHandler 切回图库首页）。
     * 顶级（MAIN）时该处理器禁用，把返回键让给主壳。
     */
    BackHandler(enabled = section != SettingsSection.MAIN) {
        section = SettingsSection.MAIN
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack {
            if (section == SettingsSection.MAIN) onBack?.invoke() else section = SettingsSection.MAIN
        },
        topBar = {
            // 独立路由页（始终有 onBack）：只保留返回箭头，不再渲染无法触达的抽屉菜单入口。
            AppTopBar(
                title = section.title,
                onBack = if (section == SettingsSection.MAIN) {
                    onBack
                } else {
                    { section = SettingsSection.MAIN }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (section) {
                SettingsSection.MAIN -> SettingsMainList(
                    onNavigate = { section = it },
                    onOpenGuide = onOpenGuide,
                    onOpenWizard = onOpenWizard,
                    onOpenStatistics = onOpenStatistics,
                    onOpenHistory = onOpenHistory,
                    onOpenCategory = onOpenCategory,
                    onOpenTankoubons = onOpenTankoubons,
                )
                SettingsSection.CONNECT -> ServerSection(container, state, vm)
                SettingsSection.READER -> ReaderSection(state, vm) { section = SettingsSection.TAP_ZONE }
                SettingsSection.TAP_ZONE -> TapZoneSection(state, vm)
                SettingsSection.INTERFACE -> InterfaceSection(container, state, vm)
                SettingsSection.TOOLS -> ToolsSection(container, state, vm, onOpenWriteback)
                SettingsSection.DATA -> DataSection(state, vm, context, {
                    downloadDirLauncher.launch(null)
                }, {
                    exportConfigLauncher.launch("lanraragi-reader-config.json")
                }, {
                    importConfigLauncher.launch(arrayOf("application/json", "text/plain"))
                })
                SettingsSection.SECURITY -> SecuritySection(state, vm)
                SettingsSection.PERMISSIONS -> PermissionSection()
                SettingsSection.DEBUG -> DebugSection(
                    state,
                    vm,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
                SettingsSection.ABOUT -> AboutSection()
            }
        }
    }
    vm.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = vm::cancelConfigImport,
            title = { Text("确认导入配置") },
            text = {
                Text(
                    if (preview.changedFields.isEmpty()) "配置与当前设置一致"
                    else "将修改 ${preview.changedFields.size} 项：\n${preview.changedFields.joinToString("\n")}",
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmConfigImport, enabled = preview.changedFields.isNotEmpty()) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = vm::cancelConfigImport) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsMainList(
    onNavigate: (SettingsSection) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenTankoubons: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))
        listOf(
            SettingsSection.CONNECT,
            SettingsSection.READER,
            SettingsSection.INTERFACE,
            SettingsSection.TOOLS,
            SettingsSection.DATA,
            SettingsSection.SECURITY,
            SettingsSection.PERMISSIONS,
            SettingsSection.DEBUG,
            SettingsSection.ABOUT,
        ).forEach { section ->
            SettingsNavRow(section.title, section.icon) { onNavigate(section) }
        }

        // 浏览：原「导航」页里真正需要保留的二级入口（下载＝底栏第 2 键、设置＝本页、
        // 诊断＝上面的「调试」分区，均已去重，不再重复出现）。
        SettingsGroupHeader("浏览")
        SettingsNavRow("统计", Icons.Filled.BarChart, onOpenStatistics)
        SettingsNavRow("历史", Icons.Filled.History, onOpenHistory)
        SettingsNavRow("分类", Icons.Filled.Category, onOpenCategory)
        // 单行本入口常驻（原先由 `a11_tankoubons` 实验开关门控，开关已随「实验室」页移除）。
        SettingsNavRow("单行本", Icons.AutoMirrored.Filled.MenuBook, onOpenTankoubons)

        // 引导入口：指南页常驻可读；设置向导以「编辑模式」复用首启向导组件再次进入。
        SettingsGroupHeader("引导")
        SettingsNavRow("入门指南", Icons.AutoMirrored.Filled.MenuBook, onOpenGuide)
        SettingsNavRow("重新运行设置向导", Icons.Filled.Restore, onOpenWizard)
        Text(
            "向导以编辑模式复用首启流程，各步骤都以当前配置为初值，只有主动修改才会写回；" +
                "存储位置也可在「数据」中单独调整。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun InterfaceSection(
    container: AppContainer,
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
) {
    var editingNs by remember { mutableStateOf<String?>(null) }
    var fabColorDialog by remember { mutableStateOf(false) }
    var bottomBarColorDialog by remember { mutableStateOf(false) }
    var bottomBarActiveColorDialog by remember { mutableStateOf(false) }
    // 开屏封面：系统照片选择器（PickVisualMedia）。选中的图会被立刻拷进私有目录，
    // 因此这里不取持久化授权；旧系统上该契约会自动回退成「文档选择器 + 仅图片」。
    val splashCoverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.setSplashCover(uri)
    }
    var showClearSplashDialog by remember { mutableStateOf(false) }
    val namespaces = remember {
        TagNamespaceRegistry.allDescriptors(includeHidden = false).map { it.name }
    }
    val fabColor = remember(state.floatingButtonColor) {
        parseHexColor(state.floatingButtonColor)?.let { Color(it) } ?: Color(0xFFE94560)
    }
    val bottomBarColor = remember(state.bottomBarColor) {
        parseHexColor(state.bottomBarColor)?.let { Color(it) } ?: Color(0xFF1E2835)
    }
    val bottomBarActiveColor = remember(state.bottomBarActiveColor) {
        parseHexColor(state.bottomBarActiveColor)?.let { Color(it) } ?: Color(0xFF0084FF)
    }
    val splashCoverSet = state.splashCoverVersion > 0L

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroupHeader("外观")
        DropdownRow(
            "主题", themeLabel(state.theme),
            listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色"),
            vm::setTheme,
        )
        // 动态取色：Android 12（API 31）以下系统不支持，置灰并说明原因。
        val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        SwitchRow(
            "动态取色（Material You）",
            state.dynamicColor,
            vm::setDynamicColor,
            subtitle = if (dynamicColorSupported) {
                "跟随系统壁纸取色，开启后覆盖上方的定制蓝紫色板"
            } else {
                "当前系统不支持，需要 Android 12（API 31）及以上"
            },
            enabled = dynamicColorSupported,
        )
        DropdownRow(
            "画廊视图", viewModeLabel(state.galleryViewMode),
            listOf("grid" to "松散网格", "compact" to "紧凑网格", "list" to "列表"),
            vm::setGalleryViewMode,
        )
        IntDropdownRow("画廊列数", state.galleryColumns, (2..8).toList(), vm::setGalleryColumns)
        IntDropdownRow("预览列数", state.previewColumns, (2..8).toList(), vm::setPreviewColumns)
        CountRow("预览加载数量", state.previewCount, vm::setPreviewCount)
        CountRow("封面预载数量", state.coverPrefetchCount, vm::setCoverPrefetchCount)

        SwitchRow("显示悬浮按钮", state.showFloatingButton, vm::setShowFloatingButton)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("悬浮按钮颜色", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fabColor),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { fabColorDialog = true }) { Text("选择") }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("底栏背景颜色", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bottomBarColor),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { bottomBarColorDialog = true }) { Text("选择") }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("底栏激活项颜色", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bottomBarActiveColor),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { bottomBarActiveColorDialog = true }) { Text("选择") }
        }

        SettingsGroupHeader("开屏封面")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧预览：有封面时直接渲染落盘后的图片（与开屏同一份文件、同一个版本键），
            // 没封面时用同尺寸的占位块，避免整行高度随内容跳动。
            Box(
                Modifier
                    .size(width = 54.dp, height = 96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (splashCoverSet) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(container.splashCoverStore.coverFile())
                            .memoryCacheKey("splash-cover-${state.splashCoverVersion}")
                            .diskCacheKey("splash-cover-${state.splashCoverVersion}")
                            .build(),
                        contentDescription = "开屏封面预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("开屏封面", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (splashCoverSet) {
                        "冷启动时全屏显示 ${SplashCoverStore.DISPLAY_DURATION_MS / 1000} 秒，点按可跳过"
                    } else {
                        "从本地图库选一张图片作为启动画面；不设置则显示 App 图标"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (splashCoverSet) {
                TextButton(onClick = { showClearSplashDialog = true }) { Text("移除") }
            }
            TextButton(onClick = {
                splashCoverLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) { Text(if (splashCoverSet) "更换图片" else "选择图片") }
        }

        SettingsGroupHeader("标签颜色")
        Text(
            "点击命名空间自定义其标签颜色，支持 RGB 与十六进制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        namespaces.forEach { ns ->
            TagColorRow(
                ns = ns,
                color = rememberTagColor(ns),
                isCustom = ns in state.tagColors,
                onClick = { editingNs = ns },
            )
        }

        Spacer(Modifier.height(96.dp))
    }

    editingNs?.let { ns ->
        ColorPickerDialog(
            title = "${TagRules.label(ns)} · $ns",
            initialColor = rememberTagColor(ns),
            onDismiss = { editingNs = null },
            onConfirm = { c ->
                vm.setTagColor(ns, c.toHexString())
                editingNs = null
            },
            onReset = {
                vm.resetTagColor(ns)
                editingNs = null
            },
        )
    }
    if (fabColorDialog) {
        ColorPickerDialog(
            title = "悬浮按钮颜色",
            initialColor = fabColor,
            onDismiss = { fabColorDialog = false },
            onConfirm = { c ->
                vm.setFloatingButtonColor(c.toHexString())
                fabColorDialog = false
            },
        )
    }
    if (bottomBarColorDialog) {
        ColorPickerDialog(
            title = "底栏背景颜色",
            initialColor = bottomBarColor,
            onDismiss = { bottomBarColorDialog = false },
            onConfirm = { c ->
                vm.setBottomBarColor(c.toHexString())
                bottomBarColorDialog = false
            },
        )
    }
    if (bottomBarActiveColorDialog) {
        ColorPickerDialog(
            title = "底栏激活项颜色",
            initialColor = bottomBarActiveColor,
            onDismiss = { bottomBarActiveColorDialog = false },
            onConfirm = { c ->
                vm.setBottomBarActiveColor(c.toHexString())
                bottomBarActiveColorDialog = false
            },
        )
    }
    if (showClearSplashDialog) {
        AlertDialog(
            onDismissRequest = { showClearSplashDialog = false },
            title = { Text("移除开屏封面") },
            text = { Text("移除后冷启动将恢复显示 App 图标。已保存的图片副本会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearSplashDialog = false
                    vm.clearSplashCover()
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearSplashDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TagColorRow(ns: String, color: Color, isCustom: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(TagRules.label(ns), style = MaterialTheme.typography.bodyLarge)
            Text(
                ns + if (isCustom) " · 已自定义" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSection(
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
    onOpenTapZone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("阅读偏好作用范围", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ReaderOrientationProfile.GLOBAL to "全局",
                ReaderOrientationProfile.PORTRAIT to "竖屏",
                ReaderOrientationProfile.LANDSCAPE to "横屏",
            ).forEach { (profile, label) ->
                FilterChip(
                    selected = state.readerOrientationProfile == profile,
                    onClick = { vm.setReaderOrientationProfile(profile) },
                    label = { Text(label) },
                )
            }
        }
        if (state.readerOrientationProfile != ReaderOrientationProfile.GLOBAL) {
            TextButton(onClick = { vm.clearReaderOrientationOverrides(state.readerOrientationProfile) }) {
                Text("当前方向跟随全局")
            }
        }
        DropdownRow(
            "阅读模式", readerModeLabel(state.readerMode),
            listOf("single" to "单页", "multi" to "多页", "continuous" to "连续"),
            vm::setReaderMode,
        )
        AutoScrollSpeedRow(state.autoScrollSpeed, vm::setAutoScrollSpeed)
        IntDropdownRow("每屏页数", state.multiPageCount, (2..8).toList(), vm::setMultiPageCount)
        SwitchRow("横屏自动双页", state.autoDoublePageLandscape, vm::setAutoDoublePageLandscape)
        SwitchRow("双页时首封面单独显示", state.doublePageFirstPageAlone, vm::setDoublePageFirstPageAlone)
        DropdownRow(
            "阅读方向", directionLabel(state.readingDirection),
            listOf("ltr" to "左→右", "rtl" to "右→左", "ttb" to "上→下"),
            vm::setReadingDirection,
        )
        DropdownRow(
            "图片适应", fitModeLabel(state.readerFitMode),
            listOf("fitWidth" to "适应宽度", "fitHeight" to "适应高度", "fitScreen" to "适应屏幕", "original" to "原始尺寸"),
            vm::setReaderFitMode,
        )
        DropdownRow(
            "阅读器背景", backgroundLabel(state.readerBackground),
            listOf("auto" to "自动", "black" to "黑", "dark" to "深灰", "gray" to "灰", "white" to "白"),
            vm::setReaderBackground,
        )
        SwitchRow("点击左右区域翻页", state.tapZonesEnabled, vm::setTapZonesEnabled)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTapZone)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("点击分区自定义", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "自定义屏幕各区域的点击动作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchRow("阅读时屏幕常亮", state.keepScreenOn, vm::setKeepScreenOn)
        SwitchRow("音量键翻页", state.volumeKeysEnabled, vm::setVolumeKeysEnabled)
        SwitchRow("打开详情即清除新标记", state.clearNewOnOpen, vm::setClearNewOnOpen)
        SwitchRow("阅读页码显示", state.readerPageOverlay, vm::setReaderPageOverlay, subtitle = "在阅读画面右上角显示页码")
        CountRow("在线预载页数", state.preloadOnlineCount, vm::setPreloadOnlineCount)
        CountRow("本地预载页数", state.preloadLocalCount, vm::setPreloadLocalCount)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun TapZoneSection(state: SettingsViewModel.UiState, vm: SettingsViewModel) {
    val grid = state.tapZoneGrid
    var selectedCell by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SettingsGroupHeader("点击分区自定义")
        Text(
            "点击九宫格切换各区域动作（上一页 → 下一页 → 菜单 → 无）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        TapZoneGridEditor(
            grid = grid,
            selectedCell = selectedCell,
            onCellClick = { index ->
                selectedCell = index
                vm.cycleTapZoneCell(index)
            },
        )
        TapZoneRatioSliderRow("列宽比", grid.colRatio) { vm.setTapZoneGrid(grid.copy(colRatio = it)) }
        TapZoneRatioSliderRow("行高比", grid.rowRatio) { vm.setTapZoneGrid(grid.copy(rowRatio = it)) }
        Text(
            "从右往左（RTL）阅读时左右区域自动镜像",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        OutlinedButton(
            onClick = {
                selectedCell = null
                vm.resetTapZoneGrid()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("恢复默认")
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun TapZoneGridEditor(
    grid: TapZoneGrid,
    selectedCell: Int?,
    onCellClick: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        (0..2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                (0..2).forEach { col ->
                    val index = row * 3 + col
                    TapZoneCell(
                        row = row,
                        column = col,
                        action = tapZoneCellAction(grid, index),
                        selected = selectedCell == index,
                        onClick = { onCellClick(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TapZoneCell(
    row: Int,
    column: Int,
    action: TapZoneAction,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .padding(4.dp)
            .height(64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "第${row + 1}行第${column + 1}列：${action.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TapZoneRatioSliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text("$label：${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value.coerceIn(0.15f, 0.5f),
            onValueChange = onChange,
            valueRange = 0.15f..0.5f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun tapZoneCellAction(grid: TapZoneGrid, index: Int): TapZoneAction =
    TapZoneAction.entries
        .getOrElse(grid.cells.getOrElse(index) { TapZoneAction.NONE.ordinal }) { TapZoneAction.NONE }

@Composable
private fun ServerSection(
    container: AppContainer,
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
) {
    val serverInfo by ApiClient.config.serverInfo.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingSwitchIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.url) {
        if (state.url.isNotBlank() && ApiClient.config.serverInfo.value == null) {
            refreshServerInfo(container.repository)
        }
    }

    LaunchedEffect(Unit) { vm.loadShinobuStatus() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerCard(state, serverInfo) { showEditDialog = true }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("服务器配置档", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "点击列表项切换活跃服务器，编辑当前服务器请点上方服务器卡片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                state.profiles.forEachIndexed { index, profile ->
                    ProfileRow(
                        profile = profile,
                        active = index == state.activeProfileIndex,
                        canDelete = state.profiles.size > 1 && index != state.activeProfileIndex,
                        onClick = { if (index != state.activeProfileIndex) pendingSwitchIndex = index },
                        onDelete = { pendingDeleteIndex = index },
                    )
                    if (index < state.profiles.lastIndex) HorizontalDivider()
                }
                TextButton(onClick = { showAddDialog = true }) { Text("+ 新增服务器") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shinobu 文件监控", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (state.shinobuAlive) {
                            true -> "Shinobu 状态：运行中"
                            false -> "Shinobu 状态：已停止"
                            null -> "Shinobu 状态：未知"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.shinobuAlive == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::loadShinobuStatus) { Text("刷新状态") }
                }
                Button(
                    onClick = vm::rescanShinobu,
                    enabled = !state.shinobuLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.shinobuLoading) {
                        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("立即重扫服务器文件夹")
                    }
                }
            }
        }
        val successMessage = state.message?.takeIf { state.success }
        if (successMessage != null) {
            Text(successMessage, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(80.dp))
    }

    if (showEditDialog) {
        ServerEditDialog(
            state = state,
            vm = vm,
            onDismiss = { showEditDialog = false },
        )
    }
    pendingSwitchIndex?.let { index ->
        val target = state.profiles.getOrNull(index)
        val label = target?.name?.takeIf { it.isNotBlank() } ?: target?.url ?: ""
        AlertDialog(
            onDismissRequest = { pendingSwitchIndex = null },
            title = { Text("切换服务器？") },
            text = { Text("将切换到「$label」，当前生效的连接配置与服务器信息都会随之更换。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSwitchIndex = null
                    vm.switchProfile(index)
                }) { Text("切换") }
            },
            dismissButton = { TextButton(onClick = { pendingSwitchIndex = null }) { Text("取消") } },
        )
    }
    pendingDeleteIndex?.let { index ->
        val target = state.profiles.getOrNull(index)
        val label = target?.name?.takeIf { it.isNotBlank() } ?: target?.url ?: ""
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text("删除服务器配置") },
            text = { Text("确定删除「$label」吗？仅删除本机保存的连接配置，不影响服务器数据。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteIndex = null
                    vm.removeProfile(index)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteIndex = null }) { Text("取消") } },
        )
    }
    if (showAddDialog) {
        AddProfileDialog(vm = vm, onDismiss = { showAddDialog = false })
    }
}

@Composable
private fun ProfileRow(
    profile: ServerProfile,
    active: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name.ifBlank { "未命名服务器" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Text("当前使用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                profile.url.ifBlank { "未配置地址" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "删除服务器配置",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddProfileDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("服务器名称") },
                    placeholder = { Text("LANraragi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址 / hostname") },
                    placeholder = { Text("manga.example.com 或 http://192.168.1.10:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.addProfile(name, url, key)
                    onDismiss()
                },
                enabled = url.isNotBlank(),
            ) {
                Text("保存并切换")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoScrollSpeedRow(value: String, onChange: (String) -> Unit) {
    var input by remember { mutableStateOf(value) }
    val seconds = autoScrollSeconds(value)
    val inputSeconds = input.toFloatOrNull()
    val inputValid = inputSeconds != null &&
        inputSeconds in AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS

    LaunchedEffect(value) {
        val localValue = input.toFloatOrNull()
        if (
            localValue == null ||
            localValue !in AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS ||
            normalizeAutoScrollSpeed(localValue.toString()) != value
        ) {
            input = value
        }
    }

    fun update(secondsValue: Float) {
        val normalized = normalizeAutoScrollSpeed(secondsValue.toString())
        input = normalized
        onChange(normalized)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text("连续滚动速度", style = MaterialTheme.typography.bodyLarge)
        Text(
            "滚过当前视口需要 ${normalizeAutoScrollSpeed(seconds.toString())} 秒",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = seconds,
            onValueChange = ::update,
            valueRange = AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS,
            steps = 58,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.5f, 3f, 6f).forEach { preset ->
                val normalized = normalizeAutoScrollSpeed(preset.toString())
                FilterChip(
                    selected = value == normalized,
                    onClick = { update(preset) },
                    label = { Text("$normalized 秒") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { newValue ->
                input = newValue
                newValue.toFloatOrNull()
                    ?.takeIf { it in AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS }
                    ?.let { onChange(it.toString()) }
            },
            label = { Text("每个视口所需秒数") },
            supportingText = { Text("可输入 0.5–30 秒") },
            isError = input.isNotEmpty() && !inputValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServerEditDialog(
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = vm::onUrlChange,
                    label = { Text("服务器地址 / hostname") },
                    placeholder = { Text("manga.example.com 或 http://192.168.1.10:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.key,
                    onValueChange = vm::onKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onNameChange,
                    label = { Text("服务器名称") },
                    placeholder = { Text("LANraragi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::test,
                    enabled = !state.testing && state.url.isNotBlank(),
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连接")
                    }
                }
                Button(
                    onClick = {
                        vm.save()
                        onDismiss()
                    },
                    enabled = !state.saving && state.url.isNotBlank(),
                ) {
                    Text(if (state.saving) "保存中…" else "保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DataSection(
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
    context: Context,
    onPickDownloadDir: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.addExtraScanDirUri(uri.toString())
        }
    }

    val storageRootLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.saveStorageRoot(uri.toString())
        }
    }

    var showStorageDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.backupDatabase(context, uri)
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreDialog = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickDownloadDir)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("下载路径", style = MaterialTheme.typography.bodyLarge)
                Text(
                    state.downloadDirUri?.let { "已配置" } ?: "尚未配置（必填）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.downloadDirUri == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showStorageDialog = true }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("存储位置", style = MaterialTheme.typography.bodyLarge)
                Text(
                    state.storageRootLabel.ifBlank { "默认目录（应用私有空间）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.storageDegradedReason?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "额外的画廊扫描路径",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (state.isScanning) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在扫描本地档案…", style = MaterialTheme.typography.bodySmall)
            }
        }

        state.extraScanDirUris.forEach { uri ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val folderName = remember(uri) {
                    runCatching { android.net.Uri.parse(uri).lastPathSegment ?: uri }.getOrDefault(uri)
                }
                Text(
                    "文件夹: $folderName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.removeExtraScanDirUri(uri) }) {
                    Icon(Icons.Default.Clear, "移除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        TextButton(
            onClick = { scanLauncher.launch(null) },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text("+ 添加扫描路径")
        }

        Spacer(Modifier.height(16.dp))
        OfflineCacheLimitEditor(state.offlineCacheLimitBytes, vm::setOfflineCacheLimitBytes)
        Text(
            "已用 ${formatBytes(state.offlineUsage.totalBytes)} / ${if (state.offlineCacheLimitBytes == 0L) "不自动淘汰" else formatBytes(state.offlineCacheLimitBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )

        DownloadConcurrencyRow(state.downloadConcurrency, vm::setDownloadConcurrency)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text("速率限制：${state.downloadRatePerSecond}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "每秒最多启动的任务数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.downloadRatePerSecond.toFloat(),
                onValueChange = { vm.setDownloadRatePerSecond(it.roundToInt()) },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text("下载重试上限：${state.downloadMaxRetries}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "单个任务在标记为失败前的自动重试次数上限（1–10）；只有网络中断与 429/5xx 会重试，认证或存储失败直接标记失败",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.downloadMaxRetries.toFloat(),
                onValueChange = { vm.setDownloadMaxRetries(it.roundToInt()) },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "配置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出配置") }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入配置") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "维护",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.clearImageCache(context) }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("清除图片缓存", style = MaterialTheme.typography.bodyLarge)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.clearOfflineCache() }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("清空离线缓存", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.backupLoading) { backupDirLauncher.launch(null) }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("备份服务器数据库", style = MaterialTheme.typography.bodyLarge)
            if (state.backupLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.restoreLoading) {
                    restoreFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("从备份恢复", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            if (state.restoreLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("存储位置") },
            text = {
                Text(
                    "当前使用：${state.storageRootLabel.ifBlank { "默认目录（应用私有空间）" }}\n\n" +
                        "切换后旧缓存保留在原位置，不会自动迁移或删除。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStorageDialog = false
                    storageRootLauncher.launch(null)
                }) {
                    Text("选择文件夹")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.saveStorageRoot(null)
                    showStorageDialog = false
                }) { Text("使用默认目录") }
            },
        )
    }

    if (showRestoreDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                pendingRestoreUri = null
            },
            title = { Text("恢复服务器数据库") },
            text = {
                Text(
                    "将从备份文件恢复整个服务器数据库，此操作会覆盖当前数据，且不可撤销！",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingRestoreUri
                    showRestoreDialog = false
                    pendingRestoreUri = null
                    if (uri != null) vm.restoreFromBackup(context, uri)
                }) {
                    Text("确认恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    pendingRestoreUri = null
                }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ToolsSection(
    container: AppContainer,
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
    onOpenWriteback: () -> Unit,
) {
    var showRegenDialog by remember { mutableStateOf(false) }
    // 原「导航」页的「用服务器下载链接」合并进工具区：对话框随之迁入，逻辑不变。
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastUpdated by TagTranslationStore.lastUpdated.collectAsState()
    val translations by TagTranslationStore.translations.collectAsState()
    val translationInfo by TagTranslationStore.info.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SettingsGroupHeader("工具")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.duplicateCheckLoading) { vm.queueDuplicateDetection() }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("重复检测", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "在服务器端按封面哈希查找重复档案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.duplicateCheckLoading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.regenThumbsLoading) { showRegenDialog = true }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("重建全部缩略图", style = MaterialTheme.typography.bodyLarge)
            if (state.regenThumbsLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    urlText = ""
                    showUrlDialog = true
                }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("用服务器下载链接", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "直接把直链 zip 交给服务器下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsGroupHeader("元数据刮削策略")
        Text(
            "为「元数据中文化」批量回写选择默认刮削来源与匹配门槛。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        DropdownRow(
            "默认来源",
            scrapeSourceLabel(state.scrapeSource),
            listOf(
                "ehentai" to "E-Hentai",
                "nhentai" to "nHentai",
                "server_plugin" to "服务器插件",
            ),
            vm::setScrapeSource,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text("匹配置信度阈值：${state.scrapeConfidence}%", style = MaterialTheme.typography.bodyLarge)
            Text(
                "匹配结果达到该百分比才采用刮削到的元数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.scrapeConfidence.toFloat(),
                onValueChange = { vm.setScrapeConfidence(it.roundToInt()) },
                valueRange = 0f..100f,
                steps = 99,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow(
            "跳过已刮削档案",
            state.scrapeSkipTagged,
            vm::setScrapeSkipTagged,
            subtitle = "已有标签的档案不再自动刮削",
        )

        SettingsGroupHeader("翻译词库")
        Text(
            "从 EhTagTranslation 数据库下载中文译名，详情页与筛选面板的英文标签会显示为中文。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            if (lastUpdated != null) {
                "已更新：${formatTime(lastUpdated!!)} · ${translationInfo?.namespaceCount ?: translations.size} 个命名空间 · ${translationInfo?.entryCount ?: translations.values.sumOf { it.size }} 条"
            } else {
                "尚未下载翻译库"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        translationInfo?.let { info ->
            Text(
                "版本：${info.version}\n来源：${info.sourceUrl}\n许可：${info.license}\n署名：${info.attribution}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        SwitchRow(
            "自动更新",
            state.tagTranslationAutoUpdate,
            vm::setTagTranslationAutoUpdate,
            subtitle = "每周自动检查并更新词库（默认关闭）",
        )
        Button(
            onClick = vm::updateTranslations,
            enabled = !state.translationUpdating,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            if (state.translationUpdating) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            } else {
                Text("更新标签翻译")
            }
        }
        if (lastUpdated != null || translations.isNotEmpty()) {
            OutlinedButton(
                onClick = vm::clearTranslations,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text("清除翻译")
            }
        }

        SettingsGroupHeader("元数据中文化")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenWriteback)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("元数据中文化向导", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "按向导批量刮削标题与标签，并回写服务器元数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(96.dp))
    }

    if (showRegenDialog) {
        AlertDialog(
            onDismissRequest = { showRegenDialog = false },
            title = { Text("重建全部缩略图") },
            text = { Text("确定要重建全部缩略图吗？可能耗时较长。") },
            confirmButton = {
                TextButton(onClick = {
                    showRegenDialog = false
                    vm.regenAllThumbnails()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenDialog = false }) { Text("取消") }
            },
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("用服务器下载链接") },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("下载链接（直链 zip）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = urlText.trim()
                        if (url.isEmpty()) {
                            Toast.makeText(context, "请输入链接", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        showUrlDialog = false
                        scope.launch {
                            runCatching { container.repository.downloadFromUrl(url) }
                                .onSuccess { Toast.makeText(context, "已提交下载", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "提交失败：${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun scrapeSourceLabel(source: String): String = when (source) {
    "nhentai" -> "nHentai"
    "server_plugin" -> "服务器插件"
    else -> "E-Hentai"
}

@Composable
private fun PermissionSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var batteryWhitelisted by remember { mutableStateOf(checkBatteryWhitelisted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = checkNotificationPermission(context)
                batteryWhitelisted = checkBatteryWhitelisted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SettingsGroupHeader("权限")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("通知权限", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (notificationsGranted) "已允许，可显示下载与任务通知" else "未允许，点按前往系统设置开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notificationsGranted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.BatteryChargingFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("电池优化白名单", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (batteryWhitelisted) "已加入白名单，后台任务不易被系统中断" else "未加入白名单，点按前往系统设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryWhitelisted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        Text(
            "以上权限状态在从系统设置返回本页时自动刷新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(96.dp))
    }
}

private fun checkNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun checkBatteryWhitelisted(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun StatsSection(container: AppContainer) {
    var stats by remember { mutableStateOf<ServerStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        try {
            stats = container.repository.getStats()
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            loading -> LoadingBox(Modifier.fillMaxWidth().height(240.dp))
            error != null -> ErrorBox(error!!, onRetry = { refreshKey++ })
            stats != null -> {
                StatCard("档案总数", stats!!.total_archives.toString())
                StatCard("标签数", stats!!.tags_count.toString())
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun AboutSection() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = "应用图标",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "LANraragi Reader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            "Version 1.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "关于本项目",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "一个为自托管漫画/本子管理服务器 LANraragi 编写的现代 Android 客户端。通过官方 REST API 实现流畅的浏览与阅读体验。",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 24.sp
                )
                Text(
                    "免责声明：本应用仅作学习与交流使用，非 LANraragi 官方出品。服务器及 API 知识产权归原作者所有。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ServerCard(
    state: SettingsViewModel.UiState,
    serverInfo: ServerInfo?,
    onClick: () -> Unit,
) {
    val name = serverInfo?.name?.takeIf { it.isNotBlank() }
        ?: state.name.takeIf { it.isNotBlank() }
        ?: "未配置"
    val address = state.url.takeIf { it.isNotBlank() } ?: "未配置"
    val version = serverInfo?.version?.takeIf { it.isNotBlank() } ?: "未知"
    val connected = ApiClient.config.isConfigured && serverInfo != null

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "服务器",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            ServerInfoLine("名称", name)
            ServerInfoLine("地址", address)
            ServerInfoLine("版本", version)
            ServerInfoLine(
                "连接状态",
                if (connected) "已连接" else "未连接",
                if (connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ServerInfoLine(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SecuritySection(
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SwitchRow("开启密码认证", state.authEnabled, vm::setAuthEnabled)
        SwitchRow("生物认证", state.biometricEnabled, vm::setBiometricEnabled)
        SwitchRow("禁止截屏", state.screenshotProtectionEnabled, vm::setScreenshotProtectionEnabled)
        SwitchRow("在相册中隐藏下载的图片", state.hideInGallery, vm::setHideInGallery)
        SwitchRow("在任务栏中隐藏应用", state.blurInRecents, vm::setBlurInRecents)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun DebugSection(
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SettingsGroupHeader("调试")
        SwitchRow("抓取崩溃日志（Logcat）", state.captureLogcat, vm::setCaptureLogcat)
        Text(
            "开启后，应用发生未捕获异常时会把异常堆栈与 Logcat 输出保存到应用私有目录的 logs 文件夹。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("诊断信息")
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SettingsNavRow(title: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(20.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OfflineLimitRow(value: Long, onSelect: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("离线缓存上限", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text(if (value == 0L) "不自动淘汰" else formatBytes(value)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(0L to "不自动淘汰", 512L * 1024 * 1024 to "512 MB", 2L * 1024 * 1024 * 1024 to "2 GB", 5L * 1024 * 1024 * 1024 to "5 GB").forEach { (n, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(n); menu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("自定义…") },
                    onClick = {
                        menu = false
                        customText = ""
                        customError = false
                        showCustom = true
                    },
                )
            }
        }
    }
    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("自定义离线缓存上限") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = {
                        customText = it
                        if (customError) {
                            customError = parseCacheLimitBytes(it) == null
                        }
                    },
                    label = { Text("缓存上限（MB/GB）") },
                    supportingText = if (customError) {
                        { Text("请输入正整数，例如 768 MB 或 2 GB") }
                    } else {
                        null
                    },
                    isError = customError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val customValue = parseCacheLimitBytes(customText)
                        if (customValue == null) {
                            customError = true
                        } else {
                            onSelect(customValue)
                            showCustom = false
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCustom = false }) { Text("取消") }
            },
        )
    }
}

internal fun parseCacheLimitBytes(value: String): Long? {
    val token = value.trim().uppercase()
    val match = Regex("^(\\d+)\\s*(MB|GB)?$").matchEntire(token) ?: return null
    val amount = match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
    val multiplier = if (match.groupValues[2] == "MB") 1024L * 1024L else 1024L * 1024L * 1024L
    return runCatching { Math.multiplyExact(amount, multiplier) }.getOrNull()
}

@Composable
private fun DownloadConcurrencyRow(value: Int, onSelect: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("同时下载数", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text("$value") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                (1..8).forEach { count ->
                    DropdownMenuItem(
                        text = { Text("$count") },
                        onClick = { onSelect(count); menu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownRow(
    label: String,
    valueLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text(valueLabel) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { onSelect(value); menu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun IntDropdownRow(
    label: String,
    value: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text("$value") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { n ->
                    DropdownMenuItem(
                        text = { Text("$n") },
                        onClick = { onSelect(n); menu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text("$value") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(4, 8, 12, 16, 24, 32).forEach { n ->
                    DropdownMenuItem(
                        text = { Text("$n") },
                        onClick = { onChange(n); menu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("自定义…") },
                    onClick = { menu = false; showCustom = true },
                )
            }
        }
    }
    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("自定义${label}") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("数量") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customText.toIntOrNull()?.let { onChange(it) }
                    showCustom = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCustom = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
