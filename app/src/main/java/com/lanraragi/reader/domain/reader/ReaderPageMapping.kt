package com.lanraragi.reader.domain.reader

object ReaderPageMapping {
    fun uiPageToApiPage(uiPage: Int): Int = uiPage.coerceAtLeast(0) + 1

    fun apiPageToUiPage(apiPage: Int): Int = (apiPage - 1).coerceAtLeast(0)

    fun screenCount(pageCount: Int, pagesPerScreen: Int, firstPageAlone: Boolean = false): Int {
        if (pageCount <= 0) return 0
        val span = pagesPerScreen.coerceAtLeast(1)
        if (!firstPageAlone || span == 1) return (pageCount + span - 1) / span
        return 1 + ((pageCount - 1) + span - 1) / span
    }

    fun pageToScreen(
        page: Int,
        pageCount: Int,
        pagesPerScreen: Int,
        firstPageAlone: Boolean = false,
    ): Int {
        if (pageCount <= 0) return 0
        val safePage = page.coerceIn(0, pageCount - 1)
        val span = pagesPerScreen.coerceAtLeast(1)
        if (!firstPageAlone || span == 1) return safePage / span
        return if (safePage == 0) 0 else 1 + (safePage - 1) / span
    }

    fun screenToFirstPage(
        screen: Int,
        pageCount: Int,
        pagesPerScreen: Int,
        firstPageAlone: Boolean = false,
    ): Int {
        if (pageCount <= 0) return 0
        val span = pagesPerScreen.coerceAtLeast(1)
        val safeScreen = screen.coerceIn(0, screenCount(pageCount, span, firstPageAlone) - 1)
        val page = if (!firstPageAlone || span == 1) safeScreen * span else if (safeScreen == 0) 0 else 1 + (safeScreen - 1) * span
        return page.coerceAtMost(pageCount - 1)
    }

    fun pagesForScreen(
        screen: Int,
        pageCount: Int,
        pagesPerScreen: Int,
        firstPageAlone: Boolean = false,
    ): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY
        val start = screenToFirstPage(screen, pageCount, pagesPerScreen, firstPageAlone)
        val count = if (firstPageAlone && screen == 0) 1 else pagesPerScreen.coerceAtLeast(1)
        return start..(start + count - 1).coerceAtMost(pageCount - 1)
    }
}
