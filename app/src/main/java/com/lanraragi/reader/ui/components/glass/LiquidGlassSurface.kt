package com.lanraragi.reader.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * 液态玻璃胶囊表面（**全 App 唯一一份配方**）。
 *
 * 参数与 MainScreen 液态底栏逐项一致：
 * `vibrancy + blur(4.dp) + lens(24.dp, 24.dp, 色散) + Highlight.Ambient(alpha = 0.5f)
 * + Shadow(16.dp, 25% 黑) + InnerShadow(6.dp, alpha = 0.25f)`，
 * 表面叠加 2% 白；离屏合成（`CompositingStrategy.Offscreen`）后按胶囊裁剪。
 *
 * 之所以抽成共享修饰符：底栏、批量操作条、库页顶栏搜索胶囊都要「一样」的玻璃质感，
 * 各自抄一份参数迟早会漂移（此前搜索胶囊就只抄了 vibrancy + blur + lens，
 * 少了高光/内外阴影/表面白，观感明显比底栏"薄"）。
 *
 * [activeColor] 非 null 时在玻璃表面叠加 20% 着色层（选中态强调），
 * 与底栏选中滑块的着色方式一致。
 *
 * [backdrop] 由调用方通过 `rememberLayerBackdrop()` 创建并在背景内容上
 * 调用 `Modifier.layerBackdrop(backdrop)` 采样；为 null 时回退为普通 surface 卡样式，
 * 组件仍可独立预览。
 *
 * 只负责「表面」：尺寸、内边距、内容布局仍由调用方决定。
 */
@Composable
fun Modifier.liquidGlassCapsule(
    backdrop: Backdrop?,
    activeColor: Color? = null,
): Modifier {
    if (backdrop == null) {
        return this
            .clip(Capsule())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), Capsule())
            .then(
                if (activeColor != null) {
                    Modifier.background(activeColor.copy(alpha = 0.20f), Capsule())
                } else {
                    Modifier
                }
            )
    }

    return this
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
}
