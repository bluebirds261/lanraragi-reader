package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.FilterPreset
import com.lanraragi.reader.data.model.ServerProfile
import kotlinx.coroutines.CancellationException
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
    val autoDoublePageLandscape: Boolean = true, // 横屏时单页模式自动双页(C7)
    val preloadOnlineCount: Int = 3,
    val readingDirection: String = "ltr",       // ltr(左往右) | rtl(右往左)
    val autoScrollSpeed: String = "off",        // off | slow | medium | fast（连续模式自动滚动速度）
    val galleryColumns: Int = 3,
    val previewColumns: Int = 4,
    val previewCount: Int = 12,
    val coverPrefetchCount: Int = 12,
    val downloadDirUri: String? = null,
    val theme: String = "system",
    val presets: List<FilterPreset> = emptyList(),
    val defaultPreset: String = "",
    val readerFitMode: String = "fitWidth",     // fitWidth | fitHeight | fitScreen | original
    val readerBackground: String = "black",     // black | dark | gray | white | auto(跟随系统深浅色)
    val readerBrightness: Int = -1,             // 阅读器亮度：-1 跟随系统，0~100 固定
    val tapZonesEnabled: Boolean = true,        // 阅读时点击左/右区域翻页
    val keepScreenOn: Boolean = true,           // 阅读时保持屏幕常亮
    val volumeKeysEnabled: Boolean = true,      // 音量键翻页
    val galleryViewMode: String = "grid",       // grid | list
    val gallerySortby: String = "title",        // 图库排序字段：title | lastread | date_added | artist | language | series
    val galleryOrder: String = "asc",           // 图库排序方向：asc | desc
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
    val clearNewOnOpen: Boolean = true,         // 打开详情即清除服务器"新"标记(A7)
    val featureFlags: Map<String, Boolean> = emptyMap(), // 实验性功能开关(E5)，默认空即全部关闭
    val offlineCacheLimitGb: Int = 0,           // 离线缓存上限(GB)，0 = 不限
    // E4
    val profiles: List<ServerProfile> = emptyList(),  // 服务器配置列表
    val activeProfileIndex: Int = 0,                  // 当前使用的 profile 索引
)

class SettingsRepository(private val context: Context) {

    private val dataStore = context.settingsDataStore

