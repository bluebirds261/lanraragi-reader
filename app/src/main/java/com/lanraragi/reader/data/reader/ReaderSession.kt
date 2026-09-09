package com.lanraragi.reader.data.reader

import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.data.diagnostics.DiagnosticProducers
import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Resolver boundary kept independent from Android UI so sessions are easy to restore and test. */
fun interface ReaderSourceResolver {
    suspend fun resolve(identity: ArchiveIdentity, localUri: android.net.Uri?): PageSource
}

/** Adapter for the existing arcid based resolver. */
class DefaultReaderSourceResolver(
    private val resolver: PageSourceResolver,
) : ReaderSourceResolver {
    override suspend fun resolve(identity: ArchiveIdentity, localUri: android.net.Uri?): PageSource = when (identity) {
        is ArchiveIdentity.Remote -> resolver.resolve(identity.arcid, localUri, identity.serverScope)
        is ArchiveIdentity.LocalSaf -> {
            val uri = android.net.Uri.parse(identity.uri)
            if (SafPageSourceSupport.isDirectory(resolver.context, uri)) {
                SafFolderPageSource(resolver.context, identity).refresh()
            } else {
                SafArchivePageSource(resolver.context, identity).refresh()
            }
        }
        is ArchiveIdentity.Tankoubon -> throw PageSourceUnavailableException("单行本页面源需由调用方提供")
    }
}

data class ReaderSessionState(
    val identity: ArchiveIdentity? = null,
    val source: PageSource? = null,
    val currentPage: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val pageCount: Int get() = source?.pageCount ?: 0
    val isLocal: Boolean get() = identity is ArchiveIdentity.LocalSaf
}

/** Lifecycle owner for one reading session. Opening a new archive cancels the old refresh/preload work. */
class ReaderSession(
    private val resolver: ReaderSourceResolver,
    private val scope: CoroutineScope,
    private val diagnostics: DiagnosticsFacade? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(ReaderSessionState())
    val state: StateFlow<ReaderSessionState> = _state.asStateFlow()
    private var openJob: Job? = null
    private var generation = 0L

    fun open(identity: ArchiveIdentity, localUri: android.net.Uri? = null, initialPage: Int = 0) {
        openJob?.cancel()
        _state.value.source?.close()
        val token = ++generation
        openJob = scope.launch(dispatcher) {
            val startedAt = System.nanoTime()
            _state.value = ReaderSessionState(identity = identity, currentPage = initialPage.coerceAtLeast(0), loading = true)
            try {
                val source = resolver.resolve(identity, localUri)
                if (token != generation) {
                    source.close()
                    return@launch
                }
                _state.value = ReaderSessionState(
                    identity = source.identity,
                    source = source,
                    currentPage = initialPage.coerceIn(0, (source.pageCount - 1).coerceAtLeast(0)),
                )
                diagnostics?.let { facade ->
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                    facade.readerFirstImage(elapsedMs)
                    DiagnosticProducers.reader(facade).success(
                        "source_ready",
                        elapsedMs,
                        mapOf("source" to source.identity::class.java.simpleName, "pages" to source.pageCount.toString()),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics?.let { DiagnosticProducers.reader(it).failure("source_failed", e) }
                _state.value = ReaderSessionState(identity = identity, currentPage = initialPage.coerceAtLeast(0), error = e.message ?: "页面源不可用")
            }
        }
    }

    fun setPage(page: Int) {
        val count = _state.value.pageCount
        _state.value = _state.value.copy(currentPage = page.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }

    fun refresh() {
        val current = _state.value
        val source = current.source ?: return
        openJob?.cancel()
        val token = ++generation
        openJob = scope.launch(dispatcher) {
            try {
                val refreshed = source.recover()
                if (token != generation) {
                    if (refreshed !== source) refreshed.close()
                    return@launch
                }
                _state.value = current.copy(source = refreshed, currentPage = current.currentPage.coerceIn(0, (refreshed.pageCount - 1).coerceAtLeast(0)), error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(error = e.message ?: "刷新页面失败")
            }
        }
    }

    fun close() {
        generation += 1L
        openJob?.cancel()
        openJob = null
        _state.value.source?.close()
        _state.value = ReaderSessionState()
    }
}
