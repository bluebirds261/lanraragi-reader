package com.lanraragi.reader.data.api

import android.net.Uri
import com.lanraragi.reader.data.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络层单例。使用占位 host（lanraragi.local）创建 Retrofit/OkHttp，
 * 真正的服务器地址由 [ServerInterceptor] 在每次请求时动态替换。
 * 这样服务器地址/API Key 变更后无需重建客户端，图片加载（Coil）也共用同一客户端。
 */
object ApiClient {

    /** 占位 host：请求此 host 的 URL 会被重写为真实服务器地址。 */
    const val SENTINEL_HOST = "lanraragi.local"
    const val SENTINEL_BASE = "http://lanraragi.local/"

    val config = ServerConfig()

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor())
        .addInterceptor(ServerInterceptor(config))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * 整本下载专用客户端：读超时放宽到 300s，且不加全局重试拦截器与 30s 读超时，
     * 重试由 [com.lanraragi.reader.data.LanraragiRepository.downloadArchive] 手动控制（含断点续传）。
     */
    val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ServerInterceptor(config))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: LanraragiApi = Retrofit.Builder()
        .baseUrl(SENTINEL_BASE)
        .client(okHttpClient)
        .build()
        .create(LanraragiApi::class.java)

    /**
     * 构造单页图片 URL。metadata 返回的 pages 已是 URL 编码后的路径，
     * 这里按原样透传；若路径不含 '%'（如纯 ASCII 文件名）则兜底做一次编码。
     */
    fun pageUrl(arcid: String, rawPath: String, thumbnail: Boolean = false): String {
        val encoded = if (rawPath.contains('%')) rawPath else Uri.encode(rawPath)
        val thumb = if (thumbnail) "&thumbnail=true" else ""
        return "${SENTINEL_BASE}api/archives/$arcid/page?path=$encoded$thumb"
    }

    fun thumbnailUrl(arcid: String): String = "${SENTINEL_BASE}api/archives/$arcid/thumbnail"

    /** 单行本封面（A11）：`GET /api/tankoubons/{id}/thumbnail`。 */
    fun tankoubonThumbnailUrl(id: String): String = "${SENTINEL_BASE}api/tankoubons/$id/thumbnail"

    /** 某页的低分辨率缩略图 URL（省流量模式用；page 从 1 起）。 */
    fun pageThumbnailUrl(arcid: String, pageIndex: Int): String =
        "${SENTINEL_BASE}api/archives/$arcid/thumbnail?page=${pageIndex + 1}"

    /** 把 /files 返回的相对分页 URL 转成绝对地址（占位 host，拦截器会改写为真实服务器）。 */
    fun toAbsoluteUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl
        }
        val p = pathOrUrl.removePrefix("./").removePrefix("/")
        return "$SENTINEL_BASE$p"
    }

    /** 真实的（用户配置的）服务器地址，供非图片类用途展示。 */
    fun displayBaseUrl(): String = config.baseUrl.trim().trimEnd('/')
}

private class ServerInterceptor(private val config: ServerConfig) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = config.baseUrl.trim().trimEnd('/')

        if (base.isEmpty() || !(base.startsWith("http://") || base.startsWith("https://"))) {
            return errorResponse(chain, 503, "尚未配置 LANraragi 服务器地址")
        }

        val baseHttp = base.toHttpUrlOrNull()
            ?: return errorResponse(chain, 400, "服务器地址格式无效")

        val isSentinel = request.url.host == ApiClient.SENTINEL_HOST

        val newRequest = if (isSentinel) {
            val prefix = baseHttp.encodedPath.trimEnd('/')
            val path = if (prefix.isEmpty()) request.url.encodedPath else prefix + request.url.encodedPath
            val newUrl = request.url.newBuilder()
                .scheme(baseHttp.scheme)
                .host(baseHttp.host)
                .port(baseHttp.port)
                .encodedPath(path)
                .build()
            request.newBuilder()
                .url(newUrl)
                .header("Authorization", "Bearer ${config.apiKey}")
                .build()
        } else {
            request.newBuilder()
                .header("Authorization", "Bearer ${config.apiKey}")
                .build()
        }

        return try {
            chain.proceed(newRequest)
        } catch (e: java.util.concurrent.CancellationException) {
            // 协程取消不应被吞掉或转成可重试的 503，直接向上抛出。
            throw e
        } catch (e: IOException) {
            // 网络异常直接向上抛出：交给外层 RetryInterceptor 重试、Repository 的 toUserMessage(e) 映射，
            // 避免把"连接超时/无法解析地址"等真实网络错误折叠成 503"服务器错误"。
            throw e
        } catch (e: Exception) {
            errorResponse(chain, 503, "网络错误: ${e.message}")
        }
    }

    private fun errorResponse(chain: Interceptor.Chain, code: Int, message: String): Response {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(message.toResponseBody("text/plain; charset=utf-8".toMediaType()))
            .build()
    }
}

/**
 * 幂等请求重试拦截器（最外层 application interceptor）。
 * 仅对幂等的 GET/HEAD 重试；POST/PUT/DELETE 一律不重试，避免删除/进度上报等操作重复执行。
 * 重试触发条件：网络异常(IOException)或 5xx(502/503/504)，最多重试 2 次，指数退避 1s、2s。
 * 401/403/404 不重试；[ServerInterceptor] 合成的"尚未配置服务器地址"(503)也不重试。
 * 拦截器是同步的，退避用 Thread.sleep 阻塞当前 OkHttp 线程。
 */
private class RetryInterceptor : Interceptor {

    companion object {
        const val MAX_RETRIES = 2
        val RETRYABLE_CODES = setOf(502, 503, 504)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isIdempotent(request.method)) {
            return chain.proceed(request)
        }

        var retries = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (retries < MAX_RETRIES && shouldRetry(response)) {
                    response.close()
                    retries++
                    Thread.sleep(backoffMillis(retries))
                    continue
                }
                return response
            } catch (e: IOException) {
                if (retries < MAX_RETRIES) {
                    retries++
                    Thread.sleep(backoffMillis(retries))
                } else {
                    throw e
                }
            }
        }
    }

    private fun isIdempotent(method: String): Boolean =
        method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)

    private fun shouldRetry(response: Response): Boolean {
        if (response.code !in RETRYABLE_CODES) return false
        // ServerInterceptor 合成的"尚未配置服务器地址"同为 503，不可重试。
        if (response.code == 503 && response.message.contains("尚未配置")) return false
        return true
    }

    private fun backoffMillis(retries: Int): Long = 1000L * (1 shl (retries - 1))
}
