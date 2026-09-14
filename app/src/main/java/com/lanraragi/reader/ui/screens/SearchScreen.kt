package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.model.TagStat
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
}

/*
 * ============================================================
 * 查询串的结构
 *
 * 库页最终拿到的是一条服务端 filter 串，标签用「精确匹配」写法 `namespace:value$`，
 * 多个条件用逗号连接（与 LibraryFilterContext.toServerFilter() 同口径）。
 * 因此这一页把查询串就当成「标签 token + 关键词」的集合来编辑：
 * 点标签不再立刻跳走，而是把它**追加**进查询串，攒够了再点底部「搜索」。
 * （这一范式参考 JHenTai 的搜索页：搜索框承载完整查询，联想/标签点击即追加。）
 * ============================================================
 */
private data class QueryToken(val raw: String) {
    /** 形如 `artist:foo$` 的精确标签 token。 */
    val isTag: Boolean get() = raw.endsWith("$") && raw.contains(':')

    /** 去掉结尾 `$` 的显示形态（chip 上不显示匹配符）。 */
    val display: String get() = if (isTag) raw.dropLast(1) else raw
}

private fun parseQueryTokens(query: String): List<QueryToken> =
    query.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(::QueryToken)

/** 把标签 token 追加进查询串（已存在则不重复加）。 */
private fun appendTagToken(query: String, fullTag: String): String {
    val token = "${fullTag}$"
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
        topBar = { AppTopBar(title = "搜索", onBack = { navController.popBackStack() }) },
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
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
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
                        SuggestionChip(
                            onClick = { vm.onQueryChange(removeQueryToken(state.query, token)) },
                            label = {
                                Text(
                                    rememberTagText(ns, TagRules.valueOf(token.display)),
                                    color = rememberTagColor(ns),
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除该标签",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "搜索历史",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = vm::clearHistory) { Text("清空") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.history.forEach { h ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SuggestionChip(
                                            onClick = {
                                                vm.onQueryChange(h)
                                                vm.submit(h, normalize = false)
                                                navController.popBackStack()
                                            },
                                            label = { Text(h, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        )
                                        // 单条删除：用显式 × 而不是长按（长按会与 chip 自身点击抢手势）
                                        IconButton(
                                            onClick = { vm.removeHistory(h) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "删除该历史",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
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
                            "标签联想",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            val color = rememberTagColor(ns)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { vm.onQueryChange(appendTagToken(state.query, tag.full)) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 命名空间前缀用命名空间着色，一眼区分作者/角色/原作…
                                Text(
                                    "${TagRules.label(ns)}:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = color,
                                )
                                Text(
                                    tag.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = color,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    tag.weight.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 底部动作条：左侧提示、右侧「搜索」（JHenTai 的搜索按钮位置）。 */
@Composable
private fun SearchActionBar(
    selectedTagCount: Int,
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
