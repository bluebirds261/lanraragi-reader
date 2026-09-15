package com.lanraragi.reader.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class SearchSessionStateTest {

    /**
     * 提交后搜索面直接收起：结果由图库页展示（同一套 LibraryResultsContent），
     * 搜索面不再有「结果态」。
     */
    @Test fun submittingClosesThePanelAndKeepsTheCommittedQuery() {
        val session = SearchSessionState()
        session.open("")
        session.accept("first")
        assertFalse(session.expanded)
        assertEquals("first", session.committed)
        assertEquals("first", session.draft.text)
    }

    @Test fun cancellingDraftRestoresCommittedQueryAndCloses() {
        val session = SearchSessionState()
        session.open("")
        session.accept("first")
        session.open("first")
        session.edit(TextFieldValue("second"))
        assertEquals("first", session.committed)
        session.back()
        assertEquals("first", session.draft.text)
        assertEquals(SearchPhase.CLOSED, session.phase)
        assertFalse(session.expanded)
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

    /** 系统回收后重建：草稿选区与已提交查询都要原样恢复。 */
    @Test fun savedStateRoundTrips() {
        val session = SearchSessionState()
        session.open("语言:汉语$")
        session.edit(TextFieldValue("语言:汉语$,art", TextRange(9)))
        val scope = SaverScope { true }
        val saved = with(SearchSessionState.Saver) { scope.save(session) }
        val restored = requireNotNull(SearchSessionState.Saver.restore(requireNotNull(saved)))
        assertEquals(SearchPhase.EDITING, restored.phase)
        assertEquals("语言:汉语$,art", restored.draft.text)
        assertEquals(9, restored.draft.selection.start)
        assertEquals("语言:汉语$", restored.committed)
    }
}
