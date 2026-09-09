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
            val stable = primary.takeIf { it != 0 } ?: a.sourceKey.compareTo(b.sourceKey)
            return if (q.direction == SortDirection.ASC) stable else -stable
        }
        return Comparator { a, b -> compare(a, b) }
    }
}
