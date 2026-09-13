package com.lanraragi.reader.ui.reader

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 点击分区可触发的动作。 */
enum class TapZoneAction(val label: String) { PREV("上一页"), NEXT("下一页"), MENU("菜单"), NONE("无") }

/**
 * 3×3 点击分区布局（行主序）。
 *
 * fx/fy ∈ 0..1（相对点击区域宽高）；colRatio 为左列宽占比、rowRatio 为上行高占比，
 * 右列/下行对称取 (1 - ratio)；rtl=true 时左右列镜像；越界值一律 clamp。
 */
@kotlinx.serialization.Serializable
data class TapZoneGrid(
    val colRatio: Float = 0.33f,   // 左列宽占比，clamp 0.15f..0.5f（右列对称）
    val rowRatio: Float = 0.33f,   // 上行高占比，clamp 0.15f..0.5f（下行对称）
    val cells: List<Int> = DEFAULT_CELLS, // 9 项，行主序，取值=TapZoneAction.ordinal
) {
    /** fx/fy ∈ 0..1（相对点击区域宽高）；rtl=true 时左右列镜像；越界 clamp。 */
    fun actionAt(fx: Float, fy: Float, rtl: Boolean = false): TapZoneAction {
        val x = fx.coerceIn(0f, 1f)
        val y = fy.coerceIn(0f, 1f)
        val column = when {
            x < safeColRatio -> 0
            x > 1f - safeColRatio -> 2
            else -> 1
        }
        val row = when {
            y < safeRowRatio -> 0
            y > 1f - safeRowRatio -> 2
            else -> 1
        }
        val effectiveColumn = when {
            rtl && column == 0 -> 2
            rtl && column == 2 -> 0
            else -> column
        }
        return safeCells[row * GRID_SIZE + effectiveColumn]
    }

    fun serialized(): String = TAP_ZONE_JSON.encodeToString(this)

    private val safeColRatio: Float get() = colRatio.coerceIn(MIN_RATIO, MAX_RATIO)
    private val safeRowRatio: Float get() = rowRatio.coerceIn(MIN_RATIO, MAX_RATIO)
    private val safeCells: List<TapZoneAction>
        get() {
            val raw = cells.takeIf { it.size == CELL_COUNT } ?: DEFAULT_CELLS
            return List(CELL_COUNT) { i -> TapZoneAction.entries.getOrElse(raw[i]) { TapZoneAction.NONE } }
        }

    companion object {
        private val ROW_TEMPLATE = listOf(
            TapZoneAction.PREV.ordinal,
            TapZoneAction.MENU.ordinal,
            TapZoneAction.NEXT.ordinal,
        )

        /** 三列全行 [PREV,MENU,NEXT]×3 行。 */
        val DEFAULT_CELLS: List<Int> = List(3) { ROW_TEMPLATE }.flatten()

        fun default(): TapZoneGrid = TapZoneGrid()

        /** null/非法/长度≠9 → default()。 */
        fun deserialize(json: String?): TapZoneGrid {
            if (json.isNullOrBlank()) return default()
            val decoded = runCatching { TAP_ZONE_JSON.decodeFromString<TapZoneGrid>(json.trim()) }
                .getOrNull() ?: return default()
            if (decoded.cells.size != CELL_COUNT) return default()
            if (decoded.cells.any { TapZoneAction.entries.getOrNull(it) == null }) return default()
            return decoded.copy(
                colRatio = decoded.colRatio.coerceIn(MIN_RATIO, MAX_RATIO),
                rowRatio = decoded.rowRatio.coerceIn(MIN_RATIO, MAX_RATIO),
            )
        }

        private const val GRID_SIZE = 3
        private const val CELL_COUNT = GRID_SIZE * GRID_SIZE
        private const val MIN_RATIO = 0.15f
        private const val MAX_RATIO = 0.5f
        private val TAP_ZONE_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}
