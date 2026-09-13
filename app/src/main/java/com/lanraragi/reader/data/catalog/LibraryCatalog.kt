package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.domain.model.ArchiveCapabilities
import com.lanraragi.reader.domain.model.ArchiveIdentity

/** Unified remote/local row consumed by a future LibraryViewModel. */
data class LibraryEntry(
    val identity: ArchiveIdentity,
    val title: String,
    val tags: List<String> = emptyList(),
    val summary: String = "",
    val categoryId: String? = null,
    val isNew: Boolean = false,
    val pageCount: Int = 0,
    val progress: Int = 0,
    val dateAdded: Long = 0L,
    val lastReadAt: Long = 0L,
    val isSaved: Boolean = false,
    val capabilities: ArchiveCapabilities = ArchiveCapabilities.forIdentity(identity),
    val localUri: String? = null,
    /**
     * 单行本（[ArchiveIdentity.Tankoubon]）的成员档案数量；普通档案与本地档案恒为 0。
     * 对齐服务端 `build_tank_json` 的 `archive_count`，最终经
     * LibraryScreen 的 legacy 映射（toLegacyArchive）落到 [com.lanraragi.reader.data.model.Archive.archive_count]
     * 供卡片渲染「单行本 · N 卷」。追加在末尾以保持既有位置构造兼容。
     */
    val volumeCount: Int = 0,
) {
    val source: LibrarySource
        get() = if (identity is ArchiveIdentity.LocalSaf) LibrarySource.LOCAL else LibrarySource.REMOTE
    val sourceKey: String get() = identity.sourceKey
}

data class LibraryPage(
    val items: List<LibraryEntry>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean,
)

interface LibraryRemoteGateway {
    /** Return all rows matching the server-side portion of [query]. */
    suspend fun fetch(query: LibraryQuery): List<LibraryEntry>
}

interface LibraryLocalGateway {
    /** Return the current local index. It must never perform network I/O. */
    suspend fun fetch(): List<LibraryEntry>
}

interface LibraryRepository {
    suspend fun load(query: LibraryQuery, page: Int = 0, pageSize: Int = DEFAULT_PAGE_SIZE): LibraryPage

    companion object { const val DEFAULT_PAGE_SIZE: Int = 30 }
}

/** Merges sources before filtering, sorting, de-duplicating and paging. */
class MixedLibraryRepository(
    private val remote: LibraryRemoteGateway,
    private val local: LibraryLocalGateway,
) : LibraryRepository {
    override suspend fun load(query: LibraryQuery, page: Int, pageSize: Int): LibraryPage {
        require(page >= 0) { "page must be non-negative" }
        require(pageSize > 0) { "pageSize must be positive" }
        val q = query.normalized()
        val candidates = buildList {
            if (q.source != LibrarySource.LOCAL) addAll(remote.fetch(q))
            if (q.source != LibrarySource.REMOTE) addAll(local.fetch())
        }
        val rows = candidates.asSequence()
            .distinctBy(LibraryEntry::sourceKey)
            .filter { it.matches(q) }
            .sortedWith(entryComparator(q))
            .toList()
        val from = (page * pageSize).coerceAtMost(rows.size)
        val to = (from + pageSize).coerceAtMost(rows.size)
        return LibraryPage(rows.subList(from, to), rows.size, page, pageSize, to < rows.size)
    }

    private fun LibraryEntry.matches(q: LibraryQuery): Boolean {
        if (q.source != LibrarySource.ALL && source != q.source) return false
        if (q.savedOnly && !isSaved) return false
        if (!q.requiredCapabilities.all { hasCapability(it) }) return false
        if (q.newOnly && !isNew) return false
        if (q.untaggedOnly && tags.isNotEmpty()) return false
        if (q.categoryId != null && categoryId != q.categoryId) return false
        val needle = q.text.trim().lowercase()
        if (needle.isNotEmpty() && !("$title $summary ${tags.joinToString(" ")}".lowercase().contains(needle))) return false
        return q.tags.all { wanted -> tags.any { it.equals(wanted, true) || it.startsWith("$wanted:", true) } }
    }

    private fun LibraryEntry.hasCapability(capability: LibraryCapability): Boolean = when (capability) {
        LibraryCapability.READ -> capabilities.canRead
        LibraryCapability.UPLOAD -> capabilities.canUpload
        LibraryCapability.EDIT_SERVER_METADATA -> capabilities.canEditServerMetadata
        LibraryCapability.EDIT_SERVER_TOC -> capabilities.canEditServerToc
        LibraryCapability.DELETE_SERVER_COPY -> capabilities.canDeleteServerCopy
        LibraryCapability.SAVE_OFFLINE -> capabilities.canSaveOffline
        LibraryCapability.SCRAPE_METADATA -> capabilities.canScrapeMetadataLocally
    }

    private fun entryComparator(q: LibraryQuery): Comparator<LibraryEntry> {
        fun compare(a: LibraryEntry, b: LibraryEntry): Int {
            val primary = when (q.sort) {
                LibrarySort.TITLE -> a.title.lowercase().compareTo(b.title.lowercase())
                LibrarySort.LAST_READ -> a.lastReadAt.compareTo(b.lastReadAt)
                LibrarySort.DATE_ADDED -> a.dateAdded.compareTo(b.dateAdded)
                else -> a.tags.firstOrNull()?.lowercase().orEmpty().compareTo(b.tags.firstOrNull()?.lowercase().orEmpty())
            }
            // 并列时返回 0：Kotlin 的 sortedWith 是稳定排序，于是保持「服务器返回顺序」。
            // 原先并列会退回按 sourceKey(arcid) 排，而服务端 sortby=date_added/lastread 的
            // 排序键并不在档案 JSON 里（例如 date_added 只在标签中），一旦解析不到就全为 0，
            // 客户端这一下重排会把服务端已经算好的顺序整个打乱——用户看到的就是「排序规则没生效」。
            return if (q.direction == SortDirection.ASC) primary else -primary
        }
        return Comparator { a, b -> compare(a, b) }
    }
}
