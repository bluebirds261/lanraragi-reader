package com.lanraragi.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 封面破缓存必须**按档案**生效。
 *
 * 旧实现是一个全局 version，展示层把它追加到每一张封面 URL 上，
 * 于是换一次封面会让全库封面的内存/磁盘缓存键同时失效、整屏重新下载。
 */
class CoverChangeBusTest {

    @Test
    fun unknownArchiveHasNoCacheBustingParameter() {
        assertEquals(0, CoverChangeBus.versionOf("never-changed"))
    }

    @Test
    fun onlyTheChangedArchiveGetsANewVersion() {
        CoverChangeBus.notifyChanged("arcid-a")
        assertEquals(1, CoverChangeBus.versionOf("arcid-a"))
        // 其它档案不受影响：URL 不变，缓存命中。
        assertEquals(0, CoverChangeBus.versionOf("arcid-b"))
        assertEquals(0, CoverChangeBus.versionOf("arcid-c"))
    }

    @Test
    fun repeatedChangesAdvanceOnlyThatArchive() {
        val before = CoverChangeBus.versionOf("arcid-repeat")
        CoverChangeBus.notifyChanged("arcid-repeat")
        CoverChangeBus.notifyChanged("arcid-repeat")
        assertEquals(before + 2, CoverChangeBus.versionOf("arcid-repeat"))
        assertEquals(0, CoverChangeBus.versionOf("arcid-untouched"))
    }

    @Test
    fun blankArchiveIdIsIgnored() {
        CoverChangeBus.notifyChanged("")
        CoverChangeBus.notifyChanged("   ")
        assertEquals(0, CoverChangeBus.versionOf(""))
    }
}
