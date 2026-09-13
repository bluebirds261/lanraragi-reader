package com.lanraragi.reader.ui.components.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 大号液态玻璃 FAB：无文字为圆形（直径 67.dp，沿用原 FloatingActionBar
 * 的尺寸与 28.dp 图标），有文字为胶囊（高 56.dp，图标 24.dp + 文字）。
 * 宽高可由 [modifier] 进一步收紧（组件内部的固定尺寸会服从外层传入的
 * 固定尺寸约束，如 DetailScreen 阅读按钮的 `Modifier.height(64.dp)`）。
 *
 * 视觉常量取自三处参考实现：
 * vibrancy + blur(8.dp) + lens(10.dp, 18.dp, 色散)
 * （LibraryScreen 搜索胶囊）；
 * Highlight.Ambient(alpha = 0.5f) + Shadow(16.dp, 25% 黑) +
 * InnerShadow(6.dp, alpha = 0.25f) + 表面 2% 白（MainScreen 液态底栏）。
 * 按下时挤压缩放（同 SafeGlassButton 反馈），禁用时整体 50% 透明。
 *
 * [pressDynamics] 为按下时的玻璃动力学开关，缺省开启（即参考实现
 * DetailScreen.LiquidGlassVisual / 同目录 LiquidGlassButton 的完整手感）：
 * - 玻璃体按按下点做视差偏移（±3.dp 乘以按下点相对组件中心的归一化位置）；
 * - blur 收窄 25%：`blurRadius * (1 - 0.25 * press)`；
 * - 折射增强 50%：`refractionAmount * (1 + 0.5 * press)`；
 * - 背景采样额外做 `translate(-nx * 18, -ny * 14)` 与
 *   `scale(1 - 0.13 * press, 1 + 0.075 * press)`（以按下点为支点）的扭曲。
 * 传 false 时上述按下调制全部关闭，只保留原有的挤压缩放/透明度反馈；
 * 无论开关取值，静止（未按下）时所有调制项都退化为恒等，外观不变。
 * 动力学与挤压缩放共用同一个 interactionSource 与同一条按压弹簧，
 * 因此不会与 `onLongClick`/`combinedClickable` 路径冲突。
 *
 * [activeColor] 为激活态强调色：非 null 时在玻璃表面叠加 20% alpha
 * 着色层，且在未指定 [tint] 时同时作为图标与文字颜色。
 * [tint] 覆盖图标与文字颜色（例如悬浮按钮的自定义颜色），
 * 优先级高于 [activeColor]；两者都为空时使用 onSurface。
 *
 * [iconContentDescription] 为图标的无障碍描述（中文），缺省使用 [label]。
 * 外边距/对齐等由 [modifier] 决定。
 *
 * [onLongClick] 为可选长按动作：传入时手势从 `clickable` 换成
 * `combinedClickable`，且复用同一个 interactionSource，按压动画不受影响
 * （DetailScreen 阅读按钮的双态菜单即依赖它）。
 *
 * 玻璃效果常量全部可覆盖，缺省值即上面的「底栏风格」取值，
 * 因此既有调用方行为不变；需要更轻的处理时（如 DetailScreen 阅读按钮）
 * 可传入自己的 [blurRadius] / [refractionAmount] / [highlight] / [shadow] 等：
 * - [vibrancyEnabled] 关闭后不叠加背景提色（vibrancy）。
 * - [blurRadius] / [refractionHeight] / [refractionAmount] /
 *   [depthEffect] / [chromaticAberration] 对应 `blur()` 与 `lens()`。
 * - [highlight] / [shadow] / [innerShadow] 为 null 时分别不加高光层、
 *   投影层、内阴影层（高光/投影传 null 即「完全没有」，而不是库默认值）；
 *   库默认取值为 `Highlight.Default` / `Shadow.Default`。
 * - [surfaceTint] 为 null 时不绘制玻璃表面着色（缺省 2% 白），
 *   [activeColor] 的 20% 着色不受影响。
 *
 * [backdrop] 由调用方通过 `rememberLayerBackdrop()` 创建并在背景内容上
 * 调用 `Modifier.layerBackdrop(backdrop)` 采样；为 null 时回退为
 * 普通 surface 卡样式，组件可独立预览。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LiquidGlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    enabled: Boolean = true,
    icon: ImageVector,
    label: String? = null,
    activeColor: Color? = null,
    tint: Color? = null,
    iconContentDescription: String? = null,
    /*
     * 长按动作（可空）：传入时改用 combinedClickable，
     * 按压反馈仍复用同一个 interactionSource。
     */
    onLongClick: (() -> Unit)? = null,
    /*
     * 按下时的玻璃动力学（视差偏移 / blur 收窄 / 折射增强 / 背景扭曲）：
     * 缺省开启，即完整复现参考实现的手感；
     * 传 false 时只保留挤压缩放反馈（旧版 Fab 行为）。
     */
    pressDynamics: Boolean = true,
    /*
     * 玻璃效果常量：缺省值等于组件原有（底栏风格）取值。
     */
    vibrancyEnabled: Boolean = true,
    blurRadius: Dp = 8.dp,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = 18.dp,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = true,
    highlight: Highlight? = Highlight.Ambient.copy(alpha = 0.5f),
    shadow: Shadow? = Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.25f)),
    innerShadow: InnerShadow? = InnerShadow(radius = 6.dp, alpha = 0.25f),
    surfaceTint: Color? = Color.White.copy(alpha = 0.02f),
) {
    val surface = MaterialTheme.colorScheme.surface

    val contentColor =
        tint
            ?: activeColor
            ?: MaterialTheme.colorScheme.onSurface

    val shape: Shape =
        if (label == null) {
            CircleShape
        } else {
            Capsule()
        }

    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val pressed by
    interactionSource.collectIsPressedAsState()

    /*
     * 按下点（组件坐标系）：参考实现用 PressInteraction 的 pressPosition
     * 驱动视差偏移与背景扭曲；松开/取消时置为 NaN，退化为「按压点居中」。
     * 与 collectIsPressedAsState 并行订阅同一个 interactionSource
     * （MutableInteractionSource 支持多订阅者，见 LiquidGlassButton 同款写法）。
     */
    var pressPosition by
    remember {
        mutableStateOf(
            Offset(
                Float.NaN,
                Float.NaN,
            ),
        )
    }

    LaunchedEffect(
        interactionSource,
        pressDynamics,
    ) {
        if (!pressDynamics) {
            return@LaunchedEffect
        }

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressPosition =
                        interaction.pressPosition
                }

                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    pressPosition =
                        Offset(
                            Float.NaN,
                            Float.NaN,
                        )
                }
            }
        }
    }

    val hasPressPosition =
        pressPosition.x.isFinite() &&
                pressPosition.y.isFinite()

    val pressProgress by
    animateFloatAsState(
        targetValue =
            if (pressed && enabled) {
                1f
            } else {
                0f
            },
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy,
                stiffness =
                    Spring.StiffnessMediumLow,
            ),
        label = "liquidGlassFabPress",
    )

    /*
     * 玻璃动力学进度：关闭 [pressDynamics] 时恒为 0，
     * 于是下面所有按下调制项都退化为静止取值（外观不变）。
     * 挤压缩放/透明度仍用 [pressProgress]，不受开关影响。
     */
    val dynamicsProgress =
        if (pressDynamics) {
            pressProgress
        } else {
            0f
        }

    val glassModifier =
        if (backdrop != null) {
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (vibrancyEnabled) {
                            vibrancy()
                        }

                        /*
                         * 按下时 blur 收窄 25%（同参考实现）。
                         */
                        blur(
                            blurRadius.toPx() *
                                    (1f - 0.25f * dynamicsProgress),
                        )

                        /*
                         * 按下时折射增强 50%（同参考实现）。
                         * 参考实现在 blurRadius / lensHeight / lensAmount 为 0 时
                         * 跳过对应调用；这里不做跳过也是等价的——blur() 与 lens()
                         * 自身在半径/折射量 <= 0 时直接 return，且 lens 的重心
                         * 参数 refractionHeight 在参考实现里是写死的 10.dp。
                         */
                        lens(
                            refractionHeight.toPx(),
                            (
                                    refractionAmount.value *
                                            (
                                                    1f +
                                                            0.5f *
                                                            dynamicsProgress
                                                    )
                                    ).dp.toPx(),
                            depthEffect = depthEffect,
                            chromaticAberration = chromaticAberration,
                        )
                    },
                    highlight = highlight?.let { h -> { h } },
                    shadow = shadow?.let { s -> { s } },
                    innerShadow = innerShadow?.let { inner -> { inner } },
                    /*
                     * 背景扭曲（同参考实现的 onDrawBackdrop）：
                     * 未按下时原样绘制（与库默认行为逐调用一致，静止外观不变）；
                     * 按下时以按下点为支点做 translate + scale。
                     */
                    onDrawBackdrop = { drawBackdrop ->
                        val p = dynamicsProgress

                        if (p <= 0f) {
                            drawBackdrop()
                        } else {
                            val cx =
                                if (hasPressPosition) {
                                    pressPosition.x
                                } else {
                                    size.width / 2f
                                }

                            val cy =
                                if (hasPressPosition) {
                                    pressPosition.y
                                } else {
                                    size.height / 2f
                                }

                            val nx =
                                (
                                        cx /
                                                size.width.coerceAtLeast(
                                                    1f,
                                                )
                                        ).coerceIn(0f, 1f) -
                                        0.5f

                            val ny =
                                (
                                        cy /
                                                size.height.coerceAtLeast(
                                                    1f,
                                                )
                                        ).coerceIn(0f, 1f) -
                                        0.5f

                            val scaleX =
                                1f -
                                        0.13f * p

                            val scaleY =
                                1f +
                                        0.075f * p

                            val translateX =
                                -nx *
                                        18f *
                                        p

                            val translateY =
                                -ny *
                                        14f *
                                        p

                            translate(
                                translateX,
                                translateY,
                            ) {
                                scale(
                                    scaleX = scaleX,
                                    scaleY = scaleY,
                                    pivot =
                                        Offset(
                                            cx,
                                            cy,
                                        ),
                                ) {
                                    drawBackdrop()
                                }
                            }
                        }
                    },
                    onDrawSurface = {
                        surfaceTint?.let { drawRect(it) }

                        if (activeColor != null) {
                            drawRect(activeColor.copy(alpha = 0.20f))
                        }
                    },
                )
        } else {
            Modifier
                .clip(shape)
                .background(surface.copy(alpha = 0.96f), shape)
                .then(
                    if (activeColor != null) {
                        Modifier.background(
                            activeColor.copy(alpha = 0.20f),
                            shape,
                        )
                    } else {
                        Modifier
                    }
                )
        }

    Box(
        modifier =
            modifier
                .then(
                    if (label == null) {
                        Modifier.size(67.dp)
                    } else {
                        Modifier
                            .height(56.dp)
                            .defaultMinSize(minWidth = 67.dp)
                    }
                )
                .graphicsLayer {
                    val p = pressProgress

                    scaleX =
                        1f -
                                0.035f * p

                    scaleY =
                        1f +
                                0.018f * p

                    /*
                     * 视差偏移：按下点在组件内越偏一侧，玻璃体越往该侧让位
                     * （±3.dp × 归一化位置 × 按下进度），同参考实现。
                     */
                    translationX =
                        if (
                            hasPressPosition &&
                            size.width > 0f
                        ) {
                            (
                                    pressPosition.x /
                                            size.width -
                                            0.5f
                                    ) *
                                    3f.dp.toPx() *
                                    dynamicsProgress
                        } else {
                            0f
                        }

                    translationY =
                        if (
                            hasPressPosition &&
                            size.height > 0f
                        ) {
                            (
                                    pressPosition.y /
                                            size.height -
                                            0.5f
                                    ) *
                                    3f.dp.toPx() *
                                    dynamicsProgress
                        } else {
                            0f
                        }

                    alpha =
                        if (enabled) {
                            1f
                        } else {
                            0.5f
                        }
                }
                .then(glassModifier)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                    } else {
                        Modifier.clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    },
                )
                .then(
                    if (label == null) {
                        Modifier
                    } else {
                        Modifier.padding(horizontal = 20.dp)
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription ?: label,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription ?: label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )

                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
