package com.lanraragi.reader.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 搜索面只有两种状态：收起、编辑中。
 *
 * 这里**没有「结果」状态**：提交后搜索面直接收起，结果显示在图库页本身
 * （见 LibraryScreen 的 submitDraft）。之前的「结果内嵌在展开层里」在真机上看下来
 * 与收起后返回的首页几乎一模一样 —— 同一套 LibraryResultsContent、同一份滚动位置、
 * 同一批操作，等于把同一个列表在两层里各挂一次，多出来的只有一层动画和一份
 * 「哪一层在响应手势」的歧义。
 */
enum class SearchPhase { CLOSED, EDITING }

/** A page-owned editor. Draft changes never mutate the committed library request. */
@Stable
class SearchSessionState {
    var phase by mutableStateOf(SearchPhase.CLOSED)
    var draft by mutableStateOf(TextFieldValue())
    var committed by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    val expanded get() = phase != SearchPhase.CLOSED

    fun open(query: String) {
        committed = query
        draft = TextFieldValue(query, TextRange(query.length))
        error = null
        phase = SearchPhase.EDITING
    }

    fun edit(value: TextFieldValue) { draft = value; error = null; phase = SearchPhase.EDITING }

    /** 提交：记下已提交查询并收起。结果由图库页展示，搜索面不再保留结果态。 */
    fun accept(query: String) {
        committed = query
        draft = TextFieldValue(query, TextRange(query.length))
        error = null
        phase = SearchPhase.CLOSED
    }

    /** 返回：丢弃草稿、恢复已提交查询并收起。 */
    fun back() {
        error = null
        draft = TextFieldValue(committed, TextRange(committed.length))
        phase = SearchPhase.CLOSED
    }

    companion object {
        val Saver = listSaver<SearchSessionState, Any>(
            save = { listOf(it.phase.name, it.draft.text, it.draft.selection.start, it.draft.selection.end, it.committed) },
            restore = { values -> SearchSessionState().apply {
                phase = SearchPhase.valueOf(values[0] as String)
                draft = TextFieldValue(values[1] as String, TextRange(values[2] as Int, values[3] as Int))
                committed = values[4] as String
            } },
        )
    }
}
