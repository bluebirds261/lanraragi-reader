package com.lanraragi.reader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.Backdrop
import com.lanraragi.reader.data.catalog.SearchQueryCodec
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.data.tags.knowledge.TagCandidate
import com.lanraragi.reader.ui.components.glass.liquidGlassCapsule
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText

/** 联想列表默认展示的行数；超出部分由「更多」展开。 */
private const val SUGGESTION_PREVIEW = 8

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibrarySearchOverlay(
    session: SearchSessionState,
    visibility: MutableTransitionState<Boolean>,
    onSubmit: () -> Unit,
    onSubmitHistory: (String) -> Unit,
    onAppendTag: (String, Boolean?) -> Unit,
    history: List<String>, legacyHistory: List<String>,
    historyHidden: Boolean, historyPaused: Boolean,
    onHistoryHidden: (Boolean) -> Unit, onHistoryPaused: (Boolean) -> Unit,
    onRemoveHistory: (String, Boolean) -> Unit, onClearHistory: (Boolean) -> Unit,
    suggestions: List<TagCandidate>, suggestionsLoading: Boolean, suggestionsError: String?,
    hotTags: List<TagStat>, hotLoading: Boolean, hotError: String?, hotUpdated: String?, hotLocal: Boolean,
    onRetryHot: () -> Unit, onOpenDictionary: () -> Unit,
    backdrop: Backdrop?,
) {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(visibility.currentState || visibility.targetState, lightSurface) {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val controller = (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        if (controller != null && (visibility.currentState || visibility.targetState)) {
            controller.isAppearanceLightStatusBars = lightSurface
        }
        onDispose {
            if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var managing by rememberSaveable { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var legacyExpanded by rememberSaveable { mutableStateOf(false) }
    var hotExpanded by rememberSaveable { mutableStateOf(false) }
    var namespace by rememberSaveable { mutableStateOf("") }
    var clearHistory by remember { mutableStateOf<Boolean?>(null) }
    var help by remember { mutableStateOf(false) }
    var suggestionsExpanded by rememberSaveable { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    var hotMenu by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    /*
     * 返回优先级：先退出历史管理模式，再收起搜索面。
     *
     * 多选不在这里处理：结果现在显示在图库页上，长按进入多选也发生在那一层，
     * 搜索面展开时只会盖住输入区，不该替底层吞掉一次返回。
     */
    val back: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        if (managing) managing = false else session.back()
    }
    BackHandler(visibility.currentState || visibility.targetState) {
        if (imeVisible) { keyboard?.hide(); focusManager.clearFocus() } else back()
    }
    val query = session.draft.text
    val conditions = remember(query) { SearchQueryCodec.parse(query).filter { it.exact && it.value.isNotBlank() } }
    val inputToken = SearchQueryCodec.active(query, session.draft.selection.start).value.substringAfter(':')
    // 输入态默认只铺前若干条：键盘打开时首屏要留给真正可能被点到的候选，其余由「更多」展开。
    val visibleSuggestions = if (suggestionsExpanded) suggestions else suggestions.take(SUGGESTION_PREVIEW)
    /*
     * 键盘高亮。
     *
     * EhViewer 与 JHenTai 都没有实现建议列表的上下键选择（EhViewer 靠 Compose 焦点系统，
     * JHenTai 全仓没有 arrowUp/arrowDown 处理），外接键盘与平板场景下只能点。
     * 这里用「高亮下标」而不是 moveFocus：建议行是 combinedClickable 的 Row、不是可聚焦节点，
     * 原先的 `moveFocus(Down)` 实际是空操作。下标 -1 表示高亮回到输入框本身。
     */
    val suggestionListState = rememberLazyListState()
    var highlighted by remember { mutableStateOf(-1) }
    // 输入变化后旧的候选已经被换掉，高亮下标不再对应任何东西。
    LaunchedEffect(query, suggestions) { highlighted = -1 }
    /** 输入态里排在高亮候选之前的行数（直接搜索 / 命中历史 / 加载条 / 错误行）。 */
    val matchedHistory = if (historyHidden) emptyList() else history.filter { it.contains(query, true) }.take(3)
    val suggestionRowOffset =
        if (query.isNotBlank()) {
            1 + matchedHistory.size +
                (if (suggestionsLoading) 1 else 0) +
                (if (suggestionsError != null) 1 else 0)
        } else {
            0
        }
    LaunchedEffect(highlighted, suggestionRowOffset) {
        if (highlighted >= 0) suggestionListState.animateScrollToItem(suggestionRowOffset + highlighted)
    }
    // 圆角跟着**开合动画**走（而不是 phase 一变就跳）：收起态胶囊是 24dp 全圆角
    // （48dp 高的一半），展开到面板时收到 14dp。两者用同一个 240ms，视觉上才是
    // 「同一个面在变形」，而不是「先变圆角、再长出面板」。
    val corner by animateDpAsState(if (visibility.targetState) 14.dp else 24.dp, tween(240), label = "searchShape")
    // AnimatedVisibility retains the outgoing subtree until collapse finishes.
    AnimatedVisibility(visibleState = visibility,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(240)) + fadeIn(tween(160)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(240)) + fadeOut(tween(160))) {
        LaunchedEffect(session.phase) {
            if (session.phase == SearchPhase.EDITING) { focusRequester.requestFocus(); keyboard?.show() }
            else { keyboard?.hide(); focusManager.clearFocus(); managing = false }
        }
        LaunchedEffect(hotLocal, hotTags) { if (hotTags.none { it.namespace.orEmpty() == namespace }) namespace = "" }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth()
                .liquidGlassCapsule(backdrop = backdrop, outline = true, shape = RoundedCornerShape(corner), baseSurfaceAlpha = 0.94f),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回搜索", Modifier.size(20.dp)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("搜索标题或标签…", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(value = session.draft, onValueChange = { session.edit(it) }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).wrapContentHeight()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Escape -> { back(); true }
                                    Key.DirectionDown -> {
                                        if (visibleSuggestions.isEmpty()) false
                                        else { highlighted = (highlighted + 1).coerceAtMost(visibleSuggestions.lastIndex); true }
                                    }
                                    Key.DirectionUp -> {
                                        if (highlighted < 0) false else { highlighted -= 1; true }
                                    }
                                    Key.Enter -> {
                                        // 高亮着候选时回车 = 采用该候选；否则才是提交搜索。
                                        val picked = visibleSuggestions.getOrNull(highlighted)
                                        if (picked != null) { onAppendTag(picked.full, null); highlighted = -1 } else onSubmit()
                                        true
                                    }
                                    else -> false
                                }
                            })
                }
                if (query.isNotEmpty()) IconButton(onClick = { session.edit(TextFieldValue()) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Clear, "清除输入", Modifier.size(18.dp)) }
                IconButton(onClick = onSubmit, enabled = query.isNotBlank() && session.draft.composition == null,
                    modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Search, "提交搜索", Modifier.size(20.dp)) }
            }
            if (conditions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    conditions.forEach { clause ->
                        SearchChip(text = translatedQuery(query.substring(clause.start, clause.end)) + " ×", onClick = {
                            val revised = SearchQueryCodec.parse(query).filter { it.start != clause.start }
                                .map { query.substring(it.start, it.end).trim() }.filter(String::isNotBlank).joinToString(",")
                            session.edit(TextFieldValue(revised, androidx.compose.ui.text.TextRange(revised.length)))
                        })
                    }
                }
            }
            session.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
            LazyColumn(state = suggestionListState, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (query.isNotBlank()) {
                        item("direct") { SearchSuggestionRow("搜索「$query」", onClick = onSubmit, searchIcon = true) }
                        items(matchedHistory, key = { "match:$it" }) { h ->
                            SearchSuggestionRow(translatedQuery(h), onClick = { onSubmitHistory(h) }, subtitle = "历史")
                        }
                        if (suggestionsLoading) item("suggestion-loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        suggestionsError?.let { error -> item("suggestion-error") {
                            Text(error, Modifier.padding(8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onOpenDictionary) { Text("词库设置", fontSize = 12.sp) }
                        } }
                        itemsIndexed(visibleSuggestions, key = { _, it -> "tag:${it.full.lowercase()}" }) { index, candidate ->
                            var menu by remember(candidate.full) { mutableStateOf(false) }
                            val active = index == highlighted
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .combinedClickable(onClick = { highlighted = index; onAppendTag(candidate.full, null) }, onLongClick = { menu = true })
                                .padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(highlight(candidate.full, inputToken), fontSize = 13.sp, lineHeight = 17.sp,
                                        color = rememberTagColor(candidate.namespace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    candidate.translatedName?.takeIf { it != candidate.full && it != candidate.tagKey }?.let {
                                        Text(highlight(it, inputToken), fontSize = 11.sp, lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                // 来源与频次必须如实标出：词库（EhTagTranslation，英文命名空间）
                                // 与服务器实际标签常常是两套平行词汇，用户得看得出哪一条是
                                // 「本库里真的有」的。全站频次也不能写成「本库 N 本」。
                                Text(candidate.sourceLabel, fontSize = 10.sp, maxLines = 1,
                                    color = if (candidate.inLibrary) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "标签操作", Modifier.size(16.dp)) }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(text = { Text(if (candidate.inLibrary) "本库 ${candidate.libraryCount} 本" else "词库候选；本库没有该标签", fontSize = 12.sp) }, enabled = false, onClick = {})
                                        if (candidate.personalCount > 0) DropdownMenuItem(text = { Text("你的历史用过 ${candidate.personalCount} 次", fontSize = 12.sp) }, enabled = false, onClick = {})
                                        DropdownMenuItem(text = { Text("包含") }, onClick = { menu = false; onAppendTag(candidate.full, false) })
                                        DropdownMenuItem(text = { Text("排除") }, onClick = { menu = false; onAppendTag(candidate.full, true) })
                                    }
                                }
                            }
                        }
                        if (suggestions.size > SUGGESTION_PREVIEW) item("suggestion-more") {
                            TextButton(onClick = { suggestionsExpanded = !suggestionsExpanded },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
                                Text(if (suggestionsExpanded) "收起" else "更多（${suggestions.size}）", fontSize = 12.sp)
                            }
                        }
                        if (!suggestionsLoading && suggestions.isEmpty() && suggestionsError == null) item("suggestion-empty") {
                            Text("暂无标签建议", Modifier.padding(8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        item("history-title") { Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                            SearchSection(if (historyPaused) "历史 · 已暂停" else if (history.isEmpty()) "历史 · 暂无" else "历史", Modifier.weight(1f))
                            if (history.isNotEmpty() && !managing && !historyHidden) IconButton(onClick = { historyExpanded = !historyExpanded }, modifier = Modifier.size(32.dp)) {
                                Icon(if (historyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    if (historyExpanded) "收起历史" else "展开历史", Modifier.size(18.dp))
                            }
                            Box {
                                IconButton(onClick = { historyMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "历史选项", Modifier.size(18.dp)) }
                                DropdownMenu(historyMenu, { historyMenu = false }) {
                                    DropdownMenuItem(text = { Text(if (historyHidden) "显示历史" else "隐藏历史") }, onClick = { historyMenu = false; onHistoryHidden(!historyHidden) })
                                    DropdownMenuItem(text = { Text(if (historyPaused) "恢复记录" else "暂停记录") }, onClick = { historyMenu = false; onHistoryPaused(!historyPaused) })
                                    if (legacyHistory.isNotEmpty()) DropdownMenuItem(text = { Text(if (legacyExpanded) "收起旧历史" else "查看旧历史") },
                                        onClick = { historyMenu = false; legacyExpanded = !legacyExpanded; onHistoryHidden(false) })
                                }
                            }
                        } }
                        if (!historyHidden) {
                            if (history.isNotEmpty()) item("history") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), maxLines = if (historyExpanded || managing) Int.MAX_VALUE else 2) {
                                    history.forEach { h ->
                                        var menu by remember(h) { mutableStateOf(false) }
                                        Box {
                                            SearchChip(translatedQuery(h), onClick = { if (managing) onRemoveHistory(h, false) else onSubmitHistory(h) },
                                                onLongClick = { menu = true }, deleting = managing)
                                            DropdownMenu(menu, { menu = false }) {
                                                DropdownMenuItem(text = { Text("编辑后搜索") }, onClick = { menu = false; session.edit(TextFieldValue(h, androidx.compose.ui.text.TextRange(h.length))) })
                                                DropdownMenuItem(text = { Text("复制") }, onClick = { menu = false; scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("搜索历史", h))) } })
                                                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onRemoveHistory(h, false) })
                                            }
                                        }
                                    }
                                }
                            }
                            if (history.isNotEmpty()) item("history-actions") {
                                /*
                                 * 历史管理入口：JHenTai 用的就是垃圾桶图标（`Icons.delete`，
                                 * search_page_mixin.dart:261-275），图标常态用 error 色；进入删除模式后
                                 * 变成 ×，「清空」与「完成」作为删除模式里的显式按钮出现。
                                 *
                                 * 与 JHenTai 的差别：它把「清空全部」藏在这个图标的长按上（:264），
                                 * 几乎不可能被发现；这里不这么做，但保留它的图标语义与位置。
                                 */
                                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.weight(1f))
                                    if (managing) {
                                        TextButton(onClick = { clearHistory = false }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("清空", fontSize = 12.sp) }
                                        TextButton(onClick = { managing = false }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("完成", fontSize = 12.sp) }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (managing) managing = false
                                            else { managing = true; historyExpanded = true; onHistoryHidden(false) }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (managing) Icons.Default.Close else Icons.Default.Delete,
                                            contentDescription = if (managing) "退出管理历史" else "管理历史",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (managing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            if (legacyHistory.isNotEmpty() && legacyExpanded) item("legacy") {
                                SearchSection("旧历史")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    legacyHistory.forEach { h -> SearchChip(translatedQuery(h),
                                        onClick = { if (managing) onRemoveHistory(h, true) else onSubmitHistory(h) }, deleting = managing) }
                                }
                                if (managing) TextButton(onClick = { clearHistory = true }) { Text("清空旧历史", fontSize = 12.sp) }
                            }
                        }
                        item("hot-title") { Row(Modifier.padding(top = 6.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                            SearchSection("热门标签", Modifier.weight(1f))
                            if (hotLoading) CircularProgressIndicator(Modifier.padding(8.dp).size(14.dp), strokeWidth = 1.5.dp)
                            Box {
                                IconButton(onClick = { hotMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "热门标签选项", Modifier.size(18.dp)) }
                                DropdownMenu(hotMenu, { hotMenu = false }) {
                                    DropdownMenuItem(text = { Text("刷新") }, enabled = !hotLoading, onClick = { hotMenu = false; onRetryHot() })
                                    DropdownMenuItem(text = { Text(if (hotLocal) "按本地标签频次排序" else "按服务器标签频次排序", fontSize = 12.sp) }, enabled = false, onClick = {})
                                    hotUpdated?.let { updated -> DropdownMenuItem(text = { Text(updated, fontSize = 11.sp) }, enabled = false, onClick = {}) }
                                    DropdownMenuItem(text = { Text("搜索帮助") }, onClick = { hotMenu = false; help = true })
                                }
                            }
                        } }
                        if (hotError != null) item("hot-error") { Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hotTags.isEmpty()) "标签暂不可用" else "暂用缓存", Modifier.weight(1f).padding(start = 8.dp),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onRetryHot) { Text("重试", fontSize = 12.sp) }
                        } }
                        if (hotTags.isNotEmpty()) {
                            item("namespaces") { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (listOf("") + hotTags.map { it.namespace.orEmpty() }.filter(String::isNotBlank).distinct()).forEach { ns ->
                                    SearchChip(if (ns.isBlank()) "全部" else TagNamespaceRegistry.descriptor(ns)?.labelZh ?: ns,
                                        onClick = { namespace = ns; hotExpanded = false }, selected = namespace == ns, outlined = true)
                                }
                            } }
                            item("hot-tags") {
                                val visible = hotTags.filter { namespace.isBlank() || it.namespace == namespace }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    visible.take(if (hotExpanded) 60 else 15).forEach { tag ->
                                        var menu by remember(tag.full) { mutableStateOf(false) }
                                        Box {
                                            SearchChip(rememberTagText(tag.namespace.orEmpty(), tag.text),
                                                onClick = { onAppendTag(tag.full, false) }, onLongClick = { menu = true })
                                            DropdownMenu(menu, { menu = false }) {
                                                DropdownMenuItem(text = { Text("${tag.full} · ${tag.weight}", fontSize = 12.sp) }, onClick = { menu = false })
                                                DropdownMenuItem(text = { Text("直接搜索") }, onClick = {
                                                    menu = false
                                                    runCatching { SearchQueryCodec.exactTag(tag.full) }.onSuccess(onSubmitHistory)
                                                        .onFailure { session.error = it.message }
                                                })
                                                DropdownMenuItem(text = { Text("排除此标签") }, onClick = { menu = false; onAppendTag(tag.full, true) })
                                            }
                                        }
                                    }
                                }
                                if (visible.size > 15) TextButton(onClick = { hotExpanded = !hotExpanded }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) { Text(if (hotExpanded) "收起" else "更多", fontSize = 12.sp) }
                            }
                        } else if (!hotLoading && hotError == null) item("hot-empty") { Text("暂无标签", Modifier.padding(8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
        }
    }
    clearHistory?.let { legacy -> AlertDialog(onDismissRequest = { clearHistory = null },
        title = { Text(if (legacy) "清空旧搜索历史？" else "清空当前范围的搜索历史？") },
        text = { Text("清空后无法恢复，其他范围的历史不受影响。") },
        confirmButton = { TextButton(onClick = { clearHistory = null; onClearHistory(legacy) }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { clearHistory = null }) { Text("取消") } }) }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("搜索标题或标签") },
        text = { Text("多个条件用逗号分隔，表示同时满足。\n\nartist:name$：精确标签\n-language:english$：排除标签\npages:>100：超过 100 页\n* 和 ?：通配符\n\n中文标签请点选联想，应用会填入原文。当前服务器不支持 ~ 运算符。") },
        confirmButton = { TextButton(onClick = { help = false }) { Text("知道了") } })
}

