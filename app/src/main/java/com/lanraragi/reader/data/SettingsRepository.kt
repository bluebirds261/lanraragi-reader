package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.download.DEFAULT_DOWNLOAD_MAX_RETRIES
import com.lanraragi.reader.data.download.MAX_DOWNLOAD_MAX_RETRIES
import com.lanraragi.reader.data.download.MIN_DOWNLOAD_MAX_RETRIES
import com.lanraragi.reader.data.model.FilterPreset
import com.lanraragi.reader.data.model.ServerProfile
import com.lanraragi.reader.data.security.AndroidKeystoreSecretStore
import com.lanraragi.reader.data.security.ConfigTransferCodec
import com.lanraragi.reader.data.security.filterSensitiveFields
import com.lanraragi.reader.data.storage.StorageRootState
import com.lanraragi.reader.data.storage.resolveStorageRoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

fun normalizeBaseUrl(input: String): String {
    val t = input.trim().trimEnd('/')
    if (t.isEmpty()) return ""
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}

const val AUTO_SCROLL_MIN_SECONDS = 0.5f
const val AUTO_SCROLL_MAX_SECONDS = 30f
const val AUTO_SCROLL_DEFAULT_SECONDS = 3f

private fun parseAutoScrollSeconds(value: String): Double = when (value.trim().lowercase()) {
    "slow" -> 6.0
    "medium", "off" -> AUTO_SCROLL_DEFAULT_SECONDS.toDouble()
    "fast" -> 1.5
    else -> value.trim().toDoubleOrNull()?.coerceIn(
        AUTO_SCROLL_MIN_SECONDS.toDouble(),
        AUTO_SCROLL_MAX_SECONDS.toDouble(),
    ) ?: AUTO_SCROLL_DEFAULT_SECONDS.toDouble()
}

fun autoScrollSeconds(value: String): Float = parseAutoScrollSeconds(value).toFloat()

fun normalizeAutoScrollSpeed(value: String): String = BigDecimal
    .valueOf(parseAutoScrollSeconds(value))
    .stripTrailingZeros()
    .toPlainString()

data class Settings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val serverName: String = "",
    val readerMode: String = "single",
    val multiPageCount: Int = 2,
    val autoDoublePageLandscape: Boolean = true,
    val preloadOnlineCount: Int = 3,
    val readingDirection: String = "ltr",
    val autoScrollSpeed: String = "3",
    val galleryColumns: Int = 3,
    val previewColumns: Int = 4,
    val previewCount: Int = 12,
    val coverPrefetchCount: Int = 12,
    val downloadDirUri: String? = null,
    val theme: String = "system",
    val presets: List<FilterPreset> = emptyList(),
    val defaultPreset: String = "",
    val readerFitMode: String = "fitWidth",
    val readerBackground: String = "black",
    val readerBrightness: Int = -1,
    val tapZonesEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val volumeKeysEnabled: Boolean = true,
    val galleryViewMode: String = "grid",
    val gallerySortby: String = "title",
    val galleryOrder: String = "asc",
    val tagColors: Map<String, String> = emptyMap(),
    val galleryTitle: String = "图库",
    val galleryTitleMode: String = "default",
    val showArchiveCount: Boolean = true,
    val showFloatingButton: Boolean = true,
    val floatingButtonColor: String = "#E94560",
    val bottomBarColor: String = "#1E2835",
    val bottomBarActiveColor: String = "#0084FF",
    val lastReadArcId: String = "",
    val authEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val screenshotProtectionEnabled: Boolean = false,
    val hideInGallery: Boolean = false,
    val blurInRecents: Boolean = false,
    val extraScanDirUris: Set<String> = emptySet(),
    val preloadLocalCount: Int = 5,
    val clearNewOnOpen: Boolean = true,
    val offlineCacheLimitBytes: Long = 0L,
    /** 同时下载的最大任务数，1..8。 */
    val downloadConcurrency: Int = 2,
    /** C1 每秒最多启动的下载任务数，1..10。 */
    val downloadRatePerSecond: Int = 2,
    /** C3 单个任务的自动重试上限，1..10（默认与 JHenTai 对齐的 5 次）。 */
    val downloadMaxRetries: Int = DEFAULT_DOWNLOAD_MAX_RETRIES,
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileIndex: Int = 0,
    /** Optional orientation-specific reader overrides; null means use the global value. */
    val readerModePortrait: String? = null,
    val readerModeLandscape: String? = null,
    val readingDirectionPortrait: String? = null,
    val readingDirectionLandscape: String? = null,
    val readerFitModePortrait: String? = null,
    val readerFitModeLandscape: String? = null,
    val doublePageFirstPageAlone: Boolean = false,
    val doublePageFirstPageAlonePortrait: Boolean? = null,
    val doublePageFirstPageAloneLandscape: Boolean? = null,
    /** 抓取崩溃日志：开启后应用崩溃时把异常堆栈与 Logcat 输出写入私有目录 logs 文件夹。 */
    val captureLogcat: Boolean = false,
    /** 图库存储根目录（SAF tree URI）；null 表示使用默认目录。 */
    val storageRootUri: String? = null,
    /** 首启向导是否已完成；未完成时启动进入向导。 */
    val onboardingCompleted: Boolean = false,
    /** Android 12+ 动态取色主题。 */
    val dynamicColor: Boolean = false,
    /** 阅读器页面浮层（页码等 HUD 叠加显示）。 */
    val readerPageOverlay: Boolean = true,
    /** 阅读器点击分区布局（ui/reader/TapZoneConfig.kt 的 TapZoneGrid 序列化 JSON）；null 表示默认三列布局。 */
    val tapZoneConfig: String? = null,
    /** 阅读器翻页动画：smooth / curl / fade。 */
    val pageTurnAnimation: String = "smooth",
    /** 阅读器图片显示区域宽度占比（0.5..1，1 为占满）。 */
    val imageRegionWidthRatio: Float = 1f,
    /** 元数据刮削默认来源：ehentai / nhentai / server_plugin（供后续 D2 元数据中文化消费）。 */
    val scrapeSource: String = "ehentai",
    /** 元数据刮削匹配置信度阈值（0..100）。 */
    val scrapeConfidence: Int = 70,
    /** 元数据刮削时跳过已有标签的档案。 */
    val scrapeSkipTagged: Boolean = true,
    /** D3 翻译词库自动更新：开启后词库超过 7 天未更新时在后台静默刷新。 */
    val tagTranslationAutoUpdate: Boolean = false,
)

data class ReaderPreferences(
    val mode: String,
    val multiPageCount: Int,
    val autoDoublePageLandscape: Boolean,
    val preloadOnlineCount: Int,
    val preloadLocalCount: Int,
    val readingDirection: String,
    val fitMode: String,
    val background: String,
    val brightness: Int,
    val tapZonesEnabled: Boolean,
    val keepScreenOn: Boolean,
    val volumeKeysEnabled: Boolean,
    val autoScrollSpeed: String,
    val firstPageAlone: Boolean,
)

