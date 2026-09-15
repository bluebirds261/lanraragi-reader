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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.style.TextDecoration
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.lanraragi.reader.data.DownloadTaskType
import com.lanraragi.reader.data.PageThumbQueue
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.data.TaskState
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.isActive
import com.lanraragi.reader.data.download.DownloadDestination
import com.lanraragi.reader.data.download.DownloadSourceIdentity
import com.lanraragi.reader.data.download.DownloadTaskSpec
import com.lanraragi.reader.data.download.SavedArtifactExporter
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.PluginInfo
import com.lanraragi.reader.data.model.Tankoubon
import com.lanraragi.reader.data.model.TocEntry
import com.lanraragi.reader.data.metadata.MetadataApplyBlockReason
import com.lanraragi.reader.data.metadata.MetadataApplyResult
import com.lanraragi.reader.data.metadata.MetadataFieldName
import com.lanraragi.reader.data.metadata.MetadataPendingReason
import com.lanraragi.reader.data.metadata.MetadataSnapshot
import com.lanraragi.reader.data.metadata.MetadataState
import com.lanraragi.reader.data.metadata.MetadataStateStatus
import com.lanraragi.reader.data.metadata.MetadataTagOperation
import com.lanraragi.reader.data.metadata.matching.MatchCandidate
import com.lanraragi.reader.data.metadata.matching.MatchEvidence
import com.lanraragi.reader.data.metadata.matching.MatchResult
import com.lanraragi.reader.data.metadata.matching.MatchTarget
import com.lanraragi.reader.data.metadata.matching.MetadataMatchEngine
import com.lanraragi.reader.data.metadata.providers.MetadataCandidateInput
import com.lanraragi.reader.data.metadata.providers.NativeMetadataProviders
import com.lanraragi.reader.data.metadata.providers.NativeGalleryMetadata
import com.lanraragi.reader.data.metadata.applyTo
import com.lanraragi.reader.data.metadata.toMetadataSnapshot
import com.lanraragi.reader.data.metadata.plugins.MetadataPluginExecution
import com.lanraragi.reader.data.metadata.plugins.MetadataPluginRequest
import com.lanraragi.reader.data.metadata.plugins.ServerMetadataPlugin
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ArchiveCover
import com.lanraragi.reader.ui.CategoryManagementSheet
import com.lanraragi.reader.ui.CategoryRefreshBus
import com.lanraragi.reader.ui.CoverChangeBus
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.LoadingImage
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.TagAssistChip
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.categoryNameError
import com.lanraragi.reader.ui.components.glass.LiquidGlassFab
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.isProtectedCategoryName
import com.lanraragi.reader.ui.parseHexColor
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.screens.FilterBus
import com.lanraragi.reader.ui.screens.LibraryRefreshBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID


/** D2 支持精确抓取完整元数据的原生来源（EH/nHentai 走现有 native fetch）。 */
internal val NATIVE_FETCH_PROVIDERS = setOf("ehentai", "nhentai")

/*
 * 预览网格页缩略图的轮询节奏，与阅读器时间线（ReaderScreen 的 THUMB_POLL_*）取同一组值：
 * 服务端任务是逐个档案排队执行的，2s 足够看出进度，也不会把服务器打满；
 * 上限给到 3 分钟是为了让大档案也能在这一次停留里跑完。
 */
