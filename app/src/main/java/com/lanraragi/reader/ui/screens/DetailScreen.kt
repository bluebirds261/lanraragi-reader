package com.lanraragi.reader.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.lanraragi.reader.data.DownloadTaskType
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.data.TaskState
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.isActive
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.PluginInfo
import com.lanraragi.reader.data.model.Tankoubon
import com.lanraragi.reader.data.model.TocEntry
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ArchiveCover
import com.lanraragi.reader.ui.CategoryManagementSheet
import com.lanraragi.reader.ui.CategoryRefreshBus
import com.lanraragi.reader.ui.CoverChangeBus
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.FilterBus
import com.lanraragi.reader.ui.LibraryRefreshBus
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.LoadingImage
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.TagAssistChip
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.categoryNameError
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.isProtectedCategoryName
import com.lanraragi.reader.ui.rememberTagColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID


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
        val cacheTaskActive: Boolean = false,
        val message: String? = null,
        val pageUrls: List<String> = emptyList(),
        val coverVersion: Int = 0,
        val previewColumns: Int = 3,
        val previewCount: Int = 12,
        val visiblePreviewCount: Int = 12,
        val clearNewOnOpen: Boolean = true,
        val categories: List<Category> = emptyList(),
        val archiveCategoryIds: Set<String> = emptySet(),
        val bookmarkCategoryId: String = "",
        val categoryLoading: Boolean = false,
        val categoryBusy: Boolean = false,
        val plugins: List<PluginInfo> = emptyList(),
        val pluginsLoading: Boolean = false,
        val pluginRunning: Boolean = false,
        // A11 加入卷
        val tanks: List<Tankoubon> = emptyList(),
        val archiveTankoubonIds: Set<String> = emptySet(),
        val tankSheetLoading: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var lastArchFailed = false
    private var categoryMutationInFlight = false

    init {
        load()

        viewModelScope.launch {
            container.favoritesRepository.favorites.collect { favs ->
                _state.update {
                    it.copy(isFavorite = arcid in favs)
                }
            }
        }

        viewModelScope.launch {
            combine(
                container.offlineCache.index,
                container.offlineCache.progress,
            ) { idx, prog ->
                idx.items.any { it.arcid == arcid } to prog[arcid]
            }.collect { (cached, prog) ->
                _state.update {
                    it.copy(
                        cached = cached,
                        cacheProgress = prog,
                    )
                }
            }
        }

        viewModelScope.launch {
            container.offlineCache.lastEvicted.collect { evicted ->
                if (
                    evicted == arcid &&
                    !container.offlineCache.isCached(arcid)
                ) {
                    _state.update {
                        it.copy(
                            message = "该档案的离线缓存已被替换，请重新缓存",
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            container.downloadManager.tasks.collect { tasks ->
                val archTask =
                    tasks.lastOrNull {
                        it.type == DownloadTaskType.ARCHIVE_FILE &&
                                it.arcid == arcid
                    }

                val cacheTask =
                    tasks.lastOrNull {
                        it.type == DownloadTaskType.OFFLINE_CACHE &&
                                it.arcid == arcid
                    }

                _state.update {
                    it.copy(
                        downloading = archTask?.isActive == true,
                        downloadProgress = archTask?.progress,
                        cacheTaskActive = cacheTask?.isActive == true,
                    )
                }

                val failedTask =
                    archTask?.takeIf {
                        it.state == TaskState.FAILED
                    }

                if (
                    failedTask != null &&
                    !lastArchFailed
                ) {
                    _state.update {
                        it.copy(
                            message = failedTask.error ?: "下载失败",
                        )
                    }
                }

                lastArchFailed = failedTask != null
            }
        }

        viewModelScope.launch {
            val s =
                container.settingsRepository.settings.first()

            _state.update {
                it.copy(
                    previewColumns = s.previewColumns,
                    previewCount = s.previewCount,
                    visiblePreviewCount = s.previewCount,
                    clearNewOnOpen = s.clearNewOnOpen,
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                val urls =
                    container.repository.getPageUrls(arcid)

                _state.update {
                    it.copy(pageUrls = urls)
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                )
            }

            try {
                val a =
                    container.repository.getMetadata(arcid)

                _state.update {
                    it.copy(
                        archive = a,
                        loading = false,
                    )
                }

                container.historyRepository.record(
                    arcid,
                    a.title,
                )

                if (
                    !arcid.startsWith("local_") &&
                    a.isNew &&
                    _state.value.clearNewOnOpen
                ) {
                    try {
                        container.repository.clearArchiveNew(arcid)

                        _state.update {
                            it.copy(
                                archive =
                                    it.archive?.copy(
                                        isnew = "false",
                                    ),
                            )
                        }

                        LibraryRefreshBus.tick.value++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            container.favoritesRepository.toggle(arcid)
        }
    }

    fun loadCategories() {
        if (_state.value.categoryLoading || categoryMutationInFlight) return

        viewModelScope.launch {
            _state.update {
                it.copy(categoryLoading = true)
            }

            try {
                reloadCategoryState()
                _state.update { it.copy(categoryLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        categoryLoading = false,
                        message = e.message ?: "加载分类失败",
                    )
                }
            }
        }
    }

    fun toggleCategory(categoryId: String) {
        mutateCategory("更新分类失败", notifyLibrary = false) {
            if (categoryId in _state.value.archiveCategoryIds) {
                container.repository.removeArchiveFromCategory(categoryId, arcid)
            } else {
                container.repository.addArchiveToCategory(categoryId, arcid)
            }

            val mine = container.repository.getArchiveCategories(arcid)
            _state.update {
                it.copy(archiveCategoryIds = mine.map(Category::id).toSet())
            }
        }
    }

    fun createCategory(name: String) {
        val trimmed = name.trim()
        categoryNameError(trimmed)?.let {
            showMessage(it)
            return
        }

        mutateCategory(
            errorMessage = "新建分类失败",
            notifyLibrary = true,
            reloadAfter = true,
        ) {
            container.repository.createCategory(trimmed)
        }
    }

    fun renameCategory(categoryId: String, name: String) {
        val category = _state.value.categories.firstOrNull { it.id == categoryId }
        if (category == null) {
            showMessage("分类不存在，请刷新后重试")
            return
        }
        if (
            category.id == _state.value.bookmarkCategoryId ||
            isProtectedCategoryName(category.name)
        ) {
            showMessage("系统分类不能重命名")
            return
        }

        val trimmed = name.trim()
        categoryNameError(trimmed)?.let {
            showMessage(it)
            return
        }

        mutateCategory(
            errorMessage = "重命名分类失败",
            notifyLibrary = true,
            reloadAfter = true,
        ) {
            container.repository.renameCategory(categoryId, trimmed, category.pinned != 0)
        }
    }

    fun deleteCategory(categoryId: String) {
        val category = _state.value.categories.firstOrNull { it.id == categoryId }
        if (category == null) {
            showMessage("分类不存在，请刷新后重试")
            return
        }
        if (
            category.id == _state.value.bookmarkCategoryId ||
            isProtectedCategoryName(category.name)
        ) {
            showMessage("系统分类不能删除")
            return
        }

        mutateCategory(
            errorMessage = "删除分类失败",
            notifyLibrary = true,
            reloadAfter = true,
        ) {
            container.repository.deleteCategory(categoryId)
        }
    }

    private fun mutateCategory(
        errorMessage: String,
        notifyLibrary: Boolean = false,
        reloadAfter: Boolean = false,
        operation: suspend () -> Unit,
    ) {
        if (categoryMutationInFlight) return
        categoryMutationInFlight = true
        _state.update { it.copy(categoryBusy = true) }

        viewModelScope.launch {
            try {
                operation()
                if (reloadAfter) {
                    try {
                        reloadCategoryState()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(
                                message = e.message ?: "分类已更新，但重新加载失败",
                            )
                        }
                    }
                }
                if (notifyLibrary) CategoryRefreshBus.notifyChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: errorMessage) }
            } finally {
                categoryMutationInFlight = false
                _state.update { it.copy(categoryBusy = false) }
            }
        }
    }

    private suspend fun reloadCategoryState() {
        val all = container.repository.getCategories()
        val mine = container.repository.getArchiveCategories(arcid)
        val bookmarkCategoryId = container.repository.getBookmarkCategoryId()
        _state.update {
            it.copy(
                categories = all,
                archiveCategoryIds = mine.map(Category::id).toSet(),
                bookmarkCategoryId = bookmarkCategoryId,
            )
        }
    }

    // ---- A11 加入卷 ----

    fun loadTanks() {
        if (_state.value.tankSheetLoading) return
        viewModelScope.launch {
            _state.update { it.copy(tankSheetLoading = true) }
            try {
                val tanks = container.repository.getTankoubons()
                val ids = container.repository.getArchiveTankoubons(arcid)
                _state.update { it.copy(tanks = tanks, archiveTankoubonIds = ids.toSet(), tankSheetLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(tankSheetLoading = false, message = e.message ?: "加载单行本失败") }
            }
        }
    }

    fun toggleArchiveInTank(tankId: String) {
        viewModelScope.launch {
            try {
                if (tankId in _state.value.archiveTankoubonIds) {
                    container.repository.removeArchiveFromTankoubon(tankId, arcid)
                } else {
                    container.repository.addArchiveToTankoubon(tankId, arcid)
                }
                val ids = container.repository.getArchiveTankoubons(arcid)
                _state.update { it.copy(archiveTankoubonIds = ids.toSet()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "更新单行本失败") }
            }
        }
    }

    fun loadPlugins() {
        viewModelScope.launch {
            _state.update {
                it.copy(pluginsLoading = true)
            }

            try {
                val plugins =
                    container.repository.listPlugins("metadata")

                _state.update {
                    it.copy(
                        plugins = plugins,
                        pluginsLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        pluginsLoading = false,
                        message = "加载插件失败",
                    )
                }
            }
        }
    }

    fun runPlugin(plugin: PluginInfo) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    pluginRunning = true,
                    message = "插件运行中…",
                )
            }

            try {
                val jobid =
                    container.repository.usePluginAsync(
                        arcid,
                        plugin.namespace,
                        null,
                    )

                val job =
                    container.repository.pollJobUntilDone(jobid)

                if (job == null) {
                    _state.update {
                        it.copy(
                            message = "插件执行超时，请稍后刷新查看",
                        )
                    }
                    return@launch
                }

                val s = job.state.lowercase()

                if (
                    s.contains("fail") ||
                    s.contains("error") ||
                    s.contains("inactive") ||
                    s == "dead" ||
                    s.isBlank()
                ) {
                    val detail =
                        job.result.ifBlank {
                            job.note.ifBlank {
                                job.state
                            }
                        }

                    _state.update {
                        it.copy(
                            message = "插件执行失败：$detail",
                        )
                    }

                    return@launch
                }

                val a =
                    container.repository.getMetadata(arcid)

                _state.update {
                    it.copy(
                        archive = a,
                        message = "插件执行完成",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message = "插件执行失败：${e.message}",
                    )
                }
            } finally {
                _state.update {
                    it.copy(pluginRunning = false)
                }
            }
        }
    }

    fun startCache() {
        _state.value.archive?.let {
            container.offlineCache.startCache(
                it,
                container.repository,
            )
        }
    }

    fun deleteCache() =
        container.offlineCache.delete(arcid)

    fun setPreviewColumns(n: Int) {
        _state.update {
            it.copy(previewColumns = n)
        }

        viewModelScope.launch {
            container.settingsRepository.setPreviewColumns(n)
        }
    }

    fun loadMorePreview() {
        _state.update {
            it.copy(
                visiblePreviewCount =
                    it.visiblePreviewCount + it.previewCount,
            )
        }
    }

    fun deleteArchive(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.update {
                it.copy(deleting = true)
            }

            try {
                container.repository.deleteArchive(arcid)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        deleting = false,
                        message = e.message ?: "删除失败",
                    )
                }
            }
        }
    }

    fun downloadArchive(context: Context) {
        val archive =
            _state.value.archive ?: return

        val existing =
            container.downloadManager.taskFor(
                DownloadTaskType.ARCHIVE_FILE,
                arcid,
            )

        if (
            existing != null &&
            existing.isActive
        ) {
            return
        }

        val appContext =
            context.applicationContext

        val title =
            archive.title.ifBlank { arcid }

        container.downloadManager.enqueue(
            DownloadTaskType.ARCHIVE_FILE,
            arcid,
            title,
        ) { onProgress ->

            val tmp =
                File(
                    appContext.cacheDir,
                    "$arcid.part",
                )

            container.repository.downloadArchive(
                arcid,
                tmp,
            ) { w, t ->
                val p =
                    if (
                        t != null &&
                        t > 0
                    ) {
                        w.toFloat() / t
                    } else {
                        null
                    }

                onProgress(p)
            }

            val name =
                sanitizeFileName(title) +
                        detectExtension(tmp)

            val treeUri =
                container.settingsRepository
                    .settings
                    .first()
                    .downloadDirUri

            val savedDesc =
                if (treeUri != null) {
                    val tree =
                        Uri.parse(treeUri)

                    val docId =
                        DocumentsContract
                            .getTreeDocumentId(tree)

                    val parentUri =
                        DocumentsContract
                            .buildDocumentUriUsingTree(
                                tree,
                                docId,
                            )

                    val fileUri =
                        DocumentsContract.createDocument(
                            appContext.contentResolver,
                            parentUri,
                            "application/octet-stream",
                            name,
                        )

                    if (fileUri != null) {
                        appContext.contentResolver
                            .openOutputStream(fileUri)
                            ?.use { out ->
                                tmp.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                            }

                        "自定义下载文件夹"
                    } else {
                        null
                    }
                } else {
                    val dir =
                        File(
                            appContext.getExternalFilesDir(
                                Environment.DIRECTORY_DOWNLOADS,
                            ) ?: appContext.filesDir,
                            "LANraragi",
                        )

                    dir.mkdirs()

                    val final =
                        File(dir, name)

                    if (final.exists()) {
                        final.delete()
                    }

                    tmp.renameTo(final)

                    final.absolutePath
                }

            tmp.delete()

            if (savedDesc == null) {
                throw IllegalStateException(
                    "写入下载文件夹失败",
                )
            } else {
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            message =
                                "已保存到 $savedDesc",
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _state.update {
            it.copy(message = null)
        }
    }

    fun showMessage(msg: String) {
        _state.update {
            it.copy(message = msg)
        }
    }

    fun changeCover(page: Int) {
        viewModelScope.launch {
            try {
                container.repository.setThumbnailFromPage(
                    arcid,
                    page,
                )

                _state.update {
                    it.copy(
                        coverVersion =
                            it.coverVersion + 1,
                        message = "封面已更新",
                    )
                }

                CoverChangeBus.version.value++
                LibraryRefreshBus.tick.value++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message =
                            e.message ?: "更换封面失败",
                    )
                }
            }
        }
    }

    fun generatePageThumbnails() {
        viewModelScope.launch {
            try {
                container.repository.queuePageThumbnails(arcid)

                _state.update {
                    it.copy(
                        message =
                            "已提交生成页缩略图任务",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message =
                            e.message ?: "生成页缩略图失败",
                    )
                }
            }
        }
    }

    fun addToc(
        title: String,
        page: Int,
    ) {
        viewModelScope.launch {
            try {
                container.repository.addTocEntry(
                    arcid,
                    page.coerceAtLeast(1),
                    title,
                )

                val a =
                    container.repository.getMetadata(arcid)

                _state.update {
                    it.copy(
                        archive = a,
                        message = "目录已更新",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message =
                            e.message ?: "添加章节失败",
                    )
                }
            }
        }
    }

    fun deleteToc(page: Int) {
        viewModelScope.launch {
            try {
                container.repository.deleteTocEntry(
                    arcid,
                    page,
                )

                val a =
                    container.repository.getMetadata(arcid)

                _state.update {
                    it.copy(
                        archive = a,
                        message = "目录已更新",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message =
                            e.message ?: "删除章节失败",
                    )
                }
            }
        }
    }

    private fun detectExtension(file: File): String {
        file.inputStream().use { input ->
            val b = ByteArray(8)
            val n = input.read(b)

            if (
                n >= 2 &&
                b[0] == 'P'.code.toByte() &&
                b[1] == 'K'.code.toByte()
            ) {
                return ".zip"
            }

            if (
                n >= 3 &&
                b[0] == 'R'.code.toByte() &&
                b[1] == 'a'.code.toByte() &&
                b[2] == 'r'.code.toByte()
            ) {
                return ".rar"
            }

            if (
                n >= 2 &&
                b[0] == '7'.code.toByte() &&
                b[1] == 'z'.code.toByte()
            ) {
                return ".7z"
            }

            if (
                n >= 2 &&
                b[0] == 0x1f.toByte() &&
                b[1] == 0x8b.toByte()
            ) {
                return ".gz"
            }
        }

        return ".zip"
    }

    private fun sanitizeFileName(
        name: String,
    ): String =
        name
            .replace(
                Regex("[\\\\/:*?\"<>|]"),
                "_",
            )
            .trim()
            .take(120)
}


