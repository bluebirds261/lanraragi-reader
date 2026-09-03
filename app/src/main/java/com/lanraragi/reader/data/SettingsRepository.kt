package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.FilterPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 规范化服务器地址：允许直接输入 hostname/反代地址（如 manga.example.com 或
 * manga.example.com/lanraragi），自动补 https:// 前缀；已是 http(s):// 则原样保留。
 */
fun normalizeBaseUrl(input: String): String {
    val t = input.trim().trimEnd('/')
    if (t.isEmpty()) return ""
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}

data class Settings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val serverName: String = "",                 // 自定义服务器名称
    val readerMode: String = "single",          // single(单页) | multi(多页) | continuous(连续)
    val multiPageCount: Int = 2,
    val preloadOnlineCount: Int = 3,
    val readingDirection: String = "ltr",       // ltr(左往右) | rtl(右往左)
    val galleryColumns: Int = 3,
    val previewColumns: Int = 4,
    val previewCount: Int = 12,
    val coverPrefetchCount: Int = 12,
    val downloadDirUri: String? = null,
    val theme: String = "system",
    val presets: List<FilterPreset> = emptyList(),
    val defaultPreset: String = "",
    val readerFitMode: String = "fitWidth",     // fitWidth | fitHeight | fitScreen | original
    val readerBackground: String = "black",     // black | dark | gray | white
    val tapZonesEnabled: Boolean = true,        // 阅读时点击左/右区域翻页
    val keepScreenOn: Boolean = true,           // 阅读时保持屏幕常亮
    val volumeKeysEnabled: Boolean = true,      // 音量键翻页
    val galleryViewMode: String = "grid",       // grid | list
    val tagColors: Map<String, String> = emptyMap(), // namespace -> "#RRGGBB"（自定义标签颜色）
    val galleryTitle: String = "图库",           // 首页标题自定义文字
    val galleryTitleMode: String = "default",    // default(图库) | custom(自定义)
    val showArchiveCount: Boolean = true,        // 首页标题是否显示档案数量
    val showFloatingButton: Boolean = true,      // 是否显示右下角悬浮方块
    val floatingButtonColor: String = "#E94560", // 悬浮方块颜色 "#RRGGBB"
    val bottomBarColor: String = "#1E2835",       // 底栏背景颜色
    val bottomBarActiveColor: String = "#0084FF", // 底栏激活项颜色
    val lastReadArcId: String = "",              // 最后阅读的画廊 ID
    val authEnabled: Boolean = false,            // 密码/生物认证
    val biometricEnabled: Boolean = false,       // 允许生物认证
    val hideInGallery: Boolean = false,          // 隐藏下载图片
    val blurInRecents: Boolean = false,          // 在任务栏中隐藏应用(多任务预览不可读,不影响截图)
    val extraScanDirUris: Set<String> = emptySet(), // 额外扫描路径
    val preloadLocalCount: Int = 5,             // 本地画廊预载页数
)

class SettingsRepository(private val context: Context) {

    private val dataStore = context.settingsDataStore

