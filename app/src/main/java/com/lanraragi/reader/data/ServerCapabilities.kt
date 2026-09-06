package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.ServerInfo
import kotlinx.coroutines.CancellationException

/**
 * 服务器能力门控。基于 `/api/info` 返回的 [ServerInfo] 做版本/特性判断，
 * 供 A 系列各入口按服务器能力显隐入口或调整行为。
 */
data class ServerCapabilities(val info: ServerInfo?) {

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

    /** 服务端是否记录阅读进度。 */
    val supportsProgress: Boolean get() = info?.server_tracks_progress == true

    /** 服务端是否自动压缩图片后再下发。 */
    val resizesImages: Boolean get() = info?.server_resizes_images == true

    /** 是否已成功拉取到服务器信息（即当前连接可用）。 */
    val isConnected: Boolean get() = info != null
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
