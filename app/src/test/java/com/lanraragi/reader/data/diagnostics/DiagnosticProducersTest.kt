package com.lanraragi.reader.data.diagnostics

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticProducersTest {
    @Test fun producerPrefixesComponentsAndBoundsDurations() {
        val facade = DiagnosticsFacade(log = DiagnosticLog(4))
        DiagnosticProducers.thumbnail(facade).success("ready", Long.MAX_VALUE)
        val event = facade.log.snapshot.value.single()
        assertEquals("thumbnail.ready", event.event)
        assertTrue(event.fields["elapsedMs"]!!.toLong() <= 86_400_000L)
    }

    @Test(expected = CancellationException::class)
    fun producerPreservesCancellation() {
        DiagnosticProducers.reader(DiagnosticsFacade()).failure("cancelled", CancellationException())
    }
}
