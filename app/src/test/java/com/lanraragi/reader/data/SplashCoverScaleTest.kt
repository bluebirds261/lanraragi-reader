package com.lanraragi.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 开屏封面落盘尺寸的纯逻辑单测。
 *
 * [SplashCoverStore] 本体依赖 `Context` / `ImageDecoder`，只能在真机或 Robolectric 下跑；
 * 这里把「按最长边等比缩放、绝不放大」这条容易写错的规则抽出来单独钉住。
 */
class SplashCoverScaleTest {

    @Test
    fun downscalesLandscapeToMaxEdge() {
        assertEquals(1600 to 900, SplashCoverStore.scaledSize(4000, 2250, 1600))
    }

    @Test
    fun downscalesPortraitToMaxEdge() {
        assertEquals(900 to 1600, SplashCoverStore.scaledSize(2250, 4000, 1600))
    }

    @Test
    fun neverUpscalesSmallImages() {
        assertEquals(800 to 600, SplashCoverStore.scaledSize(800, 600, 1600))
    }

    @Test
    fun keepsExactMaxEdgeUntouched() {
        assertEquals(1600 to 1600, SplashCoverStore.scaledSize(1600, 1600, 1600))
    }

    @Test
    fun squareImageScalesOnBothAxes() {
        assertEquals(1600 to 1600, SplashCoverStore.scaledSize(6000, 6000, 1600))
    }

    @Test
    fun neverRoundsDownToZero() {
        // 极端长条：短边按比例会小于 1px，必须夹到 1，否则 setTargetSize 会抛异常。
        assertEquals(1600 to 1, SplashCoverStore.scaledSize(20000, 5, 1600))
    }

    @Test
    fun invalidSizeIsReportedAsZero() {
        assertEquals(0 to 0, SplashCoverStore.scaledSize(0, 1200, 1600))
        assertEquals(0 to 0, SplashCoverStore.scaledSize(1200, -1, 1600))
    }
}
