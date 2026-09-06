package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.MinionJob
import com.lanraragi.reader.data.model.PluginInfo
import com.lanraragi.reader.data.model.ServerInfo
import com.lanraragi.reader.data.model.Tankoubon
import com.lanraragi.reader.data.model.TagStat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** 统一的 JSON 解析工具，兼容 LANraragi 不同版本的响应结构。 */
object JsonHelpers {

    private val json get() = ApiClient.json

    /**
     * 解析档案列表。兼容两种响应：
     *  - 纯数组：`[ {...}, {...} ]`
     *  - DataTables 对象：`{ "data": [...], "recordsTotal": n, "recordsFiltered": n }`
     * 返回 (items, total) ，total 在纯数组时为 null。
     */
    fun parseArchiveList(body: String): Pair<List<Archive>, Int?> {
        val el = json.parseToJsonElement(body)
        return when (el) {
            is JsonArray -> Pair(el.map { json.decodeFromJsonElement<Archive>(it) }, null)

            is JsonObject -> {
                val data = el["data"]
                val items = when (data) {
                    is JsonArray -> data.map { json.decodeFromJsonElement<Archive>(it) }
                    else -> emptyList()
                }
                val total = (el["recordsTotal"] as? JsonPrimitive)?.content?.toLongOrNull()
                    ?: (el["recordsFiltered"] as? JsonPrimitive)?.content?.toLongOrNull()
                Pair(items, total?.toInt())
            }

            else -> Pair(emptyList(), null)
        }
    }

    fun parseArchive(body: String): Archive = json.decodeFromString(body)

    /** 解析 `/api/archives/{id}/files` 返回的 `pages` 数组（每个元素是相对分页 URL）。 */
    fun parsePageUrls(body: String): List<String> {
        val el = json.parseToJsonElement(body)
        val pages = (el as? JsonObject)?.get("pages")
        return when (pages) {
            is JsonArray -> pages.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            else -> emptyList()
        }
    }

    /** 解析 `/api/database/stats` 返回的标签统计数组。 */
    fun parseTagStats(body: String): List<TagStat> {
        val el = json.parseToJsonElement(body)
        return when (el) {
            is JsonArray -> el.mapNotNull { runCatching { json.decodeFromJsonElement<TagStat>(it) }.getOrNull() }
            else -> emptyList()
        }
    }

    /** 解析 `/api/categories` 返回的分类对象数组。 */
    fun parseCategories(body: String): List<Category> {
        val el = json.parseToJsonElement(body)
        return when (el) {
            is JsonArray -> el.mapNotNull { runCatching { json.decodeFromJsonElement<Category>(it) }.getOrNull() }
            else -> emptyList()
        }
    }

    /** 兼容「字符串数组」或「对象（取 key）」两种返回。 */
    fun parseStringList(body: String): List<String> {
        val el = json.parseToJsonElement(body)
        return when (el) {
            is JsonArray -> el.mapNotNull { p ->
                (p as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            is JsonObject -> el.keys.toList()
            else -> emptyList()
        }
    }

    /** 解析 `/api/search/random`：兼容单对象 / 数组 / 包在 data|archives 键下的三种形态。 */
    fun parseRandomArchives(body: String): List<Archive> {
        val el = json.parseToJsonElement(body)
        val arr = when (el) {
            is JsonArray -> el
            is JsonObject -> (el["data"] as? JsonArray)
                ?: (el["archives"] as? JsonArray)
                ?: if (el["arcid"] != null || el["title"] != null) buildList { add(el) } else null
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { runCatching { json.decodeFromJsonElement<Archive>(it) }.getOrNull() }
    }

    fun parseServerInfo(body: String): ServerInfo =
        runCatching { json.decodeFromString<ServerInfo>(body) }.getOrDefault(ServerInfo())

    /** 插件列表：数组或对象含 data 键。 */
    fun parsePluginList(body: String): List<PluginInfo> {
        val el = json.parseToJsonElement(body)
        val arr = when (el) {
            is JsonArray -> el
            is JsonObject -> el["data"] as? JsonArray ?: el["plugins"] as? JsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { runCatching { json.decodeFromJsonElement<PluginInfo>(it) }.getOrNull() }
    }

    fun parseMinionJob(body: String): MinionJob = runCatching {
        val el = json.parseToJsonElement(body)
        val job = if (el is JsonObject && el["job"] != null) el["job"] else el
        json.decodeFromJsonElement<MinionJob>(job ?: el)
    }.getOrDefault(MinionJob())

    /** 单行本列表：兼容纯数组 / `{ result: [...] }`(0.9.81) / `{ data: [...] }` / `{ tankoubons: [...] }`。 */
    fun parseTankoubons(body: String): List<Tankoubon> {
        val el = json.parseToJsonElement(body)
        val arr = when (el) {
            is JsonArray -> el
            is JsonObject ->
                (el["result"] as? JsonArray)
                    ?: (el["data"] as? JsonArray)
                    ?: (el["tankoubons"] as? JsonArray)
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { runCatching { json.decodeFromJsonElement<Tankoubon>(it) }.getOrNull() }
    }

    /** 档案所属分类(对象数组,匹配 name)。返回数组原始列表。 */
    fun parseCategoryArray(body: String): List<Category> {
        val el = json.parseToJsonElement(body)
        return when (el) {
            is JsonArray -> el.mapNotNull { runCatching { json.decodeFromJsonElement<Category>(it) }.getOrNull() }
            is JsonObject -> {
                val arr = el["categories"] as? JsonArray ?: el["data"] as? JsonArray
                if (arr != null) {
                    arr.mapNotNull { runCatching { json.decodeFromJsonElement<Category>(it) }.getOrNull() }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
