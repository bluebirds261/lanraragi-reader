package com.lanraragi.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    // 第三色沿用同族低饱和蓝紫（比主色更偏藕紫），用于少量强调与图表/角标。
    tertiary = Color(0xFFA9AEE8),
    onTertiary = Color(0xFF20223C),
    tertiaryContainer = Color(0xFF3A3A5C),
    onTertiaryContainer = Color(0xFFDDE0FF),
    background = DarkBg,
    onBackground = Color(0xFFFFFFFF),
    surface = DarkSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9BCC9),
    // 深色底上的错误色保持低饱和柔红，避免刺眼但文字对比度达标。
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    outline = Color(0xFF7E8296),
    // 分割线/卡片描边等弱边界：比 outline 更暗，贴近卡片背景。
    outlineVariant = Color(0xFF3A3A46),
)

// 亮色体系：与暗色同族的低饱和蓝紫主色 + 浅灰白底 + 中性灰描边
private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = ActivePill,
    onSecondary = Color.White,
    // 第三色与暗色同族：低饱和藕紫，容器色为极浅淡紫。
    tertiary = Color(0xFF6A5F9E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E0F7),
    onTertiaryContainer = Color(0xFF23204A),
    background = Color(0xFFF2F2F6),        // 全局主背景（浅灰白）
    onBackground = Color(0xFF17171D),
    surface = Color(0xFFFAFAFC),           // 卡片/分组背景（近白）
    onSurface = Color(0xFF17171D),
    surfaceVariant = Color(0xFFE4E4EC),    // 输入框/pill 浮层背景（中性浅灰）
    onSurfaceVariant = Color(0xFF55576A),
    // 亮色底上的错误色维持可读的中性红，与低饱和主色不冲突。
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFF9295A8),           // 中性灰描边
    // 弱边界：比 outline 更浅，用于分割线与卡片细描边。
    outlineVariant = Color(0xFFC6C8D6),
)

// 定制排版：headline/title 用 Medium 并收紧行高，body 沿用 Material 3 默认
private val Typography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
)

@Composable
fun LanraragiReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
