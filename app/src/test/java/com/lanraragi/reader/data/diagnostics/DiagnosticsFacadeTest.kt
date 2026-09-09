package com.lanraragi.reader.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

class DiagnosticsFacadeTest {
    @Test
    fun logRedactsCredentialsUrlsAndAbsolutePathsBeforeStorage() {
        val log = DiagnosticLog(capacity = 4)
        log.record(
            DiagnosticLevel.ERROR,
            "request https://reader.example/api?api_key=top-secret",
            mapOf(
                "Authorization" to "Bearer very-secret",
                "folder" to "C:\\Users\\reader\\Downloads\\private.cbz",
                "Cookie" to "session=also-secret",
            ),
        )

        val text = log.snapshot.value.single().toString()
        assertFalse(text.contains("top-secret"))
        assertFalse(text.contains("very-secret"))
        assertFalse(text.contains("also-secret"))
        assertFalse(text.contains("C:\\Users\\reader"))
        assertTrue(text.contains("[REDACTED]"))
        assertTrue(text.contains("[PATH]"))
    }

    @Test
    fun logRingAndMetricsAreBounded() {
        val log = DiagnosticLog(capacity = 2)
        repeat(3) { log.record(DiagnosticLevel.INFO, "event-$it") }
        assertEquals(listOf("event-1", "event-2"), log.snapshot.value.map { it.event })

        val metrics = PerformanceMetrics(samplesPerMetric = 3)
        repeat(5) { metrics.record(PerformanceMetric.FIRST_SCREEN, (it + 1).toDouble()) }
        val snapshot = metrics.snapshot().single()
        assertEquals(3, snapshot.count)
        assertEquals(5.0, snapshot.latest!!, 0.0)
        assertEquals(4.0, snapshot.p50!!, 0.0)
    }

    @Test
    fun thumbnailCacheHitStoresBoundedAnonymousRateSamples() {
        val diagnostics = DiagnosticsFacade(metrics = PerformanceMetrics(samplesPerMetric = 2))

        diagnostics.thumbnailCacheHit(hit = false)
        diagnostics.thumbnailCacheHit(hit = true)
        diagnostics.thumbnailCacheHit(hit = true)

        val snapshot = diagnostics.metrics.snapshot().single {
            it.metric == PerformanceMetric.THUMBNAIL_CACHE_HIT_RATE
        }
        assertEquals(2, snapshot.count)
        assertEquals(100.0, snapshot.latest!!, 0.0)
        assertEquals(100.0, snapshot.average!!, 0.0)
        assertEquals("%", snapshot.unit)
        assertTrue(diagnostics.log.snapshot.value.isEmpty())
    }

    @Test
    fun concurrentWritersPreserveCapacityAndStableIds() {
        val log = DiagnosticLog(capacity = 64)
        val ready = CountDownLatch(1)
        val workers = List(8) { worker ->
            Thread {
                ready.await()
                repeat(50) { index -> log.record(DiagnosticLevel.DEBUG, "worker-$worker-$index") }
            }.apply { start() }
        }
        ready.countDown()
        workers.forEach(Thread::join)

        val events = log.snapshot.value
        assertEquals(64, events.size)
        assertEquals(events.size, events.map { it.id }.toSet().size)
    }

    @Test
    fun encodedReportContainsNoRawCredentialsUrlsTitlesTagsOrPaths() {
        val diagnostics = DiagnosticsFacade()
        diagnostics.event(
            DiagnosticLevel.WARN,
            "network failed https://example.test/archive?token=hidden",
            mapOf("browserTitle" to "private tab", "tag" to "secret-tag", "localPath" to "/data/user/0/private"),
        )
        val encoded = DiagnosticReportCodec.encode(
            diagnostics.exportReport(
                DiagnosticReportContext(
                    appVersion = "beta",
                    serverScope = "https://example.test/api?api_key=top-secret",
                    deviceSummary = mapOf("model" to "test"),
                    taskSummary = mapOf("browserTitle" to "must-not-export"),
                ),
            ),
        )

        listOf("hidden", "top-secret", "private tab", "secret-tag", "/data/user/0/private", "must-not-export").forEach {
            assertFalse(encoded.contains(it))
        }
        assertTrue(encoded.contains(DiagnosticRedactor.serverScopeHash("https://example.test/api?api_key=top-secret")))
    }
}
