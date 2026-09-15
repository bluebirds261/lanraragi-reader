package com.lanraragi.reader.ui.screens

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class SearchSessionStateTest {
    @Test fun cancellingDraftRestoresCommittedResultsThenCloses() {
        val session = SearchSessionState()
        session.open("")
        session.accept("first")
        session.edit(TextFieldValue("second"))
        assertEquals("first", session.committed)
        session.back()
        assertEquals("first", session.draft.text)
        assertEquals(SearchPhase.RESULTS, session.phase)
        session.back()
        assertFalse(session.expanded)
        assertEquals("first", session.committed)
    }
    @Test fun unsubmittedDraftNeverBecomesAnAppliedQuery() {
        val session = SearchSessionState()
        session.open("")
        session.edit(TextFieldValue("draft"))
        session.back()
        assertFalse(session.expanded)
        assertEquals("", session.committed)
        assertEquals("", session.draft.text)
    }
}
