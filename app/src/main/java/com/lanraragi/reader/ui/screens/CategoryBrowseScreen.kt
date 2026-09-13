package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.catalog.isTankArchiveId
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.RemoteThumbnailImage
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.edgeSwipeBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** D4 分类浏览页：分类墙 → 分类内档案列表（分页 + 排序）。 */
class CategoryBrowseViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val categories: List<Category> = emptyList(),
        val categoriesLoading: Boolean = false,
        val error: String? = null,
        val selected: Category? = null,
        val archives: List<Archive> = emptyList(),
        val archivesLoading: Boolean = false,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val sortby: String = "title",
        val order: String = "asc",
        val message: String? = null,
    )

    private val repository = container.repository
    private val loadMutex = Mutex()
    private var nextStart = 0

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(categoriesLoading = true, error = null) }
            try {
                val cats = repository.getCategories()
                _state.update { it.copy(categories = cats, categoriesLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(categoriesLoading = false, error = e.message ?: "加载分类失败")
                }
            }
        }
    }

    fun openCategory(id: String) {
        val cat = _state.value.categories.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(selected = cat, archives = emptyList(), hasMore = true, sortby = "title", order = "asc")
        }
        nextStart = 0
        loadArchives(0, append = false)
    }

    fun closeCategory() {
        _state.update { it.copy(selected = null, archives = emptyList(), error = null) }
        nextStart = 0
    }

    fun loadMore() {
        val s = _state.value
        if (s.archivesLoading || s.loadingMore || !s.hasMore || s.selected == null) return
        loadArchives(nextStart, append = true)
    }

    fun setSort(v: String) {
        if (_state.value.sortby == v) return
        _state.update { it.copy(sortby = v) }
        nextStart = 0
        loadArchives(0, append = false)
    }

    fun setOrder(v: String) {
        if (_state.value.order == v) return
        _state.update { it.copy(order = v) }
        nextStart = 0
        loadArchives(0, append = false)
    }

    private fun loadArchives(start: Int, append: Boolean) {
        val catId = _state.value.selected?.id ?: return
        viewModelScope.launch {
            loadMutex.withLock {
                _state.update {
                    if (append) it.copy(loadingMore = true, error = null)
                    else it.copy(archivesLoading = true, error = null)
                }
                try {
                    val r = repository.getArchives(
                        page = start,
                        sortby = _state.value.sortby,
                        order = _state.value.order,
                        categoryId = catId,
                    )
                    val prev = _state.value.archives
                    val merged = if (append) (prev + r.items).distinctBy { it.arcid } else r.items
                    val hasMore = r.items.isNotEmpty() && merged.size > prev.size &&
                        (r.total == null || merged.size < r.total)
                    nextStart = start + r.items.size
                    _state.update {
                        it.copy(
                            archives = merged,
                            hasMore = hasMore,
                            archivesLoading = false,
                            loadingMore = false,
                            error = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            archivesLoading = false,
                            loadingMore = false,
                            error = e.message ?: "加载失败",
                        )
                    }
                }
            }
        }
    }

    fun createCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.createCategory(n)
                reloadCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "新建分类失败") }
            }
        }
    }

    fun renameCategory(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val category = _state.value.categories.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                repository.renameCategory(id, n, category.pinned != 0)
                reloadCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "重命名分类失败") }
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(id)
                if (_state.value.selected?.id == id) {
                    closeCategory()
                }
                reloadCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "删除分类失败") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private suspend fun reloadCategories() {
        try {
            val cats = repository.getCategories()
            _state.update { it.copy(categories = cats) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

private val CATEGORY_SORT_OPTIONS = listOf(
    "title" to "标题",
    "date_added" to "添加日期",
    "lastread" to "最近阅读",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowseScreen(
    container: AppContainer,
    navController: NavController,
    categoryId: String?,
) {
    val vm: CategoryBrowseViewModel = viewModel { CategoryBrowseViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    val goBack: () -> Unit = {
        if (state.selected != null) vm.closeCategory() else navController.popBackStack()
    }

    var showManage by remember { mutableStateOf(false) }

    LaunchedEffect(categoryId, state.categories) {
        if (categoryId != null && state.selected == null && state.categories.isNotEmpty()) {
            vm.openCategory(categoryId)
        }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { goBack() },
        topBar = {
            AppTopBar(
                title = state.selected?.name ?: "分类",
                onBack = goBack,
                actions = {
                    if (state.selected == null) {
                        IconButton(onClick = { showManage = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "管理分类")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.selected == null && state.categoriesLoading && state.categories.isEmpty() -> LoadingBox()

                state.selected == null && state.error != null && state.categories.isEmpty() ->
                    ErrorBox(state.error!!, onRetry = vm::loadCategories)

                state.selected == null && state.categories.isEmpty() -> EmptyBox("暂无分类")

                state.selected == null -> CategoryWall(container, state.categories, vm::openCategory)

                state.error != null && state.archives.isEmpty() -> ErrorBox(
                    state.error!!,
                    onRetry = { vm.openCategory(state.selected!!.id) },
                )

                state.archivesLoading && state.archives.isEmpty() -> LoadingBox()

                state.archives.isEmpty() -> EmptyBox("该分类暂无档案")

                else -> CategoryArchiveGrid(container, state, vm, navController)
            }
        }

        if (showManage) {
            CategoryManageSheet(
                categories = state.categories,
                onCreate = vm::createCategory,
                onRename = vm::renameCategory,
                onDelete = vm::deleteCategory,
                onDismiss = { showManage = false },
            )
        }
    }
}

@Composable
private fun CategoryWall(
    container: AppContainer,
    categories: List<Category>,
    onOpen: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { c ->
            val coverArcId = c.archives.firstOrNull()
            Card(
                onClick = { onOpen(c.id) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f),
                    ) {
                        if (coverArcId != null) {
                            RemoteThumbnailImage(
                                container = container,
                                arcid = coverArcId,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    c.name.take(1).ifBlank { "?" },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (c.search.isNotBlank()) "动态分类" else "${c.archives.size} 个档案",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryArchiveGrid(
    container: AppContainer,
    state: CategoryBrowseViewModel.UiState,
    vm: CategoryBrowseViewModel,
    navController: NavController,
) {
    Column(Modifier.fillMaxSize()) {
        // 排序 chips
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CATEGORY_SORT_OPTIONS.forEach { (value, label) ->
                FilterChip(
                    selected = state.sortby == value,
                    onClick = { vm.setSort(value) },
                    label = { Text(label) },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.setOrder(if (state.order == "asc") "desc" else "asc") }) {
                Text(if (state.order == "asc") "升序" else "倒序")
            }
        }

        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) vm.loadMore()
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.archives, key = { it.arcid }) { archive ->
                ArchiveCard(
                    archive = archive,
                    // 分类结果同样受 groupby_tanks 影响：单行本只能进单行本阅读器
                    // （档案详情端点要求 40 位 arcid）。
                    onClick = {
                        if (isTankArchiveId(archive.arcid)) {
                            navController.navigate(Routes.tankReader(archive.arcid))
                        } else {
                            navController.navigate(Routes.detail(archive.arcid))
                        }
                    },
                    thumbnailContainer = container,
                )
            }
            if (state.loadingMore) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManageSheet(
    categories: List<Category>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }
    var blocked by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("分类管理", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (categories.isEmpty()) {
                Text(
                    "暂无分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                categories.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (c.search.isNotBlank()) "动态分类" else "${c.archives.size} 个档案",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { renameTarget = c; renameName = c.name }) { Text("重命名") }
                        TextButton(
                            onClick = {
                                if (c.archives.isNotEmpty()) {
                                    blocked = "该分类含 ${c.archives.size} 个档案，请先移空"
                                } else {
                                    deleteTarget = c
                                }
                            },
                        ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { newName = ""; showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新建分类")
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onCreate(newName); showCreate = false }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }

    renameTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名分类") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(c.id, renameName); renameTarget = null }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分类") },
            text = { Text("确定要删除分类「${c.name}」吗？") },
            confirmButton = {
                TextButton(onClick = { onDelete(c.id); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    blocked?.let { text ->
        AlertDialog(
            onDismissRequest = { blocked = null },
            title = { Text("无法删除") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { blocked = null }) { Text("知道了") } },
        )
    }
}
