package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate

/** 打开档案的历史记录。page 为 0 起始页，pageCount 用于展示恢复进度。 */
@Serializable
data class HistoryEntry(
    val arcid: String,
    val title: String,
    val timestamp: Long,
    val page: Int = 0,
    val pageCount: Int = 0,
)

/** 打开档案历史：记录最近一次打开时间与阅读位置，持久化到 filesDir/history.json。 */
class HistoryRepository(private val context: Context) {

    private val file = File(context.filesDir, "history.json")

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    init { load() }

    fun load() {
        runCatching {
            if (file.exists()) _entries.value = ApiClient.json.decodeFromString(file.readText())
        }
    }

    suspend fun record(arcid: String, title: String, page: Int = 0, pageCount: Int = 0) {
        val now = System.currentTimeMillis()
        val safePage = page.coerceAtLeast(0)
        val safeCount = pageCount.coerceAtLeast(0)
        val todayStart = LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val todayEntry = _entries.value.firstOrNull { it.arcid == arcid && it.timestamp >= todayStart }
        _entries.value = if (todayEntry != null) {
            _entries.value.map {
                if (it === todayEntry) {
                    HistoryEntry(arcid, title, now, safePage, if (safeCount > 0) safeCount else it.pageCount)
                } else it
            }
        } else {
            (listOf(HistoryEntry(arcid, title, now, safePage, safeCount)) + _entries.value)
        }
        trimToRecentThree()
        persist()
    }

    /** 只更新阅读位置，不改变时间戳。 */
    suspend fun recordProgress(arcid: String, page: Int, pageCount: Int = 0, title: String? = null) {
        val safePage = page.coerceAtLeast(0)
        val safeCount = pageCount.coerceAtLeast(0)
        val current = _entries.value.firstOrNull { it.arcid == arcid }
        if (current == null) {
            record(arcid, title ?: arcid, safePage, safeCount)
            return
        }
        _entries.value = _entries.value.map { entry ->
            if (entry.arcid == arcid) {
                entry.copy(
                    page = safePage,
                    pageCount = if (safeCount > 0) safeCount else entry.pageCount,
                    title = title?.takeIf { it.isNotBlank() } ?: entry.title,
                )
            } else entry
        }
        trimToRecentThree()
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        runCatching { file.delete() }
    }

    suspend fun remove(arcid: String) {
        _entries.value = _entries.value.filter { it.arcid != arcid }
        persist()
    }

    /** 最近阅读去重列表：最多返回最近三个不同档案。 */
    fun recentDeduped(n: Int): List<HistoryEntry> {
        val seen = mutableSetOf<String>()
        return _entries.value
            .sortedByDescending { it.timestamp }
            .filter { seen.add(it.arcid) }
            .take(minOf(n.coerceAtLeast(0), 3))
    }

    private fun trimToRecentThree() {
        val seen = mutableSetOf<String>()
        _entries.value = _entries.value
            .sortedByDescending { it.timestamp }
            .filter { seen.add(it.arcid) }
            .take(3)
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ApiClient.json.encodeToString(_entries.value))
        }
    }
}

/** 每日使用时长：记录每天使用 App 的时长（毫秒），持久化到 filesDir/usage.json。 */
class UsageRepository(private val context: Context) {
    private val file = File(context.filesDir, "usage.json")
    private val _usage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usage = _usage.asStateFlow()
    init { load() }
    fun load() { runCatching { if (file.exists()) _usage.value = ApiClient.json.decodeFromString(file.readText()) } }
    fun addUsage(startTime: Long) {
        val day = LocalDate.now().toString()
        val delta = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
        _usage.value = _usage.value + (day to (_usage.value[day] ?: 0L) + delta)
        persist()
    }
    fun clear() { _usage.value = emptyMap(); runCatching { file.delete() } }
    private fun persist() { runCatching { file.parentFile?.mkdirs(); file.writeText(ApiClient.json.encodeToString(_usage.value)) } }
}

/** 每日打卡：记录打卡日期（yyyy-MM-dd），持久化到 filesDir/checkin.json。 */
class CheckinRepository(private val context: Context) {
    private val file = File(context.filesDir, "checkin.json")
    private val _dates = MutableStateFlow<List<String>>(emptyList())
    val dates = _dates.asStateFlow()
    init { load() }
    fun load() { runCatching { if (file.exists()) _dates.value = ApiClient.json.decodeFromString(file.readText()) } }
    fun isCheckedToday(): Boolean = LocalDate.now().toString() in _dates.value
    suspend fun checkin() {
        val today = LocalDate.now().toString()
        if (today !in _dates.value) { _dates.value = _dates.value + today; persist() }
    }
    fun clear() { _dates.value = emptyList(); runCatching { file.delete() } }
    private fun persist() { runCatching { file.parentFile?.mkdirs(); file.writeText(ApiClient.json.encodeToString(_dates.value)) } }
}