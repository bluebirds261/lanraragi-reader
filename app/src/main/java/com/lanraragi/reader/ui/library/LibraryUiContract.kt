package com.lanraragi.reader.ui.library

import com.lanraragi.reader.data.catalog.LibraryEntry

/** UI boundary with no navigation, repository or ViewModel dependency. */
data class LibraryUiCallbacks(
    val onOpen: (LibraryEntry) -> Unit,
    val onSelect: (LibraryEntry) -> Unit,
    val onToggleSelection: (LibraryEntry) -> Unit,
    val onClearSelection: () -> Unit,
)

/** Tablet detail target keyed independently of positions and refresh order. */
data class LibraryDetailSelection(val sourceKey: String? = null) {
    fun select(entry: LibraryEntry): LibraryDetailSelection = copy(sourceKey = entry.sourceKey)
    fun clear(): LibraryDetailSelection = copy(sourceKey = null)
    fun resolve(entries: Iterable<LibraryEntry>): LibraryEntry? = entries.firstOrNull { it.sourceKey == sourceKey }
}
