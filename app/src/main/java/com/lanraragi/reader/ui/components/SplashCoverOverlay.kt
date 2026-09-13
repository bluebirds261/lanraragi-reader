package com.lanraragi.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanraragi.reader.data.SplashCoverStore
import com.lanraragi.reader.di.AppContainer
import kotlinx.coroutines.delay

/**
 * 开屏封面遮罩：盖在整个界面最上层，冷启动时显示用户自选的图片 [SplashCoverStore.DISPLAY_DURATION_MS]，
 * 到点淡出；期间点按可立即跳过。未设置封面时本组件不渲染任何东西。
 *
 * 只在「本次 Activity 创建」时出现一次：Activity 声明了 orientation/screenSize 等 configChanges，
 * 旋转不会重建 Activity，因此无需 rememberSaveable；从后台回到前台也不会重放。
 */
@Composable
fun SplashCoverOverlay(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val version by container.splashCoverStore.version.collectAsStateWithLifecycle()
    // 冷启动瞬间 version 就是最终值（store 在 Application.onCreate 时已按文件存在性初始化）；
    // 未设置封面时这里为 false，整段不参与渲染，等于零开销。
    var visible by remember { mutableStateOf(version > 0L) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(SplashCoverStore.DISPLAY_DURATION_MS)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 420)),
        modifier = modifier.fillMaxSize(),
    ) {
        val context = LocalContext.current
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .semantics { contentDescription = "开屏封面，点按跳过" }
                .pointerInput(Unit) {
                    detectTapGestures { visible = false }
                },
        ) {
            AsyncImage(
                // 文件名固定，必须用版本号换缓存键，否则「更换封面」后会继续命中旧图。
                model = ImageRequest.Builder(context)
                    .data(container.splashCoverStore.coverFile())
                    .memoryCacheKey("splash-cover-$version")
                    .diskCacheKey("splash-cover-$version")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
