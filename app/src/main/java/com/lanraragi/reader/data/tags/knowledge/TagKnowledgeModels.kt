package com.lanraragi.reader.data.tags.knowledge

import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import java.text.Normalizer
import java.util.Locale

/** Stable key shared by dictionary and frequency rows. */
data class TagKnowledgeKey(val namespace: String, val tagKey: String) {
    val fullName: String get() = if (namespace.isBlank()) tagKey else "$namespace:$tagKey"
}

/**
 * EhTagTranslation 里**不是标签命名空间**的伪命名空间。
 *
 * `rows` 是它的命名空间对照表（`rows.artist = "艺术家"`、`rows.group = "团队"`、
 * `rows.reclass = "重新分类"`），恰好就是中文社区实际在用的那套命名空间写法。
 * 混进标签词库的后果很具体：用户搜到并点中 `rows:artist` 会生成 `rows:artist$`
 * 这种服务器上根本不存在条件的查询（真机已复现）。
 *
 * 之所以解析器和排序器**各挡一次**：词库是导入时落库的，已经导入过的旧快照
 * 不会再走一遍解析，只在解析器里排除挡不住老数据。
 */
val PSEUDO_DICTIONARY_NAMESPACES = setOf("rows")

/** 该命名空间是否只是词库的元数据，不该作为标签参与联想。 */
fun isPseudoDictionaryNamespace(namespace: String?): Boolean =
    namespace != null && namespace.trim().lowercase(Locale.ROOT) in PSEUDO_DICTIONARY_NAMESPACES

data class TagDictionaryRecord(
    val namespace: String = "",
    val tagKey: String,
    val translatedName: String? = null,
    val fullName: String? = null,
    val intro: String? = null,
    val links: String? = null,
    val dataVersion: String,
)

data class TagFrequencyRecord(
    val namespace: String = "",
    val tagKey: String,
    val source: String,
    val count: Long,
    val dataVersion: String? = null,
)

/**
 * Provenance retained with a dictionary snapshot so an updater can show exactly which third-party
 * data was activated.  The data itself remains keyed by the canonical English namespace/key.
 */
data class TagKnowledgeSourceMetadata(
    val sourceUrl: String,
    val retrievedAt: Long,
    val schemaVersion: String,
    val license: String,
    val attribution: String,
    val eTag: String? = null,
    val lastModified: String? = null,
    val checksumSha256: String? = null,
) {
    fun validated(): TagKnowledgeSourceMetadata {
        require(sourceUrl.trim().isNotEmpty()) { "tag knowledge source URL must not be blank" }
        require(retrievedAt >= 0L) { "tag knowledge retrieval time must not be negative" }
        require(schemaVersion.trim().isNotEmpty()) { "tag knowledge schema version must not be blank" }
        require(license.trim().isNotEmpty()) { "tag knowledge license must not be blank" }
        require(attribution.trim().isNotEmpty()) { "tag knowledge attribution must not be blank" }
        checksumSha256?.let { checksum ->
            require(checksum.matches(Regex("[0-9a-fA-F]{64}"))) { "checksum must be SHA-256 hex" }
        }
        return copy(
            sourceUrl = sourceUrl.trim(),
            schemaVersion = schemaVersion.trim(),
            license = license.trim(),
            attribution = attribution.trim(),
            eTag = eTag?.let(::normalizeText)?.takeIf(String::isNotBlank),
            lastModified = lastModified?.let(::normalizeText)?.takeIf(String::isNotBlank),
            checksumSha256 = checksumSha256?.lowercase(Locale.ROOT),
        )
    }
}

data class TagKnowledgeSnapshot(
    val version: String,
    val dictionary: List<TagDictionaryRecord>,
    val frequencies: List<TagFrequencyRecord> = emptyList(),
    val updatedAt: Long = 0L,
    val source: TagKnowledgeSourceMetadata? = null,
) {
    /** Canonicalized and de-duplicated copy suitable for staging. */
    fun normalized(): TagKnowledgeSnapshot {
        require(version.isNotBlank()) { "dictionary version must not be blank" }
        val rows = dictionary.map { it.normalized(version) }
            .groupBy { TagKnowledgeKey(it.namespace, it.tagKey) }
            .map { (_, values) -> values.reduce { a, b -> a.merge(b) } }
            .sortedWith(compareBy(TagDictionaryRecord::namespace, TagDictionaryRecord::tagKey))
        val counts = frequencies.map { it.normalized(version) }
            .groupBy { Triple(it.namespace, it.tagKey, it.source) }
            .map { (_, values) -> values.reduce { a, b -> a.copy(count = maxOf(a.count, b.count)) } }
            .sortedWith(compareBy(TagFrequencyRecord::namespace, TagFrequencyRecord::tagKey, TagFrequencyRecord::source))
        require(rows.all { it.dataVersion == version }) { "dictionary row version mismatch" }
        require(counts.all { it.count >= 0L && (it.dataVersion == null || it.dataVersion == version) }) {
            "frequency rows must have non-negative counts and matching version"
        }
        return copy(dictionary = rows, frequencies = counts, source = source?.validated())
    }
}

private fun TagDictionaryRecord.normalized(version: String): TagDictionaryRecord {
    require(dataVersion == version) { "dictionary row version '$dataVersion' does not match '$version'" }
    val namespace = TagNamespaceRegistry.canonicalNamespace(this.namespace).orEmpty()
    val key = normalizeText(tagKey).lowercase(Locale.ROOT)
    require(key.isNotBlank()) { "tag key must not be blank" }
    return copy(
        namespace = namespace,
        tagKey = key,
        translatedName = translatedName?.let(::normalizeText)?.takeIf(String::isNotBlank),
        fullName = fullName?.let(::normalizeText)?.takeIf(String::isNotBlank),
        intro = intro?.let(::normalizeText)?.takeIf(String::isNotBlank),
        links = links?.let(::normalizeText)?.takeIf(String::isNotBlank),
        dataVersion = dataVersion,
    )
}

private fun TagFrequencyRecord.normalized(version: String): TagFrequencyRecord {
    val key = normalizeText(tagKey).lowercase(Locale.ROOT)
    require(key.isNotBlank() && source.isNotBlank()) { "frequency key/source must not be blank" }
    require(count >= 0L) { "frequency count must not be negative" }
    require(dataVersion == null || dataVersion == version) {
        "frequency row version '$dataVersion' does not match '$version'"
    }
    return copy(
        namespace = TagNamespaceRegistry.canonicalNamespace(namespace).orEmpty(),
        tagKey = key,
        source = normalizeText(source).lowercase(Locale.ROOT),
        dataVersion = dataVersion ?: version,
    )
}

private fun TagDictionaryRecord.merge(other: TagDictionaryRecord): TagDictionaryRecord = copy(
    translatedName = mergeText(translatedName, other.translatedName),
    fullName = mergeText(fullName, other.fullName),
    intro = mergeText(intro, other.intro),
    links = mergeText(links, other.links),
)

private fun mergeText(first: String?, second: String?): String? =
    listOfNotNull(first, second).filter(String::isNotBlank).minOrNull()

internal fun normalizeText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .trim().replace(Regex("\\s+"), " ")
