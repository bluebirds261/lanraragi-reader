package com.lanraragi.reader.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.lanraragi.reader.data.HistoryEntry
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.screens.DownloadScreen
import com.lanraragi.reader.ui.screens.LibraryScreen
import com.lanraragi.reader.ui.screens.MainTabBus
import com.lanraragi.reader.ui.screens.SelectionModeBus
import com.lanraragi.reader.ui.screens.SettingsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class MainTab(
    val label: String,
    val icon: ImageVector,
    val isAction: Boolean = false
)

private val mainTabs = listOf(
    MainTab(
        label = "首页",
        icon = Icons.Filled.Home
    ),
    MainTab(
        label = "下载",
        icon = Icons.Filled.Download
    ),
    MainTab(
        label = "设置",
        icon = Icons.Filled.Menu
    ),
    MainTab(
        label = "正在阅读",
        icon = Icons.Filled.Book,
        isAction = true
    )
)

private data class RecentItem(
    val arcid: String,
    val title: String,
    val page: Int = 0,
    val pagecount: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    container: AppContainer,
    navController: NavController,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
) {

    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(
            initialValue = null
        )

    val activeDownloadCount by container.downloadManager.activeCount
        .collectAsStateWithLifecycle()

    // D2 多选模式下隐藏液态底栏（选择模式激活时由 LibraryScreen 驱动）
    val selectionActive by SelectionModeBus.active
        .collectAsStateWithLifecycle()

    val downloadCount = activeDownloadCount

    val context =
        LocalContext.current

    var showExitConfirm by remember {
        mutableStateOf(false)
    }

    var showRecentList by remember {
        mutableStateOf(false)
    }

    var recentItems by remember {
        mutableStateOf<List<RecentItem>>(emptyList())
    }

    val scope =
        rememberCoroutineScope()

    /*
     * ============================================================
     * 正在阅读：最近阅读列表
     * ============================================================
     */
    val openRecent: () -> Unit = {
        scope.launch {
            val s = container.settingsRepository.settings.first()
            val recents = container.historyRepository.recentDeduped(10).toMutableList()
            val lastId = s.lastReadArcId
            if (lastId.isNotBlank() && recents.none { it.arcid == lastId }) {
                recents.add(0, HistoryEntry(lastId, "", System.currentTimeMillis()))
            }
            when {
                recents.isEmpty() -> {
                    Toast.makeText(context, "暂无正在阅读的漫画", Toast.LENGTH_SHORT).show()
                }
                recents.size == 1 -> {
                    navController.navigate(Routes.reader(recents.first().arcid))
                }
                else -> {
                    recentItems = recents.map { e ->
                        val cached = container.offlineCache.cached(e.arcid)?.metadata
                        RecentItem(
                            arcid = e.arcid,
                            title = e.title.ifBlank { cached?.displayTitle ?: e.arcid },
                            page = cached?.progress ?: 0,
                            pagecount = cached?.pagecount ?: 0,
                        )
                    }
                    showRecentList = true
                }
            }
        }
    }

    /*
     * ============================================================
     * 当前高亮玻璃块的按压状态
     * ============================================================
     */
    val selectedIndicatorInteractionSource =
        remember(selectedTab.coerceIn(0, 2)) {
            MutableInteractionSource()
        }

    val isSelectedIndicatorPressed by
    selectedIndicatorInteractionSource
        .collectIsPressedAsState()

    /*
     * ============================================================
     * Bottom bar expand / collapse
     * ============================================================
     */
    val expandProgress =
        remember {
            Animatable(1f)
        }

    val maxScrollPx =
        with(LocalDensity.current) {
            100.dp.toPx()
        }

    /*
     * ============================================================
     * Main backdrop
     * ============================================================
     */
    val mainBackdrop =
        rememberLayerBackdrop()

    /*
     * ============================================================
     * Nested scroll
     * ============================================================
     */
    val nestedScrollConnection =
        remember {

            object : NestedScrollConnection {

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {

                    val delta =
                        consumed.y

                    if (delta != 0f) {

                        val newProgress =
                            (
                                    expandProgress.value +
                                            delta / maxScrollPx
                                    ).coerceIn(
                                    0f,
                                    1f
                                )

                        if (
                            newProgress !=
                            expandProgress.value
                        ) {

                            scope.launch {

                                expandProgress.snapTo(
                                    newProgress
                                )
                            }
                        }
                    }

                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {

                    val targetValue =
                        if (
                            expandProgress.value > 0.5f
                        ) {
                            1f
                        } else {
                            0f
                        }

                    scope.launch {

                        expandProgress.animateTo(

                            targetValue =
                                targetValue,

                            animationSpec =
                                tween(
                                    durationMillis = 300,
                                    easing =
                                        FastOutSlowInEasing
                                )
                        )
                    }

                    return super.onPostFling(
                        consumed,
                        available
                    )
                }
            }
        }

    /*
     * ============================================================
     * Back handling
     * ============================================================
     */
    BackHandler {

        if (selectedTab != 0) {

            onTabChange(0)

        } else {

            showExitConfirm = true
        }
    }

    /*
     * ============================================================
     * Check-in
     * ============================================================
     */
    LaunchedEffect(Unit) {

        container.checkinRepository.checkin()
    }

    /*
     * ============================================================
     * D5 空态入口：图库空态点「扫描本地文件」→ 切到下载 Tab
     * ============================================================
     */
    LaunchedEffect(Unit) {

        MainTabBus.target.collect { t ->

            if (t != null) {

                onTabChange(t)
                MainTabBus.target.value = null
            }
        }
    }

    /*
     * ============================================================
     * Usage session
     * ============================================================
     */
    val sessionStart =
        remember {
            System.currentTimeMillis()
        }

    DisposableEffect(Unit) {

        onDispose {

            container.usageRepository.addUsage(
                sessionStart
            )
        }
    }

    /*
     * ============================================================
     * Root
     * ============================================================
     */
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(
                    nestedScrollConnection
                )
    ) {

        /*
         * ========================================================
         * Main content
         * ========================================================
         */
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(
                        mainBackdrop
                    )
        ) {

            /*
             * ====================================================
             * 页面横向切换动画
             *
             * 页面顺序：
             *
             * 首页 → 下载 → 设置
             *
             * 向右切换：
             * 新页面从右侧进入
             * 旧页面向左退出
             *
             * 向左切换：
             * 新页面从左侧进入
             * 旧页面向右退出
             *
             * 注意：
             * 这里仅控制页面进入/退出动画。
             * 底部导航栏的动画逻辑完全不参与修改。
             * ====================================================
             */
            AnimatedContent(

                targetState =
                    selectedTab.coerceIn(
                        0,
                        2
                    ),

                transitionSpec = {

                    if (
                        targetState >
                        initialState
                    ) {

                        slideInHorizontally(

                            initialOffsetX = {
                                    width ->
                                width
                            },

                            animationSpec =
                                tween(
                                    durationMillis = 300,
                                    easing =
                                        FastOutSlowInEasing
                                )
                        ) togetherWith

                                slideOutHorizontally(

                                    targetOffsetX = {
                                            width ->
                                        -width
                                    },

                                    animationSpec =
                                        tween(
                                            durationMillis = 300,
                                            easing =
                                                FastOutSlowInEasing
                                        )
                                )

                    } else {

                        slideInHorizontally(

                            initialOffsetX = {
                                    width ->
                                -width
                            },

                            animationSpec =
                                tween(
                                    durationMillis = 300,
                                    easing =
                                        FastOutSlowInEasing
                                )
                        ) togetherWith

                                slideOutHorizontally(

                                    targetOffsetX = {
                                            width ->
                                        width
                                    },

                                    animationSpec =
                                        tween(
                                            durationMillis = 300,
                                            easing =
                                                FastOutSlowInEasing
                                        )
                                )
                    }
                },

                label =
                    "mainPageTransition"

            ) { tab ->

                when (tab) {

                    0 -> {

                        LibraryScreen(
                            container = container,
                            navController = navController,
                            onOpenDrawer = {
                                navController.navigate(
                                    Routes.NAVIGATION
                                )
                            }
                        )
                    }

                    1 -> {

                        DownloadScreen(
                            container = container,
                            navController = navController,
                            onBackToHome = {
                                onTabChange(0)
                            }
                        )
                    }

                    2 -> {

                        SettingsScreen(
                            container = container,
                            onBack = null
                        )
                    }
                }
            }
        }

        /*
         * ========================================================
         * Bottom bar
         * ========================================================
         */
        AnimatedVisibility(

            visible = !selectionActive,

            enter =
                slideInVertically(
                    initialOffsetY = {
                        it
                    }
                ),

            exit =
                slideOutVertically(
                    targetOffsetY = {
                        it
                    }
                ),

            modifier =
                Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .fillMaxWidth()
                    .navigationBarsPadding()
        ) {

            /*
             * ====================================================
             * Colors
             * ====================================================
             */
            val activeColor =
                settings
                    ?.bottomBarActiveColor
                    ?.let {
                        parseHexColor(it)
                    }
                    ?.let {
                        Color(it)
                    }
                    ?: Color(0xFF0084FF)

            val inactiveColor =
                Color(0xFF94A3B8)

            /*
             * ====================================================
             * Expand progress
             * ====================================================
             */
            val progress =
                expandProgress.value

            /*
             * ====================================================
             * Bottom bar horizontal padding
             * ====================================================
             */
            val currentPaddingStart =
                20.dp * progress

            val currentPaddingEnd =
                20.dp

            /*
             * ====================================================
             * Tab opacity
             * ====================================================
             */
            val itemsAlpha =
                (
                        (progress - 0.6f) / 0.4f
                        ).coerceIn(
                        0f,
                        1f
                    )

            /*
             * ====================================================
             * Selected index
             * ====================================================
             */
            val selectedIndex =
                selectedTab.coerceIn(
                    0,
                    2
                )

            /*
             * ====================================================
             * Slider position animation
             * ====================================================
             */
            val indicatorPosition =
                remember {

                    Animatable(
                        selectedIndex.toFloat()
                    )
                }

            val indicatorSelectedIndex =
                indicatorPosition.value
                    .roundToInt()
                    .coerceIn(
                        0,
                        3
                    )

            /*
             * ====================================================
             * 指示块移动状态
             * ====================================================
             */
            var isIndicatorMoving by remember {
                mutableStateOf(false)
            }

            /*
             * ====================================================
             * 指示块触摸状态
             * ====================================================
             */
            var isSliderTouched by remember {
                mutableStateOf(false)
            }

            /*
             * ====================================================
             * 普通 Tab 触摸状态
             * ====================================================
             */
            var isTabTouched by remember {
                mutableStateOf(false)
            }

            /*
             * ====================================================
             * 指示块惯性形变
             * ====================================================
             */
            val horizontalStretch =
                remember {
                    Animatable(0f)
                }

            val verticalStretch =
                remember {
                    Animatable(0f)
                }

            LaunchedEffect(
                selectedIndex
            ) {

                isIndicatorMoving = true

                try {

                    indicatorPosition.animateTo(

                        targetValue =
                            selectedIndex.toFloat(),

                        animationSpec =
                            spring(
                                dampingRatio =
                                    0.78f,

                                stiffness =
                                    500f
                            )
                    )

                } finally {

                    isIndicatorMoving = false
                }
            }

            /*
             * ====================================================
             * 指示块缩放
             * ====================================================
             */
            val indicatorDistance =
                kotlin.math.abs(
                    indicatorPosition.value -
                            selectedIndex.toFloat()
                )

            val shrinkDistance =
                0.35f

            val movementScale =
                if (
                    indicatorDistance >=
                    shrinkDistance
                ) {

                    1.3f

                } else {

                    1f +
                            0.2f *
                            (
                                    indicatorDistance /
                                            shrinkDistance
                                    ).coerceIn(
                                    0f,
                                    1f
                                )
                }

            val sliderScale by
            animateFloatAsState(

                targetValue =
                    if (
                        isSliderTouched ||
                        isTabTouched
                    ) {

                        1.3f

                    } else if (
                        isIndicatorMoving
                    ) {

                        movementScale

                    } else if (
                        isSelectedIndicatorPressed
                    ) {

                        1.3f

                    } else {

                        1f
                    },

                animationSpec =
                    spring(
                        dampingRatio = 0.72f,
                        stiffness = 700f
                    ),

                label = "sliderScale"
            )

            /*
             * ====================================================
             * 整个底栏区域
             * ====================================================
             */
            BoxWithConstraints(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = 16.dp
                        )
            ) {

                val density =
                    LocalDensity.current

                /*
                 * =================================================
                 * 完全展开时的底栏宽度
                 * =================================================
                 */
                val fullBarWidthPx =
                    constraints.maxWidth.toFloat() -
                            with(density) {
                                40.dp.toPx()
                            }

                /*
                 * =================================================
                 * 完全展开时内部宽度
                 * =================================================
                 */
                val fullInnerWidthPx =
                    fullBarWidthPx -
                            with(density) {
                                8.dp.toPx()
                            }

                /*
                 * =================================================
                 * 完全展开时每个 Tab 的宽度
                 * =================================================
                 */
                val fullTabWidthPx =
                    fullInnerWidthPx / 4f

                /*
                 * =================================================
                 * 展开状态
                 * =================================================
                 */
                val expandedReadingCenterX =
                    with(density) {

                        20.dp.toPx() +
                                4.dp.toPx() +
                                fullTabWidthPx * 3f +
                                fullTabWidthPx / 2f
                    }

                /*
                 * =================================================
                 * 收缩状态
                 * =================================================
                 */
                val collapsedReadingCenterX =
                    constraints.maxWidth.toFloat() -
                            with(density) {

                                20.dp.toPx() +
                                        32.dp.toPx()
                            }

                /*
                 * =================================================
                 * 正在阅读中心位置
                 * =================================================
                 */
                val readingCenterX =
                    collapsedReadingCenterX +
                            (
                                    expandedReadingCenterX -
                                            collapsedReadingCenterX
                                    ) * progress

                /*
                 * =================================================
                 * 正在阅读按钮左侧 X
                 * =================================================
                 */
                val readingLeftX =
                    readingCenterX -
                            with(density) {
                                28.dp.toPx()
                            }
                val readingDragTabWidthPx =
                    fullTabWidthPx
                /*
                 * =================================================
                 * Liquid Bottom Bar
                 * =================================================
                 */
                Box(

                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomEnd
                            )
                            .fillMaxWidth()
                            .padding(
                                start =
                                    currentPaddingStart,

                                end =
                                    currentPaddingEnd
                            ),

                    contentAlignment =
                        Alignment.BottomEnd
                ) {

                    BoxWithConstraints(

                        modifier =
                            Modifier
                                .height(64.dp)
                                .then(

                                    if (
                                        progress > 0.01f
                                    ) {

                                        Modifier.fillMaxWidth(
                                            fraction =
                                                progress
                                                    .coerceAtLeast(
                                                        0.18f
                                                    )
                                        )

                                    } else {

                                        Modifier.width(
                                            64.dp
                                        )
                                    }
                                ),

                        contentAlignment =
                            Alignment.CenterStart
                    ) {

                        /*
                         * =================================================
                         * 当前 Liquid Bar 的 Tab width
                         * =================================================
                         */
                        val tabWidthPx =
                            (
                                    constraints.maxWidth
                                        .toFloat() -
                                            with(density) {
                                                8.dp.toPx()
                                            }
                                    ) / 4f

                        /*
                         * =================================================
                         * Slider opacity
                         * =================================================
                         */
                        val sliderAlpha =
                            (
                                    (progress - 0.35f) /
                                            0.65f
                                    ).coerceIn(
                                    0f,
                                    1f
                                )

                        /*
                         * =================================================
                         * Slider idle / active visual state
                         * =================================================
                         */
                        val isSliderActive =
                            isIndicatorMoving ||
                                    isSelectedIndicatorPressed ||
                                    isSliderTouched

                        val sliderVisualAlpha by
                        animateFloatAsState(

                            targetValue =
                                if (isSliderActive) {
                                    1f
                                } else {
                                    0.55f
                                },

                            animationSpec =
                                spring(
                                    dampingRatio =
                                        0.82f,

                                    stiffness =
                                        280f
                                ),

                            label =
                                "sliderVisualAlpha"
                        )

                        val sliderBlur by
                        animateFloatAsState(

                            targetValue =
                                if (isSliderActive) {
                                    0f
                                } else {
                                    18f
                                },

                            animationSpec =
                                spring(
                                    dampingRatio =
                                        0.82f,

                                    stiffness =
                                        280f
                                ),

                            label =
                                "sliderBlur"
                        )

                        /*
                         * =================================================
                         * 滑块下面底栏透明程度
                         * =================================================
                         */
                        val sliderCutoutAlpha by
                        animateFloatAsState(

                            targetValue =
                                if (isSliderActive) {
                                    1f
                                } else {
                                    0f
                                },

                            animationSpec =
                                spring(
                                    dampingRatio =
                                        0.82f,

                                    stiffness =
                                        280f
                                ),

                            label =
                                "sliderCutoutAlpha"
                        )

                        /*
                         * =================================================
                         * Tabs backdrop
                         * =================================================
                         */
                        val tabsBackdrop =
                            rememberLayerBackdrop()

                        /*
                         * =================================================
                         * Liquid Glass surface
                         * =================================================
                         */
                        Row(

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {

                                        compositingStrategy =
                                            CompositingStrategy.Offscreen

                                        shape =
                                            Capsule()

                                        clip =
                                            true
                                    }
                                    .drawBackdrop(

                                        backdrop =
                                            mainBackdrop,

                                        shape = {
                                            Capsule()
                                        },

                                        effects = {

                                            vibrancy()

                                            blur(
                                                4f.dp.toPx()
                                            )

                                            lens(

                                                24f.dp.toPx(),

                                                24f.dp.toPx(),

                                                chromaticAberration =
                                                    true
                                            )
                                        },

                                        highlight = {

                                            Highlight.Ambient.copy(
                                                alpha =
                                                    0.5f
                                            )
                                        },

                                        shadow = {

                                            Shadow(
                                                radius =
                                                    16.dp,

                                                color =
                                                    Color.Black.copy(
                                                        alpha =
                                                            0.25f
                                                    )
                                            )
                                        },

                                        innerShadow = {

                                            InnerShadow(
                                                radius =
                                                    6.dp,

                                                alpha =
                                                    0.25f
                                            )
                                        },

                                        onDrawSurface = {

                                            drawRect(
                                                Color.White.copy(
                                                    alpha =
                                                        0.02f
                                                )
                                            )
                                        }
                                    )
                                    .drawWithContent {

                                        drawContent()

                                        if (
                                            sliderCutoutAlpha >
                                            0.001f
                                        ) {

                                            val cutoutLeft =
                                                4.dp.toPx() +
                                                        indicatorPosition
                                                            .value *
                                                        tabWidthPx

                                            val cutoutWidth =
                                                tabWidthPx

                                            val cutoutHeight =
                                                56.dp.toPx()

                                            val cutoutTop =
                                                (
                                                        size.height -
                                                                cutoutHeight
                                                        ) / 2f

                                            drawIntoCanvas { canvas ->

                                                val paint =
                                                    androidx.compose.ui.graphics.Paint()
                                                        .apply {

                                                            blendMode =
                                                                BlendMode.DstOut

                                                            alpha =
                                                                sliderCutoutAlpha
                                                        }

                                                val path =
                                                    androidx.compose.ui.graphics.Path()

                                                path.addRoundRect(
                                                    androidx.compose.ui.geometry
                                                        .RoundRect(

                                                            left =
                                                                cutoutLeft +
                                                                        (
                                                                                cutoutWidth *
                                                                                        (
                                                                                                1f -
                                                                                                        sliderScale
                                                                                                )
                                                                                ) / 2f,

                                                            top =
                                                                cutoutTop +
                                                                        (
                                                                                cutoutHeight *
                                                                                        (
                                                                                                1f -
                                                                                                        sliderScale
                                                                                                )
                                                                                ) / 2f,

                                                            right =
                                                                cutoutLeft +
                                                                        cutoutWidth -
                                                                        (
                                                                                cutoutWidth *
                                                                                        (
                                                                                                1f -
                                                                                                        sliderScale
                                                                                                )
                                                                                ) / 2f,

                                                            bottom =
                                                                cutoutTop +
                                                                        cutoutHeight -
                                                                        (
                                                                                cutoutHeight *
                                                                                        (
                                                                                                1f -
                                                                                                        sliderScale
                                                                                                )
                                                                                ) / 2f,

                                                            radiusX =
                                                                cutoutHeight *
                                                                        sliderScale /
                                                                        2f,

                                                            radiusY =
                                                                cutoutHeight *
                                                                        sliderScale /
                                                                        2f
                                                        )
                                                )

                                                canvas.drawPath(
                                                    path,
                                                    paint
                                                )
                                            }
                                        }
                                    }
                                    .padding(4.dp),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            repeat(4) {

                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                )
                            }
                        }

                        /*
                         * =================================================
                         * Hidden backdrop capture layer
                         * =================================================
                         */
                        Row(

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .alpha(0f)
                                    .layerBackdrop(
                                        tabsBackdrop
                                    )
                                    .padding(4.dp),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            repeat(4) {

                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                )
                            }
                        }

                        /*
                         * =================================================
                         * Slider 视觉层
                         * =================================================
                         */
                        Box(

                            modifier =
                                Modifier
                                    .align(
                                        Alignment.CenterStart
                                    )
                                    .padding(
                                        horizontal =
                                            4.dp
                                    )
                                    .graphicsLayer {

                                        translationX =
                                            indicatorPosition
                                                .value *
                                                    tabWidthPx

                                        scaleX =
                                            sliderScale *
                                                    (
                                                            1f +
                                                                    horizontalStretch.value -
                                                                    verticalStretch.value *
                                                                    0.20f
                                                            )

                                        scaleY =
                                            sliderScale *
                                                    (
                                                            1f +
                                                                    verticalStretch.value -
                                                                    horizontalStretch.value *
                                                                    0.20f
                                                            )
                                    }
                                    .height(56.dp)
                                    .fillMaxWidth(
                                        1f / 4f
                                    )
                                    .alpha(
                                        sliderAlpha *
                                                sliderVisualAlpha
                                    )
                                    .clip(
                                        Capsule()
                                    )
                                    .drawBackdrop(

                                        backdrop =
                                            rememberCombinedBackdrop(
                                                mainBackdrop,
                                                tabsBackdrop
                                            ),

                                        shape = {
                                            Capsule()
                                        },

                                        effects = {

                                            lens(

                                                10f.dp.toPx(),

                                                14f.dp.toPx(),

                                                chromaticAberration =
                                                    true
                                            )

                                            blur(
                                                sliderBlur.dp.toPx()
                                            )
                                        },

                                        highlight = {

                                            Highlight.Default.copy(
                                                alpha =
                                                    0.7f *
                                                            sliderAlpha *
                                                            sliderVisualAlpha
                                            )
                                        },

                                        shadow = {

                                            Shadow(
                                                radius =
                                                    4.dp,

                                                color =
                                                    Color.Black.copy(
                                                        alpha =
                                                            0.08f *
                                                                    sliderAlpha *
                                                                    sliderVisualAlpha
                                                    )
                                            )
                                        },

                                        innerShadow = {

                                            InnerShadow(
                                                radius =
                                                    6.dp,

                                                alpha =
                                                    0.16f *
                                                            sliderAlpha *
                                                            sliderVisualAlpha
                                            )
                                        },

                                        onDrawSurface = {

                                            drawRect(
                                                activeColor.copy(
                                                    alpha =
                                                        0.20f *
                                                                sliderAlpha *
                                                                sliderVisualAlpha
                                                )
                                            )
                                        }
                                    )
                        )

                        /*
                         * =================================================
                         * 普通三个 Tab
                         *
                         * 这里不使用 detectHorizontalDragGestures。
                         *
                         * Down 时立即移动滑块，
                         * 但只有超过 touch-slop 后才消费事件。
                         *
                         * 因此：
                         *
                         * 按下
                         *     ↓
                         * 滑块立即移动
                         *
                         * 只是点击
                         *     ↓
                         * 不消费事件
                         *     ↓
                         * SingleTabItem.clickable 正常响应
                         *
                         * 按住并横向拖动
                         *     ↓
                         * 超过 touch-slop
                         *     ↓
                         * 开始跟手拖动
                         * =================================================
                         */
                        Row(

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            mainTabs.forEachIndexed {
                                    index,
                                    tab ->

                                Box(

                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .then(

                                                if (!tab.isAction) {

                                                    Modifier.pointerInput(
                                                        index
                                                    ) {

                                                        var dragPosition =
                                                            index.toFloat()

                                                        var lastX =
                                                            0f

                                                        var totalDrag =
                                                            0f

                                                        var isDragging =
                                                            false

                                                        awaitPointerEventScope {

                                                            while (true) {

                                                                val downEvent =
                                                                    awaitPointerEvent(
                                                                        PointerEventPass.Initial
                                                                    )

                                                                val downChange =
                                                                    downEvent
                                                                        .changes
                                                                        .firstOrNull()
                                                                        ?: continue

                                                                if (
                                                                    downChange.changedToDown()
                                                                ) {

                                                                    dragPosition =
                                                                        index.toFloat()

                                                                    lastX =
                                                                        downChange
                                                                            .position
                                                                            .x

                                                                    totalDrag =
                                                                        0f

                                                                    isDragging =
                                                                        false

                                                                    isTabTouched =
                                                                        true

                                                                    isIndicatorMoving =
                                                                        true

                                                                    scope.launch {

                                                                        indicatorPosition.stop()

                                                                        launch {

                                                                            indicatorPosition.animateTo(

                                                                                targetValue =
                                                                                    index.toFloat(),

                                                                                animationSpec =
                                                                                    spring(
                                                                                        dampingRatio =
                                                                                            0.78f,

                                                                                        stiffness =
                                                                                            500f
                                                                                    )
                                                                            )
                                                                        }
                                                                    }

                                                                    while (true) {

                                                                        val event =
                                                                            awaitPointerEvent(
                                                                                PointerEventPass.Initial
                                                                            )

                                                                        val change =
                                                                            event
                                                                                .changes
                                                                                .firstOrNull()
                                                                                ?: break

                                                                        if (
                                                                            change.changedToUpIgnoreConsumed()
                                                                        ) {

                                                                            isTabTouched =
                                                                                false

                                                                            if (
                                                                                !isDragging
                                                                            ) {

                                                                                isIndicatorMoving =
                                                                                    false
                                                                            }

                                                                            break
                                                                        }

                                                                        if (
                                                                            !change.pressed
                                                                        ) {

                                                                            break
                                                                        }

                                                                        val currentX =
                                                                            change
                                                                                .position
                                                                                .x

                                                                        val deltaX =
                                                                            currentX -
                                                                                    lastX

                                                                        lastX =
                                                                            currentX

                                                                        if (
                                                                            deltaX == 0f
                                                                        ) {

                                                                            continue
                                                                        }

                                                                        totalDrag +=
                                                                            kotlin.math.abs(
                                                                                deltaX
                                                                            )

                                                                        val touchSlop =
                                                                            viewConfiguration.touchSlop

                                                                        if (
                                                                            !isDragging &&
                                                                            totalDrag >
                                                                            touchSlop
                                                                        ) {

                                                                            isDragging =
                                                                                true

                                                                            isIndicatorMoving =
                                                                                true

                                                                            dragPosition =
                                                                                indicatorPosition.value

                                                                            scope.launch {
                                                                                indicatorPosition.stop()
                                                                            }
                                                                        }

                                                                        if (
                                                                            isDragging &&
                                                                            tabWidthPx >
                                                                            0f
                                                                        ) {

                                                                            change.consume()

                                                                            dragPosition =
                                                                                (
                                                                                        dragPosition +
                                                                                                deltaX /
                                                                                                tabWidthPx
                                                                                        ).coerceIn(
                                                                                        0f,
                                                                                        3f
                                                                                    )

                                                                            val stretch =
                                                                                (
                                                                                        kotlin.math.abs(
                                                                                            deltaX
                                                                                        ) /
                                                                                                tabWidthPx *
                                                                                                2.5f
                                                                                        ).coerceIn(
                                                                                        0f,
                                                                                        0.28f
                                                                                    )

                                                                            scope.launch {

                                                                                indicatorPosition.snapTo(
                                                                                    dragPosition
                                                                                )

                                                                                if (
                                                                                    deltaX >
                                                                                    0f
                                                                                ) {

                                                                                    horizontalStretch.snapTo(
                                                                                        stretch
                                                                                    )

                                                                                    verticalStretch.snapTo(
                                                                                        0f
                                                                                    )

                                                                                } else if (
                                                                                    deltaX <
                                                                                    0f
                                                                                ) {

                                                                                    verticalStretch.snapTo(
                                                                                        stretch
                                                                                    )

                                                                                    horizontalStretch.snapTo(
                                                                                        0f
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }

                                                                    if (
                                                                        isDragging
                                                                    ) {

                                                                        val targetIndex =
                                                                            dragPosition
                                                                                .roundToInt()
                                                                                .coerceIn(
                                                                                    0,
                                                                                    3
                                                                                )

                                                                        scope.launch {

                                                                            indicatorPosition.animateTo(

                                                                                targetValue =
                                                                                    targetIndex
                                                                                        .toFloat(),

                                                                                animationSpec =
                                                                                    spring(
                                                                                        dampingRatio =
                                                                                            0.78f,

                                                                                        stiffness =
                                                                                            500f
                                                                                    )
                                                                            )

                                                                            launch {

                                                                                horizontalStretch.animateTo(

                                                                                    targetValue =
                                                                                        0f,

                                                                                    animationSpec =
                                                                                        spring(
                                                                                            dampingRatio =
                                                                                                0.55f,

                                                                                            stiffness =
                                                                                                700f
                                                                                        )
                                                                                )
                                                                            }

                                                                            launch {

                                                                                verticalStretch.animateTo(

                                                                                    targetValue =
                                                                                        0f,

                                                                                    animationSpec =
                                                                                        spring(
                                                                                            dampingRatio =
                                                                                                0.55f,

                                                                                            stiffness =
                                                                                                700f
                                                                                        )
                                                                                )
                                                                            }

                                                                            isIndicatorMoving =
                                                                                false

                                                                            isTabTouched =
                                                                                false

                                                                            if (
                                                                                targetIndex ==
                                                                                3
                                                                            ) {

                                                                                openRecent()

                                                                            } else if (
                                                                                targetIndex !=
                                                                                selectedIndex
                                                                            ) {

                                                                                onTabChange(
                                                                                    targetIndex
                                                                                )
                                                                            }
                                                                        }

                                                                    } else {

                                                                        isTabTouched =
                                                                            false

                                                                        isIndicatorMoving =
                                                                            false
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                } else {

                                                    Modifier
                                                }
                                            ),

                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    if (
                                        !tab.isAction
                                    ) {

                                        val selected =
                                            indicatorSelectedIndex ==
                                                    index

                                        SingleTabItem(

                                            tab =
                                                tab,

                                            selected =
                                                selected,

                                            isExpanded =
                                                progress > 0.5f,

                                            activeColor =
                                                activeColor,

                                            inactiveColor =
                                                inactiveColor,

                                            downloadCount =
                                                downloadCount,

                                            interactionSource =
                                                if (selected) {

                                                    selectedIndicatorInteractionSource

                                                } else {

                                                    null
                                                },

                                            modifier =
                                                Modifier
                                                    .size(56.dp)
                                                    .alpha(
                                                        itemsAlpha
                                                    ),

                                            onClick = {

                                                if (
                                                    progress > 0.8f
                                                ) {

                                                    onTabChange(
                                                        index
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        /*
                         * =================================================
                         * Slider 透明拖拽层
                         * =================================================
                         */
                        Box(

                            modifier =
                                Modifier
                                    .align(
                                        Alignment.CenterStart
                                    )
                                    .padding(
                                        horizontal =
                                            4.dp
                                    )
                                    .graphicsLayer {

                                        translationX =
                                            indicatorPosition
                                                .value *
                                                    tabWidthPx
                                    }
                                    .height(56.dp)
                                    .fillMaxWidth(
                                        1f / 4f
                                    )
                                    .clip(
                                        Capsule()
                                    )
                                    .clickable(

                                        indication = null,

                                        interactionSource =
                                            selectedIndicatorInteractionSource,

                                        onClick = {

                                            if (
                                                progress > 0.8f
                                            ) {

                                                val targetIndex =
                                                    indicatorPosition
                                                        .value
                                                        .roundToInt()
                                                        .coerceIn(
                                                            0,
                                                            3
                                                        )

                                                if (
                                                    targetIndex == 3
                                                ) {

                                                    openRecent()

                                                } else {

                                                    onTabChange(
                                                        targetIndex
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    .draggable(

                                        orientation =
                                            Orientation.Horizontal,

                                        state =
                                            rememberDraggableState {

                                                    delta ->

                                                if (
                                                    tabWidthPx > 0f
                                                ) {

                                                    val deltaInTabs =
                                                        delta /
                                                                tabWidthPx

                                                    scope.launch {

                                                        indicatorPosition.snapTo(

                                                            (
                                                                    indicatorPosition.value +
                                                                            deltaInTabs
                                                                    ).coerceIn(
                                                                    0f,
                                                                    3f
                                                                )
                                                        )

                                                        val stretch =
                                                            (
                                                                    kotlin.math.abs(
                                                                        delta
                                                                    ) /
                                                                            tabWidthPx *
                                                                            2.5f
                                                                    ).coerceIn(
                                                                    0f,
                                                                    0.28f
                                                                )

                                                        if (
                                                            delta > 0f
                                                        ) {

                                                            horizontalStretch.snapTo(
                                                                stretch
                                                            )

                                                            verticalStretch.snapTo(
                                                                0f
                                                            )

                                                        } else if (
                                                            delta < 0f
                                                        ) {

                                                            verticalStretch.snapTo(
                                                                stretch
                                                            )

                                                            horizontalStretch.snapTo(
                                                                0f
                                                            )
                                                        }
                                                    }
                                                }
                                            },

                                        onDragStarted = {

                                            isIndicatorMoving =
                                                true

                                            isSliderTouched =
                                                true

                                            scope.launch {

                                                indicatorPosition.stop()

                                                horizontalStretch.snapTo(
                                                    0f
                                                )

                                                verticalStretch.snapTo(
                                                    0f
                                                )
                                            }
                                        },

                                        onDragStopped = {

                                            val targetIndex =
                                                indicatorPosition
                                                    .value
                                                    .roundToInt()
                                                    .coerceIn(
                                                        0,
                                                        3
                                                    )

                                            scope.launch {

                                                indicatorPosition.animateTo(

                                                    targetValue =
                                                        targetIndex
                                                            .toFloat(),

                                                    animationSpec =
                                                        spring(
                                                            dampingRatio =
                                                                0.78f,

                                                            stiffness =
                                                                500f
                                                        )
                                                )

                                                launch {

                                                    horizontalStretch.animateTo(

                                                        targetValue =
                                                            0f,

                                                        animationSpec =
                                                            spring(
                                                                dampingRatio =
                                                                    0.55f,

                                                                stiffness =
                                                                    700f
                                                            )
                                                    )
                                                }

                                                launch {

                                                    verticalStretch.animateTo(

                                                        targetValue =
                                                            0f,

                                                        animationSpec =
                                                            spring(
                                                                dampingRatio =
                                                                    0.55f,

                                                                stiffness =
                                                                    700f
                                                            )
                                                    )
                                                }

                                                isIndicatorMoving =
                                                    false

                                                isSliderTouched =
                                                    false

                                                if (
                                                    targetIndex == 3
                                                ) {

                                                    openRecent()

                                                } else if (
                                                    targetIndex !=
                                                    selectedIndex
                                                ) {

                                                    onTabChange(
                                                        targetIndex
                                                    )
                                                }
                                            }
                                        }
                                    )
                        )
                    }
                }

                /*
                 * =====================================================
                 * 正在阅读
                 *
                 * 这里增加与普通 Tab 相同的 pointerInput。
                 *
                 * 目的：
                 *
                 * 手指按下 tab3
                 *       ↓
                 * 滑块保持/移动到 3
                 *       ↓
                 * 超过 touch-slop
                 *       ↓
                 * tab3 开始继续接管横向拖动
                 *       ↓
                 * indicatorPosition 可以从 3f
                 * 向 2f、1f、0f 跟手移动
                 *
                 * 未超过 touch-slop：
                 *       ↓
                 * 不消费事件
                 *       ↓
                 * SingleTabItem.clickable
                 * 仍然保持原来的点击行为
                 * =====================================================
                 */
                Box(

                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomStart
                            )
                            .graphicsLayer {

                                translationX =
                                    readingLeftX

                                translationY =
                                    0f
                            }
                            .size(
                                width = 56.dp,
                                height = 64.dp
                            )
                            .pointerInput(Unit) {

                                var dragPosition =
                                    3f

                                var lastX =
                                    0f

                                var totalDrag =
                                    0f

                                var isDragging =
                                    false

                                awaitPointerEventScope {

                                    while (true) {

                                        val downEvent =
                                            awaitPointerEvent(
                                                PointerEventPass.Initial
                                            )

                                        val downChange =
                                            downEvent
                                                .changes
                                                .firstOrNull()
                                                ?: continue

                                        if (
                                            downChange.changedToDown()
                                        ) {

                                            /*
                                             * =============================================
                                             * tab3 按下瞬间：
                                             *
                                             * 直接把滑块定位到 3。
                                             * =============================================
                                             */
                                            dragPosition =
                                                3f

                                            lastX =
                                                downChange
                                                    .position
                                                    .x

                                            totalDrag =
                                                0f

                                            isDragging =
                                                false

                                            isTabTouched =
                                                true

                                            isIndicatorMoving =
                                                true

                                            scope.launch {

                                                indicatorPosition.stop()

                                                indicatorPosition.animateTo(

                                                    targetValue =
                                                        3f,

                                                    animationSpec =
                                                        spring(
                                                            dampingRatio =
                                                                0.78f,

                                                            stiffness =
                                                                500f
                                                        )
                                                )
                                            }

                                            /*
                                             * =============================================
                                             * 继续监听 tab3 上的手指。
                                             * =============================================
                                             */
                                            while (true) {

                                                val event =
                                                    awaitPointerEvent(
                                                        PointerEventPass.Initial
                                                    )

                                                val change =
                                                    event
                                                        .changes
                                                        .firstOrNull()
                                                        ?: break

                                                /*
                                                 * =========================================
                                                 * 手指抬起
                                                 * =========================================
                                                 */
                                                if (
                                                    change.changedToUpIgnoreConsumed()
                                                ) {

                                                    isTabTouched =
                                                        false

                                                    /*
                                                     * 如果只是点击：
                                                     * 不进行任何导航处理。
                                                     *
                                                     * SingleTabItem.clickable
                                                     * 会继续处理点击。
                                                     */
                                                    if (
                                                        !isDragging
                                                    ) {

                                                        isIndicatorMoving =
                                                            false
                                                    }

                                                    break
                                                }

                                                if (
                                                    !change.pressed
                                                ) {

                                                    break
                                                }

                                                val currentX =
                                                    change
                                                        .position
                                                        .x

                                                val deltaX =
                                                    currentX -
                                                            lastX

                                                lastX =
                                                    currentX

                                                if (
                                                    deltaX == 0f
                                                ) {

                                                    continue
                                                }

                                                totalDrag +=
                                                    kotlin.math.abs(
                                                        deltaX
                                                    )

                                                /*
                                                 * =========================================
                                                 * touch-slop
                                                 *
                                                 * 未超过阈值：
                                                 * 不消费事件。
                                                 *
                                                 * 超过阈值：
                                                 * 正式开始拖动。
                                                 * =========================================
                                                 */
                                                val touchSlop =
                                                    viewConfiguration.touchSlop

                                                if (
                                                    !isDragging &&
                                                    totalDrag >
                                                    touchSlop
                                                ) {

                                                    isDragging =
                                                        true

                                                    isIndicatorMoving =
                                                        true

                                                    dragPosition =
                                                        indicatorPosition.value

                                                    scope.launch {
                                                        indicatorPosition.stop()
                                                    }
                                                }

                                                /*
                                                 * =========================================
                                                 * 真正跟手拖动
                                                 *
                                                 * 关键：
                                                 *
                                                 * coerceIn(0f, 3f)
                                                 *
                                                 * 因此从 tab3 向左可以：
                                                 *
                                                 * 3.0 → 2.9 → 2.8 → ...
                                                 * → 2.0 → 1.0 → 0.0
                                                 * =========================================
                                                 */
                                                if (
                                                    isDragging &&
                                                    readingDragTabWidthPx > 0f
                                                ) {

                                                    change.consume()

                                                    dragPosition =
                                                        (
                                                                dragPosition +
                                                                        deltaX /
                                                                        readingDragTabWidthPx
                                                                ).coerceIn(
                                                                0f,
                                                                3f
                                                            )

                                                    val stretch =
                                                        (
                                                                kotlin.math.abs(deltaX) /
                                                                        (readingDragTabWidthPx * 2.5f)
                                                                ).coerceIn(
                                                                0f,
                                                                0.28f
                                                            )

                                                    scope.launch {

                                                        indicatorPosition.snapTo(
                                                            dragPosition
                                                        )

                                                        if (
                                                            deltaX >
                                                            0f
                                                        ) {

                                                            horizontalStretch.snapTo(
                                                                stretch
                                                            )

                                                            verticalStretch.snapTo(
                                                                0f
                                                            )

                                                        } else if (
                                                            deltaX <
                                                            0f
                                                        ) {

                                                            verticalStretch.snapTo(
                                                                stretch
                                                            )

                                                            horizontalStretch.snapTo(
                                                                0f
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            /*
                                             * =============================================
                                             * 真正拖动后：
                                             * 松手吸附到最近 Tab。
                                             *
                                             * 注意：
                                             * 这里仍然保留原来的 tab3 行为。
                                             * =============================================
                                             */
                                            if (
                                                isDragging
                                            ) {

                                                val targetIndex =
                                                    dragPosition
                                                        .roundToInt()
                                                        .coerceIn(
                                                            0,
                                                            3
                                                        )

                                                scope.launch {

                                                    indicatorPosition.animateTo(

                                                        targetValue =
                                                            targetIndex
                                                                .toFloat(),

                                                        animationSpec =
                                                            spring(
                                                                dampingRatio =
                                                                    0.78f,

                                                                stiffness =
                                                                    500f
                                                            )
                                                    )

                                                    launch {

                                                        horizontalStretch.animateTo(

                                                            targetValue =
                                                                0f,

                                                            animationSpec =
                                                                spring(
                                                                    dampingRatio =
                                                                        0.55f,

                                                                    stiffness =
                                                                        700f
                                                                )
                                                        )
                                                    }

                                                    launch {

                                                        verticalStretch.animateTo(

                                                            targetValue =
                                                                0f,

                                                            animationSpec =
                                                                spring(
                                                                    dampingRatio =
                                                                        0.55f,

                                                                    stiffness =
                                                                        700f
                                                                )
                                                        )
                                                    }

                                                    isIndicatorMoving =
                                                        false

                                                    isTabTouched =
                                                        false

                                                    /*
                                                     * =========================================
                                                     * 最终落在 tab3：
                                                     *
                                                     * 进入正在阅读。
                                                     * =========================================
                                                     */
                                                    if (
                                                        targetIndex ==
                                                        3
                                                    ) {

                                                        openRecent()

                                                    } else if (
                                                        targetIndex !=
                                                        selectedIndex
                                                    ) {

                                                        onTabChange(
                                                            targetIndex
                                                        )
                                                    }
                                                }

                                            } else {

                                                /*
                                                 * =============================================
                                                 * 普通点击：
                                                 *
                                                 * 不处理导航。
                                                 *
                                                 * SingleTabItem.clickable
                                                 * 保持原来的点击行为。
                                                 * =============================================
                                                 */
                                                isTabTouched =
                                                    false

                                                isIndicatorMoving =
                                                    false
                                            }
                                        }
                                    }
                                }
                            },

                    contentAlignment =
                        Alignment.Center
                ) {

                    SingleTabItem(

                        tab =
                            mainTabs[3],

                        selected =
                            indicatorSelectedIndex == 3,

                        isExpanded =
                            progress > 0.5f,

                        activeColor =
                            activeColor,

                        inactiveColor =
                            inactiveColor,

                        downloadCount =
                            downloadCount,

                        interactionSource =
                            null,

                        modifier =
                            Modifier
                                .size(56.dp),

                        onClick = {

                            openRecent()
                        }
                    )
                }
            }
        }

        /*
         * ============================================================
         * Exit dialog
         * ============================================================
         */
        if (showExitConfirm) {

            AlertDialog(

                onDismissRequest = {

                    showExitConfirm =
                        false
                },

                title = {

                    Text(

                        text =
                            "确认退出？",

                        style =
                            MaterialTheme.typography
                                .headlineSmall,

                        textAlign =
                            TextAlign.Start,

                        modifier =
                            Modifier.fillMaxWidth()
                    )
                },

                text = {

                    Text(

                        text =
                            "确定要退出吗？",

                        style =
                            MaterialTheme.typography
                                .bodyMedium,

                        textAlign =
                            TextAlign.Start,

                        modifier =
                            Modifier.fillMaxWidth()
                    )
                },

                confirmButton = {

                    Column(
                        horizontalAlignment =
                            Alignment.End
                    ) {

                        TextButton(

                            onClick = {

                                showExitConfirm =
                                    false
                            }
                        ) {

                            Text(
                                "手滑了"
                            )
                        }

                        TextButton(

                            onClick = {

                                (
                                        context as? Activity
                                        )?.finish()
                            }
                        ) {

                            Text(

                                text =
                                    "确认退出",

                                color =
                                    MaterialTheme.colorScheme
                                        .error
                            )
                        }
                    }
                }
            )
        }

        LaunchedEffect(showRecentList) {
            if (!showRecentList) return@LaunchedEffect
            val arcs = recentItems.map { it.arcid }
            for (arcid in arcs) {
                val meta = try {
                    container.repository.getMetadata(arcid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (meta != null) {
                    recentItems = recentItems.map {
                        if (it.arcid == arcid) {
                            RecentItem(
                                arcid = it.arcid,
                                title = meta.displayTitle.ifBlank { it.title },
                                page = meta.progress,
                                pagecount = meta.pagecount,
                            )
                        } else {
                            it
                        }
                    }
                }
            }
        }

        if (showRecentList) {
            ModalBottomSheet(
                onDismissRequest = { showRecentList = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                ) {
                    Text(
                        text = "正在阅读",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (recentItems.isEmpty()) {
                        Text(
                            text = "暂无正在阅读的漫画",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recentItems, key = { it.arcid }) { item ->
                                RecentCard(
                                    item = item,
                                    onOpen = {
                                        showRecentList = false
                                        navController.navigate(Routes.reader(item.arcid))
                                    },
                                    onRemove = {
                                        recentItems = recentItems.filter { it.arcid != item.arcid }
                                        scope.launch {
                                            container.historyRepository.remove(item.arcid)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleTabItem(

    tab: MainTab,

    selected: Boolean,

    isExpanded: Boolean,

    activeColor: Color,

    inactiveColor: Color,

    downloadCount: Int,

    interactionSource: MutableInteractionSource? = null,

    modifier: Modifier = Modifier,

    onClick: () -> Unit

) {

    val actualInteractionSource =
        interactionSource
            ?: remember {
                MutableInteractionSource()
            }

    Box(

        modifier =
            modifier,

        contentAlignment =
            Alignment.Center
    ) {

        Box(

            modifier =
                Modifier
                    .size(56.dp)
                    .clip(
                        CircleShape
                    )
                    .clickable(

                        indication = null,

                        interactionSource =
                            actualInteractionSource,

                        onClick =
                            onClick
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            BadgedBox(

                badge = {

                    if (
                        tab.label == "下载" &&
                        downloadCount > 0
                    ) {

                        Badge(

                            containerColor =
                                activeColor,

                            contentColor =
                                Color.White
                        ) {

                            Text(

                                text =
                                    if (
                                        downloadCount > 99
                                    ) {

                                        "99+"

                                    } else {

                                        downloadCount
                                            .toString()
                                    },

                                fontSize =
                                    9.sp
                            )
                        }
                    }
                }
            ) {

                Icon(

                    imageVector =
                        tab.icon,

                    contentDescription =
                        tab.label,

                    modifier =
                        Modifier
                            .size(26.dp),

                    tint =
                        if (selected) {

                            activeColor

                        } else {

                            inactiveColor
                        }
                )
            }
        }
    }
}

@Composable
private fun RecentCard(
    item: RecentItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            LoadingImage(
                model = ApiClient.thumbnailUrl(item.arcid),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title.ifBlank { item.arcid },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.pagecount > 0 && item.page > 0) {
            Text(
                text = "第 ${item.page}/${item.pagecount} 页",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}