    companion object {
        val KEY_URL = stringPreferencesKey("base_url")
        val KEY_KEY = stringPreferencesKey("api_key")
        val KEY_SERVER_NAME = stringPreferencesKey("server_name")
        val KEY_MODE = stringPreferencesKey("reader_mode")
        val KEY_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_MULTI = intPreferencesKey("multi_page_count")
        val KEY_PRELOAD_ONLINE = intPreferencesKey("preload_online_count")
        val KEY_GALLERY_COLS = intPreferencesKey("gallery_columns")
        val KEY_PREVIEW_COLS = intPreferencesKey("preview_columns")
        val KEY_PREVIEW_COUNT = intPreferencesKey("preview_count")
        val KEY_COVER_COUNT = intPreferencesKey("cover_prefetch_count")
        val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir_uri")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_PRESETS = stringPreferencesKey("filter_presets")
        val KEY_DEFAULT_PRESET = stringPreferencesKey("default_preset")
        val KEY_FIT_MODE = stringPreferencesKey("reader_fit_mode")
        val KEY_READER_BG = stringPreferencesKey("reader_background")
        val KEY_TAP_ZONES = booleanPreferencesKey("tap_zones_enabled")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_VOLUME_KEYS = booleanPreferencesKey("volume_keys_enabled")
        val KEY_GALLERY_VIEW = stringPreferencesKey("gallery_view_mode")
        val KEY_TAG_COLORS = stringPreferencesKey("tag_colors")
        val KEY_GALLERY_TITLE = stringPreferencesKey("gallery_title")
        val KEY_GALLERY_TITLE_MODE = stringPreferencesKey("gallery_title_mode")
        val KEY_SHOW_COUNT = booleanPreferencesKey("show_archive_count")
        val KEY_SHOW_FAB = booleanPreferencesKey("show_floating_button")
        val KEY_FAB_COLOR = stringPreferencesKey("floating_button_color")
        val KEY_BOTTOM_BAR_COLOR = stringPreferencesKey("bottom_bar_color")
        val KEY_BOTTOM_BAR_ACTIVE_COLOR = stringPreferencesKey("bottom_bar_active_color")
        val KEY_LAST_READ_ARCID = stringPreferencesKey("last_read_arcid")
        val KEY_AUTH_ENABLED = booleanPreferencesKey("auth_enabled")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_HIDE_IN_GALLERY = booleanPreferencesKey("hide_in_gallery")
        val KEY_BLUR_IN_RECENTS = booleanPreferencesKey("blur_in_recents")
        val KEY_EXTRA_SCAN_DIRS = stringPreferencesKey("extra_scan_dirs")
        val KEY_PRELOAD_LOCAL = intPreferencesKey("preload_local_count")
    }

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            baseUrl = p[KEY_URL] ?: "",
            apiKey = p[KEY_KEY] ?: "",
            serverName = p[KEY_SERVER_NAME] ?: "",
            readerMode = p[KEY_MODE] ?: "single",
            multiPageCount = p[KEY_MULTI] ?: 2,
            preloadOnlineCount = p[KEY_PRELOAD_ONLINE] ?: 3,
            readingDirection = p[KEY_DIRECTION] ?: "ltr",
            galleryColumns = p[KEY_GALLERY_COLS] ?: 3,
            previewColumns = p[KEY_PREVIEW_COLS] ?: 3,
            previewCount = p[KEY_PREVIEW_COUNT] ?: 12,
            coverPrefetchCount = p[KEY_COVER_COUNT] ?: 12,
            downloadDirUri = p[KEY_DOWNLOAD_DIR],
            theme = p[KEY_THEME] ?: "system",
            presets = runCatching {
                ApiClient.json.decodeFromString<List<FilterPreset>>(p[KEY_PRESETS] ?: "[]")
            }.getOrDefault(emptyList()),
            defaultPreset = p[KEY_DEFAULT_PRESET] ?: "",
            readerFitMode = p[KEY_FIT_MODE] ?: "fitWidth",
            readerBackground = p[KEY_READER_BG] ?: "black",
            tapZonesEnabled = p[KEY_TAP_ZONES] ?: true,
            keepScreenOn = p[KEY_KEEP_SCREEN_ON] ?: true,
            volumeKeysEnabled = p[KEY_VOLUME_KEYS] ?: true,
            galleryViewMode = p[KEY_GALLERY_VIEW] ?: "grid",
            tagColors = runCatching {
                ApiClient.json.decodeFromString<Map<String, String>>(p[KEY_TAG_COLORS] ?: "{}")
            }.getOrDefault(emptyMap()),
            galleryTitle = p[KEY_GALLERY_TITLE] ?: "图库",
            galleryTitleMode = p[KEY_GALLERY_TITLE_MODE] ?: "default",
            showArchiveCount = p[KEY_SHOW_COUNT] ?: true,
            showFloatingButton = p[KEY_SHOW_FAB] ?: true,
            floatingButtonColor = p[KEY_FAB_COLOR] ?: "#E94560",
            bottomBarColor = p[KEY_BOTTOM_BAR_COLOR] ?: "#1E2835",
            bottomBarActiveColor = p[KEY_BOTTOM_BAR_ACTIVE_COLOR] ?: "#0084FF",
            lastReadArcId = p[KEY_LAST_READ_ARCID] ?: "",
            authEnabled = p[KEY_AUTH_ENABLED] ?: false,
            biometricEnabled = p[KEY_BIOMETRIC_ENABLED] ?: false,
            hideInGallery = p[KEY_HIDE_IN_GALLERY] ?: false,
            blurInRecents = p[KEY_BLUR_IN_RECENTS] ?: false,
            extraScanDirUris = runCatching {
                ApiClient.json.decodeFromString<Set<String>>(p[KEY_EXTRA_SCAN_DIRS] ?: "[]")
            }.getOrDefault(emptySet()),
            preloadLocalCount = p[KEY_PRELOAD_LOCAL] ?: 5,
        )
    }

    /** 把持久化的设置同步到运行时 [ApiClient.config]，供拦截器使用。 */
    suspend fun applyToRuntime() {
        val s = settings.first()
        ApiClient.config.baseUrl = s.baseUrl.trim().trimEnd('/')
        ApiClient.config.apiKey = s.apiKey.trim()
    }

    suspend fun saveServer(url: String, apiKey: String, name: String = "") {
        dataStore.edit {
            it[KEY_URL] = normalizeBaseUrl(url)
            it[KEY_KEY] = apiKey.trim()
            it[KEY_SERVER_NAME] = name.trim()
        }
        applyToRuntime()
    }

    suspend fun setReaderMode(mode: String) {
        dataStore.edit { it[KEY_MODE] = mode }
    }

    suspend fun setMultiPageCount(n: Int) {
        dataStore.edit { it[KEY_MULTI] = n.coerceIn(2, 8) }
    }

    suspend fun setPreloadOnlineCount(n: Int) {
        dataStore.edit { it[KEY_PRELOAD_ONLINE] = n.coerceIn(1, 20) }
    }

    suspend fun setReadingDirection(direction: String) {
        dataStore.edit { it[KEY_DIRECTION] = direction }
    }

    suspend fun setGalleryColumns(n: Int) {
        dataStore.edit { it[KEY_GALLERY_COLS] = n.coerceIn(2, 8) }
    }

    suspend fun setPreviewColumns(n: Int) {
        dataStore.edit { it[KEY_PREVIEW_COLS] = n.coerceIn(2, 8) }
    }

    suspend fun setPreviewCount(n: Int) {
        dataStore.edit { it[KEY_PREVIEW_COUNT] = n.coerceIn(4, 200) }
    }

    suspend fun setCoverPrefetchCount(n: Int) {
        dataStore.edit { it[KEY_COVER_COUNT] = n.coerceIn(4, 200) }
    }

    suspend fun setDownloadDirUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(KEY_DOWNLOAD_DIR) else it[KEY_DOWNLOAD_DIR] = uri
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun savePreset(preset: FilterPreset) {
        val current = settings.first().presets
        val updated = current.filter { it.name != preset.name } + preset
        dataStore.edit { it[KEY_PRESETS] = ApiClient.json.encodeToString(updated) }
    }

    suspend fun deletePreset(name: String) {
        val current = settings.first().presets
        val updated = current.filter { it.name != name }
        dataStore.edit { it[KEY_PRESETS] = ApiClient.json.encodeToString(updated) }
    }

    suspend fun setPresets(presets: List<FilterPreset>) {
        dataStore.edit { it[KEY_PRESETS] = ApiClient.json.encodeToString(presets) }
    }

    suspend fun setDefaultPreset(name: String) {
        dataStore.edit { it[KEY_DEFAULT_PRESET] = name }
    }

    suspend fun setReaderFitMode(mode: String) {
        dataStore.edit { it[KEY_FIT_MODE] = mode }
    }

    suspend fun setReaderBackground(bg: String) {
        dataStore.edit { it[KEY_READER_BG] = bg }
    }

    suspend fun setTapZonesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TAP_ZONES] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setVolumeKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VOLUME_KEYS] = enabled }
    }

    suspend fun setGalleryViewMode(mode: String) {
        dataStore.edit { it[KEY_GALLERY_VIEW] = mode }
    }

    suspend fun setTagColor(namespace: String, hex: String) {
        val ns = namespace.lowercase()
        val updated = settings.first().tagColors + (ns to hex.uppercase())
        dataStore.edit { it[KEY_TAG_COLORS] = ApiClient.json.encodeToString(updated) }
    }

    suspend fun resetTagColor(namespace: String) {
        val ns = namespace.lowercase()
        val updated = settings.first().tagColors - ns
        dataStore.edit { it[KEY_TAG_COLORS] = ApiClient.json.encodeToString(updated) }
    }

    suspend fun setGalleryTitle(title: String) {
        dataStore.edit { it[KEY_GALLERY_TITLE] = title }
    }

    suspend fun setGalleryTitleMode(mode: String) {
        dataStore.edit { it[KEY_GALLERY_TITLE_MODE] = mode }
    }

    suspend fun setShowArchiveCount(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_COUNT] = show }
    }

    suspend fun setShowFloatingButton(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_FAB] = show }
    }

    suspend fun setFloatingButtonColor(hex: String) {
        dataStore.edit { it[KEY_FAB_COLOR] = hex.uppercase() }
    }

    suspend fun setBottomBarColor(hex: String) {
        dataStore.edit { it[KEY_BOTTOM_BAR_COLOR] = hex.uppercase() }
    }

    suspend fun setBottomBarActiveColor(hex: String) {
        dataStore.edit { it[KEY_BOTTOM_BAR_ACTIVE_COLOR] = hex.uppercase() }
    }

    suspend fun setLastReadArcId(arcid: String) {
        dataStore.edit { it[KEY_LAST_READ_ARCID] = arcid }
    }

    suspend fun setAuthEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTH_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setHideInGallery(enabled: Boolean) {
        dataStore.edit { it[KEY_HIDE_IN_GALLERY] = enabled }
        val downloadUri = settings.first().downloadDirUri
        if (downloadUri != null) {
            updateNoMediaFile(downloadUri, enabled)
        }
    }

    private fun updateNoMediaFile(uriString: String, hide: Boolean) {
        runCatching {
            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(uriString))
            if (root != null && root.isDirectory) {
                val noMedia = root.findFile(".nomedia")
                if (hide && noMedia == null) {
                    root.createFile("application/octet-stream", ".nomedia")
                } else if (!hide && noMedia != null) {
                    noMedia.delete()
                }
            }
        }
    }

    suspend fun setBlurInRecents(enabled: Boolean) {
        dataStore.edit { it[KEY_BLUR_IN_RECENTS] = enabled }
    }

    suspend fun addExtraScanDirUri(uri: String) {
        val current = settings.first().extraScanDirUris
        dataStore.edit { it[KEY_EXTRA_SCAN_DIRS] = ApiClient.json.encodeToString(current + uri) }
    }

    suspend fun removeExtraScanDirUri(uri: String) {
        val current = settings.first().extraScanDirUris
        dataStore.edit { it[KEY_EXTRA_SCAN_DIRS] = ApiClient.json.encodeToString(current - uri) }
    }

    suspend fun setPreloadLocalCount(n: Int) {
        dataStore.edit { it[KEY_PRELOAD_LOCAL] = n.coerceIn(1, 50) }
    }
}
