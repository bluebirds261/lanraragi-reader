package com.lanraragi.reader.ui.components.glass

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * 横向液态玻璃胶囊条。
 *
 * 表面直接用 [liquidGlassCapsule]（与 MainScreen 液态底栏同一份配方：
 * vibrancy + blur(4.dp) + lens(24.dp, 24.dp, 色散) + Highlight.Ambient(0.5f)
 * + Shadow(16.dp, 25% 黑) + InnerShadow(6.dp, 0.25f) + 2% 白表面；离屏合成后胶囊裁剪），
 * 内容四周保留参考实现的 4.dp 内边距。
 *
 * [activeColor] 为选中项强调色：非 null 时在玻璃表面叠加 20% alpha 着色层；
 * 各选项图标/文字的选中着色由调用方在 [content] 内自行处理。
 *
 * 尺寸（宽度/高度）由 [modifier] 决定，例如
 * `Modifier.fillMaxWidth().height(64.dp)`。
 *
 * [backdrop] 由调用方通过 `rememberLayerBackdrop()` 创建并在背景内容上
 * 调用 `Modifier.layerBackdrop(backdrop)` 采样；为 null 时回退为普通 surface 卡样式。
 */
@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    activeColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .liquidGlassCapsule(backdrop = backdrop, activeColor = activeColor)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
