package com.lanraragi.reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

enum class ReaderSurfaceMode { CONTINUOUS, VERTICAL_PAGED, HORIZONTAL_PAGED }

@Composable
fun ReaderSurface(
    mode: ReaderSurfaceMode,
    continuous: @Composable () -> Unit,
    verticalPaged: @Composable () -> Unit,
    horizontalPaged: @Composable () -> Unit,
) {
    when (mode) {
        ReaderSurfaceMode.CONTINUOUS -> continuous()
        ReaderSurfaceMode.VERTICAL_PAGED -> verticalPaged()
        ReaderSurfaceMode.HORIZONTAL_PAGED -> horizontalPaged()
    }
}

@Composable
fun ReaderControls(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(140)) + fadeIn(tween(100)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(120)) + fadeOut(tween(100)),
    ) { content() }
}

@Composable
fun ReaderSheets(
    tocVisible: Boolean,
    settingsVisible: Boolean,
    autoPageVisible: Boolean,
    historyVisible: Boolean,
    infoVisible: Boolean,
    pageMenuVisible: Boolean,
    toc: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    autoPage: @Composable () -> Unit,
    history: @Composable () -> Unit,
    info: @Composable () -> Unit,
    pageMenu: @Composable () -> Unit,
) {
    if (tocVisible) toc()
    if (settingsVisible) settings()
    if (autoPageVisible) autoPage()
    if (historyVisible) history()
    if (infoVisible) info()
    if (pageMenuVisible) pageMenu()
}

/**
 * 阅读器沉浸式（UI 规划 5.7）：进入阅读路由时隐藏状态栏与导航栏，退出时原样恢复。
 * 只作用于承载阅读器的 Activity 窗口；阅读器内基于 insets 的 chrome
 * （statusBarsPadding / navigationBarsPadding）会随 insets 归零自动让位。
 */
@Composable
fun ReaderRouteEffects(context: Context, view: View) {
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
