package com.lanraragi.reader.data

import android.content.Context
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.data.tags.knowledge.EhTagTranslationImportRequest
import com.lanraragi.reader.data.tags.knowledge.EhTagTranslationParser
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeRepository
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeSnapshot
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeSourceMetadata
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeUpdate
import com.lanraragi.reader.data.tags.knowledge.normalizeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    // D4 归一化索引缓存：以 TagTranslationStore.translations 的实例身份判断失效
    // （update/clearCache/publish 都会重新发布该 StateFlow），词库变化后下一次查询自动重建。
    @Volatile private var normalizationIndex: NormalizationIndex? = null
    @Volatile private var normalizationIndexSource: Any? = null

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
        maybeAutoUpdate()
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
        normalizationIndex = null
        normalizationIndexSource = null
        runCatching { file.delete() }
        runCatching { metadataFile.delete() }
    }

    // ==================== D4 翻译驱动搜索匹配 ====================

    /**
     * D4 分词归一化：逐 token 查词典；中文译名命中 → 原文 tag（含命名空间优先）；
     * 英文原文命中（残留英文 tag 库）→ 中文（库目标形态）。无命中原样返回。
     * 命名空间前缀（含中文标签如「作者:」）统一折算为英文 canonical 形式。
     */
    suspend fun normalizeSearchQuery(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return query
        maybeAutoUpdate()
        val index = normalizationIndex()
        if (index.allEntries.isEmpty()) return query
        return trimmed.split(SEARCH_TOKEN_SEPARATOR)
            .filter(String::isNotEmpty)
            .joinToString(" ") { token -> normalizeSearchToken(token, index) }
    }

    /**
     * D4 归一化预览：返回 (输入token, 归一化结果) 列表，供联想 UI。
     * 仅包含发生映射的 token；无任何映射时返回空列表。
     */
    suspend fun normalizeSearchPreview(query: String): List<Pair<String, String>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        maybeAutoUpdate()
        val index = normalizationIndex()
        if (index.allEntries.isEmpty()) return emptyList()
        return trimmed.split(SEARCH_TOKEN_SEPARATOR)
            .filter(String::isNotEmpty)
            .mapNotNull { token ->
                val normalized = normalizeSearchToken(token, index)
                if (normalized == token) null else token to normalized
            }
    }

    /** 单 token 归一化：原文精确（含命名空间与裸名）→ 中文译名→原文 → 英文前缀兜底 → 原样。 */
    private fun normalizeSearchToken(rawToken: String, index: NormalizationIndex): String {
        val token = rawToken.trim().removeSurrounding("\"").trim()
        if (token.isEmpty()) return rawToken
        var namespace: String? = null
        var value = token
        val colon = token.indexOf(':')
        if (colon > 0) {
            val rawNamespace = normalizeText(token.substring(0, colon))
            val canonical = TagNamespaceRegistry.allDescriptors()
                .firstOrNull { it.labelZh == rawNamespace }?.name
                ?: TagNamespaceRegistry.canonicalNamespace(rawNamespace)
            if (canonical != null) {
                namespace = canonical.lowercase(Locale.ROOT)
                value = token.substring(colon + 1).trim().removeSurrounding("\"").trim()
            }
        }
        val valueKey = normalizeText(value).lowercase(Locale.ROOT)
        if (valueKey.isEmpty()) return rawToken

        // 1) 原文 tag 精确（含命名空间 female:x 与裸名；fullName 作为英文别名兜底）：
        //    英文原文命中（残留英文 tag 库）→ 中文（库目标形态）；词典无中文条目时保留原文。
        val exactEntries = if (namespace != null) {
            index.originals["$namespace:$valueKey"].orEmpty()
        } else {
            index.originals[valueKey].orEmpty()
        }
        val aliasEntries = if (exactEntries.isEmpty()) {
            index.aliases[valueKey].orEmpty()
                .let { list -> namespace?.let { ns -> list.filter { it.namespace == ns } } ?: list }
        } else {
            emptyList()
        }
        val originalHit = pickByFrequency(exactEntries + aliasEntries, index.frequencies)
        if (originalHit != null) {
            val translated = originalHit.translatedName ?: return rawToken
            return if (namespace != null) "$namespace:$translated" else translated
        }

        // 2) 中文译名 → 原文 tag（含命名空间优先：输出带命名空间的库内原文形态）。
        val translatedEntries = index.translated[normalizeText(value)].orEmpty()
            .let { list -> namespace?.let { ns -> list.filter { it.namespace == ns } } ?: list }
        pickByFrequency(translatedEntries, index.frequencies)?.let { hit ->
            return if (hit.namespace.isBlank()) hit.tagKey else "${hit.namespace}:${hit.tagKey}"
        }

        // 3) 英文 → 中文兜底：原文未精确命中时按 tagKey 前缀匹配（≥2 字符），取词频最高者的中文译名。
        if (valueKey.length >= 2) {
            val prefixHit = pickByFrequency(
                index.allEntries.asSequence()
                    .filter { it.tagKey.startsWith(valueKey) }
                    .filter { namespace == null || it.namespace == namespace }
                    .toList(),
                index.frequencies,
            )
            if (prefixHit?.translatedName != null) {
                return if (namespace != null) "$namespace:${prefixHit.translatedName}" else prefixHit.translatedName
            }
        }
        return rawToken
    }

    /** 多次命中取词频最高；无词频数据时按命名空间补全权重与字典序稳定兜底。 */
    private fun pickByFrequency(
        entries: List<NormalizationEntry>,
        frequencies: Map<String, Long>,
    ): NormalizationEntry? = entries.maxWithOrNull(
        compareBy(
            { frequencies["${it.namespace}:${it.tagKey}"] ?: 0L },
            { TagNamespaceRegistry.descriptor(it.namespace)?.completionWeight ?: 0 },
            { it.namespace },
            { it.tagKey },
        ),
    )

    private suspend fun normalizationIndex(): NormalizationIndex {
        val source = TagTranslationStore.translations.value
        normalizationIndex?.let { cached ->
            if (normalizationIndexSource === source) return cached
        }
        val snapshot = knowledgeRepository?.current()
        val index = buildNormalizationIndex(snapshot, source)
        normalizationIndex = index
        normalizationIndexSource = source
        return index
    }

    /** Room 词库快照优先（含词频/fullName）；无快照时退回旧版紧凑映射（仅译名）。 */
    private fun buildNormalizationIndex(
        snapshot: TagKnowledgeSnapshot?,
        compact: Map<String, Map<String, String>>,
    ): NormalizationIndex {
        val originals = HashMap<String, MutableList<NormalizationEntry>>()
        val translated = HashMap<String, MutableList<NormalizationEntry>>()
        val aliases = HashMap<String, MutableList<NormalizationEntry>>()
        val frequencies = HashMap<String, Long>()
        val all = mutableListOf<NormalizationEntry>()

        fun addEntry(namespace: String, tagKey: String, translatedName: String?, fullName: String?) {
            val ns = namespace.trim().lowercase(Locale.ROOT)
            val key = normalizeText(tagKey).lowercase(Locale.ROOT)
            // "namespace" 伪命名空间是各命名空间自身的译名行，参与检索归一化会误映射，排除。
            if (ns.isEmpty() || key.isEmpty() || ns == META_NAMESPACE) return
            val entry = NormalizationEntry(
                namespace = ns,
                tagKey = key,
                translatedName = translatedName?.let(::normalizeText)?.takeIf(String::isNotBlank),
            )
            all += entry
            originals.getOrPut(key) { mutableListOf() }.add(entry)
            originals.getOrPut("$ns:$key") { mutableListOf() }.add(entry)
            entry.translatedName?.let { name ->
                translated.getOrPut(name) { mutableListOf() }.add(entry)
            }
            fullName?.let(::normalizeText)?.takeIf(String::isNotBlank)?.let { alias ->
                aliases.getOrPut(alias.lowercase(Locale.ROOT)) { mutableListOf() }.add(entry)
            }
        }

        if (snapshot != null && snapshot.dictionary.isNotEmpty()) {
            snapshot.dictionary.forEach {
                addEntry(it.namespace, it.tagKey, it.translatedName, it.fullName)
            }
            snapshot.frequencies.forEach { freq ->
                val ns = freq.namespace.trim().lowercase(Locale.ROOT)
                val key = normalizeText(freq.tagKey).lowercase(Locale.ROOT)
                if (ns.isNotEmpty() && key.isNotEmpty()) {
                    frequencies.merge("$ns:$key", freq.count, Long::plus)
                }
            }
        } else {
            compact.forEach { (namespace, tags) ->
                tags.forEach { (tag, name) -> addEntry(namespace, tag, name, null) }
            }
        }
        return NormalizationIndex(originals, translated, aliases, all, frequencies)
    }

    // ==================== D3 词库自动更新 ====================

    /** D3 自动更新检查每进程只执行一次；后台刷新静默失败，下次启动重试。 */
    private val autoUpdateTriggered = AtomicBoolean(false)
    private val autoUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 开关状态的内存镜像（由下面的单向流维护），避免每次查询都读一次 DataStore。 */
    private val autoUpdateEnabled = AtomicBoolean(false)
    private val autoUpdateObserverStarted = AtomicBoolean(false)

    /**
     * 自动更新读取开关用；与 AppContainer 的实例共享同一 DataStore
     * （settingsDataStore 为 top-level preferencesDataStore 单例委托），AppContainer 不可改时的最小接入点。
     */
    private val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    /**
     * 自动更新触发点：挂在首次对外查询（loadKnowledge / normalize*）上。
     * 开关开启且最后一次更新距今超过 7 天（从未更新视为过期）时后台刷新，静默失败。
     *
     * 注意：闩只在「开关已打开」后才落。原先的写法在读取开关之前就落闩，
     * 于是用户在本次进程内打开开关不会触发任何刷新，必须重启 App 才生效。
     * 这里改为订阅开关：开关关闭时不落闩，开关变为打开时重新武装闩并立即评估一次。
     */
    private fun maybeAutoUpdate() {
        if (!autoUpdateObserverStarted.compareAndSet(false, true)) {
            if (autoUpdateEnabled.get()) runAutoUpdateIfDue()
            return
        }
        autoUpdateScope.launch {
            settingsRepository.settings
                .map { it.tagTranslationAutoUpdate }
                .distinctUntilChanged()
                .collect { enabled ->
                    autoUpdateEnabled.set(enabled)
                    // 每次「打开」都重新武装，保证本次进程内立刻生效。
                    if (enabled) autoUpdateTriggered.set(false)
                    if (enabled) runAutoUpdateIfDue()
                }
        }
    }

    private fun runAutoUpdateIfDue() {
        if (!autoUpdateTriggered.compareAndSet(false, true)) return
        autoUpdateScope.launch {
            try {
                val lastUpdated = TagTranslationStore.lastUpdated.value
                val stale = lastUpdated == null ||
                    System.currentTimeMillis() - lastUpdated > AUTO_UPDATE_INTERVAL_MS
                if (!stale) return@launch
                update()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 静默失败：自动更新不打扰用户。
            }
        }
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

    /** D4 归一化索引的单条词典记录（namespace/tagKey 均已折算为小写 canonical 形式）。 */
    private data class NormalizationEntry(
        val namespace: String,
        val tagKey: String,
        val translatedName: String?,
    )

    /**
     * D4 搜索归一化索引：原文 tag（含命名空间与裸名两套键）、中文译名、英文别名（fullName）
     * 三个精确键，加全量记录（供前缀兜底扫描）与 tag_frequency 词频合计。
     */
    private class NormalizationIndex(
        val originals: Map<String, List<NormalizationEntry>>,
        val translated: Map<String, List<NormalizationEntry>>,
        val aliases: Map<String, List<NormalizationEntry>>,
        val allEntries: List<NormalizationEntry>,
        val frequencies: Map<String, Long>,
    )

    private companion object {
        const val TAG_DATABASE_LICENSE = "Refer to the upstream EhTagTranslation/Database terms"
        const val TAG_DATABASE_ATTRIBUTION = "EhTagTranslation/Database contributors"

        /** db.text.json 中存放命名空间自身译名的伪命名空间。 */
        const val META_NAMESPACE = "namespace"

        /** D3 自动更新间隔：词库最后一次更新距今超过 7 天视为过期。 */
        const val AUTO_UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        /** D4 分词分隔符：按空白分词。 */
        val SEARCH_TOKEN_SEPARATOR = Regex("\\s+")
    }
}