/* ============================================================
 * Glass overlay
 * ============================================================ */

private data class GlassSlot(
    val id: String,
    val position: IntOffset = IntOffset.Zero,
    val size: IntSize = IntSize.Zero,
    val enabled: Boolean,
    val height: Dp,
    val horizontalPadding: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val blurRadius: Dp,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val onClick: () -> Unit,
    val content: @Composable RowScope.() -> Unit,
)

private class GlassOverlayController {
    val slots =
        mutableStateMapOf<String, GlassSlot>()

    fun put(slot: GlassSlot) {
        slots[slot.id] = slot
    }

    fun updatePosition(
        id: String,
        position: IntOffset,
        size: IntSize,
    ) {
        val old = slots[id] ?: return

        if (
            old.position == position &&
            old.size == size
        ) {
            return
        }

        slots[id] =
            old.copy(
                position = position,
                size = size,
            )
    }

    fun remove(id: String) {
        slots.remove(id)
    }
}

private val LocalGlassOverlayController =
    staticCompositionLocalOf<GlassOverlayController?> {
        null
    }


@Composable
private fun GlassOverlayHost(
    controller: GlassOverlayController,
    modifier: Modifier = Modifier,
) {
    var rootSize by
    remember {
        mutableStateOf(IntSize.Zero)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    rootSize = it.size
                }
                .zIndex(100f),
    ) {
        controller.slots.values.forEach { slot ->
            if (
                slot.size.width > 0 &&
                slot.size.height > 0
            ) {
                val left = slot.position.x
                val top = slot.position.y
                val right =
                    left + slot.size.width
                val bottom =
                    top + slot.size.height

                val visible =
                    rootSize.width > 0 &&
                            rootSize.height > 0 &&
                            right > 0 &&
                            bottom > 0 &&
                            left < rootSize.width &&
                            top < rootSize.height

                if (visible) {
                    key(slot.id) {
                        LiquidGlassVisual(
                            onClick = slot.onClick,
                            enabled = slot.enabled,
                            height = slot.height,
                            horizontalPadding =
                                slot.horizontalPadding,
                            lensHeight = slot.lensHeight,
                            lensAmount = slot.lensAmount,
                            blurRadius = slot.blurRadius,
                            chromaticAberration =
                                slot.chromaticAberration,
                            depthEffect = slot.depthEffect,
                            modifier =
                                Modifier
                                    .absoluteOffset {
                                        slot.position
                                    }
                                    .width(
                                        with(
                                            androidx.compose.ui.platform.LocalDensity.current,
                                        ) {
                                            slot.size.width.toDp()
                                        },
                                    )
                                    .height(
                                        with(
                                            androidx.compose.ui.platform.LocalDensity.current,
                                        ) {
                                            slot.size.height.toDp()
                                        },
                                    ),
                            content = slot.content,
                        )
                    }
                }
            }
        }
    }
}


