package com.lanraragi.reader.data.api

import com.lanraragi.reader.data.ApiException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 网络错误 → 用户可读中文文案的集中映射，供 Repository 层的 network/ensureSuccess
 * 及手写 okhttp 调用的失败分支统一复用，避免逐处拼接裸异常 message。
 */
fun toUserMessage(e: Throwable): String = when (e) {
    is ApiException -> e.message ?: "未知错误"
    is SocketTimeoutException -> "连接超时，请检查网络"
    is UnknownHostException -> "无法解析服务器地址"
    is ConnectException -> "无法连接到服务器"
    is SSLException -> "SSL 证书错误"
    is IOException -> "网络错误"
    else -> e.message ?: "未知错误"
}

/** 按 HTTP 状态码映射用户可读中文文案。 */
fun toUserMessage(code: Int): String = when {
    code == 401 -> "鉴权失败，请检查 API Key"
    code == 403 -> "无权限访问"
    code == 404 -> "档案不存在"
    code >= 500 -> "服务器错误（HTTP $code）"
    else -> "请求失败（HTTP $code）"
}
