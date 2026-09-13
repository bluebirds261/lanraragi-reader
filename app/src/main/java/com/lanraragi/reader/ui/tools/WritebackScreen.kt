package com.lanraragi.reader.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.CanonicalTagKey
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.edgeSwipeBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============================ 向导状态模型（internal 状态机） ============================

internal enum class WritebackScopeKind(val label: String) {
    LIBRARY("全库"),
    CATEGORY("分类"),
    SINGLE("单本"),
}

internal enum class WritebackStep(val label: String) {
    SCOPE("范围"),
    SCAN("扫描"),
    PREVIEW("预览"),
    EXECUTE("回写"),
}

/**
 * 单条存储 tag 的英文词条判定结果。
 * [replacement] 为 null 表示词典未命中（无词典条目），回写时保留原文。
 */
internal data class WritebackTagRow(
    val raw: String,
    val namespace: String?,
    val value: String,
    val replacement: String?,
)

/** 待回写档案：仅收录含至少一个可翻译英文词条的档案。 */
internal data class WritebackCandidate(
    val arcid: String,
    val title: String,
    val displayName: String,
    val rows: List<WritebackTagRow>,
) {
    val translatableRows: List<WritebackTagRow> get() = rows.filter { it.replacement != null }
}

internal data class WritebackScanResult(
    val scanned: Int,
    /** X：含英文标签档案数。 */
    val archiveCount: Int,
    /** Y：可翻译词条数。 */
    val translatableCount: Int,
    /** Z：无词典条目数（英文但词典未命中）。 */
    val untranslatedCount: Int,
    val candidates: List<WritebackCandidate>,
)

internal data class WritebackLogEntry(
    val success: Boolean,
    val title: String,
    val detail: String? = null,
)

internal data class WritebackUiState(
    val step: WritebackStep = WritebackStep.SCOPE,
    val scopeKind: WritebackScopeKind = WritebackScopeKind.LIBRARY,
    val categoryId: String = "",
    val singleArcid: String = "",
    val categories: List<Category> = emptyList(),
    val categoriesLoading: Boolean = false,
    val categoriesError: String? = null,
    val scanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int? = null,
    val scanError: String? = null,
    val scan: WritebackScanResult? = null,
    /** arcid -> 勾选回写的原 tag（仅可翻译词条参与勾选）。 */
    val selected: Map<String, Set<String>> = emptyMap(),
    val executing: Boolean = false,
    val cancelled: Boolean = false,
    val execDone: Int = 0,
    val execFailed: Int = 0,
    val execTotal: Int = 0,
    val preservedCount: Int = 0,
    val logs: List<WritebackLogEntry> = emptyList(),
)

// ============================ ViewModel ============================

