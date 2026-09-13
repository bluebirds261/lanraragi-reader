package com.lanraragi.reader.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.metadata.MetadataSnapshot
import com.lanraragi.reader.data.metadata.MetadataState
import com.lanraragi.reader.data.metadata.matching.MatchTarget
import com.lanraragi.reader.data.metadata.matching.MetadataMatchEngine
import com.lanraragi.reader.data.metadata.providers.MetadataCandidateInput
import com.lanraragi.reader.data.metadata.providers.NativeMetadataProviders
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.asImageRequest
import com.lanraragi.reader.ui.edgeSwipeBack
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun LocalDetailScreen(
    container: AppContainer,
    arcid: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val archive = remember(arcid) {
        container.localScanManager.localArchives.value.find { it.arcid == arcid }
    }
    var pageCount by remember(arcid) { mutableIntStateOf(0) }
    var loading by remember(arcid) { mutableStateOf(true) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var nativeProviderLoading by remember { mutableStateOf(false) }
    var providerPatchPending by remember { mutableStateOf(false) }
    var nativeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val localTarget = remember(archive?.summary) {
        archive?.summary?.takeIf(String::isNotBlank)?.let { ArchiveIdentity.LocalSaf(it) }
    }
    val metadataStateFlow = remember(localTarget) {
        localTarget?.let(container.metadataRepository::observe)
            ?: MutableStateFlow(MetadataState(ArchiveIdentity.LocalSaf("local-missing")))
    }
    val metadataState by metadataStateFlow.collectAsStateWithLifecycle()
    val displayedMetadata = metadataState.latest ?: MetadataSnapshot(title = archive?.title)
    val metadataCandidates = remember(displayedMetadata, archive?.title) {
        val providerCandidates = NativeMetadataProviders.all.flatMap { provider ->
            provider.findCandidates(
                MetadataCandidateInput(
                    sourceUrls = listOfNotNull(displayedMetadata.sourceUrl),
                    existingTags = displayedMetadata.tags,
                    archiveTitle = displayedMetadata.title ?: archive?.title,
                    fileName = archive?.title,
                ),
            )
        }
        MetadataMatchEngine.rank(
            MatchTarget(
                sourceUrls = listOfNotNull(displayedMetadata.sourceUrl).toSet(),
                sourceTags = displayedMetadata.tags.filter { it.identity.namespace == "source" }
                    .map { it.raw.substringAfter(':').trim() }.toSet(),
                fileName = archive?.title,
                title = displayedMetadata.title ?: archive?.title,
                tags = displayedMetadata.tags,
            ),
            MetadataMatchEngine.fromProviderCandidates(providerCandidates),
        )
    }

    LaunchedEffect(archive) {
        if (archive == null) {
            loading = false
            return@LaunchedEffect
        }
        if (archive.pagecount > 0) pageCount = archive.pagecount else runCatching {
            pageCount = ArchiveFileReader.getImages(context, Uri.parse(archive.summary)).size
        }
        loading = false
    }

    LaunchedEffect(localTarget) {
        localTarget?.let { container.metadataRepository.refresh(it) }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = displayedMetadata.title ?: archive?.title ?: "本地档案",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        if (archive == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("本地档案不存在或已被移除")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 220.dp, height = 310.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = com.lanraragi.reader.ui.screens.ArchivePageModel(
                        Uri.parse(archive.summary),
                        0,
                        context,
                        thumbnail = true,
                        revision = "index:${archive.dateadded}:${archive.pagecount}",
                    ).asImageRequest(),
                    contentDescription = archive.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading) CircularProgressIndicator()
            }

            Spacer(Modifier.height(18.dp))
            Text(
                displayedMetadata.title ?: archive.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (pageCount > 0) "本地档案 · $pageCount 页" else "本地档案",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { navController.navigate(Routes.reader(arcid)) },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("开始阅读")
                }
                TextButton(onClick = { showMetadataSheet = true }) {
                    Text("编辑元数据")
                }
            }
            displayedMetadata.tags.takeIf { it.isNotEmpty() }?.let { localTags ->
                Spacer(Modifier.height(12.dp))
                Text(
                    localTags.sortedBy { it.full }.joinToString(", ") { it.raw },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            displayedMetadata.summary?.takeIf(String::isNotBlank)?.let { localSummary ->
                Spacer(Modifier.height(8.dp))
                Text(localSummary, style = MaterialTheme.typography.bodyMedium)
            }
            nativeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        if (showMetadataSheet && localTarget != null) {
            MetadataWorkbenchSheet(
                initial = displayedMetadata,
                state = metadataState,
                candidates = metadataCandidates,
                nativeProviderLoading = nativeProviderLoading,
                onPreviewCandidate = { result ->
                    result.candidate.patch?.let { patch ->
                        scope.launch {
                            container.metadataRepository.stagePatch(localTarget, displayedMetadata, patch)
                            container.metadataRepository.preview(localTarget, providerMetadataApplyPolicy())
                            providerPatchPending = true
                        }
                    }
                },
                onFetchCandidate = { result ->
                    val candidate = result.candidate
                    val id = candidate.sourceId
                    val url = candidate.sourceUrl
                    if (id != null && url != null) scope.launch {
                        nativeProviderLoading = true
                        nativeError = null
                        try {
                            val fetched = container.nativeMetadataFetch.fetch(candidate.providerId, id, url)
                            container.metadataRepository.stagePatch(localTarget, displayedMetadata, fetched.toPatch())
                            container.metadataRepository.preview(localTarget, providerMetadataApplyPolicy())
                            providerPatchPending = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            nativeError = "原生元数据抓取失败：${error.message}"
                        } finally {
                            nativeProviderLoading = false
                        }
                    }
                },
                onPreview = { submission ->
                    scope.launch {
                        val manual = buildManualMetadataPatch(displayedMetadata, submission)
                        container.metadataRepository.stagePatch(localTarget, manual.baseline, manual.patch)
                        container.metadataRepository.preview(localTarget, userMetadataApplyPolicy())
                    }
                },
                onApply = { approveRebase ->
                    scope.launch {
                        container.metadataRepository.applyPending(
                            localTarget,
                            if (providerPatchPending) providerMetadataApplyPolicy(approveRebase)
                            else userMetadataApplyPolicy(approveRebase),
                        )
                    }
                },
                onDiscard = {
                    scope.launch { container.metadataRepository.discardPending(localTarget) }
                },
                onDismiss = { showMetadataSheet = false },
            )
        }
    }
}