data class DownloadPreferences(
    val directoryUri: String?,
    val concurrency: Int,
    val offlineCacheLimitBytes: Long,
)

data class LibraryPreferences(
    val columns: Int,
    val viewMode: String,
    val sortBy: String,
    val order: String,
    val presets: List<FilterPreset>,
    val defaultPreset: String,
    val extraScanDirectoryUris: Set<String>,
)

data class AppearancePreferences(
    val theme: String,
    val tagColors: Map<String, String>,
    val galleryTitle: String,
    val galleryTitleMode: String,
    val showArchiveCount: Boolean,
    val showFloatingButton: Boolean,
    val floatingButtonColor: String,
    val bottomBarColor: String,
    val bottomBarActiveColor: String,
)

data class ConfigImportPreview(val payload: String, val changedFields: List<String>)

enum class ReaderOrientationProfile { GLOBAL, PORTRAIT, LANDSCAPE }

class SettingsRepository(private val context: Context) {

    private val dataStore = context.settingsDataStore
    private val secretStore = AndroidKeystoreSecretStore(context)
    @Volatile private var apiKeyCache: String = ""

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
        val KEY_SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection_enabled")
        val KEY_HIDE_IN_GALLERY = booleanPreferencesKey("hide_in_gallery")
        val KEY_BLUR_IN_RECENTS = booleanPreferencesKey("blur_in_recents")
        val KEY_EXTRA_SCAN_DIRS = stringPreferencesKey("extra_scan_dirs")
        val KEY_PRELOAD_LOCAL = intPreferencesKey("preload_local_count")
        val KEY_CLEAR_NEW_ON_OPEN = booleanPreferencesKey("clear_new_on_open")
        val KEY_OFFLINE_CACHE_LIMIT = intPreferencesKey("offline_cache_limit_gb")
        val KEY_OFFLINE_CACHE_LIMIT_BYTES = longPreferencesKey("offline_cache_limit_bytes")
        val KEY_DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
        val KEY_DOWNLOAD_RATE_PER_SECOND = intPreferencesKey("download_rate_per_second")
        val KEY_DOWNLOAD_MAX_RETRIES = intPreferencesKey("download_max_retries")
        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_PROFILES = stringPreferencesKey("profiles_json")
        val KEY_ACTIVE_PROFILE = intPreferencesKey("active_profile_index")
        val KEY_MODE_PORTRAIT = stringPreferencesKey("reader_mode_portrait")
        val KEY_MODE_LANDSCAPE = stringPreferencesKey("reader_mode_landscape")
        val KEY_DIRECTION_PORTRAIT = stringPreferencesKey("reading_direction_portrait")
        val KEY_DIRECTION_LANDSCAPE = stringPreferencesKey("reading_direction_landscape")
        val KEY_FIT_MODE_PORTRAIT = stringPreferencesKey("reader_fit_mode_portrait")
        val KEY_FIT_MODE_LANDSCAPE = stringPreferencesKey("reader_fit_mode_landscape")
        val KEY_FIRST_PAGE_ALONE = booleanPreferencesKey("double_page_first_page_alone")
        val KEY_FIRST_PAGE_ALONE_PORTRAIT = booleanPreferencesKey("double_page_first_page_alone_portrait")
        val KEY_FIRST_PAGE_ALONE_LANDSCAPE = booleanPreferencesKey("double_page_first_page_alone_landscape")
        val KEY_CAPTURE_LOGCAT = booleanPreferencesKey("capture_logcat")
        val KEY_STORAGE_ROOT = stringPreferencesKey("storage_root_uri")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_READER_PAGE_OVERLAY = booleanPreferencesKey("reader_page_overlay")
        val KEY_TAP_ZONE_CONFIG = stringPreferencesKey("tap_zone_config")
        val KEY_PAGE_TURN_ANIMATION = stringPreferencesKey("page_turn_animation")
        val KEY_IMAGE_REGION_WIDTH_RATIO = floatPreferencesKey("image_region_width_ratio")
        val KEY_SCRAPE_SOURCE = stringPreferencesKey("scrape_source")
        val KEY_SCRAPE_CONFIDENCE = intPreferencesKey("scrape_confidence")
        val KEY_SCRAPE_SKIP_TAGGED = booleanPreferencesKey("scrape_skip_tagged")
        val KEY_TAG_TRANSLATION_AUTO_UPDATE = booleanPreferencesKey("tag_translation_auto_update")
        const val SECRET_API_KEY = "lanraragi/api_key"
        private val SCRAPE_SOURCES = setOf("ehentai", "nhentai", "server_plugin")
        private val READER_MODES = setOf("single", "multi", "continuous")
        private val READING_DIRECTIONS = setOf("ltr", "rtl", "ttb")
        private val FIT_MODES = setOf("fitWidth", "fitHeight", "fitScreen", "original")
        private val PAGE_TURN_ANIMATIONS = setOf("smooth", "curl", "fade")
        private val READER_BACKGROUNDS = setOf("black", "dark", "gray", "white", "auto")
    }

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            baseUrl = p[KEY_URL] ?: "",
            apiKey = apiKeyCache,
            serverName = p[KEY_SERVER_NAME] ?: "",
            readerMode = p[KEY_MODE] ?: "single",
            multiPageCount = p[KEY_MULTI] ?: 2,
            autoDoublePageLandscape = p[KEY_AUTO_DOUBLE_PAGE_LANDSCAPE] ?: true,
            preloadOnlineCount = p[KEY_PRELOAD_ONLINE] ?: 3,
            readingDirection = p[KEY_DIRECTION] ?: "ltr",
            autoScrollSpeed = normalizeAutoScrollSpeed(p[KEY_AUTO_SCROLL_SPEED] ?: "3"),
            galleryColumns = p[KEY_GALLERY_COLS] ?: 3,
            previewColumns = p[KEY_PREVIEW_COLS] ?: 4,
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
            screenshotProtectionEnabled = p[KEY_SCREENSHOT_PROTECTION] ?: false,
            hideInGallery = p[KEY_HIDE_IN_GALLERY] ?: false,
            blurInRecents = p[KEY_BLUR_IN_RECENTS] ?: false,
            extraScanDirUris = runCatching {
                ApiClient.json.decodeFromString<Set<String>>(p[KEY_EXTRA_SCAN_DIRS] ?: "[]")
            }.getOrDefault(emptySet()),
            preloadLocalCount = p[KEY_PRELOAD_LOCAL] ?: 5,
            clearNewOnOpen = p[KEY_CLEAR_NEW_ON_OPEN] ?: true,
            offlineCacheLimitBytes = p[KEY_OFFLINE_CACHE_LIMIT_BYTES]
                ?: (p[KEY_OFFLINE_CACHE_LIMIT]?.toLong()?.let { it * BYTES_PER_GIB } ?: 0L),
            downloadConcurrency = (p[KEY_DOWNLOAD_CONCURRENCY] ?: 2).coerceIn(1, 8),
            downloadRatePerSecond = (p[KEY_DOWNLOAD_RATE_PER_SECOND] ?: 2).coerceIn(1, 10),
            downloadMaxRetries = (p[KEY_DOWNLOAD_MAX_RETRIES] ?: DEFAULT_DOWNLOAD_MAX_RETRIES)
                .coerceIn(MIN_DOWNLOAD_MAX_RETRIES, MAX_DOWNLOAD_MAX_RETRIES),
            profiles = runCatching {
                ApiClient.json.decodeFromString<List<ServerProfile>>(p[KEY_PROFILES] ?: "[]")
            }.getOrDefault(emptyList()),
            activeProfileIndex = p[KEY_ACTIVE_PROFILE] ?: 0,
            readerModePortrait = p[KEY_MODE_PORTRAIT],
            readerModeLandscape = p[KEY_MODE_LANDSCAPE],
            readingDirectionPortrait = p[KEY_DIRECTION_PORTRAIT],
            readingDirectionLandscape = p[KEY_DIRECTION_LANDSCAPE],
            readerFitModePortrait = p[KEY_FIT_MODE_PORTRAIT],
            readerFitModeLandscape = p[KEY_FIT_MODE_LANDSCAPE],
            doublePageFirstPageAlone = p[KEY_FIRST_PAGE_ALONE] ?: false,
            doublePageFirstPageAlonePortrait = p[KEY_FIRST_PAGE_ALONE_PORTRAIT],
            doublePageFirstPageAloneLandscape = p[KEY_FIRST_PAGE_ALONE_LANDSCAPE],
            captureLogcat = p[KEY_CAPTURE_LOGCAT] ?: false,
            storageRootUri = p[KEY_STORAGE_ROOT],
            onboardingCompleted = p[KEY_ONBOARDING_COMPLETED] ?: false,
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: false,
            readerPageOverlay = p[KEY_READER_PAGE_OVERLAY] ?: true,
            tapZoneConfig = p[KEY_TAP_ZONE_CONFIG],
            pageTurnAnimation = p[KEY_PAGE_TURN_ANIMATION] ?: "smooth",
            imageRegionWidthRatio = p[KEY_IMAGE_REGION_WIDTH_RATIO] ?: 1f,
            scrapeSource = p[KEY_SCRAPE_SOURCE] ?: "ehentai",
            scrapeConfidence = (p[KEY_SCRAPE_CONFIDENCE] ?: 70).coerceIn(0, 100),
            scrapeSkipTagged = p[KEY_SCRAPE_SKIP_TAGGED] ?: true,
            tagTranslationAutoUpdate = p[KEY_TAG_TRANSLATION_AUTO_UPDATE] ?: false,
        )
    }

    val schemaVersion: Flow<Int> = dataStore.data.map { it[KEY_SCHEMA_VERSION] ?: 1 }

    // ---- C6 存储根：单一权威状态流 ----

    /** storageRootState 常驻解析所需的作用域；SettingsRepository 进程级存活，随进程退出。 */
    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 下载/缓存写入侧报告的运行时存储错误；`null` 表示恢复正常（StateFlow 自动去重）。 */
    private val storageRuntimeError = MutableStateFlow<String?>(null)

    /**
     * 存储根目录的单一权威状态：`storageRootUri` 变化时重建根实例；
     * SAF 授权被回收或目录不可写时回退默认目录并标记 [StorageRootState.degraded]。
     * UI 与下载门禁都应观察此流，而不是各自解析 URI。
     */
    val storageRootState: StateFlow<StorageRootState> = combine(
        settings.map { it.storageRootUri }.distinctUntilChanged(),
        storageRuntimeError,
    ) { uri, runtimeError -> resolveStorageRoot(context, uri, runtimeError) }
        .stateIn(
            scope = storageScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            // DataStore 首读完成前的临时值（默认根）；Eagerly 收集会在其后立刻替换为真实解析结果。
            initialValue = resolveStorageRoot(context, null),
        )

    /** 由存储写入方（下载门禁 / 离线缓存）报告最近一次存储失败原因；传 `null` 清除。 */
    suspend fun reportStorageFailure(message: String?) {
        storageRuntimeError.value = message
    }

    val readerPreferences: Flow<ReaderPreferences> = settings.map { it.readerPreferences() }
    val downloadPreferences: Flow<DownloadPreferences> = settings.map { it.downloadPreferences() }
    val libraryPreferences: Flow<LibraryPreferences> = settings.map { it.libraryPreferences() }
    val appearancePreferences: Flow<AppearancePreferences> = settings.map { it.appearancePreferences() }

    init {
        DataMigration.register(2) { migrateApiKeyV2() }
        DataMigration.register(3) { migrateProfilesV3() }
    }

    suspend fun migrateIfNeeded() {
        try {
            val cur = dataStore.data.first()[KEY_SCHEMA_VERSION] ?: 1
            val next = DataMigration.runPending(context, cur)
            if (next > cur) dataStore.edit { it[KEY_SCHEMA_VERSION] = next }
            migrateApiKeyToKeystore()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun applyToRuntime() {
        migrateIfNeeded()
        apiKeyCache = secretStore.read(SECRET_API_KEY).orEmpty()
        val s = settings.first()
        val profile = s.profiles.getOrNull(s.activeProfileIndex)
        ApiClient.config.baseUrl = (profile?.url ?: s.baseUrl).trim().trimEnd('/')
        ApiClient.config.apiKey = apiKeyCache.trim()
    }

    private suspend fun migrateApiKeyV2() {
        try {
            val raw = dataStore.data.first()[KEY_KEY] ?: return
            if (raw.isEmpty() || raw.startsWith(SecurePrefs.PREFIX)) return
            val encrypted = SecurePrefs.encrypt(raw) ?: return
            dataStore.edit { it[KEY_KEY] = encrypted }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun migrateApiKeyToKeystore() {
        val prefs = dataStore.data.first()
        val profiles = runCatching {
            ApiClient.json.decodeFromString<List<ServerProfile>>(prefs[KEY_PROFILES] ?: "[]")
        }.getOrDefault(emptyList())
        val activeIndex = prefs[KEY_ACTIVE_PROFILE] ?: 0
        val old = prefs[KEY_KEY].orEmpty()
        if (old.isBlank() && profiles.none { it.apiKey.isNotBlank() }) return
        try {
            if (old.isNotBlank()) {
                val plain = SecurePrefs.decrypt(old)
                secretStore.write(SECRET_API_KEY, plain)
                apiKeyCache = plain
            }
            profiles.forEachIndexed { index, profile ->
                if (profile.apiKey.isNotBlank()) {
                    val plain = SecurePrefs.decrypt(profile.apiKey)
                    secretStore.write(profileSecretName(index), plain)
                    if (index == activeIndex && old.isBlank()) {
                        secretStore.write(SECRET_API_KEY, plain)
                        apiKeyCache = plain
                    }
                }
            }
            dataStore.edit {
                it.remove(KEY_KEY)
                if (profiles.isNotEmpty()) {
                    it[KEY_PROFILES] = ApiClient.json.encodeToString(profiles.map { profile -> profile.copy(apiKey = "") })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun migrateProfilesV3() {
        try {
            val prefs = dataStore.data.first()
            val url = prefs[KEY_URL]?.trim()?.takeIf { it.isNotBlank() } ?: return
            val name = prefs[KEY_SERVER_NAME] ?: ""
            val profile = ServerProfile(name = name, url = url, apiKey = "")
            dataStore.edit {
                it[KEY_PROFILES] = ApiClient.json.encodeToString(listOf(profile))
                it[KEY_ACTIVE_PROFILE] = 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun saveServer(url: String, apiKey: String, name: String = "") = updateActiveProfile(name, url, apiKey)

    /**
     * 更新当前活跃 profile 的名称/地址/API Key，并把三者应用为当前生效配置
     * （密钥写 [SECRET_API_KEY] 与按索引的独立记录，最后经 [applyToRuntime] 发布到 ApiClient.config）。
     */
    suspend fun updateActiveProfile(name: String, baseUrl: String, apiKey: String) {
        val normalizedKey = apiKey.trim()
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val current = settings.first()
        val index = current.activeProfileIndex.coerceIn(0, (current.profiles.size - 1).coerceAtLeast(0))
        secretStore.write(profileSecretName(index), normalizedKey)
        secretStore.write(SECRET_API_KEY, normalizedKey)
        apiKeyCache = normalizedKey
        dataStore.edit {
            it[KEY_URL] = normalizedUrl
            it.remove(KEY_KEY)
            it[KEY_SERVER_NAME] = name.trim()
            val profiles = runCatching {
                ApiClient.json.decodeFromString<List<ServerProfile>>(it[KEY_PROFILES] ?: "[]")
            }.getOrDefault(emptyList()).toMutableList()
            val p = ServerProfile(name = name.trim(), url = normalizedUrl, apiKey = "")
            if (profiles.isEmpty()) profiles.add(p) else profiles[index.coerceIn(0, profiles.lastIndex)] = p
            it[KEY_PROFILES] = ApiClient.json.encodeToString(profiles)
        }
        applyToRuntime()
    }

    /** 新增服务器 profile 并立即切换为活跃（应用为当前生效配置）。 */
    suspend fun addProfile(name: String, baseUrl: String, apiKey: String) {
        val normalizedKey = apiKey.trim()
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        var newIndex = 0
        dataStore.edit {
            val profiles = runCatching {
                ApiClient.json.decodeFromString<List<ServerProfile>>(it[KEY_PROFILES] ?: "[]")
            }.getOrDefault(emptyList()).toMutableList()
            profiles.add(ServerProfile(name = name.trim(), url = normalizedUrl, apiKey = ""))
            newIndex = profiles.lastIndex
            it[KEY_PROFILES] = ApiClient.json.encodeToString(profiles)
            it[KEY_ACTIVE_PROFILE] = newIndex
            it[KEY_URL] = normalizedUrl
            it[KEY_SERVER_NAME] = name.trim()
            it.remove(KEY_KEY)
        }
        secretStore.write(profileSecretName(newIndex), normalizedKey)
        secretStore.write(SECRET_API_KEY, normalizedKey)
        apiKeyCache = normalizedKey
        applyToRuntime()
    }

    /**
     * 删除指定 profile：至少保留 1 个（越界或仅剩 1 个时为空操作）；
     * 删除活跃项时切换到 0。按索引存储的 profile 密钥随删除整体前移，保持索引对齐。
     */
    suspend fun removeProfile(index: Int) {
        val current = settings.first()
        val oldProfiles = current.profiles
        if (index !in oldProfiles.indices || oldProfiles.size <= 1) return
        val removedActive = index == current.activeProfileIndex
        val oldActive = current.activeProfileIndex
        dataStore.edit {
            val profiles = oldProfiles.toMutableList()
            profiles.removeAt(index)
            it[KEY_PROFILES] = ApiClient.json.encodeToString(profiles)
            it[KEY_ACTIVE_PROFILE] = when {
                removedActive -> 0
                oldActive > index -> oldActive - 1
                else -> oldActive
            }
        }
        for (j in index + 1 until oldProfiles.size) {
            val key = secretStore.read(profileSecretName(j)) ?: continue
            secretStore.write(profileSecretName(j - 1), key)
        }
        secretStore.remove(profileSecretName(oldProfiles.lastIndex))
        if (removedActive) setActiveProfile(0)
    }

    /**
     * 切换活跃 profile：把该 profile 的 baseUrl/apiKey/name 应用为当前生效配置
     * （与 [saveServer] 相同的生效路径：密钥写 [SECRET_API_KEY]，经 [applyToRuntime] 发布到 ApiClient.config）。
     * profile 无独立密钥记录时沿用当前生效密钥（与历史单 profile 数据兼容）。
     */
    suspend fun setActiveProfile(index: Int) {
        val profile = settings.first().profiles.getOrNull(index) ?: return
        val storedKey = secretStore.read(profileSecretName(index))
            ?: runCatching { SecurePrefs.decrypt(profile.apiKey) }.getOrNull().orEmpty()
        val key = storedKey.ifBlank { apiKeyCache }
        secretStore.write(SECRET_API_KEY, key)
        apiKeyCache = key
        dataStore.edit {
            it[KEY_URL] = profile.url
            it[KEY_SERVER_NAME] = profile.name
            it.remove(KEY_KEY)
            it[KEY_ACTIVE_PROFILE] = index
        }
        applyToRuntime()
    }

    suspend fun setReaderMode(mode: String) { dataStore.edit { it[KEY_MODE] = mode } }
    suspend fun setReaderModeForOrientation(profile: ReaderOrientationProfile, mode: String?) {
        dataStore.edit { prefs ->
            when (profile) {
                ReaderOrientationProfile.GLOBAL -> if (mode == null) prefs.remove(KEY_MODE) else prefs[KEY_MODE] = mode
                ReaderOrientationProfile.PORTRAIT -> if (mode == null) prefs.remove(KEY_MODE_PORTRAIT) else prefs[KEY_MODE_PORTRAIT] = mode
                ReaderOrientationProfile.LANDSCAPE -> if (mode == null) prefs.remove(KEY_MODE_LANDSCAPE) else prefs[KEY_MODE_LANDSCAPE] = mode
            }
        }
    }
    suspend fun setAutoScrollSpeed(s: String) {
        dataStore.edit { it[KEY_AUTO_SCROLL_SPEED] = normalizeAutoScrollSpeed(s) }
    }
    suspend fun setMultiPageCount(n: Int) { dataStore.edit { it[KEY_MULTI] = n.coerceIn(2, 8) } }
    suspend fun setAutoDoublePageLandscape(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_DOUBLE_PAGE_LANDSCAPE] = enabled } }
    suspend fun setPreloadOnlineCount(n: Int) { dataStore.edit { it[KEY_PRELOAD_ONLINE] = n.coerceIn(1, 20) } }
    suspend fun setReadingDirection(direction: String) { dataStore.edit { it[KEY_DIRECTION] = direction } }
    suspend fun setReadingDirectionForOrientation(profile: ReaderOrientationProfile, direction: String?) {
        dataStore.edit { prefs ->
            when (profile) {
                ReaderOrientationProfile.GLOBAL -> if (direction == null) prefs.remove(KEY_DIRECTION) else prefs[KEY_DIRECTION] = direction
                ReaderOrientationProfile.PORTRAIT -> if (direction == null) prefs.remove(KEY_DIRECTION_PORTRAIT) else prefs[KEY_DIRECTION_PORTRAIT] = direction
                ReaderOrientationProfile.LANDSCAPE -> if (direction == null) prefs.remove(KEY_DIRECTION_LANDSCAPE) else prefs[KEY_DIRECTION_LANDSCAPE] = direction
            }
        }
    }
    suspend fun setGalleryColumns(n: Int) { dataStore.edit { it[KEY_GALLERY_COLS] = n.coerceIn(2, 8) } }
    suspend fun setPreviewColumns(n: Int) { dataStore.edit { it[KEY_PREVIEW_COLS] = n.coerceIn(2, 8) } }
    suspend fun setPreviewCount(n: Int) { dataStore.edit { it[KEY_PREVIEW_COUNT] = n.coerceIn(4, 200) } }
    suspend fun setCoverPrefetchCount(n: Int) { dataStore.edit { it[KEY_COVER_COUNT] = n.coerceIn(4, 200) } }
    suspend fun setDownloadDirUri(uri: String?) { dataStore.edit { if (uri == null) it.remove(KEY_DOWNLOAD_DIR) else it[KEY_DOWNLOAD_DIR] = uri } }
    suspend fun setTheme(theme: String) { dataStore.edit { it[KEY_THEME] = theme } }
    suspend fun savePreset(preset: FilterPreset) { val current = settings.first().presets; dataStore.edit { it[KEY_PRESETS] = ApiClient.json.encodeToString(current.filter { f -> f.name != preset.name } + preset) } }
    suspend fun deletePreset(name: String) { val current = settings.first().presets; dataStore.edit { it[KEY_PRESETS] = ApiClient.json.encodeToString(current.filter { it.name != name }) } }
    suspend fun setPresets(presets: List<FilterPreset>) { dataStore.edit { it[KEY_PRESETS] = ApiClient.json.encodeToString(presets) } }
    suspend fun setDefaultPreset(name: String) { dataStore.edit { it[KEY_DEFAULT_PRESET] = name } }
    suspend fun setReaderFitMode(mode: String) { dataStore.edit { it[KEY_FIT_MODE] = mode } }
    suspend fun setReaderFitModeForOrientation(profile: ReaderOrientationProfile, mode: String?) {
        dataStore.edit { prefs ->
            when (profile) {
                ReaderOrientationProfile.GLOBAL -> if (mode == null) prefs.remove(KEY_FIT_MODE) else prefs[KEY_FIT_MODE] = mode
                ReaderOrientationProfile.PORTRAIT -> if (mode == null) prefs.remove(KEY_FIT_MODE_PORTRAIT) else prefs[KEY_FIT_MODE_PORTRAIT] = mode
                ReaderOrientationProfile.LANDSCAPE -> if (mode == null) prefs.remove(KEY_FIT_MODE_LANDSCAPE) else prefs[KEY_FIT_MODE_LANDSCAPE] = mode
            }
        }
    }
    suspend fun setFirstPageAloneForOrientation(profile: ReaderOrientationProfile, enabled: Boolean?) {
        dataStore.edit { prefs ->
            when (profile) {
                ReaderOrientationProfile.GLOBAL -> if (enabled == null) prefs.remove(KEY_FIRST_PAGE_ALONE) else prefs[KEY_FIRST_PAGE_ALONE] = enabled
                ReaderOrientationProfile.PORTRAIT -> if (enabled == null) prefs.remove(KEY_FIRST_PAGE_ALONE_PORTRAIT) else prefs[KEY_FIRST_PAGE_ALONE_PORTRAIT] = enabled
                ReaderOrientationProfile.LANDSCAPE -> if (enabled == null) prefs.remove(KEY_FIRST_PAGE_ALONE_LANDSCAPE) else prefs[KEY_FIRST_PAGE_ALONE_LANDSCAPE] = enabled
            }
        }
    }
    suspend fun setReaderBackground(bg: String) { dataStore.edit { it[KEY_READER_BG] = bg } }
    suspend fun setReaderBrightness(n: Int) { dataStore.edit { it[KEY_READER_BRIGHTNESS] = n.coerceIn(-1, 100) } }
    suspend fun setTapZonesEnabled(enabled: Boolean) { dataStore.edit { it[KEY_TAP_ZONES] = enabled } }
    suspend fun setKeepScreenOn(enabled: Boolean) { dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled } }
    suspend fun setVolumeKeysEnabled(enabled: Boolean) { dataStore.edit { it[KEY_VOLUME_KEYS] = enabled } }
    suspend fun setGalleryViewMode(mode: String) { dataStore.edit { it[KEY_GALLERY_VIEW] = mode } }
    suspend fun setGallerySortby(s: String) { dataStore.edit { it[KEY_GALLERY_SORTBY] = s } }
    suspend fun setGalleryOrder(s: String) { dataStore.edit { it[KEY_GALLERY_ORDER] = s } }
    suspend fun setTagColor(namespace: String, hex: String) { val ns = namespace.lowercase(); val updated = settings.first().tagColors + (ns to hex.uppercase()); dataStore.edit { it[KEY_TAG_COLORS] = ApiClient.json.encodeToString(updated) } }
    suspend fun resetTagColor(namespace: String) { val ns = namespace.lowercase(); val updated = settings.first().tagColors - ns; dataStore.edit { it[KEY_TAG_COLORS] = ApiClient.json.encodeToString(updated) } }
    suspend fun setGalleryTitle(title: String) { dataStore.edit { it[KEY_GALLERY_TITLE] = title } }
    suspend fun setGalleryTitleMode(mode: String) { dataStore.edit { it[KEY_GALLERY_TITLE_MODE] = mode } }
    suspend fun setShowArchiveCount(show: Boolean) { dataStore.edit { it[KEY_SHOW_COUNT] = show } }
    suspend fun setShowFloatingButton(show: Boolean) { dataStore.edit { it[KEY_SHOW_FAB] = show } }
    suspend fun setFloatingButtonColor(hex: String) { dataStore.edit { it[KEY_FAB_COLOR] = hex.uppercase() } }
    suspend fun setBottomBarColor(hex: String) { dataStore.edit { it[KEY_BOTTOM_BAR_COLOR] = hex.uppercase() } }
    suspend fun setBottomBarActiveColor(hex: String) { dataStore.edit { it[KEY_BOTTOM_BAR_ACTIVE_COLOR] = hex.uppercase() } }
    suspend fun setLastReadArcId(arcid: String) { dataStore.edit { it[KEY_LAST_READ_ARCID] = arcid } }
    suspend fun setAuthEnabled(enabled: Boolean) { dataStore.edit { it[KEY_AUTH_ENABLED] = enabled } }
    suspend fun setBiometricEnabled(enabled: Boolean) { dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled } }
    suspend fun setScreenshotProtectionEnabled(enabled: Boolean) { dataStore.edit { it[KEY_SCREENSHOT_PROTECTION] = enabled } }

    suspend fun exportNonSensitiveConfig(): String {
        return ConfigTransferCodec.export(filterSensitiveFields(settings.first().nonSensitiveConfig()).value)
    }

    suspend fun previewNonSensitiveConfig(payload: String): ConfigImportPreview {
        val imported = ConfigTransferCodec.import(payload)
        val current = settings.first().nonSensitiveConfig()
        return ConfigImportPreview(
            payload = payload,
            changedFields = imported.config.entries
                .filter { (key, value) -> current[key] != value }
                .map { it.key }
                .sorted(),
        )
    }

    suspend fun importNonSensitiveConfig(payload: String) {
        val config = ConfigTransferCodec.import(payload).config
        val current = settings.first()
        dataStore.edit { prefs ->
            prefs[KEY_MODE] = config.stringValueOr("readerMode", current.readerMode).validated(READER_MODES, current.readerMode)
            prefs[KEY_DIRECTION] = config.stringValueOr("readingDirection", current.readingDirection).validated(READING_DIRECTIONS, current.readingDirection)
            prefs[KEY_FIT_MODE] = config.stringValueOr("readerFitMode", current.readerFitMode).validated(FIT_MODES, current.readerFitMode)
            prefs[KEY_MULTI] = config.intValueOr("multiPageCount", current.multiPageCount).coerceIn(2, 8)
            prefs[KEY_AUTO_DOUBLE_PAGE_LANDSCAPE] = config.booleanValueOr("autoDoublePageLandscape", current.autoDoublePageLandscape)
            prefs[KEY_FIRST_PAGE_ALONE] = config.booleanValueOr("doublePageFirstPageAlone", current.doublePageFirstPageAlone)
            prefs[KEY_PRELOAD_ONLINE] = config.intValueOr("preloadOnlineCount", current.preloadOnlineCount).coerceIn(1, 20)
            prefs[KEY_PRELOAD_LOCAL] = config.intValueOr("preloadLocalCount", current.preloadLocalCount).coerceIn(1, 50)
            prefs[KEY_TAP_ZONES] = config.booleanValueOr("tapZonesEnabled", current.tapZonesEnabled)
            prefs[KEY_KEEP_SCREEN_ON] = config.booleanValueOr("keepScreenOn", current.keepScreenOn)
            prefs[KEY_VOLUME_KEYS] = config.booleanValueOr("volumeKeysEnabled", current.volumeKeysEnabled)
            prefs[KEY_DOWNLOAD_CONCURRENCY] = config.intValueOr("downloadConcurrency", current.downloadConcurrency).coerceIn(1, 8)
            prefs[KEY_DOWNLOAD_RATE_PER_SECOND] = config.intValueOr("downloadRatePerSecond", current.downloadRatePerSecond).coerceIn(1, 10)
            prefs[KEY_DOWNLOAD_MAX_RETRIES] = config.intValueOr("downloadMaxRetries", current.downloadMaxRetries)
                .coerceIn(MIN_DOWNLOAD_MAX_RETRIES, MAX_DOWNLOAD_MAX_RETRIES)
            prefs[KEY_OFFLINE_CACHE_LIMIT_BYTES] = config.longValueOr("offlineCacheLimitBytes", current.offlineCacheLimitBytes).coerceAtLeast(0L)
            prefs[KEY_GALLERY_COLS] = config.intValueOr("galleryColumns", current.galleryColumns).coerceIn(2, 8)
            prefs[KEY_GALLERY_VIEW] = config.stringValueOr("galleryViewMode", current.galleryViewMode).validated(setOf("grid", "list", "compact"), current.galleryViewMode)
            prefs[KEY_GALLERY_SORTBY] = config.stringValueOr("gallerySortby", current.gallerySortby)
            prefs[KEY_GALLERY_ORDER] = config.stringValueOr("galleryOrder", current.galleryOrder).validated(setOf("asc", "desc"), current.galleryOrder)
            prefs[KEY_THEME] = config.stringValueOr("theme", current.theme)
            prefs[KEY_SHOW_COUNT] = config.booleanValueOr("showArchiveCount", current.showArchiveCount)
            prefs[KEY_SHOW_FAB] = config.booleanValueOr("showFloatingButton", current.showFloatingButton)
            prefs[KEY_BIOMETRIC_ENABLED] = config.booleanValueOr("biometricEnabled", current.biometricEnabled)
            prefs[KEY_BLUR_IN_RECENTS] = config.booleanValueOr("maskRecentTasks", current.blurInRecents)
            prefs[KEY_SCREENSHOT_PROTECTION] = config.booleanValueOr("screenshotProtectionEnabled", current.screenshotProtectionEnabled)
            prefs[KEY_DYNAMIC_COLOR] = config.booleanValueOr("dynamicColor", current.dynamicColor)
            prefs[KEY_READER_PAGE_OVERLAY] = config.booleanValueOr("readerPageOverlay", current.readerPageOverlay)
            prefs[KEY_PAGE_TURN_ANIMATION] = config.stringValueOr("pageTurnAnimation", current.pageTurnAnimation)
                .validated(PAGE_TURN_ANIMATIONS, current.pageTurnAnimation)
            prefs[KEY_IMAGE_REGION_WIDTH_RATIO] = config.floatValueOr("imageRegionWidthRatio", current.imageRegionWidthRatio)
                .coerceIn(0.5f, 1f)
            val importedTapZoneConfig = config.stringValueOr("tapZoneConfig", current.tapZoneConfig ?: "")
            if (importedTapZoneConfig.isBlank()) prefs.remove(KEY_TAP_ZONE_CONFIG) else prefs[KEY_TAP_ZONE_CONFIG] = importedTapZoneConfig
            // 存储根目录：导入值非空才覆盖，避免把「已配置」意外清成空（进而触发存储门禁拒绝）。
            val importedStorageRoot = config.stringValueOr("storageRootUri", current.storageRootUri ?: "")
            if (importedStorageRoot.isNotBlank()) prefs[KEY_STORAGE_ROOT] = importedStorageRoot
            prefs[KEY_TAG_TRANSLATION_AUTO_UPDATE] =
                config.booleanValueOr("tagTranslationAutoUpdate", current.tagTranslationAutoUpdate)
        }
    }

    private fun kotlinx.serialization.json.JsonObject.stringValueOr(name: String, fallback: String): String =
        (get(name) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: fallback

    private fun kotlinx.serialization.json.JsonObject.booleanValueOr(name: String, fallback: Boolean): Boolean =
        (get(name) as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: fallback

    private fun kotlinx.serialization.json.JsonObject.intValueOr(name: String, fallback: Int): Int =
        (get(name) as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: fallback

    private fun kotlinx.serialization.json.JsonObject.longValueOr(name: String, fallback: Long): Long =
        (get(name) as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: fallback

    private fun kotlinx.serialization.json.JsonObject.floatValueOr(name: String, fallback: Float): Float =
        (get(name) as? kotlinx.serialization.json.JsonPrimitive)?.floatOrNull ?: fallback

    private fun profileSecretName(index: Int): String = "lanraragi/profile/$index/api_key"

    /**
     * 「在相册中隐藏下载的图片」。
     *
     * 语义（已确认）：只在下载目录里放/删 `.nomedia`，让**系统相册 / MediaStore** 不去索引这些图片；
     * APP 自己走 SAF 目录树扫描，本来就不读 `.nomedia`，因此**本地书架不会被过滤**。
     * `.nomedia` 只对用户可见的 SAF 目录有意义——默认存储根是应用私有目录，系统相册本来就看不到，
     * 此时该开关是安全的空操作。
     *
     * 注意：不要把它改成「扫描时跳过含 .nomedia 的目录」，那会变成另一个功能（本地库会少掉档案）。
     */
    suspend fun setHideInGallery(enabled: Boolean) {
        dataStore.edit { it[KEY_HIDE_IN_GALLERY] = enabled }
        val downloadUri = settings.first().downloadDirUri
        if (downloadUri != null) updateNoMediaFile(downloadUri, enabled)
    }

    /** SAF 的 findFile/createFile/delete 都是 ContentProvider 往返，必须离开主线程。 */
    private suspend fun updateNoMediaFile(uriString: String, hide: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(uriString))
            if (root != null && root.isDirectory) {
                val noMedia = root.findFile(".nomedia")
                if (hide && noMedia == null) root.createFile("application/octet-stream", ".nomedia")
                else if (!hide && noMedia != null) noMedia.delete()
            }
        }
    }

    suspend fun setBlurInRecents(enabled: Boolean) { dataStore.edit { it[KEY_BLUR_IN_RECENTS] = enabled } }
    suspend fun setCaptureLogcat(enabled: Boolean) { dataStore.edit { it[KEY_CAPTURE_LOGCAT] = enabled } }
    /** 保存图库存储根目录（SAF tree URI）；传 null 恢复默认目录。 */
    suspend fun saveStorageRoot(uri: String?) { dataStore.edit { if (uri == null) it.remove(KEY_STORAGE_ROOT) else it[KEY_STORAGE_ROOT] = uri } }
    suspend fun markOnboardingCompleted() { dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true } }
    suspend fun saveDynamicColor(enabled: Boolean) { dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled } }
    suspend fun saveReaderPageOverlay(enabled: Boolean) { dataStore.edit { it[KEY_READER_PAGE_OVERLAY] = enabled } }
    /** 保存阅读器点击分区布局（TapZoneGrid 序列化 JSON）；传 null 恢复默认三列布局。 */
    suspend fun saveTapZoneConfig(json: String?) { dataStore.edit { if (json == null) it.remove(KEY_TAP_ZONE_CONFIG) else it[KEY_TAP_ZONE_CONFIG] = json } }
    /** 保存翻页动画；取值限 smooth/curl/fade，否则忽略。 */
    suspend fun savePageTurnAnimation(value: String) {
        if (value !in PAGE_TURN_ANIMATIONS) return
        dataStore.edit { it[KEY_PAGE_TURN_ANIMATION] = value }
    }
    /** 保存图片显示区域宽度占比，coerceIn 0.5f..1f。 */
    suspend fun saveImageRegionWidthRatio(value: Float) { dataStore.edit { it[KEY_IMAGE_REGION_WIDTH_RATIO] = value.coerceIn(0.5f, 1f) } }
    /** 保存阅读器背景色；取值限 black/dark/gray/white/auto，否则忽略。 */
    suspend fun saveReaderBackground(value: String) {
        if (value !in READER_BACKGROUNDS) return
        dataStore.edit { it[KEY_READER_BG] = value }
    }
    suspend fun addExtraScanDirUri(uri: String) { val current = settings.first().extraScanDirUris; dataStore.edit { it[KEY_EXTRA_SCAN_DIRS] = ApiClient.json.encodeToString(current + uri) } }
    suspend fun removeExtraScanDirUri(uri: String) { val current = settings.first().extraScanDirUris; dataStore.edit { it[KEY_EXTRA_SCAN_DIRS] = ApiClient.json.encodeToString(current - uri) } }
    suspend fun setPreloadLocalCount(n: Int) { dataStore.edit { it[KEY_PRELOAD_LOCAL] = n.coerceIn(1, 50) } }
    suspend fun setClearNewOnOpen(enabled: Boolean) { dataStore.edit { it[KEY_CLEAR_NEW_ON_OPEN] = enabled } }
    suspend fun setOfflineCacheLimitBytes(bytes: Long) {
        require(bytes >= 0L) { "cache limit must not be negative" }
        dataStore.edit {
            it[KEY_OFFLINE_CACHE_LIMIT_BYTES] = bytes
            it.remove(KEY_OFFLINE_CACHE_LIMIT)
        }
    }
    suspend fun setOfflineCacheLimitGb(n: Int) = setOfflineCacheLimitBytes(n.coerceAtLeast(0).toLong() * BYTES_PER_GIB)
    suspend fun setDownloadConcurrency(n: Int) { dataStore.edit { it[KEY_DOWNLOAD_CONCURRENCY] = n.coerceIn(1, 8) } }
    /** C1 保存每秒任务启动速率上限，coerceIn 1..10。 */
    suspend fun saveDownloadRatePerSecond(value: Int) { dataStore.edit { it[KEY_DOWNLOAD_RATE_PER_SECOND] = value.coerceIn(1, 10) } }

    /** C3 保存单个任务的自动重试上限，coerceIn 1..10（默认 5）。 */
    suspend fun saveDownloadMaxRetries(value: Int) {
        dataStore.edit { it[KEY_DOWNLOAD_MAX_RETRIES] = value.coerceIn(MIN_DOWNLOAD_MAX_RETRIES, MAX_DOWNLOAD_MAX_RETRIES) }
    }

    /** 保存元数据刮削默认来源；取值限 ehentai/nhentai/server_plugin，否则忽略。 */
    suspend fun saveScrapeSource(value: String) {
        if (value !in SCRAPE_SOURCES) return
        dataStore.edit { it[KEY_SCRAPE_SOURCE] = value }
    }

    /** 保存元数据刮削匹配置信度阈值，coerceIn 0..100。 */
    suspend fun saveScrapeConfidence(value: Int) { dataStore.edit { it[KEY_SCRAPE_CONFIDENCE] = value.coerceIn(0, 100) } }

    /** 保存元数据刮削时是否跳过已有标签的档案。 */
    suspend fun saveScrapeSkipTagged(enabled: Boolean) { dataStore.edit { it[KEY_SCRAPE_SKIP_TAGGED] = enabled } }

    /** 保存 D3 翻译词库自动更新开关（默认关闭；开启后超过 7 天未更新的词库在后台静默刷新）。 */
    suspend fun saveTagTranslationAutoUpdate(enabled: Boolean) { dataStore.edit { it[KEY_TAG_TRANSLATION_AUTO_UPDATE] = enabled } }

    private fun String.validated(allowed: Set<String>, fallback: String): String = takeIf(allowed::contains) ?: fallback

}

private const val BYTES_PER_GIB = 1024L * 1024L * 1024L

fun Settings.readerPreferences(profile: ReaderOrientationProfile = ReaderOrientationProfile.GLOBAL): ReaderPreferences {
    val mode = when (profile) {
        ReaderOrientationProfile.GLOBAL -> readerMode
        ReaderOrientationProfile.PORTRAIT -> readerModePortrait ?: readerMode
        ReaderOrientationProfile.LANDSCAPE -> readerModeLandscape ?: readerMode
    }
    val direction = when (profile) {
        ReaderOrientationProfile.GLOBAL -> readingDirection
        ReaderOrientationProfile.PORTRAIT -> readingDirectionPortrait ?: readingDirection
        ReaderOrientationProfile.LANDSCAPE -> readingDirectionLandscape ?: readingDirection
    }
    val fit = when (profile) {
        ReaderOrientationProfile.GLOBAL -> readerFitMode
        ReaderOrientationProfile.PORTRAIT -> readerFitModePortrait ?: readerFitMode
        ReaderOrientationProfile.LANDSCAPE -> readerFitModeLandscape ?: readerFitMode
    }
    val firstAlone = when (profile) {
        ReaderOrientationProfile.GLOBAL -> doublePageFirstPageAlone
        ReaderOrientationProfile.PORTRAIT -> doublePageFirstPageAlonePortrait ?: doublePageFirstPageAlone
        ReaderOrientationProfile.LANDSCAPE -> doublePageFirstPageAloneLandscape ?: doublePageFirstPageAlone
    }
    return ReaderPreferences(
        mode,
        multiPageCount,
        autoDoublePageLandscape,
        preloadOnlineCount,
        preloadLocalCount,
        direction,
        fit,
        readerBackground,
        readerBrightness,
        tapZonesEnabled,
        keepScreenOn,
        volumeKeysEnabled,
        autoScrollSpeed,
        firstAlone,
    )
}

fun Settings.downloadPreferences() = DownloadPreferences(downloadDirUri, downloadConcurrency, offlineCacheLimitBytes)

fun Settings.libraryPreferences() = LibraryPreferences(
    galleryColumns, galleryViewMode, gallerySortby, galleryOrder, presets, defaultPreset, extraScanDirUris,
)

fun Settings.appearancePreferences() = AppearancePreferences(
    theme, tagColors, galleryTitle, galleryTitleMode, showArchiveCount, showFloatingButton,
    floatingButtonColor, bottomBarColor, bottomBarActiveColor,
)

private fun Settings.nonSensitiveConfig() = buildJsonObject {
    put("readerMode", readerMode)
    put("readingDirection", readingDirection)
    put("readerFitMode", readerFitMode)
    put("multiPageCount", multiPageCount)
    put("autoDoublePageLandscape", autoDoublePageLandscape)
    put("doublePageFirstPageAlone", doublePageFirstPageAlone)
    put("preloadOnlineCount", preloadOnlineCount)
    put("preloadLocalCount", preloadLocalCount)
    put("tapZonesEnabled", tapZonesEnabled)
    put("keepScreenOn", keepScreenOn)
    put("volumeKeysEnabled", volumeKeysEnabled)
    put("downloadConcurrency", downloadConcurrency)
    put("downloadRatePerSecond", downloadRatePerSecond)
    put("downloadMaxRetries", downloadMaxRetries)
    put("offlineCacheLimitBytes", offlineCacheLimitBytes)
    put("galleryColumns", galleryColumns)
    put("galleryViewMode", galleryViewMode)
    put("gallerySortby", gallerySortby)
    put("galleryOrder", galleryOrder)
    put("theme", theme)
    put("showArchiveCount", showArchiveCount)
    put("showFloatingButton", showFloatingButton)
    put("biometricEnabled", biometricEnabled)
    put("maskRecentTasks", blurInRecents)
    put("screenshotProtectionEnabled", screenshotProtectionEnabled)
    put("dynamicColor", dynamicColor)
    put("readerPageOverlay", readerPageOverlay)
    put("pageTurnAnimation", pageTurnAnimation)
    put("imageRegionWidthRatio", imageRegionWidthRatio)
    put("tapZoneConfig", tapZoneConfig)
    // C6 存储根目录 / D3 词库自动更新：同属「只配置一次」的设置，导出必须带上，
    // 否则「还原备份」后存储门禁与词库更新策略会退回默认值。
    storageRootUri?.let { put("storageRootUri", it) }
    put("tagTranslationAutoUpdate", tagTranslationAutoUpdate)
}
