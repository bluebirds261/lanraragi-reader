package com.lanraragi.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapZoneConfigTest {

    @Test
    fun defaultCellsAreThreeRowsOfPrevMenuNext() {
        val expected = listOf(
            TapZoneAction.PREV.ordinal, TapZoneAction.MENU.ordinal, TapZoneAction.NEXT.ordinal,
            TapZoneAction.PREV.ordinal, TapZoneAction.MENU.ordinal, TapZoneAction.NEXT.ordinal,
            TapZoneAction.PREV.ordinal, TapZoneAction.MENU.ordinal, TapZoneAction.NEXT.ordinal,
        )
        assertEquals(expected, TapZoneGrid.DEFAULT_CELLS)
        assertEquals(9, TapZoneGrid.default().cells.size)
        assertEquals(expected, TapZoneGrid.default().cells)
    }

    @Test
    fun defaultGridHitsPrevMenuNext() {
        val grid = TapZoneGrid.default()
        // 左上 → PREV
        assertEquals(TapZoneAction.PREV, grid.actionAt(0.1f, 0.1f))
        // 正中 → MENU
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.5f, 0.5f))
        // 右下 → NEXT
        assertEquals(TapZoneAction.NEXT, grid.actionAt(0.9f, 0.9f))
        // 每一行布局一致：左/中/右
        for (row in 0..2) {
            val y = (row + 0.5f) / 3f
            assertEquals(TapZoneAction.PREV, grid.actionAt(0.1f, y))
            assertEquals(TapZoneAction.MENU, grid.actionAt(0.5f, y))
            assertEquals(TapZoneAction.NEXT, grid.actionAt(0.9f, y))
        }
    }

    @Test
    fun rtlMirrorsLeftAndRightColumns() {
        val grid = TapZoneGrid.default()
        assertEquals(TapZoneAction.NEXT, grid.actionAt(0.1f, 0.1f, rtl = true))
        assertEquals(TapZoneAction.PREV, grid.actionAt(0.9f, 0.9f, rtl = true))
        // 中列不受镜像影响
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.5f, 0.5f, rtl = true))
    }

    @Test
    fun customRatiosChangeColumnBoundaries() {
        val grid = TapZoneGrid(colRatio = 0.5f, rowRatio = 0.5f)
        assertEquals(TapZoneAction.PREV, grid.actionAt(0.49f, 0.49f))
        assertEquals(TapZoneAction.NEXT, grid.actionAt(0.51f, 0.51f))
        // 恰好落在分界线上视为中列/中行
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.5f, 0.5f))
    }

    @Test
    fun narrowRatiosKeepMinimumMiddleZone() {
        val grid = TapZoneGrid(colRatio = 0.05f, rowRatio = 0.05f)
        // clamp 到 0.15：0.1 仍属左列/上行，0.2 属中列/中行
        assertEquals(TapZoneAction.PREV, grid.actionAt(0.1f, 0.1f))
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.2f, 0.2f))
        assertEquals(TapZoneAction.NEXT, grid.actionAt(0.9f, 0.9f))
    }

    @Test
    fun customCellsChangeActionPerCell() {
        val noneEverywhere = List(9) { TapZoneAction.NONE.ordinal }
        val grid = TapZoneGrid(cells = noneEverywhere.toMutableList().also { it[4] = TapZoneAction.PREV.ordinal })
        assertEquals(TapZoneAction.PREV, grid.actionAt(0.5f, 0.5f))
        assertEquals(TapZoneAction.NONE, grid.actionAt(0.1f, 0.1f))
        assertEquals(TapZoneAction.NONE, grid.actionAt(0.9f, 0.9f))
    }

    @Test
    fun outOfRangeCoordinatesAreClamped() {
        val grid = TapZoneGrid.default()
        assertEquals(TapZoneAction.PREV, grid.actionAt(-1f, -1f))
        assertEquals(TapZoneAction.NEXT, grid.actionAt(2f, 2f))
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.5f, -3f))
        assertEquals(TapZoneAction.PREV, grid.actionAt(-3f, 0.5f))
    }

    @Test
    fun serializedRoundTrips() {
        val grid = TapZoneGrid(colRatio = 0.4f, rowRatio = 0.2f, cells = List(9) { i -> (i % 4) })
        val restored = TapZoneGrid.deserialize(grid.serialized())
        assertEquals(grid, restored)
    }

    @Test
    fun deserializeFallsBackToDefaultOnIllegalInput() {
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize(null))
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize(""))
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize("   "))
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize("not a json"))
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize("{"))
        // JSON 数组而非对象
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize("[]"))
    }

    @Test
    fun deserializeKeepsDefaultCellsWhenCellsFieldIsMissing() {
        // 缺省字段沿用默认值：仅覆盖 colRatio，其余保持默认
        val grid = TapZoneGrid.deserialize("{\"colRatio\":0.4}")
        assertEquals(0.4f, grid.colRatio, 1e-6f)
        assertEquals(TapZoneGrid.default().rowRatio, grid.rowRatio, 1e-6f)
        assertEquals(TapZoneGrid.default().cells, grid.cells)
    }

    @Test
    fun deserializeFallsBackWhenCellCountIsNotNine() {
        val tenCells = "{\"colRatio\":0.4,\"cells\":[0,1,2,0,1,2,0,1,2,2]}"
        val eightCells = "{\"colRatio\":0.4,\"cells\":[0,1,2,0,1,2,0,1]}"
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize(tenCells))
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize(eightCells))
    }

    @Test
    fun deserializeFallsBackWhenCellValueIsOutOfRange() {
        val invalid = "{\"cells\":[0,1,2,0,1,2,0,1,7]}"
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize(invalid))
    }

    @Test
    fun deserializeClampsOutOfRangeRatios() {
        val raw = "{\"colRatio\":0.9,\"rowRatio\":-0.2,\"cells\":[0,1,2,0,1,2,0,1,2]}"
        val grid = TapZoneGrid.deserialize(raw)
        assertEquals(0.5f, grid.colRatio, 1e-6f)
        assertEquals(0.15f, grid.rowRatio, 1e-6f)
        // clamp 后：x=0.6 落入右列（x>0.5），y=0.6 仍属中行（0.15..0.85）→ 该格 ordinal 2 = MENU
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.6f, 0.6f))
        // 左列 + 上行 → PREV
        assertEquals(TapZoneAction.PREV, grid.actionAt(0.05f, 0.05f))
        // 右列 + 下行（y>0.85）→ ordinal 2 = MENU
        assertEquals(TapZoneAction.MENU, grid.actionAt(0.9f, 0.9f))
    }

    @Test
    fun actionAtFallsBackToDefaultCellsWhenCellsListIsCorrupt() {
        val corrupt = TapZoneGrid(cells = listOf(0, 1, 2))
        // cells 长度≠9 时按默认布局命中，不抛异常
        assertEquals(TapZoneAction.PREV, corrupt.actionAt(0.1f, 0.1f))
        assertEquals(TapZoneAction.MENU, corrupt.actionAt(0.5f, 0.5f))
        assertEquals(TapZoneAction.NEXT, corrupt.actionAt(0.9f, 0.9f))
    }

    @Test
    fun serializedStringIsStableAndParseableJson() {
        val json = TapZoneGrid.default().serialized()
        assertTrue(json.contains("colRatio"))
        assertTrue(json.contains("cells"))
        assertEquals(TapZoneGrid.default(), TapZoneGrid.deserialize(json))
    }
}
