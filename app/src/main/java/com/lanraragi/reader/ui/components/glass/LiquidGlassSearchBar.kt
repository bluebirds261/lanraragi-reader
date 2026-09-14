package com.lanraragi.reader.ui.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * 液态玻璃搜索胶囊。
 *
 * 表面使用 [liquidGlassCapsule]，与 MainScreen 液态底栏**同一份配方**
 * （vibrancy + blur(4.dp) + lens(24.dp, 24.dp, 色散) + Highlight.Ambient(0.5f)
 * + Shadow(16.dp, 25% 黑) + InnerShadow(6.dp, 0.25f) + 2% 白表面）。
 * 高度固定 40.dp。
 *
 * 两种形态（由 [readOnly] 选择，缺省为可编辑）：
 *
 * 1. 可编辑（`readOnly = false`）：显示 [TextField]，[hint] 为空态占位文案。
 *    输入文本非空时右侧显示清除按钮（点击回调 `onValueChange("")`）；
 *    为空时右侧显示「搜索」按钮，点击触发 [onClick]。
 *
 * 2. 只读展示（`readOnly = true`）：不渲染输入框，改为
 *    「搜索」图标（contentDescription 为「搜索」）+ 文本——
 *    [value] 为空时显示 [hint]，用 onSurfaceVariant 着色；非空时显示 [value]，
 *    用 onSurface 着色。该形态没有输入变更，[onValueChange] 只会在点击清除
 *    按钮时收到空串（调用方可把它落到自己的清空动作上）。
 *
 * [leading] 是**合并进同一胶囊左侧的独立按键**（例如库页的「排序与筛选」）：
 * 它自带点击区与语义，与右侧搜索区互不干扰——整条胶囊因此可以是
 * 「一个玻璃壳 + 两个点击区」。为 null 时就是一条纯搜索胶囊，
 * 此时 [capsuleClickable] 让整条胶囊可点（等同于点搜索区）。
 *
 * [clearContentDescription] 为清除按钮的无障碍描述（中文），缺省「清除」，
 * 调用方可传入更完整的文案（如「清除搜索词」）。
 *
 * 宽度由 [modifier] 决定，例如在行内使用 `Modifier.weight(1f)`。
 *
 * [backdrop] 由调用方通过 `rememberLayerBackdrop()` 创建并在背景内容上
 * 调用 `Modifier.layerBackdrop(backdrop)` 采样；为 null 时回退为
 * 普通 surface 卡样式，组件可独立预览。
 */
@Composable
fun LiquidGlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    hint: String = "搜索",
    /*
     * 只读展示模式：不渲染 TextField，只显示当前搜索词（或 [hint] 占位）。
     */
    readOnly: Boolean = false,
    /*
     * 搜索区可点击（触发 [onClick]）。可编辑模式下忽略：输入框需要文本焦点，
     * 整块点击会与聚焦抢手势。
     */
    capsuleClickable: Boolean = false,
    /*
     * 清除按钮的无障碍描述，缺省「清除」。
     */
    clearContentDescription: String = "清除",
    /*
     * 合并进胶囊左侧的按键槽位（自带点击区），例如库页的排序/筛选键。
     */
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .height(40.dp)
                .liquidGlassCapsule(backdrop = backdrop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        if (readOnly && capsuleClickable) {
                            Modifier.clickable(
                                onClick = onClick,
                            )
                        } else {
                            Modifier
                        },
                    )
                    // 左侧没有合并按键时补上胶囊自身的内边距；有按键时按键自带
                    // 40dp 点击区，只剩一点视觉间距。
                    .padding(
                        start = if (leading == null) 12.dp else 8.dp,
                        end = 12.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (readOnly) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(20.dp),
                )

                Spacer(
                    modifier = Modifier.width(8.dp),
                )

                Text(
                    text = value.ifBlank { hint },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (value.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )

                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = clearContentDescription,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    placeholder = {
                        if (hint.isNotBlank()) {
                            Text(hint)
                        }
                    },
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.weight(1f),
                )

                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = clearContentDescription,
                        )
                    }
                } else {
                    IconButton(
                        onClick = onClick,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "搜索",
                        )
                    }
                }
            }
        }
    }
}
