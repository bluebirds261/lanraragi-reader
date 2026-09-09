package com.lanraragi.reader.ui.reader

import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import com.lanraragi.reader.data.diagnostics.PerformanceMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFrameSamplerTest {
    @Test
    fun samplesOnlySlowFramesAndThrottlesRecords() {
        val diagnostics = DiagnosticsFacade()
        val sampler = ReaderFrameSampler(
            diagnostics = diagnostics,
            droppedFrameThresholdNanos = 30_000_000L,
            minimumSampleIntervalNanos = 100_000_000L,
        )

        assertFalse(sampler.onFrame(0L))
        assertFalse(sampler.onFrame(16_000_000L))
        assertTrue(sampler.onFrame(56_000_000L))
        assertFalse(sampler.onFrame(96_000_000L))
        assertTrue(sampler.onFrame(196_000_000L))

        val sample = diagnostics.metrics.snapshot().single {
            it.metric == PerformanceMetric.READER_DROPPED_FRAME
        }
        assertEquals(2, sample.count)
        assertEquals(100.0, sample.latest!!, 0.0)
        assertTrue(diagnostics.log.snapshot.value.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDroppedFrameThreshold() {
        ReaderFrameSampler(DiagnosticsFacade(), droppedFrameThresholdNanos = 0L)
    }
}
