package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.ServerInfo
import kotlinx.coroutines.CancellationException

/**
 * 服务器能力门控。基于 `/api/info` 返回的 [ServerInfo] 做版本/特性判断，
 * 供 A 系列各入口按服务器能力显隐入口或调整行为。
 */
data class ServerCapabilities(val info: ServerInfo?) {

    companion object {
        /** 尚未拉取到 `/api/info` 时的空能力：版本门控全部关闭。 */
        val UNKNOWN = ServerCapabilities(null)

        /** 由 `/api/info` 的解析结果构造能力门控。 */
        fun from(serverInfo: ServerInfo): ServerCapabilities = ServerCapabilities(serverInfo)
    }

    /** 版本号解析：把 version 按 '.' 分割，取前导数字段（非数字段忽略）。 */
    val versionParts: List<Int>
        get() = info?.version
            ?.split('.')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()

    /** 版本逐段比较；info 为空或解析失败返回 false。 */
    fun atLeast(major: Int, minor: Int = 0, patch: Int = 0): Boolean {
        val target = listOf(major, minor, patch)
        val parts = versionParts
        if (parts.isEmpty()) return false
        for (i in target.indices) {
            val v = parts.getOrElse(i) { 0 }
            if (v > target[i]) return true
            if (v < target[i]) return false
        }
        return true
    }

    /**
     * 服务端是否**接受**进度写入（`PUT /api/archives/{id}/progress/{page}` 与
     * `PUT /api/tankoubons/{id}/progress/{page}`）。
     *
     * 判定不能只看 `server_tracks_progress`。两个端点的实际代码是
     * （`Controller/Api/Archive.pm:450`、`Controller/Api/Tankoubon.pm:245`）：
     *
     * ```perl
     * if ( enable_localprogress && !enable_authprogress ) { 拒绝 }
     * ```
     *
     * 而 `/info` 的映射是 `server_tracks_progress = !enable_localprogress`、
     * `authenticated_progress = enable_authprogress`，因此：
     * **接受 ⟺ `server_tracks_progress || authenticated_progress`**。
     *
     * 只按 `server_tracks_progress` 判定会误判一类常见配置——管理员开了「本地进度」但
     * 同时要求进度鉴权（`localprogress=1, authprogress=1`）：此时服务端照常接受写入，
     * 而单看 `server_tracks_progress == false` 会把回传整体关掉（真机验证时踩到过这个坑）。
     *
     * `/info` 尚未拉到时返回 `true`（乐观）：宁可多发一次请求，也不要因为能力信息还没就绪
     * 而把用户进度一直压在本地。
     */
    val supportsProgress: Boolean
        get() = info?.let { it.server_tracks_progress || it.authenticated_progress } ?: true

    /**
     * 进度回传是否需要鉴权（`/api/info` 的 `authenticated_progress`）。
     * 仅用于错误提示措辞：为 true 时 401 基本等同于「API Key 不对或缺失」。
     */
    val requiresAuthenticatedProgress: Boolean get() = info?.authenticated_progress == true

    /** 服务端是否自动压缩图片后再下发。 */
    val resizesImages: Boolean get() = info?.server_resizes_images == true

    /** 是否已成功拉取到服务器信息（即当前连接可用）。 */
    val isConnected: Boolean get() = info != null

    /** A11 单行本（Tankoubons）API：随服务器 0.9.0 引入。 */
    val supportsTankoubons: Boolean get() = atLeast(0, 9, 0)

    /** A13 档案目录/章节（`/api/archives/{id}/toc`）：随服务器 0.9.7 引入。 */
    val supportsToc: Boolean get() = atLeast(0, 9, 7)

    /** Stamps API：随服务器 0.9.80 引入。 */
    val supportsStamps: Boolean get() = atLeast(0, 9, 80)
}

/**
 * 拉取并缓存服务器信息。连接保存/测试成功后复用，
 * 避免在多处重复实现（成功写入缓存，失败清空）。
 */
suspend fun refreshServerInfo(repository: LanraragiRepository) {
    try {
        ApiClient.config.serverInfo.value = repository.getServerInfo()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiClient.config.serverInfo.value = null
    }
}
