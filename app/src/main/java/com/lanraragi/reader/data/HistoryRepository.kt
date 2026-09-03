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

/** 打开档案的历史记录。 */
@Serializable
data class HistoryEntry(
    val arcid: String,
    val title: String,
    val timestamp: Long,
)

/** 打开档案历史：每次打开档案记录一条（时间戳），持久化到 filesDir/history.json。 */
class HistoryRepository(private val context: Context) {

    private val file = File(context.filesDir, "history.json")

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    init {
        load()
    }

    fun load() {
        runCatching {
            if (file.exists()) {
                _entries.value = ApiClient.json.decodeFromString(file.readText())
            }
        }
    }

    suspend fun record(arcid: String, title: String) {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        // 一天内打开同一档案：合并为一条，更新时间戳为最后一次打开时间。
        val todayEntry = _entries.value.firstOrNull { it.arcid == arcid && it.timestamp >= todayStart }
        _entries.value = if (todayEntry != null) {
            _entries.value.map { if (it === todayEntry) HistoryEntry(arcid, title, now) else it }
        } else {
            (listOf(HistoryEntry(arcid, title, now)) + _entries.value).take(500)
        }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        runCatching { file.delete() }
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

    init {
        load()
    }

    fun load() {
        runCatching {
            if (file.exists()) {
                _usage.value = ApiClient.json.decodeFromString(file.readText())
            }
        }
    }

    fun addUsage(startTime: Long) {
        val day = LocalDate.now().toString()
        val delta = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
        _usage.value = _usage.value + (day to (_usage.value[day] ?: 0L) + delta)
        persist()
    }

    fun clear() {
        _usage.value = emptyMap()
        runCatching { file.delete() }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ApiClient.json.encodeToString(_usage.value))
        }
    }
}

/** 每日打卡：记录打卡日期（yyyy-MM-dd），持久化到 filesDir/checkin.json。 */
class CheckinRepository(private val context: Context) {

    private val file = File(context.filesDir, "checkin.json")

    private val _dates = MutableStateFlow<List<String>>(emptyList())
    val dates = _dates.asStateFlow()

    init {
        load()
    }

    fun load() {
        runCatching {
            if (file.exists()) {
                _dates.value = ApiClient.json.decodeFromString(file.readText())
            }
        }
    }

    fun isCheckedToday(): Boolean = LocalDate.now().toString() in _dates.value

    suspend fun checkin() {
        val today = LocalDate.now().toString()
        if (today !in _dates.value) {
            _dates.value = _dates.value + today
            persist()
        }
    }

    fun clear() {
        _dates.value = emptyList()
        runCatching { file.delete() }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ApiClient.json.encodeToString(_dates.value))
        }
    }
}
