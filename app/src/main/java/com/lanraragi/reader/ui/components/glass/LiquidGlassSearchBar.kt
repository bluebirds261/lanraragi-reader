package com.lanraragi.reader.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * 液态玻璃搜索胶囊。
 *
 * 视觉参数照抄 LibraryScreen 玻璃搜索框：
 * vibrancy + blur(8.dp) + lens(10.dp, 18.dp, 色散)，
 * Capsule 形状，高度 40.dp，内容水平内边距 12.dp，
 * 可编辑模式下 TextField 容器与指示线全部透明。
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
 * [capsuleClickable] 为 true 时整条胶囊可点击并触发 [onClick]，即使 [value]
 * 非空也能整条点按（例如 LibraryScreen 跳转搜索页）；缺省 false 时只读模式
 * 只有清除按钮可点。可编辑模式下该参数被忽略：输入框需要文本焦点，
 * 整条点击会与聚焦抢手势。
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
     * 只读模式下让整条胶囊可点击（触发 [onClick]），可编辑模式下忽略。
     */
    capsuleClickable: Boolean = false,
    /*
     * 清除按钮的无障碍描述，缺省「清除」。
     */
    clearContentDescription: String = "清除",
) {
    val surface = MaterialTheme.colorScheme.surface

    val glassModifier =
        if (backdrop != null) {
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()

                        blur(
                            8.dp.toPx(),
                        )

                        lens(
                            10.dp.toPx(),
                            18.dp.toPx(),
                            chromaticAberration = true,
                        )
                    },
                )
                .clip(Capsule())
        } else {
            Modifier
                .clip(Capsule())
                .background(surface.copy(alpha = 0.96f), Capsule())
        }

    Row(
        modifier =
            modifier
                .height(40.dp)
                .then(glassModifier)
                .then(
                    if (readOnly && capsuleClickable) {
                        Modifier.clickable(
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp),
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
