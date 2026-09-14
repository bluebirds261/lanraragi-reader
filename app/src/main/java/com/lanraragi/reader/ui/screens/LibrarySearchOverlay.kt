package com.lanraragi.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText
import com.lanraragi.reader.ui.components.glass.liquidGlassCapsule

/**
 * 库页顶栏搜索胶囊的**原地展开层**（参考 EhViewer 1.14.6 的 `SearchBarScreen`：
 * M3 `SearchBar(expanded=…)` 的展开交互 —— 胶囊在原地长成一块面板，背后压一层 scrim，
 * 展开区内直接铺联想列表；联想列表把**历史与标签合并成一条列表**，历史行右侧带 ✕ 删除）。
 *
 * 与参考实现的差异（有意保留我们自己的语言）：
 * - 表面仍是自家的液态玻璃（[liquidGlassCapsule] 支持传入形状），形变过程用
 *   `animateDpAsState` 过渡圆角，观感上是「胶囊长成面板」而不是 M3 的通用容器；
 * - 联想的命名空间沿用 [rememberTagColor]，标签文本走词库译名（JHenTai 的做法）；
 * - 收起后回到原本的搜索胶囊（不新增路由），「标签浏览」另有入口进入完整搜索页。
 *
 * @param query 当前查询串（与库页共用一份，展开时可直接继续编辑）
 * @param onSubmit 提交搜索（收起面板由调用方负责）
 * @param onCollapse 收起
 * @param onOpenAdvanced 进入完整搜索页（命名空间 + 标签面板）
 * @param onOpenFilter 打开库页既有的排序与筛选面板
 * @param onAppendTag 点选联想标签（追加进查询串）
 */
@Composable
fun LibrarySearchOverlay(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCollapse: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenFilter: () -> Unit,
    onAppendTag: (String) -> Unit,
    history: List<String>,
    onRemoveHistory: (String) -> Unit,
    suggestions: List<TagStat>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    if (!expanded) return

    val corner by animateDpAsState(
        targetValue = 18.dp,
        animationSpec = tween(durationMillis = 260),
        label = "searchOverlayCorner",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.86f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "searchOverlayScrim",
    )
    val focusRequester = remember { FocusRequester() }
    var deleteMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 展开即聚焦（EhViewer 的展开态直接给输入焦点）
    LaunchedEffect(expanded) {
        if (expanded) runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha))
            // 点空白处收起（面板本体在下面，会自己吃掉点击）
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onCollapse() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // ------------------------------------------------------------
            // 形变中的玻璃面板：胶囊 → 圆角矩形
            // ------------------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassCapsule(backdrop = backdrop, outline = true, shape = RoundedCornerShape(corner))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "收起搜索")
                    }
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索标题或标签…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }
                }

                // 展开区里的动作行（对应 EhViewer 展开态的 `filter` 槽位）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenFilter) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("排序与筛选")
                    }
                    TextButton(onClick = onOpenAdvanced) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("标签浏览")
                    }
                    Spacer(Modifier.weight(1f))
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { deleteMode = !deleteMode }) {
                            Text(if (deleteMode) "完成" else "管理历史")
                        }
                    }
                }
            }

            // ------------------------------------------------------------
            // 联想列表：历史与标签合并成一条（EhViewer 的做法）
            // ------------------------------------------------------------
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(220)) + slideInVertically { it / 12 },
                exit = fadeOut(tween(120)),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(history, key = { "h:$it" }) { h ->
                        OverlayRow(
                            leading = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            title = h,
                            subtitle = null,
                            titleColor = MaterialTheme.colorScheme.onSurface,
                            trailing = {
                                if (deleteMode) {
                                    IconButton(onClick = { onRemoveHistory(h) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "删除该历史", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            onClick = {
                                if (deleteMode) {
                                    onRemoveHistory(h)
                                } else {
                                    onQueryChange(h)
                                    onSubmit()
                                }
                            },
                        )
                    }

                    if (suggestions.isNotEmpty()) {
                        item(key = "tag-header") {
                            Text(
                                "标签",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(suggestions, key = { "t:${it.full}" }) { tag ->
                        val ns = TagNamespaceRegistry.canonicalNamespace(tag.namespace).orEmpty()
                        OverlayRow(
                            leading = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = rememberTagColor(ns),
                                )
                            },
                            title = "${ns.ifBlank { tag.namespace.orEmpty() }}:${tag.text}",
                            subtitle = rememberTagText(ns, tag.text),
                            titleColor = rememberTagColor(ns),
                            trailing = {
                                Text(
                                    tag.weight.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { onAppendTag(tag.full) },
                        )
                    }

                    if (history.isEmpty() && suggestions.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "输入关键词开始搜索；也可以到「标签浏览」按命名空间挑标签",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            )
                        }
                    }

                    item(key = "enter-hint") {
                        if (query.isNotBlank()) {
                            OverlayRow(
                                leading = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                title = "搜索「$query」",
                                subtitle = null,
                                titleColor = MaterialTheme.colorScheme.primary,
                                trailing = {},
                                onClick = onSubmit,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 展开层里的一行：两行文本 + 可选尾随动作（EhViewer 的 `ListItem` 造型）。 */
@Composable
private fun OverlayRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    titleColor: Color,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = titleColor,
            )
            if (!subtitle.isNullOrBlank() && subtitle != title) {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
