package com.lanraragi.reader.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.LoadingImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** 阅读页点击区域（借鉴 JHenTai 的三区点击交互）。 */
private enum class TapRegion { LEFT, CENTER, RIGHT }

/** 图片适应模式 → ContentScale。 */
private fun fitContentScale(mode: String): ContentScale = when (mode) {
    "fitHeight" -> ContentScale.FillHeight
    "fitScreen" -> ContentScale.Fit
    "original" -> ContentScale.None
    else -> ContentScale.FillWidth
}

/** 阅读器背景色。 */
private fun readerBackgroundColor(mode: String): Color = when (mode) {
    "dark" -> Color(0xFF1A1A1A)
    "gray" -> Color(0xFF2E2E2E)
    "white" -> Color.White
    else -> Color.Black
}

private fun fitModeLabel(mode: String): String = when (mode) {
    "fitHeight" -> "适应高"
    "fitScreen" -> "适应屏"
    "original" -> "原始"
    else -> "适应宽"
}

data class ArchivePageModel(
    val archiveUri: Uri,
    val pageIndex: Int,
    val context: Context
)

class ReaderViewModel(
    private val container: AppContainer,
    private val arcid: String,
    private val initialPage: Int? = null,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val tags: String = "",
        val pageCount: Int = 0,
        val onlinePages: List<String> = emptyList(),
        val offline: Boolean = false,
        val progress: Int = 0,
        val currentPage: Int = 0,
        val readerMode: String = "single",
        val multiPageCount: Int = 2,
        val preloadOnlineCount: Int = 3,
        val readingDirection: String = "ltr",
        val readerFitMode: String = "fitWidth",
        val readerBackground: String = "black",
        val tapZonesEnabled: Boolean = true,
        val keepScreenOn: Boolean = true,
        val volumeKeysEnabled: Boolean = true,
        val preloadLocalCount: Int = 5,
        val localPages: List<ArchiveFileReader.ArchiveEntry> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var syncJob: Job? = null
    private var lastSynced = -1

    init {
        load()
        viewModelScope.launch {
            container.settingsRepository.setLastReadArcId(arcid)
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val settings = container.settingsRepository.settings.first()
                _state.update {
                    it.copy(
                        readerMode = settings.readerMode,
                        multiPageCount = settings.multiPageCount,
                        preloadOnlineCount = settings.preloadOnlineCount,
                        readingDirection = settings.readingDirection,
                        readerFitMode = settings.readerFitMode,
                        readerBackground = settings.readerBackground,
                        tapZonesEnabled = settings.tapZonesEnabled,
                        keepScreenOn = settings.keepScreenOn,
                        volumeKeysEnabled = settings.volumeKeysEnabled,
                        preloadLocalCount = settings.preloadLocalCount,
                    )
                }

                if (arcid.startsWith("local_")) {
                    // 1. 本地档案模式
                    val local = container.localScanManager.localArchives.value.find { it.arcid == arcid }
                        ?: throw Exception("本地文件未找到")
                    val uri = Uri.parse(local.summary)
                    val images = ArchiveFileReader.getImages(container.context, uri)
                    _state.update {
                        it.copy(
                            offline = true,
                            pageCount = images.size,
                            localPages = images,
                            title = local.title,
                            currentPage = 0,
                            loading = false,
                        )
                    }
                } else {
                    val cached = container.offlineCache.cached(arcid)
                    val offlineFile = container.offlineCache.archiveFile(arcid)
                    
                    if (offlineFile.exists()) {
                        // 2. 离线原始文件模式
                        val images = ArchiveFileReader.getImages(container.context, Uri.fromFile(offlineFile))
                        _state.update {
                            it.copy(
                                offline = true,
                                pageCount = images.size,
                                localPages = images,
                                title = cached?.title ?: "",
                                currentPage = 0,
                                loading = false,
                            )
                        }
                    } else if (cached != null && cached.pageCount > 0) {
                        // 3. 兼容旧版：离线分页模式
                        _state.update {
                            it.copy(
                                offline = true,
                                pageCount = cached.pageCount,
                                title = cached.title,
                                currentPage = 0,
                                loading = false,
                            )
                        }
                    } else {
                        // 4. 云端模式
                        val meta = container.repository.getMetadata(arcid)
                        val pageUrls = container.repository.getPageUrls(arcid)
                        val requested = initialPage?.coerceIn(0, (pageUrls.size - 1).coerceAtLeast(0))
                        val start = requested ?: (meta.progress - 1).coerceIn(0, (pageUrls.size - 1).coerceAtLeast(0))
                        _state.update {
                            it.copy(
                                offline = false,
                                pageCount = pageUrls.size,
                                onlinePages = pageUrls,
                                title = meta.title,
                                tags = meta.tags,
                                progress = meta.progress,
                                currentPage = start,
                                loading = false,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun pageModel(index: Int): Any {
        val s = _state.value
        return when {
            s.offline && s.localPages.isNotEmpty() -> {
                val uri = if (arcid.startsWith("local_")) {
                    val local = container.localScanManager.localArchives.value.find { it.arcid == arcid }
                    Uri.parse(local?.summary ?: "")
                } else {
                    Uri.fromFile(container.offlineCache.archiveFile(arcid))
                }
                ArchivePageModel(uri, index, container.context)
            }
            s.offline -> {
                container.offlineCache.pageFile(arcid, index)
            }
            else -> {
                s.onlinePages[index]
            }
        }
    }

    fun reportPage(page: Int) {
        val s = _state.value
        if (page < 0 || page >= s.pageCount) return
        _state.update { it.copy(currentPage = page) }
        if (s.offline) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(700)
            val p = _state.value.currentPage
            if (p >= 0 && p != lastSynced) {
                lastSynced = p
                runCatching { container.repository.setProgress(arcid, p + 1) }
            }
        }
    }

    fun downloadCurrentPage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            if (s.pageCount <= 0) return@launch
            val idx = s.currentPage.coerceIn(0, s.pageCount - 1)
            try {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "收藏")
                dir.mkdirs()
                val dest = File(dir, "${arcid}_${idx + 1}.jpg")
                if (s.offline) {
                    container.offlineCache.pageFile(arcid, idx).copyTo(dest, overwrite = true)
                } else {
                    val url = s.onlinePages[idx]
                    val req = Request.Builder().url(url).build()
                    ApiClient.okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { input ->
                                FileOutputStream(dest).use { out -> input.copyTo(out) }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已收藏到收藏文件夹", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "收藏失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun cycleLayout() {
        val next = when (_state.value.readerMode) {
            "single" -> "multi"
            "multi" -> "continuous"
            else -> "single"
        }
        _state.update { it.copy(readerMode = next) }
        viewModelScope.launch { container.settingsRepository.setReaderMode(next) }
    }

    fun cycleFitMode() {
        val next = when (_state.value.readerFitMode) {
            "fitWidth" -> "fitHeight"
            "fitHeight" -> "fitScreen"
            "fitScreen" -> "original"
            else -> "fitWidth"
        }
        _state.update { it.copy(readerFitMode = next) }
        viewModelScope.launch { container.settingsRepository.setReaderFitMode(next) }
    }

    fun toggleDirection() {
        val next = if (_state.value.readingDirection == "rtl") "ltr" else "rtl"
        _state.update { it.copy(readingDirection = next) }
        viewModelScope.launch { container.settingsRepository.setReadingDirection(next) }
    }

    override fun onCleared() {
        syncJob?.cancel()
        val s = _state.value
        if (!s.offline && s.currentPage in 0 until s.pageCount) {
            container.applicationScope.launch {
                runCatching { container.repository.setProgress(arcid, s.currentPage + 1) }
            }
        }
    }
}

@Composable
fun ReaderScreen(container: AppContainer, arcid: String, navController: NavController, initialPage: Int? = null) {
    val vm: ReaderViewModel = viewModel { ReaderViewModel(container, arcid, initialPage) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Color.Black).edgeSwipeBack { navController.popBackStack() }) {
        when {
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load)
            state.pageCount <= 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有可显示的页面", color = Color.White)
            }
            else -> ReaderContent(vm, state, navController)
        }
    }
}

@Composable
private fun ReaderContent(
    vm: ReaderViewModel,
    state: ReaderViewModel.UiState,
    navController: NavController,
) {
    var showUi by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // 唤出 UI 后 2s 自动隐藏逻辑
    LaunchedEffect(showUi, lastInteraction) {
        if (showUi) {
            delay(2000)
            showUi = false
        }
    }

    val bgColor = readerBackgroundColor(state.readerBackground)
    val fitScale = fitContentScale(state.readerFitMode)

    val models = remember(state.pageCount, state.offline, state.onlinePages) {
        (0 until state.pageCount).map { vm.pageModel(it) }
    }
    val reverse = state.readingDirection == "rtl"
    val pagesPerScreen = if (state.readerMode == "multi") state.multiPageCount else 1
    val preloadCount = if (state.offline) state.preloadLocalCount else state.preloadOnlineCount
    val screens = ((models.size + pagesPerScreen - 1) / pagesPerScreen).coerceAtLeast(1)
    val startScreen = (state.currentPage / pagesPerScreen).coerceIn(0, screens - 1)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
    val pagerState = rememberPagerState(initialPage = startScreen) { screens }

    // 阅读时保持屏幕常亮（JHenTai 的 wakelock 行为）。
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    fun jumpTo(idx: Int) {
        val page = idx.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (state.readerMode == "continuous") {
                listState.animateScrollToItem(page)
            } else {
                pagerState.animateScrollToPage(page / pagesPerScreen)
            }
        }
        vm.reportPage(page)
    }

    fun stepScreen(delta: Int) {
        val per = if (state.readerMode == "continuous") 1 else pagesPerScreen
        jumpTo(state.currentPage + delta * per)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!state.volumeKeysEnabled || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.VolumeUp -> { stepScreen(-1); true }
                    Key.VolumeDown -> { stepScreen(+1); true }
                    else -> false
                }
            },
    ) {
        if (state.readerMode == "continuous") {
            VerticalReader(listState, models, fitScale, vm::reportPage) { showUi = !showUi }
        } else {
            HorizontalReader(
                pagerState = pagerState,
                models = models,
                reverse = reverse,
                pagesPerScreen = pagesPerScreen,
                preloadCount = preloadCount,
                fitScale = fitScale,
                tapZonesEnabled = state.tapZonesEnabled,
                onPage = vm::reportPage,
                onToggleUi = { showUi = !showUi },
            )
        }

        AnimatedVisibility(visible = showUi, modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                current = state.currentPage + 1,
                total = state.pageCount,
                onBack = { navController.popBackStack() },
            )
        }

        AnimatedVisibility(visible = showUi, modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(
                current = state.currentPage,
                total = state.pageCount,
                mode = state.readerMode,
                direction = state.readingDirection,
                fitMode = state.readerFitMode,
                onJump = {
                    lastInteraction = System.currentTimeMillis()
                    jumpTo(it)
                },
                onPrev = {
                    lastInteraction = System.currentTimeMillis()
                    stepScreen(-1)
                },
                onNext = {
                    lastInteraction = System.currentTimeMillis()
                    stepScreen(+1)
                },
                onToggleMode = {
                    lastInteraction = System.currentTimeMillis()
                    vm.cycleLayout()
                },
                onToggleDirection = {
                    lastInteraction = System.currentTimeMillis()
                    vm.toggleDirection()
                },
                onToggleFitMode = {
                    lastInteraction = System.currentTimeMillis()
                    vm.cycleFitMode()
                },
                onInfo = {
                    lastInteraction = System.currentTimeMillis()
                    showInfo = true
                },
                onFavorite = {
                    lastInteraction = System.currentTimeMillis()
                    vm.downloadCurrentPage(context)
                },
                models = models,
            )
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(state.title.ifBlank { "原档信息" }) },
            text = {
                Column {
                    Text("共 ${state.pageCount} 页")
                    if (state.tags.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("标签：${state.tags}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("关闭") }
            },
        )
    }

    // 首帧及工具栏切换后把焦点还给阅读容器，保证音量键翻页生效。
    LaunchedEffect(Unit, showUi) { focusRequester.requestFocus() }
}

@Composable
private fun VerticalReader(
    listState: LazyListState,
    models: List<Any>,
    fitScale: ContentScale,
    onPage: (Int) -> Unit,
    onToggleUi: () -> Unit,
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onPage(it) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }) },
    ) {
        itemsIndexed(models) { index, model ->
            LoadingImage(
                model = model,
                contentDescription = "第 ${index + 1} 页",
                contentScale = fitScale,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HorizontalReader(
    pagerState: PagerState,
    models: List<Any>,
    reverse: Boolean,
    pagesPerScreen: Int,
    preloadCount: Int,
    fitScale: ContentScale,
    tapZonesEnabled: Boolean,
    onPage: (Int) -> Unit,
    onToggleUi: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onPage(it * pagesPerScreen) }
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = reverse,
        beyondViewportPageCount = preloadCount,
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        val start = screen * pagesPerScreen
        Row(Modifier.fillMaxSize()) {
            repeat(pagesPerScreen) { i ->
                val idx = start + i
                if (idx < models.size) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ZoomablePage(
                            model = models[idx],
                            modifier = Modifier.fillMaxSize(),
                            fitScale = fitScale,
                            tapZonesEnabled = tapZonesEnabled,
                            onTapRegion = { region ->
                                when (region) {
                                    TapRegion.CENTER -> onToggleUi()
                                    TapRegion.LEFT -> {
                                        val target = pagerState.currentPage + if (reverse) 1 else -1
                                        scope.launch {
                                            pagerState.animateScrollToPage(target.coerceIn(0, pagerState.pageCount - 1))
                                        }
                                    }
                                    TapRegion.RIGHT -> {
                                        val target = pagerState.currentPage + if (reverse) -1 else 1
                                        scope.launch {
                                            pagerState.animateScrollToPage(target.coerceIn(0, pagerState.pageCount - 1))
                                        }
                                    }
                                }
                            },
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    model: Any,
    modifier: Modifier,
    fitScale: ContentScale,
    tapZonesEnabled: Boolean,
    onTapRegion: (TapRegion) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var loading by remember(model) { mutableStateOf(true) }

    LaunchedEffect(model) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier
            .clipToBounds()
            .pointerInput(scale > 1f) {
                if (scale > 1f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        if (!tapZonesEnabled) {
                            onTapRegion(TapRegion.CENTER)
                        } else {
                            val w = size.width.toFloat()
                            onTapRegion(
                                when {
                                    tapOffset.x < w / 3f -> TapRegion.LEFT
                                    tapOffset.x > w * 2f / 3f -> TapRegion.RIGHT
                                    else -> TapRegion.CENTER
                                }
                            )
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = fitScale,
            onLoading = { loading = true },
            onSuccess = { loading = false },
            onError = { loading = false },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    current: Int,
    total: Int,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Text("$current / $total", color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReaderBottomBar(
    current: Int,
    total: Int,
    mode: String,
    direction: String,
    fitMode: String,
    onJump: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleDirection: () -> Unit,
    onToggleFitMode: () -> Unit,
    onInfo: () -> Unit,
    onFavorite: () -> Unit,
    models: List<Any>,
) {
    var sliderValue by remember { mutableFloatStateOf(current.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(current) { sliderValue = current.toFloat() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (isDragging) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val idx = sliderValue.toInt().coerceIn(0, (models.size - 1).coerceAtLeast(0))
                if (models.isNotEmpty()) {
                    AsyncImage(
                        model = models[idx],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(56.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("第 ${idx + 1} / $total 页", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, enabled = total > 1 && current > 0) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一页", tint = Color.White)
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it; isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    onJump(sliderValue.toInt().coerceAtMost(total - 1))
                },
                valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                enabled = total > 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = total > 1 && current < total - 1) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一页", tint = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = onToggleMode) {
                Text(
                    when (mode) {
                        "multi" -> "多页"
                        "continuous" -> "连续"
                        else -> "单页"
                    },
                    color = Color.White,
                )
            }
            TextButton(onClick = onToggleDirection) {
                Text(if (direction == "rtl") "右→左" else "左→右", color = Color.White)
            }
            TextButton(onClick = onToggleFitMode) {
                Text(fitModeLabel(fitMode), color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onInfo) { Text("信息", color = Color.White) }
            TextButton(onClick = onFavorite) { Text("收藏", color = Color.White) }
        }
    }
}
