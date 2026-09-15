package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow

/** One rendering/selection/paging path for the library and its search results. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryResultsContent(
    state: LibraryState,
    vm: LibraryViewModel,
    container: AppContainer,
    listState: LazyListState,
    gridState: LazyGridState,
    onOpen: (Archive) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onEdit: (() -> Unit)? = null,
    onClearSearch: () -> Unit = vm::clearQuery,
    extraEmptyActions: @Composable () -> Unit = {},
) {
    val current by rememberUpdatedState(state)
    LaunchedEffect(state.viewMode, state.queryGeneration, state.items.size, state.hasMore, state.loadingMore, state.error, state.warning) {
        snapshotFlow {
            if (current.viewMode == "list") listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            else gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { last ->
            if (last >= 0 && last >= current.items.size - 6 && current.hasMore && !current.loading &&
                !current.loadingMore && current.error == null && current.warning == null) vm.loadMore()
        }
    }
    if (state.items.isEmpty()) {
        Column(modifier.fillMaxSize().padding(contentPadding), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.loading -> { CircularProgressIndicator(); Text("正在加载…", Modifier.padding(12.dp)) }
                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::refresh) { Text("重试") }
                }
                else -> {
                    Text("未找到匹配项")
                    onEdit?.let { TextButton(onClick = it) { Text("编辑搜索条件") } }
                    TextButton(onClick = onClearSearch) { Text("清除搜索条件") }
                    TextButton(onClick = vm::refresh) { Text("重新加载") }
                    extraEmptyActions()
                }
            }
        }
        return
    }
    // 混合来源分区标题：只在**两组都真的有内容**时才显示。
    // 本地书架为空时首页只有「服务器」一组，这个标题纯属噪音；只有本地时同理。
    // 两组并存时它仍然是有用的分区信息（本地在前、服务器在后）。
    val sourceHeaders = remember(state.items, state.source) {
        val localPresent = state.items.any { it.arcid.startsWith("local_") }
        val remotePresent = state.items.any { !it.arcid.startsWith("local_") }
        if (state.source != com.lanraragi.reader.data.catalog.LibrarySource.ALL || !localPresent || !remotePresent) {
            emptyMap()
        } else {
            state.items.mapIndexedNotNull { index, archive ->
                val local = archive.arcid.startsWith("local_")
                if (index == 0 || state.items[index - 1].arcid.startsWith("local_") != local)
                    archive.arcid to if (local) "本地书架" else "服务器" else null
            }.toMap()
        }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = vm::refresh, modifier = modifier) {
        if (state.viewMode == "list") {
            LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.arcid }) { archive ->
                    Column {
                    sourceHeaders[archive.arcid]?.let { Text(it, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
                    ArchiveListRow(archive = archive,
                        onClick = { if (state.selectedIds.isNotEmpty()) vm.toggleSelection(archive.arcid) else onOpen(archive) },
                        selectionMode = state.selectedIds.isNotEmpty(), isSelected = archive.arcid in state.selectedIds,
                        onLongPress = { vm.enterSelection(archive.arcid) }, thumbnailContainer = container,
                        isCached = archive.arcid in state.offlineArcidSet)
                    }
                }
                item("footer") { ResultsFooter(state, vm) }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(state.columns.coerceIn(2, 8)), state = gridState,
                contentPadding = contentPadding, modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.items.forEach { archive ->
                    sourceHeaders[archive.arcid]?.let { label ->
                        item("source:${archive.arcid}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(label, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    item(archive.arcid) {
                    ArchiveCard(archive = archive,
                        onClick = { if (state.selectedIds.isNotEmpty()) vm.toggleSelection(archive.arcid) else onOpen(archive) },
                        selectionMode = state.selectedIds.isNotEmpty(), isSelected = archive.arcid in state.selectedIds,
                        onLongPress = { vm.enterSelection(archive.arcid) }, thumbnailContainer = container,
                        compact = state.viewMode == "compact", isCached = archive.arcid in state.offlineArcidSet,
                        volumeCount = archive.archive_count)
                    }
                }
                item("footer", span = { GridItemSpan(maxLineSpan) }) { ResultsFooter(state, vm) }
            }
        }
    }
}

@Composable private fun ResultsFooter(state: LibraryState, vm: LibraryViewModel) {
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.loadingMore -> CircularProgressIndicator()
            state.warning != null -> {
                Text(state.warning, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::refresh) { Text("重试服务器") }
            }
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::loadMore) { Text("重试加载下一页") }
            }
            !state.hasMore -> Text("已显示全部结果", style = MaterialTheme.typography.labelMedium)
            else -> TextButton(onClick = vm::loadMore) { Text("加载更多") }
        }
    }
}
