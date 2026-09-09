package com.lanraragi.reader.data.reader

import com.lanraragi.reader.domain.reader.ReaderPageMapping

enum class ReaderLayout { Single, Spread }

data class ReaderLayoutSpec(
    val layout: ReaderLayout = ReaderLayout.Single,
    val firstPageAlone: Boolean = false,
) {
    val pagesPerScreen: Int get() = if (layout == ReaderLayout.Spread) 2 else 1
}

object ReaderLayoutAdapter {
    fun screenCount(pageCount: Int, spec: ReaderLayoutSpec): Int =
        ReaderPageMapping.screenCount(pageCount, spec.pagesPerScreen, spec.firstPageAlone)

    fun pageToScreen(page: Int, pageCount: Int, spec: ReaderLayoutSpec): Int =
        ReaderPageMapping.pageToScreen(page, pageCount, spec.pagesPerScreen, spec.firstPageAlone)

    fun pagesForScreen(screen: Int, pageCount: Int, spec: ReaderLayoutSpec): IntRange =
        ReaderPageMapping.pagesForScreen(screen, pageCount, spec.pagesPerScreen, spec.firstPageAlone)

    fun screenToFirstPage(screen: Int, pageCount: Int, spec: ReaderLayoutSpec): Int =
        ReaderPageMapping.screenToFirstPage(screen, pageCount, spec.pagesPerScreen, spec.firstPageAlone)
}
