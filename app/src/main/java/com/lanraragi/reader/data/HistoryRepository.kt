package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.db.AppDataBootstrapReport
import com.lanraragi.reader.data.db.AppDataBootstrapper
import com.lanraragi.reader.data.history.HistoryBootstrapper
import com.lanraragi.reader.data.history.HistoryStore
import com.lanraragi.reader.data.history.LegacyHistoryStore
import com.lanraragi.reader.data.history.RoomHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepository(
    context: Context,
    database: ReaderDatabase? = null,
    scope: CoroutineScope? = null,
    appDataBootstrap: Deferred<AppDataBootstrapReport>? = null,
) {

    private val legacyStore = LegacyHistoryStore(context)
    private val runtimeScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()
    private val activeStore = MutableStateFlow<HistoryStore>(legacyStore)
    private val roomStore: RoomHistoryStore?
    private val bootstrapJob: Job?

    /** Stable public flow; it switches from the legacy snapshot to Room after bootstrap commits. */
    val entries = activeStore
        .flatMapLatest { it.entries }
        .stateIn(runtimeScope, SharingStarted.Eagerly, legacyStore.entries.value)

    init {
        if (database == null) {
            roomStore = null
            bootstrapJob = null
        } else {
            val room = RoomHistoryStore(database, legacyStore, runtimeScope)
            roomStore = room
            val bootstrapper = HistoryBootstrapper(context, database)
            bootstrapJob = runtimeScope.launch {
                val roomUsable = if (appDataBootstrap != null) {
                    try {
                        appDataBootstrap.await()
                            .source(AppDataBootstrapper.HISTORY_KEY)
                            .roomUsable
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    bootstrapper.run().error == null
                }
                if (roomUsable) {
                    mutationMutex.withLock { activeStore.value = room }
                }
            }
        }
    }

    /** Kept for source compatibility; stores load their snapshot during construction. */
    fun load() = Unit

    private suspend fun awaitBootstrap() {
        bootstrapJob?.join()
    }

    suspend fun record(arcid: String, title: String, page: Int = 0, pageCount: Int = 0) {
        awaitBootstrap()
        mutationMutex.withLock { activeStore.value.record(arcid, title, page, pageCount) }
    }

    /** 只更新阅读位置，不改变时间戳。 */
    suspend fun recordProgress(arcid: String, page: Int, pageCount: Int = 0, title: String? = null) {
        awaitBootstrap()
        mutationMutex.withLock { activeStore.value.recordProgress(arcid, page, pageCount, title) }
    }

    /** UI callers historically invoke clear synchronously; serialize it in the repository scope. */
    fun clear() {
        runtimeScope.launch {
            awaitBootstrap()
            mutationMutex.withLock { activeStore.value.clear() }
        }
    }

    suspend fun remove(arcid: String) {
        awaitBootstrap()
        mutationMutex.withLock { activeStore.value.remove(arcid) }
    }

    /** 最近阅读去重列表：最多返回最近三个不同档案。 */
    fun recentDeduped(n: Int): List<HistoryEntry> = activeStore.value.recentDeduped(n)
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
