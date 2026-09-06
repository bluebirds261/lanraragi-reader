package com.lanraragi.reader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import com.lanraragi.reader.R
import com.lanraragi.reader.data.AUTO_SCROLL_MAX_SECONDS
import com.lanraragi.reader.data.AUTO_SCROLL_MIN_SECONDS
import com.lanraragi.reader.data.FeatureFlags
import com.lanraragi.reader.data.OfflineUsage
import com.lanraragi.reader.data.autoScrollSeconds
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.ServerInfo
import com.lanraragi.reader.data.model.ServerStats
import com.lanraragi.reader.data.normalizeBaseUrl
import com.lanraragi.reader.data.normalizeAutoScrollSpeed
import com.lanraragi.reader.data.refreshServerInfo
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.ColorPickerDialog
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.parseHexColor
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

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
    INTERFACE("外观", Icons.Filled.Palette),
    STORAGE("下载", Icons.Filled.Folder),
    SECURITY("安全", Icons.Filled.Lock),
    LABS("实验室", Icons.Filled.Build),
    ABOUT("关于", Icons.Filled.Info),
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val url: String = "",
        val key: String = "",
        val name: String = "",
        val readerMode: String = "single",
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
        val readerBackground: String = "black",
        val tapZonesEnabled: Boolean = true,
        val keepScreenOn: Boolean = true,
        val volumeKeysEnabled: Boolean = true,
        val clearNewOnOpen: Boolean = true,
        val galleryViewMode: String = "grid",
        val tagColors: Map<String, String> = emptyMap(),
        val galleryTitle: String = "图库",
        val galleryTitleMode: String = "default",
        val showArchiveCount: Boolean = true,
        val showFloatingButton: Boolean = true,
        val floatingButtonColor: String = "#E94560",
        val bottomBarColor: String = "#1E2835",
        val bottomBarActiveColor: String = "#0084FF",
        val authEnabled: Boolean = false,
        val biometricEnabled: Boolean = false,
        val hideInGallery: Boolean = false,
        val blurInRecents: Boolean = false,
        val extraScanDirUris: Set<String> = emptySet(),
        val featureFlags: Map<String, Boolean> = emptyMap(),
        val offlineCacheLimitGb: Int = 0,
        val downloadConcurrency: Int = 2,
        val offlineUsage: OfflineUsage = OfflineUsage(),
        val isScanning: Boolean = false,
        val translationUpdating: Boolean = false,
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
                    readerBackground = s.readerBackground,
                    tapZonesEnabled = s.tapZonesEnabled,
                    keepScreenOn = s.keepScreenOn,
                    volumeKeysEnabled = s.volumeKeysEnabled,
                    clearNewOnOpen = s.clearNewOnOpen,
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
                    hideInGallery = s.hideInGallery,
                    blurInRecents = s.blurInRecents,
                    extraScanDirUris = s.extraScanDirUris,
                    featureFlags = s.featureFlags,
                    offlineCacheLimitGb = s.offlineCacheLimitGb,
                    downloadConcurrency = s.downloadConcurrency,
                )
            }
        }
    }

    fun onUrlChange(v: String) = _state.update { it.copy(url = v, success = false, message = null) }
    fun onKeyChange(v: String) = _state.update { it.copy(key = v, success = false, message = null) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v, success = false, message = null) }

    fun setReaderMode(mode: String) {
        _state.update { it.copy(readerMode = mode) }
        viewModelScope.launch { container.settingsRepository.setReaderMode(mode) }
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
        viewModelScope.launch { container.settingsRepository.setReadingDirection(dir) }
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

    fun setReaderFitMode(mode: String) {
        _state.update { it.copy(readerFitMode = mode) }
        viewModelScope.launch { container.settingsRepository.setReaderFitMode(mode) }
    }

    fun setReaderBackground(bg: String) {
        _state.update { it.copy(readerBackground = bg) }
        viewModelScope.launch { container.settingsRepository.setReaderBackground(bg) }
    }

    fun setTapZonesEnabled(enabled: Boolean) {
        _state.update { it.copy(tapZonesEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(enabled) }
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
        _state.update { it.copy(biometricEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setBiometricEnabled(enabled) }
    }

    fun setHideInGallery(enabled: Boolean) {
        _state.update { it.copy(hideInGallery = enabled) }
        viewModelScope.launch { container.settingsRepository.setHideInGallery(enabled) }
    }

    fun setBlurInRecents(enabled: Boolean) {
        _state.update { it.copy(blurInRecents = enabled) }
        viewModelScope.launch { container.settingsRepository.setBlurInRecents(enabled) }
    }

    fun setFeatureFlag(key: String, enabled: Boolean) {
        _state.update { it.copy(featureFlags = it.featureFlags + (key to enabled)) }
        viewModelScope.launch { container.settingsRepository.setFeatureFlag(key, enabled) }
    }

    fun setOfflineCacheLimitGb(n: Int) {
        val limit = n.coerceAtLeast(0)
        _state.update { it.copy(offlineCacheLimitGb = limit) }
        viewModelScope.launch { container.settingsRepository.setOfflineCacheLimitGb(limit) }
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
            } catch (e: Exception) {
                _state.update { it.copy(translationUpdating = false, message = e.message ?: "翻译更新失败") }
            }
        }
    }

    fun clearTranslations() {
        container.tagTranslationRepository.clearCache()
        _state.update { it.copy(message = "已清除标签翻译", success = true) }
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
            _state.update { it.copy(saving = false, success = true, message = "已保存") }
        }
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

    fun backupDatabase(context: Context, treeUri: Uri) {
        if (_state.value.backupLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(backupLoading = true) }
            try {
                val jobid = container.repository.queueBackup()
                container.repository.pollJobUntilDone(jobid)
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
                container.repository.restoreBackup(json)
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
fun SettingsScreen(container: AppContainer, onBack: (() -> Unit)? = null, onOpenDrawer: () -> Unit = {}) {
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

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack {
            if (section == SettingsSection.MAIN) onBack?.invoke() else section = SettingsSection.MAIN
        },
        topBar = {
            AppTopBar(
                title = section.title,
                onBack = if (section == SettingsSection.MAIN) {
                    onBack
                } else {
                    { section = SettingsSection.MAIN }
                },
                navigationIcon = if (section == SettingsSection.MAIN && onBack == null) {
                    {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单")
                        }
                    }
                } else {
                    null
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (section) {
                SettingsSection.MAIN -> SettingsMainList { section = it }
                SettingsSection.CONNECT -> ServerSection(container, state, vm)
                SettingsSection.READER -> ReaderSection(state, vm)
                SettingsSection.INTERFACE -> InterfaceSection(state, vm)
                SettingsSection.STORAGE -> StorageSection(state, vm, context) {
                    downloadDirLauncher.launch(null)
                }
                SettingsSection.SECURITY -> SecuritySection(state, vm)
                SettingsSection.LABS -> LabsSection(state, vm)
                SettingsSection.ABOUT -> AboutSection()
            }
        }
    }
}

@Composable
private fun SettingsMainList(onNavigate: (SettingsSection) -> Unit) {
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
            SettingsSection.STORAGE,
            SettingsSection.SECURITY,
            SettingsSection.LABS,
            SettingsSection.ABOUT,
        ).forEach { section ->
            SettingsNavRow(section.title, section.icon) { onNavigate(section) }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun InterfaceSection(state: SettingsViewModel.UiState, vm: SettingsViewModel) {
    var editingNs by remember { mutableStateOf<String?>(null) }
    var fabColorDialog by remember { mutableStateOf(false) }
    var bottomBarColorDialog by remember { mutableStateOf(false) }
    var bottomBarActiveColorDialog by remember { mutableStateOf(false) }
    val namespaces = remember {
        TagRules.NAMESPACE_ORDER.filter { it in TagRules.NAMESPACE_LABELS }
    }
    val lastUpdated by TagTranslationStore.lastUpdated.collectAsState()
    val translations by TagTranslationStore.translations.collectAsState()
    val fabColor = remember(state.floatingButtonColor) {
        parseHexColor(state.floatingButtonColor)?.let { Color(it) } ?: Color(0xFFE94560)
    }
    val bottomBarColor = remember(state.bottomBarColor) {
        parseHexColor(state.bottomBarColor)?.let { Color(it) } ?: Color(0xFF1E2835)
    }
    val bottomBarActiveColor = remember(state.bottomBarActiveColor) {
        parseHexColor(state.bottomBarActiveColor)?.let { Color(it) } ?: Color(0xFF0084FF)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroupHeader("外观")
        DropdownRow(
            "主题", themeLabel(state.theme),
            listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色"),
            vm::setTheme,
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

        SettingsGroupHeader("标签翻译")
        Text(
            "从 EhTagTranslation 数据库下载中文译名，详情页与筛选面板的英文标签会显示为中文。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            if (lastUpdated != null) "已更新：${formatTime(lastUpdated!!)} · ${translations.size} 个命名空间" else "尚未下载翻译库",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSection(state: SettingsViewModel.UiState, vm: SettingsViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DropdownRow(
            "阅读模式", readerModeLabel(state.readerMode),
            listOf("single" to "单页", "multi" to "多页", "continuous" to "连续"),
            vm::setReaderMode,
        )
        AutoScrollSpeedRow(state.autoScrollSpeed, vm::setAutoScrollSpeed)
        IntDropdownRow("每屏页数", state.multiPageCount, (2..8).toList(), vm::setMultiPageCount)
        SwitchRow("横屏自动双页", state.autoDoublePageLandscape, vm::setAutoDoublePageLandscape)
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
        SwitchRow("阅读时屏幕常亮", state.keepScreenOn, vm::setKeepScreenOn)
        SwitchRow("音量键翻页", state.volumeKeysEnabled, vm::setVolumeKeysEnabled)
        SwitchRow("打开详情即清除新标记", state.clearNewOnOpen, vm::setClearNewOnOpen)
        CountRow("在线预载页数", state.preloadOnlineCount, vm::setPreloadOnlineCount)
        CountRow("本地预载页数", state.preloadLocalCount, vm::setPreloadLocalCount)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ServerSection(
    container: AppContainer,
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
) {
    val serverInfo by ApiClient.config.serverInfo.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

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
        if (state.message != null && state.success) {
            Text(state.message!!, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
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
private fun StorageSection(
    state: SettingsViewModel.UiState,
    vm: SettingsViewModel,
    context: Context,
    onPickDownloadDir: () -> Unit,
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

    var showRegenDialog by remember { mutableStateOf(false) }
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
        OfflineLimitRow(state.offlineCacheLimitGb, vm::setOfflineCacheLimitGb)
        Text(
            "已用 ${formatBytes(state.offlineUsage.totalBytes)} / ${if (state.offlineCacheLimitGb == 0) "不限" else "${state.offlineCacheLimitGb} GB"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )

        DownloadConcurrencyRow(state.downloadConcurrency, vm::setDownloadConcurrency)

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

        Spacer(Modifier.height(80.dp))
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
private fun SecuritySection(state: SettingsViewModel.UiState, vm: SettingsViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SwitchRow("开启密码认证", state.authEnabled, vm::setAuthEnabled)
        SwitchRow("生物认证", state.biometricEnabled, vm::setBiometricEnabled)
        SwitchRow("在相册中隐藏下载的图片", state.hideInGallery, vm::setHideInGallery)
        SwitchRow("在任务栏中隐藏应用", state.blurInRecents, vm::setBlurInRecents)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun LabsSection(state: SettingsViewModel.UiState, vm: SettingsViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SettingsGroupHeader("实验室")
        Text(
            "以下功能为实验性开关，默认关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        FeatureFlags.allFlags.forEach { (key, label) ->
            SwitchRow(label, state.featureFlags[key] == true) { vm.setFeatureFlag(key, it) }
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
private fun OfflineLimitRow(value: Int, onSelect: (Int) -> Unit) {
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
            TextButton(onClick = { menu = true }) { Text(if (value == 0) "不限" else "$value GB") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(0 to "不限", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB").forEach { (n, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(n); menu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("自定义…") },
                    onClick = {
                        menu = false
                        customText = value.takeIf { it > 0 }?.toString().orEmpty()
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
                            customError = it.trim().toIntOrNull()?.let { number -> number > 0 } != true
                        }
                    },
                    label = { Text("缓存上限（GB）") },
                    supportingText = if (customError) {
                        { Text("请输入正整数 GB") }
                    } else {
                        null
                    },
                    isError = customError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val customValue = customText.trim().toIntOrNull()?.takeIf { it > 0 }
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
