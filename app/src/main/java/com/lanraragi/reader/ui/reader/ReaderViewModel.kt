package com.lanraragi.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanraragi.reader.data.reader.NoopProgressWriter
import com.lanraragi.reader.data.reader.PrefetchCoordinator
import com.lanraragi.reader.data.reader.ProgressWriter
import com.lanraragi.reader.data.reader.ReaderSession
import com.lanraragi.reader.data.reader.ReaderSessionState
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Thin UI coordinator; rendering remains in the existing screen until route migration. */
class ReaderViewModel(
    private val session: ReaderSession,
    private val progressWriter: ProgressWriter = NoopProgressWriter,
    prefetchCoordinator: PrefetchCoordinator? = null,
    private val pagePrefetcher: com.lanraragi.reader.data.reader.PagePrefetcher? = null,
) : ViewModel() {
    private val prefetch = prefetchCoordinator ?: PrefetchCoordinator(viewModelScope)
    val state: StateFlow<ReaderSessionState> = session.state

    fun open(identity: ArchiveIdentity, localUri: android.net.Uri? = null, initialPage: Int = 0) = session.open(identity, localUri, initialPage)

    fun selectPage(page: Int) {
        session.setPage(page)
        val current = state.value
        current.identity?.let { identity ->
            viewModelScope.launch { progressWriter.record(identity, current.currentPage, current.pageCount) }
        }
        val source = current.source
        val loader = pagePrefetcher
        if (source != null && loader != null) {
            prefetch.updateModels(page, current.pageCount, model = source::pageModel, prefetcher = loader)
        }
    }

    fun refresh() = session.refresh()

    override fun onCleared() {
        prefetch.cancel()
        session.close()
        super.onCleared()
    }
}
