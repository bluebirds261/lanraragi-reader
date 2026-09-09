package com.lanraragi.reader.data.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailWireParserTest {
    @Test
    fun queuedJobIdAcceptsJobFieldAndIgnoresUnknownFields() {
        assertEquals("job-42", ThumbnailWireParser.queuedJobId("""{"success":1,"job":"job-42","extra":true}"""))
    }

    @Test
    fun queuedJobIdRejectsBlankOrNonObjectPayloads() {
        assertNull(ThumbnailWireParser.queuedJobId("""{"job":"  "}"""))
        assertNull(ThumbnailWireParser.queuedJobId("[]"))
    }

    @Test
    fun jobStateClassifiesFinishedActiveAndFailed() {
        assertEquals(ThumbnailJobState.Finished, ThumbnailWireParser.jobState("""{"state":"done","other":1}"""))
        assertEquals(ThumbnailJobState.Active, ThumbnailWireParser.jobState("""{"state":"running","notes":"working"}"""))
        assertEquals(ThumbnailJobState.Active, ThumbnailWireParser.jobState("""{"state":"inactive"}"""))
        assertEquals(
            ThumbnailJobState.Failed(message = "nope"),
            ThumbnailWireParser.jobState("""{"state":"failed","error":"nope"}"""),
        )
    }

    @Test
    fun malformedOrUnknownJobStateFailsSafely() {
        assertEquals(
            ThumbnailJobState.Failed(message = "Malformed thumbnail job response"),
            ThumbnailWireParser.jobState("not-json"),
        )
        assertEquals(
            ThumbnailJobState.Failed(message = "Unknown thumbnail job state"),
            ThumbnailWireParser.jobState("""{"state":"future-state"}"""),
        )
    }
}
