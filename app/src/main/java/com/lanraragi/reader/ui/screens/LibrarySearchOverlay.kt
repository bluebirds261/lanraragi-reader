package com.lanraragi.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.ui.components.glass.liquidGlassCapsule
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText

/**
 * 搜索面（库页顶栏胶囊的原地展开层）—— 合并两份参考实现的长处：
 *
 * - **EhViewer 1.14.6 `ui/screen/SearchBarScreen.kt`**：胶囊原地展开成面板、背后压 scrim、
 *   展开区里直接铺联想；联想把「历史 + 标签」合进同一条列表。
 * - **JHenTai `pages/search`**：历史是 EHTag 造形的胶囊（高 24 / 圆角 8 / 字号 12）、
 *   垃圾桶图标进入删除模式、标签行显示命名空间着色 + 词库译名、
 *   **结果留在搜索面里**（`bodyType` 在「联想/历史」与「结果」之间切换）。
 *
 * 与本项目现状的取舍（按用户裁决）：
 * - 「标签浏览」独立页已删除，标签联想与热门标签都在这里；
 * - 面板里不再显示「排序与筛选」（与搜索无关；库页顶栏左侧键仍是它）；
 * - 结果不另画一套网格，由调用方传入 [resultsContent]（复用库页同一份列表状态）；
 * - 浅色主题下玻璃底面加厚（[baseSurfaceAlpha]），否则文字读不清。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibrarySearchOverlay(
    expanded: Boolean,
    submitted: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSubmitHistory: (String) -> Unit,
    onCollapse: () -> Unit,
    onAppendTag: (fullTag: String, excluded: Boolean) -> Unit,
    history: List<String>,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    suggestions: List<TagStat>,
    hotTags: List<TagStat>,
    hotNamespaces: List<String>,
    hotNamespace: String,
    onHotNamespaceChange: (String) -> Unit,
    resultCount: Int,
    backdrop: Backdrop?,
    resultsContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) return

    val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    // 浅色主题下纯玻璃几乎读不清（用户实测反馈），垫一层较厚的 surface；深色主题保留通透感。
    val baseAlpha = if (lightTheme) 0.86f else 0.42f
    val scrimAlpha by animateFloatAsState(
        targetValue = if (submitted) 1f else if (lightTheme) 0.94f else 0.86f,
        animationSpec = tween(durationMillis = 240),
        label = "searchScrim",
    )
    val corner by animateDpAsState(
        targetValue = if (submitted) 14.dp else 18.dp,
        animationSpec = tween(durationMillis = 260),
        label = "searchCorner",
    )
    val focusRequester = remember { FocusRequester() }
    val scrimInteraction = remember { MutableInteractionSource() }

    var deleteMode by remember { mutableStateOf(false) }
    var hideHistory by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) runCatching { focusRequester.requestFocus() }
    }

    Box(modifier.fillMaxSize()) {
        // scrim 单独一层：只负责「点空白收起」，不覆盖面板（否则会把输入框的焦点/手势吃掉）
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha))
                .clickable(indication = null, interactionSource = scrimInteraction) { onCollapse() },
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // ============================================================
            // 面板：收起 + 输入 + 清除（形变中的玻璃：胶囊 → 圆角矩形）
            // ============================================================
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassCapsule(
                        backdrop = backdrop,
                        outline = true,
                        shape = RoundedCornerShape(corner),
                        baseSurfaceAlpha = baseAlpha,
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

            if (submitted) {
                // ------------------------------------------------------------
                // 结果内嵌：与库页共用同一份列表状态（[resultsContent]）
                // ------------------------------------------------------------
                Text(
                    "$resultCount 个结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                )
                resultsContent(Modifier.fillMaxSize())
                return@Column
            }

            // ------------------------------------------------------------
            // 联想 / 历史态（JHenTai 的 suggestionAndHistory）
            // ------------------------------------------------------------
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (history.isNotEmpty()) {
                    item(key = "history-header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "搜索历史",
                                style = MaterialTheme.typography.titleSmall,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                            )
                            // JHenTai：垃圾桶图标切换删除模式；隐藏时换成「眼睛」
                            IconButton(onClick = { deleteMode = !deleteMode }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = if (deleteMode) "退出删除模式" else "管理历史",
                                    modifier = Modifier.size(20.dp),
                                    tint =
                                        if (deleteMode) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                            IconButton(onClick = { hideHistory = !hideHistory }) {
                                Icon(
                                    Icons.Filled.Visibility,
                                    contentDescription = if (hideHistory) "显示历史" else "隐藏历史",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "清空",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .clickable(onClick = onClearHistory)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (deleteMode) {
                        item(key = "history-hint") {
                            Text(
                                "点历史条目即可删除，再点垃圾桶退出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = "history-chips") {
                        AnimatedVisibility(
                            visible = !hideHistory,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                history.forEach { h ->
                                    EhTagChip(
                                        text = h,
                                        inDeleteMode = deleteMode,
                                        onClick = {
                                            if (deleteMode) onRemoveHistory(h) else onSubmitHistory(h)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- 联想（有输入时）----
                if (suggestions.isNotEmpty()) {
                    item(key = "suggestion-header") {
                        Text(
                            "标签联想（点选=包含，长按=排除）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                    items(suggestions, key = { "s:${it.full}" }) { tag ->
                        val ns = TagNamespaceRegistry.canonicalNamespace(tag.namespace).orEmpty()
                        SearchRow(
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
                            onClick = { onAppendTag(tag.full, false) },
                            onLongClick = { onAppendTag(tag.full, true) },
                        )
                    }
                } else if (query.isNotBlank()) {
                    item(key = "direct") {
                        SearchRow(
                            leading = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            title = "搜索「$query」",
                            subtitle = null,
                            titleColor = MaterialTheme.colorScheme.primary,
                            onClick = { onSubmit() },
                        )
                    }
                }

                // ---- 热门标签：单独一块区域（JHenTai 的标签面板做法）----
                if (query.isBlank() && hotTags.isNotEmpty()) {
                    item(key = "hot-header") {
                        Text(
                            "热门标签",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        )
                    }
                    item(key = "hot-namespaces") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            hotNamespaces.forEach { ns ->
                                FilterChipLike(
                                    text = ns.ifBlank { "全部" },
                                    selected = ns == hotNamespace,
                                    tint =
                                        if (ns.isBlank()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            rememberTagColor(
                                                TagNamespaceRegistry.canonicalNamespace(ns).orEmpty(),
                                            )
                                        },
                                    onClick = { onHotNamespaceChange(ns) },
                                )
                            }
                        }
                    }
                    item(key = "hot-chips") {
                        val visible = remember(hotTags, hotNamespace) {
                            hotTags.filter { hotNamespace.isBlank() || it.namespace == hotNamespace }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            visible.take(60).forEach { tag ->
                                val ns = TagNamespaceRegistry.canonicalNamespace(tag.namespace).orEmpty()
                                EhTagChip(
                                    text = rememberTagText(ns, tag.text),
                                    tint = rememberTagColor(ns),
                                    onClick = { onAppendTag(tag.full, false) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** JHenTai 的 EHTag：高 24 / 圆角 8 / 水平 6 / 垂直 3 / 字号 12；删除模式下弹入 × 徽章。 */
@Composable
private fun EhTagChip(
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    inDeleteMode: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 12.sp),
            color = tint,
        )
        AnimatedVisibility(visible = inDeleteMode, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/** 命名空间筛选小胶囊（热门标签区用）。 */
@Composable
private fun FilterChipLike(
    text: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    tint.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 两行联想行（EhViewer 的 `ListItem` 造型 + JHenTai 的命名空间着色与译名）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    titleColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
