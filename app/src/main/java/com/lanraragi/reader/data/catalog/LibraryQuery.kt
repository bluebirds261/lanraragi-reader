package com.lanraragi.reader.data.catalog

/** Single source of truth for Library filters. Values are deliberately UI-agnostic. */
data class LibraryQuery(
    val text: String = "",
    val serverScope: String = "",
    val tags: Set<String> = emptySet(),
    val categoryId: String? = null,
    val source: LibrarySource = LibrarySource.ALL,
    val savedOnly: Boolean = false,
    val requiredCapabilities: Set<LibraryCapability> = emptySet(),
    val newOnly: Boolean = false,
    val untaggedOnly: Boolean = false,
    val hideCompleted: Boolean = false,
    val sort: LibrarySort = LibrarySort.TITLE,
    val direction: SortDirection = SortDirection.ASC,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
) {
    /** Trims user input and sorts tags so equivalent queries compare equal. */
    fun normalized(): LibraryQuery = copy(
        text = text.trim(),
        tags = tags.asSequence().map(String::trim).filter(String::isNotEmpty)
            .toSortedSet(String.CASE_INSENSITIVE_ORDER),
        categoryId = categoryId?.trim()?.takeIf(String::isNotEmpty),
    )

    /** Parameters accepted by LANraragi `/api/search`. */
    fun remoteRequest(): RemoteLibraryRequest {
        val q = normalized()
        val clauses = buildList {
            q.text.takeIf(String::isNotEmpty)?.let(::add)
            q.tags.forEach { add("$it$") }
        }
        return RemoteLibraryRequest(
            filter = clauses.joinToString(",").takeIf(String::isNotEmpty),
            categoryId = q.categoryId,
            newOnly = q.newOnly,
            untaggedOnly = q.untaggedOnly,
            hideCompleted = q.hideCompleted,
            sort = q.sort,
            direction = q.direction,
        )
    }
}

enum class LibrarySource { ALL, REMOTE, LOCAL }
enum class SortDirection { ASC, DESC }
enum class LibraryViewMode { GRID, LIST, COMPACT }

/** Capability gate for action-specific collection views and batch selection. */
enum class LibraryCapability {
    READ, UPLOAD, EDIT_SERVER_METADATA, EDIT_SERVER_TOC, DELETE_SERVER_COPY, SAVE_OFFLINE, SCRAPE_METADATA,
}

enum class LibrarySort(val wireValue: String) {
    TITLE("title"), LAST_READ("lastread"), DATE_ADDED("date_added"),
    ARTIST("artist"), LANGUAGE("language"), SERIES("series"),
}

data class RemoteLibraryRequest(
    val filter: String?,
    val categoryId: String?,
    val newOnly: Boolean,
    val untaggedOnly: Boolean,
    val hideCompleted: Boolean = false,
    val sort: LibrarySort,
    val direction: SortDirection,
)
