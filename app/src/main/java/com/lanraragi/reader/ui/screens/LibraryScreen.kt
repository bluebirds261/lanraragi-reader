package com.lanraragi.reader.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.FilterPreset
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow
import com.lanraragi.reader.ui.CategoryManagementSheet
import com.lanraragi.reader.ui.CategoryRefreshBus
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.TagFilterChip
import com.lanraragi.reader.ui.categoryDisplayName
import com.lanraragi.reader.ui.categoryNameError
import com.lanraragi.reader.ui.isProtectedCategoryName
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

private val SORT_OPTIONS = listOf(
    "title" to "标题",
    "lastread" to "最近阅读",
    "date_added" to "添加日期",
    "artist" to "作者",
    "language" to "语言",
    "series" to "系列",
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
    val bookmarkCategoryId: String = "",
    val categories: List<Category> = emptyList(),
    val categoryBusy: Boolean = false,
    val tags: List<TagStat> = emptyList(),
    val tagsLoading: Boolean = false,
    val newOnly: Boolean = false,
    val untaggedOnly: Boolean = false,
    val columns: Int = 3,
    val coverVersion: Int = 0,
    val presets: List<FilterPreset> = emptyList(),
    val defaultPreset: String = "",
    val showPresets: Boolean = false,
    val viewMode: String = "grid", // grid | list | compact
    val galleryTitle: String = "图库",
    val galleryTitleMode: String = "default",
    val showArchiveCount: Boolean = true,
    val markingAllRead: Boolean = false,
    val message: String? = null,
    val offlineArcidSet: Set<String> = emptySet(),
    val favArcidSet: Set<String> = emptySet(),
    // D2 多选：选中项集合（非空即处于多选模式）；batchBusy 批量进行中；batchNote 进度提示。
    val selectedIds: Set<String> = emptySet(),
    val batchBusy: Boolean = false,
    val batchNote: String? = null,
)

