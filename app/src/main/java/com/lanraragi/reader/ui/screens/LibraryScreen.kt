package com.lanraragi.reader.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.SnackbarResult
import com.lanraragi.reader.data.catalog.SearchQueryCodec
import com.lanraragi.reader.data.SearchDiscoveryRepository
import com.lanraragi.reader.data.catalog.RoomLibraryLocalGateway
import com.lanraragi.reader.data.tags.knowledge.TagCandidate
import com.lanraragi.reader.data.tags.knowledge.TagCandidateBuilder
import com.lanraragi.reader.data.tags.knowledge.TagKnowledgeKey
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.ImageRequest
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.assets.CoverModel
import com.lanraragi.reader.data.assets.CoverState
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.FilterPreset
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.catalog.LibraryQuery
import com.lanraragi.reader.data.catalog.LibrarySort
import com.lanraragi.reader.data.catalog.LibrarySortResolver
import com.lanraragi.reader.data.catalog.SortDirection
import com.lanraragi.reader.data.catalog.LibrarySource
import com.lanraragi.reader.data.catalog.archiveActionTargets
import com.lanraragi.reader.data.catalog.isTankArchiveId
import com.lanraragi.reader.data.metadata.MetadataApplyResult
import com.lanraragi.reader.data.metadata.providers.MetadataCandidateInput
import com.lanraragi.reader.data.metadata.providers.NativeMetadataProviders
import com.lanraragi.reader.data.metadata.toMetadataSnapshot
import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow
import com.lanraragi.reader.ui.CategoryManagementSheet
import com.lanraragi.reader.ui.CategoryRefreshBus
import com.lanraragi.reader.ui.CoverChangeBus
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.TagFilterChip
import com.lanraragi.reader.ui.categoryDisplayName
import com.lanraragi.reader.ui.categoryNameError
import com.lanraragi.reader.ui.isProtectedCategoryName
import com.lanraragi.reader.ui.adaptive.AdaptiveLayout
import com.lanraragi.reader.ui.adaptive.AdaptiveLayoutHost
import com.lanraragi.reader.ui.adaptive.adaptiveLayout
import com.lanraragi.reader.ui.components.Segment
import com.lanraragi.reader.ui.components.SegmentedControl
import com.lanraragi.reader.ui.components.glass.LiquidGlassBar
import com.lanraragi.reader.ui.components.glass.LiquidGlassSearchBar
import com.lanraragi.reader.ui.library.AdaptiveLibraryDetailPane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.util.Collections
import java.util.UUID
import java.nio.charset.StandardCharsets

private val SORT_OPTIONS = listOf(
    "title" to "标题",
    "lastread" to "最近阅读",
    "date_added" to "添加日期",
    "artist" to "作者",
    "language" to "语言",
    "series" to "系列",
)

/**
 * 联想候选上限。
 *
 * 与 JHenTai 的 `limit: 100`、EhViewer 的 `.take(50)` 相比刻意收紧：这里一次算完但
 * **默认只展示 [LibrarySearchOverlay] 的前若干行**，多出来的由「更多」展开 ——
 * 键盘打开时首屏要留给真正可能被点到的候选。
 */
private const val SUGGESTION_LIMIT = 40

/**
 * 本地书架的标签频次（与「热门标签」同一口径）。
 *
 * 本地来源没有 `/api/database/stats`，只能从本地索引里自己数；粉丝册（TANK_）的
 * 成员标签不在这里展开，与服务端 `build_tag_stats` 的口径一致。
 */
private suspend fun localTagStats(container: AppContainer): List<TagStat> =
    RoomLibraryLocalGateway(container.readerDatabase).fetch().flatMap { it.tags.distinct() }
        .groupingBy { it }.eachCount().map { (full, count) ->
            TagStat(if (':' in full) full.substringBefore(':') else null, full.substringAfter(':'), count)
        }

// ================================================================
// D2 批量刮削参数
//
// 刮削来源 / 置信度阈值 / 跳过策略读取设置侧的 scrapeSource /
// scrapeConfidence / scrapeSkipTagged 字段（见 readScrapeOptions）；
// 本文件仅保留执行节奏常量。
// ================================================================
private const val SCRAPE_INTER_ITEM_DELAY_MS = 500L

/** 单本刮削结果：成功应用 / 跳过（无候选、置信度不足、已有标签等）/ 失败（网络或写回出错）。 */
private enum class BatchScrapeOutcome { APPLIED, SKIPPED, FAILED }

/**
 * 多选批量操作的「单行本已跳过」说明文案，拼进既有的 state.message（Snackbar）汇总里，
 * 不新增任何消息通道。count 为 0 时返回空串，普通档案的既有文案逐字不变。
 *
 * 单行本必须跳过：服务端的档案接口（metadata / delete / isnew / 分类 / download）
 * 只要 40 位 arcid，TANK_ id 打过去必然失败，操作前剔除比事后报错更清楚。
 */
private fun tankSkipNote(count: Int): String =
    if (count > 0) "，已跳过 $count 个单行本（不支持该操作）" else ""

data class LibraryState(
    val items: List<Archive> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val filter: String = "",
    val source: LibrarySource = LibrarySource.ALL,
    val serverScope: String = "",
    val warning: String? = null,
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
    val hideCompleted: Boolean = false,
    val columns: Int = 3,
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
    // 封面预取：滚动时预热即将进入视口的封面（窗口大小来自设置项 coverPrefetchCount）。
    val coverPrefetchCount: Int = 12,
    // 库请求协调器的查询代次：refresh/筛选/排序换代时递增，无限滚动追加时保持不变。
    // 用于预取去重集合换代清空。
    val queryGeneration: Long = 0L,
)