private const val PREVIEW_THUMB_POLL_INTERVAL_MS = 2_000L
private const val PREVIEW_THUMB_POLL_MAX_MS = 180_000L


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
        val previewColumns: Int = 4,
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
        val metadataCandidates: List<MatchResult> = emptyList(),
        val providerPatchPending: Boolean = false,
        val nativeProviderLoading: Boolean = false,
        // D2 元数据工作台：关键词重搜进行中。
        val metadataSearching: Boolean = false,
        // A11 加入卷
        val tanks: List<Tankoubon> = emptyList(),
        val archiveTankoubonIds: Set<String> = emptySet(),
        val tankSheetLoading: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _previewThumbs = MutableStateFlow(ThumbPhase.IDLE)

    /** 预览网格的页缩略图生成状态；只有 READY 时网格才会请求缩略图，见 [ensurePreviewThumbnails]。 */
    val previewThumbs = _previewThumbs.asStateFlow()

    private var previewThumbJob: Job? = null

    private var lastArchFailed = false
    private var categoryMutationInFlight = false
    private val metadataTarget = ArchiveIdentity.Remote(arcid, ApiClient.config.baseUrl)

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

    fun loadMetadataWorkbench() {
        viewModelScope.launch {
            val refreshed = container.metadataRepository.refresh(metadataTarget)
            val snapshot = refreshed.latest ?: _state.value.archive?.toMetadataSnapshot() ?: return@launch
            _state.update { it.copy(metadataCandidates = nativeCandidates(snapshot, it.archive)) }
        }
    }

    internal fun previewMetadata(submission: MetadataEditSubmission) {
        val archive = _state.value.archive ?: return
        viewModelScope.launch {
            val current = container.metadataRepository.observe(metadataTarget).value.latest
                ?: archive.toMetadataSnapshot()
            val manual = buildManualMetadataPatch(current, submission)
            if (manual.patch == com.lanraragi.reader.domain.metadata.MetadataPatch()) {
                _state.update { it.copy(message = "没有需要预览的元数据变化") }
                return@launch
            }
            container.metadataRepository.stagePatch(metadataTarget, manual.baseline, manual.patch)
            container.metadataRepository.preview(metadataTarget, userMetadataApplyPolicy())
            _state.update { it.copy(providerPatchPending = false) }
        }
    }

    fun previewMetadataCandidate(result: MatchResult) {
        val patch = result.candidate.patch ?: return
        viewModelScope.launch {
            val current = container.metadataRepository.observe(metadataTarget).value.latest
                ?: _state.value.archive?.toMetadataSnapshot()
                ?: return@launch
            container.metadataRepository.stagePatch(metadataTarget, current, patch)
            container.metadataRepository.preview(metadataTarget, providerMetadataApplyPolicy())
            _state.update { it.copy(providerPatchPending = true) }
        }
    }

    fun fetchNativeMetadata(result: MatchResult) {
        val candidate = result.candidate
        val sourceId = candidate.sourceId ?: return
        val sourceUrl = candidate.sourceUrl ?: return
        if (candidate.providerId !in NATIVE_FETCH_PROVIDERS) return
        viewModelScope.launch {
            _state.update { it.copy(nativeProviderLoading = true, message = null) }
            try {
                val fetched = container.nativeMetadataFetch.fetch(
                    candidate.providerId,
                    sourceId,
                    sourceUrl,
                )
                previewFetchedNativeMetadata(fetched)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(message = "原生元数据抓取失败：${error.message}") }
            } finally {
                _state.update { it.copy(nativeProviderLoading = false) }
            }
        }
    }

    private suspend fun previewFetchedNativeMetadata(fetched: NativeGalleryMetadata) {
        val current = container.metadataRepository.observe(metadataTarget).value.latest
            ?: _state.value.archive?.toMetadataSnapshot()
            ?: return
        val patch = fetched.toPatch()
        val ranked = MetadataMatchEngine.rank(
            current.toMatchTarget(_state.value.archive),
            listOf(
                MatchCandidate(
                    providerId = fetched.providerId,
                    sourceId = fetched.sourceId,
                    sourceUrl = fetched.sourceUrl,
                    title = fetched.title,
                    tags = fetched.tags,
                    patch = patch,
                ),
            ),
        ).first()
        container.metadataRepository.stagePatch(metadataTarget, current, patch)
        container.metadataRepository.preview(metadataTarget, providerMetadataApplyPolicy())
        _state.update {
            it.copy(
                metadataCandidates = (listOf(ranked) + it.metadataCandidates)
                    .distinctBy { match -> match.candidate.identity },
                providerPatchPending = true,
                message = "原生元数据已纳入预览，请确认后保存",
            )
        }
    }

    fun applyMetadata(approveRebasedSnapshot: Boolean) {
        viewModelScope.launch {
            when (
                val result = container.metadataRepository.applyPending(
                    metadataTarget,
                    if (_state.value.providerPatchPending) {
                        providerMetadataApplyPolicy(approveRebasedSnapshot)
                    } else {
                        userMetadataApplyPolicy(approveRebasedSnapshot)
                    },
                )
            ) {
                is MetadataApplyResult.Applied -> {
                    result.state.latest?.let { snapshot ->
                        _state.update { current ->
                            current.copy(
                                archive = current.archive?.let(snapshot::applyTo),
                                message = "元数据已保存",
                                providerPatchPending = false,
                            )
                        }
                    }
                    LibraryRefreshBus.tick.value++
                }

                is MetadataApplyResult.Blocked -> _state.update {
                    it.copy(message = "元数据需要重新审阅后才能保存")
                }

                is MetadataApplyResult.Pending -> _state.update {
                    val message = when (result.reason) {
                        MetadataPendingReason.LOCKED -> "档案正被服务器锁定，修改已保留"
                        MetadataPendingReason.NETWORK -> "网络不可用，修改已保留"
                        MetadataPendingReason.INVALID_RESPONSE -> "服务器返回内容与预览不一致，修改已保留"
                        MetadataPendingReason.PERSISTENCE_ERROR -> "修改状态保存失败"
                        MetadataPendingReason.REMOTE_ERROR -> "服务器拒绝更新，修改已保留"
                    }
                    it.copy(message = message)
                }

                is MetadataApplyResult.NoPending -> _state.update {
                    it.copy(message = "没有待应用的元数据修改")
                }
            }
        }
    }

    fun discardMetadataPatch() {
        viewModelScope.launch {
            container.metadataRepository.discardPending(metadataTarget)
            _state.update { it.copy(message = "已放弃待应用的元数据修改", providerPatchPending = false) }
        }
    }

    /*
     * D2 关键词重搜：把输入词交给离线原生候选解析器重新产出候选
     * （支持 EH/nHentai 精确链接、gid/token、数字 id、标题内嵌 id 与
     * 普通标题的“仅供检索参考”建议），按现有匹配引擎重新排名。
     * 指定来源（EH/nHentai）且存在可精确抓取的候选时，
     * 直接调用现有 native fetch 拉取完整元数据并纳入预览。
     */
    fun searchMetadataCandidates(keyword: String, providerId: String?) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val provider = providerId?.takeIf { it in NATIVE_FETCH_PROVIDERS }
        viewModelScope.launch {
            _state.update { it.copy(metadataSearching = true) }
            try {
                val archive = _state.value.archive
                val snapshot = container.metadataRepository.observe(metadataTarget).value.latest
                    ?: archive?.toMetadataSnapshot()
                    ?: return@launch
                val providers = if (provider != null) {
                    NativeMetadataProviders.all.filter { it.providerId == provider }
                } else {
                    NativeMetadataProviders.all
                }
                val input = MetadataCandidateInput(
                    sourceUrls = listOf(trimmed),
                    existingTags = emptySet(),
                    archiveTitle = trimmed,
                    fileName = trimmed,
                )
                val ranked = MetadataMatchEngine.rank(
                    snapshot.toMatchTarget(archive),
                    MetadataMatchEngine.fromProviderCandidates(
                        providers.flatMap { it.findCandidates(input) },
                    ),
                )
                _state.update { it.copy(metadataCandidates = ranked) }
                if (provider != null) {
                    ranked.firstOrNull { result ->
                        result.candidate.providerId == provider &&
                                result.candidate.patch != null &&
                                result.candidate.sourceId != null &&
                                result.candidate.sourceUrl != null
                    }?.let(::fetchNativeMetadata)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "候选搜索失败：${e.message}") }
            } finally {
                _state.update { it.copy(metadataSearching = false) }
            }
        }
    }

    /*
     * D2 标签合并勾选：按用户勾选过滤候选 patch 的 addTags 后重新入库预览。
     * 走与候选预览完全一致的 stagePatch → preview → applyPending 写回路径。
     */
    fun applyCandidateTagSelection(keepTagKeys: Set<String>) {
        viewModelScope.launch {
            val current = container.metadataRepository.observe(metadataTarget).value
            val patch = current.pendingPatch
            if (patch == com.lanraragi.reader.domain.metadata.MetadataPatch()) {
                _state.update { it.copy(message = "没有待应用的候选标签") }
                return@launch
            }
            val removableKeys = patch.removeTags.mapTo(mutableSetOf<String>()) { it.full }
            val filtered = patch.copy(
                addTags = patch.addTags.filter { it.full in keepTagKeys }.toSet(),
                tagProvenance = patch.tagProvenance.filterKeys { it in keepTagKeys || it in removableKeys },
            )
            val baseline = current.baseline
                ?: current.latest
                ?: _state.value.archive?.toMetadataSnapshot()
                ?: return@launch
            container.metadataRepository.stagePatch(metadataTarget, baseline, filtered)
            container.metadataRepository.preview(metadataTarget, providerMetadataApplyPolicy())
            _state.update { it.copy(providerPatchPending = true, message = "已按所选标签更新预览") }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            container.favoritesRepository.toggle(arcid)
        }
    }

    /*
     * 标记为未读：恢复服务器的“New!”标记，
     * 并通过库刷新总线通知列表同步。
     */
    fun markAsUnread() {
        if (arcid.startsWith("local_")) {
            showMessage("本地档案无需标记为未读")
            return
        }

        viewModelScope.launch {
            try {
                container.repository.setArchiveNew(arcid)

                _state.update {
                    it.copy(
                        archive =
                            it.archive?.copy(
                                isnew = "true",
                            ),
                        message = "已标记为未读",
                    )
                }

                LibraryRefreshBus.tick.value++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message = e.message ?: "标记未读失败",
                    )
                }
            }
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
                /*
                 * 「加入卷」只需第一页作为可选项（与改动前的无参行为一致，
                 * 服务端无 page 参数时等于 page 0），这里显式传 0 说明取哪一页：
                 * 服务端 Tankoubon.pm 按 start = page * archives_per_page 切片。
                 * 卷墙的翻页由 TankoubonBrowseScreen 负责。
                 */
                val tanks = container.repository.getTankoubons(page = 0)
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
                val candidate = container.metadataPluginCoordinator.run(
                    MetadataPluginRequest(
                        target = metadataTarget,
                        plugin = ServerMetadataPlugin(
                            namespace = plugin.namespace,
                            name = plugin.name.ifBlank { plugin.namespace },
                            version = plugin.version.ifBlank { null },
                        ),
                        execution = MetadataPluginExecution.ASYNCHRONOUS,
                    ),
                )
                if (candidate == null) {
                    _state.update { it.copy(message = "插件未返回可预览的元数据，结果已保留") }
                    return@launch
                }
                val current = container.metadataRepository.observe(metadataTarget).value.latest
                    ?: _state.value.archive?.toMetadataSnapshot()
                    ?: return@launch
                val ranked = MetadataMatchEngine.rank(
                    target = current.toMatchTarget(_state.value.archive),
                    candidates = listOf(
                        MatchCandidate(
                            providerId = candidate.plugin.namespace,
                            sourceId = candidate.raw.raw["gid"]?.toString()?.trim('"'),
                            sourceUrl = candidate.patch.sourceUrl?.value,
                            title = candidate.raw.title,
                            tags = candidate.patch.addTags,
                            patch = candidate.patch,
                        ),
                    ),
                )
                val result = ranked.first()
                container.metadataRepository.stagePatch(metadataTarget, current, candidate.patch)
                container.metadataRepository.preview(metadataTarget, providerMetadataApplyPolicy())
                _state.update {
                    it.copy(
                        metadataCandidates = (listOf(result) + it.metadataCandidates)
                            .distinctBy { match -> match.candidate.identity },
                        providerPatchPending = true,
                        message = "插件候选已生成并纳入预览，请在元数据工作台确认",
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

        val title =
            archive.title.ifBlank { arcid }
        viewModelScope.launch {
            val destination = withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                val treeUri = container.settingsRepository.settings.first().downloadDirUri
                if (treeUri == null) {
                    File(
                        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: appContext.filesDir,
                        "LANraragi/${sanitizeFileName(title)}.archive",
                    ).absolutePath
                } else {
                    val tree = Uri.parse(treeUri)
                    val rootId = DocumentsContract.getTreeDocumentId(tree)
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
                    DocumentsContract.createDocument(
                        appContext.contentResolver,
                        parent,
                        "application/octet-stream",
                        "${sanitizeFileName(title)}.archive",
                    )?.toString()
                }
            }
            if (destination == null) {
                _state.update { it.copy(message = "无法创建下载目标") }
                return@launch
            }
            val source = DownloadSourceIdentity(arcid, ApiClient.config.baseUrl)
            val saved = container.savedArtifactRepository.findBySource(source)
            if (saved != null) {
                try {
                    SavedArtifactExporter(container.context.contentResolver).export(
                        saved,
                        DownloadDestination(destination),
                    )
                    _state.update { it.copy(message = "已从本地保存资源导出原档") }
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Fall through to the durable server task if the saved copy is stale.
                }
            }
            container.downloadManager.enqueue(
                DownloadTaskSpec.Archive(
                    source = source,
                    destination = DownloadDestination(destination),
                    label = title,
                ),
            )
            _state.update { it.copy(message = "已加入下载队列") }
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

                container.thumbnailRepository.refreshCover(arcid)
                _state.update { it.copy(message = "封面已更新") }

                // 只让这张封面失效，不牵连全库封面缓存。
                CoverChangeBus.notifyChanged(arcid)
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
                when (val result = container.repository.queuePageThumbnails(arcid)) {
                    is PageThumbQueue.Queued -> {
                        _state.update {
                            it.copy(
                                message =
                                    "缩略图生成中，完成后自动可用",
                            )
                        }
                    }

                    is PageThumbQueue.AlreadyAvailable -> {
                        _state.update {
                            it.copy(
                                message =
                                    "缩略图已生成",
                            )
                        }
                    }

                    is PageThumbQueue.Failed -> {
                        _state.update {
                            it.copy(
                                message =
                                    result.message ?: "生成页缩略图失败",
                            )
                        }
                    }
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

    /**
     * 预览网格的页缩略图管线（服务器档案）：入队 + 轮询，与阅读器时间线同一套做法。
     *
     * **必须先等到 READY 再让网格请求缩略图**。服务端在该页缩略图还没生成时，不带
     * `no_fallback` 会直接返回 `public/img/noThumb.png`（HTTP 200 + 占位图），客户端无法
     * 与真图区分，Coil 会把它当成功结果缓存下来 —— 这正是「有的画廊详情页前 12 张预览图
     * 一直显示 no thumbnail」的成因：首屏那 12 格（`visiblePreviewCount` 默认 12）
     * 在生成完成之前就发起了请求。
     *
     * 失败不提示：网格在未就绪或加载失败时回退整页原图，不因生成任务失败影响预览可用性。
     */
    fun ensurePreviewThumbnails() {
        if (previewThumbJob?.isActive == true) return
        if (_previewThumbs.value == ThumbPhase.READY) return
        previewThumbJob = viewModelScope.launch {
            _previewThumbs.value = ThumbPhase.GENERATING
            val queued = try {
                container.repository.queuePageThumbnails(arcid)
            } catch (e: CancellationException) {
                _previewThumbs.value = ThumbPhase.IDLE
                throw e
            } catch (_: Exception) {
                null
            }
            val jobId = when (queued) {
                PageThumbQueue.AlreadyAvailable -> {
                    _previewThumbs.value = ThumbPhase.READY
                    return@launch
                }
                is PageThumbQueue.Queued -> queued.jobId
                else -> {
                    _previewThumbs.value = ThumbPhase.FAILED
                    return@launch
                }
            }
            val deadline = System.currentTimeMillis() + PREVIEW_THUMB_POLL_MAX_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val progress = try {
                    container.repository.minionPageThumbProgress(jobId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (progress != null) {
                    val (done, pages) = progress
                    if (pages > 0 && done >= pages) break
                }
                delay(PREVIEW_THUMB_POLL_INTERVAL_MS)
            }
            // 轮询结束（完成或超时）按可用处理：个别页仍缺失时带 no_fallback 会拿到 202，
            // 单元格据此回退整页原图，不会退化成服务端的 noThumb 占位图。
            _previewThumbs.value = ThumbPhase.READY
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

    private fun nativeCandidates(
        snapshot: com.lanraragi.reader.data.metadata.MetadataSnapshot,
        archive: Archive?,
    ): List<MatchResult> {
        val input = MetadataCandidateInput(
            sourceUrls = listOfNotNull(snapshot.sourceUrl),
            existingTags = snapshot.tags,
            archiveTitle = archive?.title ?: snapshot.title,
            fileName = archive?.title,
        )
        val candidates = NativeMetadataProviders.all.flatMap { it.findCandidates(input) }
        return MetadataMatchEngine.rank(
            snapshot.toMatchTarget(archive),
            MetadataMatchEngine.fromProviderCandidates(candidates),
        )
    }

    private fun com.lanraragi.reader.data.metadata.MetadataSnapshot.toMatchTarget(
        archive: Archive?,
    ) = MatchTarget(
        sourceUrls = listOfNotNull(sourceUrl).toSet(),
        sourceTags = tags.filter { it.identity.namespace == "source" }
            .map { it.raw.substringAfter(':').trim() }
            .toSet(),
        fileName = archive?.title,
        title = archive?.title ?: title,
        tags = tags,
    )
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
@OptIn(ExperimentalFoundationApi::class)
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
    /*
     * 长按动作（可空）：为 FAB 双态菜单等提供长按入口。
     * 传入时改用 combinedClickable，按压反馈仍复用同一个 interactionSource。
     */
    onLongClick: (() -> Unit)? = null,
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
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            enabled = enabled,
                            interactionSource =
                                interactionSource,
                            indication = null,
                            role = Role.Button,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                    } else {
                        Modifier.clickable(
                            enabled = enabled,
                            interactionSource =
                                interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    },
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
    val metadataTarget = remember(arcid, ApiClient.config.baseUrl) {
        ArchiveIdentity.Remote(arcid, ApiClient.config.baseUrl)
    }

    val metadataState by container.metadataRepository
        .observe(metadataTarget)
        .collectAsStateWithLifecycle()

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

    var showMetadataSheet by
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

    /*
     * FAB 长按菜单状态（UI 规划 4.1）：
     * 「从第 N 页开始」/「从头阅读」二选一。
     */
    var showStartMenu by
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

    /*
     * 页缩略图生成状态。只有 READY 时预览网格与选封面网格才用缩略图；
     * 其余情况回退整页原图 —— 那两条路径都不会拿到服务端的 noThumb 占位图。
     */
    val previewThumbs by
    vm.previewThumbs.collectAsStateWithLifecycle(
        initialValue = com.lanraragi.reader.ui.screens.ThumbPhase.IDLE,
    )

    val previewThumbsReady = previewThumbs == ThumbPhase.READY

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

    /*
     * =========================================================
     * 预览区页缩略图
     *
     * 预览网格首次展示（pageUrls 就绪）时静默入队一次
     * 服务端页缩略图生成；失败静默，网格自行回退整页原图。
     * 本地/离线档案没有服务端缩略图管线，不请求。
     * =========================================================
     */
    var previewThumbsRequested by
    remember {
        mutableStateOf(false)
    }

    LaunchedEffect(state.pageUrls.isNotEmpty()) {
        if (
            !previewThumbsRequested &&
            !arcid.startsWith("local_") &&
            state.pageUrls.isNotEmpty()
        ) {
            previewThumbsRequested = true
            vm.ensurePreviewThumbnails()
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
                                            thumbnailContainer = container,
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
                                                    Text("编辑元数据")
                                                },
                                                onClick = {
                                                    showEditMenu = false
                                                    showMetadataSheet = true
                                                    vm.loadMetadataWorkbench()
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

                                            // A11 单行本入口：原先由 `a11_tankoubons` 实验开关门控，
                                            // 该开关已随「实验室」页移除，入口常驻。
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
                                                    Text("标记为未读")
                                                },
                                                onClick = {
                                                    showEditMenu = false
                                                    vm.markAsUnread()
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

                                                    PreviewPageImage(
                                                        arcid = archive.arcid,
                                                        page = pageIndex,
                                                        useThumbnail = previewThumbsReady,
                                                        fallbackModel = url,
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
            /*
             * 悬浮按钮主色调：
             * 读取设置的“悬浮按钮颜色”，空或无效时保持默认外观。
             */
            val fabAccentColor =
                settings?.floatingButtonColor
                    ?.let {
                        parseHexColor(it)
                    }
                    ?.let {
                        Color(it)
                    }
                    ?: LocalContentColor.current

            /*
             * FAB 双态（UI 规划 4.1）：
             * 进度取自服务端 metadata 的 progress 字段——与信息区「已读到第 N 页」
             * 同一个来源，为 1 起页号（同 ReaderScreen 里 meta.progress - 1 的口径）。
             * Routes.reader(arcid, page) 的 page 是 0 起（ReaderScreen 直接当作
             * pager 起始下标），因此跳转时传 progress - 1。
             */
            val fabProgress = state.archive?.progress ?: 0

            val fabLabel =
                if (fabProgress > 0) {
                    "继续阅读 · 第 $fabProgress 页"
                } else {
                    "开始阅读"
                }

            CompositionLocalProvider(
                LocalDetailBackdrop provides
                        detailBackdrop,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(110f),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                    ) {
                        /*
                         * 阅读按钮统一走共用的液态玻璃 FAB 组件。
                         *
                         * 外观 1:1 复现原来的 LiquidGlassVisual(height = 64.dp,
                         * horizontalPadding = 20.dp, lensHeight = 32.dp,
                         * lensAmount = 64.dp, blurRadius = 4.dp, 色散 + 深度)：
                         * 高度由 modifier 传入的 64.dp 决定（组件内部的 56.dp
                         * 受外层固定高度约束收紧为 64.dp），最小宽度 150.dp，
                         * 无背景提色（vibrancy）、blur 4.dp、折射 10.dp/64.dp；
                         * 高光与投影沿用 backdrop 库的默认层
                         * （Highlight.Default / Shadow.Default，原实现即未传
                         * highlight/shadow 而落到这两个默认值），无内阴影、
                         * 无表面着色。颜色仍取 fabAccentColor。
                         *
                         * 按下动力学同样 1:1 复现原 LiquidGlassVisual（组件内的
                         * pressDynamics，缺省即开启，这里显式写出）：
                         * 视差 ±3.dp、blur×(1-0.25p)、折射×(1+0.5p)、
                         * 背景 translate(-nx*18, -ny*14) +
                         * scale(1-0.13p, 1+0.075p)（以按下点为支点）。
                         * 原调用传的 lensHeight = 32.dp 只是「是否启用折射」的开关，
                         * 其数值被实现丢弃（refractionHeight 在 LiquidGlassVisual 里
                         * 写死 10.dp），因此这里 refractionHeight = 10.dp 才是原实现的
                         * 有效值（而不是照抄 32.dp）；lensAmount = 64.dp 对应
                         * refractionAmount = 64.dp。原 onDrawSurface = {}
                         * 的「不绘制表面着色」由 surfaceTint = null 表达
                         * （activeColor 未传即为空）。
                         */
                        LiquidGlassFab(
                            onClick = {
                                navController.navigate(
                                    if (fabProgress > 0) {
                                        Routes.reader(arcid, fabProgress - 1)
                                    } else {
                                        Routes.reader(arcid)
                                    },
                                )
                            },
                            modifier =
                                Modifier
                                    .height(64.dp)
                                    .widthIn(min = 150.dp),
                            backdrop = detailBackdrop,
                            icon = Icons.Filled.PlayArrow,
                            label = fabLabel,
                            tint = fabAccentColor,
                            iconContentDescription = fabLabel,
                            onLongClick = {
                                showStartMenu = true
                            },
                            pressDynamics = true,
                            vibrancyEnabled = false,
                            blurRadius = 4.dp,
                            refractionHeight = 10.dp,
                            refractionAmount = 64.dp,
                            depthEffect = true,
                            chromaticAberration = true,
                            highlight = Highlight.Default,
                            shadow = Shadow.Default,
                            innerShadow = null,
                            surfaceTint = null,
                        )

                        DropdownMenu(
                            expanded = showStartMenu,
                            onDismissRequest = {
                                showStartMenu = false
                            },
                        ) {
                            if (fabProgress > 0) {
                                DropdownMenuItem(
                                    text = {
                                        Text("从第 $fabProgress 页开始")
                                    },
                                    onClick = {
                                        showStartMenu = false
                                        navController.navigate(
                                            Routes.reader(arcid, fabProgress - 1),
                                        )
                                    },
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text("从头阅读")
                                },
                                onClick = {
                                    showStartMenu = false
                                    navController.navigate(
                                        Routes.reader(arcid, 0),
                                    )
                                },
                            )
                        }
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
                thumbsReady = previewThumbsReady,
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
        if (showMetadataSheet) {
            val initialMetadata = metadataState.latest
                ?: state.archive?.toMetadataSnapshot()
                ?: MetadataSnapshot()
            MetadataScrapeWorkbenchSheet(
                initial = initialMetadata,
                state = metadataState,
                candidates = state.metadataCandidates,
                nativeProviderLoading = state.nativeProviderLoading,
                metadataSearching = state.metadataSearching,
                plugins = state.plugins,
                pluginsLoading = state.pluginsLoading,
                pluginRunning = state.pluginRunning,
                onSearch = vm::searchMetadataCandidates,
                onLoadPlugins = vm::loadPlugins,
                onRunPlugin = vm::runPlugin,
                onPreviewCandidate = vm::previewMetadataCandidate,
                onFetchCandidate = vm::fetchNativeMetadata,
                onPreview = vm::previewMetadata,
                onApply = vm::applyMetadata,
                onApplyTagSelection = vm::applyCandidateTagSelection,
                onDiscard = vm::discardMetadataPatch,
                onDismiss = { showMetadataSheet = false },
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


/*
 * ============================================================
 * D2 元数据工作台（升级版）
 *
 * 相比旧版工作台新增：
 *  - 刮削来源选择行：E-Hentai / nHentai / 服务器插件；
 *  - 关键词重搜：输入词交给现有离线候选解析器，精确标识直接 native fetch；
 *  - 候选列表带封面缩略图与匹配来源说明；
 *  - 结果 diff「新旧对照」：标题 / 简介 / 标签按命名空间分组，
 *    新增高亮、将被移除的标签划线标注；
 *  - 已有标签与候选标签按命名空间去重合并展示，新标签可勾选保留后写回。
 *
 * 写回仍走既有链路：stagePatch → preview → applyPending。
 * ============================================================
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MetadataScrapeWorkbenchSheet(
    initial: MetadataSnapshot,
    state: MetadataState,
    candidates: List<MatchResult>,
    nativeProviderLoading: Boolean,
    metadataSearching: Boolean,
    plugins: List<PluginInfo>,
    pluginsLoading: Boolean,
    pluginRunning: Boolean,
    onSearch: (keyword: String, providerId: String?) -> Unit,
    onLoadPlugins: () -> Unit,
    onRunPlugin: (PluginInfo) -> Unit,
    onPreviewCandidate: (MatchResult) -> Unit,
    onFetchCandidate: (MatchResult) -> Unit,
    onPreview: (MetadataEditSubmission) -> Unit,
    onApply: (approveRebasedSnapshot: Boolean) -> Unit,
    onApplyTagSelection: (Set<String>) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf(initial.title.orEmpty()) }
    var title by remember(initial) { mutableStateOf(initial.title.orEmpty()) }
    var tags by remember(initial) {
        mutableStateOf(initial.tags.sortedBy(CanonicalTag::full).joinToString(",") { it.raw })
    }
    var summary by remember(initial) { mutableStateOf(initial.summary.orEmpty()) }
    var includeTitle by remember { mutableStateOf(false) }
    var includeTags by remember { mutableStateOf(false) }
    var includeSummary by remember { mutableStateOf(false) }
    val busy = state.status == MetadataStateStatus.LOADING || state.status == MetadataStateStatus.SAVING
    val plan = state.plan
    val nonRebaseBlocks = plan?.blockReasons.orEmpty() -
        MetadataApplyBlockReason.BASE_CHANGED_REVIEW_REQUIRED
    // Local SAF metadata is persisted through Room and deliberately carries
    // TARGET_IS_NOT_REMOTE as an audit marker; it must not disable its local
    // save action. All other planner blocks remain actionable for the UI.
    val actionableBlocks = if (state.isRemote) {
        nonRebaseBlocks
    } else {
        nonRebaseBlocks - MetadataApplyBlockReason.TARGET_IS_NOT_REMOTE
    }
    val canApply = plan?.diff?.hasEffectiveChanges == true && actionableBlocks.isEmpty() && !busy

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("元数据工作台", style = MaterialTheme.typography.titleLarge)

            // —— 刮削来源选择行 ——
            Text("刮削来源", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sourceFilter == null,
                    onClick = { sourceFilter = null },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = sourceFilter == "ehentai",
                    onClick = { sourceFilter = "ehentai" },
                    label = { Text("E-Hentai") },
                )
                FilterChip(
                    selected = sourceFilter == "nhentai",
                    onClick = { sourceFilter = "nhentai" },
                    label = { Text("nHentai") },
                )
                FilterChip(
                    selected = sourceFilter == "plugin",
                    onClick = {
                        sourceFilter = "plugin"
                        onLoadPlugins()
                    },
                    label = { Text("服务器插件") },
                )
            }

            if (sourceFilter == "plugin") {
                MetadataPluginPicker(
                    plugins = plugins,
                    loading = pluginsLoading,
                    running = pluginRunning,
                    busy = busy,
                    onRun = onRunPlugin,
                )
            } else {
                // —— 关键词重搜（全部 / E-Hentai / nHentai）——
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("关键词") },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onSearch(keyword, sourceFilter) },
                        enabled = !busy && !metadataSearching && keyword.isNotBlank(),
                    ) {
                        Text(if (metadataSearching) "搜索中…" else "搜索")
                    }
                }
                Text(
                    "支持 EH / nHentai 链接、gid/token、数字 id 或标题关键词；精确标识会直接抓取完整元数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // —— 匹配候选列表 ——
            Text("匹配候选", style = MaterialTheme.typography.titleMedium)
            val visibleCandidates = when (sourceFilter) {
                "ehentai" -> candidates.filter { it.candidate.providerId == "ehentai" }
                "nhentai" -> candidates.filter { it.candidate.providerId == "nhentai" }
                else -> candidates
            }
            if (visibleCandidates.isEmpty()) {
                Text(
                    if (metadataSearching) "候选搜索中…" else "暂无候选，可修改关键词后重新搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                visibleCandidates.forEach { result ->
                    MetadataCandidateRow(
                        result = result,
                        busy = busy,
                        nativeProviderLoading = nativeProviderLoading,
                        onPreview = { onPreviewCandidate(result) },
                        onFetch = { onFetchCandidate(result) },
                    )
                }
            }

            HorizontalDivider()

            // —— 手动编辑（既有功能保留）——
            SelectableWorkbenchField("标题", includeTitle, { includeTitle = it }) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = includeTitle && !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SelectableWorkbenchField("标签", includeTags, { includeTags = it }) {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    enabled = includeTags && !busy,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SelectableWorkbenchField("简介", includeSummary, { includeSummary = it }) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    enabled = includeSummary && !busy,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDiscard, enabled = state.hasPendingPatch && !busy) {
                    Text("放弃待应用修改")
                }
                TextButton(
                    onClick = {
                        onPreview(
                            MetadataEditSubmission(
                                title = title.takeIf { includeTitle },
                                tags = tags.takeIf { includeTags },
                                summary = summary.takeIf { includeSummary },
                            ),
                        )
                    },
                    enabled = (includeTitle || includeTags || includeSummary) && !busy,
                ) {
                    Text("生成预览")
                }
            }

            // —— 结果 diff：新旧对照 ——
            plan?.let { currentPlan ->
                HorizontalDivider()
                Text("应用预览（新旧对照）", style = MaterialTheme.typography.titleMedium)
                if (currentPlan.baseChanged) {
                    Text(
                        "服务器元数据已变化；保存时会基于最新内容重新合并。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                currentPlan.diff.fields.forEach { change ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            "${metadataFieldLabel(change.field)}（旧 → 新）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (!change.before.isNullOrEmpty()) {
                            Text(
                                "旧：${change.before}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("新：${change.after.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (currentPlan.diff.tags.isNotEmpty()) {
                    Text(
                        "标签（按命名空间）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    currentPlan.diff.tags
                        .groupBy { it.tag.identity.namespace.orEmpty() }
                        .entries
                        .sortedBy { (ns, _) ->
                            TagRules.NAMESPACE_ORDER.indexOf(ns).let { if (it < 0) Int.MAX_VALUE else it }
                        }
                        .forEach { (ns, changes) ->
                            Text(
                                TagRules.label(ns),
                                style = MaterialTheme.typography.titleSmall,
                                color = rememberTagColor(ns),
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                changes.forEach { change ->
                                    if (change.operation == MetadataTagOperation.ADD) {
                                        Text(
                                            "+ ${change.tag.raw}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Text(
                                            "− ${change.tag.raw}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                textDecoration = TextDecoration.LineThrough,
                                            ),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                }
                currentPlan.diff.conflicts.forEach { conflict ->
                    Text(
                        conflict.explanation ?: "${conflict.key} 未应用",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!currentPlan.diff.hasEffectiveChanges && currentPlan.diff.conflicts.isEmpty()) {
                    Text("没有需要应用的变化")
                }
                state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(
                        onClick = { onApply(currentPlan.baseChanged) },
                        enabled = canApply,
                    ) {
                        Text(if (currentPlan.baseChanged) "确认重合并并保存" else "确认保存")
                    }
                }
            }

            // —— 标签合并：已有 + 候选按命名空间去重，勾选保留后写回 ——
            if (state.pendingPatch.addTags.isNotEmpty()) {
                HorizontalDivider()
                Text("标签合并", style = MaterialTheme.typography.titleMedium)
                Text(
                    "已与现有标签按命名空间去重合并；取消勾选的新标签不会写回。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var selectedAdds by remember(state.pendingPatch) {
                    mutableStateOf<Set<String>>(state.pendingPatch.addTags.map { it.full }.toSet())
                }
                val existingKeys = initial.canonicalTags.keys
                mergeCandidateTagRows(initial.tags, state.pendingPatch.addTags).forEach { (ns, rows) ->
                    Text(
                        TagRules.label(ns),
                        style = MaterialTheme.typography.titleSmall,
                        color = rememberTagColor(ns),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                    rows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = row.tag.full in selectedAdds,
                                onCheckedChange = { checked ->
                                    selectedAdds = if (checked) {
                                        selectedAdds + row.tag.full
                                    } else {
                                        selectedAdds - row.tag.full
                                    }
                                },
                                enabled = !busy,
                            )
                            Text(
                                if (row.isNew) "${row.tag.raw}（新增）" else "${row.tag.raw}（已有）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.isNew) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { onApplyTagSelection(selectedAdds) },
                        enabled = !busy,
                    ) {
                        Text("按所选标签更新预览")
                    }
                }
            }

            if (plan == null) {
                state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
            if (state.status == MetadataStateStatus.SAVED && !state.hasPendingPatch) {
                Text("元数据已保存", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


/** 工作台内嵌的服务器插件选择区（复用 PluginsSheet 同一条插件执行路径）。 */
@Composable
private fun MetadataPluginPicker(
    plugins: List<PluginInfo>,
    loading: Boolean,
    running: Boolean,
    busy: Boolean,
    onRun: (PluginInfo) -> Unit,
) {
    Text(
        "服务器插件将在当前档案上执行，结果会加入候选并进入应用预览；也可以继续使用「编辑 → 运行服务器插件」原入口。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        loading && plugins.isEmpty() -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier
                        .width(14.dp)
                        .height(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text("插件加载中…", style = MaterialTheme.typography.bodySmall)
            }
        }

        plugins.isEmpty() -> {
            Text(
                "暂无可用插件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {
            plugins.forEach { plugin ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy && !running) { onRun(plugin) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            plugin.name.ifBlank { plugin.namespace },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (plugin.name.isNotBlank() && plugin.namespace.isNotBlank()) {
                            Text(
                                plugin.namespace,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (running) {
                        CircularProgressIndicator(
                            Modifier
                                .width(14.dp)
                                .height(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}


/** 匹配候选行：封面缩略图 + 标题 + 来源/评分/依据 + 预览与抓取操作。 */
@Composable
private fun MetadataCandidateRow(
    result: MatchResult,
    busy: Boolean,
    nativeProviderLoading: Boolean,
    onPreview: () -> Unit,
    onFetch: () -> Unit,
) {
    val candidate = result.candidate
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val coverUrl = candidateCoverUrl(candidate)
        if (coverUrl != null) {
            LoadingImage(
                model = coverUrl,
                contentDescription = "候选封面",
                modifier = Modifier
                    .width(56.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(
                Modifier
                    .width(56.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "候选封面占位",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.title ?: candidate.sourceUrl ?: candidate.sourceId ?: candidate.providerId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Text(
                "${providerLabel(candidate.providerId)} · ${(result.score * 100).toInt()}% · " +
                    result.evidence.joinToString("、", transform = ::matchEvidenceLabel),
                style = MaterialTheme.typography.bodySmall,
                color = if (result.lowConfidence) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row {
                if (candidate.patch != null) {
                    TextButton(onClick = onPreview, enabled = !busy) { Text("纳入应用预览") }
                }
                if (candidate.providerId in NATIVE_FETCH_PROVIDERS &&
                    candidate.sourceId != null &&
                    candidate.sourceUrl != null
                ) {
                    TextButton(
                        onClick = onFetch,
                        enabled = !busy && !nativeProviderLoading,
                    ) {
                        Text(if (nativeProviderLoading) "抓取中…" else "抓取完整元数据")
                    }
                }
            }
        }
    }
}


@Composable
private fun SelectableWorkbenchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label, modifier = Modifier.padding(top = 12.dp))
        }
        content()
    }
}


private fun providerLabel(providerId: String): String = when (providerId) {
    "ehentai" -> "E-Hentai"
    "nhentai" -> "nHentai"
    else -> "插件：$providerId"
}

/**
 * 候选封面缩略图：nHentai 的公开封面直链可由数字 id 推出；
 * E-Hentai 的封面地址无法由 gid/token 推出，展示占位图。
 */
private fun candidateCoverUrl(candidate: MatchCandidate): String? {
    val id = candidate.sourceId ?: return null
    return when (candidate.providerId) {
        "nhentai" -> if (Regex("[1-9][0-9]{0,8}").matches(id)) "https://nhentai.net/g/$id/cover.jpg" else null
        else -> null
    }
}

private fun metadataFieldLabel(field: MetadataFieldName): String = when (field) {
    MetadataFieldName.TITLE -> "标题"
    MetadataFieldName.SUMMARY -> "简介"
    MetadataFieldName.SOURCE_URL -> "来源"
    MetadataFieldName.TAGS -> "标签"
}

private fun matchEvidenceLabel(evidence: MatchEvidence): String = when (evidence) {
    MatchEvidence.URL -> "来源地址"
    MatchEvidence.SOURCE_TAG -> "来源标识"
    MatchEvidence.FILENAME_ID -> "文件名 ID"
    MatchEvidence.TITLE_EXACT -> "标题一致"
    MatchEvidence.TAG_INTERSECTION -> "标签交集"
    MatchEvidence.COVER_HASH -> "封面哈希"
    MatchEvidence.FUZZY_TITLE -> "标题相似"
}

private data class MergedCandidateTag(val tag: CanonicalTag, val isNew: Boolean)

/** 已有标签与候选标签按命名空间去重合并（同一 full 键只保留一条），供勾选保留。 */
private fun mergeCandidateTagRows(
    existing: Set<CanonicalTag>,
    adds: Set<CanonicalTag>,
): List<Pair<String, List<MergedCandidateTag>>> {
    val existingKeys = existing.mapTo(mutableSetOf<String>()) { it.full }
    return (existing.asSequence() + adds.asSequence())
        .distinctBy { it.full }
        .groupBy { it.identity.namespace.orEmpty() }
        .entries
        .sortedBy { (ns, _) ->
            TagRules.NAMESPACE_ORDER.indexOf(ns).let { if (it < 0) Int.MAX_VALUE else it }
        }
        .map { (ns, tags) -> ns to tags.map { MergedCandidateTag(it, it.full !in existingKeys) } }
}


/**
 * 预览网格单元格：远程档案在**服务端页缩略图已就绪**时加载页缩略图
 * （`GET api/archives/{id}/thumbnail?page=N&no_fallback=true`，缓存键 `pagethumb:arcid:page`
 * 与阅读器时间线共享）；未就绪或加载失败时回退整页原图；
 * 本地/离线档案（arcid 以 local_ 开头）没有服务端缩略图管线，直接用整页原图。
 *
 * [useThumbnail] 来自 `DetailViewModel.previewThumbs == READY`，不要恒为 true：
 * 生成完成前请求缩略图只会拿到 202（解码失败）或服务端的 noThumb 占位图。
 */
@Composable
private fun PreviewPageImage(
    arcid: String,
    page: Int,
    useThumbnail: Boolean,
    fallbackModel: Any?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale =
            androidx.compose.ui.layout.ContentScale.Crop,
) {
    val context =
        LocalContext.current

    var thumbFailed by
    remember(page) {
        mutableStateOf(false)
    }

    /*
     * 缩略图请求用 remember 固定实例：
     * LoadingImage 以 model 判等管理加载态，避免重组时重复发起请求。
     *
     * 关键：**不读不写磁盘缓存**。页缩略图未生成时服务端返回 202 + job（JSON），
     * 那份响应体会被 Coil 落盘导致之后每次解码都失败；旧版本还可能留下 noThumb
     * 占位图的磁盘条目（就是「前 12 张一直是 no thumbnail」的那批）。
     * 内存缓存只会在成功解码后写入，可以留着。
     */
    val thumbModel =
        remember(arcid, page) {
            ImageRequest.Builder(context)
                .data(ApiClient.pageThumbnailUrl(arcid, page))
                .memoryCacheKey("pagethumb:$arcid:$page")
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }

    val useServerThumb =
        useThumbnail &&
                !arcid.startsWith("local_") &&
                !thumbFailed

    LoadingImage(
        model = if (useServerThumb) thumbModel else fallbackModel,
        contentDescription =
            null,
        modifier = modifier,
        contentScale =
            contentScale,
        onError = {
            if (!thumbFailed) {
                thumbFailed = true
            }
        },
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverPickerSheet(
    arcid: String,
    pageCount: Int,
    thumbsReady: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberModalBottomSheetState()

    val context =
        LocalContext.current

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

            if (!thumbsReady) {
                Text(
                    "页缩略图生成中，稍后重试或先等预览网格出图",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(top = 4.dp),
                )
            }

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
                    /*
                     * 与预览网格同一套约束：带 no_fallback（未生成时得到 202 而不是
                     * noThumb 占位图），并且**不读写磁盘缓存** —— 否则那份 202 的 JSON
                     * 响应体会被落盘，之后每次解码都失败。
                     */
                    val model =
                        remember(arcid, index) {
                            ImageRequest.Builder(context)
                                .data(ApiClient.pageThumbnailUrl(arcid, index))
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build()
                        }

                    LoadingImage(
                        model =
                            model,
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
                    "暂无单行本\n请在设置页「浏览 → 单行本」点右上「+」新建",
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
