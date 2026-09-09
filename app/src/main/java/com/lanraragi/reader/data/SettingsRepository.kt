package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.FilterPreset
import com.lanraragi.reader.data.model.ServerProfile
import com.lanraragi.reader.data.security.AndroidKeystoreSecretStore
import com.lanraragi.reader.data.security.ConfigTransferCodec
import com.lanraragi.reader.data.security.filterSensitiveFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val offlineCacheLimitBytes: Long = 0L,
    /** 同时下载的最大任务数，1..8。 */
    val downloadConcurrency: Int = 2,
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
        val KEY_FEATURE_FLAGS = stringPreferencesKey("feature_flags")
        val KEY_OFFLINE_CACHE_LIMIT = intPreferencesKey("offline_cache_limit_gb")
        val KEY_OFFLINE_CACHE_LIMIT_BYTES = longPreferencesKey("offline_cache_limit_bytes")
        val KEY_DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
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
        const val SECRET_API_KEY = "lanraragi/api_key"
        private val READER_MODES = setOf("single", "multi", "continuous")
        private val READING_DIRECTIONS = setOf("ltr", "rtl", "ttb")
        private val FIT_MODES = setOf("fitWidth", "fitHeight", "fitScreen", "original")
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
            screenshotProtectionEnabled = p[KEY_SCREENSHOT_PROTECTION] ?: false,
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
            offlineCacheLimitBytes = p[KEY_OFFLINE_CACHE_LIMIT_BYTES]
                ?: (p[KEY_OFFLINE_CACHE_LIMIT]?.toLong()?.let { it * BYTES_PER_GIB } ?: 0L),
            downloadConcurrency = (p[KEY_DOWNLOAD_CONCURRENCY] ?: 2).coerceIn(1, 8),
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
        )
    }

    val schemaVersion: Flow<Int> = dataStore.data.map { it[KEY_SCHEMA_VERSION] ?: 1 }

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

    suspend fun saveServer(url: String, apiKey: String, name: String = "") {
        val normalizedKey = apiKey.trim()
        secretStore.write(SECRET_API_KEY, normalizedKey)
        apiKeyCache = normalizedKey
        dataStore.edit {
            it[KEY_URL] = normalizeBaseUrl(url)
            it.remove(KEY_KEY)
            it[KEY_SERVER_NAME] = name.trim()
            val profiles = runCatching {
                ApiClient.json.decodeFromString<List<ServerProfile>>(it[KEY_PROFILES] ?: "[]")
            }.getOrDefault(emptyList()).toMutableList()
            val p = ServerProfile(name = name.trim(), url = normalizeBaseUrl(url), apiKey = "")
            if (profiles.isNotEmpty()) profiles[0] = p else profiles.add(p)
            it[KEY_PROFILES] = ApiClient.json.encodeToString(profiles)
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

    private fun profileSecretName(index: Int): String = "lanraragi/profile/$index/api_key"

    suspend fun setHideInGallery(enabled: Boolean) {
        dataStore.edit { it[KEY_HIDE_IN_GALLERY] = enabled }
        val downloadUri = settings.first().downloadDirUri
        if (downloadUri != null) updateNoMediaFile(downloadUri, enabled)
    }

    private fun updateNoMediaFile(uriString: String, hide: Boolean) {
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
    suspend fun addExtraScanDirUri(uri: String) { val current = settings.first().extraScanDirUris; dataStore.edit { it[KEY_EXTRA_SCAN_DIRS] = ApiClient.json.encodeToString(current + uri) } }
    suspend fun removeExtraScanDirUri(uri: String) { val current = settings.first().extraScanDirUris; dataStore.edit { it[KEY_EXTRA_SCAN_DIRS] = ApiClient.json.encodeToString(current - uri) } }
    suspend fun setPreloadLocalCount(n: Int) { dataStore.edit { it[KEY_PRELOAD_LOCAL] = n.coerceIn(1, 50) } }
    suspend fun setClearNewOnOpen(enabled: Boolean) { dataStore.edit { it[KEY_CLEAR_NEW_ON_OPEN] = enabled } }
    suspend fun setFeatureFlag(key: String, enabled: Boolean) { val updated = settings.first().featureFlags + (key to enabled); dataStore.edit { it[KEY_FEATURE_FLAGS] = ApiClient.json.encodeToString(updated) } }
    suspend fun setOfflineCacheLimitBytes(bytes: Long) {
        require(bytes >= 0L) { "cache limit must not be negative" }
        dataStore.edit {
            it[KEY_OFFLINE_CACHE_LIMIT_BYTES] = bytes
            it.remove(KEY_OFFLINE_CACHE_LIMIT)
        }
    }
    suspend fun setOfflineCacheLimitGb(n: Int) = setOfflineCacheLimitBytes(n.coerceAtLeast(0).toLong() * BYTES_PER_GIB)
    suspend fun setDownloadConcurrency(n: Int) { dataStore.edit { it[KEY_DOWNLOAD_CONCURRENCY] = n.coerceIn(1, 8) } }

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
}