@Composable private fun SearchSection(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SearchChip(
    text: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    deleting: Boolean = false, selected: Boolean = false, outlined: Boolean = false,
) {
    val color = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        outlined -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color,
        modifier = Modifier.padding(vertical = 2.dp).heightIn(min = 32.dp).widthIn(max = 240.dp)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)) {
        Text((if (deleting) "× " else "") + text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SearchSuggestionRow(text: String, onClick: () -> Unit, subtitle: String? = null, searchIcon: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (searchIcon) Icon(Icons.Default.Search, null, Modifier.padding(end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun highlight(text: String, query: String): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text)
        if (query.isNotBlank()) {
            var from = text.indexOf(query, ignoreCase = true)
            while (from >= 0) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), from, from + query.length)
                from = text.indexOf(query, from + query.length, ignoreCase = true)
            }
        }
    }
}

@Composable private fun translatedQuery(query: String): String {
    val values = mutableListOf<String>()
    SearchQueryCodec.parse(query).filter { it.value.isNotBlank() }.forEach { clause ->
        if (clause.exact) {
            val namespace = if (':' in clause.value) clause.value.substringBefore(':') else ""
            val translated = rememberTagText(namespace, clause.value.substringAfter(':'))
            values += (if (clause.excluded) "排除 " else "") + translated
        } else values += query.substring(clause.start, clause.end).trim()
    }
    return values.joinToString("，")
}
