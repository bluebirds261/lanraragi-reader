package com.lanraragi.reader.ui.library

import com.lanraragi.reader.data.catalog.LibraryCapability
import com.lanraragi.reader.data.catalog.LibraryEntry

/** Selection is keyed by stable sourceKey, never by list index or display title. */
data class LibrarySelection(val sourceKeys: Set<String> = emptySet()) {
    fun toggle(entry: LibraryEntry): LibrarySelection =
        if (entry.sourceKey in sourceKeys) copy(sourceKeys = sourceKeys - entry.sourceKey)
        else copy(sourceKeys = sourceKeys + entry.sourceKey)

    fun replace(entries: Iterable<LibraryEntry>): LibrarySelection =
        copy(sourceKeys = entries.map(LibraryEntry::sourceKey).toSet())

    fun clear(): LibrarySelection = copy(sourceKeys = emptySet())

    fun selected(entries: Iterable<LibraryEntry>): List<LibraryEntry> =
        entries.filter { it.sourceKey in sourceKeys }
}

data class BatchCapabilitySummary(
    val selectedCount: Int,
    val common: Set<LibraryCapability>,
    val available: Set<LibraryCapability>,
) {
    fun supports(capability: LibraryCapability): Boolean = capability in common
}

fun summarizeBatchCapabilities(entries: Iterable<LibraryEntry>): BatchCapabilitySummary {
    val rows = entries.toList()
    if (rows.isEmpty()) return BatchCapabilitySummary(0, emptySet(), emptySet())
    fun caps(entry: LibraryEntry): Set<LibraryCapability> = buildSet {
        val c = entry.capabilities
        if (c.canRead) add(LibraryCapability.READ)
        if (c.canUpload) add(LibraryCapability.UPLOAD)
        if (c.canEditServerMetadata) add(LibraryCapability.EDIT_SERVER_METADATA)
        if (c.canEditServerToc) add(LibraryCapability.EDIT_SERVER_TOC)
        if (c.canDeleteServerCopy) add(LibraryCapability.DELETE_SERVER_COPY)
        if (c.canSaveOffline) add(LibraryCapability.SAVE_OFFLINE)
        if (c.canScrapeMetadataLocally) add(LibraryCapability.SCRAPE_METADATA)
    }
    val each = rows.map(::caps)
    val common = each.drop(1).fold(each.first()) { acc, value -> acc intersect value }
    return BatchCapabilitySummary(rows.size, common, each.flatten().toSet())
}
