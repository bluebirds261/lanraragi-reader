package com.lanraragi.reader.data.diagnostics

import kotlinx.coroutines.CancellationException

/** Small adapters that keep feature code from assembling unbounded/free-form diagnostics. */
class DiagnosticProducer(private val diagnostics: DiagnosticsFacade, private val component: String) {
    fun event(level: DiagnosticLevel, name: String, fields: Map<String, String> = emptyMap()) {
        diagnostics.event(level, "$component.$name", fields)
    }

    fun success(name: String, elapsedMs: Long? = null, fields: Map<String, String> = emptyMap()) {
        val bounded = elapsedMs?.coerceIn(0, MAX_DURATION_MS)
        event(DiagnosticLevel.INFO, name, fields + listOfNotNull(bounded?.let { "elapsedMs" to it.toString() }).toMap())
    }

    fun failure(name: String, error: Throwable, fields: Map<String, String> = emptyMap()) {
        if (error is CancellationException) throw error
        event(DiagnosticLevel.ERROR, name, fields + ("errorType" to (error::class.simpleName ?: "Exception")))
    }

    private companion object { const val MAX_DURATION_MS = 86_400_000L }
}

object DiagnosticProducers {
    fun thumbnail(diagnostics: DiagnosticsFacade) = DiagnosticProducer(diagnostics, "thumbnail")
    fun download(diagnostics: DiagnosticsFacade) = DiagnosticProducer(diagnostics, "download")
    fun scan(diagnostics: DiagnosticsFacade) = DiagnosticProducer(diagnostics, "scan")
    fun reader(diagnostics: DiagnosticsFacade) = DiagnosticProducer(diagnostics, "reader")
}
