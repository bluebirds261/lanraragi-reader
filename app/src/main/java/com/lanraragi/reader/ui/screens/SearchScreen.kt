package com.lanraragi.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val query: String = "",
        val tags: List<TagStat> = emptyList(),
        val tagsLoading: Boolean = false,
        val history: List<String> = emptyList(),
        val suggestions: List<TagStat> = emptyList(),
        /** D4 词典归一化映射（输入 token → 库内 tag），供联想区映射 chips 展示。 */
        val mappings: List<Pair<String, String>> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** 输入框内容（驱动防抖补全，与 query 同步更新）。 */
    private val queryInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            container.searchHistoryRepository.history.collect { h ->
                _state.update { it.copy(history = h) }
            }
        }
        viewModelScope.launch {
            queryInput
                .drop(1)
                .debounce(250)
                .distinctUntilChanged()
                .collect { q -> updateSuggestions(q) }
        }
    }

    /** Room 词库负责中英文/namespace 排序，服务器标签在词库未覆盖时补齐；同时生成 D4 词典映射。 */
    private suspend fun updateSuggestions(q: String) {
        val query = q.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(suggestions = emptyList(), mappings = emptyList()) }
            return
        }
        val mappings = try {
            container.tagTranslationRepository.normalizeSearchPreview(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        _state.update { it.copy(mappings = mappings) }
        val lower = query.lowercase()
        val namespaceQuery = TagNamespaceRegistry.canonicalNamespace(lower)
        val ranked = _state.value.tags.mapNotNull { tag ->
            val namespace = TagNamespaceRegistry.canonicalNamespace(tag.namespace).orEmpty()
            val rank = when {
                // 0 = 标签值前缀命中，1 = 命名空间前缀命中，2 = 完整标签前缀命中
                tag.text.lowercase().startsWith(lower) -> 0
                !namespaceQuery.isNullOrEmpty() && namespace.startsWith(namespaceQuery) -> 1
                tag.full.lowercase().startsWith(lower) -> 2
                else -> null
            } ?: return@mapNotNull null
            rank to tag
        }
        val serverSuggestions = ranked
            .sortedWith(
                compareBy<Pair<Int, TagStat>> { it.first }
                    .thenByDescending {
                        TagNamespaceRegistry.descriptor(it.second.namespace)?.completionWeight ?: 0
                    }
                    .thenByDescending { it.second.weight }
                    .thenBy { it.second.full.lowercase() },
            )
            .take(10)
            .map { it.second }
        val knowledgeSuggestions = try {
            container.tagKnowledgeRepository.suggestions(query, limit = 10).map { suggestion ->
                TagStat(
                    namespace = suggestion.entry.namespace.takeIf(String::isNotBlank),
                    text = suggestion.entry.tagKey,
                    weight = suggestion.frequency.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val suggestions = (knowledgeSuggestions + serverSuggestions)
            .distinctBy { it.full.lowercase() }
            .take(10)
        _state.update { it.copy(suggestions = suggestions) }
    }

    fun onQueryChange(v: String) {
        _state.update { it.copy(query = v) }
        queryInput.value = v
    }

    fun loadTags() {
        if (_state.value.tags.isNotEmpty() || _state.value.tagsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(tagsLoading = true) }
            try {
                val tags = container.repository.getTags()
                _state.update { it.copy(tags = tags, tagsLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(tagsLoading = false) }
            }
        }
    }

    /**
     * 提交搜索：[normalize] 为 true 时先经 D4 词典归一化（中文译名/别名 → 库内原文 tag，
     * 英文原文 → 中文库目标形态）再写回 SearchBus；词库不可用时回退原文。
     * 已是库内形态的查询（含标签 token）传 normalize = false 直接提交。
     */
    fun submit(query: String, normalize: Boolean = true) {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            container.searchHistoryRepository.add(q)
            SearchBus.query.value = if (!normalize) q else try {
                container.tagTranslationRepository.normalizeSearchQuery(q)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                q
            }
        }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { container.searchHistoryRepository.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.searchHistoryRepository.clear() }
    }

    /** 清空筛选并重新搜索（对应 JHenTai 搜索框左侧图标「清空并刷新」）。 */
    fun clearAndSearch() {
        SearchBus.query.value = ""
    }
}

/*
 * ============================================================
 * 查询串的结构
 *
 * 库页最终拿到的是一条服务端 filter 串。LANraragi 的解析规则（Model/Search.pm:400-469，
 * 从右往左逐 token）支持三种写法，这一页全部用上：
 *   - `ns:value$`       精确匹配（`$` 结尾 = isexact）
 *   - `ns:"value$"`     引号包裹的精确匹配（JHenTai 的写法，服务端同样接受）
 *   - `-ns:value$`      排除（前导 `-` = isneg）
 * 因此点标签不再立刻跳走，而是把它**追加**进查询串，攒够了再点底部「搜索」。
 * （这一范式参考 JHenTai 的搜索页：搜索框承载完整查询，联想/标签点击即追加。）
 * ============================================================
 */
private data class QueryToken(val raw: String) {
    /** 是否为「精确匹配」的标签 token（`ns:value$` 或 `ns:"value$"`，可带前导 `-`）。 */
    val isTag: Boolean
        get() {
            val body = raw.removePrefix("-")
            return body.replace("\"", "").endsWith("$") && body.contains(':')
        }

    /** 是否为排除项（前导 `-`）。 */
    val isExcluded: Boolean get() = raw.startsWith("-")

    /** 显示形态：去掉 `-` 与引号、去掉结尾 `$`，chip 上只留 `ns:value`。 */
    val display: String
        get() = raw.removePrefix("-")
            .replace("\"", "")
            .removeSuffix("$")
}

private fun parseQueryTokens(query: String): List<QueryToken> =
    query.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(::QueryToken)

/** 标签 token 的两种形态（引号形式与 JHenTai 一致）：`ns:"value$"`，排除时前缀 `-`。 */
private fun tagToken(fullTag: String, excluded: Boolean): String {
    val prefix = if (excluded) "-" else ""
    val colon = fullTag.indexOf(':')
    if (colon < 0) return "$prefix\"$fullTag\$\""
    val ns = fullTag.substring(0, colon)
    val value = fullTag.substring(colon + 1)
    return "$prefix$ns:\"$value\$\""
}

/** 把标签 token 追加进查询串（已存在则不重复加）。 */
private fun appendTagToken(query: String, fullTag: String, excluded: Boolean = false): String {
    val token = tagToken(fullTag, excluded)
    val tokens = parseQueryTokens(query)
    if (tokens.any { it.raw == token }) return query
    return (tokens.map { it.raw } + token).joinToString(",")
}

/** 从查询串里移除某个 token。 */
private fun removeQueryToken(query: String, token: QueryToken): String =
    parseQueryTokens(query)
        .filterNot { it.raw == token.raw }
        .joinToString(",") { it.raw }

/**
 * 是否属于「机器元数据」标签（日期 / 时间戳一类）。
 *
 * 这些标签每个档案都带，weight 天然极高，放进「热门标签」只会霸屏；
 * 命名空间可能被中文化或自定义，所以命名空间名与「值形如日期」两条都判。
 */
private val METADATA_NAMESPACES = setOf(
    "date", "dates", "date_added", "timestamp", "timestamps", "temp", "temporary",
    "日期", "添加日期", "时间戳", "临时",
)

private val DATE_LIKE = Regex("""^\d{4}-\d{2}-\d{2}([ T].*)?$""")

private fun TagStat.isMetadataTag(): Boolean {
    val ns = namespace?.trim()?.lowercase().orEmpty()
    return ns in METADATA_NAMESPACES || DATE_LIKE.matches(text.trim())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(container: AppContainer, navController: NavController) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // 当前浏览的命名空间："" = 全部（展示热门标签）
    var namespace by remember { mutableStateOf("") }

    // 历史区三种模式（对齐 JHenTai）：删除模式 / 隐藏历史 / 历史译名
    var deleteMode by remember { mutableStateOf(false) }
    var hideHistory by remember { mutableStateOf(false) }
    var translateHistory by remember { mutableStateOf(false) }

    // 排除模式：进入后点击标签/联想追加的是 `-ns:"value$"`（服务端 isneg 语义）
    var excludeMode by remember { mutableStateOf(false) }

    // 词库是否就绪（决定是否显示「历史译名」开关，与 JHenTai 的 tagTranslationService.isReady 同义）
    val translations by TagTranslationStore.translations.collectAsStateWithLifecycle()
    val dictionaryReady = translations.isNotEmpty()

    val tokens = remember(state.query) { parseQueryTokens(state.query) }
    val selectedTags = remember(tokens) { tokens.filter { it.isTag } }
    val keywordOnly = remember(tokens) { tokens.none { it.isTag } }

    fun commit() {
        val q = state.query.trim()
        if (q.isEmpty()) return
        // 纯关键词仍走词典归一化（D4）；含标签 token 时已是库内形态，直接提交。
        vm.submit(q, normalize = keywordOnly)
        navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(
                title = "搜索",
                onBack = { navController.popBackStack() },
                // 对应 JHenTai 顶栏的 filter 动作：这里先用它承载「排除模式」开关
                // （搜索配置/快捷搜索的等价物是库页私有的 FilterSheet/PresetPanel，
                //  需要一起提升为共享组件，留待与结果页一并处理）。
                actions = {
                    IconButton(onClick = { excludeMode = !excludeMode }) {
                        Icon(
                            if (excludeMode) Icons.Filled.Block else Icons.Outlined.Block,
                            contentDescription = if (excludeMode) "退出排除模式" else "排除模式",
                            tint =
                                if (excludeMode) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                },
            )
        },
        bottomBar = {
            SearchActionBar(
                selectedTagCount = selectedTags.size,
                enabled = state.query.isNotBlank(),
                onSearch = ::commit,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ============================================================
            // 查询框：承载「标签 token + 关键词」的完整查询串
            // ============================================================
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("关键词或点选下方标签…") },
                // JHenTai：已选标签以浮动 label 的形式挂在搜索框上（` / ` 分隔）
                label =
                    if (selectedTags.isNotEmpty()) {
                        {
                            Text(
                                selectedTags.joinToString(" / ") { it.display },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        null
                    },
                // JHenTai：点搜索图标 = 清空条件并重新搜索（这里即回到库页的未筛选状态）
                leadingIcon = {
                    IconButton(
                        onClick = {
                            vm.onQueryChange("")
                            vm.clearAndSearch()
                            navController.popBackStack()
                        },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "清空条件并搜索")
                    }
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { vm.loadTags() }

            // ============================================================
            // 已选标签：查询串里的标签 token，逐个可摘掉
            // ============================================================
            if (selectedTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    selectedTags.forEach { token ->
                        val ns = TagRules.nsOf(token.display)
                        val excluded = token.isExcluded
                        // 已选标签也用 EHTag 造型（与历史 chips 同一套视觉）
                        EhTag(
                            text =
                                (if (excluded) "-" else "") +
                                    rememberTagText(ns, TagRules.valueOf(token.display)),
                            color = if (excluded) MaterialTheme.colorScheme.error else rememberTagColor(ns),
                            background = MaterialTheme.colorScheme.surfaceVariant,
                            inDeleteMode = true,
                            onClick = { vm.onQueryChange(removeQueryToken(state.query, token)) },
                        )
                    }
                }
            }

            // ============================================================
            // 命名空间横排：一次只看一类标签（JHenTai 的标签页做法）
            //
            // 命名空间以**服务器实际返回值**为准，而不是内置词表：LANraragi 的标签
            // 命名空间可以被中文化/自定义（本库就是「作者 / 原作 / 角色」这种中文命名空间），
            // 只按英文词表列出来的话，点进去会是空的。词表能解析出中文名就用中文名，
            // 否则原样显示服务器返回的命名空间。
            // ============================================================
            val namespaceOptions = remember(state.tags) {
                val counts = state.tags
                    .mapNotNull { it.namespace?.trim()?.takeIf(String::isNotBlank) }
                    .groupingBy { it }
                    .eachCount()
                buildList {
                    add("" to "全部")
                    counts.entries
                        .sortedWith(
                            compareByDescending<Map.Entry<String, Int>> { it.value }
                                .thenBy { it.key },
                        )
                        .forEach { (ns, _) ->
                            val canonical = TagNamespaceRegistry.canonicalNamespace(ns)
                            val label = (canonical?.let { TagNamespaceRegistry.descriptor(it)?.labelZh })
                                ?: ns
                            add(ns to label)
                        }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                namespaceOptions.forEach { (ns, label) ->
                    FilterChip(
                        selected = namespace == ns,
                        onClick = { namespace = ns },
                        label = {
                            Text(
                                label,
                                color = if (ns.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    rememberTagColor(ns)
                                },
                            )
                        },
                    )
                }
            }

            val query = state.query.trim()

            // 面板标签：命名空间过滤 + 按热度排序（全部时只取前 60 个当「热门」）。
            // 必须在 LazyColumn 之外算好 —— LazyColumn 的 content 是 LazyListScope，
            // 不是 @Composable 作用域，里面不能调用 remember。
            val panelTags = remember(state.tags, namespace) {
                val visible = state.tags
                    .filterNot {
                        TagNamespaceRegistry.descriptor(it.namespace)?.defaultHidden == true
                    }
                    .filter { namespace.isEmpty() || it.namespace == namespace }
                if (namespace.isEmpty()) {
                    /*
                     * 「全部」= 热门：按使用热度排，并**排除日期/时间戳这类机器元数据**——
                     * 它们每个档案都有、weight 极高，不排除的话热门面板全是「2026-09-03」。
                     * 这里按标签自身判断（命名空间名 + 值形如日期），因此对中文命名空间
                     * 或自定义命名空间的库同样有效。
                     */
                    visible
                        .filterNot { it.isMetadataTag() }
                        .sortedWith(
                            compareByDescending<TagStat> { it.weight }
                                .thenBy { it.full.lowercase() },
                        )
                        .take(60)
                } else {
                    visible.sortedWith(
                        compareByDescending<TagStat> { it.weight }
                            .thenBy { it.full.lowercase() },
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (query.isEmpty()) {
                    // --------------------------------------------------------
                    // 空查询：搜索历史 + 热门/命名空间标签面板
                    // --------------------------------------------------------
                    if (state.history.isNotEmpty()) {
                        item(key = "history") {
                            SearchHistorySection(
                                history = state.history,
                                hideHistory = hideHistory,
                                deleteMode = deleteMode,
                                translateHistory = translateHistory,
                                dictionaryReady = dictionaryReady,
                                onToggleHide = { hideHistory = !hideHistory },
                                onToggleDeleteMode = { deleteMode = !deleteMode },
                                onToggleTranslate = { translateHistory = !translateHistory },
                                onClearAll = vm::clearHistory,
                                onSearch = { h ->
                                    vm.onQueryChange(h)
                                    vm.submit(h, normalize = false)
                                    navController.popBackStack()
                                },
                                onAppend = { h ->
                                    // JHenTai：长按历史 = 追加进搜索框（而不是立刻搜）
                                    val base = state.query.trimEnd()
                                    vm.onQueryChange(if (base.isEmpty()) h else "$base $h")
                                },
                                onRemove = vm::removeHistory,
                            )
                        }
                    }

                    item(key = "panel-header") {
                        Text(
                            if (namespace.isEmpty()) {
                                "热门标签（点选追加到查询）"
                            } else {
                                "${TagRules.label(namespace)} · ${panelTags.size} 个（点选追加）"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (state.tagsLoading && state.tags.isEmpty()) {
                        item(key = "panel-loading") {
                            Text(
                                "加载中…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (panelTags.isEmpty()) {
                        item(key = "panel-empty") {
                            /*
                             * 服务器标签索引为空时的说明。
                             *
                             * LANraragi 的 `/api/database/stats` 读的是 Redis `LRR_STATS`
                             * 有序集合（`Model/Stats.pm::build_tag_stats`），它由服务端在
                             * 扫描/重建索引时写入。若该集合没有内容（例如库是直接拷进去的、
                             * 或 Redis 被清过），这里就只能拿到极少数标签 —— 页面会显得"没有标签"。
                             * 这属于服务端数据状态，APP 侧给出可执行的排查指引。
                             */
                            Text(
                                "服务器没有返回可用的标签统计。\n" +
                                    "LANraragi 的标签索引（Redis LRR_STATS）需要在服务端重建一次：" +
                                    "到「设置 → 连接 → 立即重扫服务器文件夹」跑一次重扫，" +
                                    "或在服务器端重建统计后再回来。\n" +
                                    "在此之前可以直接在上方输入框里输入关键词或标签搜索。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // 两列等宽 chips：一屏能看到更多标签
                        items(
                            items = panelTags.chunked(2),
                            key = { row -> row.first().full },
                        ) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { tag ->
                                    val ns = TagNamespaceRegistry.canonicalNamespace(tag.namespace).orEmpty()
                                    TagCell(
                                        label = rememberTagText(ns, tag.text),
                                        color = rememberTagColor(ns),
                                        modifier = Modifier.weight(1f),
                                        onClick = { vm.onQueryChange(appendTagToken(state.query, tag.full)) },
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // --------------------------------------------------------
                    // 有输入：关键词直接搜索 + 词典映射 + 联想（点选追加）
                    // --------------------------------------------------------
                    item(key = "direct") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { commit() }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "直接搜索「$query」",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (state.mappings.isNotEmpty()) {
                        item(key = "mappings") {
                            Text(
                                "词典映射（点选追加库内标签）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.mappings.forEach { (input, normalized) ->
                                    val ns = TagRules.nsOf(normalized)
                                    SuggestionChip(
                                        onClick = { vm.onQueryChange(appendTagToken(state.query, normalized)) },
                                        label = { Text("$input → ${rememberTagText(ns, TagRules.valueOf(normalized))}") },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "suggestions-header") {
                        Text(
                            if (excludeMode) "标签联想（点选=排除，长按=包含）" else "标签联想（点选=包含，长按=排除）",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (excludeMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (state.suggestions.isEmpty()) {
                        item(key = "no-suggestion") {
                            Text(
                                if (state.tagsLoading) "加载中…" else "没有匹配的标签，可直接搜索关键词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.suggestions, key = { it.full }) { tag ->
                            val ns = TagNamespaceRegistry.canonicalNamespace(tag.namespace).orEmpty()
                            SuggestionRow(
                                namespace = ns.ifBlank { tag.namespace.orEmpty() },
                                value = tag.text,
                                translation = rememberTagText(ns, tag.text),
                                query = state.query,
                                weight = tag.weight,
                                excludeMode = excludeMode,
                                onAppend = { excluded ->
                                    vm.onQueryChange(appendTagToken(state.query, tag.full, excluded))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * EHTag：JHenTai 的标签胶囊（`widget/eh_tag.dart`）。
 *
 * 造型逐项对齐：**高 24 / 圆角 8 / 水平内边距 6 / 垂直 3 / 字号 12 / 行高 1**，
 * 背景为命名空间底色（这里用调用方给的颜色淡版），文字用命名空间色。
 * 删除模式下在右侧**弹入**一个圆形 × 徽章（JHenTai 用 AnimatedSwitcher，这里用
 * AnimatedVisibility + scaleIn，观感一致）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EhTag(
    text: String,
    color: Color,
    background: Color,
    inDeleteMode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 12.sp),
            color = color,
        )
        AnimatedVisibility(
            visible = inDeleteMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/**
 * 搜索历史区（对齐 JHenTai 的 `buildSearchHistory` + `buildButtons`）：
 * 一行为历史 chips（点击=直接再搜、长按=追加进搜索框、删除模式下点击=删除），
 * 右上角是三个动作：历史译名开关（词库就绪时才有）、隐藏/删除模式切换（长按=清空全部）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySection(
    history: List<String>,
    hideHistory: Boolean,
    deleteMode: Boolean,
    translateHistory: Boolean,
    dictionaryReady: Boolean,
    onToggleHide: () -> Unit,
    onToggleDeleteMode: () -> Unit,
    onToggleTranslate: () -> Unit,
    onClearAll: () -> Unit,
    onSearch: (String) -> Unit,
    onAppend: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    // 历史条目没有命名空间，用统一的胶囊底色；译名按词库直查（与 TagChip 的查法一致）。
    val chipBackground = MaterialTheme.colorScheme.surfaceVariant
    val chipText = MaterialTheme.colorScheme.onSurface
    val translations by TagTranslationStore.translations.collectAsStateWithLifecycle()

    fun displayOf(raw: String): String {
        if (!translateHistory || !dictionaryReady) return raw
        // 历史可能整条是关键词（含空格），逐 token 尝试词库直查，命中就替换。
        return raw.split(' ').joinToString(" ") { word ->
            val ns = word.substringBefore(':', "")
            val value = word.substringAfter(':', word)
            translations[ns]?.get(value)?.takeIf { it.isNotBlank() } ?: word
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "搜索历史",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = !hideHistory && dictionaryReady) {
                IconButton(onClick = onToggleTranslate) {
                    Icon(
                        Icons.Filled.Translate,
                        contentDescription = if (translateHistory) "取消历史译名" else "历史显示译名",
                        modifier = Modifier.size(20.dp),
                        tint =
                            if (translateHistory) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
            IconButton(onClick = if (hideHistory) onToggleHide else onToggleDeleteMode) {
                Icon(
                    when {
                        hideHistory -> Icons.Filled.Visibility
                        deleteMode -> Icons.Filled.Close
                        else -> Icons.Filled.DeleteOutline
                    },
                    contentDescription = when {
                        hideHistory -> "显示历史"
                        deleteMode -> "退出删除模式"
                        else -> "删除历史"
                    },
                    modifier = Modifier.size(20.dp),
                    tint = if (deleteMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClearAll) { Text("清空") }
        }
        // JHenTai 用 AnimatedSwitcher + SizeTransition 折叠整个历史区
        AnimatedVisibility(visible = !hideHistory, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                history.forEach { h ->
                    EhTag(
                        text = displayOf(h),
                        color = chipText,
                        background = chipBackground,
                        inDeleteMode = deleteMode,
                        onClick = { if (deleteMode) onRemove(h) else onSearch(h) },
                        onLongClick = if (deleteMode) null else ({ onAppend(h) }),
                    )
                }
            }
        }
    }
}

/**
 * 联想行（对齐 JHenTai 的 `buildSuggestions`）：
 * 标题是**高亮命中片段**的原始标签、副标题是译名（同样高亮），
 * 点击把 `ns:"value$"` 追加进查询框，**长按追加排除形式** `-ns:"value$"`。
 * 逐行淡入（JHenTai 用 FadeIn 400ms）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionRow(
    namespace: String,
    value: String,
    translation: String,
    query: String,
    weight: Int,
    excludeMode: Boolean,
    onAppend: (Boolean) -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val nsColor = rememberTagColor(namespace)
    val keyword = query.trim().substringAfterLast(' ').substringAfterLast(',').removePrefix("-").replace("\"", "")

    // 命中片段高亮：在“命名空间:值”与译名里各找一次关键字
    fun highlighted(text: String, color: Color): AnnotatedString {
        val start = if (keyword.isBlank()) -1 else text.indexOf(keyword, ignoreCase = true)
        return if (start < 0) {
            AnnotatedString(text, SpanStyle(color = color))
        } else {
            buildAnnotatedString {
                withStyle(SpanStyle(color = color)) { append(text.substring(0, start)) }
                withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
                    append(text.substring(start, start + keyword.length))
                }
                withStyle(SpanStyle(color = color)) { append(text.substring(start + keyword.length)) }
            }
        }
    }

    // 逐行淡入
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(durationMillis = 400)) }

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(alpha.value)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onAppend(excludeMode) },
                onLongClick = { onAppend(!excludeMode) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = secondary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlighted("$namespace:$value", nsColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (translation.isNotBlank() && translation != value) {
                Text(
                    highlighted(translation, secondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            weight.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = secondary,
        )
    }
}

/** 底部动作条：左侧提示、右侧「搜索」（JHenTai 的搜索按钮位置）。 */
@Composable
private fun SearchActionBar(    selectedTagCount: Int,
    enabled: Boolean,
    onSearch: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selectedTagCount > 0) "已选 $selectedTagCount 个标签" else "输入关键词或点选标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSearch, enabled = enabled) { Text("搜索") }
        }
    }
}

/** 面板里的标签小格：等宽、命名空间着色。 */
@Composable
private fun TagCell(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
