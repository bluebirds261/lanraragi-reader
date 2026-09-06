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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Tankoubon
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.LoadingImage
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.edgeSwipeBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A11 单行本浏览：卷墙 → 卷详情（成员档案 + 整卷阅读）。 */
class TankoubonBrowseViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val tanks: List<Tankoubon> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val selected: Tankoubon? = null,
        val members: List<Archive> = emptyList(),
        val membersLoading: Boolean = false,
        val message: String? = null,
    )

    private val repository = container.repository
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        loadTanks()
    }

    fun loadTanks() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val tanks = repository.getTankoubons()
                _state.update { it.copy(tanks = tanks, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载单行本失败") }
            }
        }
    }

    fun openTank(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(membersLoading = true, error = null) }
            try {
                val full = repository.getTankoubonFull(id)
                _state.update {
                    it.copy(selected = full, members = full.full_data, membersLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(membersLoading = false, error = e.message ?: "加载卷详情失败") }
            }
        }
    }

    fun closeTank() {
        _state.update { it.copy(selected = null, members = emptyList(), error = null) }
    }

    fun createTank(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.createTankoubon(n)
                loadTanks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "新建单行本失败") }
            }
        }
    }

    fun renameTank(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.renameTankoubon(id, n)
                loadTanks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "重命名失败") }
            }
        }
    }

    fun deleteTank(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteTankoubon(id)
                if (_state.value.selected?.id == id) closeTank()
                loadTanks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "删除单行本失败") }
            }
        }
    }

    fun removeArchive(tankId: String, arcid: String) {
        viewModelScope.launch {
            try {
                repository.removeArchiveFromTankoubon(tankId, arcid)
                openTank(tankId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "移出失败") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankoubonBrowseScreen(
    container: AppContainer,
    navController: NavController,
    tankId: String?,
) {
    val vm: TankoubonBrowseViewModel = viewModel { TankoubonBrowseViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    var showManage by remember { mutableStateOf(false) }

    val goBack: () -> Unit = {
        if (state.selected != null) vm.closeTank() else navController.popBackStack()
    }

    LaunchedEffect(tankId, state.tanks) {
        if (tankId != null && state.selected == null && state.tanks.isNotEmpty()) {
            vm.openTank(tankId)
        }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { goBack() },
        topBar = {
            AppTopBar(
                title = state.selected?.name ?: "单行本",
                onBack = goBack,
                actions = {
                    if (state.selected == null) {
                        IconButton(onClick = { showManage = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "管理单行本")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.selected == null && state.loading && state.tanks.isEmpty() -> LoadingBox()
                state.selected == null && state.error != null && state.tanks.isEmpty() ->
                    ErrorBox(state.error!!, onRetry = vm::loadTanks)
                state.selected == null && state.tanks.isEmpty() -> EmptyBox("暂无单行本")
                state.selected == null -> TankWall(state.tanks, vm::openTank)
                state.membersLoading && state.members.isEmpty() -> LoadingBox()
                state.error != null && state.members.isEmpty() -> ErrorBox(state.error!!, onRetry = { state.selected?.let { vm.openTank(it.id) } })
                state.members.isEmpty() -> EmptyBox("该卷暂无档案\n（在档案详情页点「加入卷」添加）")
                else -> TankDetail(state, vm, navController)
            }
        }

        if (showManage) {
            TankManageSheet(
                tanks = state.tanks,
                onCreate = vm::createTank,
                onRename = vm::renameTank,
                onDelete = vm::deleteTank,
                onDismiss = { showManage = false },
            )
        }
    }
}

@Composable
private fun TankWall(tanks: List<Tankoubon>, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tanks, key = { it.id }) { tank ->
            Card(onClick = { onOpen(tank.id) }, shape = RoundedCornerShape(10.dp)) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
                        LoadingImage(
                            model = ApiClient.tankoubonThumbnailUrl(tank.id),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        tank.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TankDetail(
    state: TankoubonBrowseViewModel.UiState,
    vm: TankoubonBrowseViewModel,
    navController: NavController,
) {
    val tank = state.selected ?: return
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 ${state.members.size} 本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { navController.navigate(Routes.tankReader(tank.id)) }) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("整卷阅读")
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.members.size) { i ->
                val archive = state.members[i]
                Card(shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(72.dp)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            LoadingImage(
                                model = ApiClient.thumbnailUrl(archive.arcid),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${i + 1}. ${archive.displayTitle.ifBlank { archive.arcid }}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${archive.pagecount} 页",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.removeArchive(tank.id, archive.arcid) }) {
                            Icon(Icons.Filled.Delete, "移出卷", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TankManageSheet(
    tanks: List<Tankoubon>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Tankoubon?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Tankoubon?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("单行本管理", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (tanks.isEmpty()) {
                Text("暂无单行本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tanks.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { renameTarget = t; renameName = t.name }) { Text("重命名") }
                        TextButton(onClick = { deleteTarget = t }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { newName = ""; showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新建单行本")
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建单行本") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("名称") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onCreate(newName); showCreate = false }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
    renameTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名单行本") },
            text = { OutlinedTextField(value = renameName, onValueChange = { renameName = it }, label = { Text("名称") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(t.id, renameName); renameTarget = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除单行本") },
            text = { Text("确定要删除单行本「${t.name}」吗？不会删除其中档案。") },
            confirmButton = { TextButton(onClick = { onDelete(t.id); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}
