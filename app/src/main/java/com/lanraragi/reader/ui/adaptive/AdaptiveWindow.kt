package com.lanraragi.reader.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Layout classes shared by Library/Detail screens. 840dp matches the roadmap tablet gate. */
enum class AdaptiveLayout { PHONE_SINGLE_PANE, TABLET_MASTER_DETAIL }

fun adaptiveLayout(width: Dp, tabletBreakpoint: Dp = 840.dp): AdaptiveLayout =
    if (width >= tabletBreakpoint) AdaptiveLayout.TABLET_MASTER_DETAIL else AdaptiveLayout.PHONE_SINGLE_PANE

@Composable
fun AdaptiveLayoutHost(
    layout: AdaptiveLayout,
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    when (layout) {
        AdaptiveLayout.PHONE_SINGLE_PANE -> master()
        AdaptiveLayout.TABLET_MASTER_DETAIL -> androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(0.44f).fillMaxHeight(),
            ) { master() }
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(0.56f).fillMaxHeight(),
            ) { detail() }
        }
    }
}
