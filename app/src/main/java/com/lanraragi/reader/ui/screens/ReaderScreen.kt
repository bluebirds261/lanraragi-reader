package com.lanraragi.reader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.lanraragi.reader.data.ServerCapabilities
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.Tankoubon
import com.lanraragi.reader.data.model.TocEntry
import com.lanraragi.reader.data.PageThumbQueue
import com.lanraragi.reader.data.normalizeAutoScrollSpeed
import com.lanraragi.reader.data.reader.DefaultReaderSourceResolver
import com.lanraragi.reader.data.reader.PageModel
import com.lanraragi.reader.data.reader.PageSource
import com.lanraragi.reader.data.reader.PageSourceResolver
import com.lanraragi.reader.data.reader.PrefetchCoordinator
import com.lanraragi.reader.data.reader.ReaderGestureArbiter
import com.lanraragi.reader.data.ReaderOrientationProfile
import com.lanraragi.reader.ui.reader.ReaderAction
import com.lanraragi.reader.ui.reader.CoilPagePrefetcher
import com.lanraragi.reader.ui.reader.ReaderRouteEffects
import com.lanraragi.reader.ui.reader.ReaderSurface
import com.lanraragi.reader.ui.reader.ReaderSurfaceMode
import com.lanraragi.reader.ui.reader.ReaderSheets
import com.lanraragi.reader.ui.reader.rememberReaderFrameSampler
import com.lanraragi.reader.ui.reader.readerActionForKey
import com.lanraragi.reader.ui.reader.TapZoneAction
import com.lanraragi.reader.ui.reader.TapZoneGrid
import com.lanraragi.reader.data.reader.ReaderSession
import com.lanraragi.reader.data.reader.ReaderSourceResolver
import com.lanraragi.reader.data.reader.SavedArchivePageSource
import com.lanraragi.reader.domain.reader.ReaderPageMapping
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.CoverChangeBus
import com.lanraragi.reader.ui.LibraryRefreshBus
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.asImageRequest
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun fitContentScale(mode: String): ContentScale = when (mode) {
    "fitHeight" -> ContentScale.FillHeight
    "fitScreen" -> ContentScale.Fit
    "original" -> ContentScale.None
    else -> ContentScale.FillWidth
}

/** 缩放状态下越过显示区边界的快速滑动触发跨页翻页的速度阈值（px/s）。 */
private const val FLING_BEYOND_EDGE_VELOCITY = 2500f

/** 橡皮筋阻力：边界内 1:1 跟手，越界部分按 0.5 衰减。 */
private fun resistBeyondEdge(value: Float, max: Float): Float = when {
    max <= 0f -> 0f
    value > max -> max + (value - max) * 0.5f
    value < -max -> -max + (value + max) * 0.5f
    else -> value
}

/** 分页翻页动画预设：基于页面偏移比例应用 graphicsLayer 变换；连续滚动模式不调用。 */
private fun GraphicsLayerScope.applyPageTurnEffect(animation: String, pageOffset: Float) {
    val clamped = pageOffset.coerceIn(-1f, 1f)
    when (animation) {
        "curl" -> {
            rotationY = -clamped * 30f
            cameraDistance = 8f * density
            alpha = 1f - abs(clamped) * 0.2f
        }
        "fade" -> alpha = 1f - abs(clamped) * 0.9f
        else -> {} // smooth 及未知值：无变换
    }
}

/** Stable identity for Compose lazy containers; never derives identity from the list position alone. */
private fun stablePageKey(model: Any, index: Int): String = when (model) {
    is ArchivePageModel -> "archive:${model.archiveUri}:$index"
    is PageModel -> "${model.revision}:$index"
    is java.io.File -> "file:${model.absolutePath}:$index"
    else -> "page:$index:${model.toString()}"
}

