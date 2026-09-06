package com.lanraragi.reader.data

/**
 * 实验性功能开关（E5）：统一维护开关 key 常量与中文展示标签。
 * 各功能入口通过 [isEnabled] 读取开关，集中控制显隐。
 * 默认关：featureFlags 未出现的 key 一律视为 false。
 */
object FeatureFlags {
    const val THUMBNAILS = "a5_thumbnails"
    const val MULTI_SELECT = "d2_multi_select"
    const val TANKOUBONS = "a11_tankoubons"

    val allFlags = listOf(
        THUMBNAILS to "缩略图体系",
        MULTI_SELECT to "批量多选",
        TANKOUBONS to "单行本/卷",
    )

    fun isEnabled(settings: Settings, key: String): Boolean = settings.featureFlags[key] == true
}