@OptIn(FlowPreview::class)
class LibraryViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val repository = container.repository
    private val query = MutableStateFlow("")
    private var categoryMutationInFlight = false

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private val _scrollToTop = MutableSharedFlow<Unit>()
    val scrollToTop = _scrollToTop.asSharedFlow()

    /**
     * 请求列表/网格回到顶部。
     *
     * 除了筛选变化（[refresh] 内部会调用）之外，底栏「首页」键**双击**也走这里：
     * MainScreen 通过同一 ViewModelStoreOwner 拿到的是同一个 VM 实例，
     * 因此不需要再额外加一条跨屏总线。
     */
    fun requestScrollToTop() {
        viewModelScope.launch { _scrollToTop.emit(Unit) }
    }

    /** Commit immediately; persistence failure cannot prevent a search. */
    fun submitSearch(text: String, historyScope: String = "") {
        val q = text.trim().trimEnd(',')
        if (q.isEmpty()) return
        val error = SearchQueryCodec.validationError(q)
        if (error != null) { _state.update { it.copy(message = error) }; return }
        if (_state.value.loading && _state.value.filter == q) return
        _state.update { it.copy(filter = q, selectedIds = emptySet(), items = emptyList(), total = null, loading = true, error = null) }
        refresh()
        viewModelScope.launch {
            try { container.searchHistoryRepository.add(q, historyScope) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(message = "搜索已提交，但历史保存失败") } }
        }
    }

    fun setSource(source: LibrarySource) {
        _state.update { it.copy(source = source, selectedIds = emptySet()) }
        refresh()
    }

    fun setServerScope(serverScope: String) {
        if (_state.value.serverScope == serverScope) return
        _state.update { it.copy(serverScope = serverScope, categoryId = "", selectedIds = emptySet()) }
        refresh()
    }

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
                    coverPrefetchCount = s.coverPrefetchCount,
                )
            }
        }

        // The coordinator is the sole owner of remote/local library request
        // lifecycle.  This legacy screen keeps its Archive-shaped state for
        // the existing card/batch UI, but never starts a second request path.
        viewModelScope.launch {
            container.libraryRequests.state.collect { request ->
                // 「隐藏读完」：服务端已按 hidecompleted 过滤远端结果，这里只兜底本地档案，
                // 口径必须与服务端一致——lib/LANraragi/Model/Search.pm 判定
                // pagecount > 0 && (progress / pagecount) > 0.85 即视为读完。
                val hideCompleted = _state.value.hideCompleted
                val visibleItems = request.items
                    .filter { entry ->
                        !hideCompleted || entry.source != LibrarySource.LOCAL || !(entry.pageCount > 0 && entry.progress.toFloat() / entry.pageCount > 0.85f)
                    }
                    .map { entry -> entry.toLegacyArchive() }

                _state.update {
                    it.copy(
                        items = visibleItems,
                        total = request.total,
                        loading = request.loading,
                        loadingMore = request.loadingMore,
                        hasMore = request.hasMore,
                        error = request.error?.message ?: request.error?.javaClass?.simpleName,
                        warning = request.warning,
                        queryGeneration = request.generation,
                    )
                }
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
            // drop(1)：StateFlow 会立刻重放当前值，若历史 tick > 0 就会在 init 的首次加载之外
            // 再发一次 refresh()，两者互相取消（后发的 refresh 会 cancel 前一发的 job），
            // 结果可能停在空列表。与同文件 CategoryRefreshBus.revision.drop(1) 口径一致。
            LibraryRefreshBus.tick.drop(1).collect { t ->
                if (t > 0) {
                    refresh()
                }
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

        // 搜索词变化立即上报筛选上下文（列表刷新仍走 350ms 防抖的 refresh）。
        publishFilterContext()

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
                hideCompleted = false,
            )
        }

        refresh()
    }

    fun refresh() {
        val s = _state.value
        publishFilterContext()
        container.libraryRequests.refresh(
            LibraryQuery(
                text = s.filter,
                tags = s.selectedTags.toSet(),
                categoryId = s.categoryId.ifBlank { null },
                source = s.source,
                serverScope = s.serverScope,
                newOnly = s.newOnly,
                untaggedOnly = s.untaggedOnly,
                hideCompleted = s.hideCompleted,
                sort = s.sortby.toLibrarySort(),
                direction = if (s.order.equals("desc", true)) SortDirection.DESC else SortDirection.ASC,
            ),
        )
        viewModelScope.launch { _scrollToTop.emit(Unit) }
    }

    fun loadMore() {
        val s = _state.value

        if (s.loading || s.loadingMore || !s.hasMore) {
            return
        }

        container.libraryRequests.loadMore()
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
                hideCompleted = preset.hideCompleted,
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
            hideCompleted = s.hideCompleted,
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

    fun toggleHideCompleted() {
        _state.update {
            it.copy(hideCompleted = !it.hideCompleted)
        }

        refresh()
    }

    /**
     * 把当前筛选上下文（分类/搜索词/仅新/未标记/隐藏读完）上报到
     * FilterContextBus，供主壳随机抽屉等跨屏消费者复用同一套条件。
     * 所有筛选变化路径最终都会经过 refresh()（或 onQueryChange），
     * 因此在这里统一上报即可覆盖全部入口。
     */
    private fun publishFilterContext() {
        val s = _state.value
        FilterContextBus.update(
            LibraryFilterContext(
                category = s.categoryId.ifBlank { null },
                filter = s.filter.ifBlank { null },
                newOnly = s.newOnly,
                untaggedOnly = s.untaggedOnly,
                hideCompleted = s.hideCompleted,
                tags = s.selectedTags,
            ),
        )
    }

    /**
     * A11 全库清除「New」标记：单次服务器调用 `clearAllNew()`
     * （契约 clearNewAll，`DELETE /api/database/isnew`）。
     *
     * 原实现是逐条 `clearArchiveNew(id)`，只覆盖 `items` 里当前已加载的档案，
     * 用户没滚动到的分页会一直保留 New 标记，而且付出 N 次 HTTP 请求的代价。
     * 服务器全库调用没有这两个问题，因此全局入口统一走它。
     * 多选态的「标已读」是另一个动作（只清所选档案），仍走逐条接口。
     */
    fun markAllRead() {
        if (_state.value.markingAllRead) return
        viewModelScope.launch {
            _state.update { it.copy(markingAllRead = true) }
            val ok = try {
                repository.clearAllNew()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) {
                LibraryRefreshBus.tick.value++
            }
            _state.update {
                it.copy(
                    message = if (ok) {
                        "已清除全库的新标记"
                    } else {
                        "清除新标记失败，请检查网络或服务器"
                    },
                    markingAllRead = false,
                )
            }
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
     *
     * 单行本（TANK_ 前缀）一律跳过：收藏集合虽然只存在本地，但后续「收藏列表 → 打开档案」
     * 的一切入口都按 40 位 arcid 语义使用该集合，单行本应走单行本通道，不混入档案收藏。
     * 跳过的数量经既有 state.message（Snackbar）说明，不新增消息通道。
     */
    fun batchFavorite() {
        if (_state.value.batchBusy) return
        val selected = _state.value.selectedIds.toList()
        val skippedTanks = selected.count(::isTankArchiveId)
        val ids = selected.filterNot(::isTankArchiveId)
        if (ids.isEmpty()) {
            if (skippedTanks > 0) {
                _state.update { it.copy(message = "没有可收藏的档案" + tankSkipNote(skippedTanks)) }
            }
            return
        }
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
            val msg = (if (fail > 0) "$verb $ok 本，失败 $fail 本" else "$verb $ok 本") +
                tankSkipNote(skippedTanks)
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

    /** D2 批量缓存整本：逐项入 B1 下载队列（本地档案与单行本跳过）。 */
    fun batchCache() {
        if (_state.value.batchBusy) return
        val selected = _state.value.selectedIds.toList()
        if (selected.isEmpty()) return
        // 只剔除单行本：local_ 的「跳过」计数保持原有语义（在下面的循环里统计）。
        val skippedTanks = selected.count(::isTankArchiveId)
        val ids = selected.filterNot(::isTankArchiveId)
        if (ids.isEmpty()) {
            _state.update { it.copy(message = "所选档案无法缓存" + tankSkipNote(skippedTanks)) }
            return
        }
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
                (if (enqueued > 0 && skipped > 0) {
                    "已将 $enqueued 本加入缓存队列，$skipped 本跳过"
                } else if (enqueued > 0) {
                    "已将 $enqueued 本加入缓存队列"
                } else {
                    "所选档案无法缓存"
                }) + tankSkipNote(skippedTanks)
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

    /** D2 批量清除"新"标记（本地档案与单行本跳过），完成后刷新图库。 */
    fun batchClearNew() {
        if (_state.value.batchBusy) return
        val targets = archiveActionTargets(_state.value.selectedIds)
        val ids = targets.ids
        if (ids.isEmpty()) {
            _state.update {
                it.copy(message = "没有可操作的档案" + tankSkipNote(targets.skippedTankCount))
            }
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
            val msg = (if (fail > 0) "已清除 $ok 本的新标记，失败 $fail 本" else "已清除 $ok 本的新标记") +
                tankSkipNote(targets.skippedTankCount)
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

    /**
     * D2 批量标记"未读"（本地档案与单行本跳过）：逐项调用 setArchiveNew，
     * 完成后经 LibraryRefreshBus 刷新图库并给出汇总消息。
     */
    fun batchMarkUnread() {
        if (_state.value.batchBusy) return
        val targets = archiveActionTargets(_state.value.selectedIds)
        val ids = targets.ids
        if (ids.isEmpty()) {
            _state.update {
                it.copy(message = "没有可标记未读的档案" + tankSkipNote(targets.skippedTankCount))
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(batchBusy = true, batchNote = "标未读处理中…")
            }
            var ok = 0
            var fail = 0
            ids.forEachIndexed { index, arcid ->
                try {
                    repository.setArchiveNew(arcid)
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail++
                }
                _state.update {
                    it.copy(batchNote = "标未读处理中 ${index + 1}/${ids.size}")
                }
            }
            LibraryRefreshBus.tick.value++
            val msg = (if (fail > 0) "已标记 $ok 本为未读，失败 $fail 本" else "已标记 $ok 本为未读") +
                tankSkipNote(targets.skippedTankCount)
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

    /** D2 批量删除：逐项 DELETE，汇总成功 / 失败，完成后刷新图库（单行本跳过）。 */
    fun batchDelete() {
        if (_state.value.batchBusy) return
        val targets = archiveActionTargets(_state.value.selectedIds)
        val ids = targets.ids
        if (ids.isEmpty()) {
            _state.update {
                it.copy(message = "没有可删除的档案" + tankSkipNote(targets.skippedTankCount))
            }
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
            val msg = (if (fail > 0) "已删除 $ok 本，失败 $fail 本" else "已删除 $ok 本") +
                tankSkipNote(targets.skippedTankCount)
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

    /** D2 批量加入分类（本地档案与单行本跳过）。 */
    fun batchAddToCategory(categoryId: String) {
        if (categoryId.isBlank() || _state.value.batchBusy) return
        val targets = archiveActionTargets(_state.value.selectedIds)
        val ids = targets.ids
        if (ids.isEmpty()) {
            _state.update {
                it.copy(message = "没有可加入分类的档案" + tankSkipNote(targets.skippedTankCount))
            }
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
            val msg = (if (fail > 0) "已将 $ok 本加入分类，失败 $fail 本" else "已将 $ok 本加入分类") +
                tankSkipNote(targets.skippedTankCount)
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

    // ================================================================
    // D2 批量刮削：逐本取最佳指定来源精确候选（设置侧 scrapeSource，默认 EH）→
    // native fetch → 标题置信度达标（设置侧 scrapeConfidence）自动应用，不达标跳过并计数。
    // ================================================================

    private var batchScrapeJob: Job? = null

    /**
     * 批量刮削（仅服务器档案；local_ 与单行本直接跳过）：
     * viewModelScope 顺序队列逐本执行，间隔 500ms 降低对服务器与刮削源的压力；
     * 进度经 batchNote 展示，可经 cancelBatchScrape 取消（Job cancel）；
     * 完成后经 LibraryRefreshBus 刷新图库并弹出汇总 Snackbar。
     */
    fun batchScrape() {
        if (_state.value.batchBusy) return
        val selected = _state.value.selectedIds.toList()
        if (selected.isEmpty()) return
        val archivesById = _state.value.items.associateBy { it.arcid }
        // 单行本不能刮削：刮削写回走的是以 40 位 arcid 为键的元数据仓储
        // （ArchiveIdentity.Remote + /api/archives/{id}/metadata），TANK_ id 必然失败。
        val skippedTanks = selected.count(::isTankArchiveId)
        val serverIds = selected.filter { !it.startsWith("local_") && !isTankArchiveId(it) }
        if (serverIds.isEmpty()) {
            _state.update {
                it.copy(
                    message = "没有可刮削的档案（批量刮削仅支持服务器档案）" + tankSkipNote(skippedTanks),
                )
            }
            return
        }
        batchScrapeJob = viewModelScope.launch {
            _state.update { it.copy(batchBusy = true, batchNote = "刮削准备中…") }
            val scrapeOptions = readScrapeOptions()
            var applied = 0
            // local_ 档案不参与刮削，按“跳过”计数；单行本不进 serverIds，另行在汇总文案里说明。
            var skipped = selected.count { it.startsWith("local_") }
            var failed = 0
            try {
                serverIds.forEachIndexed { index, arcid ->
                    val archive = archivesById[arcid]
                    val outcome = if (archive == null) {
                        BatchScrapeOutcome.SKIPPED
                    } else {
                        try {
                            scrapeArchiveWithNativeProvider(
                                archive,
                                scrapeOptions.confidenceThreshold,
                                scrapeOptions.skipTagged,
                                scrapeOptions.source,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            BatchScrapeOutcome.FAILED
                        }
                    }
                    when (outcome) {
                        BatchScrapeOutcome.APPLIED -> applied++
                        BatchScrapeOutcome.SKIPPED -> skipped++
                        BatchScrapeOutcome.FAILED -> failed++
                    }
                    _state.update {
                        it.copy(
                            batchNote =
                                "刮削中 ${index + 1}/${serverIds.size} · 成功 $applied · 跳过 $skipped · 失败 $failed",
                        )
                    }
                    if (index < serverIds.lastIndex) delay(SCRAPE_INTER_ITEM_DELAY_MS)
                }
                LibraryRefreshBus.tick.value++
                _state.update {
                    it.copy(
                        message = "批量刮削完成：成功 $applied · 跳过 $skipped · 失败 $failed" +
                            tankSkipNote(skippedTanks),
                        selectedIds = emptySet(),
                        batchBusy = false,
                        batchNote = null,
                    )
                }
            } catch (e: CancellationException) {
                // 保留已完成部分：刷新图库并给出取消时的累计汇总。
                LibraryRefreshBus.tick.value++
                _state.update {
                    it.copy(
                        message = "批量刮削已取消：成功 $applied · 跳过 $skipped · 失败 $failed" +
                            tankSkipNote(skippedTanks),
                        selectedIds = emptySet(),
                        batchBusy = false,
                        batchNote = null,
                    )
                }
                throw e
            } finally {
                batchScrapeJob = null
            }
        }
    }

    /** 取消批量刮削：已应用的条目保留，取消后给出累计汇总并刷新图库。 */
    fun cancelBatchScrape() {
        batchScrapeJob?.cancel()
    }

    /**
     * 刮削参数；confidenceThreshold 为 0..1 比例（设置侧 scrapeConfidence 为 0..100 整数百分比），
     * source 为设置侧 scrapeSource（ehentai / nhentai / server_plugin）。
     */
    private data class ScrapeOptions(
        val confidenceThreshold: Float,
        val skipTagged: Boolean,
        val source: String = "ehentai",
    )

    private suspend fun readScrapeOptions(): ScrapeOptions {
        val s = container.settingsRepository.settings.first()
        return ScrapeOptions(
            confidenceThreshold = s.scrapeConfidence.coerceIn(0, 100) / 100f,
            skipTagged = s.scrapeSkipTagged,
            source = s.scrapeSource,
        )
    }

    /**
     * 单本刮削：
     * 1. 以档案标题 + 已有 source 标签交给现有离线候选解析器，取置信度最高的
     *    [source] 指定来源（ehentai / nhentai；server_plugin 无原生实现，回落
     *    E-Hentai）精确候选（完整链接或 gid/token，可被 native fetch 抓取）；
     * 2. 已有待应用修改 / 已有标签（skipTagged）/ 无精确候选 → 跳过；
     * 3. native fetch 拉取完整元数据，标题相似度（精确 / 包含 / 编辑距离长度比）
     *    达到阈值才自动应用，否则跳过并计数；
     * 4. 写回走与详情页工作台一致的 stagePatch → applyPending 路径
     *    （providerMetadataApplyPolicy：保留用户覆盖与用户标签）。
     */
    private suspend fun scrapeArchiveWithNativeProvider(
        archive: Archive,
        confidenceThreshold: Float,
        skipTagged: Boolean,
        source: String,
    ): BatchScrapeOutcome {
        val target = ArchiveIdentity.Remote(archive.arcid, ApiClient.config.baseUrl)
        val current = container.metadataRepository.observe(target).value
        if (current.hasPendingPatch) {
            // 该档案存在未保存的元数据修改，避免批量操作覆盖用户审阅中的内容。
            return BatchScrapeOutcome.SKIPPED
        }
        val snapshot = current.latest ?: archive.toMetadataSnapshot()
        if (skipTagged && snapshot.tags.isNotEmpty()) {
            return BatchScrapeOutcome.SKIPPED
        }
        // 原生抓取仅支持 ehentai / nhentai（HttpNativeMetadataGateway 白名单）；
        // server_plugin 走服务器插件尚未实现，先回落 E-Hentai。
        val providerId = if (source == "nhentai") "nhentai" else "ehentai"
        val exact = NativeMetadataProviders.all
            .filter { it.providerId == providerId }
            .flatMap {
                it.findCandidates(
                    MetadataCandidateInput(
                        sourceUrls = listOfNotNull(snapshot.sourceUrl),
                        existingTags = snapshot.tags,
                        archiveTitle = archive.title,
                        fileName = archive.title,
                    ),
                )
            }
            .filter { it.sourceId != null && it.sourceUrl != null }
            .maxByOrNull { it.confidence }
            ?: return BatchScrapeOutcome.SKIPPED
        val sourceId = exact.sourceId ?: return BatchScrapeOutcome.SKIPPED
        val sourceUrl = exact.sourceUrl ?: return BatchScrapeOutcome.SKIPPED
        val fetched = container.nativeMetadataFetch.fetch(providerId, sourceId, sourceUrl)
        val fetchedTitle = fetched.title?.trim().orEmpty()
        if (fetchedTitle.isEmpty()) {
            return BatchScrapeOutcome.SKIPPED
        }
        val confidence = batchScrapeTitleConfidence(fetchedTitle, archive.title)
        if (confidence < confidenceThreshold) {
            return BatchScrapeOutcome.SKIPPED
        }
        container.metadataRepository.stagePatch(target, snapshot, fetched.toPatch())
        return when (container.metadataRepository.applyPending(target, providerMetadataApplyPolicy())) {
            is MetadataApplyResult.Applied -> BatchScrapeOutcome.APPLIED
            is MetadataApplyResult.Blocked,
            is MetadataApplyResult.NoPending,
            is MetadataApplyResult.Pending,
            -> BatchScrapeOutcome.FAILED
        }
    }

    /** 批量刮削的标题置信度：精确相等 > 互相包含 > 编辑距离长度比。 */
    private fun batchScrapeTitleConfidence(fetched: String, local: String): Float {
        val a = fetched.trim().lowercase()
        val b = local.trim().lowercase()
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        if (a.contains(b) || b.contains(a)) return 0.95f
        val distance = levenshteinDistance(a, b)
        return 1f - distance.toFloat() / maxOf(a.length, b.length).coerceAtLeast(1)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            for (j in b.indices) {
                cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
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

    private fun String.toLibrarySort(): LibrarySort = when (lowercase()) {
        "lastread" -> LibrarySort.LAST_READ
        "date_added" -> LibrarySort.DATE_ADDED
        "artist" -> LibrarySort.ARTIST
        "language" -> LibrarySort.LANGUAGE
        "series" -> LibrarySort.SERIES
        else -> LibrarySort.TITLE
    }

    private fun com.lanraragi.reader.data.catalog.LibraryEntry.toLegacyArchive(): Archive = when (val sourceIdentity = identity) {
        is ArchiveIdentity.Remote -> Archive(
            arcid = sourceIdentity.arcid,
            title = title,
            tags = tags.joinToString(","),
            isnew = if (isNew) "true" else "false",
            pagecount = pageCount,
            progress = progress,
            // LibraryEntry 统一用 epoch 毫秒，而 Archive.dateadded 与卡片渲染沿用「秒」的口径
            // （ArchiveCard 里是 dateadded * 1000），这里做一次换算。
            dateadded = dateAdded / 1000L,
            summary = summary,
            category = categoryId.orEmpty(),
        )
        is ArchiveIdentity.LocalSaf -> Archive(
            arcid = "local_${UUID.nameUUIDFromBytes(sourceIdentity.uri.toByteArray(StandardCharsets.UTF_8))}",
            title = title,
            tags = tags.joinToString(","),
            pagecount = pageCount,
            // 本地档案的「添加日期」取索引里的最后校验时间（毫秒 → 秒，与卡片口径一致）。
            dateadded = dateAdded / 1000L,
            summary = localUri ?: sourceIdentity.uri,
        )
        // 单行本：arcid 是 TANK_xxxxxxxxxx（不是 40 位 arcid 的档案），
        // archive_count 是成员档案数量（= 卷数），卡片据此渲染「单行本 · N 卷」角标。
        // 注意 pagecount 对单行本是成员页数之和，不是卷数。
        // 进度 / 新标记 / 添加日期与普通档案同源（服务端 build_tank_json 同样输出
        // progress / isnew，date_added 则在统一标签里），因此卡片上的进度条、「新」角标
        // 与日期行对单行本一致生效。
        is ArchiveIdentity.Tankoubon -> Archive(
            arcid = sourceIdentity.tankId,
            title = title,
            tags = tags.joinToString(","),
            isnew = if (isNew) "true" else "false",
            pagecount = pageCount,
            progress = progress,
            dateadded = dateAdded / 1000L,
            summary = summary,
            archive_count = volumeCount,
        )
    }

    override fun onCleared() {
        // 刻意**不**调用 container.libraryRequests.cancel()：
        // libraryRequests 是 AppContainer 级（进程级）单例，而本 ViewModel 是页面级的。
        // 页面被回收时取消一个共享请求，会让协调器停在 loading=false + items 为空 + error=null，
        // UI 就渲染成「没有找到档案」且永不重试（保存服务器后进入主界面、重建导航图时最易触发）。
        // 旧请求的生命周期由 refresh() 自己接管（每次 refresh 都会取消上一个 job）。
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    navController: NavController,
) {
    val vm: LibraryViewModel =
        viewModel {
            LibraryViewModel(container)
        }

    val state by vm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // D5 空态入口：「上传到服务器」直链下载对话框
    var showUrlImport by remember { mutableStateOf(false) }
    var urlImportText by remember { mutableStateOf("") }

    val backdrop = rememberLayerBackdrop()

    val searchSession = rememberSaveable(saver = SearchSessionState.Saver) { SearchSessionState() }
    val searchTransition = remember { MutableTransitionState(false) }
    searchTransition.targetState = searchSession.expanded
    val searchExpanded = searchTransition.currentState || searchTransition.targetState
    /**
     * 展开动画**真正结束**之后才为 true。
     *
     * 用它（而不是 [searchExpanded]）决定底层图库是否挂载：圆形揭示期间圆外仍然是图库，
     * 底层必须先留着，否则展开过程中会被一块空白顶掉；展开结束后再卸载，
     * 保证开合期间始终只有一个活跃列表（两个 LazyGrid 共用同一份 gridState 会打架）。
     */
    var searchFullyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searchSession.expanded) {
        if (searchSession.expanded) {
            delay(SEARCH_REVEAL_MS.toLong())
            searchFullyOpen = true
        } else {
            searchFullyOpen = false
        }
    }
    // 图库页上的清除（胶囊的 ×、空结果的「清除搜索条件」）会直接改 state.filter；
    // 同步回会话的「已提交查询」，否则下次打开搜索面会拿旧查询当草稿。
    LaunchedEffect(state.filter) {
        if (!searchSession.expanded && state.filter != searchSession.committed) searchSession.committed = state.filter
    }
    val historyRepository = container.searchHistoryRepository
    val serverScope by remember(container) {
        container.settingsRepository.settings.map { (it.profiles.getOrNull(it.activeProfileIndex)?.url ?: it.baseUrl).trim().trimEnd('/') }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = ApiClient.config.baseUrl)
    val localDiscovery = state.source == LibrarySource.LOCAL || serverScope.isBlank()
    val historyScope = if (localDiscovery) "local" else "server:$serverScope"
    val overlayHistory by remember(historyScope) { historyRepository.history(historyScope) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val legacyHistory by historyRepository.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyHidden by historyRepository.hidden.collectAsStateWithLifecycle(initialValue = false)
    val historyPaused by historyRepository.paused.collectAsStateWithLifecycle(initialValue = false)
    var overlaySuggestions by remember(historyScope) { mutableStateOf<List<TagCandidate>>(emptyList()) }
    var suggestionsLoading by remember { mutableStateOf(false) }
    var suggestionsError by remember { mutableStateOf<String?>(null) }
    var overlayHotTags by remember(historyScope) { mutableStateOf<List<TagStat>>(emptyList()) }
    var hotLoading by remember(historyScope) { mutableStateOf(false) }
    var hotError by remember(historyScope) { mutableStateOf<String?>(null) }
    var hotUpdated by remember(historyScope) { mutableStateOf<String?>(null) }
    var hotRevision by remember { mutableStateOf(0) }
    /**
     * 服务器上每个命名空间的标签条数（键小写），用来判断「排序字段在当前库上到底有没有生效」。
     *
     * 服务端把 `sortby` 当成字面正则去标签串里匹配命名空间（LANraragi 0.9.81
     * `Model/Search.pm:576,595`）：库里没有该命名空间时既不报错也不退回上一个顺序，
     * 只是把全部条目按 Redis 索引的任意顺序返回。详见 [LibrarySortResolver]。
     * null 表示尚未取到统计，此时不阻断任何排序选项。
     */
    var namespaceCounts by remember(historyScope) { mutableStateOf<Map<String, Int>?>(null) }
    val libraryRevision by LibraryRefreshBus.tick.collectAsStateWithLifecycle()
    var previousServer by rememberSaveable { mutableStateOf(serverScope) }
    LaunchedEffect(serverScope) {
        if (previousServer != serverScope) {
            searchSession.phase = SearchPhase.CLOSED
            searchSession.committed = ""
            searchSession.draft = TextFieldValue()
            previousServer = serverScope
        }
        vm.setServerScope(serverScope)
    }
    LaunchedEffect(searchExpanded, historyScope, hotRevision, libraryRevision) {
        if (!searchExpanded) return@LaunchedEffect
        hotLoading = true
        hotError = null
        try {
            val repository = container.searchDiscoveryRepository
            val cached = repository.cached(historyScope)
            cached?.let { overlayHotTags = it.tags }
            val snapshot = if (cached != null && !localDiscovery && hotRevision == 0 && libraryRevision == 0 &&
                System.currentTimeMillis() - cached.updatedAt < SearchDiscoveryRepository.TTL) cached else {
                val tags = if (localDiscovery) localTagStats(container) else container.repository.getTags()
                repository.save(historyScope, tags)
            }
            namespaceCounts = snapshot.namespaces
            overlayHotTags = snapshot.tags
            hotUpdated = "更新于 " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(snapshot.updatedAt))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { hotError = if (overlayHotTags.isEmpty()) "标签暂不可用：${error.message}" else "正在显示缓存，刷新失败：${error.message}"
        } finally { hotLoading = false }
    }
    val draft = searchSession.draft
    LaunchedEffect(searchSession.phase, draft, historyScope, overlayHotTags) {
        overlaySuggestions = emptyList()
        suggestionsError = null
        suggestionsLoading = false
        if (!searchExpanded || draft.composition != null) return@LaunchedEffect
        val token = SearchQueryCodec.active(draft.text, draft.selection.start).value
        if (token.isBlank()) return@LaunchedEffect
        suggestionsLoading = true
        delay(220)
        try {
            val personal = overlayHistory.flatMap { SearchQueryCodec.parse(it) }.groupingBy {
                TagKnowledgeKey(if (':' in it.value) it.value.substringBefore(':') else "", it.value.substringAfter(':'))
            }.eachCount().mapValues { it.value.toLong() }
            // 词库按去尾冒号的写法查（`language:` 要能列出该命名空间下的标签），
            // 但合并排序交给 TagCandidateBuilder，用完整 token 才能识别命名空间限定。
            val known = container.tagKnowledgeRepository.suggestions(token.removeSuffix(":"), personalFrequency = personal, limit = SUGGESTION_LIMIT)
            overlaySuggestions = TagCandidateBuilder.build(
                token = token,
                dictionary = known,
                libraryTags = overlayHotTags,
                limit = SUGGESTION_LIMIT,
            )
            if (known.isEmpty() && container.tagKnowledgeRepository.current() == null) suggestionsError = "尚未安装标签词库；可用库内标签或直接搜索"
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { suggestionsError = "标签联想暂不可用：${error.message}"
        } finally { suggestionsLoading = false }
    }
    /**
     * 提交搜索：**搜索面直接收起**，结果显示在图库页本身。
     *
     * 不再把结果内嵌在展开层里：真机上看下来那一层与收起后返回的首页是同一套
     * LibraryResultsContent、同一份滚动位置、同一批操作，等于把同一个列表在两层里
     * 各挂一次，多出来的只有一层动画和「哪一层在响应手势」的歧义。
     */
    fun submitDraft(value: String = searchSession.draft.text) {
        if (searchSession.draft.composition != null) return
        val q = value.trim().trimEnd(',')
        if (q.isBlank()) return
        val error = SearchQueryCodec.validationError(q)
        if (error != null) { searchSession.error = error; return }
        vm.submitSearch(q, historyScope)
        searchSession.accept(q)
    }

    var showFilter by remember {
        mutableStateOf(false)
    }

    // 面板要回答「这个排序字段在本库能不能生效」，所以打开时补齐一次命名空间分布。
    // 已有缓存（搜索面板开过一次）就直接用，不重复请求；取不到时不阻断任何选项。
    LaunchedEffect(showFilter, historyScope) {
        if (!showFilter) return@LaunchedEffect
        val repository = container.searchDiscoveryRepository
        repository.namespaceCounts(historyScope)?.let {
            namespaceCounts = it
            return@LaunchedEffect
        }
        runCatching {
            val tags = if (localDiscovery) localTagStats(container) else container.repository.getTags()
            namespaceCounts = repository.save(historyScope, tags).namespaces
        }
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

    /*
     * 悬浮顶栏几何（顶栏与底栏现在同一套悬浮关系）：
     * 顶栏不再用 Scaffold 的 topBar 预留整块空间，而是浮在内容之上，
     * 画廊从胶囊下方穿过（玻璃因此能采样到卡片），只靠列表的 contentPadding
     * 保证「首屏第一行不被挡住」。
     *
     * 数值必须与顶栏自身的写法对上（见下方 `.statusBarsPadding().padding(vertical = topBarVerticalPadding)`
     * 与 `LiquidGlassSearchBar(height = topBarCapsuleHeight)`），改一处这里跟着改。
     */
    val topBarCapsuleHeight = 48.dp
    val topBarVerticalPadding = 6.dp
    val topBarToContentGap = 8.dp
    val topBarClearance = topBarVerticalPadding + topBarCapsuleHeight + topBarToContentGap

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val adaptiveLayout = adaptiveLayout(LocalConfiguration.current.screenWidthDp.dp)
    var detailSelection by rememberSaveable { mutableStateOf<String?>(null) }

    /*
     * 顶栏**不随滚动隐藏**。
     *
     * 曾经移植过 EhViewer 的 NestedScrollConnection 上滑隐藏（`GalleryListScreen.kt:511-526`），
     * 真机用下来不合适：库页顶栏是「搜索入口 + 排序筛选入口」两个主动作，
     * 藏起来之后想搜索必须先往回滚一点，多一步无谓操作；
     * 而且位移上限要越过状态栏才能真正藏住，滑到一半停住时半截胶囊更难看。
     * 顶栏常驻，让画廊从它下方穿过即可。
     *
     * 搜索面的展开原点（用于「以搜索栏为中心展开覆盖全屏」）在这里量取。
     */
    var searchBarOrigin by remember { mutableStateOf(Offset.Zero) }
    var searchBarOriginReady by remember { mutableStateOf(false) }

    // Keep a selection keyed by archive id across refreshes/recomposition. If the
    // selected row disappears, clear it rather than showing stale details.
    LaunchedEffect(state.items, adaptiveLayout) {
        if (detailSelection != null && state.items.none { it.arcid == detailSelection }) {
            detailSelection = null
        }
    }

    // ================================================================
    // 卡片 / 行单击的共用入口（列表视图与网格视图共用同一份判定）：
    // 平板主从布局先在右侧详情面板选中，其余情况进路由。
    // 单行本（TANK_xxxxxxxxxx）必须走 Routes.tankReader：
    // 它的 arcid 不是 40 位档案 id，详情页 / 阅读器都会打到
    // /api/archives/{id}/metadata 这类只收 40 位 arcid 的接口上而失败。
    // ================================================================
    val openLibraryEntry: (Archive) -> Unit = { archive ->
        if (adaptiveLayout == AdaptiveLayout.TABLET_MASTER_DETAIL && !searchExpanded) {
            detailSelection = archive.arcid
        } else if (isTankArchiveId(archive.arcid)) {
            navController.navigate(Routes.tankReader(archive.arcid))
        } else {
            navController.navigate(Routes.detail(archive.arcid))
        }
    }

    LaunchedEffect(vm, state.viewMode) {
        vm.scrollToTop.collect {
            if (state.viewMode == "list") {
                listState.animateScrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
    }

    // ================================================================
    // 封面预取（设置项 coverPrefetchCount）：网格 / 紧凑网格 / 列表滚动时，
    // 把 [最后可见项 + 1, + coverPrefetchCount] 窗口内、已加载条目的封面
    // 按显示组件同一构造预热进 Coil 缓存（见 enqueueCoverPrefetch）。
    // 已预取集合按查询代次（queryGeneration）换代：刷新 / 筛选 / 排序换代即清空，
    // 无限滚动追加沿用同一代不清空（追加只处理新进入窗口的尾部条目）。
    // ================================================================
    if (state.coverPrefetchCount > 0 && state.items.isNotEmpty()) {
        val prefetchedArcids = remember(state.queryGeneration) {
            mutableSetOf<String>()
        }

        LaunchedEffect(
            state.queryGeneration,
            state.items,
            state.viewMode,
            state.coverPrefetchCount,
        ) {
            val lastVisibleIndexFlow =
                if (state.viewMode == "list") {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo
                            .lastOrNull()?.index ?: -1
                    }
                } else {
                    snapshotFlow {
                        gridState.layoutInfo.visibleItemsInfo
                            .lastOrNull()?.index ?: -1
                    }
                }

            lastVisibleIndexFlow
                .distinctUntilChanged()
                .collectLatest { lastVisible ->
                    if (lastVisible < 0) return@collectLatest

                    // 低优先级：Coil 2.7 已移除按请求优先级（Priority）API，
                    // 这里用「滚动停顿后入队」替代——collectLatest 会丢弃被新
                    // 滚动事件取代的窗口，落定 250ms 后才预热，预取不与
                    // 可见封面在 Coil 执行队列里竞争。
                    delay(250)

                    // 预取窗口右端截断到已加载末尾：超出部分等 loadMore 后自然覆盖
                    val windowEnd = minOf(
                        lastVisible + state.coverPrefetchCount,
                        state.items.lastIndex,
                    )

                    for (index in (lastVisible + 1)..windowEnd) {
                        val archive = state.items.getOrNull(index) ?: break
                        if (archive.arcid in prefetchedArcids) continue

                        // 失败静默：任何异常都视为已处理，不影响 UI，也不反复重试。
                        // 单行本与普通档案都走这里；URL 的差异（tank 缩略图端点）由
                        // enqueueCoverPrefetch 内部按 arcid 判定，保证与卡片展示同源。
                        val handled = runCatching {
                            enqueueCoverPrefetch(context, container, archive.arcid)
                        }.getOrDefault(true)

                        if (handled) {
                            prefetchedArcids.add(archive.arcid)
                        }
                    }
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
    // 搜索展开层同理：展开期间也要把底栏收起来，否则玻璃底栏会浮在展开层之上还能被误触
    // （EhViewer 的展开态是全屏的）。两个条件取或，保持单一写入点。
    LaunchedEffect(state.selectedIds.isNotEmpty(), searchExpanded) {
        SelectionModeBus.active.value = state.selectedIds.isNotEmpty() || searchExpanded
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
                    // 只预留状态栏高度：顶栏本身是浮在内容之上的（与底栏同一套悬浮关系），
                    // 画廊内容可以从胶囊下方穿过，玻璃因此能采样到卡片。
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    )
                },
                snackbarHost = { if (!searchExpanded) SnackbarHost(snackbarHostState) },
            ) { padding ->

            if (!searchFullyOpen) {
                AdaptiveLayoutHost(
                    layout = adaptiveLayout,
                    master = {
                        LibraryResultsContent(state, vm, container, listState, gridState, openLibraryEntry,
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topBarClearance, bottom = listBottomPad),
                            // 空结果时直接回到搜索面改条件；结果本来就在这一页上，
                            // 不需要再切一层「结果页」。
                            onEdit = { searchSession.open(state.filter) },
                            onClearSearch = { vm.clearQuery(); searchSession.open("") },
                            extraEmptyActions = {
                                TextButton(onClick = { MainTabBus.requestDownloadTab(); DownloadSubTabBus.local.value = true }) { Text("扫描本地文件") }
                                TextButton(onClick = { urlImportText = ""; showUrlImport = true }) { Text("上传到服务器") }
                                TextButton(onClick = { ReaderDrawerBus.requestRandom() }) { Text("随机一本") }
                            })
                    },
                            detail = {
                                val selected = state.items.firstOrNull { it.arcid == detailSelection }
                                if (selected == null) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("选择一个档案查看详情", style = MaterialTheme.typography.bodyLarge)
                                    }
                                } else {
                                    // 平板主从布局的右侧面板同样要区分单行本：
                                    // 「打开详情」与「开始阅读」对 TANK_ id 都只能进单行本阅读器，
                                    // 否则会打开详情页并请求只收 40 位 arcid 的 metadata 接口。
                                    val isTank = isTankArchiveId(selected.arcid)
                                    AdaptiveLibraryDetailPane(
                                        archive = selected,
                                        onOpenDetail = {
                                            if (isTank) {
                                                navController.navigate(Routes.tankReader(selected.arcid))
                                            } else {
                                                navController.navigate(Routes.detail(selected.arcid))
                                            }
                                        },
                                        onRead = {
                                            if (isTank) {
                                                navController.navigate(Routes.tankReader(selected.arcid))
                                            } else {
                                                navController.navigate(Routes.reader(selected.arcid))
                                            }
                                        },
                                    )
                                }
                            },
                )
            }
        }
        }

        // ================================================================
        // 浮动顶栏（兄弟节点）：多选模式下显示选择操作栏，
        // 否则排序按钮 + 搜索框（从背景采样层取景）
        // ================================================================
        if (!searchExpanded) {
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
                    vertical = topBarVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            // One 48dp glass shell: 搜索区 + 最右侧一个「排序与筛选」键。
            LiquidGlassSearchBar(
                value = state.filter,
                // 只读模式下组件不会产生输入变更：value 只会被置空，
                // 因此这里把「值变为空串」落到 vm.clearQuery()。
                onValueChange = {
                    vm.clearQuery()
                },
                onClick = {
                    searchSession.open(state.filter)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        // 量取这条胶囊在根坐标里的中心：搜索面「以搜索栏为中心展开覆盖全屏」
                        // 的圆心就是它。顶栏常驻，所以开合前后这个值都是有效的。
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            searchBarOrigin = Offset(bounds.center.x, bounds.center.y)
                            searchBarOriginReady = true
                        },
                backdrop = backdrop,
                hint = "搜索",
                readOnly = true,
                capsuleClickable = true,
                clearContentDescription = "清除搜索词",
                // 结果就在图库页上，所以数量也放在这条胶囊里；不额外占一行。
                // 总数未知（请求中 / 服务器没给）时不显示，避免先亮出上一轮的旧数字。
                trailingLabel =
                    state.total?.takeIf { state.filter.isNotBlank() }?.let { "共 $it 项" },
                height = topBarCapsuleHeight,
                trailing = {
                    // 排序 / 视图 / 筛选合并成一个键：它们都是「怎么看这个库」的同一次决策，
                    // 拆成两个键时用户为了换个列数要先越过一整屏筛选条件。
                    // 放在最右侧（主操作侧），图标沿用「排序与视图」的 Tune。
                    // 按压反馈与底栏一致：`indication = null`，不要 Material 的灰色衬底
                    // （MainScreen.kt:3293 的每个底栏键都是这个写法）。
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(topBarCapsuleHeight)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                showFilter = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "排序与筛选",
                            modifier = Modifier.size(20.dp),
                        )
                        // 条件圆点：有生效中的筛选 / 非默认范围时提醒一次。
                        if (state.categoryId.isNotBlank() || state.selectedTags.isNotEmpty() || state.newOnly || state.untaggedOnly || state.hideCompleted || state.source != LibrarySource.ALL) {
                            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                },
            )
        }
        }

        }

        // ================================================================
        // 搜索面（EhViewer 式原地展开）：只负责输入、联想、历史与热门；
        // 提交后**直接收起**，结果显示在图库页本身。
        // ================================================================
        LibrarySearchOverlay(
            session = searchSession, visibility = searchTransition,
            // 「以搜索栏为中心展开覆盖全屏」的圆心。取不到时按屏幕上方居中兜底。
            revealOrigin = searchBarOrigin.takeIf { searchBarOriginReady },
            onSubmit = { submitDraft() },
            onSubmitHistory = { submitDraft(it) },
            onAppendTag = { full, excluded ->
                try {
                    val value = searchSession.draft
                    val edit = SearchQueryCodec.replace(value.text, value.selection.start, value.selection.end, full, excluded)
                    searchSession.edit(TextFieldValue(edit.text, TextRange(edit.cursor)))
                } catch (error: IllegalArgumentException) { searchSession.error = error.message }
            },
            history = overlayHistory, legacyHistory = legacyHistory,
            historyHidden = historyHidden, historyPaused = historyPaused,
            onHistoryHidden = { scope.launch { historyRepository.setHidden(it) } },
            onHistoryPaused = { scope.launch { historyRepository.setPaused(it) } },
            onRemoveHistory = { value, legacy ->
                val targetScope = if (legacy) "" else historyScope
                scope.launch {
                    historyRepository.remove(value, targetScope)
                    if (snackbarHostState.showSnackbar("已删除搜索历史", actionLabel = "撤销") == SnackbarResult.ActionPerformed) {
                        historyRepository.add(value, targetScope, restoring = true)
                    }
                }
            },
            onClearHistory = { legacy -> scope.launch { historyRepository.clear(if (legacy) "" else historyScope) } },
            suggestions = overlaySuggestions, suggestionsLoading = suggestionsLoading, suggestionsError = suggestionsError,
            hotTags = overlayHotTags, hotLoading = hotLoading, hotError = hotError, hotUpdated = hotUpdated, hotLocal = localDiscovery,
            onRetryHot = { hotRevision++ },
            onOpenDictionary = { searchSession.phase = SearchPhase.CLOSED; MainTabBus.requestSettingsTab() },
            backdrop = backdrop,
        )
        if (searchExpanded) SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())

        // ================================================================
        // 批量操作条：多选模式下悬浮于底部（此时 MainScreen 液态底栏已隐藏）
        // ================================================================
        if (state.selectedIds.isNotEmpty()) {
            BatchActionBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                backdrop = backdrop,
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
                onMarkUnread = vm::batchMarkUnread,
                onScrape = vm::batchScrape,
                onCancelScrape = vm::cancelBatchScrape,
                onDelete = {
                    if (!state.batchBusy) {
                        showDeleteConfirm = true
                    }
                },
            )
        }
    }

    // ================================================================
    // 排序 / 视图 / 筛选合并面板（顶栏胶囊最右侧那一个键）
    // ================================================================
    if (showFilter) {
        LibraryOptionsSheet(
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
            namespaceCounts = namespaceCounts,
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
 * 封面预取：以与显示组件完全一致的构造，把一张封面预热进 Coil 内存/磁盘缓存。
 *
 * 与 RemoteThumbnailImage 的两条路径逐字段对齐：
 * - 缩略图仓库路径（缩略图实验开关开启）：读缩略图仓库当前状态，就绪（Ready /
 *   生成中或失败时的 lastGood）则按 `CoverModel.RemoteCover(serverKey, arcid,
 *   revision)` 设置 memory/disk 缓存键、以 `ThumbnailResource` 为数据构建请求，
 *   显示组件后续可直接命中预热条目。尚未探测（Unrequested）或生成中且无
 *   lastGood 时，先触发与显示组件 LaunchedEffect 相同的 `requestCover`
 *   （仓库内部单飞并切 IO 执行探测），返回 false 留待下一次滚动窗口补 Coil 级
 *   预热；已探测失败且无 lastGood 的封面不再重试（显示组件提供手动重试入口），
 *   返回 true 防止滚动窗口反复重启探测。
 * - 遗留路径（开关关闭）：与 legacy 分支同一 URL——`ApiClient.thumbnailUrl(arcid)`
 *   并在该档案的 CoverChangeBus 版本号 >0 时追加 `?v=` 破缓存参数；默认缓存键即 URL 本身。
 *
 * 鉴权头无需显式设置：Coil 全局 ImageLoader（LanraragiApplication）复用
 * ApiClient.okHttpClient，ServerInterceptor 对 lanraragi.local 占位 host 改写为
 * 真实服务器地址并注入 Authorization 头——与显示组件完全一致。
 *
 * 必须在主线程调用（Coil enqueue 要求主线程，执行时自行切 IO）。
 * 返回 true 表示该条目已处理完毕（已入队预热 / 无需预热 / 无封面可预热），
 * 调用方据此写入去重集合。
 */
private fun enqueueCoverPrefetch(
    context: Context,
    container: AppContainer,
    arcid: String,
): Boolean {
    if (arcid.isBlank() || arcid.startsWith("local_")) {
        // 本地档案（LocalSaf）没有可预热的远程缩略图
        return true
    }

    // 单行本（TANK_xxx）没有「档案缩略图」，但有 `/api/tankoubons/{id}/thumbnail`：
    // 必须走那条端点，且不能进档案缩略图仓库（后者会请求 /api/archives/TANK_xxx/thumbnail）。
    if (isTankArchiveId(arcid)) {
        // 预取也必须与展示组件用同一个 URL：这里同样只取本档案的版本号。
        val legacyCoverVersion = CoverChangeBus.versionOf(arcid)
        context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(
                    ApiClient.tankoubonThumbnailUrl(arcid) +
                        if (legacyCoverVersion > 0) "?v=$legacyCoverVersion" else "",
                )
                .build(),
        )
        return true
    }

    // 普通档案：缩略图仓库是唯一路径（原先的 `a5_thumbnails` 实验开关已随「实验室」页移除）。
    val current = container.thumbnailRepository.cover(arcid).value
    val ready = when (current) {
        is CoverState.Ready -> current
        is CoverState.Generating -> current.lastGood
        is CoverState.Failed -> current.lastGood
        is CoverState.Unrequested -> null
    }

    if (ready == null) {
        if (current is CoverState.Failed) {
            return true
        }
        container.thumbnailRepository.requestCover(arcid)
        return false
    }

    val model = CoverModel.RemoteCover(
        serverKey = ApiClient.config.serverKey.value,
        arcid = arcid,
        revision = ready.revision,
    )
    context.imageLoader.enqueue(
        ImageRequest.Builder(context)
            .data(ready.resource.value)
            .memoryCacheKey(model.cacheKey)
            .diskCacheKey(model.cacheKey)
            .build(),
    )
    return true
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
 * D2 底部批量操作条（玻璃化，见设计稿 3.4）：收藏 / 缓存 / 分类 / 标已读 / 标记未读 / 刮削 / 删除。
 * 用共用的 LiquidGlassBar 承载，按钮较多时 FlowRow 自动折行，避免窄屏挤掉末尾按钮；
 * 批量进行中替换为进度提示并禁用操作；
 * 批量刮削进行中提供「取消刮削」入口（其余批量操作不可取消）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchActionBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    busy: Boolean,
    note: String?,
    onFavorite: () -> Unit,
    onCache: () -> Unit,
    onCategory: () -> Unit,
    onClearNew: () -> Unit,
    onMarkUnread: () -> Unit,
    onScrape: () -> Unit,
    onCancelScrape: () -> Unit,
    onDelete: () -> Unit,
) {
    LiquidGlassBar(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        backdrop = backdrop,
    ) {
        if (busy) {
            Spacer(Modifier.width(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                note ?: "处理中…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (note?.startsWith("刮削") == true) {
                TextButton(onClick = onCancelScrape) {
                    Text("取消刮削")
                }
            }
        } else {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.Center,
            ) {
                BatchTextButton("收藏", onFavorite)
                BatchTextButton("缓存", onCache)
                BatchTextButton("分类", onCategory)
                BatchTextButton("标已读", onClearNew)
                BatchTextButton("标记未读", onMarkUnread)
                BatchTextButton("刮削", onScrape, color = MaterialTheme.colorScheme.primary)

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

/** 批量操作条里的紧凑文字按钮，避免多个按钮挤爆窄屏。 */
@Composable
private fun BatchTextButton(
    label: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Text(label, color = color)
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

/** 当前筛选是否与该预设完全一致（含「隐藏读完」）。 */
private fun presetMatchesCurrent(
    state: LibraryState,
    preset: FilterPreset,
): Boolean =
    state.filter == preset.filter &&
            state.selectedTags == preset.tags &&
            state.sortby == preset.sortby &&
            state.order == preset.order &&
            state.categoryId == preset.categoryId &&
            state.newOnly == preset.newOnly &&
            state.untaggedOnly == preset.untaggedOnly &&
            state.hideCompleted == preset.hideCompleted

/** 预设摘要：排序字段 + 方向，尽力而为展示。 */
private fun presetSortLabel(preset: FilterPreset): String {
    val label =
        SORT_OPTIONS
            .firstOrNull { it.first == preset.sortby }
            ?.second
            ?: preset.sortby

    val direction =
        if (preset.order.equals("desc", true)) {
            "倒序"
        } else {
            "升序"
        }

    return "$label·$direction"
}

/** 预设摘要小徽标（非交互，避免拦截行点击）。 */
@Composable
private fun PresetSummaryChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 2.dp,
            ),
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
private fun LibraryOptionsSheet(
    state: LibraryState,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenCategoryManager: () -> Unit,
    /** 服务器命名空间分布；null 表示未知，此时所有排序字段一律可选用。 */
    namespaceCounts: Map<String, Int>? = null,
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
            // 排序：字段 + 方向
            // 「按什么排」与「往哪边排」是同一个决策，合并成一组：方向是二选一，
            // 用右侧的小分段控件表达，比两个并列的 FilterChip 更像一个开关而不是筛选项。
            // ============================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "排序",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )

                SegmentedControl(
                    segments = listOf(
                        Segment("升序", Icons.Filled.ArrowUpward),
                        Segment("倒序", Icons.Filled.ArrowDownward),
                    ),
                    selectedIndex = if (state.order == "desc") 1 else 0,
                    onSelect = { vm.setOrder(if (it == 1) "desc" else "asc") },
                    modifier = Modifier.width(156.dp),
                    compact = true,
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            FlowRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {

                SORT_OPTIONS.forEach {
                        (value, label) ->

                    // 服务端把 sortby 当字面正则匹配命名空间：库里没有该命名空间时，
                    // 「排序」既不报错也不退回上一个顺序，只给出任意顺序。这里提前标出来，
                    // 不让用户在「看起来变了、其实随机」的结果里猜。
                    val report =
                        LibrarySortResolver.report(
                            value,
                            namespaceCounts,
                        )

                    FilterChip(
                        selected =
                            state.sortby == value,
                        leadingIcon =
                            if (report.ineffective) {
                                {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = "该排序字段在本库不生效",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        onClick = {
                            vm.setSort(value)
                            LibrarySortResolver
                                .explain(value, label, report)
                                ?.let(vm::showMessage)
                        },
                        label = {
                            Text(label)
                        },
                    )
                }
            }

            // 只点名「本库没有该命名空间」的字段。文案压到一行：带警示图标的那几个
            // 已经说明了问题，这里只需要给出服务端实际在用的写法。
            val ineffectiveSorts =
                SORT_OPTIONS.filter {
                    (value, _) ->
                    LibrarySortResolver
                        .report(value, namespaceCounts)
                        .ineffective
                }

            if (ineffectiveSorts.isNotEmpty()) {
                Text(
                    ineffectiveSorts.joinToString("、") { (value, _) ->
                        LibrarySortResolver
                            .report(value, namespaceCounts)
                            .similarNamespace
                            ?.let { ns -> "$value→$ns" }
                            ?: value
                    } + "：本库无此命名空间，排序不生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 视图 / 列数
            // 先定「怎么显示」，再定「一行几本」；列表视图下每行固定一本，
            // 列数不参与布局，因此置灰并说明，避免出现「改了没反应」的控件。
            // ============================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "视图",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )

                val columnsEffective = state.viewMode != "list"

                Text(
                    text = if (columnsEffective) "${state.columns} 列" else "列表不分列",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            SegmentedControl(
                segments = listOf(
                    Segment("松散", Icons.Filled.GridView),
                    Segment("紧凑", Icons.Filled.GridOn),
                    Segment("列表", Icons.AutoMirrored.Filled.ViewList),
                ),
                selectedIndex = when (state.viewMode) {
                    "compact" -> 1
                    "list" -> 2
                    else -> 0
                },
                onSelect = { index ->
                    vm.setViewMode(
                        when (index) {
                            1 -> "compact"
                            2 -> "list"
                            else -> "grid"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(
                Modifier.height(8.dp)
            )

            SegmentedControl(
                segments = (2..8).map { Segment("$it") },
                selectedIndex = (state.columns - 2).coerceIn(0, 6),
                onSelect = { vm.setColumns(it + 2) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.viewMode != "list",
                compact = true,
            )

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 范围
            // ============================================================
            Text("范围", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySource.entries.forEach { source ->
                    FilterChip(selected = state.source == source, onClick = { vm.setSource(source) },
                        label = { Text(when (source) { LibrarySource.ALL -> "全部"; LibrarySource.REMOTE -> "服务器"; LibrarySource.LOCAL -> "本地" }) })
                }
            }

            // ============================================================
            // 已选条件（只列可单独移除的：标签与分类）
            // ============================================================
            if (
                state.selectedTags.isNotEmpty() ||
                state.categoryId.isNotEmpty()
            ) {

                Spacer(Modifier.height(12.dp))

                Text(
                    "已选",
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
                }
            }

            // ============================================================
            // 分类与状态
            // 两者都是「只看哪一类」的同一个决策，放同一行 chips：分类在前、状态在后，
            // 既省一个标题，也不必让用户在两处找同一个开关。
            // ============================================================
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    "分类 / 状态",
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

                FilterChip(
                    selected = state.hideCompleted,
                    onClick = vm::toggleHideCompleted,
                    label = {
                        Text("隐藏读完")
                    },
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            // ============================================================
            // 全部标为已读（A11：服务器全库清除 New）
            //
            // 计数 = 当前已加载的筛选结果里带「新」标的本数，仅当 > 0 时显示
            // （对应设计稿 3.4「仅当筛选结果含 New 时显示计数」）。
            // 它只是提示：操作范围是全库，未加载到的分页也会一并清除。
            // 数据来自 items 的 Archive.isNew，无需额外请求。
            // ============================================================
            val loadedNewCount = state.items.count { it.isNew }

            TextButton(
                onClick = vm::markAllRead,
                enabled = !state.markingAllRead,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.markingAllRead -> "标已读中…"
                        loadedNewCount > 0 -> "全部已读（$loadedNewCount）"
                        else -> "全部已读"
                    },
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

@Composable
private fun LibraryDetailPane(
    archive: Archive,
    onOpenDetail: () -> Unit,
    onRead: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(archive.displayTitle.ifBlank { archive.arcid }, style = MaterialTheme.typography.titleLarge)
        if (archive.tags.isNotBlank()) {
            Text(archive.tags, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (archive.pagecount > 0) "${archive.pagecount} 页 · 已读 ${archive.progress.coerceIn(0, archive.pagecount)} 页"
            else "页数将在本地/服务器索引可用后显示",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (archive.summary.isNotBlank()) {
            Text(archive.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenDetail) { Text("打开详情") }
            TextButton(onClick = onRead) { Text("开始阅读") }
        }
    }
}
