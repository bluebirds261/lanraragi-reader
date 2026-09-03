package com.lanraragi.reader.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.screens.DownloadScreen
import com.lanraragi.reader.ui.screens.LibraryScreen
import com.lanraragi.reader.ui.screens.SettingsScreen
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

    val offlineIndex by container.offlineCache.index
        .collectAsStateWithLifecycle()

    val downloadCount =
        offlineIndex.items.size

    val historyEntries by container.historyRepository.entries
        .collectAsStateWithLifecycle()

    val context =
        LocalContext.current

    // 继续阅读：优先使用上次阅读的画廊 ID，其次回退到阅读历史
    val openRecent = {
        val recentId = settings?.lastReadArcId
        if (!recentId.isNullOrEmpty()) {
            navController.navigate(Routes.reader(recentId))
        } else {
            val recent = historyEntries.firstOrNull()
            if (recent != null && recent.arcid.isNotEmpty()) {
                navController.navigate(Routes.reader(recent.arcid, 0))
            } else {
                Toast.makeText(context, "暂无正在阅读的漫画", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showExitConfirm by remember {
        mutableStateOf(false)
    }

    val scope =
        rememberCoroutineScope()

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

            when (selectedTab) {

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

                else -> {

                    SettingsScreen(
                        container = container,
                        onBack = null,
                        onOpenDrawer = {
                            navController.navigate(Routes.NAVIGATION)
                        }
                    )
                }
            }
        }

        /*
         * ========================================================
         * Bottom bar
         * ========================================================
         */
        AnimatedVisibility(

            visible = true,

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

                                                        awaitPointerEventScope {

                                                            while (true) {

                                                                val event =
                                                                    awaitPointerEvent(
                                                                        PointerEventPass.Initial
                                                                    )

                                                                event.changes.forEach {
                                                                        change ->

                                                                    if (
                                                                        change.changedToDown()
                                                                    ) {

                                                                        isTabTouched =
                                                                            true

                                                                        isIndicatorMoving =
                                                                            true

                                                                        scope.launch {

                                                                            try {

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

                                                                            } finally {

                                                                                isIndicatorMoving =
                                                                                    false
                                                                            }
                                                                        }
                                                                    }

                                                                    if (
                                                                        change.changedToUpIgnoreConsumed()
                                                                    ) {

                                                                        isTabTouched =
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
                                            selectedTab ==
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

                                                        /*
                                                         * 根据拖动方向和速度产生形变
                                                         *
                                                         * 向右：
                                                         * 横向拉伸
                                                         *
                                                         * 向左：
                                                         * 纵向拉伸
                                                         */
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
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    SingleTabItem(

                        tab =
                            mainTabs[3],

                        selected =
                            false,

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
                        onClick = onClick
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            BadgedBox(

                badge = {

                    if (
                        tab.label == "下载" &&
                        downloadCount > 0 &&
                        isExpanded
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