package com.lanraragi.reader.data.history

import android.content.Context
import com.lanraragi.reader.data.HistoryEntry
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.writeTextAtomically
import com.lanraragi.reader.data.readTextAtomically
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Legacy JSON adapter, retained as import input and a rollback/shadow mirror. */
class LegacyHistoryStore(
    context: Context,
    private val file: File = File(context.filesDir, "history.json"),
) : HistoryStore {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<HistoryEntry>>(loadFromDisk())
    override val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    override suspend fun record(arcid: String, title: String, page: Int, pageCount: Int) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val safePage = page.coerceAtLeast(0)
            val safeCount = pageCount.coerceAtLeast(0)
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val existing = _entries.value.firstOrNull { it.arcid == arcid && it.timestamp >= todayStart }
            _entries.value = if (existing == null) {
                listOf(HistoryEntry(arcid, title, now, safePage, safeCount)) + _entries.value
            } else {
                _entries.value.map {
                    if (it.arcid == arcid && it.timestamp == existing.timestamp) {
                        it.copy(title = title, timestamp = now, page = safePage,
                            pageCount = if (safeCount > 0) safeCount else it.pageCount)
                    } else it
                }
            }.sortedByDescending { it.timestamp }
            persistLocked()
        }
    }

    override suspend fun recordProgress(arcid: String, page: Int, pageCount: Int, title: String?) {
        mutex.withLock {
            val current = _entries.value.firstOrNull { it.arcid == arcid }
            if (current == null) {
                recordUnlocked(arcid, title ?: arcid, page, pageCount)
            } else {
                _entries.value = _entries.value.map {
                    if (it.arcid == arcid) it.copy(
                        page = page.coerceAtLeast(0),
                        pageCount = if (pageCount > 0) pageCount else it.pageCount,
                        title = title?.takeIf(String::isNotBlank) ?: it.title,
                    ) else it
                }
            }
            persistLocked()
        }
    }

    override suspend fun remove(arcid: String) = mutex.withLock {
        _entries.value = _entries.value.filterNot { it.arcid == arcid }
        persistLocked()
    }

    override suspend fun clear() {
        mutex.withLock {
            _entries.value = emptyList()
            persistLocked()
        }
    }

    override fun recentDeduped(n: Int): List<HistoryEntry> = dedupe(_entries.value, n)

    private fun recordUnlocked(arcid: String, title: String, page: Int, pageCount: Int) {
        _entries.value = (listOf(HistoryEntry(arcid, title, System.currentTimeMillis(),
            page.coerceAtLeast(0), pageCount.coerceAtLeast(0))) + _entries.value)
            .sortedByDescending { it.timestamp }
    }

    private fun persistLocked() {
        runCatching {
            file.writeTextAtomically(ApiClient.json.encodeToString(_entries.value))
        }
    }

    private fun loadFromDisk(): List<HistoryEntry> = runCatching {
        if (file.exists()) ApiClient.json.decodeFromString<List<HistoryEntry>>(file.readTextAtomically())
        else emptyList()
    }.getOrDefault(emptyList()).sortedByDescending { it.timestamp }

    companion object {
        fun dedupe(entries: List<HistoryEntry>, n: Int): List<HistoryEntry> {
            val seen = mutableSetOf<String>()
            return entries.sortedByDescending { it.timestamp }
                .filter { seen.add(it.arcid) }
                .take(n.coerceAtLeast(0).coerceAtMost(3))
        }
    }
}
