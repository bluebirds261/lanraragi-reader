package com.lanraragi.reader.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class SearchPhase { CLOSED, EDITING, RESULTS }

/** A page-owned editor. Draft changes never mutate the committed library request. */
@Stable
class SearchSessionState {
    var phase by mutableStateOf(SearchPhase.CLOSED)
    var draft by mutableStateOf(TextFieldValue())
    var hasResults by mutableStateOf(false)
    var committed by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    val expanded get() = phase != SearchPhase.CLOSED
    val submitted get() = phase == SearchPhase.RESULTS

    fun open(query: String) {
        committed = query
        draft = TextFieldValue(query, TextRange(query.length))
        hasResults = query.isNotBlank()
        error = null
        phase = SearchPhase.EDITING
    }
    fun edit(value: TextFieldValue) { draft = value; error = null; phase = SearchPhase.EDITING }
    fun accept(query: String) {
        committed = query
        draft = TextFieldValue(query, TextRange(query.length))
        hasResults = true
        error = null
        phase = SearchPhase.RESULTS
    }
    fun back() {
        error = null
        draft = TextFieldValue(committed, TextRange(committed.length))
        phase = if (phase == SearchPhase.EDITING && hasResults) SearchPhase.RESULTS else SearchPhase.CLOSED
    }
    companion object {
        val Saver = listSaver<SearchSessionState, Any>(
            save = { listOf(it.phase.name, it.draft.text, it.draft.selection.start, it.draft.selection.end, it.hasResults, it.committed) },
            restore = { values -> SearchSessionState().apply {
                phase = SearchPhase.valueOf(values[0] as String)
                draft = TextFieldValue(values[1] as String, TextRange(values[2] as Int, values[3] as Int))
                hasResults = values[4] as Boolean
                committed = values[5] as String
            } },
        )
    }
}