/** 与 PageSource.kt 的 revisionFor 同构：按内容派生稳定 revision，供 Coil 缓存键区分档案与页。 */
private fun tankRevisionFor(sourceKey: String, values: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(values.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "$sourceKey:$digest"
}

/** 单行本成员档案的页序列：档案内页 URL + 展示标题 + 稳定 revision。 */
private data class TankMemberPages(val arcid: String, val title: String, val urls: List<String>) {
    val pageCount: Int get() = urls.size
    val revision: String = tankRevisionFor("tank:${arcid.lowercase()}", urls)
}

/** 成员档案内的一页：所属成员 + 档案内页序号。 */
private data class TankMemberPage(val member: TankMemberPages, val localIndex: Int)

/** Tankoubon 阅读页面源：把各成员档案的页依次扁平化为全局页序列（索引全局唯一）。 */
private class TankPageSource(
    private val tankId: String,
    private val members: List<TankMemberPages>,
    private val reload: suspend () -> List<TankMemberPages>,
) : PageSource {
    override val identity: ArchiveIdentity = ArchiveIdentity.Tankoubon(tankId)
    override val pageCount: Int = members.sumOf { it.urls.size }
    override val revision: String = tankRevisionFor(identity.sourceKey, members.flatMap { it.urls })

    /** 全局页索引 → (成员档案, 档案内页序号)。 */
    fun locate(index: Int): TankMemberPage {
        var offset = 0
        members.forEach { member ->
            if (index < offset + member.urls.size) return TankMemberPage(member, index - offset)
            offset += member.urls.size
        }
        throw IndexOutOfBoundsException("tank page index $index outside 0 until $pageCount")
    }

    /** 成员档案边界自动生成的目录项（page 为 1 起全局页号，供 TocSheet 跳转）。 */
    fun boundaryToc(): List<TocEntry> = members.mapIndexedNotNull { i, member ->
        if (member.urls.isEmpty()) {
            null
        } else {
            TocEntry(name = member.title.ifBlank { member.arcid }, page = startIndexOf(i) + 1)
        }
    }

    private fun startIndexOf(memberIndex: Int): Int = members.take(memberIndex).sumOf { it.urls.size }

    override suspend fun refresh(): PageSource = TankPageSource(tankId, reload(), reload)

    override fun pageModel(index: Int): PageModel {
        val located = locate(index)
        return PageModel.RemotePage(
            url = located.member.urls[located.localIndex],
            index = index,
            thumbnail = false,
            revision = located.member.revision,
        )
    }

    override fun thumbnailModel(index: Int): PageModel {
        val located = locate(index)
        return PageModel.RemotePage(
            url = ApiClient.pageThumbnailUrl(located.member.arcid, located.localIndex),
            index = index,
            thumbnail = true,
            revision = located.member.revision,
        )
    }
}

@Composable
private fun coilPageModel(model: Any, attempt: Int = 0): Any {
    val context = LocalContext.current
    // 重试时把 attempt 计入 memoryCacheKey，使 Coil 视为不同请求而重新发起加载。
    val retrySuffix = if (attempt > 0) "#retry$attempt" else ""
    return when (model) {
        is PageModel.RemotePage -> ImageRequest.Builder(context)
            .data(model.url)
            .memoryCacheKey(model.cacheKey + retrySuffix)
            .diskCacheKey(model.cacheKey)
            .build()
        is PageModel.LocalEntry -> ArchivePageModel(
            model.uri,
            model.index,
            context,
            model.thumbnail,
            model.cacheKey + retrySuffix,
        ).asImageRequest()
        else -> model
    }
}

/** 时间线页缩略图生成管线阶段：IDLE 未触发；GENERATING 入队/轮询中；READY 缩略图可用；FAILED 失败（保持整页原图）。 */
enum class ThumbPhase { IDLE, GENERATING, READY, FAILED }

/** 时间线页缩略图生成状态（进度 p/total 仅在 GENERATING 阶段有意义）。 */
data class TimelineThumbState(
    val phase: ThumbPhase = ThumbPhase.IDLE,
    val progress: Int = 0,
    val total: Int = 0,
)

/** 时间线页缩略图请求：`GET api/archives/{id}/thumbnail?page=N`，记忆键带档案与页号，避免与整页原图缓存互扰。 */
@Composable
private fun timelineThumbImageModel(arcid: String, page: Int): Any {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(ApiClient.pageThumbnailUrl(arcid, page))
        .memoryCacheKey("pagethumb:$arcid:$page")
        .diskCacheKey("pagethumb:$arcid:$page")
        .build()
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
    /** 非 null 时进入 tank 模式：阅读整卷（Tankoubon），arcid 不参与。 */
    private val tankId: String? = null,
) : ViewModel() {
    val diagnostics = container.diagnostics

    private companion object {
        /** 时间线页缩略图进度轮询间隔。 */
        const val THUMB_POLL_INTERVAL_MS = 2_000L

        /** 轮询兜底时限：minion 任务的失败终态无法从进度契约直接判定，超时后按可用处理（缺页回退占位/原图）。 */
        const val THUMB_POLL_MAX_MS = 10 * 60_000L
    }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val tags: String = "",
        val tankId: String? = null,
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
        val tapZoneConfig: String? = null,
        val keepScreenOn: Boolean = true,
        val volumeKeysEnabled: Boolean = true,
        val preloadLocalCount: Int = 5,
        val toc: List<TocEntry> = emptyList(),
        val pageSourceRevision: String? = null,
        val firstPageAlone: Boolean = false,
        val readerPageOverlay: Boolean = true,
        val pageTurnAnimation: String = "smooth",
        val imageRegionWidthRatio: Float = 1f,
    )

    private val _state = MutableStateFlow(UiState(tankId = tankId))
    val state = _state.asStateFlow()
    private var syncJob: Job? = null
    private var lastSynced = -1
    private var metadataReady = false
    private var pendingInitialPage = initialPage?.coerceAtLeast(0) ?: 0

    /**
     * 初始页只允许「强制应用一次」。
     *
     * [pendingInitialPage] 由路由参数与本地/服务器进度推导，可能晚于会话就绪，
     * 所以会话状态收集器需要把会话页对齐到它。但这个对齐必须是一次性的：
     * 之前它没有清除时机，于是会话状态每变一次就把 currentPage 拽回 pendingInitialPage，
     * 结果翻页 / 点缩略图 / 音量键全部被立刻回滚，阅读器锁死在起始页。
     *
     * 每当 [pendingInitialPage] 被重新推导（元数据加载、tank 进度）就重新武装一次。
     */
    private var pendingInitialPageApplied = false
    private var orientationProfile = ReaderOrientationProfile.GLOBAL
    private val defaultSourceResolver = DefaultReaderSourceResolver(
        PageSourceResolver(
            container.context,
            container.repository,
            container.offlineCache,
            container.savedArtifactRepository,
        ),
    )
    /** Shared page-source/session owner; legacy metadata state remains in this VM for compatibility. */
    private val readerSession = ReaderSession(
        resolver = ReaderSourceResolver { identity, localUri ->
            if (identity is ArchiveIdentity.Tankoubon) {
                TankPageSource(identity.tankId, resolveTankMembers()) { resolveTankMembers() }
            } else {
                defaultSourceResolver.resolve(identity, localUri)
            }
        },
        scope = viewModelScope,
        diagnostics = container.diagnostics,
    )
    private val prefetchCoordinator = PrefetchCoordinator(viewModelScope)
    private val pagePrefetcher = CoilPagePrefetcher(container.context)

    /** 时间线页缩略图生成状态（仅服务器档案参与；本地/离线/tank 模式保持 IDLE，时间线用整页原图）。 */
    private val _timelineThumbs = MutableStateFlow(TimelineThumbState())
    val timelineThumbs: StateFlow<TimelineThumbState> = _timelineThumbs.asStateFlow()
    private var thumbPollJob: Job? = null
    private var thumbJobId: String? = null
    private var thumbQueueAttempted = false

    /** 单行本详情按会话记忆，避免 resolver 与 load() 双重拉取。 */
    private var tankMemo: Tankoubon? = null

    private suspend fun tankData(): Tankoubon {
        tankMemo?.let { return it }
        val tank = container.repository.getTankoubonFull(requireNotNull(tankId))
        tankMemo = tank
        return tank
    }

    /** 拉取整卷并把各成员档案的页 URL 按序扁平化（单个成员失败容错为空页，保证其余成员可读）。 */
    private suspend fun resolveTankMembers(): List<TankMemberPages> {
        val tank = tankData()
        return tank.archives.map { arcid ->
            val title = tank.full_data.firstOrNull { it.arcid == arcid }?.title.orEmpty()
            val urls = try {
                container.repository.getPageUrls(arcid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            TankMemberPages(arcid, title, urls)
        }
    }

    init {
        if (tankId != null) {
            readerSession.open(ArchiveIdentity.Tankoubon(tankId), initialPage = 0)
        } else {
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
        }
        viewModelScope.launch {
            readerSession.state.collect { session ->
                val source = session.source
                // 只在初始页尚未应用时对齐一次；之后 currentPage 完全交给会话（用户翻页）。
                val resolvedPage = if (source != null && !pendingInitialPageApplied) {
                    pendingInitialPage.coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))
                } else {
                    session.currentPage
                }
                if (source != null && !pendingInitialPageApplied) {
                    pendingInitialPageApplied = true
                    if (session.currentPage != resolvedPage) {
                        readerSession.setPage(resolvedPage)
                    }
                }
                _state.update { state ->
                    state.copy(
                        pageCount = source?.pageCount ?: state.pageCount,
                        pageSourceRevision = source?.revision ?: state.pageSourceRevision,
                        offline = source is SavedArchivePageSource ||
                            (
                                source != null && source.identity !is ArchiveIdentity.Remote &&
                                    source.identity !is ArchiveIdentity.Tankoubon
                                ),
                        toc = (source as? TankPageSource)?.boundaryToc() ?: state.toc,
                        currentPage = resolvedPage,
                        loading = !metadataReady || session.loading,
                        error = session.error ?: state.error,
                    )
                }
            }
        }
        load()
        if (tankId == null) {
            viewModelScope.launch { container.settingsRepository.setLastReadArcId(arcid) }
        }
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
                        tapZoneConfig = settings.tapZoneConfig,
                        keepScreenOn = settings.keepScreenOn,
                        volumeKeysEnabled = settings.volumeKeysEnabled,
                        preloadLocalCount = settings.preloadLocalCount,
                        readerPageOverlay = settings.readerPageOverlay,
                        pageTurnAnimation = settings.pageTurnAnimation,
                        imageRegionWidthRatio = settings.imageRegionWidthRatio.coerceIn(0.5f, 1f),
                    )
                }

                if (tankId != null) {
                    // tank 模式：标题/进度/初始页来自整卷详情；页序列由 session resolver 扁平化加载。
                    val tank = tankData()
                    pendingInitialPage = initialPage?.coerceAtLeast(0)
                        ?: (tank.progress - 1).coerceAtLeast(0)
                    pendingInitialPageApplied = false
                    _state.update {
                        it.copy(
                            offline = false,
                            title = tank.name,
                            tags = tank.tags,
                            progress = tank.progress,
                        )
                    }
                    metadataReady = true
                    val session = readerSession.state.value
                    if (session.source != null) readerSession.setPage(pendingInitialPage)
                    _state.update {
                        it.copy(
                            pageCount = session.pageCount,
                            currentPage = pendingInitialPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
                            loading = session.loading || session.source == null,
                            error = session.error ?: it.error,
                        )
                    }
                    return@launch
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
                    pendingInitialPageApplied = false
                } else {
                    val cached = container.offlineCache.cached(arcid)
                    if (cached != null) {
                        val cachedProgress = savedPage.takeIf { it > 0 }
                            ?: cached.metadata?.progress?.minus(1)?.coerceAtLeast(0) ?: 0
                        _state.update {
                            it.copy(
                                offline = true,
                                title = cached.title,
                                tags = cached.metadata?.tags ?: "",
                                toc = cached.metadata?.toc ?: emptyList(),
                            )
                        }
                        pendingInitialPage = cachedProgress
                        pendingInitialPageApplied = false
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
                        pendingInitialPageApplied = false
                    }
                }
                metadataReady = true
                val session = readerSession.state.value
                // 元数据（含本地/服务器进度）可能晚于会话就绪：这里必须把会话页也对齐到刚推导出的
                // 初始页，否则收集器里「初始页尚未应用」的分支会在用户第一次翻页时把它回滚。
                if (session.source != null) {
                    readerSession.setPage(
                        pendingInitialPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
                    )
                }
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
        val activeTankId = tankId
        if (activeTankId != null) {
            // 各子档案自身进度写入（本地历史照常）。
            val tankSource = source as? TankPageSource
            if (tankSource != null) {
                val located = tankSource.locate(page)
                viewModelScope.launch {
                    container.historyRepository.recordProgress(
                        located.member.arcid,
                        located.localIndex,
                        located.member.pageCount,
                        located.member.title,
                    )
                }
            }
            // 整卷进度回传：照 per-archive 的 700ms 防抖模式，page 为全局 1 起页号。
            syncJob?.cancel()
            syncJob = viewModelScope.launch {
                delay(700)
                val p = _state.value.currentPage
                if (p >= 0 && p != lastSynced) {
                    lastSynced = p
                    // A3 能力门控：与单档案进度一样，服务端未开启进度记录时不发（该端点必然被拒）。
                    if (!ServerCapabilities(ApiClient.config.serverInfo.value).supportsProgress) return@launch
                    try {
                        container.repository.updateTankoubonProgress(activeTankId, p + 1)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 回传失败静默忽略，后续翻页会按新进度重试。
                    }
                }
            }
            return
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
                // B8：本地已即时落库，服务端回传按 5 秒合并窗口批量推送，避免逐页打服务器。
                container.progressWriter.scheduleFlush()
            }
        }
    }

    fun addToc(title: String) {
        val s = _state.value
        // tank 模式目录由成员档案边界自动生成，不支持增删。
        if (tankId != null || s.offline || s.currentPage !in 0 until s.pageCount || title.isBlank()) return
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
        // tank 模式目录由成员档案边界自动生成，不支持增删。
        if (tankId != null || arcid.startsWith("local_")) return
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

    fun setCoverFromPage(page: Int) {
        // tank 模式不做 tank 缩略图，也不改写成员档案封面。
        if (tankId != null || arcid.startsWith("local_")) return
        viewModelScope.launch {
            try {
                container.repository.setThumbnailFromPage(arcid, page.coerceAtLeast(1))
                container.thumbnailRepository.refreshCover(arcid)
                // 只让这张封面失效，不牵连全库封面缓存。
                CoverChangeBus.notifyChanged(arcid)
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

    fun setReaderBackground(bg: String) {
        _state.update { it.copy(readerBackground = bg) }
        viewModelScope.launch { container.settingsRepository.saveReaderBackground(bg) }
    }

    fun setPageTurnAnimation(value: String) {
        _state.update { it.copy(pageTurnAnimation = value) }
        viewModelScope.launch { container.settingsRepository.savePageTurnAnimation(value) }
    }

    fun setImageRegionWidthRatio(value: Float) {
        val v = value.coerceIn(0.5f, 1f)
        _state.update { it.copy(imageRegionWidthRatio = v) }
        viewModelScope.launch { container.settingsRepository.saveImageRegionWidthRatio(v) }
    }

    fun setTapZonesEnabled(enabled: Boolean) {
        _state.update { it.copy(tapZonesEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(enabled) }
    }

    fun setVolumeKeysEnabled(enabled: Boolean) {
        _state.update { it.copy(volumeKeysEnabled = enabled) }
        viewModelScope.launch { container.settingsRepository.setVolumeKeysEnabled(enabled) }
    }

    fun setMultiPageCount(n: Int) {
        val v = n.coerceIn(2, 8)
        _state.update { it.copy(multiPageCount = v) }
        viewModelScope.launch { container.settingsRepository.setMultiPageCount(v) }
    }

    fun setPreloadOnlineCount(n: Int) {
        val v = n.coerceIn(1, 20)
        _state.update { it.copy(preloadOnlineCount = v) }
        viewModelScope.launch { container.settingsRepository.setPreloadOnlineCount(v) }
    }

    fun setPreloadLocalCount(n: Int) {
        val v = n.coerceIn(1, 50)
        _state.update { it.copy(preloadLocalCount = v) }
        viewModelScope.launch { container.settingsRepository.setPreloadLocalCount(v) }
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

    /**
     * 打开时间线时触发整本页缩略图管线（服务器档案）：
     * 首次打开入队 `queuePageThumbnails`，随后以 2s 间隔轮询 `minionPageThumbProgress` 直至完成或超时；
     * 时间线隐藏时 [stopTimelineThumbs] 取消轮询，重开时间线凭 jobId 恢复轮询，不重复入队。
     */
    fun startTimelineThumbs() {
        if (tankId != null || arcid.startsWith("local_") || _state.value.offline) return
        val phase = _timelineThumbs.value.phase
        if (phase == ThumbPhase.READY || phase == ThumbPhase.FAILED) return
        if (thumbPollJob?.isActive == true) return
        thumbPollJob = viewModelScope.launch {
            if (thumbJobId == null) {
                // 防御分支：已尝试入队但未取得 jobId。入队失败已置 FAILED（开头已拦下）；
                // 入队请求被取消时 thumbQueueAttempted 已复位为 false，下次打开时间线可重试。
                if (thumbQueueAttempted) return@launch
                thumbQueueAttempted = true
                val queued = try {
                    container.repository.queuePageThumbnails(arcid)
                } catch (e: CancellationException) {
                    thumbQueueAttempted = false
                    throw e
                } catch (e: Exception) {
                    null
                }
                when (queued) {
                    is PageThumbQueue.Queued -> thumbJobId = queued.jobId
                    PageThumbQueue.AlreadyAvailable -> {
                        val pages = _state.value.pageCount
                        _timelineThumbs.value = TimelineThumbState(ThumbPhase.READY, pages, pages)
                        return@launch
                    }
                    else -> {
                        _timelineThumbs.value = TimelineThumbState(ThumbPhase.FAILED)
                        return@launch
                    }
                }
            }
            val jobId = thumbJobId ?: return@launch
            _timelineThumbs.value = _timelineThumbs.value.copy(phase = ThumbPhase.GENERATING)
            val deadline = System.currentTimeMillis() + THUMB_POLL_MAX_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val progress = try {
                    container.repository.minionPageThumbProgress(jobId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (progress != null) {
                    val (done, pages) = progress
                    _timelineThumbs.value = _timelineThumbs.value.copy(progress = done, total = pages)
                    if (pages > 0 && done >= pages) break
                }
                delay(THUMB_POLL_INTERVAL_MS)
            }
            // 轮询结束（完成或超时）：缩略图按可用处理；个别页缺失时服务端会回退占位图，加载失败再回退整页原图。
            _timelineThumbs.value = _timelineThumbs.value.copy(phase = ThumbPhase.READY)
        }
    }

    /** 时间线隐藏时停止进度轮询（服务端任务不受影响）。 */
    fun stopTimelineThumbs() {
        thumbPollJob?.cancel()
        thumbPollJob = null
    }

    override fun onCleared() {
        syncJob?.cancel()
        prefetchCoordinator.cancel()
        readerSession.close()
        val s = _state.value
        val page = if (s.currentPage in 0 until s.pageCount) s.currentPage + 1 else null
        if (page == null) return
        val activeTankId = tankId
        if (activeTankId != null) {
            // 退出时把整卷进度兜底回传（防抖未及落盘的情形）。
            container.applicationScope.launch {
                // A3 能力门控：服务端未开启进度记录时该端点必然被拒，直接跳过。
                if (!ServerCapabilities(ApiClient.config.serverInfo.value).supportsProgress) return@launch
                try {
                    container.repository.updateTankoubonProgress(activeTankId, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 回传失败静默忽略。
                }
            }
            return
        }
        if (arcid.startsWith("local_")) return
        container.applicationScope.launch {
            container.progressWriter.record(ArchiveIdentity.Remote(arcid), page - 1, s.pageCount)
            // 退出阅读器立刻补推一次，不再等到下次冷启动（否则本次会话进度只在本地）。
            try {
                container.progressWriter.flush()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 任务保留在 outbox，由 ProgressWriter 内部的有限次退避重试接手；仍失败则下次启动继续。
            }
        }
    }
}

@Composable
fun ReaderScreen(
    container: AppContainer,
    arcid: String = "",
    navController: NavController,
    initialPage: Int? = null,
    onBack: (() -> Unit)? = null,
    tankId: String? = null,
) {
    val vm: ReaderViewModel = viewModel { ReaderViewModel(container, arcid, initialPage, tankId) }
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
            else -> ReaderContent(vm, state, arcid, onBack)
        }
    }
}

@Composable
private fun ReaderContent(vm: ReaderViewModel, state: ReaderViewModel.UiState, arcid: String, onBack: (() -> Unit)? = null) {
    var showUi by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAutoPage by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    // 底部功能行「亮度」气泡：与 showTopMenu 一样，展开期间不自动隐藏 chrome。
    var showBrightness by remember { mutableStateOf(false) }
    var pageMenuIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    rememberReaderFrameSampler(vm.diagnostics)
    // 时间线页缩略图生成状态与进度条拖动时的中央大图预览页（null=未在拖动）。
    val timelineThumbs by vm.timelineThumbs.collectAsStateWithLifecycle()
    var sliderPreviewPage by remember { mutableStateOf<Int?>(null) }
    val useServerThumbs = state.tankId == null && !state.offline && !arcid.startsWith("local_")

    fun touch() { showUi = true; lastInteraction = System.currentTimeMillis() }

    LaunchedEffect(showUi, lastInteraction, showToc, showSettings, showAutoPage, showHistory, showInfo, showTopMenu, showBrightness, pageMenuIndex) {
        if (showUi && !showToc && !showSettings && !showAutoPage && !showHistory && !showInfo && !showTopMenu && !showBrightness && pageMenuIndex == null) {
            delay(3500)
            if (System.currentTimeMillis() - lastInteraction >= 3300) showUi = false
        }
    }

    // 时间线可见（showUi）时启动整本页缩略图管线，隐藏即停轮询；重开时间线恢复轮询不重复入队。
    LaunchedEffect(showUi) {
        if (showUi) vm.startTimelineThumbs() else vm.stopTimelineThumbs()
    }

    val bgColor = readerBackgroundColor(state.readerBackground)
    val fitScale = fitContentScale(state.readerFitMode)
    val models = remember(state.pageCount, state.offline, state.pageSourceRevision) {
        (0 until state.pageCount).map(vm::pageModel)
    }
    val reverse = state.readingDirection == "rtl"
    val tapGrid = remember(state.tapZoneConfig) { TapZoneGrid.deserialize(state.tapZoneConfig) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(configuration.orientation) { vm.applyOrientation(configuration.orientation) }
    val pagesPerScreen = when {
        // tank 模式不做双页。
        state.tankId != null -> 1
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

    // 点击分区命中 → 动作：PREV/NEXT 复用 stepScreen（分页模式翻页，连续模式滚动到上/下一页边界），
    // MENU 切换 chrome 显隐，NONE 无操作；tapZonesEnabled 关闭时任意点按仅切换 chrome。
    fun handleTap(fx: Float, fy: Float) {
        if (!state.tapZonesEnabled) {
            if (showUi) showUi = false else touch()
            return
        }
        when (tapGrid.actionAt(fx, fy, rtl = reverse)) {
            TapZoneAction.PREV -> stepScreen(-1, revealUi = false)
            TapZoneAction.NEXT -> stepScreen(1, revealUi = false)
            TapZoneAction.MENU -> if (showUi) showUi = false else touch()
            TapZoneAction.NONE -> Unit
        }
    }

    // 页面操作回调：由长按菜单（PageMenuSheet）与顶栏溢出菜单共用。
    fun copyPageLink(idx: Int) {
        val pageModel = runCatching { vm.pageModel(idx) }.getOrNull()
        if (!state.offline && pageModel is PageModel.RemotePage) {
            val url = ApiClient.displayBaseUrl().trimEnd('/') + "/" + pageModel.url.removePrefix(ApiClient.SENTINEL_BASE)
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("页面链接", url))
            Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
        }
        pageMenuIndex = null
        touch()
    }
    fun setPageAsCover(idx: Int) { vm.setCoverFromPage(idx + 1); pageMenuIndex = null; touch() }
    fun showPageInfo() { pageMenuIndex = null; showInfo = true; touch() }

    // 续读提示：进入阅读器时若落在上次阅读位置（>0），短暂提示并提供"从头开始"入口。
    LaunchedEffect(Unit) {
        if (state.currentPage > 0) {
            launch {
                val result = snackbarHostState.showSnackbar(
                    message = "已跳转到上次位置 · 第 ${state.currentPage + 1} 页",
                    actionLabel = "从头开始",
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) jumpTo(0)
            }
            delay(2500)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

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
                state.imageRegionWidthRatio,
                vm::reportPage,
                ::handleTap,
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
                state.imageRegionWidthRatio,
                state.pageTurnAnimation,
                vm::reportPage,
                ::handleTap,
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
                state.imageRegionWidthRatio,
                state.pageTurnAnimation,
                vm::reportPage,
                ::handleTap,
                ::openPageMenu,
            ) },
        )

        // 顶部控制栏：显隐与底部条同步（showUi 驱动）。
        AnimatedVisibility(
            visible = showUi,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xE9000000))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                }
                Text(
                    text = state.title.ifBlank { arcid },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                Text(
                    text = "${state.currentPage + 1} / ${state.pageCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Box {
                    IconButton(onClick = { touch(); showTopMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作", tint = Color.White)
                    }
                    DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("复制链接") },
                            enabled = !state.offline,
                            onClick = { showTopMenu = false; copyPageLink(state.currentPage) },
                        )
                        if (!state.offline && state.tankId == null) {
                            DropdownMenuItem(
                                text = { Text("设为封面") },
                                onClick = { showTopMenu = false; setPageAsCover(state.currentPage) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("查看信息") },
                            onClick = { showTopMenu = false; showPageInfo() },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showUi,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it },
        ) {
            ReaderTimeline(
                currentPage = state.currentPage,
                total = state.pageCount,
                models = models,
                arcid = arcid,
                useServerThumbs = useServerThumbs,
                thumbs = timelineThumbs,
                onJump = ::jumpTo,
                onPrev = { stepScreen(-1) },
                onNext = { stepScreen(1) },
                onTouch = ::touch,
                onLongPress = ::openPageMenu,
                onOpenToc = { touch(); showToc = true },
                onOpenHistory = { touch(); showHistory = true },
                onOpenFunctions = { touch(); showSettings = true },
                onOpenAutoPage = { touch(); showAutoPage = true },
                brightness = state.readerBrightness,
                brightnessExpanded = showBrightness,
                onBrightnessExpandedChange = { expanded ->
                    showBrightness = expanded
                    if (expanded) touch()
                },
                onBrightnessChange = { vm.setReaderBrightness(it) },
                onBrightnessFollowSystem = { vm.setReaderBrightness(-1) },
                onPreview = { sliderPreviewPage = it },
            )
        }

        // 顶栏已展示页码，UI 可见时隐藏右上角页码悬浮层避免重叠。
        if (state.readerPageOverlay && !showUi) {
            Text(
                text = "${state.currentPage + 1} / ${state.pageCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // 拖动进度条时的中央大图预览：位于 chrome 之上、SnackbarHost 之下；
        // 容器与内容均无指针输入修饰，手势穿透到下层 Slider，不影响拖动。
        sliderPreviewPage?.takeIf { it in models.indices }?.let { previewTarget ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SliderPreviewOverlay(
                    page = previewTarget,
                    models = models,
                    arcid = arcid,
                    useServerThumbs = useServerThumbs,
                    thumbsReady = timelineThumbs.phase == ThumbPhase.READY,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
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
            // tank 模式目录由成员档案边界自动生成，不提供增删入口。
            state.offline || state.tankId != null,
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
        PageMenuSheet(menuIdx + 1, state.offline, state.tankId == null,
            { copyPageLink(menuIdx) },
            { setPageAsCover(menuIdx) },
            { showPageInfo() },
            { pageMenuIndex = null; touch() })
        } },
    )
    LaunchedEffect(Unit, showUi) { focusRequester.requestFocus() }
}

@Composable
private fun ContinuousReader(
    listState: LazyListState,
    models: List<Any>,
    fitScale: ContentScale,
    imageRegionWidthRatio: Float,
    onPage: (Int) -> Unit,
    onTapRegion: (fx: Float, fy: Float) -> Unit,
    onPageLongPress: (Int) -> Unit,
) {
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { onPage(it) } }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(models, key = { index, model -> stablePageKey(model, index) }) { index, model ->
            ZoomablePage(
                model,
                Modifier.fillMaxWidth(),
                fitScale,
                imageRegionWidthRatio = imageRegionWidthRatio,
                onTapRegion = onTapRegion,
                onLongPress = { onPageLongPress(index) },
            )
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
    imageRegionWidthRatio: Float,
    pageTurnAnimation: String,
    onPage: (Int) -> Unit,
    onTapRegion: (fx: Float, fy: Float) -> Unit,
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
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offsetFraction = (pagerState.currentPage - screen) + pagerState.currentPageOffsetFraction
                    applyPageTurnEffect(pageTurnAnimation, offsetFraction)
                },
        ) {
            val count = if (firstPageAlone && screen == 0) 1 else pagesPerScreen
            repeat(count) { i ->
                val idx = start + i
                if (idx < models.size) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ZoomablePage(
                            models[idx],
                            Modifier.fillMaxSize(),
                            fitScale,
                            imageRegionWidthRatio = imageRegionWidthRatio,
                            verticalPaging = true,
                            onFlingBeyondEdge = { direction ->
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        (pagerState.currentPage + direction).coerceIn(0, pagerState.pageCount - 1),
                                    )
                                }
                            },
                            verticalTapZones = true,
                            onTapRegion = onTapRegion,
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
private fun HorizontalReader(
    pagerState: PagerState,
    models: List<Any>,
    reverse: Boolean,
    pagesPerScreen: Int,
    firstPageAlone: Boolean,
    preloadCount: Int,
    fitScale: ContentScale,
    imageRegionWidthRatio: Float,
    pageTurnAnimation: String,
    onPage: (Int) -> Unit,
    onTapRegion: (fx: Float, fy: Float) -> Unit,
    onPageLongPress: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, pagesPerScreen, firstPageAlone) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect {
            onPage(ReaderPageMapping.screenToFirstPage(it, models.size, pagesPerScreen, firstPageAlone))
        }
    }
    HorizontalPager(state = pagerState, reverseLayout = reverse, beyondViewportPageCount = preloadCount, modifier = Modifier.fillMaxSize()) { screen ->
        val start = ReaderPageMapping.screenToFirstPage(screen, models.size, pagesPerScreen, firstPageAlone)
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offsetFraction = (pagerState.currentPage - screen) + pagerState.currentPageOffsetFraction
                    applyPageTurnEffect(pageTurnAnimation, offsetFraction)
                },
        ) {
            val count = if (firstPageAlone && screen == 0) 1 else pagesPerScreen
            repeat(count) { i ->
                val idx = start + i
                if (idx < models.size) Box(Modifier.weight(1f).fillMaxSize()) {
                    ZoomablePage(
                        models[idx],
                        Modifier.fillMaxSize(),
                        fitScale,
                        imageRegionWidthRatio = imageRegionWidthRatio,
                        onFlingBeyondEdge = { direction ->
                            // rtl（reverseLayout）下翻页方向取反，使甩动手势与视觉前进方向一致。
                            val delta = if (reverse) -direction else direction
                            scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + delta).coerceIn(0, pagerState.pageCount - 1)) }
                        },
                        onTapRegion = onTapRegion,
                        onLongPress = { onPageLongPress(idx) },
                    )
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
    onTapRegion: (fx: Float, fy: Float) -> Unit,
    verticalTapZones: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    imageRegionWidthRatio: Float = 1f,
    verticalPaging: Boolean = false,
    onFlingBeyondEdge: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var attempt by remember(model) { mutableIntStateOf(0) }
    val imageModel = coilPageModel(model, attempt)
    val scale = remember(model) { mutableFloatStateOf(1f) }
    val offset = remember(model) { mutableStateOf(Offset.Zero) }
    val gestureArbiter = remember(model) { ReaderGestureArbiter() }
    var zoomAnimJob by remember(model) { mutableStateOf<Job?>(null) }
    val verticalTapZonesState = rememberUpdatedState(verticalTapZones)
    val onTapRegionState = rememberUpdatedState(onTapRegion)
    val onLongPressState = rememberUpdatedState(onLongPress)
    val regionRatioState = rememberUpdatedState(imageRegionWidthRatio.coerceIn(0.5f, 1f))
    val verticalPagingState = rememberUpdatedState(verticalPaging)
    val onFlingBeyondEdgeState = rememberUpdatedState(onFlingBeyondEdge)
    var loading by remember(model) { mutableStateOf(true) }
    var failed by remember(model) { mutableStateOf(false) }
    val regionRatio = imageRegionWidthRatio.coerceIn(0.5f, 1f)
    Box(modifier.clipToBounds().pointerInput(model) {
        detectTapGestures(onTap = { tapOffset ->
            // 未缩放时把点按坐标归一化为 fx/fy（0..1），由上层 TapZoneGrid 决定命中动作。
            if (scale.floatValue <= 1f) {
                val width = size.width.coerceAtLeast(1).toFloat()
                val height = size.height.coerceAtLeast(1).toFloat()
                if (verticalTapZonesState.value) {
                    // 垂直翻页（上→下）时纵横轴互换：分区列对应上下条带（上=前，下=后）。
                    onTapRegionState.value(tapOffset.y / height, tapOffset.x / width)
                } else {
                    onTapRegionState.value(tapOffset.x / width, tapOffset.y / height)
                }
            }
        }, onDoubleTap = {
            // 双击缩放改为 300ms 平滑过渡：scale 向目标插值，offset 同步动画归零。
            // 动画期间逐帧同步 arbiter 的内部 zoom，使双击手势循环的回写恒等于当前帧（不互相干扰）。
            val startScale = scale.floatValue
            val targetScale = if (startScale > 1f) 1f else 2f
            val startOffset = offset.value
            zoomAnimJob?.cancel()
            zoomAnimJob = scope.launch {
                val progress = Animatable(0f)
                progress.animateTo(1f, animationSpec = tween(durationMillis = 300)) {
                    scale.floatValue = startScale + (targetScale - startScale) * value
                    offset.value = startOffset * (1f - value)
                    gestureArbiter.resetZoom()
                    if (scale.floatValue > 1f) gestureArbiter.applyZoom(scale.floatValue)
                }
                scale.floatValue = targetScale
                offset.value = Offset.Zero
                if (targetScale > 1f) gestureArbiter.applyZoom(targetScale) else gestureArbiter.resetZoom()
            }
        }, onLongPress = { if (scale.floatValue <= 1f) onLongPressState.value?.invoke() })
    }.pointerInput(model) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            zoomAnimJob?.cancel()
            zoomAnimJob = null
            gestureArbiter.resetZoom()
            if (scale.floatValue > 1f) gestureArbiter.applyZoom(scale.floatValue)
            gestureArbiter.begin()
            var ownsGesture = scale.floatValue > 1f
            // 橡皮筋：raw 记录未钳制的位移，展示值在边界外按 0.5 阻力衰减，松手后回弹。
            var rawX = offset.value.x
            var rawY = offset.value.y
            val velocityTracker = VelocityTracker()
            do {
                val event = awaitPointerEvent()
                val pressedPointers = event.changes.count { it.pressed }
                if (pressedPointers == 1) {
                    event.changes.firstOrNull { it.pressed }?.let { change ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                    }
                }
                if (pressedPointers >= 2) {
                    ownsGesture = true
                    zoomAnimJob?.cancel()
                    zoomAnimJob = null
                    velocityTracker.resetTracking()
                    gestureArbiter.begin(pressedPointers)
                }

                if (ownsGesture) {
                    val oldScale = scale.floatValue
                    val newScale = gestureArbiter.applyZoom(event.calculateZoom()).zoom.coerceIn(1f, 5f)
                    if (newScale <= 1f) {
                        rawX = 0f
                        rawY = 0f
                        scale.floatValue = 1f
                        offset.value = Offset.Zero
                    } else {
                        val scaleRatio = newScale / oldScale
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val centroidFromCenter = event.calculateCentroid(useCurrent = true) - center
                        val pan = event.calculatePan()
                        rawX = rawX * scaleRatio + centroidFromCenter.x * (1f - scaleRatio) + pan.x
                        rawY = rawY * scaleRatio + centroidFromCenter.y * (1f - scaleRatio) + pan.y
                        // 水平钳制基于显示区有效宽度（容器宽 × ratio），垂直仍基于容器高。
                        val maxOffsetX = size.width * regionRatioState.value * (newScale - 1f) / 2f
                        val maxOffsetY = size.height * (newScale - 1f) / 2f
                        scale.floatValue = newScale
                        offset.value = Offset(
                            resistBeyondEdge(rawX, maxOffsetX),
                            resistBeyondEdge(rawY, maxOffsetY),
                        )
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged()) change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
            gestureArbiter.end()
            if (ownsGesture && scale.floatValue > 1f) {
                val maxOffsetX = size.width * regionRatioState.value * (scale.floatValue - 1f) / 2f
                val maxOffsetY = size.height * (scale.floatValue - 1f) / 2f
                // 已到边界仍快速滑动：交由 Pager 层翻页（连续滚动模式回调为空，自动忽略）。
                val velocity = velocityTracker.calculateVelocity()
                val direction = if (verticalPagingState.value) {
                    when {
                        offset.value.y <= -maxOffsetY + 1f && velocity.y < -FLING_BEYOND_EDGE_VELOCITY -> 1
                        offset.value.y >= maxOffsetY - 1f && velocity.y > FLING_BEYOND_EDGE_VELOCITY -> -1
                        else -> 0
                    }
                } else {
                    when {
                        offset.value.x <= -maxOffsetX + 1f && velocity.x < -FLING_BEYOND_EDGE_VELOCITY -> 1
                        offset.value.x >= maxOffsetX - 1f && velocity.x > FLING_BEYOND_EDGE_VELOCITY -> -1
                        else -> 0
                    }
                }
                if (direction != 0) onFlingBeyondEdgeState.value(direction)
                // 松手回弹：越界位移在约 250ms 内弹回钳制值。
                val clampedX = rawX.coerceIn(-maxOffsetX, maxOffsetX)
                val clampedY = rawY.coerceIn(-maxOffsetY, maxOffsetY)
                if (offset.value.x != clampedX || offset.value.y != clampedY) {
                    val startX = offset.value.x
                    val startY = offset.value.y
                    zoomAnimJob?.cancel()
                    zoomAnimJob = scope.launch {
                        val progress = Animatable(0f)
                        progress.animateTo(1f, animationSpec = tween(durationMillis = 250)) {
                            offset.value = Offset(
                                startX + (clampedX - startX) * value,
                                startY + (clampedY - startY) * value,
                            )
                        }
                        offset.value = Offset(clampedX, clampedY)
                    }
                }
            }
        }
    }, contentAlignment = Alignment.Center) {
        // 显示区宽度比例：图片宽度 = 容器宽 × ratio，水平居中，两侧露出阅读背景。
        // 未缩放时裁剪到显示区（防止适应模式溢出到背景区）；缩放后放开，仍受页面边界裁剪。
        Box(
            Modifier
                .fillMaxWidth(regionRatio)
                .fillMaxHeight()
                .graphicsLayer {
                    clip = scale.floatValue <= 1f
                    shape = RectangleShape
                },
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = fitScale,
                onLoading = { loading = true; failed = false },
                onSuccess = { loading = false; failed = false },
                onError = { loading = false; failed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale.floatValue; scaleY = scale.floatValue; translationX = offset.value.x; translationY = offset.value.y },
            )
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Color.White) }
        if (failed) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable {
                        failed = false
                        loading = true
                        attempt += 1
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.BrokenImage,
                    contentDescription = "页面加载失败",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("加载失败，点击重试", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReaderTimeline(
    currentPage: Int,
    total: Int,
    models: List<Any>,
    arcid: String,
    useServerThumbs: Boolean,
    thumbs: TimelineThumbState,
    onJump: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTouch: () -> Unit,
    onLongPress: (Int) -> Unit,
    onOpenToc: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFunctions: () -> Unit,
    onOpenAutoPage: () -> Unit,
    onPreview: (Int?) -> Unit,
    // 阅读亮度（UI 规划 5.1）：底部功能行「亮度」气泡复用设置 Sheet 里的同一状态与回调。
    brightness: Int,
    brightnessExpanded: Boolean,
    onBrightnessExpandedChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessFollowSystem: () -> Unit,
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
        // 页缩略图生成进度条：仅服务器档案入队生成期间显示在时间线顶部。
        if (thumbs.phase == ThumbPhase.GENERATING) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                Text(
                    if (thumbs.total > 0) "页面缩略图生成中 ${thumbs.progress} / ${thumbs.total}" else "页面缩略图生成中…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                if (thumbs.total > 0) {
                    LinearProgressIndicator(
                        progress = { thumbs.progress.toFloat() / thumbs.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
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
                    // 服务器档案且页缩略图就绪时改用专用缩略图 URL；加载失败（含服务端缺页）回退整页原图。
                    var thumbFailed by remember(page) { mutableStateOf(false) }
                    val useThumb = useServerThumbs && thumbs.phase == ThumbPhase.READY && !thumbFailed
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
                                model = if (useThumb) timelineThumbImageModel(arcid, page) else coilPageModel(models[page]),
                                contentDescription = "第 ${page + 1} 页",
                                contentScale = ContentScale.Crop,
                                onError = { thumbFailed = true },
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
                    // 拖动中只更新预览（时间线跟随 + 中央大图），不触发实际跳页；松手后才跳转。
                    dragging = true
                    sliderValue = it
                    previewPage = it.toInt().coerceIn(0, total - 1)
                    onPreview(previewPage)
                    onTouch()
                },
                onValueChangeFinished = {
                    dragging = false
                    onJump(previewPage)
                    onPreview(null)
                    onTouch()
                },
                valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                enabled = total > 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        // 无障碍描述与右侧页码计数同口径（1 起）：拖动中即当前滑块预览页。
                        stateDescription = "第 ${previewPage + 1} 页，共 $total 页"
                    },
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
            /*
             * ☀ 亮度：点击弹出小气泡内嵌亮度滑杆（复用设置 Sheet 的 readerBrightness
             * 状态与 vm.setReaderBrightness，不复制状态）；设置 Sheet 内入口保持可用。
             */
            Box {
                IconButton(onClick = { onBrightnessExpandedChange(!brightnessExpanded) }) {
                    Icon(Icons.Filled.Brightness6, contentDescription = "阅读亮度", tint = Color.White)
                }
                DropdownMenu(
                    expanded = brightnessExpanded,
                    onDismissRequest = { onBrightnessExpandedChange(false) },
                ) {
                    Column(Modifier.width(220.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            if (brightness < 0) "阅读亮度 · 跟随系统" else "阅读亮度 · ${brightness}%",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = if (brightness < 0) 50f else brightness.toFloat(),
                            onValueChange = { onBrightnessChange(it.toInt()); onTouch() },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { onBrightnessFollowSystem(); onTouch() }) {
                            Text("跟随系统")
                        }
                    }
                }
            }
            IconButton(onClick = onOpenFunctions) {
                Icon(Icons.Filled.Settings, contentDescription = "打开阅读设置", tint = Color.White)
            }
        }
    }
}

/** 拖动进度条时中央浮出的大图预览：约 60% 屏宽、圆角、阴影；服务器档案用页缩略图，其余用整页原图，加载失败回退原图。 */
@Composable
private fun SliderPreviewOverlay(
    page: Int,
    models: List<Any>,
    arcid: String,
    useServerThumbs: Boolean,
    thumbsReady: Boolean,
) {
    var thumbFailed by remember(page) { mutableStateOf(false) }
    val useThumb = useServerThumbs && thumbsReady && !thumbFailed
    val configuration = LocalConfiguration.current
    val previewWidth = configuration.screenWidthDp.dp * 0.6f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = if (useThumb) timelineThumbImageModel(arcid, page) else coilPageModel(models[page]),
            contentDescription = "第 ${page + 1} 页预览",
            contentScale = ContentScale.Crop,
            onError = { thumbFailed = true },
            modifier = Modifier
                .width(previewWidth)
                .aspectRatio(0.69f)
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF202020)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "第 ${page + 1} 页",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
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
    var section by remember { mutableStateOf(0) }
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
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("布局", "手势", "图像与性能").forEachIndexed { index, label ->
                    FilterChip(
                        selected = section == index,
                        onClick = { section = index; onTouch() },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (section) {
                0 -> {
                    ReaderSettingsLabel("阅读模式")
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

                    ReaderSettingsLabel("每屏页数")
                    Text(
                        "每屏 ${state.multiPageCount} 页",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.multiPageCount.toFloat(),
                        onValueChange = { vm.setMultiPageCount(it.toInt()); onTouch() },
                        valueRange = 2f..8f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )

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

                    ReaderSettingsLabel("阅读背景")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "black" to "黑色",
                            "dark" to "深灰",
                            "gray" to "灰色",
                            "white" to "白色",
                            "auto" to "自动",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = state.readerBackground == mode,
                                onClick = { vm.setReaderBackground(mode); onTouch() },
                                label = { Text(label) },
                            )
                        }
                    }

                    ReaderSettingsLabel("显示区宽度比例")
                    Text(
                        "${(state.imageRegionWidthRatio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.imageRegionWidthRatio,
                        onValueChange = { vm.setImageRegionWidthRatio(it); onTouch() },
                        valueRange = 0.5f..1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )

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
                1 -> {
                    ReaderSettingsLabel("点击与按键")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.tapZonesEnabled,
                            onClick = { vm.setTapZonesEnabled(!state.tapZonesEnabled); onTouch() },
                            label = { Text("点击区域翻页") },
                        )
                        FilterChip(
                            selected = state.volumeKeysEnabled,
                            onClick = { vm.setVolumeKeysEnabled(!state.volumeKeysEnabled); onTouch() },
                            label = { Text("音量键翻页") },
                        )
                    }

                    ReaderSettingsLabel("翻页动画")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("smooth" to "平滑", "curl" to "卷曲", "fade" to "淡入淡出").forEach { (mode, label) ->
                            FilterChip(
                                selected = state.pageTurnAnimation == mode,
                                onClick = { vm.setPageTurnAnimation(mode); onTouch() },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "点击分区自定义在 设置→阅读",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                2 -> {
                    ReaderSettingsLabel("在线预载页数")
                    Text(
                        "${state.preloadOnlineCount} 页",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.preloadOnlineCount.toFloat(),
                        onValueChange = { vm.setPreloadOnlineCount(it.toInt()); onTouch() },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ReaderSettingsLabel("本地预载页数")
                    Text(
                        "${state.preloadLocalCount} 页",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.preloadLocalCount.toFloat(),
                        onValueChange = { vm.setPreloadLocalCount(it.toInt()); onTouch() },
                        valueRange = 1f..50f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
private fun PageMenuSheet(pageNumber: Int, offline: Boolean, showSetCover: Boolean, onCopyLink: () -> Unit, onSetCover: () -> Unit, onShowInfo: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("第 $pageNumber 页", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCopyLink, enabled = !offline, modifier = Modifier.fillMaxWidth()) { Text("复制本页链接") }
            if (showSetCover) TextButton(onClick = onSetCover, modifier = Modifier.fillMaxWidth()) { Text("设为档案封面") }
            TextButton(onClick = onShowInfo, modifier = Modifier.fillMaxWidth()) { Text("查看信息") }
        }
    }
}
