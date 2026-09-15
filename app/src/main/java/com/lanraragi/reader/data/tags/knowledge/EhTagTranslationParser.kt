package com.lanraragi.reader.data.tags.knowledge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/** Input contract for one immutable EhTagTranslation import. */
data class EhTagTranslationImportRequest(
    val version: String,
    val source: TagKnowledgeSourceMetadata,
    val updatedAt: Long = source.retrievedAt,
) {
    init {
        require(version.isNotBlank()) { "tag dictionary version must not be blank" }
    }
}

data class EhTagTranslationParseResult(
    val snapshot: TagKnowledgeSnapshot,
    val skippedRows: Int,
)

/**
 * Strict, dependency-free import adapter for the public EhTagTranslation database formats.
 * JSON is the authoritative format. HTML is deliberately limited to an embedded JSON document or
 * rows exposing data-namespace/data-tag attributes, which keeps a presentation change from being
 * mistaken for a complete database update.
 */
object EhTagTranslationParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parseJson(body: String, request: EhTagTranslationImportRequest): EhTagTranslationParseResult {
        val root = try {
            json.parseToJsonElement(body)
        } catch (error: Exception) {
            throw IllegalArgumentException("invalid EhTagTranslation JSON", error)
        }
        val records = mutableListOf<TagDictionaryRecord>()
        var skipped = 0
        when (root) {
            is JsonArray -> root.forEach { element ->
                val row = element as? JsonObject
                if (row == null) skipped++ else skipped += appendNamespaceRow(row, request.version, records)
            }
            is JsonObject -> {
                if (root.string("namespace") != null) {
                    skipped += appendNamespaceRow(root, request.version, records)
                } else {
                    val database = root["data"]
                    when (database) {
                        is JsonArray -> database.forEach { element ->
                            val row = element as? JsonObject
                            if (row == null) skipped++ else skipped += appendNamespaceRow(row, request.version, records)
                        }
                        is JsonObject -> database.forEach { (namespace, values) ->
                            skipped += appendTagMap(namespace, values as? JsonObject, request.version, records)
                        }
                        else -> root.forEach { (namespace, values) ->
                            if (namespace !in ROOT_METADATA_KEYS && namespace.lowercase() !in PSEUDO_NAMESPACES) {
                                skipped += appendTagMap(namespace, values as? JsonObject, request.version, records)
                            }
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("EhTagTranslation root must be an object or array")
        }
        return result(records, skipped, body, request)
    }

    fun parseHtml(body: String, request: EhTagTranslationImportRequest): EhTagTranslationParseResult {
        embeddedJson(body)?.let { return parseJson(it, request) }
        val records = mutableListOf<TagDictionaryRecord>()
        var skipped = 0
        HTML_ROW.findAll(body).forEach { match ->
            val attributes = attributes(match.groupValues[1])
            val namespace = attributes["data-namespace"] ?: attributes["namespace"]
            val tagKey = attributes["data-tag"] ?: attributes["data-key"]
            if (namespace.isNullOrBlank() || tagKey.isNullOrBlank()) {
                skipped++
            } else {
                records += TagDictionaryRecord(
                    namespace = decodeHtml(namespace),
                    tagKey = decodeHtml(tagKey),
                    translatedName = attributes["data-name"]?.let(::decodeHtml),
                    fullName = attributes["data-full-name"]?.let(::decodeHtml),
                    intro = attributes["data-intro"]?.let(::decodeHtml),
                    links = attributes["data-links"]?.let(::decodeHtml),
                    dataVersion = request.version,
                )
            }
        }
        if (records.isEmpty()) {
            throw IllegalArgumentException("EhTagTranslation HTML contains no supported dictionary rows")
        }
        return result(records, skipped, body, request)
    }

    fun sha256(body: String): String = MessageDigest.getInstance("SHA-256")
        .digest(body.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun result(
        records: List<TagDictionaryRecord>,
        skippedRows: Int,
        body: String,
        request: EhTagTranslationImportRequest,
    ): EhTagTranslationParseResult {
        val checksum = sha256(body)
        val supplied = request.source.checksumSha256
        require(supplied == null || supplied.equals(checksum, ignoreCase = true)) {
            "EhTagTranslation checksum does not match source metadata"
        }
        val snapshot = TagKnowledgeSnapshot(
            version = request.version,
            dictionary = records,
            updatedAt = request.updatedAt,
            source = request.source.copy(checksumSha256 = checksum),
        ).normalized()
        require(snapshot.dictionary.isNotEmpty()) { "EhTagTranslation import contains no valid dictionary records" }
        return EhTagTranslationParseResult(snapshot, skippedRows)
    }

    private fun appendNamespaceRow(
        row: JsonObject,
        version: String,
        output: MutableList<TagDictionaryRecord>,
    ): Int {
        val namespace = row.string("namespace") ?: return 1
        return appendTagMap(namespace, row["data"] as? JsonObject, version, output)
    }

    private fun appendTagMap(
        namespace: String,
        values: JsonObject?,
        version: String,
        output: MutableList<TagDictionaryRecord>,
    ): Int {
        if (values == null) return 1
        var skipped = 0
        values.forEach { (tagKey, rawInfo) ->
            val info = rawInfo as? JsonObject
            if (tagKey.isBlank() || info == null) {
                skipped++
            } else {
                val name = info.string("name")
                output += TagDictionaryRecord(
                    namespace = namespace,
                    tagKey = tagKey,
                    translatedName = name?.takeUnless { it.equals(tagKey, ignoreCase = true) },
                    fullName = info.string("fullName") ?: info.string("full_name"),
                    intro = info.string("intro"),
                    links = info.string("links") ?: info["links"]?.toString(),
                    dataVersion = version,
                )
            }
        }
        return skipped
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf(String::isNotBlank)

    private fun embeddedJson(html: String): String? = EMBEDDED_JSON.find(html)?.groupValues?.get(1)

    private fun attributes(raw: String): Map<String, String> = ATTRIBUTE.findAll(raw).associate {
        it.groupValues[1].lowercase() to it.groupValues[3]
    }

    private fun decodeHtml(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private val ROOT_METADATA_KEYS = setOf("version", "schemaversion", "updatedat", "license", "source")

    /**
     * EhTagTranslation 顶层里**不是标签命名空间**的键。
     *
     * `rows` 是它的命名空间对照表：`rows.artist = "艺术家"`、`rows.group = "团队"`、
     * `rows.reclass = "重新分类"` …… 恰好就是中文社区实际在用的那套命名空间写法。
     * 不排除它就会被当成 13 个普通标签导入（`rows:artist` → 艺术家），
     * 用户点中后生成 `rows:artist$` 这种服务器上根本不存在条件的查询（真机已复现）。
     *
     * 与 [PSEUDO_DICTIONARY_NAMESPACES] 共用同一份定义；那里是给**已经导入过**的
     * 旧快照兜底用的（见该常量注释）。
     *
     * 这张表本身是有价值的输入（命名空间中英归一要以它为准），等归一功能落地时
     * 会由解析器单独读取，而不是混进标签词库。
     */
    private val PSEUDO_NAMESPACES = PSEUDO_DICTIONARY_NAMESPACES

    private val EMBEDDED_JSON = Regex(
        "<script[^>]*(?:id=[\"']eh-tag-translation-data[\"']|type=[\"']application/json[\"'])[^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HTML_ROW = Regex("<(?:tr|li|div)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
}
