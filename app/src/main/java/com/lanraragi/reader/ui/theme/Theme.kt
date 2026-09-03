package com.lanraragi.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 设计 token（参考 UI 复现规格：深黑背景 + 卡片 + 低饱和蓝紫高亮）。
val Accent = Color(0xFF7E8BE0)          // 激活图标/主色（淡蓝紫）
val ActivePill = Color(0xFF5D5C8C)      // 侧栏选中态药丸（藕紫）
val DarkBg = Color(0xFF0E0E12)          // 全局主背景
val DarkSurface = Color(0xFF1B1B22)     // 卡片/分组背景
val DarkSurfaceHigh = Color(0xFF23222B) // 搜索框/pill 浮层背景

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = ActivePill,
    onSecondary = Color.White,
    background = DarkBg,
    onBackground = Color(0xFFFFFFFF),
    surface = DarkSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9BCC9),
    outline = Color(0xFF7E8296),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = ActivePill,
)

@Composable
fun LanraragiReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
