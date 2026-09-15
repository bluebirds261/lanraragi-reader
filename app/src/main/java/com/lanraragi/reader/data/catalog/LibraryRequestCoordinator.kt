package com.lanraragi.reader.data.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

data class LibraryRequestState(
    val generation: Long = 0L,
    val query: LibraryQuery = LibraryQuery(),
    val items: List<LibraryEntry> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
    val warning: String? = null,
    val hasMore: Boolean = true,
    /** Zero-based cursor for the next append; valid only after a successful load. */
    val nextPage: Int = 0,
) {
    /** True only after a successful request returned no rows. */
    val isEmpty: Boolean get() = !loading && !loadingMore && error == null && items.isEmpty()
}

/** UI-facing query contract. Implementations, not screens, own request lifecycle. */
interface LibrarySearchFacade {
    val state: StateFlow<LibraryRequestState>
    fun refresh(query: LibraryQuery): Long
    fun loadMore(): Long?
    fun cancel()
}

/** Cancels obsolete loads and guards every publication with an explicit generation. */
class LibraryRequestCoordinator(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val pageSize: Int = LibraryRepository.DEFAULT_PAGE_SIZE,
    private val debounceMillis: Long = 250L,
) : LibrarySearchFacade {
    private val nextGeneration = AtomicLong(0L)
    private var job: Job? = null
    private val _state = MutableStateFlow(LibraryRequestState())
    override val state: StateFlow<LibraryRequestState> = _state.asStateFlow()

    init {
        require(pageSize > 0) { "pageSize must be positive" }
        require(debounceMillis >= 0L) { "debounceMillis must not be negative" }
    }

    override fun refresh(query: LibraryQuery): Long {
        val generation = nextGeneration.incrementAndGet()
        job?.cancel()
        _state.value = LibraryRequestState(generation = generation, query = query.normalized(), loading = true)
        job = scope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            load(generation, query.normalized(), append = false)
        }
        return generation
    }

    override fun loadMore(): Long? {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return null
        val generation = current.generation
        val query = current.query
        _state.update { it.copy(loadingMore = true, error = null) }
        job = scope.launch { load(generation, query, page = current.nextPage, append = true) }
        return generation
    }

    override fun cancel() {
        val generation = nextGeneration.incrementAndGet()
        job?.cancel()
        job = null
        _state.update { it.copy(generation = generation, loading = false, loadingMore = false, hasMore = false) }
    }

    private suspend fun load(generation: Long, query: LibraryQuery, page: Int = 0, append: Boolean) {
        try {
            val result = repository.load(query, page = page, pageSize = pageSize)
            if (nextGeneration.get() != generation) return
            val merged = if (append) (_state.value.items + result.items).distinctBy(LibraryEntry::sourceKey) else result.items
            _state.update {
                if (it.generation != generation) return@update it
                it.copy(
                    items = merged,
                    total = result.total,
                    warning = result.warning,
                    loading = false,
                    loadingMore = false,
                    hasMore = result.hasMore,
                    nextPage = result.page + 1,
                    error = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (nextGeneration.get() != generation) return
            _state.update { if (it.generation == generation) it.copy(loading = false, loadingMore = false, error = error) else it }
        }
    }
}
