package com.lanraragi.reader.data.reader

import kotlin.math.abs

enum class ReaderGestureMode { Idle, Tap, Pager, PanZoom }

data class GestureDecision(
    val mode: ReaderGestureMode,
    val consume: Boolean,
    val zoom: Float = 1f,
)

/** Pure gesture arbitration: multi-touch/zoom owns the stream, otherwise horizontal drags page. */
class ReaderGestureArbiter(
    private val dragSlop: Float = 24f,
) {
    private var mode = ReaderGestureMode.Idle
    private var zoom = 1f

    fun begin(pointerCount: Int = 1): GestureDecision {
        mode = if (pointerCount > 1 || zoom > 1.01f) ReaderGestureMode.PanZoom else ReaderGestureMode.Tap
        return GestureDecision(mode, consume = mode == ReaderGestureMode.PanZoom)
    }

    fun move(dx: Float, dy: Float, pointerCount: Int = 1): GestureDecision {
        if (pointerCount > 1 || mode == ReaderGestureMode.PanZoom) {
            mode = ReaderGestureMode.PanZoom
            return GestureDecision(mode, consume = true, zoom = zoom)
        }
        if (mode == ReaderGestureMode.Tap && abs(dx) >= dragSlop && abs(dx) >= abs(dy)) mode = ReaderGestureMode.Pager
        return GestureDecision(mode, consume = mode == ReaderGestureMode.PanZoom)
    }

    fun applyZoom(factor: Float): GestureDecision {
        zoom = (zoom * factor).coerceIn(1f, 8f)
        mode = if (zoom > 1.01f) ReaderGestureMode.PanZoom else ReaderGestureMode.Tap
        return GestureDecision(mode, consume = true, zoom = zoom)
    }

    fun end(): GestureDecision {
        val result = GestureDecision(mode, consume = mode == ReaderGestureMode.PanZoom, zoom = zoom)
        mode = ReaderGestureMode.Idle
        return result
    }

    fun resetZoom(): GestureDecision {
        zoom = 1f
        mode = ReaderGestureMode.Idle
        return GestureDecision(mode, consume = false, zoom = zoom)
    }
}
