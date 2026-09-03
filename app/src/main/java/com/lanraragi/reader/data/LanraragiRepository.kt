package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.api.LanraragiApi
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.ServerStats
import com.lanraragi.reader.data.model.TagStat
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.delay

class ApiException(message: String) : Exception(message)

data class PageResult(
    val items: List<Archive>,
    val total: Int?,
)

class LanraragiRepository(
    private val api: LanraragiApi,
) {

    private suspend fun <T> network(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        throw ApiException("无法连接到服务器，请检查地址与网络：${e.message}")
    }

    suspend fun getArchives(
        page: Int,
        filter: String? = null,
        sortby: String? = null,
        order: String? = null,
        categoryId: String? = null,
        newonly: Boolean? = null,
        untaggedonly: Boolean? = null,
    ): PageResult = network {
        val resp = api.getArchives(
            start = page,
            filter = filter?.takeIf { it.isNotBlank() },
            sortby = sortby,
            order = order,
            category = categoryId?.takeIf { it.isNotBlank() },
            newonly = if (newonly == true) "true" else null,
            untaggedonly = if (untaggedonly == true) "true" else null,
        )
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        val (items, total) = JsonHelpers.parseArchiveList(body ?: "[]")
        PageResult(items, total)
    }

    suspend fun getMetadata(arcid: String): Archive = network {
        val resp = api.getMetadata(arcid)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseArchive(body ?: "{}")
    }

    /** 获取档案的分页图片绝对 URL 列表（0.9.x 的 metadata 已不含 pages，需走 /files）。 */
    suspend fun getPageUrls(arcid: String): List<String> = network {
        val resp = api.getFiles(arcid)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parsePageUrls(body ?: "{}").map { ApiClient.toAbsoluteUrl(it) }
    }

    suspend fun getStats(): ServerStats = network {
        var archives = 0L
        var tags = 0L
        try {
            val resp = api.getArchives(start = 0)
            val body = resp.body()?.string()
            if (resp.isSuccessful) {
                val (items, total) = JsonHelpers.parseArchiveList(body ?: "[]")
                archives = (total ?: items.size).toLong()
            }
        } catch (_: Exception) {
        }
        try {
            val resp = api.getTagStats()
            val body = resp.body()?.string()
            if (resp.isSuccessful) {
                tags = JsonHelpers.parseTagStats(body ?: "[]").size.toLong()
            }
        } catch (_: Exception) {
        }
        ServerStats(total_archives = archives, tags_count = tags)
    }

    suspend fun getCategories(): List<Category> = network {
        val resp = api.getCategories()
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseCategories(body ?: "[]")
    }

    suspend fun getTags(): List<TagStat> = network {
        val resp = api.getTagStats()
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseTagStats(body ?: "[]")
    }

    suspend fun setProgress(arcid: String, page: Int) {
        network {
            val resp = api.setProgress(arcid, page.coerceAtLeast(1))
            val body = resp.body()?.string()
            ensureSuccess(resp, body)
        }
    }

    suspend fun deleteArchive(arcid: String) {
        network {
            val resp = api.deleteArchive(arcid)
            val body = resp.body()?.string()
            ensureSuccess(resp, body)
        }
    }

    /** 测试连接，用图库同款接口（/api/archives）验证连通性与鉴权；失败时抛出具体原因。 */
    suspend fun testConnection(): ServerStats = network {
        val resp = api.getArchives(start = 0)
        val body = resp.body()?.string()
        if (resp.code() == 401 || resp.code() == 403) {
            throw ApiException("API Key 错误或未授权（HTTP ${resp.code()}）")
        }
        ensureSuccess(resp, body)
        val (items, total) = JsonHelpers.parseArchiveList(body ?: "[]")
        ServerStats(total_archives = (total ?: items.size).toLong())
    }

    /** 下载档案原档到指定文件，支持断点续传（如果服务器支持）并增加重试逻辑。 */
    suspend fun downloadArchive(
        arcid: String,
        dest: File,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): File = network {
        var retryCount = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                val startByte = if (dest.exists()) dest.length() else 0L
                val reqBuilder = Request.Builder()
                    .url(ApiClient.toAbsoluteUrl("/api/archives/$arcid/download"))
                
                // 如果文件已存在，尝试断点续传（LANraragi 的 Mojolicious 端点通常支持 Range）
                if (startByte > 0) {
                    reqBuilder.header("Range", "bytes=$startByte-")
                }

                val req = reqBuilder.build()
                ApiClient.okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) {
                        if (resp.code == 416) { // Range 不满足，可能已下载完
                            return@network dest
                        }
                        throw ApiException("下载失败：HTTP ${resp.code}")
                    }

                    val body = resp.body ?: throw ApiException("下载失败：空响应")
                    val contentLength = body.contentLength()
                    val total = if (resp.code == 206) contentLength + startByte else contentLength
                    
                    dest.parentFile?.mkdirs()
                    val append = resp.code == 206
                    
                    var written = startByte
                    FileOutputStream(dest, append).use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(128 * 1024) // 128KB buffer
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                written += n
                                onProgress(written, if (total > 0) total else null)
                            }
                        }
                    }
                    return@network dest
                }
            } catch (e: Exception) {
                lastException = e
                retryCount++
                if (retryCount < maxRetries) {
                    delay(1000L * retryCount) // 退避重试
                }
            }
        }
        throw ApiException("下载失败（尝试 $maxRetries 次）：${lastException?.message}")
    }

    private fun ensureSuccess(resp: Response<ResponseBody>, body: String?) {
        if (resp.isSuccessful) return
        if (resp.code() == 401 || resp.code() == 403) {
            throw ApiException("API Key 错误或未授权（HTTP ${resp.code()}）")
        }
        throw ApiException("服务器返回错误 HTTP ${resp.code()} ${resp.message()}")
    }
}