    companion object {
        val KEY_URL = stringPreferencesKey("base_url")
        val KEY_KEY = stringPreferencesKey("api_key")
        val KEY_SERVER_NAME = stringPreferencesKey("server_name")
        val KEY_MODE = stringPreferencesKey("reader_mode")
        val KEY_AUTO_SCROLL_SPEED = stringPreferencesKey("auto_scroll_speed")
        val KEY_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_MULTI = intPreferencesKey("multi_page_count")
        val KEY_AUTO_DOUBLE_PAGE_LANDSCAPE = booleanPreferencesKey("auto_double_page_landscape")
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
        val KEY_READER_BRIGHTNESS = intPreferencesKey("reader_brightness")
        val KEY_TAP_ZONES = booleanPreferencesKey("tap_zones_enabled")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_VOLUME_KEYS = booleanPreferencesKey("volume_keys_enabled")
        val KEY_GALLERY_VIEW = stringPreferencesKey("gallery_view_mode")
        val KEY_GALLERY_SORTBY = stringPreferencesKey("gallery_sortby")
        val KEY_GALLERY_ORDER = stringPreferencesKey("gallery_order")
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
        val KEY_CLEAR_NEW_ON_OPEN = booleanPreferencesKey("clear_new_on_open")
        val KEY_FEATURE_FLAGS = stringPreferencesKey("feature_flags")
        val KEY_OFFLINE_CACHE_LIMIT = intPreferencesKey("offline_cache_limit_gb")
        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_PROFILES = stringPreferencesKey("profiles_json")
        val KEY_ACTIVE_PROFILE = intPreferencesKey("active_profile_index")
    }

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            baseUrl = p[KEY_URL] ?: "",
            // 磁盘上是密文（或旧明文），这里统一解密为明文暴露给上层；UI 回显与运行时均拿到明文。
            apiKey = SecurePrefs.decrypt(p[KEY_KEY] ?: ""),
            serverName = p[KEY_SERVER_NAME] ?: "",
            readerMode = p[KEY_MODE] ?: "single",
            multiPageCount = p[KEY_MULTI] ?: 2,
            autoDoublePageLandscape = p[KEY_AUTO_DOUBLE_PAGE_LANDSCAPE] ?: true,
            preloadOnlineCount = p[KEY_PRELOAD_ONLINE] ?: 3,
            readingDirection = p[KEY_DIRECTION] ?: "ltr",
            autoScrollSpeed = p[KEY_AUTO_SCROLL_SPEED] ?: "off",
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
            readerBrightness = p[KEY_READER_BRIGHTNESS] ?: -1,
            tapZonesEnabled = p[KEY_TAP_ZONES] ?: true,
            keepScreenOn = p[KEY_KEEP_SCREEN_ON] ?: true,
            volumeKeysEnabled = p[KEY_VOLUME_KEYS] ?: true,
            galleryViewMode = p[KEY_GALLERY_VIEW] ?: "grid",
            gallerySortby = p[KEY_GALLERY_SORTBY] ?: "title",
            galleryOrder = p[KEY_GALLERY_ORDER] ?: "asc",
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
            clearNewOnOpen = p[KEY_CLEAR_NEW_ON_OPEN] ?: true,
            featureFlags = runCatching {
                ApiClient.json.decodeFromString<Map<String, Boolean>>(p[KEY_FEATURE_FLAGS] ?: "{}")
            }.getOrDefault(emptyMap()),
            offlineCacheLimitGb = p[KEY_OFFLINE_CACHE_LIMIT] ?: 0,
            profiles = runCatching {
                ApiClient.json.decodeFromString<List<ServerProfile>>(p[KEY_PROFILES] ?: "[]")
            }.getOrDefault(emptyList()),
            activeProfileIndex = p[KEY_ACTIVE_PROFILE] ?: 0,
        )
    }

    val schemaVersion: Flow<Int> = dataStore.data.map { it[KEY_SCHEMA_VERSION] ?: 1 }

    init {
        DataMigration.register(2) { migrateApiKeyV2() }
        DataMigration.register(3) { migrateProfilesV3() }
    }

    /** 执行未跑过的迁移步骤（版本链），成功后写入 schema_version。 */
    suspend fun migrateIfNeeded() {
        try {
            val cur = dataStore.data.first()[KEY_SCHEMA_VERSION] ?: 1
            val next = DataMigration.runPending(context, cur)
            if (next > cur) dataStore.edit { it[KEY_SCHEMA_VERSION] = next }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 静默：迁移失败不阻断启动，下次启动重试。
        }
    }

    /** 把持久化的设置同步到运行时 [ApiClient.config]，供拦截器使用。 */
    suspend fun applyToRuntime() {
        val s = settings.first()
        // E4：优先从 profiles 取当前活跃配置，为空时回退旧 3 键
        val profile = s.profiles.getOrNull(s.activeProfileIndex)
        ApiClient.config.baseUrl = (profile?.url ?: s.baseUrl).trim().trimEnd('/')
        ApiClient.config.apiKey = (profile?.apiKey ?: s.apiKey).trim()
        migrateIfNeeded()
    }

    /**
     * 旧版本明文 api_key 迁移到 Keystore 加密存储（F1，幂等）。
     * 仅在原始值非空且不以加密前缀开头（旧明文）时改写；失败静默（加密失败回退明文，
     * 不阻断登录），由下次启动的 [applyToRuntime] 自然重试。
     */
    private suspend fun migrateApiKeyV2() {
        try {
            val raw = dataStore.data.first()[KEY_KEY] ?: return
            if (raw.isEmpty() || raw.startsWith(SecurePrefs.PREFIX)) return
            val encrypted = SecurePrefs.encrypt(raw) ?: return
            dataStore.edit { it[KEY_KEY] = encrypted }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 静默：迁移失败不影响本次登录，下次启动重试。
        }
    }

    /** E4 v3 迁移：旧的 baseUrl/apiKey/serverName 三键迁移到 profiles[0]。 */
    private suspend fun migrateProfilesV3() {
        try {
            val prefs = dataStore.data.first()
            val url = prefs[KEY_URL]?.trim()?.takeIf { it.isNotBlank() } ?: return
            val key = prefs[KEY_KEY] ?: "" // 保持原值（明文或密文），applyToRuntime 会解密
            val name = prefs[KEY_SERVER_NAME] ?: ""
            val profile = ServerProfile(name = name, url = url, apiKey = key)
            dataStore.edit {
                it[KEY_PROFILES] = ApiClient.json.encodeToString(listOf(profile))
                it[KEY_ACTIVE_PROFILE] = 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun saveServer(url: String, apiKey: String, name: String = "") {
        dataStore.edit {
            it[KEY_URL] = normalizeBaseUrl(url)
            it[KEY_KEY] = SecurePrefs.encrypt(apiKey.trim()) ?: apiKey.trim()
            it[KEY_SERVER_NAME] = name.trim()
            // E4 双写：同时更新 profiles[0]
            val profiles = runCatching {
                ApiClient.json.decodeFromString<List<ServerProfile>>(it[KEY_PROFILES] ?: "[]")
            }.getOrDefault(emptyList()).toMutableList()
            val p = ServerProfile(name = name.trim(), url = normalizeBaseUrl(url), apiKey = it[KEY_KEY] ?: apiKey.trim())
            if (profiles.isNotEmpty()) profiles[0] = p else profiles.add(p)
            it[KEY_PROFILES] = ApiClient.json.encodeToString(profiles)
        }
        applyToRuntime()
    }

    suspend fun setReaderMode(mode: String) {
        dataStore.edit { it[KEY_MODE] = mode }
    }

    suspend fun setAutoScrollSpeed(s: String) {
        dataStore.edit { it[KEY_AUTO_SCROLL_SPEED] = s }
    }

    suspend fun setMultiPageCount(n: Int) {
        dataStore.edit { it[KEY_MULTI] = n.coerceIn(2, 8) }
    }

    suspend fun setAutoDoublePageLandscape(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_DOUBLE_PAGE_LANDSCAPE] = enabled }
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

    suspend fun setReaderBrightness(n: Int) {
        dataStore.edit { it[KEY_READER_BRIGHTNESS] = n.coerceIn(-1, 100) }
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

    suspend fun setGallerySortby(s: String) {
        dataStore.edit { it[KEY_GALLERY_SORTBY] = s }
    }

    suspend fun setGalleryOrder(s: String) {
        dataStore.edit { it[KEY_GALLERY_ORDER] = s }
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

    suspend fun setClearNewOnOpen(enabled: Boolean) {
        dataStore.edit { it[KEY_CLEAR_NEW_ON_OPEN] = enabled }
    }

    suspend fun setFeatureFlag(key: String, enabled: Boolean) {
        val updated = settings.first().featureFlags + (key to enabled)
        dataStore.edit { it[KEY_FEATURE_FLAGS] = ApiClient.json.encodeToString(updated) }
    }

    suspend fun setOfflineCacheLimitGb(n: Int) {
        dataStore.edit { it[KEY_OFFLINE_CACHE_LIMIT] = n.coerceIn(0, 100) }
    }
}
