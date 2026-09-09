package com.lanraragi.reader.data.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

@Serializable
enum class DiagnosticLevel { DEBUG, INFO, WARN, ERROR }

@Serializable
data class DiagnosticEvent(
    val id: Long,
    val timestampEpochMs: Long,
    val level: DiagnosticLevel,
    val event: String,
    val fields: Map<String, String> = emptyMap(),
)

@Serializable
enum class PerformanceMetric {
    FIRST_SCREEN,
    THUMBNAIL_READY,
    THUMBNAIL_CACHE_HIT_RATE,
    READER_FIRST_IMAGE,
    READER_DROPPED_FRAME,
    SAF_SCAN,
    DOWNLOAD_THROUGHPUT,
    DOWNLOAD_RETRY,
}

@Serializable
data class MetricSample(
    val metric: PerformanceMetric,
    val value: Double,
    val timestampEpochMs: Long,
)

@Serializable
data class MetricSnapshot(
    val metric: PerformanceMetric,
    val count: Int,
    val latest: Double? = null,
    val average: Double? = null,
    val p50: Double? = null,
    val p95: Double? = null,
    val p99: Double? = null,
    val unit: String = "ms",
)

@Serializable
data class DiagnosticReport(
    val generatedAtEpochMs: Long,
    val appVersion: String,
    val deviceSummary: Map<String, String> = emptyMap(),
    val serverScopeHash: String? = null,
    val taskSummary: Map<String, String> = emptyMap(),
    val cacheSummary: Map<String, String> = emptyMap(),
    val roomSchemaSummary: Map<String, String> = emptyMap(),
    val metrics: List<MetricSnapshot> = emptyList(),
    val logs: List<DiagnosticEvent> = emptyList(),
)

object DiagnosticReportCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun encode(report: DiagnosticReport): String = json.encodeToString(report)
    fun decode(payload: String): DiagnosticReport = json.decodeFromString(payload)
}

object DiagnosticRedactor {
    private val secretKey = Regex("(?i)(api[_-]?key|authorization|cookie|password|token|secret|credential|session|title|tab|browser|tag|path|url|uri)")
    private val winPath = Regex("(?i)([A-Za-z]:\\\\[^\\s\\\"']+)")
    private val unixPath = Regex("(?<![A-Za-z0-9])/(?:[^\\s\\\"']+/)+[^\\s\\\"']*")
    private val url = Regex("(?i)https?://[^\\s\\\"']+")
    private val secretAssignment = Regex("(?i)((?:api[_-]?key|authorization|cookie|password|token|secret|session)\\s*[:=]\\s*)[^\\s,;]+")

    fun redactField(key: String, value: String): String {
        if (secretKey.containsMatchIn(key)) return "[REDACTED]"
        return redact(value)
    }

    fun redact(value: String): String {
        var result = value
        result = url.replace(result) { match -> redactUrl(match.value) }
        result = secretAssignment.replace(result) { "${it.groupValues[1]}[REDACTED]" }
        result = winPath.replace(result, "[PATH]")
        result = unixPath.replace(result, "[PATH]")
        return result
    }

    fun redactFields(fields: Map<String, String>): Map<String, String> =
        fields.mapValues { (key, value) -> redactField(key, value) }

    fun redactUrl(raw: String): String = runCatching {
        val uri = URI(raw)
        val host = uri.host ?: return "[URL]"
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        "${uri.scheme}://$host$port/[URL]"
    }.getOrElse { "[URL]" }

    fun serverScopeHash(scope: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }
}

internal fun nowEpochMs(): Long = Instant.now().toEpochMilli()
