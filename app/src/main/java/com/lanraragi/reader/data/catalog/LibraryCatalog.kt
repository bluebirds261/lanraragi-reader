package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.domain.model.ArchiveCapabilities
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val total: Int?,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean,
    val warning: String? = null,
)

interface LibraryRemoteGateway {
    /** Return all rows matching the server-side portion of [query]. */
    suspend fun fetch(query: LibraryQuery): List<LibraryEntry>
}

data class RemoteLibraryPage(val items: List<LibraryEntry>, val total: Int?, val nextOffset: Int, val hasMore: Boolean)

interface PagedLibraryRemoteGateway : LibraryRemoteGateway {
    suspend fun fetchPage(query: LibraryQuery, offset: Int): RemoteLibraryPage
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
    private val mutex = Mutex()
    private data class Session(
        val query: LibraryQuery,
        val rows: List<LibraryEntry>,
        val localCount: Int,
        val nextOffset: Int = 0,
        val remoteTotal: Int? = null,
        val hasMore: Boolean = false,
        val warning: String? = null,
        val pageStarts: Map<Int, Int> = mapOf(0 to 0),
    )
    private var session: Session? = null

    override suspend fun load(query: LibraryQuery, page: Int, pageSize: Int): LibraryPage = mutex.withLock {
        require(page >= 0 && pageSize > 0)
        val q = query.normalized()
        require(SearchQueryCodec.validationError(q.text) == null) { SearchQueryCodec.validationError(q.text).orEmpty() }
        val paged = remote as? PagedLibraryRemoteGateway
        var initial = session?.takeIf { page > 0 && it.query == q }
        if (initial == null) {
            val localRows = if (q.source != LibrarySource.REMOTE) local.fetch().filter { it.matches(q) }
                .distinctBy(LibraryEntry::sourceKey).sortedWith(entryComparator(q)) else emptyList()
            if (paged == null && q.source != LibrarySource.LOCAL) {
                val remoteRows = remote.fetch(q).filter { it.matches(q) }
                val rows = (remoteRows + localRows).distinctBy(LibraryEntry::sourceKey).sortedWith(entryComparator(q))
                initial = Session(q, rows, 0, remoteTotal = rows.size)
            } else {
                initial = Session(q, localRows, localRows.size, hasMore = q.source != LibrarySource.LOCAL,
                    remoteTotal = if (q.source == LibrarySource.LOCAL) 0 else null)
            }
        }
        var current = requireNotNull(initial)
        val from = current.pageStarts[page] ?: (page * pageSize).coerceAtMost(current.rows.size)
        val required = from + pageSize
        // Production mixed-source results are explicitly grouped: local first, server order next.
        // Fetch at least the first remote page to expose counts/errors even if local fills the screen.
        while (current.hasMore && (current.rows.size < required || current.nextOffset == 0)) {
            try {
                val fetched = requireNotNull(paged).fetchPage(q, current.nextOffset)
                val rows = (current.rows + fetched.items.filter { it.matches(q) }).distinctBy(LibraryEntry::sourceKey)
                current = current.copy(rows = rows, nextOffset = fetched.nextOffset, remoteTotal = fetched.total,
                    hasMore = fetched.hasMore && fetched.nextOffset > current.nextOffset, warning = null)
                if (current.rows.size > from) break
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (current.rows.isEmpty() || page > 0) throw error
                current = current.copy(warning = "服务器结果未加载：${error.message ?: "连接失败"}")
                break
            }
        }
        val to = (from + pageSize).coerceAtMost(current.rows.size)
        session = current.copy(pageStarts = current.pageStarts + (page + 1 to to))
        val total = when {
            current.warning != null -> null
            !current.hasMore -> current.rows.size
            q.savedOnly || q.requiredCapabilities.isNotEmpty() -> null
            else -> current.remoteTotal?.plus(current.localCount)
        }
        LibraryPage(current.rows.subList(from, to), total, page, pageSize,
            to < current.rows.size || current.hasMore, current.warning)
    }

    private fun LibraryEntry.matches(q: LibraryQuery): Boolean {
        if (q.source != LibrarySource.ALL && source != q.source) return false
        if (q.savedOnly && !isSaved) return false
        if (!q.requiredCapabilities.all { hasCapability(it) }) return false
        // Search/category membership and remote flags were already evaluated by LANraragi.
        if (source == LibrarySource.REMOTE) return true
        if (q.newOnly && !isNew) return false
        if (q.untaggedOnly && tags.isNotEmpty()) return false
        if (q.hideCompleted && pageCount > 0 && progress.toDouble() / pageCount > 0.85) return false
        if (q.categoryId != null && categoryId != q.categoryId) return false
        return SearchQueryCodec.matches(q.remoteRequest().filter.orEmpty(), title, tags, pageCount, progress)
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
        val namespaceKey = when (q.sort) {
            LibrarySort.ARTIST, LibrarySort.LANGUAGE, LibrarySort.SERIES -> q.sort.wireValue
            else -> null
        }
        fun compare(a: LibraryEntry, b: LibraryEntry): Int {
            if (namespaceKey != null) {
                // 按命名空间排序：取该命名空间下的标签值。
                // 命中不到该命名空间的条目一律留在末尾，**且不随升/降序翻转** ——
                // 与服务端一致（Search.pm:616-621 先 reverse 排序结果，再把未命中项 push 到末尾）。
                // 两端都缺该命名空间时返回 0 保持原有顺序，绝不再退回 tags.firstOrNull()：
                // 那会用「标签列表里的第一个标签」（可能是语言、可能是时间戳）去打乱整份顺序，
                // 表现正是用户报过的「排序规则没生效」。
                val av = a.namespaceValue(namespaceKey)
                val bv = b.namespaceValue(namespaceKey)
                if (av.isEmpty() || bv.isEmpty()) {
                    return when {
                        av.isEmpty() && bv.isEmpty() -> 0
                        av.isEmpty() -> 1
                        else -> -1
                    }
                }
                val order = av.lowercase().compareTo(bv.lowercase())
                return if (q.direction == SortDirection.ASC) order else -order
            }
            val primary = when (q.sort) {
                LibrarySort.TITLE -> a.title.lowercase().compareTo(b.title.lowercase())
                LibrarySort.LAST_READ -> a.lastReadAt.compareTo(b.lastReadAt)
                LibrarySort.DATE_ADDED -> a.dateAdded.compareTo(b.dateAdded)
                else -> 0
            }
            // 并列时返回 0：Kotlin 的 sortedWith 是稳定排序，于是保持「服务器返回顺序」。
            // 原先并列会退回按 sourceKey(arcid) 排，而服务端 sortby=date_added/lastread 的
            // 排序键并不在档案 JSON 里（例如 date_added 只在标签中），一旦解析不到就全为 0，
            // 客户端这一下重排会把服务端已经算好的顺序整个打乱——用户看到的就是「排序规则没生效」。
            return if (q.direction == SortDirection.ASC) primary else -primary
        }
        return Comparator { a, b -> compare(a, b) }
    }

    /** 该命名空间下的第一个标签值；库里没有这个命名空间（或写法不同）时返回空串。 */
    private fun LibraryEntry.namespaceValue(namespace: String): String = tags
        .firstOrNull { it.substringBefore(':', "").trim().equals(namespace, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        .orEmpty()
}
