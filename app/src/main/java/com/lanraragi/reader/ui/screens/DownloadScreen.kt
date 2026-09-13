package com.lanraragi.reader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.data.DownloadTask
import com.lanraragi.reader.data.DownloadTaskType
import com.lanraragi.reader.data.EnqueueRejection
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.OfflineUsage
import com.lanraragi.reader.data.ServerTask
import com.lanraragi.reader.data.TaskState
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.download.DEFAULT_DOWNLOAD_MAX_RETRIES
import com.lanraragi.reader.data.download.DownloadFailureCategory
import com.lanraragi.reader.data.download.DownloadSourceIdentity
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.CachedArchive
import com.lanraragi.reader.data.storage.StorageRootState
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.asImageRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 任务优先级映射值（DownloadTaskSpec.priority）：高派发优先。 */
private const val TASK_PRIORITY_HIGH = 10
private const val TASK_PRIORITY_NORMAL = 0
private const val TASK_PRIORITY_LOW = -10

/**
 * 自动恢复下载（本地会话行为）：打开下载页时自动恢复暂停任务。
 * DownloadManager 无内建自动恢复能力（恢复语义由 recover/手动恢复承担），故做成下载页本地开关。
 */
private val autoResumeDownloadEnabled = mutableStateOf(false)

class DownloadViewModel(private val container: AppContainer) : ViewModel() {
    var isRefreshing by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    // 正在上传到服务器的本地档案 arcid 集合（状态锁：上传中禁止并发上传）。
    var uploadingArcids by mutableStateOf<Set<String>>(emptySet())
        private set

    var pinnedArcids by mutableStateOf<Set<String>>(emptySet())
        private set

    fun loadPinned(arcids: Collection<String>) {
        viewModelScope.launch {
            pinnedArcids = arcids.mapNotNull { arcid ->
                container.savedArtifactRepository.findBySource(
                    DownloadSourceIdentity(arcid, ApiClient.config.baseUrl),
                )?.takeIf { it.pinned }?.source?.archiveId
            }.toSet()
        }
    }

    fun togglePinned(arcid: String) {
        viewModelScope.launch {
            val artifact = container.savedArtifactRepository.findBySource(
                DownloadSourceIdentity(arcid, ApiClient.config.baseUrl),
            ) ?: return@launch
            val next = arcid !in pinnedArcids
            if (container.savedArtifactRepository.pin(artifact.artifactKey, next)) {
                pinnedArcids = if (next) pinnedArcids + arcid else pinnedArcids - arcid
            }
        }
    }

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

    fun saveDownloadRatePerSecond(value: Int) {
        viewModelScope.launch { container.settingsRepository.saveDownloadRatePerSecond(value) }
    }

