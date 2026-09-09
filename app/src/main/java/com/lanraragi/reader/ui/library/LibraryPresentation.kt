package com.lanraragi.reader.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.catalog.LibraryEntry
import com.lanraragi.reader.data.catalog.LibraryViewMode
import com.lanraragi.reader.ui.adaptive.AdaptiveLayout
import com.lanraragi.reader.ui.adaptive.AdaptiveLayoutHost

/** Stable, slot-based presentation; callers own navigation and cover loading. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItems(
    entries: List<LibraryEntry>,
    mode: LibraryViewMode,
    selection: LibrarySelection = LibrarySelection(),
    onClick: (LibraryEntry) -> Unit,
    onLongClick: (LibraryEntry) -> Unit = onClick,
    onToggleSelection: (LibraryEntry) -> Unit = {},
    modifier: Modifier = Modifier,
    itemContent: @Composable (LibraryEntry, Boolean, Modifier) -> Unit = { entry, selected, itemModifier ->
        DefaultLibraryItem(entry, selected, itemModifier)
    },
) {
    val render: @Composable (LibraryEntry, Modifier) -> Unit = { entry, itemModifier ->
        val selected = entry.sourceKey in selection.sourceKeys
        itemContent(entry, selected, itemModifier.combinedClickable(
            onClick = { if (selection.sourceKeys.isEmpty()) onClick(entry) else onToggleSelection(entry) },
            onLongClick = { onLongClick(entry) },
        ))
    }
    when (mode) {
        LibraryViewMode.GRID, LibraryViewMode.COMPACT -> LazyVerticalGrid(
            columns = GridCells.Adaptive(if (mode == LibraryViewMode.GRID) 150.dp else 108.dp),
            modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(entries, key = LibraryEntry::sourceKey) { render(it, Modifier.fillMaxWidth()) } }
        LibraryViewMode.LIST -> LazyColumn(
            modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp),
        ) { items(entries, key = LibraryEntry::sourceKey) { render(it, Modifier.fillMaxWidth()) } }
    }
}

@Composable
private fun DefaultLibraryItem(entry: LibraryEntry, selected: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        ListItem(
            headlineContent = { Text(entry.title.ifBlank { entry.sourceKey }) },
            supportingContent = { if (entry.tags.isNotEmpty()) Text(entry.tags.take(3).joinToString(", ")) },
        )
    }
}

@Composable
fun LibraryEmptyDetail(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp)) {
        Text("选择一个档案查看详情", style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Complete master-detail rendering boundary. On phones the detail slot is never composed;
 * callers route [onOpen] to the normal single-pane destination instead.
 */
@Composable
fun LibraryMasterDetail(
    layout: AdaptiveLayout,
    entries: List<LibraryEntry>,
    mode: LibraryViewMode,
    detailSelection: LibraryDetailSelection,
    onDetailSelectionChange: (LibraryDetailSelection) -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    selection: LibrarySelection = LibrarySelection(),
    onToggleSelection: (LibraryEntry) -> Unit = {},
    detailContent: @Composable (LibraryEntry) -> Unit,
) {
    val master: @Composable () -> Unit = {
        LibraryItems(
            entries = entries,
            mode = mode,
            selection = selection,
            onClick = { entry ->
                if (layout == AdaptiveLayout.TABLET_MASTER_DETAIL) {
                    onDetailSelectionChange(detailSelection.select(entry))
                } else {
                    onOpen(entry)
                }
            },
            onLongClick = { entry -> onToggleSelection(entry) },
            onToggleSelection = onToggleSelection,
            modifier = modifier.fillMaxSize(),
        )
    }
    AdaptiveLayoutHost(
        layout = layout,
        master = master,
        detail = {
            val selected = detailSelection.resolve(entries)
            if (selected == null) LibraryEmptyDetail()
            else Box(Modifier.fillMaxSize()) { detailContent(selected) }
        },
    )
}
