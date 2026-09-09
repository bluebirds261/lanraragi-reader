package com.lanraragi.reader.data.history

import com.lanraragi.reader.data.HistoryEntry
import kotlinx.coroutines.flow.StateFlow

/** Durable history boundary. Pages are always zero based. */
interface HistoryStore {
    val entries: StateFlow<List<HistoryEntry>>

    suspend fun record(arcid: String, title: String, page: Int = 0, pageCount: Int = 0)

    suspend fun recordProgress(
        arcid: String,
        page: Int,
        pageCount: Int = 0,
        title: String? = null,
    )

    suspend fun remove(arcid: String)

    suspend fun clear()

    fun recentDeduped(n: Int): List<HistoryEntry>
}
