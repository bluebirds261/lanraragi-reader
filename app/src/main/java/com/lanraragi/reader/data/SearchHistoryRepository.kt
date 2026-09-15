package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/** Scoped history; updates read and write inside the same DataStore transaction. */
class SearchHistoryRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.searchHistoryDataStore)
    companion object {
        val KEY = stringPreferencesKey("history_json")
        private val HIDDEN = booleanPreferencesKey("hidden")
        private val PAUSED = booleanPreferencesKey("paused")
    }
    private fun key(scope: String) = if (scope.isBlank()) KEY else stringPreferencesKey("scope_" +
        MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) })
    private fun decode(raw: String?) = runCatching { ApiClient.json.decodeFromString<List<String>>(raw ?: "[]") }.getOrDefault(emptyList())
    val history: Flow<List<String>> = history("")
    fun history(scope: String): Flow<List<String>> = dataStore.data.map { decode(it[key(scope)]) }
    val hidden = dataStore.data.map { it[HIDDEN] ?: false }
    val paused = dataStore.data.map { it[PAUSED] ?: false }
    suspend fun setHidden(value: Boolean) { dataStore.edit { it[HIDDEN] = value } }
    suspend fun setPaused(value: Boolean) { dataStore.edit { it[PAUSED] = value } }
    suspend fun add(query: String, scope: String = "", restoring: Boolean = false) {
        val q = query.trim()
        if (q.isEmpty()) return
        dataStore.edit { p ->
            if (p[PAUSED] != true || restoring) p[key(scope)] = ApiClient.json.encodeToString(
                (listOf(q) + decode(p[key(scope)]).filter { it != q }).take(20))
        }
    }
    suspend fun remove(query: String, scope: String = "") {
        dataStore.edit { p -> p[key(scope)] = ApiClient.json.encodeToString(decode(p[key(scope)]).filter { it != query.trim() }) }
    }
    suspend fun clear(scope: String = "") { dataStore.edit { it[key(scope)] = "[]" } }
}
