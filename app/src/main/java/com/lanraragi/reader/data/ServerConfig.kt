package com.lanraragi.reader.data

/**
 * 运行时服务器配置。由 [com.lanraragi.reader.data.api.ServerInterceptor] 读取，
 * 用于在请求时动态替换 host 并注入 Authorization 头。
 */
class ServerConfig {
    @Volatile
    var baseUrl: String = ""

    @Volatile
    var apiKey: String = ""

    val isConfigured: Boolean
        get() = baseUrl.trim().startsWith("http")
}
