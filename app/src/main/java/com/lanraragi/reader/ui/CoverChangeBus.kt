package com.lanraragi.reader.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 封面被替换后通知 UI 破 Coil 缓存。
 *
 * **按档案**记录，而不是一个全局计数器：旧实现把唯一的 version 追加到**每一张**封面 URL，
 * 于是换一次封面会让全库封面的内存/磁盘缓存键同时失效、整屏重新下载。
 *
 * 用法：
 * - 封面写入成功后调用 [notifyChanged]`(arcid)`；
 * - 展示层用 [versionOf]`(arcid)` 取该档案当前的破缓存参数（从未变过为 0，此时 URL 不带参数）。
 *
 * 说明：状态是进程内的，重启后归零，与旧实现一致；跨进程的封面目标准确性依赖服务器的
 * HTTP 缓存头（缩略图实验开关开启时由 [com.lanraragi.reader.data.assets.ThumbnailRepository]
 * 的 revision 负责）。
 */
object CoverChangeBus {

    private val _versions = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** arcid -> 破缓存版本号（只包含被替换过封面的档案）。 */
    val versions: StateFlow<Map<String, Int>> = _versions.asStateFlow()

    /** 该档案当前应附加的破缓存版本号；0 表示无需附加参数。 */
    fun versionOf(arcid: String): Int = _versions.value[arcid] ?: 0

    /** 封面替换成功后调用：只让这一个档案的封面缓存失效。 */
    fun notifyChanged(arcid: String) {
        if (arcid.isBlank()) return
        _versions.update { current ->
            current + (arcid to (current[arcid] ?: 0) + 1)
        }
    }
}
