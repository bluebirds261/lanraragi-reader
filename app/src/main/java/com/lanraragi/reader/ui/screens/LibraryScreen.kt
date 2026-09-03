package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.Collections
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.FilterPreset
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.TagFilterChip
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.rememberTagColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val SORT_OPTIONS = listOf(
    "title" to "标题",
    "lastread" to "最近阅读",
    "dateadded" to "添加日期",
    "artist" to "作者",
)

data class LibraryState(
    val items: List<Archive> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val filter: String = "",
    val selectedTags: List<String> = emptyList(),
    val sortby: String = "title",
    val order: String = "asc",
    val categoryId: String = "",
    val categories: List<Category> = emptyList(),
    val tags: List<TagStat> = emptyList(),
    val tagsLoading: Boolean = false,
    val newOnly: Boolean = false,
    val untaggedOnly: Boolean = false,
    val columns: Int = 3,
    val coverPrefetchCount: Int = 12,
    val firstPages: Map<String, String> = emptyMap(),
    val presets: List<FilterPreset> = emptyList(),
    val defaultPreset: String = "",
    val showPresets: Boolean = false,
    val viewMode: String = "grid", // grid | list | compact
    val galleryTitle: String = "图库",
    val galleryTitleMode: String = "default",
    val showArchiveCount: Boolean = true,
)

