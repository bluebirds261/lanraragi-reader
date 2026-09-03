package com.lanraragi.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp

/** 左边缘右滑返回手势（仅在左边缘触发，非边缘不消费，避免与阅读器翻页等冲突）。 */
fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier = pointerInput(Unit) {
    val edge = 40.dp.toPx()
    val threshold = 120.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x > edge) return@awaitEachGesture
        var total = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) break
            if (change.positionChanged()) {
                total += change.positionChange().x
                change.consume()
                if (total > threshold) {
                    onBack()
                    break
                }
            }
        }
    }
}

/** 右边缘左滑手势（用于退出确认等，仅在右边缘触发）。 */
fun Modifier.exitSwipe(onExit: () -> Unit): Modifier = pointerInput(Unit) {
    val edge = 40.dp.toPx()
    val threshold = 120.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x < size.width - edge) return@awaitEachGesture
        var total = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) break
            if (change.positionChanged()) {
                total += change.positionChange().x
                change.consume()
                if (total < -threshold) {
                    onExit()
                    break
                }
            }
        }
    }
}
