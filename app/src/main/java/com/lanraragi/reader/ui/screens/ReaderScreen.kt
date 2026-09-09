package com.lanraragi.reader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanraragi.reader.data.AUTO_SCROLL_MAX_SECONDS
import com.lanraragi.reader.data.AUTO_SCROLL_MIN_SECONDS
import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.data.autoScrollSeconds
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.download.DownloadDestination
import com.lanraragi.reader.data.download.DownloadSourceIdentity
import com.lanraragi.reader.data.download.DownloadTaskSpec
import com.lanraragi.reader.data.model.TocEntry
import com.lanraragi.reader.data.normalizeAutoScrollSpeed
import com.lanraragi.reader.data.reader.DefaultReaderSourceResolver
import com.lanraragi.reader.data.reader.PageModel
import com.lanraragi.reader.data.reader.PageSourceResolver
import com.lanraragi.reader.data.reader.PrefetchCoordinator
import com.lanraragi.reader.data.reader.ReaderGestureArbiter
import com.lanraragi.reader.data.ReaderOrientationProfile
import com.lanraragi.reader.ui.reader.ReaderAction
import com.lanraragi.reader.ui.reader.CoilPagePrefetcher
import com.lanraragi.reader.ui.reader.ReaderControls
import com.lanraragi.reader.ui.reader.ReaderRouteEffects
import com.lanraragi.reader.ui.reader.ReaderSurface
import com.lanraragi.reader.ui.reader.ReaderSurfaceMode
import com.lanraragi.reader.ui.reader.ReaderSheets
import com.lanraragi.reader.ui.reader.rememberReaderFrameSampler
import com.lanraragi.reader.ui.reader.readerActionForKey
import com.lanraragi.reader.data.reader.ReaderSession
import com.lanraragi.reader.data.reader.SavedArchivePageSource
import com.lanraragi.reader.domain.reader.ReaderPageMapping
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.CoverChangeBus
import com.lanraragi.reader.ui.LibraryRefreshBus
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.asImageRequest
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
import java.io.File
import java.io.FileOutputStream

private enum class TapRegion { LEFT, CENTER, RIGHT }

private fun fitContentScale(mode: String): ContentScale = when (mode) {
    "fitHeight" -> ContentScale.FillHeight
    "fitScreen" -> ContentScale.Fit
    "original" -> ContentScale.None
    else -> ContentScale.FillWidth
}

/** Stable identity for Compose lazy containers; never derives identity from the list position alone. */
private fun stablePageKey(model: Any, index: Int): String = when (model) {
    is ArchivePageModel -> "archive:${model.archiveUri}:$index"
    is PageModel -> "${model.revision}:$index"
    is java.io.File -> "file:${model.absolutePath}:$index"
    else -> "page:$index:${model.toString()}"
}

@Composable
private fun coilPageModel(model: Any): Any {
    val context = LocalContext.current
    return when (model) {
        is PageModel.RemotePage -> ImageRequest.Builder(context)
            .data(model.url)
            .memoryCacheKey(model.cacheKey)
            .diskCacheKey(model.cacheKey)
            .build()
        is PageModel.LocalEntry -> ArchivePageModel(
            model.uri,
            model.index,
            context,
            model.thumbnail,
            model.cacheKey,
        ).asImageRequest()
        else -> model
    }
}

@Composable
private fun readerBackgroundColor(mode: String): Color = when (mode) {
    "dark" -> Color(0xFF1A1A1A)
    "gray" -> Color(0xFF2E2E2E)
    "white" -> Color.White
    "auto" -> if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color.Black
    else -> Color.Black
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setWindowBrightness(context: Context, value: Float) {
    context.findActivity()?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = value } }
}

data class ArchivePageModel(
    val archiveUri: Uri,
    val pageIndex: Int,
    val context: Context,
    val thumbnail: Boolean = false,
    val revision: String? = null,
)

