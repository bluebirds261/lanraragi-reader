package com.lanraragi.reader.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** Thread-safe, bounded ring of structured events. Inputs are redacted before storage. */
class DiagnosticLog(private val capacity: Int = DEFAULT_CAPACITY) {
    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEvent>(capacity)
    private val nextId = AtomicLong(0)
    private val _snapshot = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val snapshot: StateFlow<List<DiagnosticEvent>> = _snapshot.asStateFlow()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun record(
        level: DiagnosticLevel,
        event: String,
        fields: Map<String, String> = emptyMap(),
        timestampEpochMs: Long = nowEpochMs(),
    ) {
        val safeEvent = DiagnosticRedactor.redact(event).take(MAX_EVENT_LENGTH)
        val safeFields = DiagnosticRedactor.redactFields(fields)
            .mapValues { (_, value) -> value.take(MAX_FIELD_LENGTH) }
        synchronized(lock) {
            if (events.size == capacity) events.removeFirst()
            events.addLast(DiagnosticEvent(nextId.incrementAndGet(), timestampEpochMs, level, safeEvent, safeFields))
            _snapshot.value = events.toList()
        }
    }

    fun clear() = synchronized(lock) {
        events.clear()
        _snapshot.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 300
        private const val MAX_EVENT_LENGTH = 160
        private const val MAX_FIELD_LENGTH = 1_024
    }
}

/** Finite sample stores protect the UI and report size while retaining percentile estimates. */
class PerformanceMetrics(private val samplesPerMetric: Int = DEFAULT_SAMPLES_PER_METRIC) {
    private val lock = Any()
    private val samples = PerformanceMetric.entries.associateWith { ArrayDeque<MetricSample>(samplesPerMetric) }
    private val _snapshot = MutableStateFlow(emptyList<MetricSnapshot>())
    val snapshot: StateFlow<List<MetricSnapshot>> = _snapshot.asStateFlow()

    init {
        require(samplesPerMetric > 0) { "samplesPerMetric must be positive" }
        publishLocked()
    }

    fun record(
        metric: PerformanceMetric,
        value: Double,
        timestampEpochMs: Long = nowEpochMs(),
    ) {
        if (!value.isFinite() || value < 0) return
        synchronized(lock) {
            val ring = checkNotNull(samples[metric])
            if (ring.size == samplesPerMetric) ring.removeFirst()
            ring.addLast(MetricSample(metric, value, timestampEpochMs))
            publishLocked()
        }
    }

    fun snapshot(): List<MetricSnapshot> = synchronized(lock) { buildSnapshotsLocked() }

    private fun publishLocked() {
        _snapshot.value = buildSnapshotsLocked()
    }

    private fun buildSnapshotsLocked(): List<MetricSnapshot> = PerformanceMetric.entries.mapNotNull { metric ->
        val values = checkNotNull(samples[metric]).map { it.value }
        if (values.isEmpty()) return@mapNotNull null
        val sorted = values.sorted()
        MetricSnapshot(
            metric = metric,
            count = values.size,
            latest = values.last(),
            average = values.average(),
            p50 = percentile(sorted, 0.50),
            p95 = percentile(sorted, 0.95),
            p99 = percentile(sorted, 0.99),
            unit = metric.unit,
        )
    }

    private fun percentile(sorted: List<Double>, fraction: Double): Double =
        sorted[((sorted.size - 1) * fraction).toInt()]

    private val PerformanceMetric.unit: String
        get() = when (this) {
            PerformanceMetric.DOWNLOAD_THROUGHPUT -> "bytes/s"
            PerformanceMetric.DOWNLOAD_RETRY -> "count"
            PerformanceMetric.THUMBNAIL_CACHE_HIT_RATE -> "%"
            else -> "ms"
        }

    companion object { const val DEFAULT_SAMPLES_PER_METRIC = 120 }
}

/** Caller-owned metadata for a report. Raw server scope is hashed, never serialized. */
data class DiagnosticReportContext(
    val appVersion: String,
    val deviceSummary: Map<String, String> = emptyMap(),
    val serverScope: String? = null,
    val taskSummary: Map<String, String> = emptyMap(),
    val cacheSummary: Map<String, String> = emptyMap(),
    val roomSchemaSummary: Map<String, String> = emptyMap(),
)

/** Integration point for AppContainer and feature code; it owns no global lifecycle. */
class DiagnosticsFacade(
    val log: DiagnosticLog = DiagnosticLog(),
    val metrics: PerformanceMetrics = PerformanceMetrics(),
) {
    fun event(level: DiagnosticLevel, name: String, fields: Map<String, String> = emptyMap()) =
        log.record(level, name, fields)

    fun firstScreenReady(elapsedMs: Long) = metrics.record(PerformanceMetric.FIRST_SCREEN, elapsedMs.toDouble())
    fun thumbnailReady(elapsedMs: Long) = metrics.record(PerformanceMetric.THUMBNAIL_READY, elapsedMs.toDouble())
    /** Records a bounded 0/100 sample, never an archive identity or cache key. */
    fun thumbnailCacheHit(hit: Boolean) =
        metrics.record(PerformanceMetric.THUMBNAIL_CACHE_HIT_RATE, if (hit) 100.0 else 0.0)
    fun readerFirstImage(elapsedMs: Long) = metrics.record(PerformanceMetric.READER_FIRST_IMAGE, elapsedMs.toDouble())
    fun readerDroppedFrame(frameDurationMs: Double) =
        metrics.record(PerformanceMetric.READER_DROPPED_FRAME, frameDurationMs)
    fun safScan(elapsedMs: Long) = metrics.record(PerformanceMetric.SAF_SCAN, elapsedMs.toDouble())
    fun downloadThroughput(bytesPerSecond: Double) = metrics.record(PerformanceMetric.DOWNLOAD_THROUGHPUT, bytesPerSecond)
    fun downloadRetry(count: Int = 1) = metrics.record(PerformanceMetric.DOWNLOAD_RETRY, count.toDouble())

    fun clearLogs() = log.clear()

    fun exportReport(context: DiagnosticReportContext): DiagnosticReport = DiagnosticReport(
        generatedAtEpochMs = nowEpochMs(),
        appVersion = DiagnosticRedactor.redact(context.appVersion),
        deviceSummary = sanitizeSummary(context.deviceSummary),
        serverScopeHash = context.serverScope?.let(DiagnosticRedactor::serverScopeHash),
        taskSummary = sanitizeSummary(context.taskSummary),
        cacheSummary = sanitizeSummary(context.cacheSummary),
        roomSchemaSummary = sanitizeSummary(context.roomSchemaSummary),
        metrics = metrics.snapshot(),
        logs = log.snapshot.value.map { event ->
            event.copy(
                event = DiagnosticRedactor.redact(event.event),
                fields = DiagnosticRedactor.redactFields(event.fields),
            )
        },
    )

    private fun sanitizeSummary(summary: Map<String, String>): Map<String, String> = summary
        .filterKeys { key -> !FORBIDDEN_REPORT_KEYS.any { it.containsMatchIn(key) } }
        .mapValues { (_, value) -> DiagnosticRedactor.redact(value) }

    companion object {
        private val FORBIDDEN_REPORT_KEYS = listOf(
            Regex("(?i)(title|tab|browser|tag|path|url|uri|cookie|key|token|authorization|secret)"),
        )
    }
}
