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
    @Serializable data class Snapshot(
        val tags: List<TagStat>,
        val updatedAt: Long,
        /**
         * 服务器上每个命名空间的标签条数（键小写）。
         *
         * 与 [tags] 不同，这里刻意保留**服务器原文**：它只用来回答「库里到底有没有
         * 这个命名空间」，服务端排序（`sortby`）就是按字面命名空间匹配的，
         * 见 [LibrarySortResolver]。归一化后的展示用列表会改写命名空间写法，
         * 用它来判断会把不存在的排序键误判成可用。旧缓存没有该字段时按空表降级。
         */
        val namespaces: Map<String, Int> = emptyMap(),
    )
    private fun key(scope: String) = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        .joinToString("") { "%02x".format(it) }
    suspend fun cached(scope: String): Snapshot? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.json.decodeFromString<Snapshot>(prefs.getString(key(scope), null) ?: return@withContext null) }.getOrNull()
    }
    suspend fun save(scope: String, tags: List<TagStat>): Snapshot = withContext(Dispatchers.IO) {
        val snapshot = Snapshot(normalize(tags), System.currentTimeMillis(), namespaceCountsOf(tags))
        prefs.edit().putString(key(scope), ApiClient.json.encodeToString(snapshot)).apply()
        snapshot
    }

    /** 已缓存的命名空间分布；无缓存或旧缓存缺该字段时返回 null（调用方据此判「未知」）。 */
    suspend fun namespaceCounts(scope: String): Map<String, Int>? =
        cached(scope)?.namespaces?.takeIf { it.isNotEmpty() }

    companion object {
        const val TTL = 30 * 60 * 1000L
        private val hidden = setOf("source", "date", "date_added", "timestamp", "temp", "日期", "添加日期", "时间戳", "临时")

        /** 统计原始标签的命名空间条数；无命名空间的标签不参与。 */
        fun namespaceCountsOf(tags: List<TagStat>): Map<String, Int> = tags
            .mapNotNull { it.namespace?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
        fun normalize(tags: List<TagStat>): List<TagStat> = tags.map {
            it.copy(namespace = TagNamespaceRegistry.canonicalNamespace(it.namespace)?.takeIf(String::isNotBlank))
        }.filterNot { it.namespace in hidden || Regex("""^\d{4}-\d{2}-\d{2}([ T].*)?$""").matches(it.text.trim()) }
            .distinctBy { it.full }.sortedWith(compareByDescending<TagStat> { it.weight }.thenBy { it.full })
    }
}
