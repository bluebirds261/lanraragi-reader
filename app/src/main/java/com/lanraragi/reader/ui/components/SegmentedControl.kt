package com.lanraragi.reader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 分段控件的一段：文字 + 可选前置图标。 */
data class Segment(
    val label: String,
    val icon: ImageVector? = null,
)

/**
 * 胶囊分段控件：等宽分段、单选、选中段以主题色填充。
 *
 * 与下载页 / 元数据中文化页里那两份私有实现同一视觉语言（surfaceVariant 轨道 +
 * primaryContainer 选中填充），抽到公共组件后库页筛选面板的「排序方向 / 视图模式 / 网格大小」
 * 可以共用，避免继续复制第四份。
 *
 * 仅用于 2–4 段的互斥选择；段数更多（如 2..8 列）请配合 [compact] 使用小字号，
 * 或改用滑杆。选项文字本身就是无障碍标签，图标一律标记为装饰，避免读屏重复朗读。
 */
@Composable
fun SegmentedControl(
    segments: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = MaterialTheme.colorScheme.primaryContainer
    val height = if (compact) 36.dp else 40.dp

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { index, segment ->
                val isSelected = selectedIndex == index
                val background by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else Color.Transparent,
                    animationSpec = tween(durationMillis = 160),
                    label = "segmentFill",
                )
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(background)
                        .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                        .semantics {
                            this.selected = isSelected
                            this.role = Role.RadioButton
                        }
                        .padding(horizontal = if (compact) 2.dp else 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
                    ) {
                        segment.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                // 文字已表达语义，图标保持装饰。
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                            )
                        }
                        Text(
                            text = segment.label,
                            style = if (compact) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
