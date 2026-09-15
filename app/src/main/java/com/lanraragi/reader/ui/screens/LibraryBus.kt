package com.lanraragi.reader.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨屏幕轻量通信的中枢。
 *
 * **每个总线在这里只能有一份定义。** 历史上 `FilterBus` 与 `LibraryRefreshBus` 在
 * `com.lanraragi.reader.ui` 与 `com.lanraragi.reader.ui.screens` 各有一份同名 object，
 * 详情页/阅读器写了 `ui.*`，图库收的是 `screens.*` —— 于是「详情页点标签回图库过滤」
 * 「收藏 / 改标签 / 阅读进度之后刷新图库」这些跨屏动作静默失效，日志里也不会报错。
 * 重复定义已删除，统一到本文件。
 */

object FilterBus {
    val filter = MutableStateFlow<String?>(null)
}

object LibraryRefreshBus {
    val tick = MutableStateFlow(0)
}

// 封面破缓存总线已统一到 com.lanraragi.reader.ui.CoverChangeBus（按 arcid 记录，
// 避免换一张封面就让全库封面缓存失效）。此处原有的同名重复定义已删除。

/** D2 多选模式是否激活：MainScreen 据此隐藏液态底栏。 */
object SelectionModeBus {
    val active = MutableStateFlow(false)
}

/** D5 空态入口：请求跨屏切换到主 Tab（1=下载页），MainScreen 消费后复位。 */
object MainTabBus {
    /** 底栏第 2 键 = 下载页 Tab 的索引（MainScreen 的 mainTabs 顺序：首页/下载/设置）。 */
    const val DOWNLOAD_TAB = 1
    const val SETTINGS_TAB = 2
    fun requestSettingsTab() { target.value = SETTINGS_TAB }

    val target = MutableStateFlow<Int?>(null)

    /** 请求切到下载 Tab（库页空态「扫描本地文件」等入口使用），由 MainScreen 收集后 onTabChange 执行并复位。 */
    fun requestDownloadTab() {
        target.value = DOWNLOAD_TAB
    }
}

/** D5 空态入口：进入下载页后切到「本地」子 tab（DownloadScreen 消费后复位）。 */
object DownloadSubTabBus {
    val local = MutableStateFlow(false)
}

/**
 * D5 空态入口：库页空态的「随机一本」请求主壳呼出第 4 键的「续读 / 随机」共用抽屉，
 * 并切到随机模式（随机范围继承库页当前筛选上下文，经 FilterContextBus 传递）。
 *
 * 随机入口统一收敛到该抽屉，库页不再直接取一本随机档案跳阅读器——
 * 那会产生第二个语义不同的随机入口（忽略用户当前搜索 / 标签 / 仅新 / 隐藏读完 条件）。
 * MainScreen 收集后复位，避免重复消费。
 */
object ReaderDrawerBus {
    val random = MutableStateFlow(false)

    /** 请求主壳以随机模式打开「续读 / 随机」抽屉。 */
    fun requestRandom() {
        random.value = true
    }
}

/** E7 深链/分享：外部 intent 传入的档案 arcid，AppRoot 消费后复位。 */
object DeepLinkBus {
    val arcid = MutableStateFlow<String?>(null)
}

/** 库页当前筛选上下文：供主壳随机抽屉等跨屏消费者复用库页的同一套筛选条件。 */
data class LibraryFilterContext(
    val category: String?,
    val filter: String?,
    val newOnly: Boolean,
    val untaggedOnly: Boolean,
    val hideCompleted: Boolean,
    /**
     * 库页标签筛选面板选中的标签。服务端 filter 语法里标签要写成精确匹配 `tag$`，
     * 与关键词用逗号连接，因此随机抽屉需要拿到原始标签列表才能复现同样的命中范围。
     */
    val tags: List<String> = emptyList(),
)

/** 库页筛选状态总线：LibraryViewModel 在筛选每次变化时上报，主壳随机抽屉消费。 */
object FilterContextBus {
    private val _context = MutableStateFlow(LibraryFilterContext(null, null, false, false, false))
    val context: StateFlow<LibraryFilterContext> = _context.asStateFlow()
    fun update(context: LibraryFilterContext) { _context.value = context }
}

/**
 * 把筛选上下文翻译成服务端 `/api/search` 的 filter 串：
 * 关键词原样透传，标签按 `tag$` 精确匹配追加，用逗号连接（与 LibraryQuery.remoteRequest 一致）。
 */
fun LibraryFilterContext.toServerFilter(): String? = buildList {
    filter?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    tags.asSequence().map(String::trim).filter(String::isNotEmpty).forEach { add("$it$") }
}.joinToString(",").takeIf(String::isNotEmpty)
