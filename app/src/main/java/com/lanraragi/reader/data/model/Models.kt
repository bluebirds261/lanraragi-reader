package com.lanraragi.reader.data.model

import kotlinx.serialization.Serializable

/**
 * LANraragi 档案对象（列表与 metadata 接口共用，字段全部带默认值以便兼容不同版本）。
 * 注意：LANraragi 的 isnew 是字符串 "true"/"false"，progress/pagecount 为数字。
 */
@Serializable
data class Archive(
    val arcid: String = "",
    val title: String = "",
    val tags: String = "",
    val isnew: String = "false",
    val pagecount: Int = 0,
    val progress: Int = 0,
    val dateadded: Long = 0,
    val summary: String = "",
    val category: String = "",
    val pages: List<String> = emptyList(),
) {
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val isNew: Boolean
        get() = isnew.equals("true", ignoreCase = true) || isnew == "1"

    val progressPercent: Float
        get() = if (pagecount > 0) (progress.toFloat() / pagecount).coerceIn(0f, 1f) else 0f

    val displayTitle: String
        get() = title.replace(Regex("\\.(zip|cbz|rar|7z)$", RegexOption.IGNORE_CASE), "")
}

/** `/api/database/stats` 返回的服务器统计。 */
@Serializable
data class ServerStats(
    val total_archives: Long = 0,
    val total_pages: Long = 0,
    val minion_archives: Long = 0,
    val tags_count: Long = 0,
)

/** 离线缓存索引（filesDir/offline/index.json）。 */
@Serializable
data class CachedArchive(
    val arcid: String,
    val title: String = "",
    val pageCount: Int = 0,
    val coverExists: Boolean = false,
    val metadata: Archive? = null, // 存储完整元数据
    val isLocal: Boolean = false,   // 是否为外部导入的本地文件
    val localUri: String? = null,   // 本地文件路径/URI
)

@Serializable
data class OfflineIndex(
    val items: List<CachedArchive> = emptyList(),
)

/** `/api/database/stats` 返回的标签统计（含命名空间）。 */
@Serializable
data class TagStat(
    val namespace: String? = null,
    val text: String = "",
    val weight: Int = 0,
) {
    val full: String get() = if (namespace.isNullOrBlank()) text else "$namespace:$text"
}

/** `/api/categories` 返回的分类对象。 */
@Serializable
data class Category(
    val id: String = "",
    val name: String = "",
    val search: String = "",
    val pinned: Int = 0,
)

/** 保存的筛选预设。 */
@Serializable
data class FilterPreset(
    val name: String,
    val filter: String = "",
    val tags: List<String> = emptyList(),
    val sortby: String = "title",
    val order: String = "asc",
    val categoryId: String = "",
    val newOnly: Boolean = false,
    val untaggedOnly: Boolean = false,
)