/* ============================================================
 * 实际液态玻璃按钮
 * ============================================================ */

@Composable
private fun LiquidGlassVisual(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    lensHeight: Dp = 0.dp,
    lensAmount: Dp = 0.dp,
    blurRadius: Dp = 0.dp,
    chromaticAberration: Boolean = true,
    depthEffect: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val pressed by
    interactionSource.collectIsPressedAsState()

    var pressPosition by
    remember {
        mutableStateOf(
            Offset(
                Float.NaN,
                Float.NaN,
            ),
        )
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressPosition =
                        interaction.pressPosition
                }

                is PressInteraction.Release -> {
                    pressPosition =
                        Offset(
                            Float.NaN,
                            Float.NaN,
                        )
                }

                is PressInteraction.Cancel -> {
                    pressPosition =
                        Offset(
                            Float.NaN,
                            Float.NaN,
                        )
                }
            }
        }
    }

    val hasPressPosition =
        pressPosition.x.isFinite() &&
                pressPosition.y.isFinite()

    val pressProgress by
    animateFloatAsState(
        targetValue =
            if (pressed && enabled) {
                1f
            } else {
                0f
            },
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy,
                stiffness =
                    Spring.StiffnessMediumLow,
            ),
        label = "glassPress",
    )

    val surface =
        MaterialTheme.colorScheme.surface

    val onSurface =
        MaterialTheme.colorScheme.onSurface

    Row(
        modifier =
            modifier
                .height(height)
                .graphicsLayer {
                    val p = pressProgress

                    scaleX =
                        1f -
                                0.035f * p

                    scaleY =
                        1f +
                                0.018f * p

                    translationX =
                        if (
                            hasPressPosition &&
                            size.width > 0f
                        ) {
                            (
                                    pressPosition.x /
                                            size.width -
                                            0.5f
                                    ) * 3f.dp.toPx() * p
                        } else {
                            0f
                        }

                    translationY =
                        if (
                            hasPressPosition &&
                            size.height > 0f
                        ) {
                            (
                                    pressPosition.y /
                                            size.height -
                                            0.5f
                                    ) * 3f.dp.toPx() * p
                        } else {
                            0f
                        }

                    alpha =
                        if (enabled) {
                            1f
                        } else {
                            0.5f
                        }
                }
                .drawBackdrop(
                    backdrop =
                        LocalDetailBackdrop.current
                            ?: error(
                                "LiquidGlassVisual requires DetailScreen backdrop",
                            ),

                    shape = {
                        Capsule()
                    },

                    effects = {
                        if (blurRadius > 0.dp) {
                            blur(
                                blurRadius.toPx() *
                                        (1f - 0.25f * pressProgress),
                            )
                        }

                        if (
                            lensHeight > 0.dp ||
                            lensAmount > 0.dp
                        ) {
                            lens(
                                refractionHeight =
                                    10.dp.toPx(),

                                refractionAmount =
                                    (
                                            lensAmount.value *
                                                    (1f + 0.5f * pressProgress)
                                            ).dp.toPx(),

                                depthEffect = depthEffect,
                                chromaticAberration = chromaticAberration,
                            )
                        }
                    },

                    onDrawBackdrop = { drawBackdrop ->

                        val p =
                            pressProgress

                        if (p <= 0f) {
                            drawBackdrop()
                        } else {
                            val cx =
                                if (hasPressPosition) {
                                    pressPosition.x
                                } else {
                                    size.width / 2f
                                }

                            val cy =
                                if (hasPressPosition) {
                                    pressPosition.y
                                } else {
                                    size.height / 2f
                                }

                            val nx =
                                (
                                        cx /
                                                size.width.coerceAtLeast(
                                                    1f,
                                                )
                                        ).coerceIn(0f, 1f) -
                                        0.5f

                            val ny =
                                (
                                        cy /
                                                size.height.coerceAtLeast(
                                                    1f,
                                                )
                                        ).coerceIn(0f, 1f) -
                                        0.5f

                            val scaleX =
                                1f -
                                        0.13f * p

                            val scaleY =
                                1f +
                                        0.075f * p

                            val translateX =
                                -nx *
                                        18f *
                                        p

                            val translateY =
                                -ny *
                                        14f *
                                        p

                            translate(
                                translateX,
                                translateY,
                            ) {
                                scale(
                                    scaleX = scaleX,
                                    scaleY = scaleY,
                                    pivot =
                                        Offset(
                                            cx,
                                            cy,
                                        ),
                                ) {
                                    drawBackdrop()
                                }
                            }
                        }
                    },

                    onDrawSurface = {
                    },
                )
                .clickable(
                    enabled = enabled,
                    interactionSource =
                        interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(
                    horizontal = horizontalPadding,
                ),

        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally,
            ),

        verticalAlignment =
            Alignment.CenterVertically,

        content = content,
    )
}


