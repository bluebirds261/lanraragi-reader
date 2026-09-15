package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

/** Disk-backed, server-scoped tag statistics. Never stores credentials. */
class SearchDiscoveryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("search_tag_stats", Context.MODE_PRIVATE)
    @Serializable data class Snapshot(val tags: List<TagStat>, val updatedAt: Long)
    private fun key(scope: String) = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        .joinToString("") { "%02x".format(it) }
    suspend fun cached(scope: String): Snapshot? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.json.decodeFromString<Snapshot>(prefs.getString(key(scope), null) ?: return@withContext null) }.getOrNull()
    }
    suspend fun save(scope: String, tags: List<TagStat>): Snapshot = withContext(Dispatchers.IO) {
        val snapshot = Snapshot(normalize(tags), System.currentTimeMillis())
        prefs.edit().putString(key(scope), ApiClient.json.encodeToString(snapshot)).apply()
        snapshot
    }
    companion object {
        const val TTL = 30 * 60 * 1000L
        private val hidden = setOf("source", "date", "date_added", "timestamp", "temp", "日期", "添加日期", "时间戳", "临时")
        fun normalize(tags: List<TagStat>): List<TagStat> = tags.map {
            it.copy(namespace = TagNamespaceRegistry.canonicalNamespace(it.namespace)?.takeIf(String::isNotBlank))
        }.filterNot { it.namespace in hidden || Regex("""^\d{4}-\d{2}-\d{2}([ T].*)?$""").matches(it.text.trim()) }
            .distinctBy { it.full }.sortedWith(compareByDescending<TagStat> { it.weight }.thenBy { it.full })
    }
}
