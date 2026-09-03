package com.lanraragi.reader.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ArchiveCover
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.LoadingImage
import com.lanraragi.reader.ui.TagAssistChip
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.FloatingActionBar
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.parseHexColor
import com.lanraragi.reader.ui.rememberTagColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DetailViewModel(
    private val container: AppContainer,
    private val arcid: String,
) : ViewModel() {

    data class UiState(
        val archive: Archive? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val isFavorite: Boolean = false,
        val cached: Boolean = false,
        val cacheProgress: Float? = null,
        val deleting: Boolean = false,
        val downloading: Boolean = false,
        val downloadProgress: Float? = null,
        val message: String? = null,
        val pageUrls: List<String> = emptyList(),
        val previewColumns: Int = 3,
        val previewCount: Int = 12,
        val visiblePreviewCount: Int = 12,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            container.favoritesRepository.favorites.collect { favs ->
                _state.update { it.copy(isFavorite = arcid in favs) }
            }
        }
        viewModelScope.launch {
            combine(container.offlineCache.index, container.offlineCache.progress) { idx, prog ->
                idx.items.any { it.arcid == arcid } to prog[arcid]
            }.collect { (cached, prog) ->
                _state.update { it.copy(cached = cached, cacheProgress = prog) }
            }
        }
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            _state.update {
                it.copy(
                    previewColumns = s.previewColumns,
                    previewCount = s.previewCount,
                    visiblePreviewCount = s.previewCount,
                )
            }
        }
        viewModelScope.launch {
            runCatching {
                val urls = container.repository.getPageUrls(arcid)
                _state.update { it.copy(pageUrls = urls) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val a = container.repository.getMetadata(arcid)
                _state.update { it.copy(archive = a, loading = false) }
                container.historyRepository.record(arcid, a.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { container.favoritesRepository.toggle(arcid) }
    }

    fun startCache() {
        _state.value.archive?.let { container.offlineCache.startCache(it, container.repository) }
    }

    fun deleteCache() = container.offlineCache.delete(arcid)

    fun setPreviewColumns(n: Int) {
        _state.update { it.copy(previewColumns = n) }
        viewModelScope.launch { container.settingsRepository.setPreviewColumns(n) }
    }

    fun loadMorePreview() {
        _state.update { it.copy(visiblePreviewCount = it.visiblePreviewCount + it.previewCount) }
    }

    fun deleteArchive(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            try {
                container.repository.deleteArchive(arcid)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(deleting = false, message = e.message ?: "删除失败") }
            }
        }
    }

    fun downloadArchive(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(downloading = true, downloadProgress = 0f) }
            try {
                val title = _state.value.archive?.title.orEmpty()
                val tmp = File(context.cacheDir, "$arcid.part")
                container.repository.downloadArchive(arcid, tmp) { w, t ->
                    val p = if (t != null && t > 0) w.toFloat() / t else null
                    _state.update { it.copy(downloadProgress = p) }
                }
                val name = sanitizeFileName(title.ifBlank { arcid }) + detectExtension(tmp)
                val treeUri = container.settingsRepository.settings.first().downloadDirUri

                val savedDesc = if (treeUri != null) {
                    val tree = Uri.parse(treeUri)
                    val docId = DocumentsContract.getTreeDocumentId(tree)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    val fileUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        "application/octet-stream",
                        name,
                    )
                    if (fileUri != null) {
                        context.contentResolver.openOutputStream(fileUri)?.use { out ->
                            tmp.inputStream().use { input -> input.copyTo(out) }
                        }
                        "自定义下载文件夹"
                    } else {
                        null
                    }
                } else {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "LANraragi")
                    dir.mkdirs()
                    val final = File(dir, name)
                    if (final.exists()) final.delete()
                    tmp.renameTo(final)
                    final.absolutePath
                }
                tmp.delete()

                if (savedDesc == null) {
                    _state.update { it.copy(downloading = false, downloadProgress = null, message = "写入下载文件夹失败") }
                } else {
                    _state.update { it.copy(downloading = false, downloadProgress = null, message = "已保存到 $savedDesc") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, downloadProgress = null, message = e.message ?: "下载失败") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun detectExtension(file: File): String {
        file.inputStream().use { input ->
            val b = ByteArray(8)
            val n = input.read(b)
            if (n >= 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()) return ".zip"
            if (n >= 3 && b[0] == 'R'.code.toByte() && b[1] == 'a'.code.toByte() && b[2] == 'r'.code.toByte()) return ".rar"
            if (n >= 2 && b[0] == '7'.code.toByte() && b[1] == 'z'.code.toByte()) return ".7z"
            if (n >= 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte()) return ".gz"
        }
        return ".zip"
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(container: AppContainer, arcid: String, navController: NavController) {
    val vm: DetailViewModel = viewModel { DetailViewModel(container, arcid) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val showFab = settings?.showFloatingButton ?: true
    val fabColorHex = settings?.floatingButtonColor ?: "#E94560"
    val fabColor = remember(fabColorHex) {
        parseHexColor(fabColorHex)?.let { Color(it) } ?: Color(0xFFE94560)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(title = state.archive?.title?.ifBlank { arcid } ?: "详情", onBack = { navController.popBackStack() })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                FloatingActionBar(
                    icon = Icons.Filled.PlayArrow,
                    onClick = { navController.navigate(Routes.reader(arcid)) },
                    backgroundColor = fabColor,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load, modifier = Modifier.padding(padding))
            state.archive != null -> {
                val archive = state.archive!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        ArchiveCover(
                            archive = archive,
                            modifier = Modifier
                                .width(140.dp)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(10.dp)),
                            firstPageUrl = state.pageUrls.firstOrNull(),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(archive.title.ifBlank { arcid }, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("${archive.pagecount} 页", style = MaterialTheme.typography.bodyMedium)
                            if (archive.progress > 0) {
                                Text(
                                    "已读到第 ${archive.progress} 页",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (archive.isNew) {
                                Spacer(Modifier.height(4.dp))
                                Text("新加入", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = vm::toggleFavorite, modifier = Modifier.weight(1f)) {
                            Icon(
                                if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                null,
                                tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isFavorite) "已收藏" else "收藏")
                        }
                        OutlinedButton(
                            onClick = { vm.downloadArchive(context) },
                            enabled = !state.downloading,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.downloading) {
                                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Download, null)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("下载原档")
                        }
                    }

                    if (archive.tagList.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        val translations by TagTranslationStore.translations.collectAsState()
                        val groupedTags = remember(archive.tags, translations) {
                            TagRules.groupTags(archive.tagList).map { (ns, tags) ->
                                ns to tags.sortedBy { full ->
                                    val v = TagRules.valueOf(full)
                                    (translations[ns]?.get(v) ?: v).lowercase()
                                }
                            }
                        }
                        groupedTags.forEach { (ns, tags) ->
                            Text(
                                TagRules.label(ns),
                                style = MaterialTheme.typography.titleSmall,
                                color = rememberTagColor(ns),
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tags.forEach { full ->
                                    TagAssistChip(
                                        fullTag = full,
                                        onClick = {
                                            FilterBus.filter.value = full
                                            navController.popBackStack()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (archive.summary.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("简介", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(archive.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (state.pageUrls.isNotEmpty()) {
                        var colsMenu by remember { mutableStateOf(false) }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("预览", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Box {
                                TextButton(onClick = { colsMenu = true }) { Text("${state.previewColumns}列") }
                                DropdownMenu(expanded = colsMenu, onDismissRequest = { colsMenu = false }) {
                                    (2..8).forEach { n ->
                                        DropdownMenuItem(
                                            text = { Text("$n 列") },
                                            onClick = {
                                                vm.setPreviewColumns(n)
                                                colsMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var base = 0
                        state.pageUrls.take(state.visiblePreviewCount).chunked(state.previewColumns).forEach { rowUrls ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowUrls.forEachIndexed { col, url ->
                                    val pageIndex = base + col
                                    LoadingImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.72f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { navController.navigate(Routes.reader(archive.arcid, pageIndex)) },
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                repeat(state.previewColumns - rowUrls.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            base += state.previewColumns
                        }
                        if (state.visiblePreviewCount < state.pageUrls.size) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = vm::loadMorePreview, modifier = Modifier.fillMaxWidth()) {
                                Text("预览更多")
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { navController.navigate(Routes.reader(archive.arcid)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (archive.progress > 0) "继续阅读（第 ${archive.progress} 页）" else "开始阅读")
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (state.cached) {
                            OutlinedButton(onClick = vm::deleteCache, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CloudDone, null)
                                Spacer(Modifier.width(4.dp))
                                Text("删除缓存")
                            }
                        } else {
                            OutlinedButton(onClick = vm::startCache, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CloudDownload, null)
                                Spacer(Modifier.width(4.dp))
                                Text("离线缓存")
                            }
                        }
                    }

                    if (state.cacheProgress != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.cacheProgress!! },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "缓存中 ${(state.cacheProgress!! * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除档案") },
            text = { Text("确定要从服务器删除「${state.archive?.title ?: arcid}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteArchive {
                        LibraryRefreshBus.tick.value++
                        navController.popBackStack()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}


