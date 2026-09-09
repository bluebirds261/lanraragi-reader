package com.lanraragi.reader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import kotlinx.coroutines.isActive

/**
 * Composition-scoped dropped-frame sampler for ReaderScreen.
 *
 * It stores only two monotonic timestamps and records at most one bounded metric
 * sample per [minimumSampleIntervalNanos]. No frame identity or reader content
 * reaches diagnostics.
 */
class ReaderFrameSampler(
    private val diagnostics: DiagnosticsFacade,
    private val droppedFrameThresholdNanos: Long = DEFAULT_DROPPED_FRAME_THRESHOLD_NANOS,
    private val minimumSampleIntervalNanos: Long = DEFAULT_MINIMUM_SAMPLE_INTERVAL_NANOS,
) {
    private var previousFrameNanos = NO_FRAME
    private var lastRecordedFrameNanos = NO_FRAME

    init {
        require(droppedFrameThresholdNanos > 0) { "droppedFrameThresholdNanos must be positive" }
        require(minimumSampleIntervalNanos >= 0) { "minimumSampleIntervalNanos must not be negative" }
    }

    /** Returns true only when this frame produces a metrics sample. */
    fun onFrame(frameTimeNanos: Long): Boolean {
        val previous = previousFrameNanos
        previousFrameNanos = frameTimeNanos
        if (previous == NO_FRAME || frameTimeNanos <= previous) return false

        val frameDurationNanos = frameTimeNanos - previous
        if (frameDurationNanos < droppedFrameThresholdNanos) return false
        if (lastRecordedFrameNanos != NO_FRAME &&
            frameTimeNanos - lastRecordedFrameNanos < minimumSampleIntervalNanos
        ) {
            return false
        }

        lastRecordedFrameNanos = frameTimeNanos
        diagnostics.readerDroppedFrame(frameDurationNanos / NANOS_PER_MILLISECOND.toDouble())
        return true
    }

    companion object {
        const val DEFAULT_DROPPED_FRAME_THRESHOLD_NANOS = 32_000_000L
        const val DEFAULT_MINIMUM_SAMPLE_INTERVAL_NANOS = 1_000_000_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val NO_FRAME = Long.MIN_VALUE
    }
}

/**
 * Starts sampling only while this call remains in the composition. The effect is
 * cancelled automatically when ReaderScreen leaves composition or inputs change.
 */
@Composable
fun rememberReaderFrameSampler(
    diagnostics: DiagnosticsFacade,
    droppedFrameThresholdMs: Long = 32L,
    minimumSampleIntervalMs: Long = 1_000L,
): ReaderFrameSampler {
    require(droppedFrameThresholdMs > 0) { "droppedFrameThresholdMs must be positive" }
    require(minimumSampleIntervalMs >= 0) { "minimumSampleIntervalMs must not be negative" }
    val sampler = remember(diagnostics, droppedFrameThresholdMs, minimumSampleIntervalMs) {
        ReaderFrameSampler(
            diagnostics = diagnostics,
            droppedFrameThresholdNanos = millisecondsToNanos(droppedFrameThresholdMs),
            minimumSampleIntervalNanos = millisecondsToNanos(minimumSampleIntervalMs),
        )
    }
    LaunchedEffect(sampler) {
        while (isActive) {
            sampler.onFrame(withFrameNanos { it })
        }
    }
    return sampler
}

private fun millisecondsToNanos(milliseconds: Long): Long =
    milliseconds.coerceAtMost(Long.MAX_VALUE / 1_000_000L) * 1_000_000L