class ReaderViewModel(
    private val container: AppContainer,
    private val arcid: String,
    private val initialPage: Int? = null,
) : ViewModel() {
    val diagnostics = container.diagnostics
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val tags: String = "",
        val pageCount: Int = 0,
        val offline: Boolean = false,
        val progress: Int = 0,
        val currentPage: Int = 0,
        val readerMode: String = "single",
        val multiPageCount: Int = 2,
        val autoDoublePageLandscape: Boolean = true,
        val preloadOnlineCount: Int = 3,
        val readingDirection: String = "ltr",
        val autoScrollSpeed: String = "3",
        val autoScrolling: Boolean = false,
        val readerFitMode: String = "fitWidth",
        val readerBackground: String = "black",
        val readerBrightness: Int = -1,
        val tapZonesEnabled: Boolean = true,
        val keepScreenOn: Boolean = true,
        val volumeKeysEnabled: Boolean = true,
        val preloadLocalCount: Int = 5,
        val toc: List<TocEntry> = emptyList(),
        val pageSourceRevision: String? = null,
        val firstPageAlone: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var syncJob: Job? = null
    private var lastSynced = -1
    private var metadataReady = false
    private var pendingInitialPage = initialPage?.coerceAtLeast(0) ?: 0
    private var orientationProfile = ReaderOrientationProfile.GLOBAL
    /** Shared page-source/session owner; legacy metadata state remains in this VM for compatibility. */
    private val readerSession = ReaderSession(
        resolver = DefaultReaderSourceResolver(
            PageSourceResolver(
                container.context,
                container.repository,
                container.offlineCache,
                container.savedArtifactRepository,
            ),
        ),
        scope = viewModelScope,
        diagnostics = container.diagnostics,
    )
    private val prefetchCoordinator = PrefetchCoordinator(viewModelScope)
    private val pagePrefetcher = CoilPagePrefetcher(container.context)

    init {
        val localUri = if (arcid.startsWith("local_")) {
            container.localScanManager.localArchives.value
                .firstOrNull { it.arcid == arcid }
                ?.summary
                ?.let(Uri::parse)
        } else null
        val identity = if (localUri != null) {
            ArchiveIdentity.LocalSaf(localUri.toString())
        } else {
            ArchiveIdentity.Remote(arcid, ApiClient.config.baseUrl)
        }
        readerSession.open(identity, localUri, initialPage ?: 0)
        viewModelScope.launch {
            readerSession.state.collect { session ->
                val source = session.source
                val resolvedPage = source?.let {
                    pendingInitialPage.coerceIn(0, (it.pageCount - 1).coerceAtLeast(0))
                } ?: session.currentPage
                if (source != null && session.currentPage != resolvedPage) {
                    readerSession.setPage(resolvedPage)
                }
                _state.update { state ->
                    state.copy(
                        pageCount = source?.pageCount ?: state.pageCount,
                        pageSourceRevision = source?.revision ?: state.pageSourceRevision,
                        offline = source is SavedArchivePageSource ||
                            (source?.identity !is ArchiveIdentity.Remote && source != null),
                        currentPage = resolvedPage,
                        loading = !metadataReady || session.loading,
                        error = session.error ?: state.error,
                    )
                }
            }
        }
        load()
        viewModelScope.launch { container.settingsRepository.setLastReadArcId(arcid) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val settings = container.settingsRepository.settings.first()
                val orientationProfile = if (container.context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    ReaderOrientationProfile.LANDSCAPE
                } else ReaderOrientationProfile.PORTRAIT
                this@ReaderViewModel.orientationProfile = orientationProfile
                _state.update {
                    it.copy(
                        readerMode = when (orientationProfile) {
                            ReaderOrientationProfile.LANDSCAPE -> settings.readerModeLandscape ?: settings.readerMode
                            ReaderOrientationProfile.PORTRAIT -> settings.readerModePortrait ?: settings.readerMode
                            ReaderOrientationProfile.GLOBAL -> settings.readerMode
                        },
                        multiPageCount = settings.multiPageCount,
                        autoDoublePageLandscape = settings.autoDoublePageLandscape,
                        preloadOnlineCount = settings.preloadOnlineCount,
                        readingDirection = when (orientationProfile) {
                            ReaderOrientationProfile.LANDSCAPE -> settings.readingDirectionLandscape ?: settings.readingDirection
                            ReaderOrientationProfile.PORTRAIT -> settings.readingDirectionPortrait ?: settings.readingDirection
                            ReaderOrientationProfile.GLOBAL -> settings.readingDirection
                        },
                        autoScrollSpeed = settings.autoScrollSpeed,
                        readerFitMode = when (orientationProfile) {
                            ReaderOrientationProfile.LANDSCAPE -> settings.readerFitModeLandscape ?: settings.readerFitMode
                            ReaderOrientationProfile.PORTRAIT -> settings.readerFitModePortrait ?: settings.readerFitMode
                            ReaderOrientationProfile.GLOBAL -> settings.readerFitMode
                        },
                        firstPageAlone = when (orientationProfile) {
                            ReaderOrientationProfile.LANDSCAPE -> settings.doublePageFirstPageAloneLandscape
                                ?: settings.doublePageFirstPageAlone
                            ReaderOrientationProfile.PORTRAIT -> settings.doublePageFirstPageAlonePortrait
                                ?: settings.doublePageFirstPageAlone
                            ReaderOrientationProfile.GLOBAL -> settings.doublePageFirstPageAlone
                        },
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
                    _state.update {
                        it.copy(
                            offline = true,
                            title = local.title,
                        )
                    }
                    pendingInitialPage = requestedPage
                } else {
                    val cached = container.offlineCache.cached(arcid)
                    if (cached != null) {
                        val cachedProgress = savedPage.takeIf { it > 0 }
                            ?: cached?.metadata?.progress?.minus(1)?.coerceAtLeast(0) ?: 0
                        _state.update {
                            it.copy(
                                offline = true,
                                title = cached?.title ?: "",
                                tags = cached?.metadata?.tags ?: "",
                                toc = cached?.metadata?.toc ?: emptyList(),
                            )
                        }
                        pendingInitialPage = cachedProgress
                    } else {
                        val meta = container.repository.getMetadata(arcid)
                        val serverProgress = meta.progress.minus(1).coerceAtLeast(0)
                        val start = initialPage?.coerceAtLeast(0)
                            ?: savedPage.coerceAtLeast(0)
                                .takeIf { savedPage > 0 }
                            ?: serverProgress
                        _state.update {
                            it.copy(
                                offline = false,
                                title = meta.title,
                                tags = meta.tags,
                                progress = meta.progress,
                                toc = meta.toc,
                            )
                        }
                        pendingInitialPage = start
                    }
                }
                metadataReady = true
                val session = readerSession.state.value
                _state.update {
                    it.copy(
                        pageCount = session.pageCount,
                        currentPage = pendingInitialPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
                        loading = session.loading || session.source == null,
                        error = session.error ?: it.error,
                    )
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

    fun applyOrientation(orientation: Int) {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val landscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            orientationProfile = if (landscape) ReaderOrientationProfile.LANDSCAPE else ReaderOrientationProfile.PORTRAIT
            _state.update { state ->
                state.copy(
                    readerMode = if (landscape) settings.readerModeLandscape ?: settings.readerMode else settings.readerModePortrait ?: settings.readerMode,
                    readingDirection = if (landscape) settings.readingDirectionLandscape ?: settings.readingDirection else settings.readingDirectionPortrait ?: settings.readingDirection,
                    readerFitMode = if (landscape) settings.readerFitModeLandscape ?: settings.readerFitMode else settings.readerFitModePortrait ?: settings.readerFitMode,
                    firstPageAlone = if (landscape) settings.doublePageFirstPageAloneLandscape
                        ?: settings.doublePageFirstPageAlone
                    else settings.doublePageFirstPageAlonePortrait ?: settings.doublePageFirstPageAlone,
                )
            }
        }
    }

    fun pageModel(index: Int): PageModel = readerSession.state.value.source?.pageModel(index)
        ?: throw IllegalStateException("页面源尚未就绪")

    fun reportPage(page: Int) {
        val s = _state.value
        if (page !in 0 until s.pageCount) return
        _state.update { it.copy(currentPage = page) }
        readerSession.setPage(page)
        val source = readerSession.state.value.source
        if (source != null) {
            prefetchCoordinator.updateModels(
                center = page,
                pageCount = s.pageCount,
                radius = if (s.offline) s.preloadLocalCount else s.preloadOnlineCount,
                model = source::pageModel,
                prefetcher = pagePrefetcher,
            )
        }
        viewModelScope.launch { container.historyRepository.recordProgress(arcid, page, s.pageCount, s.title) }
        if (s.offline) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(700)
            val p = _state.value.currentPage
            if (p >= 0 && p != lastSynced) {
                lastSynced = p
                container.progressWriter.record(ArchiveIdentity.Remote(arcid), p, _state.value.pageCount)
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
        val dir = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "收藏",
        )
        dir.mkdirs()
        val destination = File(dir, "${arcid}_${idx + 1}.jpg")

        if (s.offline) {
            // Local/previously saved archives are exported locally and never enter a server task.
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        when (val model = pageModel(idx)) {
                            is PageModel.LocalEntry -> {
                                val input = com.lanraragi.reader.data.ArchiveFileReader
                                    .openImageStream(appContext, model.uri, model.entryName)
                                    ?: throw IllegalStateException("无法读取本地页面")
                                input.use { source ->
                                    FileOutputStream(destination).use { output -> source.copyTo(output) }
                                }
                            }
                            else -> throw IllegalStateException("离线页面不可用")
                        }
                    }
                    Toast.makeText(appContext, "已保存到收藏文件夹", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(appContext, e.message ?: "保存页面失败", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        container.downloadManager.enqueue(
            DownloadTaskSpec.Page(
                source = DownloadSourceIdentity(arcid, ApiClient.config.baseUrl),
                destination = DownloadDestination(destination.absolutePath),
                page = idx,
                label = "第 ${idx + 1} 页",
            ),
        )
        Toast.makeText(context, "已加入下载队列", Toast.LENGTH_SHORT).show()
    }

    fun setCoverFromPage(page: Int) {
        if (arcid.startsWith("local_")) return
        viewModelScope.launch {
            try {
                container.repository.setThumbnailFromPage(arcid, page.coerceAtLeast(1))
                container.thumbnailRepository.refreshCover(arcid)
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

    fun setReaderMode(mode: String) {
        _state.update { it.copy(readerMode = mode) }
        viewModelScope.launch { container.settingsRepository.setReaderModeForOrientation(orientationProfile, mode) }
    }

    fun setReaderFitMode(mode: String) {
        _state.update { it.copy(readerFitMode = mode) }
        viewModelScope.launch { container.settingsRepository.setReaderFitModeForOrientation(orientationProfile, mode) }
    }

    fun setReadingDirection(direction: String) {
        _state.update { it.copy(readingDirection = direction) }
        viewModelScope.launch { container.settingsRepository.setReadingDirectionForOrientation(orientationProfile, direction) }
    }

    fun setFirstPageAlone(enabled: Boolean) {
        _state.update { it.copy(firstPageAlone = enabled) }
        viewModelScope.launch { container.settingsRepository.setFirstPageAloneForOrientation(orientationProfile, enabled) }
    }

    fun setReaderBrightness(n: Int) {
        val v = n.coerceIn(-1, 100)
        _state.update { it.copy(readerBrightness = v) }
        viewModelScope.launch { container.settingsRepository.setReaderBrightness(v) }
    }

    fun toggleAutoScroll() {
        _state.update { it.copy(autoScrolling = !it.autoScrolling) }
    }

    fun stopAutoScroll() {
        _state.update { it.copy(autoScrolling = false) }
    }

    fun setAutoScrollSpeed(s: String) {
        val normalized = normalizeAutoScrollSpeed(s)
        _state.update { it.copy(autoScrollSpeed = normalized) }
        viewModelScope.launch { container.settingsRepository.setAutoScrollSpeed(normalized) }
    }

    override fun onCleared() {
        syncJob?.cancel()
        prefetchCoordinator.cancel()
        readerSession.close()
        val s = _state.value
        val page = if (s.currentPage in 0 until s.pageCount) s.currentPage + 1 else null
        if (page == null || arcid.startsWith("local_")) return
        container.applicationScope.launch {
            container.progressWriter.record(ArchiveIdentity.Remote(arcid), page - 1, s.pageCount)
        }
    }
}

@Composable
fun ReaderScreen(container: AppContainer, arcid: String, navController: NavController, initialPage: Int? = null) {
    val vm: ReaderViewModel = viewModel { ReaderViewModel(container, arcid, initialPage) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    ReaderRouteEffects(context, view)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .edgeSwipeBack { navController.popBackStack() },
    ) {
        when {
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load)
            state.pageCount <= 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有可显示的页面", color = Color.White) }
            else -> ReaderContent(vm, state, arcid)
        }
    }
}

@Composable
private fun ReaderContent(vm: ReaderViewModel, state: ReaderViewModel.UiState, arcid: String) {
    var showUi by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAutoPage by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var pageMenuIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }
    rememberReaderFrameSampler(vm.diagnostics)

    fun touch() { showUi = true; lastInteraction = System.currentTimeMillis() }

    LaunchedEffect(showUi, lastInteraction, showToc, showSettings, showAutoPage, showHistory, showInfo, pageMenuIndex) {
        if (showUi && !showToc && !showSettings && !showAutoPage && !showHistory && !showInfo && pageMenuIndex == null) {
            delay(10000)
            if (System.currentTimeMillis() - lastInteraction >= 9800) showUi = false
        }
    }

    val bgColor = readerBackgroundColor(state.readerBackground)
    val fitScale = fitContentScale(state.readerFitMode)
    val models = remember(state.pageCount, state.offline, state.pageSourceRevision) {
        (0 until state.pageCount).map(vm::pageModel)
    }
    val reverse = state.readingDirection == "rtl"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(configuration.orientation) { vm.applyOrientation(configuration.orientation) }
    val pagesPerScreen = when {
        state.readerMode == "multi" -> state.multiPageCount
        state.readerMode == "single" && state.autoDoublePageLandscape && isLandscape -> 2
        else -> 1
    }
    val preloadCount = if (state.offline) state.preloadLocalCount else state.preloadOnlineCount
    val firstPageAlone = state.firstPageAlone && pagesPerScreen > 1
    val screens = ReaderPageMapping.screenCount(models.size, pagesPerScreen, firstPageAlone).coerceAtLeast(1)
    val startScreen = ReaderPageMapping.pageToScreen(
        state.currentPage,
        models.size,
        pagesPerScreen,
        firstPageAlone,
    ).coerceIn(0, screens - 1)
    val listState = key(state.readerMode) {
        rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
    }
    val pagerState = key(state.readerMode, pagesPerScreen) {
        rememberPagerState(initialPage = startScreen) { screens }
    }

    DisposableEffect(state.keepScreenOn) { view.keepScreenOn = state.keepScreenOn; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(state.readerBrightness) { setWindowBrightness(context, if (state.readerBrightness >= 0) state.readerBrightness / 100f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    DisposableEffect(Unit) { onDispose { setWindowBrightness(context, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) } }
    LaunchedEffect(
        state.autoScrolling,
        state.autoScrollSpeed,
        state.readerMode,
        listState,
        pagerState,
        pagesPerScreen,
    ) {
        if (!state.autoScrolling) return@LaunchedEffect
        val secondsPerViewport = autoScrollSeconds(state.autoScrollSpeed)
        if (state.readerMode == "continuous") {
            while (true) {
                if (!listState.canScrollForward) {
                    vm.stopAutoScroll()
                    break
                }
                val viewportHeight = listState.layoutInfo.run {
                    (viewportEndOffset - viewportStartOffset).coerceAtLeast(1)
                }
                val pxPerSec = viewportHeight / secondsPerViewport
                listState.scrollBy(pxPerSec * 0.016f)
                delay(16)
            }
        } else {
            while (true) {
                delay((secondsPerViewport * 1000).toLong())
                val currentScreen = pagerState.settledPage
                if (currentScreen >= pagerState.pageCount - 1) {
                    vm.stopAutoScroll()
                    break
                }
                pagerState.animateScrollToPage(currentScreen + 1)
            }
        }
    }

    fun jumpTo(idx: Int, revealUi: Boolean = true) {
        val page = idx.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (state.readerMode == "continuous") listState.animateScrollToItem(page)
            else pagerState.animateScrollToPage(
                ReaderPageMapping.pageToScreen(page, state.pageCount, pagesPerScreen, firstPageAlone),
            )
        }
        vm.reportPage(page)
        if (revealUi) touch()
    }
    fun stepScreen(delta: Int, revealUi: Boolean = true) {
        if (state.readerMode == "continuous") {
            jumpTo(state.currentPage + delta, revealUi)
        } else {
            val screen = ReaderPageMapping.pageToScreen(
                state.currentPage, state.pageCount, pagesPerScreen, firstPageAlone,
            )
            val target = (screen + delta).coerceIn(0, screens - 1)
            jumpTo(
                ReaderPageMapping.screenToFirstPage(target, state.pageCount, pagesPerScreen, firstPageAlone),
                revealUi,
            )
        }
    }
    fun openPageMenu(idx: Int) { touch(); pageMenuIndex = idx }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (readerActionForKey(event.key, state.volumeKeysEnabled)) {
                        ReaderAction.Previous -> {
                            stepScreen(-1)
                            true
                        }
                        ReaderAction.Next -> {
                            stepScreen(1)
                            true
                        }
                        null -> false
                    }
                }
            },
    ) {
        ReaderSurface(
            mode = when {
                state.readerMode == "continuous" -> ReaderSurfaceMode.CONTINUOUS
                state.readingDirection == "ttb" -> ReaderSurfaceMode.VERTICAL_PAGED
                else -> ReaderSurfaceMode.HORIZONTAL_PAGED
            },
            continuous = {
            ContinuousReader(
                listState,
                models,
                fitScale,
                vm::reportPage,
                { if (showUi) showUi = false else touch() },
                ::openPageMenu,
            ) },
            verticalPaged = {
            VerticalPagedReader(
                pagerState,
                models,
                pagesPerScreen,
                firstPageAlone,
                preloadCount,
                fitScale,
                state.tapZonesEnabled,
                vm::reportPage,
                { if (showUi) showUi = false else touch() },
                ::openPageMenu,
            ) },
            horizontalPaged = {
            HorizontalReader(
                pagerState,
                models,
                reverse,
                pagesPerScreen,
                firstPageAlone,
                preloadCount,
                fitScale,
                state.tapZonesEnabled,
                vm::reportPage,
                { if (showUi) showUi = false else touch() },
                ::openPageMenu,
            ) },
        )

        ReaderControls(
            visible = showUi,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
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
                onOpenToc = { touch(); showToc = true },
                onOpenHistory = { touch(); showHistory = true },
                onOpenFunctions = { touch(); showSettings = true },
                onOpenAutoPage = { touch(); showAutoPage = true },
            )
        }
    }

    ReaderSheets(
        tocVisible = showToc,
        settingsVisible = showSettings,
        autoPageVisible = showAutoPage,
        historyVisible = showHistory,
        infoVisible = showInfo,
        pageMenuVisible = pageMenuIndex != null,
        toc = { TocSheet(
            state.toc,
            state.currentPage,
            state.offline,
            { jumpTo(it - 1); showToc = false },
            { vm.addToc(it); showToc = false; touch() },
            { vm.deleteToc(it); touch() },
        ) { showToc = false; touch() } },
        settings = { ReaderSettingsSheet(state, vm, { showSettings = false; touch() }, ::touch) },
        autoPage = { AutoPageSheet(state, vm, { showAutoPage = false; touch() }, ::touch) },
        history = {
        AlertDialog(onDismissRequest = { showHistory = false; touch() }, title = { Text("阅读进度") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.title.ifBlank { arcid }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("当前第 ${state.currentPage + 1} / ${state.pageCount} 页")
                Text(if (state.offline) "当前使用本地/离线资源" else "当前使用服务器资源", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton(onClick = { showHistory = false; touch() }) { Text("关闭") } }) },
        info = {
        AlertDialog(
            onDismissRequest = { showInfo = false; touch() },
            title = { Text(state.title.ifBlank { "原档信息" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("共 ${state.pageCount} 页")
                    if (state.tags.isNotBlank()) {
                        Text("标签：${state.tags}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false; touch() }) { Text("关闭") } },
        ) },
        pageMenu = { pageMenuIndex?.let { menuIdx ->
        PageMenuSheet(menuIdx + 1, state.offline,
            { vm.downloadCurrentPage(context, menuIdx); pageMenuIndex = null; touch() },
            {
                val pageModel = runCatching { vm.pageModel(menuIdx) }.getOrNull()
                if (!state.offline && pageModel is PageModel.RemotePage) {
                    val url = ApiClient.displayBaseUrl().trimEnd('/') + "/" + pageModel.url.removePrefix(ApiClient.SENTINEL_BASE)
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("页面链接", url))
                    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                }
                pageMenuIndex = null; touch()
            },
            { vm.setCoverFromPage(menuIdx + 1); pageMenuIndex = null; touch() },
            { pageMenuIndex = null; showInfo = true; touch() },
            { pageMenuIndex = null; touch() })
        } },
    )
    LaunchedEffect(Unit, showUi) { focusRequester.requestFocus() }
}

@Composable
private fun ContinuousReader(listState: LazyListState, models: List<Any>, fitScale: ContentScale, onPage: (Int) -> Unit, onToggleUi: () -> Unit, onPageLongPress: (Int) -> Unit) {
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { onPage(it) } }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(models, key = { index, model -> stablePageKey(model, index) }) { index, model ->
            ZoomablePage(model, Modifier.fillMaxWidth(), fitScale, false, { _ -> onToggleUi() }) { onPageLongPress(index) }
        }
    }
}

@Composable
private fun VerticalPagedReader(
    pagerState: PagerState,
    models: List<Any>,
    pagesPerScreen: Int,
    firstPageAlone: Boolean,
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
            .collect {
                onPage(ReaderPageMapping.screenToFirstPage(it, models.size, pagesPerScreen, firstPageAlone))
            }
    }
    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = preloadCount,
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        val start = ReaderPageMapping.screenToFirstPage(screen, models.size, pagesPerScreen, firstPageAlone)
        Row(Modifier.fillMaxSize()) {
            val count = if (firstPageAlone && screen == 0) 1 else pagesPerScreen
            repeat(count) { i ->
                val idx = start + i
                if (idx < models.size) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ZoomablePage(
                            models[idx],
                            Modifier.fillMaxSize(),
                            fitScale,
                            tapZonesEnabled,
                            verticalTapZones = true,
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
private fun HorizontalReader(pagerState: PagerState, models: List<Any>, reverse: Boolean, pagesPerScreen: Int, firstPageAlone: Boolean, preloadCount: Int, fitScale: ContentScale, tapZonesEnabled: Boolean, onPage: (Int) -> Unit, onToggleUi: () -> Unit, onPageLongPress: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, pagesPerScreen, firstPageAlone) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect {
            onPage(ReaderPageMapping.screenToFirstPage(it, models.size, pagesPerScreen, firstPageAlone))
        }
    }
    HorizontalPager(state = pagerState, reverseLayout = reverse, beyondViewportPageCount = preloadCount, modifier = Modifier.fillMaxSize()) { screen ->
        val start = ReaderPageMapping.screenToFirstPage(screen, models.size, pagesPerScreen, firstPageAlone)
        Row(Modifier.fillMaxSize()) {
            val count = if (firstPageAlone && screen == 0) 1 else pagesPerScreen
            repeat(count) { i ->
                val idx = start + i
                if (idx < models.size) Box(Modifier.weight(1f).fillMaxSize()) {
                    ZoomablePage(models[idx], Modifier.fillMaxSize(), fitScale, tapZonesEnabled, { region ->
                        when (region) {
                            TapRegion.CENTER -> onToggleUi()
                            TapRegion.LEFT -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceIn(0, pagerState.pageCount - 1)) }
                            TapRegion.RIGHT -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceIn(0, pagerState.pageCount - 1)) }
                        }
                    }, onLongPress = { onPageLongPress(idx) })
                } else Spacer(Modifier.weight(1f))
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
    verticalTapZones: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val imageModel = coilPageModel(model)
    val scale = remember(model) { mutableFloatStateOf(1f) }
    val offset = remember(model) { mutableStateOf(Offset.Zero) }
    val gestureArbiter = remember(model) { ReaderGestureArbiter() }
    val tapZonesEnabledState = rememberUpdatedState(tapZonesEnabled)
    val verticalTapZonesState = rememberUpdatedState(verticalTapZones)
    val onTapRegionState = rememberUpdatedState(onTapRegion)
    val onLongPressState = rememberUpdatedState(onLongPress)
    var loading by remember(model) { mutableStateOf(true) }
    Box(modifier.clipToBounds().pointerInput(model) {
        detectTapGestures(onTap = { tapOffset ->
            if (scale.floatValue <= 1f) {
                if (!tapZonesEnabledState.value) onTapRegionState.value(TapRegion.CENTER) else {
                    val position = if (verticalTapZonesState.value) tapOffset.y else tapOffset.x
                    val extent = if (verticalTapZonesState.value) size.height.toFloat() else size.width.toFloat()
                    onTapRegionState.value(
                        when {
                            position < extent / 3f -> TapRegion.LEFT
                            position > extent * 2f / 3f -> TapRegion.RIGHT
                            else -> TapRegion.CENTER
                        },
                    )
                }
            }
        }, onDoubleTap = {
            if (scale.floatValue > 1f) {
                scale.floatValue = 1f
                offset.value = Offset.Zero
                gestureArbiter.resetZoom()
            } else {
                scale.floatValue = 2f
                offset.value = Offset.Zero
                gestureArbiter.applyZoom(2f)
            }
        }, onLongPress = { if (scale.floatValue <= 1f) onLongPressState.value?.invoke() })
    }.pointerInput(model) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            gestureArbiter.resetZoom()
            if (scale.floatValue > 1f) gestureArbiter.applyZoom(scale.floatValue)
            gestureArbiter.begin()
            var ownsGesture = scale.floatValue > 1f
            do {
                val event = awaitPointerEvent()
                val pressedPointers = event.changes.count { it.pressed }
                if (pressedPointers >= 2) {
                    ownsGesture = true
                    gestureArbiter.begin(pressedPointers)
                }

                if (ownsGesture) {
                    val oldScale = scale.floatValue
                    val newScale = gestureArbiter.applyZoom(event.calculateZoom()).zoom.coerceIn(1f, 5f)
                    if (newScale <= 1f) {
                        scale.floatValue = 1f
                        offset.value = Offset.Zero
                    } else {
                        val scaleRatio = newScale / oldScale
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val centroidFromCenter = event.calculateCentroid(useCurrent = true) - center
                        val transformedOffset = offset.value * scaleRatio +
                            centroidFromCenter * (1f - scaleRatio) +
                            event.calculatePan()
                        val maxOffsetX = size.width * (newScale - 1f) / 2f
                        val maxOffsetY = size.height * (newScale - 1f) / 2f
                        scale.floatValue = newScale
                        offset.value = Offset(
                            transformedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                            transformedOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
                        )
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged()) change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
            gestureArbiter.end()
        }
    }, contentAlignment = Alignment.Center) {
        AsyncImage(model = imageModel, contentDescription = null, contentScale = fitScale, onLoading = { loading = true }, onSuccess = { loading = false }, onError = { loading = false }, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale.floatValue; scaleY = scale.floatValue; translationX = offset.value.x; translationY = offset.value.y })
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Color.White) }
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
    onOpenToc: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFunctions: () -> Unit,
    onOpenAutoPage: () -> Unit,
) {
    var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var previewPage by remember(currentPage) { mutableStateOf(currentPage) }
    val thumbnailListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentPage - 2).coerceAtLeast(0),
    )
    LaunchedEffect(currentPage, dragging) {
        if (!dragging) {
            sliderValue = currentPage.toFloat()
            previewPage = currentPage
        }
    }
    LaunchedEffect(previewPage, models.size) {
        if (models.isEmpty() || thumbnailListState.isScrollInProgress) return@LaunchedEffect
        val targetPage = previewPage.coerceIn(models.indices)
        thumbnailListState.animateScrollToItem((targetPage - 2).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxWidth().background(Color(0xE9000000)).padding(vertical = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val thumbnailPadding = 2.dp
            val thumbnailSpacing = 4.dp
            val thumbnailWidth = ((maxWidth - thumbnailPadding * 2 - thumbnailSpacing * 4) / 4.7f)
                .coerceAtLeast(44.dp)
            LazyRow(
                state = thumbnailListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = thumbnailPadding),
                horizontalArrangement = Arrangement.spacedBy(thumbnailSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                items(models.size, key = { page -> stablePageKey(models[page], page) }) { page ->
                    val selected = page == previewPage
                    Column(
                        Modifier
                            .width(thumbnailWidth)
                            .pointerInput(page) {
                                detectTapGestures(
                                    onTap = { onJump(page); onTouch() },
                                    onLongPress = { onLongPress(page) },
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.69f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color(0xFF202020)),
                        ) {
                            AsyncImage(
                                model = coilPageModel(models[page]),
                                contentDescription = "第 ${page + 1} 页",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    alpha = if (selected) 1f else 0.88f
                                },
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${page + 1}",
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onPrev(); onTouch() },
                enabled = currentPage > 0,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一页", tint = Color.White)
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    sliderValue = it
                    previewPage = it.toInt().coerceIn(0, total - 1)
                    onJump(previewPage)
                    onTouch()
                },
                onValueChangeFinished = { dragging = false; onTouch() },
                valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                enabled = total > 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${previewPage + 1} / $total",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            IconButton(
                onClick = { onNext(); onTouch() },
                enabled = currentPage < total - 1,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页", tint = Color.White)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenAutoPage) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "自动翻页设置",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onOpenToc) {
                Icon(Icons.Filled.AutoStories, contentDescription = "打开目录", tint = Color.White)
            }
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.Timeline, contentDescription = "查看阅读进度", tint = Color.White)
            }
            IconButton(onClick = onOpenFunctions) {
                Icon(Icons.Filled.Settings, contentDescription = "打开阅读设置", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(toc: List<TocEntry>, currentPage: Int, offline: Boolean, onJump: (Int) -> Unit, onAdd: (String) -> Unit, onDelete: (Int) -> Unit, onDismiss: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var addTitle by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!offline) TextButton(onClick = { addTitle = ""; showAdd = true }) { Text("添加 · 当前第 ${currentPage + 1} 页") }
            }
            Spacer(Modifier.height(8.dp))
            if (toc.isEmpty()) Text("暂无目录", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) { itemsIndexed(toc) { _, entry -> Row(Modifier.fillMaxWidth().clickable { onJump(entry.page) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name.ifBlank { "未命名章节" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("第 ${entry.page} 页", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!offline) TextButton(onClick = { onDelete(entry.page) }) { Text("删除") }
            } } }
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("添加目录") }, text = { Column { Text("页码：${currentPage + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); TextField(value = addTitle, onValueChange = { addTitle = it }, singleLine = true, placeholder = { Text("章节名称") }) } }, confirmButton = { TextButton(enabled = addTitle.isNotBlank(), onClick = { onAdd(addTitle); showAdd = false }) { Text("添加") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AutoPageSheet(
    state: ReaderViewModel.UiState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
    onTouch: () -> Unit,
) {
    var speedInput by remember { mutableStateOf(state.autoScrollSpeed) }
    val speedSeconds = autoScrollSeconds(state.autoScrollSpeed)
    val inputSeconds = speedInput.toFloatOrNull()
    val speedInputValid = inputSeconds != null &&
        inputSeconds in AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS

    LaunchedEffect(state.autoScrollSpeed) {
        val localValue = speedInput.toFloatOrNull()
        if (
            localValue == null ||
            localValue !in AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS ||
            normalizeAutoScrollSpeed(localValue.toString()) != state.autoScrollSpeed
        ) {
            speedInput = state.autoScrollSpeed
        }
    }

    fun updateSpeed(seconds: Float) {
        val normalized = normalizeAutoScrollSpeed(seconds.toString())
        speedInput = normalized
        vm.setAutoScrollSpeed(normalized)
        onTouch()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("自动翻页", style = MaterialTheme.typography.titleMedium)
            ReaderSettingsLabel("播放状态")
            FilterChip(
                selected = state.autoScrolling,
                onClick = { vm.toggleAutoScroll(); onTouch() },
                leadingIcon = {
                    Icon(
                        if (state.autoScrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.autoScrolling) "暂停自动翻页" else "开始自动翻页",
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = { Text(if (state.autoScrolling) "暂停" else "开始") },
            )

            ReaderSettingsLabel("翻页速度")
            Text(
                "当前：${normalizeAutoScrollSpeed(speedSeconds.toString())} 秒/屏",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.5f, 3f, 6f).forEach { seconds ->
                    val normalized = normalizeAutoScrollSpeed(seconds.toString())
                    FilterChip(
                        selected = state.autoScrollSpeed == normalized,
                        onClick = { updateSpeed(seconds) },
                        label = { Text("$normalized 秒") },
                    )
                }
            }
            Slider(
                value = speedSeconds,
                onValueChange = ::updateSpeed,
                valueRange = AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS,
                steps = 58,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = speedInput,
                onValueChange = { value ->
                    speedInput = value
                    onTouch()
                    value.toFloatOrNull()
                        ?.takeIf { it in AUTO_SCROLL_MIN_SECONDS..AUTO_SCROLL_MAX_SECONDS }
                        ?.let { vm.setAutoScrollSpeed(it.toString()) }
                },
                label = { Text("秒/屏") },
                supportingText = { Text("可输入 0.5–30 秒") },
                isError = speedInput.isNotEmpty() && !speedInputValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsSheet(
    state: ReaderViewModel.UiState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
    onTouch: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)

            ReaderSettingsLabel("阅读布局")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("single" to "单页", "multi" to "多页", "continuous" to "连续").forEach { (mode, label) ->
                    FilterChip(
                        selected = state.readerMode == mode,
                        onClick = { vm.setReaderMode(mode); onTouch() },
                        label = { Text(label) },
                    )
                }
            }

            ReaderSettingsLabel("阅读方向")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ltr" to "左→右", "rtl" to "右→左", "ttb" to "上→下").forEach { (direction, label) ->
                    FilterChip(
                        selected = state.readingDirection == direction,
                        onClick = { vm.setReadingDirection(direction); onTouch() },
                        label = { Text(label) },
                    )
                }
            }

            ReaderSettingsLabel("双页")
            FilterChip(
                selected = state.firstPageAlone,
                onClick = { vm.setFirstPageAlone(!state.firstPageAlone); onTouch() },
                label = { Text("首封面单独显示") },
            )

            ReaderSettingsLabel("图片适应")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "fitWidth" to "适应宽度",
                    "fitHeight" to "适应高度",
                    "fitScreen" to "适应屏幕",
                    "original" to "原始尺寸",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.readerFitMode == mode,
                        onClick = { vm.setReaderFitMode(mode); onTouch() },
                        label = { Text(label) },
                    )
                }
            }

            ReaderSettingsLabel("阅读亮度")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.readerBrightness < 0) "跟随系统" else "${state.readerBrightness}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.readerBrightness < 0,
                    onClick = { vm.setReaderBrightness(-1); onTouch() },
                    label = { Text("跟随系统") },
                )
            }
            Slider(
                value = if (state.readerBrightness < 0) 50f else state.readerBrightness.toFloat(),
                onValueChange = { vm.setReaderBrightness(it.toInt()); onTouch() },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReaderSettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageMenuSheet(pageNumber: Int, offline: Boolean, onSave: () -> Unit, onCopyLink: () -> Unit, onSetCover: () -> Unit, onShowInfo: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("第 $pageNumber 页", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存此页到收藏") }
            TextButton(onClick = onCopyLink, enabled = !offline, modifier = Modifier.fillMaxWidth()) { Text("复制本页链接") }
            if (!offline) TextButton(onClick = onSetCover, modifier = Modifier.fillMaxWidth()) { Text("设为档案封面") }
            TextButton(onClick = onShowInfo, modifier = Modifier.fillMaxWidth()) { Text("查看信息") }
        }
    }
}
