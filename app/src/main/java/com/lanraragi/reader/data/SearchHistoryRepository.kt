package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/** 搜索历史（最多保留 20 条，最近优先）。 */
class SearchHistoryRepository(private val context: Context) {

    private val dataStore = context.searchHistoryDataStore

    companion object {
        val KEY = stringPreferencesKey("history_json")
    }

    val history: Flow<List<String>> = dataStore.data.map { p ->
        runCatching {
            ApiClient.json.decodeFromString<List<String>>(p[KEY] ?: "[]")
        }.getOrDefault(emptyList())
    }

    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val current = history.first().filter { it != q }
        val updated = (listOf(q) + current).take(20)
        dataStore.edit { it[KEY] = ApiClient.json.encodeToString(updated) }
    }

    /** 从历史中移除一条（不存在则保持不变，幂等）。 */
    suspend fun remove(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val updated = history.first().filter { it != q }
        dataStore.edit { it[KEY] = ApiClient.json.encodeToString(updated) }
    }

    suspend fun clear() {
        dataStore.edit { it[KEY] = "[]" }
    }
}