@OptIn(FlowPreview::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val query = MutableStateFlow("")
    private val loadMutex = Mutex()
    private var nextStart = 0

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private val _scrollToTop = MutableSharedFlow<Unit>()
    val scrollToTop = _scrollToTop.asSharedFlow()

    init {
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            _state.update {
                it.copy(
                    columns = s.galleryColumns,
                    coverPrefetchCount = s.coverPrefetchCount,
                    viewMode = s.galleryViewMode,
                    galleryTitle = s.galleryTitle,
                    galleryTitleMode = s.galleryTitleMode,
                    showArchiveCount = s.showArchiveCount,
                )
            }
        }
        viewModelScope.launch {
            try {
                val cats = repository.getCategories()
                _state.update { it.copy(categories = cats) }
            } catch (_: Exception) {
            }
        }
        viewModelScope.launch {
            query.drop(1).debounce(350).distinctUntilChanged().collectLatest { refresh() }
        }
        viewModelScope.launch {
            FilterBus.filter.collect { f ->
                if (f != null) {
                    query.value = ""
                    _state.update { it.copy(filter = "", selectedTags = listOf(f)) }
                    FilterBus.filter.value = null
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            SearchBus.query.collect { q ->
                if (q != null) {
                    _state.update { it.copy(filter = q) }
                    SearchBus.query.value = null
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            LibraryRefreshBus.tick.collect { t ->
                if (t > 0) refresh()
            }
        }
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            _state.update { it.copy(presets = s.presets, defaultPreset = s.defaultPreset) }
            val def = s.presets.firstOrNull { it.name == s.defaultPreset }
            if (def != null) applyPreset(def)
        }
        refresh()
    }

    fun onQueryChange(v: String) {
        _state.update { it.copy(filter = v) }
        query.value = v
    }

    fun clearQuery() {
        query.value = ""
        _state.update { it.copy(filter = "") }
        refresh()
    }

    fun clearFilters() {
        query.value = ""
        _state.update { it.copy(filter = "", selectedTags = emptyList(), categoryId = "", newOnly = false, untaggedOnly = false) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _scrollToTop.emit(Unit)
            loadPage(0, append = false)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        viewModelScope.launch { loadPage(nextStart, append = true) }
    }

    fun setSort(v: String) {
        _state.update { it.copy(sortby = v) }
        refresh()
    }

    fun setOrder(v: String) {
        _state.update { it.copy(order = v) }
        refresh()
    }

    fun toggleOrder() {
        _state.update { it.copy(order = if (it.order == "asc") "desc" else "asc") }
        refresh()
    }

    fun toggleTag(tag: String) {
        _state.update {
            val tags = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag
            it.copy(selectedTags = tags)
        }
        refresh()
    }

    fun applyPreset(preset: FilterPreset) {
        query.value = ""
        _state.update {
            it.copy(
                filter = preset.filter,
                selectedTags = preset.tags,
                sortby = preset.sortby,
                order = preset.order,
                categoryId = preset.categoryId,
                newOnly = preset.newOnly,
                untaggedOnly = preset.untaggedOnly,
            )
        }
        refresh()
    }

    fun savePresetsOrder(ordered: List<FilterPreset>) {
        viewModelScope.launch {
            container.settingsRepository.setPresets(ordered)
            _state.update { it.copy(presets = ordered) }
        }
    }

    fun savePreset(name: String) {
        val s = _state.value
        val n = name.trim()
        if (n.isEmpty()) return
        val preset = FilterPreset(
            name = n,
            filter = s.filter,
            tags = s.selectedTags,
            sortby = s.sortby,
            order = s.order,
            categoryId = s.categoryId,
            newOnly = s.newOnly,
            untaggedOnly = s.untaggedOnly,
        )
        viewModelScope.launch {
            container.settingsRepository.savePreset(preset)
            val updated = container.settingsRepository.settings.first().presets
            _state.update { it.copy(presets = updated) }
        }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch {
            container.settingsRepository.deletePreset(name)
            val updated = container.settingsRepository.settings.first().presets
            _state.update { it.copy(presets = updated, defaultPreset = if (it.defaultPreset == name) "" else it.defaultPreset) }
        }
    }

    fun setDefaultPreset(name: String) {
        _state.update { it.copy(defaultPreset = name) }
        viewModelScope.launch { container.settingsRepository.setDefaultPreset(name) }
    }

    fun togglePresets() {
        _state.update { it.copy(showPresets = !it.showPresets) }
    }

    /** 打开筛选面板时懒加载标签（避免启动时拉全量标签拖慢加载）。 */
    fun loadTags() {
        if (_state.value.tags.isNotEmpty() || _state.value.tagsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(tagsLoading = true) }
            try {
                val tags = repository.getTags()
                _state.update { it.copy(tags = tags, tagsLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(tagsLoading = false) }
            }
        }
    }

    fun setCategoryId(v: String) {
        _state.update { it.copy(categoryId = v) }
        refresh()
    }

    fun toggleNewOnly() {
        _state.update { it.copy(newOnly = !it.newOnly) }
        refresh()
    }

    fun toggleUntaggedOnly() {
        _state.update { it.copy(untaggedOnly = !it.untaggedOnly) }
        refresh()
    }

    fun setColumns(n: Int) {
        _state.update { it.copy(columns = n) }
        viewModelScope.launch { container.settingsRepository.setGalleryColumns(n) }
    }

    fun setViewMode(mode: String) {
        _state.update { it.copy(viewMode = mode) }
        viewModelScope.launch { container.settingsRepository.setGalleryViewMode(mode) }
    }

    fun toggleViewMode() {
        val next = when (_state.value.viewMode) {
            "grid" -> "list"
            "list" -> "compact"
            else -> "grid"
        }
        setViewMode(next)
    }

    /** 懒加载某档案的首页图 URL（卡片可见时调用），用于封面。 */
    fun loadFirstPage(arcid: String) {
        if (arcid in _state.value.firstPages) return
        viewModelScope.launch {
            runCatching { repository.getPageUrls(arcid).firstOrNull() }
                .getOrNull()?.let { url ->
                    _state.update { it.copy(firstPages = it.firstPages + (arcid to url)) }
                }
        }
    }

    /** 预载前 N 张封面（按配置数量），其余懒加载。 */
    private fun prefetchCovers(items: List<Archive>) {
        val count = _state.value.coverPrefetchCount
        val existing = _state.value.firstPages
        val missing = items.map { it.arcid }.filter { it !in existing }.take(count)
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val map = mutableMapOf<String, String>()
            for (id in missing) {
                runCatching { repository.getPageUrls(id).firstOrNull() }
                    .getOrNull()?.let { map[id] = it }
            }
            _state.update { it.copy(firstPages = it.firstPages + map) }
        }
    }

    private suspend fun loadPage(start: Int, append: Boolean) {
        loadMutex.withLock {
            val s = _state.value
            if (append) _state.update { it.copy(loadingMore = true, error = null) }
            else _state.update { it.copy(loading = true, error = null) }
            try {
                // 多个条件用逗号分隔，LANraragi 按 AND 逻辑取交集（空格会被当成单个 token 的一部分）。
                val combined = buildList {
                    s.filter.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    s.selectedTags.forEach { add("$it$") }
                }.joinToString(",")
                val r = repository.getArchives(
                    page = start,
                    filter = combined.ifBlank { null },
                    sortby = s.sortby,
                    order = s.order,
                    categoryId = s.categoryId.ifBlank { null },
                    newonly = s.newOnly,
                    untaggedonly = s.untaggedOnly,
                )
                val prev = _state.value.items
                val merged = if (append) (prev + r.items).distinctBy { it.arcid } else r.items
                val hasMore = r.items.isNotEmpty() &&
                        merged.size > prev.size &&
                        (r.total == null || merged.size < r.total)
                nextStart = start + r.items.size
                _state.update {
                    it.copy(
                        items = merged,
                        total = r.total,
                        hasMore = hasMore,
                        loading = false,
                        loadingMore = false,
                        error = null,
                    )
                }
                prefetchCovers(r.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "加载失败") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(container: AppContainer, navController: NavController, onOpenDrawer: () -> Unit) {
    val vm: LibraryViewModel = viewModel { LibraryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showPresetPanel by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(vm) {
        vm.scrollToTop.collect {
            if (state.viewMode == "list") {
                listState.animateScrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(showFilter) { if (showFilter) vm.loadTags() }

    Scaffold(
        topBar = {
            // 菜单按键 + 胶囊搜索框 + 刷新按键，三者同一行。
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Menu, contentDescription = "菜单")
                }
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = state.filter,
                        onValueChange = vm::onQueryChange,
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (state.filter.isNotEmpty()) {
                        IconButton(onClick = vm::clearQuery) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            QuickFilterBar(
                state, vm,
                onOpenFilter = { showFilter = true },
                onOpenPresets = { showPresetPanel = true },
            )

            when {
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!, onRetry = vm::refresh)
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> EmptyBox("没有找到档案。\n换个关键词或筛选条件，或检查服务器里的内容。")
                else -> PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.viewMode == "list") {
                        // 列表视图：单列，左侧封面大图 + 右侧标题。
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val info = listState.layoutInfo
                                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                                info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) vm.loadMore()
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.items, key = { it.arcid }) { archive ->
                                ArchiveListRow(
                                    archive = archive,
                                    onClick = { navController.navigate(Routes.detail(archive.arcid)) },
                                    coverUrl = state.firstPages[archive.arcid],
                                    onRequestCover = { vm.loadFirstPage(archive.arcid) },
                                )
                            }
                            if (state.loadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    } else {
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val info = gridState.layoutInfo
                                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                                info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) vm.loadMore()
                        }
                        // 松散网格（有标题）与紧凑网格（无标题）封面间隔保持一致。
                        val compactGrid = state.viewMode == "compact"
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.columns),
                            state = gridState,
                            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.items, key = { it.arcid }) { archive ->
                                ArchiveCard(
                                    archive = archive,
                                    onClick = { navController.navigate(Routes.detail(archive.arcid)) },
                                    coverUrl = state.firstPages[archive.arcid],
                                    onRequestCover = { vm.loadFirstPage(archive.arcid) },
                                    compact = compactGrid,
                                )
                            }
                            if (state.loadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        FilterSheet(state, vm, onDismiss = { showFilter = false })
    }

    if (showPresetPanel) {
        PresetPanel(state, vm, onDismiss = { showPresetPanel = false })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetPanel(
    state: LibraryState,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    var presets by remember(state.presets) { mutableStateOf(state.presets) }
    var draggingName by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        // 遮罩：点击关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
        )
        // 右侧侧栏（占 2/3 页面）
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(2f / 3f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("已保存预设", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (presets.isEmpty()) {
                    Text(
                        "暂无预设",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(presets, key = { _, p -> p.name }) { index, preset ->
                            val isDefault = state.defaultPreset == preset.name
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.applyPreset(preset); onDismiss() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 左侧拖动把手：长按拖动排序
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .pointerInput(preset.name) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { draggingName = preset.name; dragAccum = 0f },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragAccum += amount.y
                                                    val currentIndex = presets.indexOfFirst { it.name == preset.name }
                                                    if (currentIndex >= 0) {
                                                        if (dragAccum > rowHeightPx && currentIndex < presets.size - 1) {
                                                            presets = presets.toMutableList().also {
                                                                Collections.swap(it, currentIndex, currentIndex + 1)
                                                            }
                                                            dragAccum -= rowHeightPx
                                                        } else if (dragAccum < -rowHeightPx && currentIndex > 0) {
                                                            presets = presets.toMutableList().also {
                                                                Collections.swap(it, currentIndex, currentIndex - 1)
                                                            }
                                                            dragAccum += rowHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingName = null
                                                    dragAccum = 0f
                                                    vm.savePresetsOrder(presets)
                                                },
                                                onDragCancel = {
                                                    draggingName = null
                                                    dragAccum = 0f
                                                },
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.DragHandle,
                                        contentDescription = "拖动排序",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    preset.name + if (isDefault) "（默认）" else "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.setDefaultPreset(preset.name) }) {
                                    Text(if (isDefault) "默认✓" else "设默认")
                                }
                                IconButton(onClick = { vm.deletePreset(preset.name) }) {
                                    Icon(Icons.Filled.Clear, "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickFilterBar(
    state: LibraryState,
    vm: LibraryViewModel,
    onOpenFilter: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    var colsMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onOpenFilter,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Filled.FilterList, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("排序", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { viewMenu = true }) {
                    Icon(
                        when (state.viewMode) {
                            "list" -> Icons.AutoMirrored.Filled.ViewList
                            "compact" -> Icons.Filled.GridOn
                            else -> Icons.Filled.GridView
                        },
                        contentDescription = "视图",
                    )
                }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    DropdownMenuItem(text = { Text("松散网格") }, onClick = { vm.setViewMode("grid"); viewMenu = false })
                    DropdownMenuItem(text = { Text("紧凑网格") }, onClick = { vm.setViewMode("compact"); viewMenu = false })
                    DropdownMenuItem(text = { Text("列表") }, onClick = { vm.setViewMode("list"); viewMenu = false })
                }
            }
            IconButton(onClick = onOpenPresets) {
                Icon(Icons.Filled.Bookmark, contentDescription = "预设")
            }
            Box {
                IconButton(onClick = { colsMenu = true }) {
                    Icon(Icons.Filled.Apps, contentDescription = "列数")
                }
                DropdownMenu(expanded = colsMenu, onDismissRequest = { colsMenu = false }) {
                    (2..8).forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n 列") },
                            onClick = {
                                vm.setColumns(n)
                                colsMenu = false
                            },
                        )
                    }
                }
            }
        }
        if (state.selectedTags.isNotEmpty() || state.categoryId.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.selectedTags.forEach { tag ->
                    TagFilterChip(
                        fullTag = tag,
                        selected = true,
                        onClick = { vm.toggleTag(tag) },
                    )
                }
                if (state.categoryId.isNotEmpty()) {
                    val catName = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: state.categoryId
                    FilterChip(
                        selected = true,
                        onClick = { vm.setCategoryId("") },
                        label = { Text("分类: ${categoryLabel(catName)}") },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: LibraryState, vm: LibraryViewModel, onDismiss: () -> Unit) {
    var showSave by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("排序", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showSave = true }) { Text("保存") }
                TextButton(onClick = vm::clearFilters) { Text("清除") }
            }

            Text("排序字段", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SORT_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = state.sortby == value,
                        onClick = { vm.setSort(value) },
                        label = { Text(label) },
                    )
                }
            }

            Text("排序方向", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.order == "asc", onClick = { vm.setOrder("asc") }, label = { Text("升序") })
                FilterChip(selected = state.order == "desc", onClick = { vm.setOrder("desc") }, label = { Text("倒序") })
            }

            Text("分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.categoryId.isEmpty(),
                    onClick = { vm.setCategoryId("") },
                    label = { Text("全部") },
                )
                state.categories.forEach { c ->
                    FilterChip(
                        selected = state.categoryId == c.id,
                        onClick = { vm.setCategoryId(c.id) },
                        label = { Text(categoryLabel(c.name)) },
                    )
                }
                FilterChip(selected = state.newOnly, onClick = vm::toggleNewOnly, label = { Text("新增") })
                FilterChip(selected = state.untaggedOnly, onClick = vm::toggleUntaggedOnly, label = { Text("无标签") })
            }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("保存筛选预设") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预设名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.savePreset(name); showSave = false }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text("取消") }
            },
        )
    }
}

/** 分类名本地化：LANraragi 默认分类（Favorites / duplicate archives）映射为中文。 */
private fun categoryLabel(name: String?): String {
    if (name == null) return ""
    val n = name.lowercase().trim()
    return when {
        n.contains("favorites") || n.contains("favourite") -> "收藏"
        n.contains("duplicate") -> "重复"
        else -> name
    }
}

private fun viewModeLabel(v: String) = when (v) {
    "list" -> "列表"
    "compact" -> "紧凑网格"
    else -> "松散网格"
}