    fun clearMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }

    /**
     * 存储门禁恢复：把重新选择的 SAF 目录（或 null = 默认目录）写回设置。
     * SAF 授权由调用方先 takePersistableUriPermission，与向导/设置页同一写入路径。
     */
    fun saveStorageRoot(uri: String?) {
        viewModelScope.launch {
            container.settingsRepository.saveStorageRoot(uri)
            message = if (uri == null) "已恢复默认存储目录" else "已更新存储目录"
        }
    }

    fun runLruCleanup() {
        viewModelScope.launch {
            container.offlineCache.enforceConfiguredLimit()
            message = "LRU 清理已执行"
        }
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
    val activeCount by container.downloadManager.activeCount.collectAsStateWithLifecycle()
    val storageState by container.offlineCache.storageState.collectAsStateWithLifecycle()
    val enqueueRejection by container.downloadManager.lastEnqueueRejection.collectAsStateWithLifecycle()
    // C3 认证失败自动暂停整队的提示（协调器单一来源，渲染后由用户关闭）。
    val pauseNotice by container.durableDownloadCoordinator.pauseNotice.collectAsStateWithLifecycle()
    // C3 重试上限（设置单一来源）：失败行按真实上限展示「已重试 N/上限 次」。
    val maxRetries = settings?.downloadMaxRetries ?: DEFAULT_DOWNLOAD_MAX_RETRIES

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 全部, 1: 云端, 2: 本地
    var showQueueMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showLruConfirm by remember { mutableStateOf(false) }
    var showCacheTop10 by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(index.items) { vm.loadPinned(index.items.map { it.arcid }) }

    // 存储门禁恢复 CTA：重新选择 SAF 目录（持久化授权后写入存储根），或恢复应用默认目录。
    val storageFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                vm.saveStorageRoot(uri.toString())
            } catch (e: Exception) {
                vm.showMessage("无法获取文件夹访问权限：${e.message ?: "未知错误"}")
            }
        }
    }
    val onPickStorageFolder: () -> Unit = { storageFolderLauncher.launch(null) }
    val onUseDefaultStorage: () -> Unit = { vm.saveStorageRoot(null) }
    // 仅在存储根确实不可用（SAF 授权失效降级回退默认目录，或最近一次写入被门禁拒绝）时
    // 才提供恢复入口，避免存储正常时给出无意义的「重新选择文件夹」。
    val storageRecoveryNeeded = storageState.degraded || storageState.lastError != null

    // 自动恢复下载（本地行为）：打开下载页时按开关恢复暂停任务
    LaunchedEffect(Unit) {
        if (autoResumeDownloadEnabled.value) container.downloadManager.resumeAll()
    }

    // C5 速度统计：滑窗估算 + 无进行中任务清零
    val speedTrackers = remember { mutableStateMapOf<String, SpeedTracker>() }
    val taskSpeeds = remember { mutableStateMapOf<String, Float>() }
    var totalSpeedBytesPerSecond by remember { mutableStateOf(0f) }
    LaunchedEffect(downloadTasks) {
        val now = System.currentTimeMillis()
        val running = downloadTasks.filter { it.state == TaskState.RUNNING }
        speedTrackers.keys.retainAll(running.map { it.id }.toSet())
        if (running.isEmpty()) {
            taskSpeeds.clear()
            totalSpeedBytesPerSecond = 0f
        } else {
            var sum = 0f
            running.forEach { task ->
                val tracker = speedTrackers.getOrPut(task.id) { SpeedTracker() }
                tracker.update(task.completedBytes, now)
                val speed = tracker.bytesPerSecond
                taskSpeeds[task.id] = speed
                sum += speed
            }
            totalSpeedBytesPerSecond = sum
        }
    }

    // 缓存占用 Top10（简化版）：对当前存储根逐项递归求和（OfflineCacheManager 无逐条用量 API）
    var cacheTop10 by remember { mutableStateOf<List<Pair<CachedArchive, Long>>>(emptyList()) }
    LaunchedEffect(showCacheTop10, index.items) {
        if (!showCacheTop10) return@LaunchedEffect
        val root = container.settingsRepository.storageRootState.value.root
        val sized = index.items
            .map { item -> item to runCatching { root.sizeOf(item.arcid) }.getOrDefault(0L) }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(10)
        cacheTop10 = sized
    }

    // 已完成任务完成后立即从队列消失（决策①）；顺序与派发器一致：优先级降序 → 创建时间升序
    val queuedTasks = remember(downloadTasks) {
        downloadTasks.filter { it.state != TaskState.DONE }
            .sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.createdAtEpochMs })
    }
    // 排队中任务的「前面 N 个」：仅统计排在它前面的等待任务
    val waitingAheadById = remember(queuedTasks) {
        val map = LinkedHashMap<String, Int>()
        var ahead = 0
        queuedTasks.forEach { task ->
            if (task.state == TaskState.WAITING) {
                map[task.id] = ahead
                ahead += 1
            }
        }
        map
    }

    val viewMode = settings?.galleryViewMode ?: "grid"
    val columns = settings?.galleryColumns ?: 3
    val workbenchVisible = selectedTab == 0

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

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // 状态汇总卡计数控点击 → 滚动定位到对应状态的第一个任务（尽力而为）
    fun scrollToTaskState(state: TaskState) {
        if (!workbenchVisible) return
        var base = 0
        if (enqueueRejection != null) base += 1 // 存储拒绝横幅
        if (pauseNotice != null) base += 1 // 认证失败自动暂停提示横幅
        base += 2 // 状态汇总卡 + 缓存管理卡
        if (serverTasks.isNotEmpty()) base += 1 // 服务器任务区
        if (queuedTasks.isNotEmpty()) base += 1 // 下载队列标题
        val offset = queuedTasks.indexOfFirst { it.state == state }
        if (offset < 0) return
        val target = base + offset
        scope.launch {
            runCatching {
                if (viewMode == "list") listState.animateScrollToItem(target) else gridState.animateScrollToItem(target)
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
                        Icon(Icons.Filled.MoreVert, contentDescription = "下载任务操作")
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
                        DropdownMenuItem(
                            text = { Text("自动恢复下载") },
                            trailingIcon = {
                                Checkbox(checked = autoResumeDownloadEnabled.value, onCheckedChange = null)
                            },
                            onClick = {
                                autoResumeDownloadEnabled.value = !autoResumeDownloadEnabled.value
                                if (autoResumeDownloadEnabled.value) container.downloadManager.resumeAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("速率限制（${settings?.downloadRatePerSecond ?: 2}/s）") },
                            onClick = {
                                showQueueMenu = false
                                showRateDialog = true
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("清空任务") },
                            enabled = downloadTasks.any { it.state == TaskState.DONE || it.state == TaskState.FAILED },
                            onClick = {
                                showQueueMenu = false
                                showClearAllConfirm = true
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

            if (!workbenchVisible && displayedItems.isEmpty()) {
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
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            workbenchItems(
                                workbenchVisible = workbenchVisible,
                                enqueueRejection = enqueueRejection,
                                onDismissRejection = container.downloadManager::dismissEnqueueRejection,
                                pauseNotice = pauseNotice,
                                onDismissPauseNotice = container.durableDownloadCoordinator::dismissPauseNotice,
                                maxRetries = maxRetries,
                                storageRecoveryNeeded = storageRecoveryNeeded,
                                onPickStorageFolder = onPickStorageFolder,
                                onUseDefaultStorage = onUseDefaultStorage,
                                runningCount = queuedTasks.count { it.state == TaskState.RUNNING },
                                pausedCount = queuedTasks.count { it.state == TaskState.PAUSED },
                                failedCount = queuedTasks.count { it.state == TaskState.FAILED },
                                totalSpeed = totalSpeedBytesPerSecond,
                                activeCount = activeCount,
                                concurrencyLimit = settings?.downloadConcurrency ?: 2,
                                ratePerSecond = settings?.downloadRatePerSecond ?: 2,
                                onScrollToState = ::scrollToTaskState,
                                usage = offlineUsage,
                                limitBytes = settings?.offlineCacheLimitBytes ?: 0L,
                                storageState = storageState,
                                cacheTop10 = cacheTop10,
                                cacheTop10Expanded = showCacheTop10,
                                onToggleCacheTop10 = { showCacheTop10 = !showCacheTop10 },
                                onLruCleanup = { showLruConfirm = true },
                                onDeleteCached = container.offlineCache::delete,
                                serverTasks = serverTasks,
                                repository = container.repository,
                                queuedTasks = queuedTasks,
                                waitingAheadById = waitingAheadById,
                                taskSpeeds = taskSpeeds,
                                onPauseTask = container.downloadManager::pause,
                                onResumeTask = container.downloadManager::resume,
                                onRetryTask = container.downloadManager::retry,
                                onRemoveTask = container.downloadManager::remove,
                                onSetPriority = container.downloadManager::setPriority,
                                onPauseAll = container.downloadManager::pauseAll,
                                coverResolver = container.offlineCache::coverUri,
                                displayedItems = displayedItems,
                                emptyArchivesHint = workbenchVisible && displayedItems.isEmpty(),
                                compactMode = viewMode == "compact",
                                vm = vm,
                                navController = navController,
                                context = context,
                                container = container,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            workbenchGridItems(
                                columns = columns,
                                workbenchVisible = workbenchVisible,
                                enqueueRejection = enqueueRejection,
                                onDismissRejection = container.downloadManager::dismissEnqueueRejection,
                                pauseNotice = pauseNotice,
                                onDismissPauseNotice = container.durableDownloadCoordinator::dismissPauseNotice,
                                maxRetries = maxRetries,
                                storageRecoveryNeeded = storageRecoveryNeeded,
                                onPickStorageFolder = onPickStorageFolder,
                                onUseDefaultStorage = onUseDefaultStorage,
                                runningCount = queuedTasks.count { it.state == TaskState.RUNNING },
                                pausedCount = queuedTasks.count { it.state == TaskState.PAUSED },
                                failedCount = queuedTasks.count { it.state == TaskState.FAILED },
                                totalSpeed = totalSpeedBytesPerSecond,
                                activeCount = activeCount,
                                concurrencyLimit = settings?.downloadConcurrency ?: 2,
                                ratePerSecond = settings?.downloadRatePerSecond ?: 2,
                                onScrollToState = ::scrollToTaskState,
                                usage = offlineUsage,
                                limitBytes = settings?.offlineCacheLimitBytes ?: 0L,
                                storageState = storageState,
                                cacheTop10 = cacheTop10,
                                cacheTop10Expanded = showCacheTop10,
                                onToggleCacheTop10 = { showCacheTop10 = !showCacheTop10 },
                                onLruCleanup = { showLruConfirm = true },
                                onDeleteCached = container.offlineCache::delete,
                                serverTasks = serverTasks,
                                repository = container.repository,
                                queuedTasks = queuedTasks,
                                waitingAheadById = waitingAheadById,
                                taskSpeeds = taskSpeeds,
                                onPauseTask = container.downloadManager::pause,
                                onResumeTask = container.downloadManager::resume,
                                onRetryTask = container.downloadManager::retry,
                                onRemoveTask = container.downloadManager::remove,
                                onSetPriority = container.downloadManager::setPriority,
                                onPauseAll = container.downloadManager::pauseAll,
                                coverResolver = container.offlineCache::coverUri,
                                displayedItems = displayedItems,
                                emptyArchivesHint = workbenchVisible && displayedItems.isEmpty(),
                                compactMode = viewMode == "compact",
                                vm = vm,
                                navController = navController,
                                context = context,
                                container = container,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清空任务记录？") },
            text = { Text("只会清理已完成或失败的任务记录，不会影响进行中、等待或暂停的下载，也不会删除离线档案。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.downloadManager.clearFinished()
                        showClearAllConfirm = false
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showLruConfirm) {
        AlertDialog(
            onDismissRequest = { showLruConfirm = false },
            title = { Text("LRU 清理缓存？") },
            text = {
                Text(
                    if ((settings?.offlineCacheLimitBytes ?: 0L) <= 0L) {
                        "尚未设置缓存上限，清理不会移除任何档案。可先在设置中配置缓存上限。"
                    } else {
                        "将按最近访问时间从旧到新移除离线档案，直到占用回落到缓存上限以内。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLruConfirm = false
                        if ((settings?.offlineCacheLimitBytes ?: 0L) > 0L) {
                            vm.runLruCleanup()
                        }
                    },
                ) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showLruConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showRateDialog) {
        var sliderValue by remember(showRateDialog) {
            mutableStateOf((settings?.downloadRatePerSecond ?: 2).toFloat())
        }
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("速率限制") },
            text = {
                Column {
                    Text("每秒最多启动 ${sliderValue.roundToInt()} 个下载任务（1–10），与并发上限同时生效。")
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        onValueChangeFinished = { vm.saveDownloadRatePerSecond(sliderValue.roundToInt()) },
                    )
                    Text(
                        "当前：${sliderValue.roundToInt()} 个/秒",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveDownloadRatePerSecond(sliderValue.roundToInt())
                        showRateDialog = false
                    },
                ) { Text("完成") }
            },
        )
    }
}

/** 「全部」工作台的公共 item 序列：横幅 → 汇总卡 → 缓存卡 → 服务器任务 → 下载队列 → 档案清单。 */
private fun LazyListScope.workbenchItems(
    workbenchVisible: Boolean,
    enqueueRejection: EnqueueRejection?,
    onDismissRejection: () -> Unit,
    pauseNotice: String?,
    onDismissPauseNotice: () -> Unit,
    maxRetries: Int,
    storageRecoveryNeeded: Boolean,
    onPickStorageFolder: () -> Unit,
    onUseDefaultStorage: () -> Unit,
    runningCount: Int,
    pausedCount: Int,
    failedCount: Int,
    totalSpeed: Float,
    activeCount: Int,
    concurrencyLimit: Int,
    ratePerSecond: Int,
    onScrollToState: (TaskState) -> Unit,
    usage: OfflineUsage,
    limitBytes: Long,
    storageState: StorageRootState,
    cacheTop10: List<Pair<CachedArchive, Long>>,
    cacheTop10Expanded: Boolean,
    onToggleCacheTop10: () -> Unit,
    onLruCleanup: () -> Unit,
    onDeleteCached: (String) -> Unit,
    serverTasks: List<ServerTask>,
    repository: LanraragiRepository,
    queuedTasks: List<DownloadTask>,
    waitingAheadById: Map<String, Int>,
    taskSpeeds: Map<String, Float>,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onSetPriority: (String, Int) -> Unit,
    onPauseAll: () -> Unit,
    coverResolver: suspend (String) -> Any?,
    displayedItems: List<Archive>,
    emptyArchivesHint: Boolean,
    compactMode: Boolean,
    vm: DownloadViewModel,
    navController: NavController?,
    context: android.content.Context,
    container: AppContainer,
) {
    if (workbenchVisible) {
        enqueueRejection?.let { rejection ->
            item(key = "enqueue-rejection") {
                EnqueueRejectionBanner(
                    rejection = rejection,
                    onDismiss = onDismissRejection,
                    storageRecoveryNeeded = storageRecoveryNeeded,
                    onPickStorageFolder = onPickStorageFolder,
                    onUseDefaultStorage = onUseDefaultStorage,
                )
            }
        }
        pauseNotice?.let { notice ->
            item(key = "auth-pause-notice") {
                AuthPauseNoticeBanner(notice = notice, onDismiss = onDismissPauseNotice)
            }
        }
        item(key = "status-summary") {
            StatusSummaryCard(
                runningCount = runningCount,
                pausedCount = pausedCount,
                failedCount = failedCount,
                totalSpeed = totalSpeed,
                activeCount = activeCount,
                concurrencyLimit = concurrencyLimit,
                ratePerSecond = ratePerSecond,
                onScrollToState = onScrollToState,
            )
        }
        item(key = "cache-management") {
            CacheManagementCard(
                usage = usage,
                limitBytes = limitBytes,
                storageState = storageState,
                top10 = cacheTop10,
                top10Expanded = cacheTop10Expanded,
                onToggleTop10 = onToggleCacheTop10,
                onLruCleanup = onLruCleanup,
                onDeleteCached = onDeleteCached,
                coverResolver = coverResolver,
            )
        }
        if (serverTasks.isNotEmpty()) {
            item(key = "server-tasks") { ServerTasksSection(serverTasks, repository) }
        }
        if (queuedTasks.isNotEmpty()) {
            item(key = "queue-header") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "下载队列（${queuedTasks.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onPauseAll,
                        enabled = queuedTasks.any { it.state == TaskState.WAITING || it.state == TaskState.RUNNING },
                    ) { Text("暂停全部") }
                }
            }
            items(items = queuedTasks, key = { task -> "task-${task.id}" }) { task ->
                DownloadTaskCard(
                    task = task,
                    waitingAhead = waitingAheadById[task.id] ?: 0,
                    speedBytesPerSecond = taskSpeeds[task.id] ?: 0f,
                    maxRetries = maxRetries,
                    coverModel = offlineCoverModel(task.arcid, coverResolver),
                    onPause = onPauseTask,
                    onResume = onResumeTask,
                    onRetry = onRetryTask,
                    onRemove = onRemoveTask,
                    onSetPriority = onSetPriority,
                )
            }
        }
        if (emptyArchivesHint) {
            item(key = "empty-archives") {
                Text(
                    "暂无离线档案，可在档案详情页添加离线缓存",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        }
    }
    items(displayedItems, key = { it.arcid }) { item ->
        ArchiveItemRow(
            item = item,
            vm = vm,
            navController = navController,
            context = context,
            container = container,
        )
    }
}

/** 网格视图的同序变体：工作台/任务区恒为整行列表形态（任务区不随网格切换）。 */private fun androidx.compose.foundation.lazy.grid.LazyGridScope.workbenchGridItems(
    columns: Int,
    workbenchVisible: Boolean,
    enqueueRejection: EnqueueRejection?,
    onDismissRejection: () -> Unit,
    pauseNotice: String?,
    onDismissPauseNotice: () -> Unit,
    maxRetries: Int,
    storageRecoveryNeeded: Boolean,
    onPickStorageFolder: () -> Unit,
    onUseDefaultStorage: () -> Unit,
    runningCount: Int,
    pausedCount: Int,
    failedCount: Int,
    totalSpeed: Float,
    activeCount: Int,
    concurrencyLimit: Int,
    ratePerSecond: Int,
    onScrollToState: (TaskState) -> Unit,
    usage: OfflineUsage,
    limitBytes: Long,
    storageState: StorageRootState,
    cacheTop10: List<Pair<CachedArchive, Long>>,
    cacheTop10Expanded: Boolean,
    onToggleCacheTop10: () -> Unit,
    onLruCleanup: () -> Unit,
    onDeleteCached: (String) -> Unit,
    serverTasks: List<ServerTask>,
    repository: LanraragiRepository,
    queuedTasks: List<DownloadTask>,
    waitingAheadById: Map<String, Int>,
    taskSpeeds: Map<String, Float>,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onSetPriority: (String, Int) -> Unit,
    onPauseAll: () -> Unit,
    coverResolver: suspend (String) -> Any?,
    displayedItems: List<Archive>,
    emptyArchivesHint: Boolean,
    compactMode: Boolean,
    vm: DownloadViewModel,
    navController: NavController?,
    context: android.content.Context,
    container: AppContainer,
) {
    val span: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan =
        { GridItemSpan(columns) }
    if (workbenchVisible) {
        enqueueRejection?.let { rejection ->
            item(key = "enqueue-rejection", span = span) {
                EnqueueRejectionBanner(
                    rejection = rejection,
                    onDismiss = onDismissRejection,
                    storageRecoveryNeeded = storageRecoveryNeeded,
                    onPickStorageFolder = onPickStorageFolder,
                    onUseDefaultStorage = onUseDefaultStorage,
                )
            }
        }
        pauseNotice?.let { notice ->
            item(key = "auth-pause-notice", span = span) {
                AuthPauseNoticeBanner(notice = notice, onDismiss = onDismissPauseNotice)
            }
        }
        item(key = "status-summary", span = span) {
            StatusSummaryCard(
                runningCount = runningCount,
                pausedCount = pausedCount,
                failedCount = failedCount,
                totalSpeed = totalSpeed,
                activeCount = activeCount,
                concurrencyLimit = concurrencyLimit,
                ratePerSecond = ratePerSecond,
                onScrollToState = onScrollToState,
            )
        }
        item(key = "cache-management", span = span) {
            CacheManagementCard(
                usage = usage,
                limitBytes = limitBytes,
                storageState = storageState,
                top10 = cacheTop10,
                top10Expanded = cacheTop10Expanded,
                onToggleTop10 = onToggleCacheTop10,
                onLruCleanup = onLruCleanup,
                onDeleteCached = onDeleteCached,
                coverResolver = coverResolver,
            )
        }
        if (serverTasks.isNotEmpty()) {
            item(key = "server-tasks", span = span) { ServerTasksSection(serverTasks, repository) }
        }
        if (queuedTasks.isNotEmpty()) {
            item(key = "queue-header", span = span) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "下载队列（${queuedTasks.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onPauseAll,
                        enabled = queuedTasks.any { it.state == TaskState.WAITING || it.state == TaskState.RUNNING },
                    ) { Text("暂停全部") }
                }
            }
            gridItems(items = queuedTasks, key = { task -> "task-${task.id}" }) { task ->
                DownloadTaskCard(
                    task = task,
                    waitingAhead = waitingAheadById[task.id] ?: 0,
                    speedBytesPerSecond = taskSpeeds[task.id] ?: 0f,
                    maxRetries = maxRetries,
                    coverModel = offlineCoverModel(task.arcid, coverResolver),
                    onPause = onPauseTask,
                    onResume = onResumeTask,
                    onRetry = onRetryTask,
                    onRemove = onRemoveTask,
                    onSetPriority = onSetPriority,
                )
            }
        }
        if (emptyArchivesHint) {
            item(key = "empty-archives", span = span) {
                Text(
                    "暂无离线档案，可在档案详情页添加离线缓存",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        }
    }
    gridItems(displayedItems, key = { it.arcid }) { item ->
        ArchiveItemRow(
            item = item,
            vm = vm,
            navController = navController,
            context = context,
            container = container,
            grid = true,
            compact = compactMode,
        )
    }
}

/**
 * 解析离线封面引用（Coil 模型）：经存储根抽象取值，默认根返回 `file://` URI，
 * 自定义 SAF 根返回 `content://` URI；封面缺失或解析失败时为 `null`，
 * 由调用方的占位（容器底色 / ArchiveCard 既有回退）承接。
 * 以 arcid 为键收集：同一档案在重组间保持同一模型（Coil 缓存键稳定），
 * 档案删除/重缓存后重新解析；resolver 实例经 rememberUpdatedState 跟随，不重启收集。
 */
@Composable
private fun offlineCoverModel(
    arcid: String,
    coverResolver: suspend (String) -> Any?,
): Any? {
    val resolver by rememberUpdatedState(coverResolver)
    return produceState<Any?>(null, arcid) {
        value = try {
            resolver(arcid)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }.value
}

/** 云端/本地档案清单行（列表形态用列表行，网格形态用卡片）。 */
@Composable
private fun ArchiveItemRow(
    item: Archive,
    vm: DownloadViewModel,
    navController: NavController?,
    context: android.content.Context,
    container: AppContainer,
    grid: Boolean = false,
    compact: Boolean = false,
) {
    val isLocal = item.arcid.startsWith("local_")
    if (isLocal) {
        val content: @Composable () -> Unit = {
            if (grid) {
                ArchiveCard(
                    archive = item,
                    onClick = {},
                    compact = compact,
                    isOffline = true,
                    offlineCover = ArchivePageModel(
                        Uri.parse(item.summary),
                        0,
                        context,
                        thumbnail = true,
                        revision = "index:${item.dateadded}:${item.pagecount}",
                    ).asImageRequest(),
                )
            } else {
                ArchiveListRow(
                    archive = item,
                    onClick = {},
                    isOffline = true,
                    offlineCover = ArchivePageModel(
                        Uri.parse(item.summary),
                        0,
                        context,
                        thumbnail = true,
                        revision = "index:${item.dateadded}:${item.pagecount}",
                    ).asImageRequest(),
                )
            }
        }
        LocalArchiveItem(
            uploading = item.arcid in vm.uploadingArcids,
            onUpload = { vm.uploadLocal(item) },
            onClick = { navController?.navigate(Routes.detail(item.arcid)) },
            content = content,
        )
    } else if (grid) {
        ArchiveCard(
            archive = item,
            onClick = { navController?.navigate(Routes.reader(item.arcid)) },
            compact = compact,
            isOffline = true,
            offlineCover = offlineCoverModel(item.arcid) { container.offlineCache.coverUri(it) },
            isPinned = item.arcid in vm.pinnedArcids,
            onTogglePin = { vm.togglePinned(item.arcid) },
        )
    } else {
        ArchiveListRow(
            archive = item,
            onClick = { navController?.navigate(Routes.reader(item.arcid)) },
            isOffline = true,
            offlineCover = offlineCoverModel(item.arcid) { container.offlineCache.coverUri(it) },
            isPinned = item.arcid in vm.pinnedArcids,
            onTogglePin = { vm.togglePinned(item.arcid) },
        )
    }
}

/**
 * 存储门禁拒绝横幅：展示最近一次入队拒绝原因，[知道了] 关闭。
 * 存储根确实缺失/授权失效时（[storageRecoveryNeeded]）额外提供
 * 「重新选择文件夹」（SAF OpenDocumentTree → 持久化授权 → saveStorageRoot）
 * 与「使用默认目录」（saveStorageRoot(null)）两个恢复入口。
 */
@Composable
private fun EnqueueRejectionBanner(
    rejection: EnqueueRejection,
    onDismiss: () -> Unit,
    storageRecoveryNeeded: Boolean = false,
    onPickStorageFolder: () -> Unit = {},
    onUseDefaultStorage: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "存储不可用",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "存储不可用",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    rejection.reason.ifBlank { "任务「${rejection.label}」未能入队" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
        if (storageRecoveryNeeded) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPickStorageFolder) { Text("重新选择文件夹") }
                TextButton(onClick = onUseDefaultStorage) { Text("使用默认目录") }
            }
        }
    }
}

/**
 * C3「特定错误自动暂停整队」提示横幅：401/403 认证失败后协调器已暂停整队，
 * 这里说明原因与恢复方式，[知道了] 关闭（关闭后新的认证失败才会再次提示）。
 */
@Composable
private fun AuthPauseNoticeBanner(
    notice: String,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "认证失败",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "认证失败，已自动暂停整队",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

/** 状态汇总卡：进行中/已暂停/失败计数控（点击滚动定位）+ 总速度 + 并发占用 + 速率设置值。 */
@Composable
private fun StatusSummaryCard(
    runningCount: Int,
    pausedCount: Int,
    failedCount: Int,
    totalSpeed: Float,
    activeCount: Int,
    concurrencyLimit: Int,
    ratePerSecond: Int,
    onScrollToState: (TaskState) -> Unit,
) {
    PlainCard {
        Row(Modifier.fillMaxWidth()) {
            SummaryChip("进行中", runningCount, MaterialTheme.colorScheme.primary) {
                onScrollToState(TaskState.RUNNING)
            }
            SummaryChip("已暂停", pausedCount, MaterialTheme.colorScheme.onSurfaceVariant) {
                onScrollToState(TaskState.PAUSED)
            }
            SummaryChip("失败", failedCount, MaterialTheme.colorScheme.error) {
                onScrollToState(TaskState.FAILED)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                append("${formatBytes(totalSpeed.toLong())}/s")
                append(" · 并发 ").append(activeCount).append("/").append(concurrencyLimit)
                append(" · 速率 ").append(ratePerSecond).append("/s")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.SummaryChip(
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 缓存管理卡：环形用量进度 + 上限 + LRU 清理（确认）+ 占用 Top10 展开 + 存储状态行。 */
@Composable
private fun CacheManagementCard(
    usage: OfflineUsage,
    limitBytes: Long,
    storageState: StorageRootState,
    top10: List<Pair<CachedArchive, Long>>,
    top10Expanded: Boolean,
    onToggleTop10: () -> Unit,
    onLruCleanup: () -> Unit,
    onDeleteCached: (String) -> Unit,
    coverResolver: suspend (String) -> Any?,
) {
    val progress = if (limitBytes > 0L) (usage.totalBytes.toFloat() / limitBytes).coerceIn(0f, 1f) else 0f
    PlainCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                UsageRing(progress, Modifier.fillMaxSize())
                Text(
                    if (limitBytes > 0L) "${(progress * 100).roundToInt()}%" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("离线缓存", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (limitBytes > 0L) {
                        "${formatBytes(usage.totalBytes)} / ${formatBytes(limitBytes)} · ${usage.count} 本"
                    } else {
                        "${formatBytes(usage.totalBytes)} · 未设上限 · ${usage.count} 本"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 存储状态行：存储根名称；degraded 时红字显示原因
                Text(
                    text = "存储位置：${storageState.root.displayName()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (storageState.degraded) {
                    Text(
                        text = storageState.degradedReason ?: "存储目录不可用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Row {
            TextButton(onClick = onLruCleanup) { Text("LRU 清理") }
            TextButton(onClick = onToggleTop10) {
                Text(if (top10Expanded) "收起 Top10" else "占用 Top10")
            }
        }
        if (top10Expanded) {
            if (top10.isEmpty()) {
                Text(
                    "暂无可统计的缓存项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                top10.forEach { (item, size) ->
                    val cover = offlineCoverModel(item.arcid, coverResolver)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = cover,
                                contentDescription = "封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title.ifBlank { item.arcid },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatBytes(size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteCached(item.arcid) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "清理此缓存",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 普通表面卡（状态汇总卡/缓存管理卡等，不用玻璃材质）。 */
@Composable
private fun PlainCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

/** 环形用量进度（Canvas 弧线）。 */
@Composable
private fun UsageRing(progress: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (progress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * 队列任务卡（C2/C3）：下载中/排队中/已暂停/失败四种状态渲染；已完成任务不入队列表。
 * 左滑操作简化为行内按钮（暂停/继续/重试/移除）；长按弹优先级菜单（高/中/低，落库任务优先级）。
 */
@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    waitingAhead: Int,
    speedBytesPerSecond: Float,
    maxRetries: Int,
    coverModel: Any?,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSetPriority: (String, Int) -> Unit,
) {
    var priorityMenuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { priorityMenuOpen = true },
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title.ifBlank { task.arcid.ifBlank { task.id } },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DownloadTaskStatusLine(task, waitingAhead, speedBytesPerSecond)
                    when (task.state) {
                        TaskState.FAILED -> DownloadTaskErrorRow(task, maxRetries)
                        TaskState.RUNNING -> {
                            if (task.progress != null) {
                                LinearProgressIndicator(
                                    progress = { task.progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                        else -> {}
                    }
                }
                Spacer(Modifier.width(8.dp))
                when (task.state) {
                    TaskState.RUNNING -> IconButton(onClick = { onPause(task.id) }) {
                        Icon(Icons.Filled.Pause, contentDescription = "暂停", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TaskState.PAUSED -> IconButton(onClick = { onResume(task.id) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "继续", tint = MaterialTheme.colorScheme.primary)
                    }
                    TaskState.FAILED -> if (task.category != DownloadFailureCategory.STORAGE) {
                        TextButton(onClick = { onRetry(task.id) }) { Text("重试") }
                    }
                    else -> IconButton(onClick = { priorityMenuOpen = true }) {
                        Icon(Icons.Filled.LowPriority, contentDescription = "优先级", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onRemove(task.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        DropdownMenu(expanded = priorityMenuOpen, onDismissRequest = { priorityMenuOpen = false }) {
            listOf(
                "高优先级" to TASK_PRIORITY_HIGH,
                "中优先级" to TASK_PRIORITY_NORMAL,
                "低优先级" to TASK_PRIORITY_LOW,
            ).forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (task.priority == value) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        priorityMenuOpen = false
                        onSetPriority(task.id, value)
                    },
                )
            }
        }
    }
}

/** 状态文案行（原 downloadStateLabel 逻辑重排进卡片内部）。 */
@Composable
private fun DownloadTaskStatusLine(task: DownloadTask, waitingAhead: Int, speedBytesPerSecond: Float) {
    val typeLabel = downloadTypeLabel(task.type)
    val text = when (task.state) {
        TaskState.RUNNING -> {
            val sizeLine = buildString {
                append(formatBytes(task.completedBytes))
                task.totalBytes?.let { append(" / ").append(formatBytes(it)) }
                if (task.progress != null) append(" · ${(task.progress * 100).toInt()}%")
                if (speedBytesPerSecond > 0f) append(" · ${formatBytes(speedBytesPerSecond.toLong())}/s")
            }
            "$typeLabel · $sizeLine"
        }
        TaskState.WAITING -> if (task.retryAtEpochMs != null) {
            if (task.retryCount > 0) "$typeLabel · 等待重试（已重试 ${task.retryCount} 次）" else "$typeLabel · 等待重试"
        } else {
            "$typeLabel · 排队中 · 前面 $waitingAhead 个"
        }
        TaskState.PAUSED -> "$typeLabel · 已暂停 · ${formatBytes(task.completedBytes)}"
        else -> typeLabel
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 失败态错误行：分类标签（DownloadFailureCategory.label）+ 截断的错误消息 + 存储引导。
 * C3 重试计数用当前设置的重试上限（[maxRetries]）作分母，而不是写死的 5。
 */
@Composable
private fun DownloadTaskErrorRow(task: DownloadTask, maxRetries: Int) {
    val category = task.category ?: DownloadFailureCategory.OTHER
    Spacer(Modifier.height(2.dp))
    Text(
        text = buildString {
            append("⚠ ").append(category.label)
            if (task.retryCount > 0) append(" · 已重试 ${task.retryCount}/$maxRetries 次")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    if (!task.error.isNullOrBlank()) {
        Text(
            text = task.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (category == DownloadFailureCategory.STORAGE) {
        Text(
            text = "请先处理存储目录（见缓存卡存储状态），暂不提供重试",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun LocalArchiveItem(
    uploading: Boolean,
    onUpload: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
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
private fun ServerTasksSection(tasks: List<ServerTask>, repository: LanraragiRepository) {
    var failedTask by remember { mutableStateOf<ServerTask?>(null) }
    Column {
        Text("服务器任务（${tasks.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        tasks.forEach { task ->
            ServerTaskRow(task, repository, onClick = { if (task.isFailed) failedTask = task })
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

/** 单条服务器任务：未结束时以 2s 间隔轮询整本页缩略图进度（行离开组合即停止轮询）。 */
@Composable
private fun ServerTaskRow(task: ServerTask, repository: LanraragiRepository, onClick: () -> Unit) {
    var thumbProgress by remember(task.jobid) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(task.jobid, task.isFinished) {
        if (task.isFinished) return@LaunchedEffect
        while (true) {
            thumbProgress = repository.minionPageThumbProgress(task.jobid)
            delay(2000)
        }
    }
    val failed = task.isFailed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = failed, onClick = onClick)
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
            thumbProgress?.let { (done, total) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$done/$total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stateLabel(task.state),
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/** 滑窗速度估算：EWMA 平滑的字节/秒；无样本时从 0 起步。 */
private class SpeedTracker {
    private var lastAt = 0L
    private var lastBytes = 0L
    var bytesPerSecond = 0f
        private set

    fun update(bytes: Long, now: Long) {
        if (lastAt == 0L) {
            lastAt = now
            lastBytes = bytes
            return
        }
        val dtSeconds = (now - lastAt) / 1000f
        if (dtSeconds < 0.25f) return
        val instant = (bytes - lastBytes).coerceAtLeast(0L) / dtSeconds
        bytesPerSecond = if (bytesPerSecond == 0f) instant else bytesPerSecond * 0.6f + instant * 0.4f
        lastAt = now
        lastBytes = bytes
    }
}

private fun downloadTypeLabel(type: DownloadTaskType): String = when (type) {
    DownloadTaskType.OFFLINE_CACHE -> "离线缓存"
    DownloadTaskType.ARCHIVE_FILE -> "原档下载"
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
    if (bytes <= 0) return "0 KB"
    val kb = bytes.toDouble() / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(java.util.Locale.getDefault(), "%.0f KB", kb)
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
