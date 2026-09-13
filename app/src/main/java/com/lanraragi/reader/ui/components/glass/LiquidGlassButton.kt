package com.lanraragi.reader.ui.components.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.Capsule

/**
 * 液态玻璃按钮：纯图标或「图标 + 文字」。
 *
 * 视觉参数照抄 DetailScreen 的 LiquidGlassButton/LiquidGlassVisual：
 * Capsule 形状，默认高度 48.dp、水平内边距 16.dp、内容间距 8.dp，
 * blur(1.dp，按下时收窄 25%)，lens(refractionHeight = 10.dp,
 * refractionAmount = 1.dp，按下时增强 50%，depthEffect，色散)，
 * 高光/投影使用 drawBackdrop 默认值（Highlight.Default + Shadow.Default）；
 * 按下时玻璃体做参考实现同款挤压/偏移/扭曲反馈，禁用时整体 50% 透明。
 *
 * [activeColor] 为激活态强调色：非 null 时在玻璃表面叠加 20% alpha
 * 着色层，且在未指定 [tint] 时同时作为图标与文字颜色。
 * [tint] 覆盖图标与文字颜色（例如悬浮按钮的自定义颜色），
 * 优先级高于 [activeColor]；两者都为空时使用 onSurface。
 *
 * [iconContentDescription] 为图标的无障碍描述（中文），缺省使用 [label]。
 * 宽度由 [modifier] 决定（参考实现常用 `Modifier.weight(1f)` 等分）。
 *
 * [backdrop] 由调用方通过 `rememberLayerBackdrop()` 创建并在背景内容上
 * 调用 `Modifier.layerBackdrop(backdrop)` 采样；为 null 时回退为
 * 普通 surface 卡样式，组件可独立预览。
 *
 * 与参考实现的差异：不再经过 DetailScreen 的玻璃悬浮层
 * （GlassOverlayController/GlassSlot）机制，按钮始终在原位渲染。
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    enabled: Boolean = true,
    activeColor: Color? = null,
    tint: Color? = null,
    icon: ImageVector? = null,
    label: String? = null,
    iconContentDescription: String? = null,
) {
    val surface = MaterialTheme.colorScheme.surface

    val contentColor =
        tint
            ?: activeColor
            ?: MaterialTheme.colorScheme.onSurface

    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val pressed by
    interactionSource.collectIsPressedAsState()

    var pressPosition by
    remember {
        mutableStateOf(
            Offset(
                Float.NaN,
                Float.NaN,
            ),
        )
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressPosition = interaction.pressPosition
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
        label = "liquidGlassButtonPress",
    )

    val glassModifier =
        if (backdrop != null) {
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        blur(
                            1.dp.toPx() *
                                    (1f - 0.25f * pressProgress),
                        )

                        lens(
                            refractionHeight = 10.dp.toPx(),
                            refractionAmount =
                                (1f + 0.5f * pressProgress).dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        val p =
                            pressProgress

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
                        drawRect(Color.White.copy(alpha = 0.02f))

                        if (activeColor != null) {
                            drawRect(activeColor.copy(alpha = 0.20f))
                        }
                    },
                )
        } else {
            Modifier
                .clip(Capsule())
                .background(surface.copy(alpha = 0.96f), Capsule())
                .then(
                    if (activeColor != null) {
                        Modifier.background(
                            activeColor.copy(alpha = 0.20f),
                            Capsule(),
                        )
                    } else {
                        Modifier
                    }
                )
        }

    Row(
        modifier =
            modifier
                .height(48.dp)
                .graphicsLayer {
                    val p = pressProgress

                    scaleX =
                        1f -
                                0.035f * p

                    scaleY =
                        1f +
                                0.018f * p

                    translationX =
                        if (
                            hasPressPosition &&
                            size.width > 0f
                        ) {
                            (
                                    pressPosition.x /
                                            size.width -
                                            0.5f
                                    ) * 3f.dp.toPx() * p
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
                                    ) * 3f.dp.toPx() * p
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
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(
                    horizontal = 16.dp,
                ),
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription ?: label,
                tint = contentColor,
            )
        }

        if (label != null) {
            Text(
                text = label,
                color = contentColor,
            )
        }
    }
}
