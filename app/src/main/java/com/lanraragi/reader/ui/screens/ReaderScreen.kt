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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
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
import com.lanraragi.reader.ui.CoverChangeBus
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LibraryRefreshBus
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

private enum class TapRegion { LEFT, CENTER, RIGHT }

private fun fitContentScale(mode: String): ContentScale = when (mode) {
    "fitHeight" -> ContentScale.FillHeight
    "fitScreen" -> ContentScale.Fit
    "original" -> ContentScale.None
    else -> ContentScale.FillWidth
}

@Composable
private fun readerBackgroundColor(mode: String): Color = when (mode) {
    "dark" -> Color(0xFF1A1A1A)
    "gray" -> Color(0xFF2E2E2E)
    "white" -> Color.White
    "auto" -> if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color.Black
    else -> Color.Black
}

private fun fitModeLabel(mode: String): String = when (mode) {
    "fitHeight" -> "适应高度"
    "fitScreen" -> "适应屏幕"
    "original" -> "原始尺寸"
    else -> "适应宽度"
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setWindowBrightness(context: Context, value: Float) {
    context.findActivity()?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = value } }
}

data class ArchivePageModel(val archiveUri: Uri, val pageIndex: Int, val context: Context)

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
        viewModelScope.launch { container.settingsRepository.setLastReadArcId(arcid) }
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

                val savedPage = container.historyRepository.entries.value.firstOrNull { it.arcid == arcid }?.page ?: 0
                val requestedPage = initialPage?.coerceAtLeast(0) ?: savedPage

                if (arcid.startsWith("local_")) {
                    val local = container.localScanManager.localArchives.value.find { it.arcid == arcid }
                        ?: throw Exception("本地文件未找到")
                    val images = ArchiveFileReader.getImages(container.context, Uri.parse(local.summary))
                    _state.update {
                        it.copy(
                            offline = true,
                            pageCount = images.size,
                            localPages = images,
                            title = local.title,
                            currentPage = requestedPage.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
                            loading = false,
                        )
                    }
                } else {
                    val cached = container.offlineCache.cached(arcid)
                    val offlineFile = container.offlineCache.archiveFile(arcid)
                    if (offlineFile.exists()) {
                        val images = ArchiveFileReader.getImages(container.context, Uri.fromFile(offlineFile))
                        val cachedProgress = savedPage.takeIf { it > 0 }
                            ?: cached?.metadata?.progress?.minus(1)?.coerceAtLeast(0) ?: 0
                        _state.update {
                            it.copy(
                                offline = true,
                                pageCount = images.size,
                                localPages = images,
                                title = cached?.title ?: "",
                                tags = cached?.metadata?.tags ?: "",
                                toc = cached?.metadata?.toc ?: emptyList(),
                                currentPage = cachedProgress.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
                                loading = false,
                            )
                        }
                    } else if (cached != null && cached.pageCount > 0) {
                        val cachedProgress = savedPage.takeIf { it > 0 }
                            ?: cached.metadata.progress.minus(1).coerceAtLeast(0)
                        _state.update {
                            it.copy(
                                offline = true,
                                pageCount = cached.pageCount,
                                title = cached.title,
                                tags = cached.metadata.tags,
                                toc = cached.metadata.toc,
                                currentPage = cachedProgress.coerceIn(0, cached.pageCount - 1),
                                loading = false,
                            )
                        }
                    } else {
                        val meta = container.repository.getMetadata(arcid)
                        val pageUrls = container.repository.getPageUrls(arcid)
                        val serverProgress = meta.progress.minus(1).coerceIn(0, (pageUrls.size - 1).coerceAtLeast(0))
                        val start = initialPage?.coerceIn(0, (pageUrls.size - 1).coerceAtLeast(0))
                            ?: savedPage.coerceIn(0, (pageUrls.size - 1).coerceAtLeast(0))
                                .takeIf { savedPage > 0 }
                            ?: serverProgress
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
                val loaded = _state.value
                container.historyRepository.record(arcid, loaded.title, loaded.currentPage, loaded.pageCount)
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
                    Uri.parse(container.localScanManager.localArchives.value.find { it.arcid == arcid }?.summary ?: "")
                } else {
                    Uri.fromFile(container.offlineCache.archiveFile(arcid))
                }
                ArchivePageModel(uri, index, container.context)
            }
            s.offline -> container.offlineCache.pageFile(arcid, index)
            else -> s.onlinePages[index]
        }
    }

    fun reportPage(page: Int) {
        val s = _state.value
        if (page !in 0 until s.pageCount) return
        _state.update { it.copy(currentPage = page) }
        viewModelScope.launch {
            container.historyRepository.recordProgress(arcid, page, s.pageCount, s.title)
        }
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

    fun addToc(title: String) {
        val s = _state.value
        if (s.offline || s.currentPage !in 0 until s.pageCount || title.isBlank()) return
        viewModelScope.launch {
            try {
                container.repository.addTocEntry(arcid, s.currentPage + 1, title.trim())
                refreshToc()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(container.context, e.message ?: "添加目录失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteToc(page: Int) {
        if (arcid.startsWith("local_")) return
        viewModelScope.launch {
            try {
                container.repository.deleteTocEntry(arcid, page)
                refreshToc()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(container.context, e.message ?: "删除目录失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun refreshToc() {
        val meta = runCatching { container.repository.getMetadata(arcid) }.getOrNull() ?: return
        _state.update { it.copy(toc = meta.toc) }
    }

    fun downloadCurrentPage(context: Context, page: Int? = null) {
        val s = _state.value
        if (s.pageCount <= 0) return
        val idx = (page ?: s.currentPage).coerceIn(0, s.pageCount - 1)
        val appContext = context.applicationContext
        container.downloadManager.enqueue(DownloadTaskType.PAGE, arcid, "第 ${idx + 1} 页") {
            val dir = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
                "收藏",
            )
            dir.mkdirs()
            val dest = File(dir, "${arcid}_${idx + 1}.jpg")
            if (s.offline) {
                container.offlineCache.pageFile(arcid, idx).copyTo(dest, overwrite = true)
            } else {
                ApiClient.okHttpClient.newCall(Request.Builder().url(s.onlinePages[idx]).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("下载失败 HTTP ${resp.code}")
                    resp.body?.byteStream()?.use { input -> FileOutputStream(dest).use { out -> input.copyTo(out) } }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "已收藏到收藏文件夹", Toast.LENGTH_SHORT).show()
            }
        }
        Toast.makeText(context, "已加入下载队列", Toast.LENGTH_SHORT).show()
    }

    fun setCoverFromPage(page: Int) {
        if (arcid.startsWith("local_")) return
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

    fun toggleAutoScroll() {
        if (_state.value.readerMode == "continuous") {
            _state.update { it.copy(autoScrolling = !it.autoScrolling) }
        }
    }

    fun setAutoScrollSpeed(s: String) {
        _state.update { it.copy(autoScrollSpeed = s) }
        viewModelScope.launch { container.settingsRepository.setAutoScrollSpeed(s) }
    }

    override fun onCleared() {
        syncJob?.cancel()
        val s = _state.value
        val page = if (s.currentPage in 0 until s.pageCount) s.currentPage + 1 else null
        if (page == null || arcid.startsWith("local_")) return
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .edgeSwipeBack { navController.popBackStack() },
    ) {
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
    var showUi by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var pageMenuIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }

    fun touch() {
        showUi = true
        lastInteraction = System.currentTimeMillis()
    }

    LaunchedEffect(showUi, lastInteraction, showToc, showSettings, showHistory, pageMenuIndex) {
        if (showUi && !showToc && !showSettings && !showHistory && pageMenuIndex == null) {
            delay(5000)
            if (System.currentTimeMillis() - lastInteraction >= 4800) showUi = false
        }
    }

    val bgColor = readerBackgroundColor(state.readerBackground)
    val fitScale = fitContentScale(state.readerFitMode)
    val models = remember(state.pageCount, state.offline, state.onlinePages) {
        (0 until state.pageCount).map(vm::pageModel)
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

    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(state.readerBrightness) {
        setWindowBrightness(
            context,
            if (state.readerBrightness >= 0) state.readerBrightness / 100f
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
        )
    }
    DisposableEffect(Unit) {
        onDispose { setWindowBrightness(context, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
    LaunchedEffect(state.autoScrolling, state.autoScrollSpeed, state.readerMode) {
        if (state.readerMode != "continuous" || !state.autoScrolling || state.autoScrollSpeed == "off") return@LaunchedEffect
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
        touch()
    }

    fun stepScreen(delta: Int) {
        val step = if (state.readerMode == "continuous") 1 else pagesPerScreen
        jumpTo(state.currentPage + delta * step)
    }

    fun openPageMenu(idx: Int) {
        touch()
        pageMenuIndex = idx
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!state.volumeKeysEnabled || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.VolumeUp -> { stepScreen(-1); true }
                        Key.VolumeDown -> { stepScreen(1); true }
                        else -> false
                    }
                }
            },
    ) {
        if (state.readerMode == "continuous") {
            VerticalReader(
                listState = listState,
                models = models,
                fitScale = fitScale,
                onPage = { vm.reportPage(it); touch() },
                onToggleUi = { if (showUi) showUi = false else touch() },
                onPageLongPress = ::openPageMenu,
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
                onPage = { vm.reportPage(it); touch() },
                onToggleUi = { if (showUi) showUi = false else touch() },
                onPageLongPress = ::openPageMenu,
            )
        }

        AnimatedVisibility(visible = showUi, modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                title = state.title,
                current = state.currentPage + 1,
                total = state.pageCount,
                continuous = state.readerMode == "continuous",
                autoScrolling = state.autoScrolling,
                onBack = { navController.popBackStack() },
                onHistory = { touch(); showHistory = true },
                onSettings = { touch(); showSettings = true },
                onToggleAutoScroll = { vm.toggleAutoScroll(); touch() },
            )
        }

        AnimatedVisibility(
            visible = showUi,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            ReaderTimeline(
                currentPage = state.currentPage,
                total = state.pageCount,
                models = models,
                onJump = ::jumpTo,
                onPrev = { stepScreen(-1) },
                onNext = { stepScreen(1) },
                onTouch = ::touch,
                onLongPress = ::openPageMenu,
            )
        }
    }

    if (showToc) {
        TocSheet(
            toc = state.toc,
            currentPage = state.currentPage,
            offline = state.offline,
            onJump = { jumpTo(it - 1); showToc = false },
            onAdd = { vm.addToc(it); showToc = false },
            onDelete = vm::deleteToc,
            onDismiss = { showToc = false },
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            state = state,
            vm = vm,
            onOpenToc = { showSettings = false; showToc = true; touch() },
            onDismiss = { showSettings = false; touch() },
            onTouch = ::touch,
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false; touch() },
            title = { Text("阅读进度") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.title.ifBlank { arcid }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("当前第 ${state.currentPage + 1} / ${state.pageCount} 页")
                    Text(
                        if (state.offline) "当前使用本地/离线资源" else "当前使用服务器资源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false; touch() }) { Text("关闭") }
            },
        )
    }

    pageMenuIndex?.let { menuIdx ->
        PageMenuSheet(
            pageNumber = menuIdx + 1,
            offline = state.offline,
            onSave = { vm.downloadCurrentPage(context, menuIdx); pageMenuIndex = null; touch() },
            onCopyLink = {
                if (!state.offline && menuIdx in state.onlinePages.indices) {
                    val url = ApiClient.displayBaseUrl().trimEnd('/') + "/" +
                        state.onlinePages[menuIdx].removePrefix(ApiClient.SENTINEL_BASE)
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("页面链接", url))
                    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                }
                pageMenuIndex = null
                touch()
            },
            onSetCover = { vm.setCoverFromPage(menuIdx + 1); pageMenuIndex = null; touch() },
            onShowInfo = {
                pageMenuIndex = null
                showHistory = true
                touch()
            },
            onDismiss = { pageMenuIndex = null; touch() },
        )
    }

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
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                                    TapRegion.LEFT -> scope.launch {
                                        pagerState.animateScrollToPage(
                                            (pagerState.currentPage - 1).coerceIn(0, pagerState.pageCount - 1),
                                        )
                                    }
                                    TapRegion.RIGHT -> scope.launch {
                                        pagerState.animateScrollToPage(
                                            (pagerState.currentPage + 1).coerceIn(0, pagerState.pageCount - 1),
                                        )
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
                        offset += pan
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
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
                                    },
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
                    onLongPress = {
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
    title: String,
    current: Int,
    total: Int,
    continuous: Boolean,
    autoScrolling: Boolean,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onToggleAutoScroll: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title.ifBlank { "阅读" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$current / $total",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (continuous) {
            IconButton(onClick = onToggleAutoScroll) {
                Icon(
                    if (autoScrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (autoScrolling) "暂停滚动" else "自动滚动",
                    tint = Color.White,
                )
            }
        }
        IconButton(onClick = onHistory) {
            Icon(Icons.Filled.History, contentDescription = "阅读进度", tint = Color.White)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "阅读设置", tint = Color.White)
        }
    }
}

@Composable
private fun ReaderTimeline(
    currentPage: Int,
    total: Int,
    models: List<Any>,
    onJump: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTouch: () -> Unit,
    onLongPress: (Int) -> Unit,
) {
    var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage, dragging) {
        if (!dragging) sliderValue = currentPage.toFloat()
    }

    val start = when {
        total <= 5 -> 0
        currentPage <= 2 -> 0
        currentPage >= total - 3 -> total - 5
        else -> currentPage - 2
    }
    val visiblePages = (start until (start + 5).coerceAtMost(total)).toList()

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xE9000000))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        LazyRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
            userScrollEnabled = false,
        ) {
            itemsIndexed(visiblePages) { _, index ->
                val selected = index == currentPage
                Column(
                    Modifier
                        .weight(1f)
                        .clickable {
                            onJump(index)
                            onTouch()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.69f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF202020))
                            .then(
                                if (selected) Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(8.dp),
                                ) else Modifier,
                            )
                            .pointerInput(index) {
                                detectTapGestures(onLongPress = { onLongPress(index) })
                            },
                    ) {
                        AsyncImage(
                            model = models[index],
                            contentDescription = "第 ${index + 1} 页",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (selected) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(5.dp),
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${index + 1}",
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPrev(); onTouch() }, enabled = currentPage > 0) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一页", tint = Color.White)
            }
            Text(
                "${currentPage + 1}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    sliderValue = it
                    onJump(it.toInt().coerceIn(0, total - 1))
                    onTouch()
                },
                onValueChangeFinished = {
                    dragging = false
                    onTouch()
                },
                valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                enabled = total > 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$total",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { onNext(); onTouch() }, enabled = currentPage < total - 1) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一页", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    toc: List<TocEntry>,
    currentPage: Int,
    offline: Boolean,
    onJump: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var addTitle by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!offline) {
                    TextButton(onClick = { addTitle = ""; showAdd = true }) {
                        Text("添加 · 当前第 ${currentPage + 1} 页")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (toc.isEmpty()) {
                Text(
                    "暂无目录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    itemsIndexed(toc) { _, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJump(entry.page) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.name.ifBlank { "未命名章节" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "第 ${entry.page} 页",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!offline) {
                                TextButton(onClick = { onDelete(entry.page) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加目录") },
            text = {
                Column {
                    Text("页码：${currentPage + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = addTitle,
                        onValueChange = { addTitle = it },
                        singleLine = true,
                        placeholder = { Text("章节名称") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = addTitle.isNotBlank(),
                    onClick = { onAdd(addTitle); showAdd = false },
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    state: ReaderViewModel.UiState,
    vm: ReaderViewModel,
    onOpenToc: () -> Unit,
    onDismiss: () -> Unit,
    onTouch: () -> Unit,
) {
    var showBrightness by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenToc, Modifier.fillMaxWidth()) { Text("目录") }
            TextButton(onClick = { vm.cycleLayout(); onTouch() }, Modifier.fillMaxWidth()) {
                Text("布局：${when (state.readerMode) { "multi" -> "多页"; "continuous" -> "连续"; else -> "单页" }}")
            }
            TextButton(onClick = { vm.toggleDirection(); onTouch() }, Modifier.fillMaxWidth()) {
                Text("方向：${if (state.readingDirection == "rtl") "右→左" else "左→右"}")
            }
            TextButton(onClick = { vm.cycleFitMode(); onTouch() }, Modifier.fillMaxWidth()) {
                Text("图片适应：${fitModeLabel(state.readerFitMode)}")
            }
            TextButton(onClick = { showBrightness = true }, Modifier.fillMaxWidth()) {
                Text("亮度：${if (state.readerBrightness < 0) "跟随系统" else "${state.readerBrightness}%"}")
            }
            TextButton(
                onClick = {
                    vm.setAutoScrollSpeed(
                        when (state.autoScrollSpeed) {
                            "off" -> "slow"
                            "slow" -> "medium"
                            "medium" -> "fast"
                            else -> "off"
                        },
                    )
                    onTouch()
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("自动滚动速度：${when (state.autoScrollSpeed) { "slow" -> "慢"; "medium" -> "中"; "fast" -> "快"; else -> "关闭" }}")
            }
        }
    }

    if (showBrightness) {
        AlertDialog(
            onDismissRequest = { showBrightness = false },
            title = { Text("阅读亮度") },
            text = {
                Column {
                    Slider(
                        value = if (state.readerBrightness < 0) 50f else state.readerBrightness.toFloat(),
                        onValueChange = {
                            vm.setReaderBrightness(it.toInt())
                            onTouch()
                        },
                        valueRange = 0f..100f,
                    )
                    TextButton(onClick = { vm.setReaderBrightness(-1); onTouch() }) {
                        Text("跟随系统")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBrightness = false }) { Text("关闭") } },
        )
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("第 $pageNumber 页", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSave, Modifier.fillMaxWidth()) { Text("保存此页到收藏") }
            TextButton(onClick = onCopyLink, enabled = !offline, Modifier.fillMaxWidth()) { Text("复制本页链接") }
            if (!offline) TextButton(onClick = onSetCover, Modifier.fillMaxWidth()) { Text("设为档案封面") }
            TextButton(onClick = onShowInfo, Modifier.fillMaxWidth()) { Text("查看信息") }
        }
    }
}
