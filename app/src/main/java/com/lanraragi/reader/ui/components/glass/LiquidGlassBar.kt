package com.lanraragi.reader.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * 横向液态玻璃胶囊条。
 *
 * 视觉参数照抄 MainScreen 液态底栏：
 * vibrancy + blur(4.dp) + lens(24.dp, 24.dp, 色散) +
 * Highlight.Ambient(alpha = 0.5f) + Shadow(16.dp, 25% 黑) +
 * InnerShadow(6.dp, alpha = 0.25f)，表面叠加 2% 白；
 * 内容四周保留参考实现的 4.dp 内边距，整体离屏合成并按胶囊裁剪。
 *
 * [activeColor] 为选中项强调色：非 null 时在玻璃表面叠加
 * 20% alpha 着色层（照抄参考实现选中滑块的着色方式）；
 * 各选项图标/文字的选中着色由调用方在 [content] 内自行处理。
 *
 * 尺寸（宽度/高度）由 [modifier] 决定，例如
 * `Modifier.fillMaxWidth().height(64.dp)`。
 *
 * [backdrop] 由调用方通过 `rememberLayerBackdrop()` 创建并在背景内容上
 * 调用 `Modifier.layerBackdrop(backdrop)` 采样；为 null 时回退为
 * 普通 surface 卡样式，组件可独立预览。
 */
@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    activeColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface

    if (backdrop == null) {
        Row(
            modifier =
                modifier
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
                    .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        return
    }

    Row(
        modifier =
            modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    shape = Capsule()
                    clip = true
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()

                        blur(
                            4.dp.toPx(),
                        )

                        lens(
                            24.dp.toPx(),
                            24.dp.toPx(),
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            alpha = 0.5f,
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 16.dp,
                            color = Color.Black.copy(alpha = 0.25f),
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 6.dp,
                            alpha = 0.25f,
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.02f))

                        if (activeColor != null) {
                            drawRect(activeColor.copy(alpha = 0.20f))
                        }
                    },
                )
                .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
