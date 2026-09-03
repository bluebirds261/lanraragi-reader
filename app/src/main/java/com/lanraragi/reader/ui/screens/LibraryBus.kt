package com.lanraragi.reader.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow

/** 跨屏幕轻量通信：详情页点标签 → 图库按标签过滤；删除档案后 → 图库刷新。 */
object FilterBus {
    val filter = MutableStateFlow<String?>(null)
}

object LibraryRefreshBus {
    val tick = MutableStateFlow(0)
}

/** 搜索页提交的关键词 → 图库按关键词过滤。 */
object SearchBus {
    val query = MutableStateFlow<String?>(null)
}
