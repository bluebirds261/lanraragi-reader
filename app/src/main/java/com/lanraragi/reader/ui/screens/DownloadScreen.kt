package com.lanraragi.reader.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.DownloadTask
import com.lanraragi.reader.data.DownloadTaskType
import com.lanraragi.reader.data.ServerTask
import com.lanraragi.reader.data.TaskState
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DownloadViewModel(private val container: AppContainer) : ViewModel() {
    var isRefreshing by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    // 正在上传到服务器的本地档案 arcid 集合（状态锁：上传中禁止并发上传）。
    var uploadingArcids by mutableStateOf<Set<String>>(emptySet())
        private set

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            // 重新扫描本地路径；LocalScanManager 会在文件系统签名未变化时自动跳过完整扫描。
            val settings = container.settingsRepository.settings.first()
            container.localScanManager.scan(settings.extraScanDirUris)
            // 重新加载离线索引
            container.offlineCache.loadIndex()
            isRefreshing = false
        }
    }

    fun clearMessage() {
        message = null
    }

    fun uploadLocal(archive: Archive) {
        if (archive.arcid in uploadingArcids) return
        uploadingArcids = uploadingArcids + archive.arcid
        viewModelScope.launch(Dispatchers.IO) {
            val input = try {
                container.context.contentResolver.openInputStream(Uri.parse(archive.summary))
            } catch (e: Exception) {
                null
            }
            if (input == null) {
                message = "无法读取档案：${archive.title}"
                uploadingArcids = uploadingArcids - archive.arcid
                return@launch
            }
            try {
                container.repository.uploadArchive(archive.title, input, archive.title, null)
                message = "上传成功：${archive.title}"
                LibraryRefreshBus.tick.value++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "上传失败"
            } finally {
                runCatching { input.close() }
                uploadingArcids = uploadingArcids - archive.arcid
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    container: AppContainer,
    navController: NavController? = null,
    onBackToHome: () -> Unit = {}
) {
    val vm: DownloadViewModel = viewModel { DownloadViewModel(container) }
    val index by container.offlineCache.index.collectAsStateWithLifecycle()
    val localArchives by container.localScanManager.localArchives.collectAsStateWithLifecycle()
    val isScanning by container.localScanManager.isScanning.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val serverTasks by container.jobTracker.tasks.collectAsStateWithLifecycle()
    val downloadTasks by container.downloadManager.tasks.collectAsStateWithLifecycle()
    val offlineUsage by container.offlineCache.usage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 全部, 1: 云端, 2: 本地
    var showQueueMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val favDir = remember {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        File(base, "收藏")
    }
    var favFiles by remember { mutableStateOf(favDir.listFiles()?.toList().orEmpty()) }

    val viewMode = settings?.galleryViewMode ?: "grid"
    val columns = settings?.galleryColumns ?: 3

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // D5 空态入口：图库空态点「扫描本地文件」→ 进入下载页后自动切到「本地」子 tab
    LaunchedEffect(Unit) {
        DownloadSubTabBus.local.collect { goLocal ->
            if (goLocal) {
                selectedTab = 2
                DownloadSubTabBus.local.value = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                SegmentedControl(
                    tabs = listOf("全部", "云端", "本地"),
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showQueueMenu = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "下载任务操作")
                    }
                    DropdownMenu(
                        expanded = showQueueMenu,
                        onDismissRequest = { showQueueMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("刷新") },
                            onClick = {
                                showQueueMenu = false
                                vm.refresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除全部任务") },
                            enabled = downloadTasks.isNotEmpty(),
                            onClick = {
                                showQueueMenu = false
                                showClearAllConfirm = true
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("暂停全部") },
                            enabled = downloadTasks.any { it.state == TaskState.WAITING || it.state == TaskState.RUNNING },
                            onClick = {
                                showQueueMenu = false
                                container.downloadManager.pauseAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("开始全部") },
                            enabled = downloadTasks.any { it.state == TaskState.PAUSED },
                            onClick = {
                                showQueueMenu = false
                                container.downloadManager.resumeAll()
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.isRefreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding)
        ) {
            // 云端下载的档案列表
            val cloudItems = index.items.mapNotNull { it.metadata }

            // 合并列表用于“全部”页
            val allItems = (cloudItems + localArchives).sortedBy { it.title.lowercase() }

            val displayedItems = when (selectedTab) {
                0 -> allItems
                1 -> cloudItems
                2 -> localArchives
                else -> emptyList()
            }

            val hasServerTasks = selectedTab == 0 && serverTasks.isNotEmpty()
            val hasDownloadTasks = selectedTab == 0 && downloadTasks.isNotEmpty()

            if (displayedItems.isEmpty() && favFiles.isEmpty() && !hasServerTasks && !hasDownloadTasks) {
                if (isScanning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("正在扫描本地档案…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    EmptyBox("暂无内容")
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        "离线缓存已用 ${formatBytes(offlineUsage.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (selectedTab == 2 && vm.uploadingArcids.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在上传…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (isScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (viewMode == "list") {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasServerTasks) {
                                item { ServerTasksSection(serverTasks) }
                            }
                            if (hasDownloadTasks) {
                                item {
                                    DownloadTasksSection(
                                        tasks = downloadTasks,
                                        onPause = container.downloadManager::pause,
                                        onResume = container.downloadManager::resume,
                                        onRetry = container.downloadManager::retry,
                                        onRemove = container.downloadManager::remove,
                                    )
                                }
                            }
                            items(displayedItems, key = { it.arcid }) { item ->
                                val isLocal = item.arcid.startsWith("local_")
                                if (isLocal) {
                                    LocalArchiveItem(
                                        uploading = item.arcid in vm.uploadingArcids,
                                        onUpload = { vm.uploadLocal(item) },
                                    ) {
                                        ArchiveListRow(
                                            archive = item,
                                            onClick = { navController?.navigate(Routes.detail(item.arcid)) },
                                            isOffline = true,
                                            offlineCover = ArchivePageModel(Uri.parse(item.summary), 0, context),
                                        )
                                    }
                                } else {
                                    ArchiveListRow(
                                        archive = item,
                                        onClick = { navController?.navigate(Routes.reader(item.arcid)) },
                                        isOffline = true,
                                        offlineCover = container.offlineCache.coverFile(item.arcid),
                                    )
                                }
                            }

                            if (selectedTab == 0 && favFiles.isNotEmpty()) {
                                item { FavoriteSection(favFiles, favDir) { favFiles = it } }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasServerTasks) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                                    ServerTasksSection(serverTasks)
                                }
                            }
                            if (hasDownloadTasks) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                                    DownloadTasksSection(
                                        tasks = downloadTasks,
                                        onPause = container.downloadManager::pause,
                                        onResume = container.downloadManager::resume,
                                        onRetry = container.downloadManager::retry,
                                        onRemove = container.downloadManager::remove,
                                    )
                                }
                            }
                            gridItems(displayedItems, key = { it.arcid }) { item ->
                                val isLocal = item.arcid.startsWith("local_")
                                if (isLocal) {
                                    LocalArchiveItem(
                                        uploading = item.arcid in vm.uploadingArcids,
                                        onUpload = { vm.uploadLocal(item) },
                                    ) {
                                        ArchiveCard(
                                            archive = item,
                                            onClick = { navController?.navigate(Routes.detail(item.arcid)) },
                                            compact = viewMode == "compact",
                                            isOffline = true,
                                            offlineCover = ArchivePageModel(Uri.parse(item.summary), 0, context),
                                        )
                                    }
                                } else {
                                    ArchiveCard(
                                        archive = item,
                                        onClick = { navController?.navigate(Routes.reader(item.arcid)) },
                                        compact = viewMode == "compact",
                                        isOffline = true,
                                        offlineCover = container.offlineCache.coverFile(item.arcid),
                                    )
                                }
                            }

                            if (selectedTab == 0 && favFiles.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                                    FavoriteSection(favFiles, favDir) { favFiles = it }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("删除全部下载任务？") },
            text = { Text("会取消当前下载并删除任务记录，不会删除已经完成的离线档案。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadTasks.forEach { container.downloadManager.remove(it.id) }
                        showClearAllConfirm = false
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LocalArchiveItem(
    uploading: Boolean,
    onUpload: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuOpen = true },
            ),
    ) {
        content()
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (uploading) "上传中…" else "上传到服务器") },
                enabled = !uploading,
                onClick = {
                    menuOpen = false
                    onUpload()
                },
            )
        }
    }
}