private val LocalDetailBackdrop =
    staticCompositionLocalOf<Backdrop?> {
        null
    }


@Composable
private fun LiquidGlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    lensHeight: Dp = 1.dp,
    lensAmount: Dp = 1.dp,
    blurRadius: Dp = 1.dp,
    chromaticAberration: Boolean = true,
    depthEffect: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val controller =
        LocalGlassOverlayController.current

    if (controller == null) {
        CompositionLocalProvider(
            LocalDetailBackdrop provides backdrop,
        ) {
            LiquidGlassVisual(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                height = height,
                horizontalPadding = horizontalPadding,
                lensHeight = lensHeight,
                lensAmount = lensAmount,
                blurRadius = blurRadius,
                chromaticAberration =
                    chromaticAberration,
                depthEffect = depthEffect,
                content = content,
            )
        }

        return
    }

    val id =
        remember {
            UUID.randomUUID().toString()
        }

    val slot =
        GlassSlot(
            id = id,
            enabled = enabled,
            height = height,
            horizontalPadding =
                horizontalPadding,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            blurRadius = blurRadius,
            chromaticAberration =
                chromaticAberration,
            depthEffect = depthEffect,
            onClick = onClick,
            content = content,
        )

    SideEffect {
        controller.put(slot)
    }

    DisposableEffect(id) {
        onDispose {
            controller.remove(id)
        }
    }

    Box(
        modifier =
            modifier
                .height(height)
                .widthIn(min = 64.dp)
                .onGloballyPositioned { coordinates ->
                    controller.updatePosition(
                        id = id,
                        position =
                            coordinates
                                .positionInRoot()
                                .let {
                                    IntOffset(
                                        it.x.toInt(),
                                        it.y.toInt(),
                                    )
                                },
                        size = coordinates.size,
                    )
                },
    )
}


