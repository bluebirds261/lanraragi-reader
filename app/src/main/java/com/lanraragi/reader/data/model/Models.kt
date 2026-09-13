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
    val toc: List<TocEntry> = emptyList(),
    /**
     * 单行本（TANK_ 前缀 arcid）的成员档案数量，由服务端 `build_tank_json` 输出。
     * 普通档案 JSON 没有该字段 → 0（默认值），因此卡片以此判定「是否为单行本 / 共几卷」。
     * 追加在构造函数末尾：既有按位置构造 Archive 的调用点不受影响。
     * 注意 pagecount 对单行本是**成员页数之和**，不是卷数，卷数只认这个字段。
     */
    val archive_count: Int = 0,
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
    val lastAccess: Long = 0,       // 最后访问时间戳（毫秒），LRU 淘汰依据
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
    val archives: List<String> = emptyList(),
)

/** E4 服务器配置 profile：多服务器切换。 */
@Serializable
data class ServerProfile(
    val name: String = "",
    val url: String = "",
    val apiKey: String = "",
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
    /**
     * 「隐藏读完」也属于筛选条件，必须随预设一起保存：
     * 否则应用预设后该开关会残留上一次的状态，而高亮又判定「与预设一致」，
     * 用户看到的列表和预设定义就会对不上（随机抽屉也会继承这个不可见条件）。
     */
    val hideCompleted: Boolean = false,
)

/** `/api/info` 返回的服务器信息(字段以默认值兜底,未知字段忽略)。 */
@Serializable
data class ServerInfo(
    val name: String = "",
    val motd: String = "",
    val version: String = "",
    val version_name: String = "",
    val version_desc: String = "",
    val has_password: Boolean = false,
    val debug_mode: Boolean = false,
    val nofun_mode: Boolean = false,
    val archives_per_page: Int = 0,
    val server_resizes_images: Boolean = false,
    val server_tracks_progress: Boolean = false,
    val authenticated_progress: Boolean = false,
    val total_pages_read: Int = 0,
    val total_archives: Int = 0,
    val cache_last_cleared: Int = 0,
    val excluded_namespaces: List<String> = emptyList(),
)

/** Minion 任务基础状态(`/api/minion/{jobid}`)。 */
@Serializable
data class MinionJob(
    val id: String = "",
    val state: String = "",
    val task: String = "",
    val retries: Int = 0,
    val note: String = "",
    val result: String = "",
) {
    /** 终态判定，与 [com.lanraragi.reader.data.LanraragiRepository.pollJobUntilDone] 保持一致。 */
    val isTerminal: Boolean
        get() {
            val s = state.lowercase()
            return s.contains("finish") || s.contains("done") || s.contains("fail") ||
                s.contains("error") || s.contains("inactive") || s == "dead" || s.isBlank()
        }

    val isFailed: Boolean
        get() {
            val s = state.lowercase()
            return s.contains("fail") || s.contains("error") || s == "dead"
        }
}

/** 服务器插件信息(`/api/plugins/{type}`)。 */
@Serializable
data class PluginInfo(
    val namespace: String = "",
    val type: String = "",
    val version: String = "",
    val name: String = "",
)

/** 单行本/卷(`/api/tankoubons*`)。字段与 openapi 的 TankoubonMetadataJson / /full result 对齐。 */
@Serializable
data class Tankoubon(
    val id: String = "",                          // TANK_xxxxxxxxxx
    val name: String = "",
    val summary: String = "",
    val tags: String = "",
    val archives: List<String> = emptyList(),     // 卷内档案 arcid（有序）
    val progress: Int = 0,                        // 全局页进度(1起)
    val full_data: List<Archive> = emptyList(),   // 仅 /full 返回的成员档案元数据
)

/** 档案目录项(长本分章 TOC)。字段与 openapi 的 toc 元素一致：{ name, page }。 */
@Serializable
data class TocEntry(
    val name: String = "",
    val page: Int = 0,
)
