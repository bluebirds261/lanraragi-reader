package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.tags.knowledge.EhTagTranslationImportRequest
import com.lanraragi.reader.data.tags.knowledge.EhTagTranslationParser
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeRepository
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeSnapshot
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeSourceMetadata
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
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

    val info = MutableStateFlow<TagTranslationInfo?>(null)

    fun clear() {
        translations.value = emptyMap()
        lastUpdated.value = null
        info.value = null
    }
}

data class TagTranslationInfo(
    val version: String,
    val sourceUrl: String,
    val license: String,
    val attribution: String,
    val namespaceCount: Int,
    val entryCount: Int,
    val updatedAt: Long,
)

class TagTranslationRepository(
    private val context: Context,
    private val knowledgeRepository: TagKnowledgeRepository? = null,
) {

    private val file = File(context.filesDir, "tag_translations.json")
    private val metadataFile = File(context.filesDir, "tag_translations.metadata.json")

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
            publishInfo(map, readMetadata())
        }
    }

    /** Hydrates the legacy display adapter from the active Room dictionary. */
    suspend fun loadKnowledge() {
        val snapshot = knowledgeRepository?.current() ?: return
        publish(snapshot, readMetadata())
    }

    /** 下载并更新翻译数据库。返回 (namespace 数量, 翻译条数) 描述，失败抛异常。 */
    suspend fun update(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val previous = readMetadata()
        val req = Request.Builder().url(sourceUrl).apply {
            previous?.eTag?.let { header("If-None-Match", it) }
            previous?.lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 304) {
                val current = knowledgeRepository?.current()
                if (current != null) {
                    publish(current)
                    return@withContext counts(current)
                }
                if (file.isFile) {
                    load()
                    val compact = TagTranslationStore.translations.value
                    return@withContext compact.size to compact.values.sumOf { it.size }
                }
                throw IllegalStateException("服务器返回未修改，但本地词库不存在")
            }
            if (!resp.isSuccessful) throw IllegalStateException("下载翻译库失败：HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IllegalStateException("下载翻译库失败：空响应")
            val retrievedAt = System.currentTimeMillis()
            val metadata = TranslationMetadata(
                eTag = resp.header("ETag"),
                lastModified = resp.header("Last-Modified"),
                retrievedAt = retrievedAt,
                version = retrievedAt.toString(),
                sourceUrl = sourceUrl,
                schemaVersion = "eh-tag-translation-text-v1",
                license = TAG_DATABASE_LICENSE,
                attribution = TAG_DATABASE_ATTRIBUTION,
            )
            val currentRepository = knowledgeRepository
            if (currentRepository == null) {
                val parsed = parseDb(body)
                if (parsed.isEmpty()) throw IllegalStateException("翻译库解析结果为空")
                val compact = parsed.mapValues { (_, tags) -> tags.filterValues { it.isNotBlank() } }
                file.parentFile?.mkdirs()
                file.writeTextAtomically(ApiClient.json.encodeToString(compact))
                metadataFile.writeTextAtomically(ApiClient.json.encodeToString(metadata))
                TagTranslationStore.translations.value = compact
                TagTranslationStore.lastUpdated.value = retrievedAt
                publishInfo(compact, metadata)
                return@withContext compact.size to compact.values.sumOf { it.size }
            }

            val request = EhTagTranslationImportRequest(
                version = retrievedAt.toString(),
                source = TagKnowledgeSourceMetadata(
                    sourceUrl = sourceUrl,
                    retrievedAt = retrievedAt,
                    schemaVersion = "eh-tag-translation-text-v1",
                    license = TAG_DATABASE_LICENSE,
                    attribution = TAG_DATABASE_ATTRIBUTION,
                    eTag = metadata.eTag,
                    lastModified = metadata.lastModified,
                ),
            )
            val parsed = EhTagTranslationParser.parseJson(body, request)
            when (val activated = currentRepository.replace(parsed.snapshot)) {
                is TagKnowledgeUpdate.Accepted -> {
                    metadataFile.writeTextAtomically(ApiClient.json.encodeToString(metadata))
                    publish(activated.snapshot, metadata)
                    counts(activated.snapshot)
                }
                is TagKnowledgeUpdate.Rejected -> throw IllegalStateException(
                    "翻译库校验失败：${activated.reason}",
                )
            }
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            knowledgeRepository?.clear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        TagTranslationStore.clear()
        runCatching { file.delete() }
        runCatching { metadataFile.delete() }
    }

    private fun publish(snapshot: TagKnowledgeSnapshot, metadata: TranslationMetadata? = null) {
        val compact = snapshot.dictionary
            .mapNotNull { record -> record.translatedName?.takeIf(String::isNotBlank)?.let { record to it } }
            .groupBy({ (record, _) -> record.namespace }, { (record, translated) -> record.tagKey to translated })
            .mapValues { (_, rows) -> rows.toMap() }
        file.parentFile?.mkdirs()
        file.writeTextAtomically(ApiClient.json.encodeToString(compact))
        TagTranslationStore.translations.value = compact
        TagTranslationStore.lastUpdated.value = snapshot.updatedAt.takeIf { it > 0L }
        val source = snapshot.source
        TagTranslationStore.info.value = TagTranslationInfo(
            version = snapshot.version,
            sourceUrl = source?.sourceUrl ?: metadata?.sourceUrl ?: sourceUrl,
            license = source?.license ?: metadata?.license ?: TAG_DATABASE_LICENSE,
            attribution = source?.attribution ?: metadata?.attribution ?: TAG_DATABASE_ATTRIBUTION,
            namespaceCount = snapshot.dictionary.map { it.namespace }.distinct().size,
            entryCount = snapshot.dictionary.size,
            updatedAt = snapshot.updatedAt.takeIf { it > 0L } ?: metadata?.retrievedAt ?: 0L,
        )
    }

    private fun publishInfo(
        compact: Map<String, Map<String, String>>,
        metadata: TranslationMetadata?,
    ) {
        val updatedAt = metadata?.retrievedAt ?: file.lastModified().coerceAtLeast(0L)
        TagTranslationStore.info.value = TagTranslationInfo(
            version = metadata?.version ?: updatedAt.toString(),
            sourceUrl = metadata?.sourceUrl ?: sourceUrl,
            license = metadata?.license ?: TAG_DATABASE_LICENSE,
            attribution = metadata?.attribution ?: TAG_DATABASE_ATTRIBUTION,
            namespaceCount = compact.size,
            entryCount = compact.values.sumOf { it.size },
            updatedAt = updatedAt,
        )
    }

    private fun counts(snapshot: TagKnowledgeSnapshot): Pair<Int, Int> =
        snapshot.dictionary.map { it.namespace }.distinct().size to snapshot.dictionary.size

    private fun readMetadata(): TranslationMetadata? = runCatching {
        if (!metadataFile.isFile) return null
        ApiClient.json.decodeFromString<TranslationMetadata>(metadataFile.readTextAtomically())
    }.getOrNull()

    @Serializable
    private data class TranslationMetadata(
        val eTag: String? = null,
        val lastModified: String? = null,
        val retrievedAt: Long,
        val version: String? = null,
        val sourceUrl: String? = null,
        val schemaVersion: String? = null,
        val license: String? = null,
        val attribution: String? = null,
    )

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

    private companion object {
        const val TAG_DATABASE_LICENSE = "Refer to the upstream EhTagTranslation/Database terms"
        const val TAG_DATABASE_ATTRIBUTION = "EhTagTranslation/Database contributors"
    }
}