@Composable
private fun SafeGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    horizontalPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop =
        rememberLayerBackdrop()

    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val pressed by
    interactionSource.collectIsPressedAsState()

    val pressProgress by
    animateFloatAsState(
        targetValue =
            if (pressed && enabled) {
                1f
            } else {
                0f
            },
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy,
                stiffness =
                    Spring.StiffnessMediumLow,
            ),
        label = "safeGlassPress",
    )

    val glassSurface =
        MaterialTheme.colorScheme.surface

    Box(
        modifier =
            modifier.height(height),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    glassSurface.copy(alpha = 0.96f),
                    Capsule(),
                )
                .layerBackdrop(backdrop),
        )

        Row(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX =
                        1f -
                                0.035f *
                                pressProgress

                    scaleY =
                        1f +
                                0.015f *
                                pressProgress

                    alpha =
                        if (enabled) {
                            1f
                        } else {
                            0.5f
                        }
                }
                .clip(Capsule())
                .background(Color.Transparent)
                .clickable(
                    enabled = enabled,
                    interactionSource =
                        interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(
                    horizontal = horizontalPadding,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                    Alignment.CenterHorizontally,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
            content = content,
        )
    }
}


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun DetailScreen(
    container: AppContainer,
    arcid: String,
    navController: NavController,
) {
    val vm: DetailViewModel =
        viewModel {
            DetailViewModel(
                container,
                arcid,
            )
        }

    val state by
    vm.state.collectAsStateWithLifecycle()

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    var showDeleteDialog by
    remember {
        mutableStateOf(false)
    }

    var showCategorySheet by
    remember {
        mutableStateOf(false)
    }

    var showCoverMenu by
    remember {
        mutableStateOf(false)
    }

    var showCoverPicker by
    remember {
        mutableStateOf(false)
    }

    var showPluginsSheet by
    remember {
        mutableStateOf(false)
    }

    var showTankSheet by
    remember {
        mutableStateOf(false)
    }

    var showTocSheet by
    remember {
        mutableStateOf(false)
    }

    /*
     * “编辑”按钮自己的菜单状态。
     *
     * 编辑目录、服务器插件、删除三个功能
     * 都从这里进入。
     */
    var showEditMenu by
    remember {
        mutableStateOf(false)
    }

    val context =
        LocalContext.current

    val settings by
    container.settingsRepository.settings
        .collectAsStateWithLifecycle(
            initialValue = null,
        )

    val showFab =
        settings?.showFloatingButton ?: true

    val detailBackdrop =
        rememberLayerBackdrop()

    val glassController =
        remember {
            GlassOverlayController()
        }

    /*
     * =========================================================
     * 详情页滚动状态
     *
     * 用于实现预览区域接近底部时自动加载下一批图片。
     * =========================================================
     */
    val scrollState =
        rememberScrollState()

    LaunchedEffect(
        scrollState,
        state.visiblePreviewCount,
        state.pageUrls.size,
    ) {
        snapshotFlow {
            scrollState.value to scrollState.maxValue
        }.collect { (value, maxValue) ->
            if (
                maxValue > 0 &&
                value >= maxValue - 800 &&
                state.visiblePreviewCount < state.pageUrls.size
            ) {
                vm.loadMorePreview()
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(showCategorySheet) {
        if (showCategorySheet) {
            vm.loadCategories()
        }
    }

    LaunchedEffect(showPluginsSheet) {
        if (showPluginsSheet) {
            vm.loadPlugins()
        }
    }

    Box(
        Modifier.fillMaxSize(),
    ) {

        CompositionLocalProvider(
            LocalGlassOverlayController provides
                    glassController,
            LocalDetailBackdrop provides
                    detailBackdrop,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(detailBackdrop),
            ) {
                Scaffold(
                    modifier =
                        Modifier.edgeSwipeBack {
                            navController.popBackStack()
                        },

                    topBar = {
                        AppTopBar(
                            title =
                                state.archive
                                    ?.title
                                    ?.ifBlank { arcid }
                                    ?: "详情",
                            onBack = {
                                navController.popBackStack()
                            },
                        )
                    },

                    snackbarHost = {
                        SnackbarHost(snackbarHostState)
                    },
                ) { padding ->

                    when {
                        state.loading -> {
                            LoadingBox(
                                Modifier.padding(padding),
                            )
                        }

                        state.error != null -> {
                            ErrorBox(
                                state.error!!,
                                onRetry = vm::load,
                                modifier =
                                    Modifier.padding(padding),
                            )
                        }

                        state.archive != null -> {
                            val archive =
                                state.archive!!

                            val coverUrl =
                                ApiClient.thumbnailUrl(arcid) +
                                        if (state.coverVersion > 0) {
                                            "?v=${state.coverVersion}"
                                        } else {
                                            ""
                                        }

                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .verticalScroll(scrollState)
                                    .padding(16.dp),
                            ) {

                                Row(
                                    Modifier.fillMaxWidth(),
                                ) {
                                    Box {
                                        ArchiveCover(
                                            archive = archive,
                                            modifier =
                                                Modifier
                                                    .width(140.dp)
                                                    .aspectRatio(0.72f)
                                                    .clip(
                                                        RoundedCornerShape(
                                                            10.dp,
                                                        ),
                                                    )
                                                    .combinedClickable(
                                                        onClick = {},
                                                        onLongClick = {
                                                            showCoverMenu =
                                                                true
                                                        },
                                                    ),
                                            firstPageUrl = coverUrl,
                                        )

                                        DropdownMenu(
                                            expanded =
                                                showCoverMenu,
                                            onDismissRequest = {
                                                showCoverMenu = false
                                            },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "更换封面（选页）",
                                                    )
                                                },
                                                onClick = {
                                                    showCoverMenu = false

                                                    if (
                                                        state.pageUrls
                                                            .isEmpty()
                                                    ) {
                                                        vm.showMessage(
                                                            "暂无可选页",
                                                        )
                                                    } else {
                                                        showCoverPicker =
                                                            true
                                                    }
                                                },
                                            )

                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "生成页缩略图",
                                                    )
                                                },
                                                onClick = {
                                                    showCoverMenu = false
                                                    vm.generatePageThumbnails()
                                                },
                                            )
                                        }
                                    }

                                    Spacer(
                                        Modifier.width(16.dp),
                                    )

                                    Column(
                                        Modifier.weight(1f),
                                    ) {
                                        Text(
                                            archive.title
                                                .ifBlank { arcid },
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .titleMedium,
                                        )

                                        Spacer(
                                            Modifier.height(8.dp),
                                        )

                                        Text(
                                            "${archive.pagecount} 页",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodyMedium,
                                        )

                                        if (archive.progress > 0) {
                                            Text(
                                                "已读到第 ${archive.progress} 页",
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodyMedium,
                                                color =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .primary,
                                            )
                                        }

                                        if (archive.isNew) {
                                            Spacer(
                                                Modifier.height(4.dp),
                                            )

                                            Text(
                                                "新加入",
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelMedium,
                                                color =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .primary,
                                            )
                                        }
                                    }
                                }

                                if (state.pluginRunning) {
                                    Spacer(
                                        Modifier.height(8.dp),
                                    )

                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier
                                                .width(14.dp)
                                                .height(14.dp),
                                            strokeWidth = 2.dp,
                                        )

                                        Spacer(
                                            Modifier.width(6.dp),
                                        )

                                        Text(
                                            "插件运行中…",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .labelMedium,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .primary,
                                        )
                                    }
                                }

                                Spacer(
                                    Modifier.height(12.dp),
                                )

                                /*
                                 * =====================================================
                                 * 第一排操作按钮
                                 *
                                 * 收藏 | 下载 | 分类 | 编辑
                                 *
                                 * 编辑按钮内部包含：
                                 *   - 编辑目录
                                 *   - 运行服务器插件
                                 *   - 删除
                                 * =====================================================
                                 */
                                Row(
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp),
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                ) {
                                    LiquidGlassButton(
                                        backdrop =
                                            detailBackdrop,
                                        onClick =
                                            vm::toggleFavorite,
                                        modifier =
                                            Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            if (state.isFavorite) {
                                                Icons.Filled.Favorite
                                            } else {
                                                Icons.Filled.FavoriteBorder
                                            },
                                            contentDescription = null,
                                            tint =
                                                if (state.isFavorite) {
                                                    MaterialTheme
                                                        .colorScheme
                                                        .primary
                                                } else {
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSurface
                                                },
                                        )

                                        Text(
                                            if (state.isFavorite) {
                                                ""
                                            } else {
                                                ""
                                            },
                                        )
                                    }

                                    LiquidGlassButton(
                                        backdrop =
                                            detailBackdrop,
                                        onClick = {
                                            if (state.downloading) {
                                                navController.navigate(
                                                    Routes.MAIN,
                                                )
                                            } else {
                                                vm.downloadArchive(context)
                                            }
                                        },
                                        modifier =
                                            Modifier.weight(1f),
                                    ) {
                                        if (state.downloading) {
                                            CircularProgressIndicator(
                                                Modifier
                                                    .width(18.dp)
                                                    .height(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = null,
                                            )
                                        }

                                        Text(
                                            when {
                                                state.downloading &&
                                                        state.downloadProgress != null ->
                                                    " ${(state.downloadProgress!! * 100).toInt()}%"

                                                state.downloading ->
                                                    ""

                                                else ->
                                                    ""
                                            },
                                        )
                                    }

                                    LiquidGlassButton(
                                        backdrop =
                                            detailBackdrop,
                                        onClick = {
                                            showCategorySheet = true
                                        },
                                        modifier =
                                            Modifier.weight(1f),
                                    ) {
                                        Text("分类")
                                    }

                                    /*
                                     * 编辑按钮
                                     *
                                     * 与收藏、下载、分类保持同一排。
                                     */
                                    Box(
                                        Modifier.weight(1f),
                                    ) {
                                        LiquidGlassButton(
                                            backdrop =
                                                detailBackdrop,
                                            onClick = {
                                                showEditMenu = true
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                        ) {
                                            Text("编辑")
                                        }

                                        DropdownMenu(
                                            expanded = showEditMenu,
                                            onDismissRequest = {
                                                showEditMenu = false
                                            },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text("编辑目录")
                                                },
                                                onClick = {
                                                    showEditMenu = false
                                                    showTocSheet = true
                                                },
                                            )

                                            DropdownMenuItem(
                                                text = {
                                                    Text("运行服务器插件")
                                                },
                                                onClick = {
                                                    showEditMenu = false
                                                    showPluginsSheet = true
                                                },
                                            )

                                            DropdownMenuItem(
                                                text = {
                                                    Text("加入卷")
                                                },
                                                onClick = {
                                                    showEditMenu = false
                                                    vm.loadTanks()
                                                    showTankSheet = true
                                                },
                                            )

                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "删除",
                                                        color =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .error,
                                                    )
                                                },
                                                onClick = {
                                                    showEditMenu = false
                                                    showDeleteDialog = true
                                                },
                                            )
                                        }
                                    }
                                }

                                if (archive.tagList.isNotEmpty()) {
                                    Spacer(
                                        Modifier.height(16.dp),
                                    )

                                    val translations by
                                    TagTranslationStore
                                        .translations
                                        .collectAsState()

                                    val groupedTags =
                                        remember(
                                            archive.tags,
                                            translations,
                                        ) {
                                            TagRules
                                                .groupTags(
                                                    archive.tagList,
                                                )
                                                .map { (ns, tags) ->
                                                    ns to
                                                            tags.sortedBy { full ->
                                                                val v =
                                                                    TagRules.valueOf(
                                                                        full,
                                                                    )

                                                                (
                                                                        translations[
                                                                            ns
                                                                        ]?.get(v)
                                                                            ?: v
                                                                        ).lowercase()
                                                            }
                                                }
                                        }

                                    groupedTags.forEach { (ns, tags) ->
                                        Text(
                                            TagRules.label(ns),
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .titleSmall,
                                            color =
                                                rememberTagColor(ns),
                                            modifier =
                                                Modifier.padding(
                                                    top = 10.dp,
                                                    bottom = 4.dp,
                                                ),
                                        )

                                        FlowRow(
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp),
                                        ) {
                                            tags.forEach { full ->
                                                TagAssistChip(
                                                    fullTag = full,
                                                    onClick = {
                                                        FilterBus
                                                            .filter
                                                            .value = full

                                                        navController
                                                            .popBackStack()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                if (archive.summary.isNotBlank()) {
                                    Spacer(
                                        Modifier.height(16.dp),
                                    )

                                    Text(
                                        "简介",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleSmall,
                                    )

                                    Spacer(
                                        Modifier.height(4.dp),
                                    )

                                    Text(
                                        archive.summary,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant,
                                    )
                                }

                                if (state.pageUrls.isNotEmpty()) {
                                    var colsMenu by
                                    remember {
                                        mutableStateOf(false)
                                    }

                                    Spacer(
                                        Modifier.height(16.dp),
                                    )

                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "预览",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .titleSmall,
                                            modifier =
                                                Modifier.weight(1f),
                                        )

                                        Box {
                                            LiquidGlassButton(
                                                backdrop =
                                                    detailBackdrop,
                                                onClick = {
                                                    colsMenu = true
                                                },
                                                modifier =
                                                    Modifier.height(40.dp),
                                                height = 40.dp,
                                                horizontalPadding = 12.dp,
                                            ) {
                                                Text(
                                                    "${state.previewColumns}列",
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = colsMenu,
                                                onDismissRequest = {
                                                    colsMenu = false
                                                },
                                            ) {
                                                (2..8).forEach { n ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text("$n 列")
                                                        },
                                                        onClick = {
                                                            vm.setPreviewColumns(
                                                                n,
                                                            )
                                                            colsMenu = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(
                                        Modifier.height(8.dp),
                                    )

                                    var base = 0

                                    state.pageUrls
                                        .take(
                                            state.visiblePreviewCount,
                                        )
                                        .chunked(
                                            state.previewColumns,
                                        )
                                        .forEach { rowUrls ->

                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        bottom = 8.dp,
                                                    ),
                                                horizontalArrangement =
                                                    Arrangement.spacedBy(
                                                        8.dp,
                                                    ),
                                            ) {
                                                rowUrls.forEachIndexed {
                                                        col,
                                                        url,
                                                    ->
                                                    val pageIndex =
                                                        base + col

                                                    LoadingImage(
                                                        model = url,
                                                        contentDescription =
                                                            null,
                                                        modifier =
                                                            Modifier
                                                                .weight(1f)
                                                                .aspectRatio(
                                                                    0.72f,
                                                                )
                                                                .clip(
                                                                    RoundedCornerShape(
                                                                        6.dp,
                                                                    ),
                                                                )
                                                                .clickable {
                                                                    navController
                                                                        .navigate(
                                                                            Routes.reader(
                                                                                archive.arcid,
                                                                                pageIndex,
                                                                            ),
                                                                        )
                                                                },
                                                        contentScale =
                                                            androidx.compose.ui.layout.ContentScale.Crop,
                                                    )
                                                }

                                                repeat(
                                                    state.previewColumns -
                                                            rowUrls.size,
                                                ) {
                                                    Spacer(
                                                        Modifier.weight(1f),
                                                    )
                                                }
                                            }

                                            base +=
                                                state.previewColumns
                                        }
                                }

                                /*
                                 * =====================================================
                                 * 已移除详情页原来的三个独立入口：
                                 *
                                 * 1. 开始阅读 / 继续阅读
                                 * 2. 离线缓存 / 删除缓存
                                 * 3. 删除
                                 *
                                 * 其中编辑目录、运行服务器插件、删除
                                 * 现在统一从“编辑”按钮进入。
                                 *
                                 * ViewModel 中的功能全部保留。
                                 * =====================================================
                                 */

                                Spacer(
                                    Modifier.height(96.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        /*
         * =========================================================
         * Overlay
         * =========================================================
         */
        CompositionLocalProvider(
            LocalDetailBackdrop provides
                    detailBackdrop,
        ) {
            GlassOverlayHost(
                controller = glassController,
            )
        }

        /*
         * =========================================================
         * FAB
         *
         * 右下角开始阅读入口保留。
         * =========================================================
         */
        if (
            showFab &&
            state.archive != null
        ) {
            CompositionLocalProvider(
                LocalDetailBackdrop provides
                        detailBackdrop,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(110f),
                ) {
                    LiquidGlassVisual(
                        onClick = {
                            navController.navigate(
                                Routes.reader(arcid),
                            )
                        },
                        height = 64.dp,
                        horizontalPadding = 20.dp,
                        lensHeight = 32.dp,
                        lensAmount = 64.dp,
                        blurRadius = 4.dp,
                        chromaticAberration = true,
                        depthEffect = true,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 16.dp,
                                    bottom = 16.dp,
                                )
                                .widthIn(min = 150.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "开始阅读",
                        )

                        Text(
                            "开始阅读",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelLarge,
                        )
                    }
                }
            }
        }

        if (showCategorySheet) {
            CategoryManagementSheet(
                categories = state.categories,
                selectedCategoryIds = state.archiveCategoryIds,
                protectedCategoryIds = setOf(state.bookmarkCategoryId).filter(String::isNotBlank).toSet(),
                loading = state.categoryLoading,
                busy = state.categoryBusy,
                onToggle = vm::toggleCategory,
                onCreate = vm::createCategory,
                onRename = vm::renameCategory,
                onDelete = vm::deleteCategory,
                onValidationError = vm::showMessage,
                onDismiss = {
                    showCategorySheet = false
                },
            )
        }

        if (showCoverPicker) {
            CoverPickerSheet(
                arcid = arcid,
                pageCount = state.pageUrls.size,
                onPick = { page ->
                    vm.changeCover(page)
                    showCoverPicker = false
                },
                onDismiss = {
                    showCoverPicker = false
                },
            )
        }

        if (showPluginsSheet) {
            PluginsSheet(
                plugins = state.plugins,
                loading = state.pluginsLoading,
                onRun = { plugin ->
                    showPluginsSheet = false
                    vm.runPlugin(plugin)
                },
                onDismiss = {
                    showPluginsSheet = false
                },
            )
        }

        // A11
        if (showTankSheet) {
            TankSheet(
                tanks = state.tanks,
                archiveTankoubonIds = state.archiveTankoubonIds,
                loading = state.tankSheetLoading,
                onToggle = vm::toggleArchiveInTank,
                onDismiss = { showTankSheet = false },
            )
        }

        if (showTocSheet) {
            TocEditSheet(
                toc =
                    state.archive?.toc
                        ?: emptyList(),
                onDelete = vm::deleteToc,
                onAdd = vm::addToc,
                onDismiss = {
                    showTocSheet = false
                },
            )
        }

        /*
         * 删除功能本身保留。
         *
         * 现在的入口是：
         * 编辑 → 删除 → 删除确认弹窗
         */
        if (showDeleteDialog) {
            DeleteArchiveDialog(
                title =
                    state.archive?.title
                        ?: arcid,
                onConfirm = {
                    showDeleteDialog = false

                    vm.deleteArchive {
                        LibraryRefreshBus.tick.value++
                        navController.popBackStack()
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginsSheet(
    plugins: List<PluginInfo>,
    loading: Boolean,
    onRun: (PluginInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "服务器插件",
                style =
                    MaterialTheme.typography.titleMedium,
            )

            Spacer(
                Modifier.height(8.dp),
            )

            when {
                loading && plugins.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                plugins.isEmpty() -> {
                    Text(
                        "暂无可用插件",
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                vertical = 8.dp,
                            ),
                    )
                }

                else -> {
                    plugins.forEach { p ->
                        val name =
                            p.name.ifBlank {
                                p.namespace
                            }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRun(p)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                name,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge,
                            )

                            if (
                                p.name.isNotBlank() &&
                                p.namespace.isNotBlank()
                            ) {
                                Spacer(
                                    Modifier.height(2.dp),
                                )

                                Text(
                                    p.namespace,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverPickerSheet(
    arcid: String,
    pageCount: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "选择封面页",
                style =
                    MaterialTheme.typography.titleMedium,
            )

            Spacer(
                Modifier.height(12.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
            ) {
                items(pageCount) { index ->
                    LoadingImage(
                        model =
                            ApiClient.pageThumbnailUrl(
                                arcid,
                                index,
                            ),
                        contentDescription =
                            "第 ${index + 1} 页",
                        modifier =
                            Modifier
                                .aspectRatio(0.72f)
                                .clip(
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    onPick(index + 1)
                                },
                        contentScale =
                            androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocEditSheet(
    toc: List<TocEntry>,
    onDelete: (Int) -> Unit,
    onAdd: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberModalBottomSheetState()

    var showAddDialog by
    remember {
        mutableStateOf(false)
    }

    val backdrop =
        rememberLayerBackdrop()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CompositionLocalProvider(
            LocalDetailBackdrop provides backdrop,
            LocalGlassOverlayController provides null,
        ) {
            Box(
                Modifier.fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surface,
                        )
                        .layerBackdrop(backdrop),
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState(),
                        )
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                ) {
                    Text(
                        "编辑目录",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                    )

                    Spacer(
                        Modifier.height(8.dp),
                    )

                    if (toc.isEmpty()) {
                        Text(
                            "暂无目录",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            modifier =
                                Modifier.padding(
                                    vertical = 8.dp,
                                ),
                        )
                    } else {
                        toc.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                ) {
                                    Text(
                                        entry.name.ifBlank {
                                            "未命名章节"
                                        },
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyLarge,
                                    )

                                    Text(
                                        "第 ${entry.page} 页",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .labelSmall,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant,
                                    )
                                }

                                LiquidGlassButton(
                                    backdrop = backdrop,
                                    onClick = {
                                        onDelete(entry.page)
                                    },
                                    height = 36.dp,
                                    horizontalPadding = 12.dp,
                                    lensHeight = 18.dp,
                                    lensAmount = 36.dp,
                                    blurRadius = 2.dp,
                                    chromaticAberration = true,
                                    depthEffect = true,
                                ) {
                                    Text(
                                        "删除",
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .error,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp),
                    )

                    LiquidGlassButton(
                        backdrop = backdrop,
                        onClick = {
                            showAddDialog = true
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text("添加章节")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTocDialog(
            onConfirm = { title, page ->
                showAddDialog = false
                onAdd(title, page)
            },
            onDismiss = {
                showAddDialog = false
            },
        )
    }
}


@Composable
private fun DeleteArchiveDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("删除档案")
        },

        text = {
            Text(
                "确定要从服务器删除「$title」吗？此操作不可恢复。",
            )
        },

        confirmButton = {
            SafeGlassButton(
                onClick = onConfirm,
            ) {
                Text(
                    "删除",
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                )
            }
        },

        dismissButton = {
            SafeGlassButton(
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}


@Composable
private fun AddTocDialog(
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by
    remember {
        mutableStateOf("")
    }

    var page by
    remember {
        mutableStateOf("")
    }

    val pageNum =
        page.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("添加章节")
        },

        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text("章节标题")
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth(),
                )

                Spacer(
                    Modifier.height(8.dp),
                )

                OutlinedTextField(
                    value = page,
                    onValueChange = {
                        page =
                            it.filter { c ->
                                c.isDigit()
                            }
                    },
                    label = {
                        Text("起始页码")
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number,
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                )
            }
        },

        confirmButton = {
            TextButton(
                onClick = {
                    if (
                        title.isNotBlank() &&
                        pageNum > 0
                    ) {
                        onConfirm(
                            title.trim(),
                            pageNum,
                        )
                    }
                },
                enabled =
                    title.isNotBlank() &&
                            pageNum > 0,
            ) {
                Text("添加")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

// ---- A11 加入卷弹层 ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TankSheet(
    tanks: List<Tankoubon>,
    archiveTankoubonIds: Set<String>,
    loading: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("加入单行本", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (tanks.isEmpty()) {
                Text(
                    "暂无单行本\n请在导航页「单行本」中创建",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tanks.forEach { t ->
                        FilterChip(
                            selected = t.id in archiveTankoubonIds,
                            onClick = { onToggle(t.id) },
                            label = { Text(t.name) },
                        )
                    }
                }
            }
        }
    }
}