@Composable
private fun FavoriteSection(favFiles: List<File>, favDir: File, onUpdate: (List<File>) -> Unit) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text("收藏图片（${favFiles.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(240.dp),
            userScrollEnabled = false,
        ) {
            gridItems(favFiles, key = { it.absolutePath }) { f ->
                Box(Modifier.aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))) {
                    AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    IconButton(
                        onClick = { f.delete(); onUpdate(favDir.listFiles()?.toList().orEmpty()) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Filled.Delete, "删除", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerTasksSection(tasks: List<ServerTask>) {
    var failedTask by remember { mutableStateOf<ServerTask?>(null) }
    Column {
        Text("服务器任务（${tasks.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        tasks.forEach { task ->
            val failed = task.isFailed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = failed) { failedTask = task }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.label.ifBlank { task.jobid },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatTaskTime(task.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stateLabel(task.state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    failedTask?.let { task ->
        AlertDialog(
            onDismissRequest = { failedTask = null },
            title = { Text("任务失败原因") },
            text = {
                Text(
                    listOf(task.result, task.note).filter { it.isNotBlank() }.joinToString("\n")
                        .ifBlank { "无详细信息" }
                )
            },
            confirmButton = {
                TextButton(onClick = { failedTask = null }) { Text("确定") }
            },
        )
    }
}

@Composable
private fun DownloadTasksSection(
    tasks: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        Text("下载任务（${tasks.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        tasks.forEach { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title.ifBlank { task.arcid.ifBlank { task.id } },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = downloadTypeLabel(task.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.state == TaskState.RUNNING || task.state == TaskState.WAITING) {
                        if (task.progress != null) {
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    if (task.state == TaskState.FAILED && task.error != null) {
                        Text(
                            text = task.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = downloadStateLabel(task),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (task.state) {
                        TaskState.FAILED -> MaterialTheme.colorScheme.error
                        TaskState.DONE -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                when (task.state) {
                    TaskState.RUNNING, TaskState.WAITING -> TextButton(onClick = { onPause(task.id) }) { Text("暂停") }
                    TaskState.PAUSED -> TextButton(onClick = { onResume(task.id) }) { Text("恢复") }
                    TaskState.FAILED -> TextButton(onClick = { onRetry(task.id) }) { Text("重试") }
                    else -> {}
                }
                IconButton(onClick = { onRemove(task.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun downloadTypeLabel(type: DownloadTaskType): String = when (type) {
    DownloadTaskType.OFFLINE_CACHE -> "离线缓存"
    DownloadTaskType.ARCHIVE_FILE -> "原档下载"
    DownloadTaskType.PAGE -> "收藏单页"
}

private fun downloadStateLabel(task: DownloadTask): String = when (task.state) {
    TaskState.WAITING -> "等待"
    TaskState.RUNNING -> if (task.progress != null) "${(task.progress * 100).toInt()}%" else "进行中"
    TaskState.PAUSED -> "已暂停"
    TaskState.FAILED -> "失败"
    TaskState.DONE -> "完成"
}

private fun stateLabel(state: String): String {
    val s = state.lowercase()
    return when {
        s.contains("finish") || s.contains("done") -> "成功"
        s.contains("fail") || s.contains("error") || s == "dead" -> "失败"
        else -> "进行中"
    }
}

private fun formatTaskTime(ts: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    }
}

@Composable
private fun SegmentedControl(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
