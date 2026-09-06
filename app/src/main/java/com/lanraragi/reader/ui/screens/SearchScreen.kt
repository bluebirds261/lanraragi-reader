package com.lanraragi.reader.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText
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

    /** 防抖后按输入内容对本地标签做前缀匹配补全（最多 10 条）。 */
    private fun updateSuggestions(q: String) {
        val query = q.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        val lower = query.lowercase()
        val ranked = _state.value.tags.mapNotNull { tag ->
            val rank = when {
                // 0 = 标签值前缀命中，1 = 命名空间前缀命中，2 = 完整标签前缀命中
                tag.text.lowercase().startsWith(lower) -> 0
                (tag.namespace ?: "").lowercase().startsWith(lower) -> 1
                tag.full.lowercase().startsWith(lower) -> 2
                else -> null
            } ?: return@mapNotNull null
            rank to tag
        }
        val suggestions = ranked
            .sortedWith(
                compareBy<Pair<Int, TagStat>> { it.first }
                    .thenByDescending { it.second.weight }
                    .thenBy { it.second.full.lowercase() },
            )
            .take(10)
            .map { it.second }
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
            runCatching { container.repository.getTags() }
                .onSuccess { tags -> _state.update { it.copy(tags = tags, tagsLoading = false) } }
                .onFailure { _state.update { it.copy(tagsLoading = false) } }
        }
    }

    fun submit(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            container.searchHistoryRepository.add(q)
            SearchBus.query.value = q
        }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { container.searchHistoryRepository.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.searchHistoryRepository.clear() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(container: AppContainer, navController: NavController) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        if (state.query.isBlank()) return
        vm.submit(state.query)
        navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = { AppTopBar(title = "搜索", onBack = { navController.popBackStack() }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("搜索标题或标签…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Clear, "清除") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            LaunchedEffect(Unit) { vm.loadTags() }

            val q = state.query.trim()
            if (q.isEmpty()) {
                val groups = remember(state.tags) {
                    val byNs = LinkedHashMap<String, MutableList<TagStat>>()
                    state.tags.sortedBy { it.full.lowercase() }.forEach { t ->
                        val ns = t.namespace?.lowercase() ?: ""
                        if (ns == "source") return@forEach
                        byNs.getOrPut(ns) { mutableListOf() }.add(t)
                    }
                    val ordered = mutableListOf<Pair<String, List<TagStat>>>()
                    TagRules.NAMESPACE_ORDER.forEach { ns -> byNs.remove(ns)?.let { ordered += ns to it } }
                    byNs.entries.filter { it.key.isNotEmpty() }.sortedBy { it.key }
                        .forEach { (ns, list) -> ordered += ns to list }
                    byNs[""]?.let { ordered += "" to it }
                    ordered
                }
                val hot = remember(state.tags) {
                    state.tags.sortedByDescending { it.weight }.take(30)
                }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    if (state.history.isNotEmpty()) {
                        item(key = "history") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("搜索历史", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = vm::clearHistory) { Text("清除") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.history.forEach { h ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SuggestionChip(
                                            onClick = {
                                                vm.onQueryChange(h)
                                                vm.submit(h)
                                                navController.popBackStack()
                                            },
                                            label = { Text(h) },
                                        )
                                        IconButton(
                                            onClick = { vm.removeHistory(h) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "hot-header") {
                        Text(
                            "热门标签",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }
                    item(key = "hot") {
                        if (state.tagsLoading && state.tags.isEmpty()) {
                            Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                hot.forEach { tag ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            vm.submit(tag.full)
                                            navController.popBackStack()
                                        },
                                        label = { Text(rememberTagText(TagRules.nsOf(tag.full), TagRules.valueOf(tag.full)), color = rememberTagColor(TagRules.nsOf(tag.full))) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "filter-header") {
                        Text(
                            "标签筛选（点击按标签过滤）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }
                    if (state.tags.isEmpty() && state.tagsLoading) {
                        item(key = "filter-loading") {
                            Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        groups.forEach { (ns, list) ->
                            item(key = "header-$ns") {
                                Text(
                                    TagRules.label(ns),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = rememberTagColor(ns),
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }
                            item(key = "tags-$ns") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    list.forEach { tag ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                FilterBus.filter.value = tag.full
                                                navController.popBackStack()
                                            },
                                            label = { Text(rememberTagText(ns, tag.text), color = rememberTagColor(ns)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 联想（由 ViewModel 防抖前缀补全生成）
                val suggestions = state.suggestions
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(
                        "联想（点击搜索）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { tag ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    vm.submit(tag.full)
                                    navController.popBackStack()
                                },
                                label = { Text(tag.full, color = rememberTagColor(TagRules.nsOf(tag.full))) },
                            )
                        }
                    }
                    if (suggestions.isEmpty() && state.tags.isNotEmpty()) {
                        Text(
                            "没有匹配的标签，按回车直接搜索关键词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
