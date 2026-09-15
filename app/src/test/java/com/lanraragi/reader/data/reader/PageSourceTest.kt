package com.lanraragi.reader.data.reader

import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageSourceTest {
    @Test
    fun remoteSourceLoadsZeroBasedModelsAndStableRevision() = runTest {
        val source = RemotePageSource("arc-1", RemotePageLoader { listOf("https://server/p/1", "https://server/p/2") })

        source.refresh()

        assertEquals(2, source.pageCount)
        assertEquals(
            PageModel.RemotePage("https://server/p/1", 0, thumbnail = false, revision = source.revision),
            source.pageModel(0),
        )
        assertEquals(
            // 必须带 no_fallback=true：服务端在该页缩略图未生成时，不带这个参数会返回
            // public/img/noThumb.png（HTTP 200 + 占位图），客户端无法与真图区分，
            // Coil 会把它当成功结果缓存，之后即使生成好也一直显示「no thumbnail」。
            "http://lanraragi.local/api/archives/arc-1/thumbnail?page=2&no_fallback=true",
            (source.thumbnailModel(1) as PageModel.RemotePage).url,
        )
        assertEquals(source.revision, source.snapshot().revision)
    }

    @Test
    fun remoteRevisionChangesWhenPageListChanges() = runTest {
        var pages = listOf("https://server/p/1")
        val source = RemotePageSource("arc-1", RemotePageLoader { pages })

        source.refresh()
        val firstRevision = source.revision
        pages = listOf("https://server/p/1", "https://server/p/2")
        source.recover()

        assertNotEquals(firstRevision, source.revision)
    }

    @Test
    fun pageModelsRejectIndexesOutsideLoadedRange() = runTest {
        val source = RemotePageSource("arc-1", RemotePageLoader { listOf("https://server/p/1") })
        source.refresh()

        assertThrows(IndexOutOfBoundsException::class.java) { source.pageModel(1) }
        assertThrows(IndexOutOfBoundsException::class.java) { source.pageModel(-1) }
    }

    @Test
    fun sourceIdentityIsPreserved() {
        val identity = ArchiveIdentity.Remote("arc-1")
        val source = RemotePageSource(identity, RemotePageLoader { emptyList() })

        assertEquals(identity, source.identity)
    }
}