internal class WritebackViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val _state = MutableStateFlow(WritebackUiState())
    val state = _state.asStateFlow()

    /** 协作式取消标记：置位后执行循环在下一本档案前停止。 */
    private var executeCancelRequested = false

    fun setScopeKind(kind: WritebackScopeKind) {
        _state.update { it.copy(scopeKind = kind) }
        if (kind == WritebackScopeKind.CATEGORY &&
            _state.value.categories.isEmpty() &&
            !_state.value.categoriesLoading
        ) {
            loadCategories()
        }
    }

    fun setCategoryId(id: String) = _state.update { it.copy(categoryId = id) }

    fun setSingleArcid(value: String) = _state.update { it.copy(singleArcid = value.trim()) }

    fun loadCategories() {
        if (_state.value.categoriesLoading) return
        _state.update { it.copy(categoriesLoading = true, categoriesError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val categories = repository.getCategories()
                _state.update { it.copy(categoriesLoading = false, categories = categories) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update {
                    it.copy(categoriesLoading = false, categoriesError = e.message ?: "分类加载失败")
                }
            }
        }
    }

    /** 步骤 1 → 2：按范围扫描英文词条。 */
    fun startScan() {
        val snapshot = _state.value
        if (snapshot.scanning || snapshot.executing) return
        val scopeKind = snapshot.scopeKind
        val categoryId = snapshot.categoryId
        val singleArcid = snapshot.singleArcid
        _state.update {
            it.copy(
                step = WritebackStep.SCAN,
                scanning = true,
                scanProgress = 0,
                scanTotal = if (scopeKind == WritebackScopeKind.SINGLE) 1 else null,
                scanError = null,
                scan = null,
                selected = emptyMap(),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dict = TagTranslationStore.translations.value
                var scanned = 0
                var translatable = 0
                var untranslated = 0
                val candidates = mutableListOf<WritebackCandidate>()
                val seenArcIds = mutableSetOf<String>()

                fun ingest(archive: Archive) {
                    if (archive.arcid.isBlank() || !seenArcIds.add(archive.arcid)) return
                    scanned++
                    val rows = archive.tagList.mapNotNull { analyzeEnglishTag(it, dict) }
                    val hits = rows.count { it.replacement != null }
                    translatable += hits
                    untranslated += rows.size - hits
                    if (hits > 0) {
                        candidates += WritebackCandidate(
                            arcid = archive.arcid,
                            title = archive.title,
                            displayName = archive.displayTitle.ifBlank { archive.title.ifBlank { archive.arcid } },
                            rows = rows,
                        )
                    }
                }

                when (scopeKind) {
                    WritebackScopeKind.SINGLE -> ingest(repository.getMetadata(singleArcid))
                    else -> {
                        var offset = 0
                        while (true) {
                            val result = repository.getArchives(
                                page = offset,
                                categoryId = categoryId.takeIf { scopeKind == WritebackScopeKind.CATEGORY },
                            )
                            val items = result.items
                            if (items.isEmpty()) break
                            // 全库无筛选时服务端返回的 recordsTotal 即为精确分母；分类扫描分母未知。
                            if (offset == 0 && scopeKind == WritebackScopeKind.LIBRARY) {
                                _state.update { it.copy(scanTotal = result.total) }
                            }
                            items.forEach(::ingest)
                            offset += items.size
                            _state.update { it.copy(scanProgress = scanned) }
                            val expected = result.total
                            if (expected != null && offset >= expected) break
                        }
                    }
                }

                val distinctCandidates = candidates.distinctBy { it.arcid }
                _state.update {
                    it.copy(
                        scanning = false,
                        scanProgress = scanned,
                        scan = WritebackScanResult(
                            scanned = scanned,
                            archiveCount = distinctCandidates.size,
                            translatableCount = translatable,
                            untranslatedCount = untranslated,
                            candidates = distinctCandidates,
                        ),
                        // 默认全选所有可翻译词条。
                        selected = distinctCandidates.associate { candidate ->
                            candidate.arcid to candidate.translatableRows.map { row -> row.raw }.toSet()
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update { it.copy(scanning = false, scanError = e.message ?: "扫描失败") }
            }
        }
    }

    /** 步骤 2 → 3。 */
    fun openPreview() {
        if ((_state.value.scan?.archiveCount ?: 0) > 0) {
            _state.update { it.copy(step = WritebackStep.PREVIEW) }
        }
    }

    fun backToScope() {
        _state.update {
            WritebackUiState(
                scopeKind = it.scopeKind,
                categoryId = it.categoryId,
                singleArcid = it.singleArcid,
                categories = it.categories,
            )
        }
    }

    fun backToScan() = _state.update { it.copy(step = WritebackStep.SCAN) }

    /** 整组勾选/取消：已全选则清空，否则全选该组可翻译词条。 */
    fun toggleGroup(arcid: String) {
        _state.update { s ->
            val candidate = s.scan?.candidates?.firstOrNull { it.arcid == arcid } ?: return@update s
            val all = candidate.translatableRows.map { it.raw }
            if (all.isEmpty()) return@update s
            val current = s.selected[arcid].orEmpty()
            val next = if (all.all { it in current }) emptySet() else all.toSet()
            s.copy(selected = if (next.isEmpty()) s.selected - arcid else s.selected + (arcid to next))
        }
    }

    fun selectAll() {
        _state.update { s ->
            val scan = s.scan ?: return@update s
            s.copy(
                selected = scan.candidates
                    .filter { it.translatableRows.isNotEmpty() }
                    .associate { it.arcid to it.translatableRows.map { row -> row.raw }.toSet() },
            )
        }
    }

    fun invertSelection() {
        _state.update { s ->
            val scan = s.scan ?: return@update s
            s.copy(
                selected = scan.candidates.mapNotNull { candidate ->
                    val flipped = candidate.translatableRows
                        .map { it.raw }
                        .filterNot { it in s.selected[candidate.arcid].orEmpty() }
                    if (flipped.isEmpty()) null else candidate.arcid to flipped.toSet()
                }.toMap(),
            )
        }
    }

    /** 步骤 3 → 4：逐本 getMetadata → 合并替换 → 全量 PUT。 */
    fun startExecute() {
        val snapshot = _state.value
        val scan = snapshot.scan ?: return
        val targets = scan.candidates.mapNotNull { candidate ->
            val picked = snapshot.selected[candidate.arcid].orEmpty()
            if (picked.isEmpty()) null else candidate to picked
        }
        if (targets.isEmpty()) return
        executeCancelRequested = false
        _state.update {
            it.copy(
                step = WritebackStep.EXECUTE,
                executing = true,
                cancelled = false,
                execTotal = targets.size,
                execDone = 0,
                execFailed = 0,
                preservedCount = 0,
                logs = emptyList(),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            var failed = 0
            var preserved = 0
            var cancelled = false
            try {
                for ((index, target) in targets.withIndex()) {
                    if (executeCancelRequested) {
                        cancelled = true
                        break
                    }
                    val (candidate, picked) = target
                    try {
                        val fresh = repository.getMetadata(candidate.arcid)
                        // 勾选项按 CanonicalTag 身份（namespace+key 归一）匹配，
                        // 与扫描时刻的原始 tag 逐字对应，能容忍服务端 tag 的空白/大小写漂移。
                        val pickedIdentities = picked.mapNotNull(::parseIdentity).toSet()
                        val replacementByIdentity = candidate.translatableRows
                            .mapNotNull { row -> parseIdentity(row.raw)?.let { it to row.replacement!! } }
                            .toMap()

                        val seenIdentities = mutableSetOf<CanonicalTagKey>()
                        val seenTexts = mutableSetOf<String>()
                        val merged = mutableListOf<String>()
                        var localPreserved = 0
                        for (rawTag in fresh.tagList) {
                            val trimmed = rawTag.trim()
                            if (trimmed.isEmpty()) continue
                            val identity = parseIdentity(trimmed)
                            val replacement = if (identity != null && identity in pickedIdentities) {
                                replacementByIdentity[identity]
                            } else {
                                null
                            }
                            val outTag = replacement ?: trimmed
                            val duplicated = (identity != null && !seenIdentities.add(identity)) ||
                                !seenTexts.add(outTag.lowercase())
                            if (duplicated) continue
                            merged += outTag
                            if (replacement == null && analyzeEnglishTag(trimmed, TagTranslationStore.translations.value) != null) {
                                // 英文词条但未被勾选替换（或无词典条目）→ 保留原文。
                                localPreserved++
                            }
                        }

                        // 覆盖式 PUT：title/tags/summary 必须整体写回，防止服务端丢字段。
                        repository.updateArchiveMetadata(
                            arcid = candidate.arcid,
                            title = fresh.title.ifBlank { candidate.title },
                            tags = merged.joinToString(","),
                            summary = fresh.summary,
                        )
                        preserved += localPreserved
                        val log = WritebackLogEntry(
                            success = true,
                            title = fresh.displayTitle.ifBlank { candidate.displayName },
                        )
                        _state.update { s ->
                            s.copy(
                                execDone = index + 1,
                                preservedCount = preserved,
                                logs = (listOf(log) + s.logs).take(LOG_LIMIT),
                            )
                        }
                    } catch (cancelledEx: CancellationException) {
                        throw cancelledEx
                    } catch (e: Exception) {
                        failed++
                        val log = WritebackLogEntry(
                            success = false,
                            title = candidate.displayName,
                            detail = e.message ?: "未知错误",
                        )
                        _state.update { s ->
                            s.copy(
                                execDone = index + 1,
                                execFailed = failed,
                                logs = (listOf(log) + s.logs).take(LOG_LIMIT),
                            )
                        }
                    }
                    if (index != targets.lastIndex) delay(WRITEBACK_INTERVAL_MS)
                }
            } catch (cancelledEx: CancellationException) {
                throw cancelledEx
            } finally {
                _state.update { it.copy(executing = false, cancelled = cancelled) }
            }
        }
    }

    /** [取消]：协作式停止，当前这本完成后不再继续。 */
    fun cancelExecute() {
        executeCancelRequested = true
    }

    private fun parseIdentity(raw: String): CanonicalTagKey? =
        runCatching { CanonicalTag.parse(raw).identity }.getOrNull()

    private companion object {
        const val LOG_LIMIT = 20

        /** 逐本回写间隔。 */
        const val WRITEBACK_INTERVAL_MS = 500L
    }
}

// ============================ 词条判定与词典查询 ============================

/**
 * 判定一个存储 tag 是否为可本地化的英文词条：
 *  - 机器命名空间（source / date_added / timestamp 等注册表标记 defaultHidden 的）不参与；
 *  - 值部分须为 ASCII 可打印字符（即当前存储为英文）；
 *  - 命名空间保留原文，仅按词典翻译值部分；词典未命中时 replacement 为 null。
 * 返回 null 表示该 tag 不参与翻译流程（非英文 / 机器命名空间 / 空值）。
 */
internal fun analyzeEnglishTag(raw: String, dict: Map<String, Map<String, String>>): WritebackTagRow? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val colon = trimmed.indexOf(':')
    val namespace = if (colon > 0) trimmed.substring(0, colon).trim().takeIf(String::isNotEmpty) else null
    val value = (if (colon > 0) trimmed.substring(colon + 1) else trimmed).trim()
    if (value.isEmpty()) return null
    if (namespace != null && TagNamespaceRegistry.descriptor(namespace)?.defaultHidden == true) return null
    if (value.any { it.code !in ASCII_PRINTABLE_RANGE }) return null
    val translated = lookupTranslation(dict, namespace, value)
        ?: return WritebackTagRow(raw = trimmed, namespace = namespace, value = value, replacement = null)
    val replacement = if (namespace != null) "$namespace:$translated" else translated
    return WritebackTagRow(raw = trimmed, namespace = namespace, value = value, replacement = replacement)
}

/**
 * 词典查询（namespace(lowercase) -> tag -> 译名，与 TagChip 展示逻辑同源）。
 * 无命名空间词条回落到 EhTagTranslation 的 misc 命名空间；有命名空间时同时尝试别名归一。
 */
private fun lookupTranslation(
    dict: Map<String, Map<String, String>>,
    namespace: String?,
    value: String,
): String? {
    val namespaceKeys = if (namespace == null) {
        listOf("misc", "")
    } else {
        listOfNotNull(namespace.lowercase(), TagNamespaceRegistry.canonicalNamespace(namespace)).distinct()
    }
    namespaceKeys.forEach { key ->
        val map = dict[key] ?: return@forEach
        (map[value] ?: map[value.lowercase()])?.takeIf(String::isNotBlank)?.let { return it }
    }
    return null
}

private val ASCII_PRINTABLE_RANGE: IntRange = 0x20..0x7E

// ============================ UI ============================

/** D6 元数据中文化向导：扫描服务器英文 tag → 词典映射中文 → 覆盖式回写。 */
@Composable
fun WritebackScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: WritebackViewModel = viewModel { WritebackViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.edgeSwipeBack(onBack = onBack),
        topBar = { AppTopBar(title = "元数据中文化向导", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            StepHeader(state.step)
            when (state.step) {
                WritebackStep.SCOPE -> ScopeStep(state, vm)
                WritebackStep.SCAN -> ScanStep(state, vm)
                WritebackStep.PREVIEW -> PreviewStep(state, vm)
                WritebackStep.EXECUTE -> ExecuteStep(state, vm)
            }
        }
    }
}

@Composable
private fun StepHeader(step: WritebackStep) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        WritebackStep.entries.forEach { entry ->
            val selected = entry == step
            Text(
                text = "${entry.ordinal + 1}. ${entry.label}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ScopeStep(state: WritebackUiState, vm: WritebackViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SegmentedControl(
            tabs = WritebackScopeKind.entries.map { it.label },
            selectedTab = state.scopeKind.ordinal,
            onTabSelected = { index -> vm.setScopeKind(WritebackScopeKind.entries[index]) },
        )
        Spacer(Modifier.height(16.dp))
        when (state.scopeKind) {
            WritebackScopeKind.LIBRARY -> Text(
                "将分批扫描服务器上的全部档案，检索其中存储为英文的标签。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WritebackScopeKind.CATEGORY -> CategoryPicker(state, vm)

            WritebackScopeKind.SINGLE -> OutlinedTextField(
                value = state.singleArcid,
                onValueChange = vm::setSingleArcid,
                label = { Text("档案 ID（arcid）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        WritebackWarningCard()
        Spacer(Modifier.height(16.dp))
        val canStart = when (state.scopeKind) {
            WritebackScopeKind.LIBRARY -> true
            WritebackScopeKind.CATEGORY -> state.categoryId.isNotBlank()
            WritebackScopeKind.SINGLE -> state.singleArcid.isNotBlank()
        }
        Button(
            onClick = vm::startScan,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始扫描")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CategoryPicker(state: WritebackUiState, vm: WritebackViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedName = state.categories
        .firstOrNull { it.id == state.categoryId }
        ?.let { it.name.ifBlank { it.id } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("选择分类", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(
                onClick = { menuOpen = true },
                enabled = !state.categoriesLoading,
            ) {
                Text(
                    when {
                        state.categoriesLoading -> "加载中…"
                        selectedName != null -> selectedName
                        else -> "选择分类"
                    },
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (state.categories.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            val categoriesError = state.categoriesError
                            Text(
                                when {
                                    state.categoriesLoading -> "加载中…"
                                    categoriesError != null -> categoriesError
                                    else -> "暂无分类"
                                },
                            )
                        },
                        onClick = {},
                    )
                }
                state.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name.ifBlank { category.id }) },
                        onClick = {
                            vm.setCategoryId(category.id)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
    if (state.categoriesError != null) {
        TextButton(onClick = vm::loadCategories) {
            Text("分类加载失败，点击重试")
        }
    }
}

@Composable
private fun WritebackWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "警告",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "回写为覆盖式操作：将整体替换服务器上的标签集（标题与简介保持不变）。\n" +
                    "建议先在 设置 → 数据 备份服务器数据库。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScanStep(state: WritebackUiState, vm: WritebackViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        when {
            state.scanning -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = state.scanTotal?.let { total -> "扫描中 ${state.scanProgress}/$total" }
                        ?: "扫描中 ${state.scanProgress}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                val total = state.scanTotal
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.scanProgress.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "正在分批拉取档案标签，大库可能耗时较长。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.scanError != null -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    "扫描失败：${state.scanError}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::startScan, modifier = Modifier.fillMaxWidth()) {
                    Text("重试")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::backToScope, modifier = Modifier.fillMaxWidth()) {
                    Text("返回重新选择")
                }
            }

            state.scan != null -> {
                val scan = state.scan
                Spacer(Modifier.height(16.dp))
                Text("扫描完成（共 ${scan.scanned} 本）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StatLine("含英文标签档案", "${scan.archiveCount} 本")
                StatLine("可翻译词条", "${scan.translatableCount} 个")
                StatLine("无词典条目", "${scan.untranslatedCount} 个")
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = vm::openPreview,
                    enabled = scan.archiveCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("生成预览")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::backToScope, modifier = Modifier.fillMaxWidth()) {
                    Text("返回重新选择")
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PreviewStep(state: WritebackUiState, vm: WritebackViewModel) {
    val scan = state.scan ?: return
    val selectedArchives = state.selected.count { it.value.isNotEmpty() }
    val selectedTags = state.selected.values.sumOf { it.size }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "已选 $selectedArchives 本 · $selectedTags 条",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "无词典条目始终保留原文",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = vm::selectAll) { Text("全选") }
            TextButton(onClick = vm::invertSelection) { Text("反选") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(scan.candidates, key = { it.arcid }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    selected = state.selected[candidate.arcid].orEmpty(),
                    onToggle = { vm.toggleGroup(candidate.arcid) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = vm::backToScan,
                modifier = Modifier.weight(1f),
            ) {
                Text("上一步")
            }
            Button(
                onClick = vm::startExecute,
                enabled = selectedArchives > 0,
                modifier = Modifier.weight(2f),
            ) {
                Text("执行回写（$selectedArchives 本）")
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: WritebackCandidate,
    selected: Set<String>,
    onToggle: () -> Unit,
) {
    val translatable = candidate.translatableRows
    val allChecked = translatable.isNotEmpty() && translatable.all { it.raw in selected }
    val checkedCount = translatable.count { it.raw in selected }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allChecked,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = "勾选该档案的全部可翻译词条" },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        candidate.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$checkedCount/${translatable.size} 条已勾选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            candidate.rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.raw,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f),
                    )
                    Text(
                        "→",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    if (row.replacement != null) {
                        Text(
                            row.replacement,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            "保留原文",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecuteStep(state: WritebackUiState, vm: WritebackViewModel) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                state.executing -> "回写中 ${state.execDone}/${state.execTotal}"
                state.cancelled -> "已停止"
                else -> "执行完成"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                if (state.execTotal > 0) state.execDone.toFloat() / state.execTotal else 0f
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.executing) {
            Spacer(Modifier.height(12.dp))
            val succeeded = state.execDone - state.execFailed
            Text(
                text = (if (state.cancelled) "已取消 · " else "") +
                    "回写 $succeeded 本 · 失败 ${state.execFailed} 本 · 保留原文词条 ${state.preservedCount} 个",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "执行日志（最近 ${state.logs.size} 条）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(state.logs.size) { index ->
                val log = state.logs[index]
                Text(
                    text = if (log.success) {
                        "✓ ${log.title}"
                    } else {
                        "✗ ${log.title}：${log.detail ?: "失败"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.success) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }
        if (state.executing) {
            OutlinedButton(
                onClick = vm::cancelExecute,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        } else {
            Button(
                onClick = vm::backToScope,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回范围选择")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 三段式范围选择控件（与下载页同款样式）。 */
@Composable
private fun SegmentedControl(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
