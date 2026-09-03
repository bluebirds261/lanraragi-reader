package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 借鉴 JHenTai 的 EhTagTranslation 标签翻译模块：从 EhTagTranslation/Database 下载
 * 命名空间 -> 标签 -> 中文译名 的映射，缓存到本地，供标签展示时把英文 tag 翻译成中文。
 */
object TagTranslationStore {
    /** namespace(lowercase) -> tag -> 译名。 */
    val translations = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    /** 最后更新时间（epoch millis），未更新过为 null。 */
    val lastUpdated = MutableStateFlow<Long?>(null)

    fun clear() {
        translations.value = emptyMap()
        lastUpdated.value = null
    }
}

class TagTranslationRepository(private val context: Context) {

    private val file = File(context.filesDir, "tag_translations.json")

    /** EhTagTranslation 数据库的稳定下载地址（GitHub Release 资产）。 */
    private val sourceUrl = "https://github.com/EhTagTranslation/Database/releases/latest/download/db.text.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** 启动时从本地缓存加载翻译数据。 */
    fun load() {
        runCatching {
            if (!file.exists()) return
            val map = ApiClient.json.decodeFromString<Map<String, Map<String, String>>>(file.readText())
            TagTranslationStore.translations.value = map
            TagTranslationStore.lastUpdated.value = file.lastModified().takeIf { it > 0 }
        }
    }

    /** 下载并更新翻译数据库。返回 (namespace 数量, 翻译条数) 描述，失败抛异常。 */
    suspend fun update(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(sourceUrl).build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载翻译库失败：HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("下载翻译库失败：空响应")
        }
        val parsed = parseDb(body)
        if (parsed.isEmpty()) throw IllegalStateException("翻译库解析结果为空")

        val compact = parsed.mapValues { (_, tags) -> tags.filterValues { it.isNotBlank() } }
        file.parentFile?.mkdirs()
        file.writeText(ApiClient.json.encodeToString(compact))
        TagTranslationStore.translations.value = compact
        TagTranslationStore.lastUpdated.value = System.currentTimeMillis()
        val totalTags = compact.values.sumOf { it.size }
        compact.size to totalTags
    }

    fun clearCache() {
        TagTranslationStore.clear()
        runCatching { file.delete() }
    }

    /** 解析 db.text.json：`[{namespace, data: {tag: {name}}}]`。 */
    private fun parseDb(body: String): Map<String, MutableMap<String, String>> {
        val el = runCatching { ApiClient.json.parseToJsonElement(body) }.getOrNull() ?: return emptyMap()
        val array = el as? JsonArray ?: return emptyMap()
        val result = LinkedHashMap<String, MutableMap<String, String>>()
        array.forEach { elem ->
            val obj = elem as? JsonObject ?: return@forEach
            val ns = (obj["namespace"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
            val data = obj["data"] as? JsonObject ?: return@forEach
            data.forEach { (tag, info) ->
                val infoObj = info as? JsonObject ?: return@forEach
                val name = (infoObj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
                if (name.isNotEmpty() && !name.equals(tag, ignoreCase = true)) {
                    result.getOrPut(ns.lowercase()) { LinkedHashMap() }[tag] = name
                }
            }
        }
        return result
    }
}
