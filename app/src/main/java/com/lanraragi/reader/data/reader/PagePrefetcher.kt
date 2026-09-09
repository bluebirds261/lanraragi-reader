package com.lanraragi.reader.data.reader

fun interface PagePrefetcher {
    /** Returns a handle whose close cancels this prefetch request. */
    fun prefetch(model: PageModel): AutoCloseable
}
