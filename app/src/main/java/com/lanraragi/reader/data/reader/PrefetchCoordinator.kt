package com.lanraragi.reader.data.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Keeps a small, cancellable page window around the visible page. */
class PrefetchCoordinator(
    private val scope: CoroutineScope,
    private val radius: Int = 2,
) {
    private var windowJob: Job? = null
    private var generation = 0L
    private val handles = mutableListOf<AutoCloseable>()

    fun update(center: Int, pageCount: Int, load: suspend (Int) -> Unit) {
        cancelWindow()
        val token = ++generation
        if (pageCount <= 0) return
        val first = (center - radius.coerceAtLeast(0)).coerceAtLeast(0)
        val last = (center + radius.coerceAtLeast(0)).coerceAtMost(pageCount - 1)
        windowJob = scope.launch {
            try {
                coroutineScope {
                    (first..last).map { index -> async { if (token == generation) load(index) } }.awaitAll()
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun updateModels(
        center: Int,
        pageCount: Int,
        radius: Int = this.radius,
        model: (Int) -> PageModel,
        prefetcher: PagePrefetcher,
    ) {
        cancelWindow()
        generation += 1L
        if (pageCount <= 0) return
        val first = (center - radius.coerceAtLeast(0)).coerceAtLeast(0)
        val last = (center + radius.coerceAtLeast(0)).coerceAtMost(pageCount - 1)
        for (index in first..last) {
            handles += prefetcher.prefetch(model(index))
        }
    }

    fun cancel() {
        generation++
        cancelWindow()
    }

    private fun cancelWindow() {
        windowJob?.cancel()
        windowJob = null
        handles.forEach { runCatching { it.close() } }
        handles.clear()
    }
}
