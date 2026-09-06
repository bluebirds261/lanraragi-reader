package com.lanraragi.reader.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow

/** 跨屏幕轻量通信：详情页点标签 → 图库按标签过滤；删除档案后 → 图库刷新。 */
object FilterBus {
    val filter = MutableStateFlow<String?>(null)
}

object LibraryRefreshBus {
    val tick = MutableStateFlow(0)
}

/** 换封面成功后自增，图库卡片用它在封面 URL 上追加版本参数，破 Coil 缓存立即刷新。 */
object CoverChangeBus {
    val version = MutableStateFlow(0)
}

/** 搜索页提交的关键词 → 图库按关键词过滤。 */
object SearchBus {
    val query = MutableStateFlow<String?>(null)
}

/** D2 多选模式是否激活：MainScreen 据此隐藏液态底栏。 */
object SelectionModeBus {
    val active = MutableStateFlow(false)
}

/** D5 空态入口：请求跨屏切换到主 Tab（1=下载页），MainScreen 消费后复位。 */
object MainTabBus {
    val target = MutableStateFlow<Int?>(null)
}

/** D5 空态入口：进入下载页后切到「本地」子 tab（DownloadScreen 消费后复位）。 */
object DownloadSubTabBus {
    val local = MutableStateFlow(false)
}

/** E7 深链/分享：外部 intent 传入的档案 arcid，AppRoot 消费后复位。 */
object DeepLinkBus {
    val arcid = MutableStateFlow<String?>(null)
}
