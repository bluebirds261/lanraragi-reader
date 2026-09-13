package com.lanraragi.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.request.ImageRequest
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.catalog.isTankArchiveId
import com.lanraragi.reader.data.assets.CoverModel
import com.lanraragi.reader.data.assets.CoverState
import com.lanraragi.reader.di.AppContainer

/**
 * Renders a remote archive cover through the 200/202-aware thumbnail repository.
 *
 * 缩略图仓库（200 直出 / 202 排队轮询）原先是 `a5_thumbnails` 实验开关控制、
 * 关闭时退回遗留直连 URL；该开关已随「实验室」页一并移除，仓库路径现在是唯一路径。
 * 仓库路径不缓存服务端的兜底响应：真实 200 就绪前显示客户端占位图。
 */
@Composable
fun RemoteThumbnailImage(
    container: AppContainer,
    arcid: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackModel: Any? = null,
    enabled: Boolean? = null,
    onError: (() -> Unit)? = null,
) {
    // 单行本（TANK_xxx）的封面走 `/api/tankoubons/{id}/thumbnail`，不是档案缩略图端点：
    // 档案缩略图仓库会用 `/api/archives/TANK_xxx/thumbnail`（no_fallback），
    // 服务端查不到对应文件，只会排一个注定失败的缩略图任务。因此 tank 一律走遗留直连路径。
    val isTank = isTankArchiveId(arcid)
    val useRepository = (enabled ?: true) && !isTank
    val serverKey by ApiClient.config.serverKey.collectAsStateWithLifecycle()
    // 只订阅「本档案」的封面版本号：换别的档案的封面不该让这张图重新下载。
    val coverVersions by CoverChangeBus.versions.collectAsStateWithLifecycle()
    val legacyCoverVersion = coverVersions[arcid] ?: 0
    val coverFlow = remember(arcid, serverKey) {
        container.thumbnailRepository.retainCover(arcid)
    }
    DisposableEffect(coverFlow, arcid, serverKey) {
        onDispose {
            container.thumbnailRepository.releaseCover(arcid, serverKey)
        }
    }
    val state by coverFlow.collectAsStateWithLifecycle()

    LaunchedEffect(arcid, useRepository, serverKey) {
        if (useRepository) {
            container.thumbnailRepository.requestCover(arcid)
        }
    }

    if (!useRepository) {
        val base = if (isTank) ApiClient.tankoubonThumbnailUrl(arcid) else ApiClient.thumbnailUrl(arcid)
        val legacyModel = fallbackModel ?: (base +
            if (legacyCoverVersion > 0) "?v=$legacyCoverVersion" else "")
        LoadingImage(
            model = legacyModel,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onError = onError,
        )
        return
    }

    val ready = when (val current = state) {
        is CoverState.Ready -> current
        is CoverState.Generating -> current.lastGood
        is CoverState.Failed -> current.lastGood
        is CoverState.Unrequested -> null
    }
    val context = LocalContext.current
    val request = ready?.let { cover ->
        val model = CoverModel.RemoteCover(
            serverKey = serverKey,
            arcid = arcid,
            revision = cover.revision,
        )
        remember(model.cacheKey, cover.resource.value) {
            ImageRequest.Builder(context)
                .data(cover.resource.value)
                .memoryCacheKey(model.cacheKey)
                .diskCacheKey(model.cacheKey)
                .build()
        }
    }

    Box(modifier) {
        if (request != null) {
            LoadingImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = onError,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (state is CoverState.Generating || state is CoverState.Unrequested) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "封面加载失败",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }

        if (state is CoverState.Failed) {
            IconButton(
                onClick = { container.thumbnailRepository.requestCover(arcid) },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "重试封面")
            }
        }
    }
}