@OptIn(FlowPreview::class)
class LibraryViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val repository = container.repository
    private val query = MutableStateFlow("")
    private val loadMutex = Mutex()
    private var nextStart = 0
    private var categoryMutationInFlight = false

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
                    viewMode = s.galleryViewMode,
                    galleryTitle = s.galleryTitle,
                    galleryTitleMode = s.galleryTitleMode,
                    showArchiveCount = s.showArchiveCount,
                    sortby = s.gallerySortby,
                    order = s.galleryOrder,
                )
            }
        }

        viewModelScope.launch {
            try {
                reloadCategories()
            } catch (_: Exception) {
            }
        }

        viewModelScope.launch {
            CategoryRefreshBus.revision.drop(1).collect {
                try {
                    val clearedSelection = reloadCategories()
                    if (clearedSelection) refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(message = e.message ?: "刷新分类失败")
                    }
                }
            }
        }

        viewModelScope.launch {
            query
                .drop(1)
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest {
                    refresh()
                }
        }

        viewModelScope.launch {
            FilterBus.filter.collect { f ->
                if (f != null) {
                    query.value = ""

                    _state.update {
                        it.copy(
                            filter = "",
                            selectedTags = listOf(f),
                        )
                    }

                    FilterBus.filter.value = null
                    refresh()
                }
            }
        }

        viewModelScope.launch {
            SearchBus.query.collect { q ->
                if (q != null) {
                    _state.update {
                        it.copy(filter = q)
                    }

                    SearchBus.query.value = null
                    refresh()
                }
            }
        }

        viewModelScope.launch {
            LibraryRefreshBus.tick.collect { t ->
                if (t > 0) {
                    refresh()
                }
            }
        }

        viewModelScope.launch {
            CoverChangeBus.version.collect { v ->
                _state.update { it.copy(coverVersion = v) }
            }
        }

        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()

            _state.update {
                it.copy(
                    presets = s.presets,
                    defaultPreset = s.defaultPreset,
                )
            }

            val def = s.presets.firstOrNull {
                it.name == s.defaultPreset
            }

            if (def != null) {
                applyPreset(def)
            }
        }

        // 离线缓存集合：驱动卡片“已缓存”角标，随缓存变化即时刷新
        viewModelScope.launch {
            container.offlineCache.index
                .map { idx -> idx.items.map { a -> a.arcid }.toSet() }
                .collect { ids ->
                    _state.update { it.copy(offlineArcidSet = ids) }
                }
        }

        // 收藏集合：驱动卡片红心角标，与详情页收藏按钮共用同一 flow，天然同步
        viewModelScope.launch {
            container.favoritesRepository.favorites.collect { favs ->
                _state.update { it.copy(favArcidSet = favs) }
            }
        }

        refresh()
    }

    fun onQueryChange(v: String) {
        _state.update {
            it.copy(filter = v)
        }

        query.value = v
    }

    fun clearQuery() {
        query.value = ""

        _state.update {
            it.copy(filter = "")
        }

        refresh()
    }

    fun clearFilters() {
        query.value = ""

        _state.update {
            it.copy(
                filter = "",
                selectedTags = emptyList(),
                categoryId = "",
                newOnly = false,
                untaggedOnly = false,
            )
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (loadPage(0, append = false)) {
                _scrollToTop.emit(Unit)
            }
        }
    }

    fun loadMore() {
        val s = _state.value

        if (s.loading || s.loadingMore || !s.hasMore) {
            return
        }

        viewModelScope.launch {
            loadPage(nextStart, append = true)
        }
    }

    fun setSort(v: String) {
        _state.update {
            it.copy(sortby = v)
        }

        viewModelScope.launch {
            container.settingsRepository.setGallerySortby(v)
        }

        refresh()
    }

    fun setOrder(v: String) {
        _state.update {
            it.copy(order = v)
        }

        viewModelScope.launch {
            container.settingsRepository.setGalleryOrder(v)
        }

        refresh()
    }

    fun toggleOrder() {
        val next =
            if (_state.value.order == "asc") {
                "desc"
            } else {
                "asc"
            }

        setOrder(next)
    }

    fun toggleTag(tag: String) {
        _state.update {
            val tags =
                if (tag in it.selectedTags) {
                    it.selectedTags - tag
                } else {
                    it.selectedTags + tag
                }

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

            _state.update {
                it.copy(presets = ordered)
            }
        }
    }

    fun savePreset(name: String) {
        val s = _state.value
        val n = name.trim()

        if (n.isEmpty()) {
            return
        }

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

            val updated =
                container.settingsRepository.settings.first().presets

            _state.update {
                it.copy(presets = updated)
            }
        }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch {
            container.settingsRepository.deletePreset(name)

            val updated =
                container.settingsRepository.settings.first().presets

            _state.update {
                it.copy(
                    presets = updated,
                    defaultPreset =
                        if (it.defaultPreset == name) {
                            ""
                        } else {
                            it.defaultPreset
                        },
                )
            }
        }
    }

    fun setDefaultPreset(name: String) {
        _state.update {
            it.copy(defaultPreset = name)
        }

        viewModelScope.launch {
            container.settingsRepository.setDefaultPreset(name)
        }
    }

    fun togglePresets() {
        _state.update {
            it.copy(showPresets = !it.showPresets)
        }
    }

    /**
     * 打开筛选面板时懒加载标签，
     * 避免启动时拉全量标签拖慢加载。
     */
    fun loadTags() {
        if (
            _state.value.tags.isNotEmpty() ||
            _state.value.tagsLoading
        ) {
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(tagsLoading = true)
            }

            try {
                val tags = repository.getTags()

                _state.update {
                    it.copy(
                        tags = tags,
                        tagsLoading = false,
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(tagsLoading = false)
                }
            }
        }
    }

    fun setCategoryId(v: String) {
        _state.update {
            it.copy(categoryId = v)
        }

        refresh()
    }

    fun createCategory(name: String) {
        val n = name.trim()
        categoryNameError(n)?.let {
            showMessage(it)
            return
        }
        mutateCategory("新建分类失败") {
            repository.createCategory(n)
        }
    }

    fun renameCategory(id: String, name: String) {
        val category = _state.value.categories.firstOrNull { it.id == id }
        if (category == null) {
            showMessage("分类不存在，请刷新后重试")
            return
        }
        if (
            category.id == _state.value.bookmarkCategoryId ||
            isProtectedCategoryName(category.name)
        ) {
            showMessage("系统分类不能重命名")
            return
        }

        val n = name.trim()
        categoryNameError(n)?.let {
            showMessage(it)
            return
        }
        mutateCategory("重命名分类失败") {
            repository.renameCategory(id, n, category.pinned != 0)
        }
    }

    fun deleteCategory(id: String) {
        val category = _state.value.categories.firstOrNull { it.id == id }
        if (category == null) {
            showMessage("分类不存在，请刷新后重试")
            return
        }
        if (
            category.id == _state.value.bookmarkCategoryId ||
            isProtectedCategoryName(category.name)
        ) {
            showMessage("系统分类不能删除")
            return
        }

        mutateCategory("删除分类失败") {
            repository.deleteCategory(id)
        }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun mutateCategory(
        errorMessage: String,
        operation: suspend () -> Unit,
    ) {
        if (categoryMutationInFlight) return
        categoryMutationInFlight = true
        _state.update { it.copy(categoryBusy = true) }

        viewModelScope.launch {
            try {
                operation()

                var clearedSelection = false
                try {
                    clearedSelection = reloadCategories()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            message = e.message ?: "分类已更新，但重新加载失败",
                        )
                    }
                }

                CategoryRefreshBus.notifyChanged()
                if (clearedSelection) refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: errorMessage) }
            } finally {
                categoryMutationInFlight = false
                _state.update { it.copy(categoryBusy = false) }
            }
        }
    }

    private suspend fun reloadCategories(): Boolean {
        val categories = repository.getCategories()
        val bookmarkCategoryId = repository.getBookmarkCategoryId()
        var clearedSelection = false
        _state.update { current ->
            clearedSelection =
                current.categoryId.isNotEmpty() &&
                    categories.none { it.id == current.categoryId }
            current.copy(
                categories = categories,
                categoryId = if (clearedSelection) "" else current.categoryId,
                bookmarkCategoryId = bookmarkCategoryId,
            )
        }
        return clearedSelection
    }

    fun toggleNewOnly() {
        _state.update {
            it.copy(newOnly = !it.newOnly)
        }

        refresh()
    }

    fun toggleUntaggedOnly() {
        _state.update {
            it.copy(untaggedOnly = !it.untaggedOnly)
        }

        refresh()
    }

    fun markAllRead() {
        if (_state.value.markingAllRead) return
        viewModelScope.launch {
            _state.update { it.copy(markingAllRead = true) }
            val ids = _state.value.items.map { it.arcid }.filter { !it.startsWith("local_") }
            for (id in ids) {
                try {
                    repository.clearArchiveNew(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            LibraryRefreshBus.tick.value++
            _state.update { it.copy(markingAllRead = false) }
        }
    }

    /** 卡片红心点击：切换收藏，与详情页收藏按钮走同一 repository。 */
    fun toggleFavorite(arcid: String) {
        viewModelScope.launch {
            container.favoritesRepository.toggle(arcid)
        }
    }

    // ================================================================
    // D2 批量多选
    // ================================================================

    /** 长按卡片进入多选：该卡片作为首个选中项。 */
    fun enterSelection(arcid: String) {
        if (_state.value.batchBusy) return
        _state.update { it.copy(selectedIds = setOf(arcid)) }
    }

    /** 多选模式下点击卡片：加入 / 移出选择。 */
    fun toggleSelection(arcid: String) {
        if (_state.value.batchBusy) return
        _state.update {
            it.copy(
                selectedIds =
                    if (arcid in it.selectedIds) {
                        it.selectedIds - arcid
                    } else {
                        it.selectedIds + arcid
                    },
            )
        }
    }

    /** 全选当前已加载的档案。 */
    fun selectAll() {
        if (_state.value.batchBusy) return
        val ids = _state.value.items.map { it.arcid }.toSet()
        _state.update { it.copy(selectedIds = ids) }
    }

    /** 退出多选：清空选择与批量状态。 */
    fun exitSelection() {
        _state.update {
            it.copy(
                selectedIds = emptySet(),
                batchBusy = false,
                batchNote = null,
            )
        }
    }

    /**
     * D2 批量收藏：若选中项全部已收藏则全部取消收藏，否则全部加入收藏。
     * 逐项调用 favoritesRepository.toggle（与单卡红心同一机制）。
     */
    fun batchFavorite() {
        if (_state.value.batchBusy) return
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        val favs = _state.value.favArcidSet
        val wantFav = !ids.all { it in favs } // 不全收藏 → 目标为全部收藏；全收藏 → 目标为全部取消
        viewModelScope.launch {
            _state.update {
                it.copy(batchBusy = true, batchNote = "收藏处理中…")
            }
            var ok = 0
            var fail = 0
            ids.forEachIndexed { index, arcid ->
                try {
                    val current = arcid in favs
                    if (current != wantFav) {
                        container.favoritesRepository.toggle(arcid)
                        ok++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail++
                }
                _state.update {
                    it.copy(batchNote = "收藏处理中 ${index + 1}/${ids.size}")
                }
            }
            val verb = if (wantFav) "已收藏" else "已取消收藏"
            val msg = if (fail > 0) "$verb $ok 本，失败 $fail 本" else "$verb $ok 本"
            _state.update {
                it.copy(
                    message = msg,
                    selectedIds = emptySet(),
                    batchBusy = false,
                    batchNote = null,
                )
            }
        }
    }

    /** D2 批量缓存整本：逐项入 B1 下载队列（本地档案跳过）。 */
    fun batchCache() {
        if (_state.value.batchBusy) return
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        val archivesById = _state.value.items.associateBy { it.arcid }
        viewModelScope.launch {
            _state.update {
                it.copy(batchBusy = true, batchNote = "缓存处理中…")
            }
            var enqueued = 0
            var skipped = 0
            ids.forEachIndexed { index, arcid ->
                try {
                    val archive = archivesById[arcid]
                    if (archive != null && !arcid.startsWith("local_")) {
                        container.offlineCache.startCache(archive, repository)
                        enqueued++
                    } else {
                        skipped++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    skipped++
                }
                _state.update {
                    it.copy(batchNote = "缓存处理中 ${index + 1}/${ids.size}")
                }
            }
            val msg =
                if (enqueued > 0 && skipped > 0) {
                    "已将 $enqueued 本加入缓存队列，$skipped 本跳过"
                } else if (enqueued > 0) {
                    "已将 $enqueued 本加入缓存队列"
                } else {
                    "所选档案无法缓存"
                }
            _state.update {
                it.copy(
                    message = msg,
                    selectedIds = emptySet(),
                    batchBusy = false,
                    batchNote = null,
                )
            }
        }
    }

    /** D2 批量清除"新"标记（本地档案跳过），完成后刷新图库。 */
    fun batchClearNew() {
        if (_state.value.batchBusy) return
        val ids = _state.value.selectedIds.toList().filter { !it.startsWith("local_") }
        if (ids.isEmpty()) {
            _state.update { it.copy(message = "没有可操作的档案") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(batchBusy = true, batchNote = "标已读处理中…")
            }
            var ok = 0
            var fail = 0
            ids.forEachIndexed { index, arcid ->
                try {
                    repository.clearArchiveNew(arcid)
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail++
                }
                _state.update {
                    it.copy(batchNote = "标已读处理中 ${index + 1}/${ids.size}")
                }
            }
            LibraryRefreshBus.tick.value++
            val msg = if (fail > 0) "已清除 $ok 本的新标记，失败 $fail 本" else "已清除 $ok 本的新标记"
            _state.update {
                it.copy(
                    message = msg,
                    selectedIds = emptySet(),
                    batchBusy = false,
                    batchNote = null,
                )
            }
        }
    }

    /** D2 批量删除：逐项 DELETE，汇总成功 / 失败，完成后刷新图库。 */
    fun batchDelete() {
        if (_state.value.batchBusy) return
        val ids = _state.value.selectedIds.toList().filter { !it.startsWith("local_") }
        if (ids.isEmpty()) {
            _state.update { it.copy(message = "没有可删除的档案") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(batchBusy = true, batchNote = "删除处理中…")
            }
            var ok = 0
            var fail = 0
            ids.forEachIndexed { index, arcid ->
                try {
                    repository.deleteArchive(arcid)
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail++
                }
                _state.update {
                    it.copy(batchNote = "删除处理中 ${index + 1}/${ids.size}")
                }
            }
            LibraryRefreshBus.tick.value++
            val msg = if (fail > 0) "已删除 $ok 本，失败 $fail 本" else "已删除 $ok 本"
            _state.update {
                it.copy(
                    message = msg,
                    selectedIds = emptySet(),
                    batchBusy = false,
                    batchNote = null,
                )
            }
        }
    }

    /** D2 批量加入分类（本地档案跳过）。 */
    fun batchAddToCategory(categoryId: String) {
        if (categoryId.isBlank() || _state.value.batchBusy) return
        val ids = _state.value.selectedIds.toList().filter { !it.startsWith("local_") }
        if (ids.isEmpty()) {
            _state.update { it.copy(message = "没有可加入分类的档案") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(batchBusy = true, batchNote = "分类处理中…")
            }
            var ok = 0
            var fail = 0
            ids.forEachIndexed { index, arcid ->
                try {
                    repository.addArchiveToCategory(categoryId, arcid)
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail++
                }
                _state.update {
                    it.copy(batchNote = "分类处理中 ${index + 1}/${ids.size}")
                }
            }
            val msg = if (fail > 0) "已将 $ok 本加入分类，失败 $fail 本" else "已将 $ok 本加入分类"
            _state.update {
                it.copy(
                    message = msg,
                    selectedIds = emptySet(),
                    batchBusy = false,
                    batchNote = null,
                )
            }
        }
    }

    fun setColumns(n: Int) {
        _state.update {
            it.copy(columns = n)
        }

        viewModelScope.launch {
            container.settingsRepository.setGalleryColumns(n)
        }
    }

    fun setViewMode(mode: String) {
        _state.update {
            it.copy(viewMode = mode)
        }

        viewModelScope.launch {
            container.settingsRepository.setGalleryViewMode(mode)
        }
    }

    fun toggleViewMode() {
        val next = when (_state.value.viewMode) {
            "grid" -> "list"
            "list" -> "compact"
            else -> "grid"
        }

        setViewMode(next)
    }

    private suspend fun loadPage(
        start: Int,
        append: Boolean,
    ): Boolean {
        return loadMutex.withLock {
            val s = _state.value

            if (append) {
                _state.update {
                    it.copy(
                        loadingMore = true,
                        error = null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        loading = true,
                        error = null,
                    )
                }
            }

            try {
                // 多个条件用逗号分隔，LANraragi 按 AND 逻辑取交集。
                val combined =
                    buildList {
                        s.filter
                            .trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { add(it) }

                        s.selectedTags.forEach {
                            add("$it$")
                        }
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

                val merged =
                    if (append) {
                        (prev + r.items).distinctBy {
                            it.arcid
                        }
                    } else {
                        r.items
                    }

                val hasMore =
                    r.items.isNotEmpty() &&
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

                true

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = e.message ?: "加载失败",
                    )
                }

                false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    navController: NavController,
    onOpenDrawer: () -> Unit,
) {
    val vm: LibraryViewModel =
        viewModel {
            LibraryViewModel(container)
        }

    val state by vm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var randomLoading by remember { mutableStateOf(false) }

    // D5 空态入口：「上传到服务器」直链下载对话框
    var showUrlImport by remember { mutableStateOf(false) }
    var urlImportText by remember { mutableStateOf("") }

    val backdrop = rememberLayerBackdrop()

    var showFilter by remember {
        mutableStateOf(false)
    }

    var showPresetPanel by remember {
        mutableStateOf(false)
    }

    var showCategoryManager by remember {
        mutableStateOf(false)
    }

    var showDeleteConfirm by remember {
        mutableStateOf(false)
    }

    var showCategorySheet by remember {
        mutableStateOf(false)
    }

    // D2：多选模式下为底部批量操作条让位（正常模式为底部液态底栏预留 96dp）
    val listBottomPad =
        if (state.selectedIds.isNotEmpty()) {
            152.dp
        } else {
            96.dp
        }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(vm, state.viewMode) {
        vm.scrollToTop.collect {
            if (state.viewMode == "list") {
                listState.animateScrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(showFilter) {
        if (showFilter) {
            vm.loadTags()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // D2：选择模式激活时通知 MainScreen 隐藏液态底栏（多选结束后自动复位）
    LaunchedEffect(state.selectedIds.isNotEmpty()) {
        SelectionModeBus.active.value = state.selectedIds.isNotEmpty()
    }

    DisposableEffect(Unit) {
        onDispose {
            SelectionModeBus.active.value = false
        }
    }

    // D2：多选模式系统返回 → 退出选择；批量进行中先不拦截
    BackHandler(enabled = state.selectedIds.isNotEmpty() && !state.batchBusy) {
        vm.exitSelection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {

        // ================================================================
        // 背景采样层：只包住 Scaffold 内容；搜索框作为兄弟节点从外部采样，
        // 避免 drawBackdrop 成为 layerBackdrop 的后代而产生 RenderNode 自引用环。
        // ================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {

            Scaffold(
                topBar = {
                    // 为浮动顶栏预留空间
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(52.dp),
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->

            // ================================================================
            // QuickFilterBar 已彻底删除
            // ================================================================
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {

                when {

                    state.error != null &&
                            state.items.isEmpty() -> {

                        ErrorBox(
                            state.error!!,
                            onRetry = vm::refresh,
                        )
                    }

                    state.loading &&
                            state.items.isEmpty() -> {

                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.items.isEmpty() -> {

                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // D5 空态入口：可行动作 → 扫描本地文件 / 上传到服务器 / 随机一本
                            EmptyBox(
                                "没有找到档案。\n换个关键词或筛选条件，或检查服务器里的内容。",
                                modifier = Modifier.weight(1f),
                                actions = listOf(
                                    "扫描本地文件" to {
                                        MainTabBus.target.value = 1
                                        DownloadSubTabBus.local.value = true
                                    },
                                    "上传到服务器" to {
                                        urlImportText = ""
                                        showUrlImport = true
                                    },
                                    "随机一本" to {
                                        if (!randomLoading) {
                                            scope.launch {
                                                randomLoading = true
                                                val archive = runCatching {
                                                    container.repository.getRandomArchive()
                                                }.getOrNull()
                                                randomLoading = false

                                                if (archive == null || archive.arcid.isBlank()) {
                                                    Toast.makeText(
                                                        context,
                                                        "没有可用档案",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    navController.navigate(
                                                        Routes.reader(archive.arcid),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                ),
                            )
                        }
                    }

                    else -> {

                        PullToRefreshBox(
                            isRefreshing = state.loading,
                            onRefresh = vm::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {

                            if (state.viewMode == "list") {

                                // =================================================
                                // 列表视图
                                // =================================================
                                val shouldLoadMore by remember {
                                    derivedStateOf {
                                        val info =
                                            listState.layoutInfo

                                        val last =
                                            info.visibleItemsInfo
                                                .lastOrNull()
                                                ?.index ?: -1

                                        info.totalItemsCount > 0 &&
                                                last >=
                                                info.totalItemsCount - 8
                                    }
                                }

                                LaunchedEffect(
                                    shouldLoadMore,
                                ) {
                                    if (shouldLoadMore) {
                                        vm.loadMore()
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    contentPadding =
                                        PaddingValues(
                                            start = 12.dp,
                                            top = 12.dp,
                                            end = 12.dp,
                                            bottom = listBottomPad,
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                        ),
                                    modifier =
                                        Modifier.fillMaxSize(),
                                ) {
                                    items(
                                        state.items,
                                        key = { it.arcid },
                                    ) { archive ->

                                        ArchiveListRow(
                                            archive = archive,
                                            onClick = if (state.selectedIds.isNotEmpty()) {
                                                { vm.toggleSelection(archive.arcid) }
                                            } else {
                                                {
                                                    navController.navigate(
                                                        Routes.detail(
                                                            archive.arcid,
                                                        )
                                                    )
                                                }
                                            },
                                            selectionMode = state.selectedIds.isNotEmpty(),
                                            isSelected = archive.arcid in state.selectedIds,
                                            onLongPress = { vm.enterSelection(archive.arcid) },
                                            coverUrl = ApiClient.thumbnailUrl(archive.arcid) +
                                                if (state.coverVersion > 0) "?v=${state.coverVersion}" else "",
                                            isCached = archive.arcid in state.offlineArcidSet,
                                        )
                                    }

                                    if (state.loadingMore) {
                                        item {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment =
                                                    Alignment.Center,
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                    }
                                }

                            } else {

                                // =================================================
                                // 网格 / 紧凑网格
                                // =================================================
                                val shouldLoadMore by remember {
                                    derivedStateOf {
                                        val info =
                                            gridState.layoutInfo

                                        val last =
                                            info.visibleItemsInfo
                                                .lastOrNull()
                                                ?.index ?: -1

                                        info.totalItemsCount > 0 &&
                                                last >=
                                                info.totalItemsCount - 8
                                    }
                                }

                                LaunchedEffect(
                                    shouldLoadMore,
                                ) {
                                    if (shouldLoadMore) {
                                        vm.loadMore()
                                    }
                                }

                                val compactGrid =
                                    state.viewMode == "compact"

                                LazyVerticalGrid(
                                    columns =
                                        GridCells.Fixed(
                                            state.columns,
                                        ),
                                    state = gridState,
                                    contentPadding =
                                        PaddingValues(
                                            start = 12.dp,
                                            top = 12.dp,
                                            end = 12.dp,
                                            bottom = listBottomPad,
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                        ),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                        ),
                                    modifier =
                                        Modifier.fillMaxSize(),
                                ) {

                                    items(
                                        state.items,
                                        key = { it.arcid },
                                    ) { archive ->

                                        ArchiveCard(
                                            archive = archive,
                                            onClick = if (state.selectedIds.isNotEmpty()) {
                                                { vm.toggleSelection(archive.arcid) }
                                            } else {
                                                {
                                                    navController.navigate(
                                                        Routes.detail(
                                                            archive.arcid,
                                                        )
                                                    )
                                                }
                                            },
                                            selectionMode = state.selectedIds.isNotEmpty(),
                                            isSelected = archive.arcid in state.selectedIds,
                                            onLongPress = { vm.enterSelection(archive.arcid) },
                                            coverUrl = ApiClient.thumbnailUrl(archive.arcid) +
                                                if (state.coverVersion > 0) "?v=${state.coverVersion}" else "",
                                            compact = compactGrid,
                                            isCached = archive.arcid in state.offlineArcidSet,
                                        )
                                    }

                                    if (state.loadingMore) {
                                        item {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment =
                                                    Alignment.Center,
                                            ) {
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
        }
        }

        // ================================================================
        // 浮动顶栏（兄弟节点）：多选模式下显示选择操作栏，
        // 否则排序按钮 + 搜索框（从背景采样层取景）
        // ================================================================
        if (state.selectedIds.isNotEmpty()) {
            SelectionActionBar(
                count = state.selectedIds.size,
                allSelected = state.items.isNotEmpty() &&
                    state.selectedIds.size >= state.items.size,
                busy = state.batchBusy,
                onSelectAll = {
                    if (state.items.isNotEmpty() &&
                        state.selectedIds.size >= state.items.size
                    ) {
                        vm.exitSelection()
                    } else {
                        vm.selectAll()
                    }
                },
                onClose = vm::exitSelection,
            )
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            // 排序 / 筛选按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(
                        RoundedCornerShape(
                            percent = 50,
                        ),
                    )
                    .clickable {
                        showFilter = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = "排序与筛选",
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(
                modifier = Modifier.width(8.dp),
            )

            // 搜索框（液态玻璃，采样背景层）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()

                            blur(
                                8.dp.toPx(),
                            )

                            lens(
                                10.dp.toPx(),
                                18.dp.toPx(),
                                chromaticAberration = true,
                            )
                        },
                    )
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

                    IconButton(
                        onClick = vm::clearQuery,
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "清除",
                        )
                    }

                } else {

                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                    )
                }
            }
        }
        }

        // ================================================================
        // 批量操作条：多选模式下悬浮于底部（此时 MainScreen 液态底栏已隐藏）
        // ================================================================
        if (state.selectedIds.isNotEmpty()) {
            BatchActionBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                busy = state.batchBusy,
                note = state.batchNote,
                onFavorite = vm::batchFavorite,
                onCache = vm::batchCache,
                onCategory = {
                    if (!state.batchBusy) {
                        showCategorySheet = true
                    }
                },
                onClearNew = vm::batchClearNew,
                onDelete = {
                    if (!state.batchBusy) {
                        showDeleteConfirm = true
                    }
                },
            )
        }
    }

    // ================================================================
    // 排序 / 筛选 / 视图 / 网格大小统一面板
    // ================================================================
    if (showFilter) {
        FilterSheet(
            state = state,
            vm = vm,
            onDismiss = {
                showFilter = false
            },
            onOpenPresets = {
                showFilter = false
                showPresetPanel = true
            },
            onOpenCategoryManager = {
                showFilter = false
                showCategoryManager = true
            },
        )
    }

    if (showPresetPanel) {
        PresetPanel(
            state = state,
            vm = vm,
            onDismiss = {
                showPresetPanel = false
            },
        )
    }

    if (showCategoryManager) {
        CategoryManagementSheet(
            categories = state.categories,
            protectedCategoryIds = setOf(state.bookmarkCategoryId).filter(String::isNotBlank).toSet(),
            loading = false,
            busy = state.categoryBusy,
            onCreate = vm::createCategory,
            onRename = vm::renameCategory,
            onDelete = vm::deleteCategory,
            onValidationError = vm::showMessage,
            onDismiss = {
                showCategoryManager = false
            },
        )
    }

    // ================================================================
    // D2：批量删除强确认 + 加入分类弹层
    // ================================================================
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
            },
            title = {
                Text("删除选中档案")
            },
            text = {
                Text("将删除 ${state.selectedIds.size} 本档案，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.batchDelete()
                    },
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showCategorySheet = false
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    "加入分类（已选 ${state.selectedIds.size} 项）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                if (state.categories.isEmpty()) {
                    Text(
                        "暂无分类",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    state.categories.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCategorySheet = false
                                    vm.batchAddToCategory(c.id)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                categoryDisplayName(c.name),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }

    // ================================================================
    // D5 空态入口：「上传到服务器」→ 服务器直链下载对话框
    // ================================================================
    if (showUrlImport) {
        AlertDialog(
            onDismissRequest = {
                showUrlImport = false
            },
            title = {
                Text("上传到服务器")
            },
            text = {
                OutlinedTextField(
                    value = urlImportText,
                    onValueChange = { urlImportText = it },
                    label = { Text("下载链接（直链 zip）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = urlImportText.trim()
                        if (url.isEmpty()) {
                            Toast.makeText(context, "请输入链接", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        showUrlImport = false
                        scope.launch {
                            runCatching { container.repository.downloadFromUrl(url) }
                                .onSuccess {
                                    Toast.makeText(context, "已提交下载", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "提交失败：${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUrlImport = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * D2 顶部选择操作栏：已选计数 + 全选/全不选 + 关闭。
 * 仅在多选模式下替代原有排序/搜索浮动顶栏显示。
 */
@Composable
private fun SelectionActionBar(
    count: Int,
    allSelected: Boolean,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已选 $count 项",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )

                TextButton(
                    onClick = onSelectAll,
                    enabled = !busy && count > 0,
                ) {
                    Text(if (allSelected) "全不选" else "全选")
                }

                IconButton(
                    onClick = onClose,
                    enabled = !busy,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "退出多选",
                    )
                }
            }
        }
    }
}

/**
 * D2 底部批量操作条：收藏 / 缓存 / 分类 / 标已读 / 删除。
 * 批量进行中替换为进度提示并禁用操作。
 */
@Composable
private fun BatchActionBar(
    modifier: Modifier = Modifier,
    busy: Boolean,
    note: String?,
    onFavorite: () -> Unit,
    onCache: () -> Unit,
    onCategory: () -> Unit,
    onClearNew: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        if (busy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    note ?: "处理中…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BatchTextButton("收藏", onFavorite)
                BatchTextButton("缓存", onCache)
                BatchTextButton("分类", onCategory)
                BatchTextButton("标已读", onClearNew)

                TextButton(onClick = onDelete) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 批量操作条里的紧凑文字按钮，避免 5 个按钮挤爆窄屏。 */
@Composable
private fun BatchTextButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Text(label)
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
private fun PresetPanel(
    state: LibraryState,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    var presets by remember(state.presets) {
        mutableStateOf(state.presets)
    }

    var draggingName by remember {
        mutableStateOf<String?>(null)
    }

    var dragAccum by remember {
        mutableFloatStateOf(0f)
    }

    val rowHeightPx =
        with(LocalDensity.current) {
            56.dp.toPx()
        }

    val sheetState =
        rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 16.dp,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {

                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.primary,
                    )

                    Spacer(
                        Modifier.width(12.dp)
                    )

                    Text(
                        "已保存预设",
                        style =
                            MaterialTheme.typography.titleMedium,
                    )
                }

                HorizontalDivider()

                Spacer(
                    Modifier.height(8.dp)
                )

                if (presets.isEmpty()) {

                    Text(
                        "暂无预设",
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                horizontal = 20.dp,
                                vertical = 12.dp,
                            ),
                    )

                } else {

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        contentPadding =
                            PaddingValues(bottom = 24.dp),
                    ) {

                        itemsIndexed(
                            presets,
                            key = { _, p -> p.name },
                        ) { index, preset ->

                            val isDefault =
                                state.defaultPreset ==
                                        preset.name

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.applyPreset(
                                            preset
                                        )
                                        onDismiss()
                                    }
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                            ) {

                                // 左侧拖动把手
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .pointerInput(
                                            preset.name
                                        ) {
                                            detectDragGesturesAfterLongPress(

                                                onDragStart = {
                                                    draggingName =
                                                        preset.name
                                                    dragAccum = 0f
                                                },

                                                onDrag = {
                                                        change,
                                                        amount ->

                                                    change.consume()

                                                    dragAccum +=
                                                        amount.y

                                                    val currentIndex =
                                                        presets.indexOfFirst {
                                                            it.name ==
                                                                    preset.name
                                                        }

                                                    if (currentIndex >= 0) {

                                                        if (
                                                            dragAccum >
                                                            rowHeightPx &&
                                                            currentIndex <
                                                            presets.size - 1
                                                        ) {

                                                            presets =
                                                                presets
                                                                    .toMutableList()
                                                                    .also {
                                                                        Collections.swap(
                                                                            it,
                                                                            currentIndex,
                                                                            currentIndex + 1,
                                                                        )
                                                                    }

                                                            dragAccum -=
                                                                rowHeightPx

                                                        } else if (
                                                            dragAccum <
                                                            -rowHeightPx &&
                                                            currentIndex >
                                                            0
                                                        ) {

                                                            presets =
                                                                presets
                                                                    .toMutableList()
                                                                    .also {
                                                                        Collections.swap(
                                                                            it,
                                                                            currentIndex,
                                                                            currentIndex - 1,
                                                                        )
                                                                    }

                                                            dragAccum +=
                                                                rowHeightPx
                                                        }
                                                    }
                                                },

                                                onDragEnd = {
                                                    draggingName =
                                                        null
                                                    dragAccum = 0f

                                                    vm.savePresetsOrder(
                                                        presets
                                                    )
                                                },

                                                onDragCancel = {
                                                    draggingName =
                                                        null
                                                    dragAccum = 0f
                                                },
                                            )
                                        },
                                    contentAlignment =
                                        Alignment.Center,
                                ) {

                                    Icon(
                                        Icons.Filled.DragHandle,
                                        contentDescription =
                                            "拖动排序",
                                        tint =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant,
                                    )
                                }

                                Text(
                                    preset.name +
                                            if (isDefault) {
                                                "（默认）"
                                            } else {
                                                ""
                                            },
                                    style =
                                        MaterialTheme.typography.bodyLarge,
                                    modifier =
                                        Modifier.weight(1f),
                                )

                                TextButton(
                                    onClick = {
                                        vm.setDefaultPreset(
                                            preset.name
                                        )
                                    }
                                ) {
                                    Text(
                                        if (isDefault) {
                                            "默认✓"
                                        } else {
                                            "设默认"
                                        }
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        vm.deletePreset(
                                            preset.name
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        "删除",
                                        tint =
                                            MaterialTheme
                                                .colorScheme
                                                .error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
private fun FilterSheet(
    state: LibraryState,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenCategoryManager: () -> Unit,
) {
    var showSave by remember {
        mutableStateOf(false)
    }

    var presetName by remember {
        mutableStateOf("")
    }

    val sheetState =
        rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {

            // ============================================================
            // 标题栏
            // ============================================================
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {

                Text(
                    "排序与筛选",
                    style =
                        MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier.weight(1f),
                )

                IconButton(
                    onClick = onOpenPresets,
                ) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "预设",
                    )
                }

                TextButton(
                    onClick = {
                        presetName = ""
                        showSave = true
                    }
                ) {
                    Text("保存")
                }

                TextButton(
                    onClick = vm::clearFilters
                ) {
                    Text("清除")
                }
            }

            // ============================================================
            // 当前筛选条件
            // ============================================================
            if (
                state.selectedTags.isNotEmpty() ||
                state.categoryId.isNotEmpty() ||
                state.newOnly ||
                state.untaggedOnly
            ) {

                Text(
                    "当前筛选",
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(
                    Modifier.height(4.dp)
                )

                FlowRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {

                    state.selectedTags.forEach { tag ->

                        TagFilterChip(
                            fullTag = tag,
                            selected = true,
                            onClick = {
                                vm.toggleTag(tag)
                            },
                        )
                    }

                    if (state.categoryId.isNotEmpty()) {

                        val catName =
                            state.categories
                                .firstOrNull {
                                    it.id == state.categoryId
                                }
                                ?.name
                                ?: state.categoryId

                        FilterChip(
                            selected = true,
                            onClick = {
                                vm.setCategoryId("")
                            },
                            label = {
                                Text(
                                    "分类: ${categoryDisplayName(catName)}"
                                )
                            },
                        )
                    }

                    if (state.newOnly) {

                        FilterChip(
                            selected = true,
                            onClick = vm::toggleNewOnly,
                            label = {
                                Text("新增")
                            },
                        )
                    }

                    if (state.untaggedOnly) {

                        FilterChip(
                            selected = true,
                            onClick = vm::toggleUntaggedOnly,
                            label = {
                                Text("无标签")
                            },
                        )
                    }
                }

                Spacer(
                    Modifier.height(12.dp)
                )
            }

            // ============================================================
            // 排序字段
            // ============================================================
            Text(
                "排序字段",
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(
                Modifier.height(4.dp)
            )

            FlowRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {

                SORT_OPTIONS.forEach {
                        (value, label) ->

                    FilterChip(
                        selected =
                            state.sortby == value,
                        onClick = {
                            vm.setSort(value)
                        },
                        label = {
                            Text(label)
                        },
                    )
                }
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 排序方向
            // ============================================================
            Text(
                "排序方向",
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {

                FilterChip(
                    selected =
                        state.order == "asc",
                    onClick = {
                        vm.setOrder("asc")
                    },
                    label = {
                        Text("升序")
                    },
                )

                FilterChip(
                    selected =
                        state.order == "desc",
                    onClick = {
                        vm.setOrder("desc")
                    },
                    label = {
                        Text("倒序")
                    },
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 网格大小
            // ============================================================
            Text(
                "网格大小",
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(
                Modifier.height(4.dp)
            )

            FlowRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {

                (2..8).forEach { n ->

                    FilterChip(
                        selected =
                            state.columns == n,
                        onClick = {
                            vm.setColumns(n)
                        },
                        label = {
                            Text("${n}列")
                        },
                    )
                }
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 视图模式
            // ============================================================
            Text(
                "视图模式",
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(
                Modifier.height(4.dp)
            )

            FlowRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {

                FilterChip(
                    selected =
                        state.viewMode == "grid",
                    onClick = {
                        vm.setViewMode("grid")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text("松散网格")
                    },
                )

                FilterChip(
                    selected =
                        state.viewMode == "compact",
                    onClick = {
                        vm.setViewMode("compact")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.GridOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text("紧凑网格")
                    },
                )

                FilterChip(
                    selected =
                        state.viewMode == "list",
                    onClick = {
                        vm.setViewMode("list")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text("列表")
                    },
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 分类
            // ============================================================
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    "分类",
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.weight(1f),
                )

                TextButton(
                    onClick = onOpenCategoryManager,
                ) {
                    Text("管理")
                }
            }

            Spacer(
                Modifier.height(4.dp)
            )

            FlowRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {

                FilterChip(
                    selected =
                        state.categoryId.isEmpty(),
                    onClick = {
                        vm.setCategoryId("")
                    },
                    label = {
                        Text("全部")
                    },
                )

                state.categories.forEach { c ->

                    FilterChip(
                        selected =
                            state.categoryId == c.id,
                        onClick = {
                            vm.setCategoryId(c.id)
                        },
                        label = {
                            Text(
                                categoryDisplayName(c.name)
                            )
                        },
                    )
                }

                FilterChip(
                    selected = state.newOnly,
                    onClick = vm::toggleNewOnly,
                    label = {
                        Text("新增")
                    },
                )

                FilterChip(
                    selected = state.untaggedOnly,
                    onClick = vm::toggleUntaggedOnly,
                    label = {
                        Text("无标签")
                    },
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 全部标为已读
            // ============================================================
            TextButton(
                onClick = vm::markAllRead,
                enabled = !state.markingAllRead,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.markingAllRead) {
                        "标已读中…"
                    } else {
                        "全部标为已读"
                    }
                )
            }
        }
    }

    // ================================================================
    // 保存预设
    // ================================================================
    if (showSave) {

        AlertDialog(
            onDismissRequest = {
                showSave = false
            },

            title = {
                Text("保存筛选预设")
            },

            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = {
                        presetName = it
                    },
                    label = {
                        Text("预设名称")
                    },
                    singleLine = true,
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        vm.savePreset(presetName)
                        showSave = false
                    }
                ) {
                    Text("保存")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showSave = false
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
}

private fun viewModeLabel(v: String) =
    when (v) {
        "list" -> "列表"
        "compact" -> "紧凑网格"
        else -> "松散网格"
    }
