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
import com.lanraragi.reader.data.assets.CoverModel
import com.lanraragi.reader.data.assets.CoverState
import com.lanraragi.reader.di.AppContainer

/**
 * Renders a remote archive cover through the 200/202-aware thumbnail repository.
 *
 * When the experimental flag is disabled this deliberately keeps the legacy
 * URL path as a kill switch. No server fallback response is cached while the
 * flag is enabled; a client placeholder is shown until a real 200 is ready.
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
    val configuredEnabled by container.thumbnailEnabled.collectAsStateWithLifecycle()
    val useRepository = enabled ?: configuredEnabled
    val serverKey by ApiClient.config.serverKey.collectAsStateWithLifecycle()
    val legacyCoverVersion by CoverChangeBus.version.collectAsStateWithLifecycle()
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
        val legacyModel = fallbackModel ?: (ApiClient.thumbnailUrl(arcid) +
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
