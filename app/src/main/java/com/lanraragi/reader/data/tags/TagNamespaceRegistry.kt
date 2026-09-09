package com.lanraragi.reader.data.tags

import java.text.Normalizer
import java.util.Locale

/**
 * Pure namespace presentation metadata shared by tag discovery and display code.
 * Color values intentionally remain serializable tokens instead of Compose types.
 */
data class TagNamespaceDescriptor(
    val name: String,
    val labelZh: String,
    val aliases: Set<String> = emptySet(),
    val defaultColorToken: String,
    val displayOrder: Int,
    val completionWeight: Int,
    val defaultHidden: Boolean = false,
)

/** A comparable namespace position that keeps unknown namespaces stable after built-ins. */
data class TagNamespaceSortKey(
    val bucket: Int,
    val displayOrder: Int,
    val namespace: String,
) : Comparable<TagNamespaceSortKey> {
    override fun compareTo(other: TagNamespaceSortKey): Int = compareValuesBy(
        this,
        other,
        TagNamespaceSortKey::bucket,
        TagNamespaceSortKey::displayOrder,
        TagNamespaceSortKey::namespace,
    )
}

/**
 * Canonical namespace registry for E-Hentai and LANraragi metadata.
 *
 * The registry deliberately normalizes namespaces only. Tag value identity, source, and
 * translated labels belong to the domain tag model and never participate here.
 */
object TagNamespaceRegistry {
    const val UNKNOWN_COLOR_TOKEN = "#9E9E9E"

    private val builtIns = listOf(
        descriptor("parody", "原作", "#EF5350", 0, 100, "parodies"),
        descriptor("character", "角色", "#66BB6A", 1, 95, "characters"),
        descriptor("group", "社团", "#42A5F5", 2, 90, "groups", "circle", "circles"),
        descriptor("artist", "作者", "#FFA726", 3, 85, "artists"),
        descriptor("female", "女性", "#EC407A", 4, 80, "females"),
        descriptor("male", "男性", "#5C9CE6", 5, 79, "males"),
        descriptor("mixed", "混合", "#FF7043", 6, 78),
        descriptor("language", "语言", "#BA68C8", 7, 70, "languages", "lang"),
        descriptor("cosplayer", "Coser", "#26C6DA", 8, 65, "cosplayers"),
        descriptor("reclass", "重分类", "#CE93D8", 9, 40, "reclassify", "reclassification"),
        descriptor("temp", "临时", "#9E9E9E", 10, 20, "temporary"),
        descriptor("uploader", "上传者", "#90A4AE", 11, 35, "uploaders", "uploaded by"),
        descriptor("collection", "收藏集", "#FFCA28", 12, 34, "collections"),
        descriptor("convention", "展会", "#90A4AE", 13, 33, "conventions"),
        descriptor("location", "地点", "#90A4AE", 14, 32, "locations"),
        descriptor("other", "其他", "#BDBDBD", 15, 10),
        descriptor("source", "来源", "#90A4AE", 16, 5, "sources", defaultHidden = true),
        descriptor("date", "日期", "#90A4AE", 17, 4, "dates"),
        descriptor(
            "date_added",
            "添加日期",
            "#90A4AE",
            18,
            3,
            "date added",
            "date-added",
            "added date",
        ),
        // LANraragi/E-Hentai-compatible namespaces used by the existing tag UI.
        descriptor("series", "系列", "#FFCA28", 19, 30),
        descriptor("category", "分类", "#90A4AE", 20, 29, "categories"),
        descriptor("event", "活动", "#90A4AE", 21, 28, "events"),
        descriptor("timestamp", "时间戳", "#90A4AE", 22, 1, "timestamps", defaultHidden = true),
    )

    private val descriptorsByName = builtIns.associateBy(TagNamespaceDescriptor::name)
    private val aliasesToName = buildAliasIndex(builtIns)

    /** Returns built-ins in fixed display order, optionally omitting machine-oriented metadata. */
    fun allDescriptors(includeHidden: Boolean = true): List<TagNamespaceDescriptor> =
        if (includeHidden) builtIns else builtIns.filterNot(TagNamespaceDescriptor::defaultHidden)

    /** Returns built-ins in completion priority order with display order as a deterministic tie-breaker. */
    fun completionDescriptors(includeHidden: Boolean = false): List<TagNamespaceDescriptor> =
        allDescriptors(includeHidden).sortedWith(
            compareByDescending<TagNamespaceDescriptor> { it.completionWeight }
                .thenBy(TagNamespaceDescriptor::displayOrder),
        )

    /** Canonicalizes a namespace alias; an unknown non-blank namespace is intentionally retained. */
    fun canonicalNamespace(rawNamespace: String?): String? {
        val normalized = normalize(rawNamespace) ?: return null
        return aliasesToName[normalized] ?: normalized
    }

    /**
     * Resolves a descriptor for a namespace. Unknown namespaces receive a deterministic fallback
     * descriptor so callers can display and sort them without silently removing user data.
     */
    fun descriptor(namespace: String?): TagNamespaceDescriptor? {
        val canonical = canonicalNamespace(namespace) ?: return null
        return descriptorsByName[canonical] ?: TagNamespaceDescriptor(
            name = canonical,
            labelZh = canonical,
            defaultColorToken = UNKNOWN_COLOR_TOKEN,
            displayOrder = Int.MAX_VALUE,
            completionWeight = 0,
        )
    }

    /** Display sorting puts built-ins first and then normalized unknown namespaces lexically. */
    fun sortKey(namespace: String?): TagNamespaceSortKey {
        val canonical = canonicalNamespace(namespace).orEmpty()
        val builtIn = descriptorsByName[canonical]
        return if (builtIn != null) {
            TagNamespaceSortKey(bucket = 0, displayOrder = builtIn.displayOrder, namespace = canonical)
        } else {
            TagNamespaceSortKey(bucket = 1, displayOrder = Int.MAX_VALUE, namespace = canonical)
        }
    }

    private fun descriptor(
        name: String,
        labelZh: String,
        colorToken: String,
        displayOrder: Int,
        completionWeight: Int,
        vararg aliases: String,
        defaultHidden: Boolean = false,
    ): TagNamespaceDescriptor = TagNamespaceDescriptor(
        name = name,
        labelZh = labelZh,
        aliases = aliases.mapNotNull(::normalize).toSet(),
        defaultColorToken = colorToken,
        displayOrder = displayOrder,
        completionWeight = completionWeight,
        defaultHidden = defaultHidden,
    )

    private fun buildAliasIndex(descriptors: List<TagNamespaceDescriptor>): Map<String, String> {
        val aliases = linkedMapOf<String, String>()
        descriptors.forEach { descriptor ->
            (listOf(descriptor.name) + descriptor.aliases).forEach { alias ->
                val previous = aliases.put(alias, descriptor.name)
                require(previous == null || previous == descriptor.name) {
                    "namespace alias '$alias' maps to both '$previous' and '${descriptor.name}'"
                }
            }
        }
        return aliases.toMap()
    }

    private fun normalize(value: String?): String? = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotEmpty)
}
