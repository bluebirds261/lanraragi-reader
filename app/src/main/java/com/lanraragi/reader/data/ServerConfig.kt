package com.lanraragi.reader.data

import com.lanraragi.reader.data.model.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 运行时服务器配置。由 [com.lanraragi.reader.data.api.ServerInterceptor] 读取，
 * 用于在请求时动态替换 host 并注入 Authorization 头。
 */
class ServerConfig {
    private val _serverKey = MutableStateFlow("")
    val serverKey: StateFlow<String> = _serverKey.asStateFlow()

    @Volatile
    var baseUrl: String = ""
        set(value) {
            field = value
            _serverKey.value = value
        }

    @Volatile
    var apiKey: String = ""

    val isConfigured: Boolean
        get() = baseUrl.trim().startsWith("http")

    /** `/api/info` 拉取到的服务器信息缓存(可观察)。null 表示尚未拉取或拉取失败。 */
    val serverInfo = MutableStateFlow<ServerInfo?>(null)

    fun setServerInfo(info: ServerInfo?) {
        serverInfo.value = info
    }
}
