package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
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
}
