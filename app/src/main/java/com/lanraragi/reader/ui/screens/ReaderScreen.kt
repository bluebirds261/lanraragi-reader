package com.lanraragi.reader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.DownloadTaskType
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.TocEntry
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.edgeSwipeBack
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

/** 阅读器背景色；"auto" 按系统深浅色映射到黑/深灰。 */
@Composable
private fun readerBackgroundColor(mode: String): Color = when (mode) {
    "dark" -> Color(0xFF1A1A1A)
    "gray" -> Color(0xFF2E2E2E)
    "white" -> Color.White
    "auto" -> if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color.Black
    else -> Color.Black
}

private fun fitModeLabel(mode: String): String = when (mode) {
    "fitHeight" -> "适应高"
    "fitScreen" -> "适应屏"
    "original" -> "原始"
    else -> "适应宽"
}

/** 沿 Context 链找到所属 Activity（LocalContext.current 可能是 ContextWrapper）。 */
private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 设置阅读窗口亮度；value 为 -1f（BRIGHTNESS_OVERRIDE_NONE，跟随系统）或 0f~1f。 */
private fun setWindowBrightness(context: Context, value: Float) {
    context.findActivity()?.window?.let { w ->
        w.attributes = w.attributes.apply { screenBrightness = value }
    }
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
        val autoDoublePageLandscape: Boolean = true,
        val preloadOnlineCount: Int = 3,
        val readingDirection: String = "ltr",
        val autoScrollSpeed: String = "off",
        val autoScrolling: Boolean = false,
        val readerFitMode: String = "fitWidth",
        val readerBackground: String = "black",
        val readerBrightness: Int = -1,
        val tapZonesEnabled: Boolean = true,
        val keepScreenOn: Boolean = true,
        val volumeKeysEnabled: Boolean = true,
        val preloadLocalCount: Int = 5,
        val localPages: List<ArchiveFileReader.ArchiveEntry> = emptyList(),
        val toc: List<TocEntry> = emptyList(),
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
                        autoDoublePageLandscape = settings.autoDoublePageLandscape,
                        preloadOnlineCount = settings.preloadOnlineCount,
                        readingDirection = settings.readingDirection,
                        autoScrollSpeed = settings.autoScrollSpeed,
                        readerFitMode = settings.readerFitMode,
                        readerBackground = settings.readerBackground,
                        readerBrightness = settings.readerBrightness,
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
                                toc = cached?.metadata?.toc ?: emptyList(),
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
                                toc = cached.metadata?.toc ?: emptyList(),
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
                                toc = meta.toc,
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

    fun downloadCurrentPage(context: Context, page: Int? = null) {
        val s = _state.value
        if (s.pageCount <= 0) return
        val idx = (page ?: s.currentPage).coerceIn(0, s.pageCount - 1)
        val appContext = context.applicationContext
        container.downloadManager.enqueue(DownloadTaskType.PAGE, arcid, "第 ${idx + 1} 页") {
            val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir, "收藏")
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
                    } else {
                        throw IllegalStateException("下载失败 HTTP ${resp.code}")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "已收藏到收藏文件夹", Toast.LENGTH_SHORT).show()
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, "已加入下载队列", Toast.LENGTH_SHORT).show()
        }
    }

    /** C4：把指定页（1 起）设为档案封面（复用 A5 端点），成功后刷新图库封面缓存。 */
    fun setCoverFromPage(page: Int) {
        viewModelScope.launch {
            try {
                container.repository.setThumbnailFromPage(arcid, page.coerceAtLeast(1))
                CoverChangeBus.version.value++
                LibraryRefreshBus.tick.value++
                Toast.makeText(container.context, "封面已更新", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(container.context, e.message ?: "更换封面失败", Toast.LENGTH_SHORT).show()
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

    fun setReaderBrightness(n: Int) {
        val v = n.coerceIn(-1, 100)
        _state.update { it.copy(readerBrightness = v) }
        viewModelScope.launch { container.settingsRepository.setReaderBrightness(v) }
    }

    fun setAutoScrollSpeed(s: String) {
        _state.update { it.copy(autoScrollSpeed = s) }
        viewModelScope.launch { container.settingsRepository.setAutoScrollSpeed(s) }
    }

    /** C6：播放/暂停连续模式自动滚动；仅连续模式生效。 */
    fun toggleAutoScroll() {
        if (_state.value.readerMode != "continuous") return
        _state.update { it.copy(autoScrolling = !it.autoScrolling) }
    }

    override fun onCleared() {
        syncJob?.cancel()
        val s = _state.value
        val page = if (s.currentPage in 0 until s.pageCount) s.currentPage + 1 else null
        // local_ 档案在服务器不存在，跳过；无有效页码也跳过。
        if (page == null || arcid.startsWith("local_")) return

        // B4: 会话结束回写进度（服务器从 1 计）。对非 local_ 且有有效页码的档案
        // 一律尝试直连服务器，失败（含未配置服务器/离线不可达）记入待同步队列，
        // 留待下次启动或联网后补推。取消异常直接向上抛出，不吞。
        container.applicationScope.launch {
            try {
                container.repository.setProgress(arcid, page)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                container.pendingProgress.record(arcid, page)
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
            else -> ReaderContent(vm, state, navController, arcid)
        }
    }
}

@Composable
private fun ReaderContent(
    vm: ReaderViewModel,
    state: ReaderViewModel.UiState,
    navController: NavController,
    arcid: String,
) {
    var showUi by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var pageMenuIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // 唤出 UI 后 2s 自动隐藏逻辑
    LaunchedEffect(showUi, lastInteraction, showGrid) {
        if (showUi && !showGrid) {
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val pagesPerScreen = when {
        state.readerMode == "multi" -> state.multiPageCount
        state.readerMode == "single" && state.autoDoublePageLandscape && isLandscape -> 2
        else -> 1
    }
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

    // 进入阅读时应用偏好亮度；-1 表示跟随系统。
    LaunchedEffect(Unit) {
        setWindowBrightness(
            context,
            if (state.readerBrightness >= 0) state.readerBrightness / 100f
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
        )
    }

    // 退出阅读（含异常/返回导航）恢复系统亮度。
    DisposableEffect(Unit) {
        onDispose { setWindowBrightness(context, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }

    // C6：连续模式自动滚动。按速度平滑 scrollBy；到底自动停；LaunchedEffect 取消时协程自然取消。
    LaunchedEffect(state.autoScrolling, state.autoScrollSpeed, state.readerMode) {
        if (state.readerMode != "continuous" || !state.autoScrolling || state.autoScrollSpeed == "off") {
            return@LaunchedEffect
        }
        val pxPerSec = when (state.autoScrollSpeed) {
            "slow" -> 40f
            "medium" -> 80f
            "fast" -> 140f
            else -> 0f
        }
        if (pxPerSec <= 0f) return@LaunchedEffect
        while (true) {
            if (!listState.canScrollForward) {
                vm.toggleAutoScroll()
                break
            }
            listState.scrollBy(pxPerSec * 0.016f)
            delay(16)
        }
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

    fun openPageMenu(idx: Int) {
        lastInteraction = System.currentTimeMillis()
        pageMenuIndex = idx
    }

    fun copyPageLink(idx: Int) {
        if (state.offline) {
            Toast.makeText(context, "离线档案无链接", Toast.LENGTH_SHORT).show()
            return
        }
        if (idx !in state.onlinePages.indices) return
        val url = ApiClient.displayBaseUrl().trimEnd('/') + "/" +
            state.onlinePages[idx].removePrefix(ApiClient.SENTINEL_BASE)
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("页面链接", url))
        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
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
            VerticalReader(
                listState = listState,
                models = models,
                fitScale = fitScale,
                onPage = vm::reportPage,
                onToggleUi = { showUi = !showUi },
                onPageLongPress = { openPageMenu(it) },
            )
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
                onPageLongPress = { openPageMenu(it) },
            )
        }

        AnimatedVisibility(visible = showUi, modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                current = state.currentPage + 1,
                total = state.pageCount,
                continuous = state.readerMode == "continuous",
                autoScrolling = state.autoScrolling,
                onToggleAutoScroll = vm::toggleAutoScroll,
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
                onOpenGrid = {
                    lastInteraction = System.currentTimeMillis()
                    showGrid = true
                },
                toc = state.toc,
                onOpenToc = {
                    lastInteraction = System.currentTimeMillis()
                    showToc = true
                },
                onFavorite = {
                    lastInteraction = System.currentTimeMillis()
                    vm.downloadCurrentPage(context)
                },
                brightness = state.readerBrightness,
                onBrightnessChange = { n ->
                    lastInteraction = System.currentTimeMillis()
                    vm.setReaderBrightness(n)
                    setWindowBrightness(
                        context,
                        if (n >= 0) n / 100f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                    )
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

    if (showGrid) {
        PageGridSheet(
            arcid = arcid,
            total = state.pageCount,
            currentPage = state.currentPage,
            offline = state.offline,
            models = models,
            onJump = { index ->
                lastInteraction = System.currentTimeMillis()
                jumpTo(index)
                showGrid = false
                focusRequester.requestFocus()
            },
            onDismiss = { showGrid = false },
        )
    }

    if (showToc) {
        TocSheet(
            toc = state.toc,
            onJump = { page ->
                lastInteraction = System.currentTimeMillis()
                jumpTo(page - 1)
                showToc = false
                focusRequester.requestFocus()
            },
            onDismiss = { showToc = false },
        )
    }

    pageMenuIndex?.let { menuIdx ->
        PageMenuSheet(
            pageNumber = menuIdx + 1,
            offline = state.offline,
            onSave = {
                vm.downloadCurrentPage(context, menuIdx)
                pageMenuIndex = null
            },
            onCopyLink = {
                copyPageLink(menuIdx)
                pageMenuIndex = null
            },
            onSetCover = {
                vm.setCoverFromPage(menuIdx + 1)
                pageMenuIndex = null
            },
            onShowInfo = {
                showInfo = true
                pageMenuIndex = null
            },
            onDismiss = { pageMenuIndex = null },
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
    onPageLongPress: (Int) -> Unit,
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onPage(it) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(models) { index, model ->
            Box(Modifier.fillMaxWidth()) {
                ZoomablePage(
                    model = model,
                    modifier = Modifier.fillMaxWidth(),
                    fitScale = fitScale,
                    tapZonesEnabled = false,
                    onTapRegion = { onToggleUi() },
                    onLongPress = { onPageLongPress(index) },
                )
            }
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
    onPageLongPress: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, pagesPerScreen) {
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
                            onLongPress = { onPageLongPress(idx) },
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
    onLongPress: (() -> Unit)? = null,
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
                        // 缩放时单击仅消费手势，不触发翻页/唤出 UI，避免放大状态下误翻页。
                        if (scale <= 1f) {
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
                    onLongPress = { _ ->
                        // 长按且未缩放时才弹菜单，避免"想放大却弹菜单"。
                        if (scale <= 1f) onLongPress?.invoke()
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
    continuous: Boolean,
    autoScrolling: Boolean,
    onToggleAutoScroll: () -> Unit,
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Text("$current / $total", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        if (continuous) {
            IconButton(onClick = onToggleAutoScroll) {
                Icon(
                    if (autoScrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (autoScrolling) "暂停滚动" else "自动滚动",
                    tint = Color.White,
                )
            }
        }
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
    onOpenGrid: () -> Unit,
    toc: List<TocEntry>,
    onOpenToc: () -> Unit,
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    models: List<Any>,
) {
    var sliderValue by remember { mutableFloatStateOf(current.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var showBrightness by remember { mutableStateOf(false) }
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
            TextButton(onClick = { showBrightness = true }) { Text("亮度", color = Color.White) }
            TextButton(onClick = onOpenToc, enabled = toc.isNotEmpty()) { Text("目录", color = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenGrid) { Text("网格", color = Color.White) }
            TextButton(onClick = onInfo) { Text("信息", color = Color.White) }
            TextButton(onClick = onFavorite) { Text("收藏", color = Color.White) }
        }
    }

    if (showBrightness) {
        BrightnessDialog(
            brightness = brightness,
            onBrightnessChange = onBrightnessChange,
            onDismiss = { showBrightness = false },
        )
    }
}

@Composable
private fun BrightnessDialog(
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读亮度") },
        text = {
            Column {
                Slider(
                    value = if (brightness < 0) 50f else brightness.toFloat(),
                    onValueChange = { onBrightnessChange(it.toInt()) },
                    valueRange = 0f..100f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (brightness < 0) "跟随系统" else "$brightness%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (brightness >= 0) {
                        TextButton(onClick = { onBrightnessChange(-1) }) { Text("跟随系统") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageGridSheet(
    arcid: String,
    total: Int,
    currentPage: Int,
    offline: Boolean,
    models: List<Any>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("页码（共 $total 页）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                items(total) { index ->
                    val isCurrent = index == currentPage
                    Box(
                        Modifier
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E2E2E))
                            .then(
                                if (isCurrent) {
                                    Modifier.border(2.dp, Color(0xFF4C8DFF), RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onJump(index) },
                    ) {
                        AsyncImage(
                            model = if (offline) models[index] else ApiClient.pageThumbnailUrl(arcid, index),
                            contentDescription = "第 ${index + 1} 页",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color(0x99000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageMenuSheet(
    pageNumber: Int,
    offline: Boolean,
    onSave: () -> Unit,
    onCopyLink: () -> Unit,
    onSetCover: () -> Unit,
    onShowInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("第 $pageNumber 页", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存此页到收藏")
            }
            TextButton(
                onClick = onCopyLink,
                enabled = !offline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("复制本页链接")
            }
            if (!offline) {
                TextButton(onClick = onSetCover, modifier = Modifier.fillMaxWidth()) {
                    Text("设为档案封面")
                }
            }
            TextButton(onClick = onShowInfo, modifier = Modifier.fillMaxWidth()) {
                Text("查看信息")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    toc: List<TocEntry>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("目录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (toc.isEmpty()) {
                Text(
                    "暂无目录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                ) {
                    itemsIndexed(toc) { _, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJump(entry.page) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.name.ifBlank { "未命名章节" },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "第 ${entry.page} 页",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
