package com.lanraragi.reader.data

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.api.LanraragiApi
import com.lanraragi.reader.data.api.MinionJobDetail
import com.lanraragi.reader.data.api.toUserMessage
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.data.model.MinionJob
import com.lanraragi.reader.data.model.PluginInfo
import com.lanraragi.reader.data.model.ServerInfo
import com.lanraragi.reader.data.model.ServerStats
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.model.Tankoubon
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class ApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

data class PageResult(
    val items: List<Archive>,
    val total: Int?,
)

/**
 * 页缩略图入队结果（POST /api/archives/{id}/files/thumbnails）。
 * 服务器契约：200 = 所有页缩略图已存在（force=0 时）；202 = 已入队（新任务，
 * 或复用同档案进行中的任务并返回该任务 ID）；其余 = 失败。
 */
sealed class PageThumbQueue {
    /** 202：任务已入队或正在进行，用 [jobId] 轮询 [LanraragiRepository.minionPageThumbProgress]。 */
    data class Queued(val jobId: String) : PageThumbQueue()

    /** 200：所有页缩略图已存在，可直接用 [LanraragiRepository.pageThumbnailUrl] 加载。 */
    object AlreadyAvailable : PageThumbQueue()

    /** 非 2xx 或网络异常；[message] 为用户可读错误（可能为 null）。 */
    data class Failed(val message: String?) : PageThumbQueue()
}

/**
 * A7 重复检测入队结果（POST /api/minion/find_duplicates/queue）。
 * 服务器契约：200 = 已入队并返回任务 ID（`{operation, success, job}`）；
 * 其余 = 失败（服务器不支持该任务类型 / 鉴权失败 / Minion 后端不可用等）。
 */
sealed class DuplicateDetectionQueue {
    /** 任务已入队，[jobId] 可配合 [LanraragiRepository.getMinionJob] / minionJobDetail 轮询进度。 */
    data class Queued(val jobId: String) : DuplicateDetectionQueue()

    /** 非 2xx 或网络异常；[message] 为用户可读错误（可能为 null）。 */
    data class Failed(val message: String?) : DuplicateDetectionQueue()
}

class LanraragiRepository(
    private val api: LanraragiApi,
) {

    private suspend fun <T> network(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        throw ApiException(toUserMessage(e))
    }

    suspend fun getArchives(
        page: Int,
        filter: String? = null,
        sortby: String? = null,
        order: String? = null,
        categoryId: String? = null,
        newonly: Boolean? = null,
        untaggedonly: Boolean? = null,
        hideCompleted: Boolean = false,
    ): PageResult = network {
        val resp = api.getArchives(
            start = page,
            filter = filter?.takeIf { it.isNotBlank() },
            sortby = sortby,
            order = order,
            category = categoryId?.takeIf { it.isNotBlank() },
            newonly = if (newonly == true) "true" else null,
            untaggedonly = if (untaggedonly == true) "true" else null,
            hidecompleted = if (hideCompleted) "true" else null,
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
                ApiClient.downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) {
                        if (resp.code == 416) { // Range 不满足，可能已下载完
                            return@network dest
                        }
                        throw ApiException(toUserMessage(resp.code))
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
        throw ApiException("下载失败（尝试 $maxRetries 次）：${lastException?.let { toUserMessage(it) } ?: "未知错误"}")
    }

    private fun ensureSuccess(resp: Response<ResponseBody>, body: String?) {
        if (resp.isSuccessful) return
        // ServerInterceptor 合成的配置错误（尚未配置 / 地址格式无效）直透原文，避免误报"服务器错误"。
        val msg = resp.message()
        if (msg.contains("尚未配置") || msg.contains("格式无效")) {
            throw ApiException(msg)
        }
        throw ApiException(toUserMessage(resp.code()), statusCode = resp.code())
    }

    // ============ A 系列 ============

    /** A1 更新档案元数据(title/tags 以逗号分隔)。 */
    suspend fun updateArchiveMetadata(arcid: String, title: String, tags: String, summary: String? = null) {
        network {
            val resp = api.updateMetadata(arcid, title, tags, summary)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /** A2 档案所属分类(可能只有 name 无 id,按 name 匹配 UI 层)。 */
    suspend fun getArchiveCategories(arcid: String): List<Category> = network {
        val resp = api.getArchiveCategories(arcid)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseCategoryArray(body ?: "[]")
    }

    suspend fun createCategory(name: String) {
        network {
            val resp = api.createCategory(name)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun getBookmarkCategoryId(): String = network {
        val resp = api.getBookmarkCategoryLink()
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        val root = body?.let(ApiClient.json::parseToJsonElement) as? JsonObject
        (root?.get("category_id") as? JsonPrimitive)?.content.orEmpty()
    }

    suspend fun renameCategory(id: String, name: String, pinned: Boolean) {
        network {
            val resp = api.renameCategory(id, name, pinned)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun deleteCategory(id: String) {
        network {
            val resp = api.deleteCategory(id)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun addArchiveToCategory(categoryId: String, arcid: String) {
        network {
            val resp = api.addArchiveToCategory(categoryId, arcid)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun removeArchiveFromCategory(categoryId: String, arcid: String) {
        network {
            val resp = api.removeArchiveFromCategory(categoryId, arcid)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /** A4 后台任务状态。 */
    suspend fun getMinionJob(jobid: String): MinionJob = network {
        val resp = api.getMinionJob(jobid)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseMinionJob(body ?: "{}")
    }

    /** Full Minion payload, including plugin result.data, for metadata previews. */
    suspend fun getMinionJobDetail(jobid: String): String = network {
        val resp = api.getMinionJobDetail(jobid)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        body ?: "{}"
    }

    /**
     * GET /api/minion/{jobid}/detail（🔑）：完整任务信息，含 notes（页缩略图任务在此带进度）。
     * 请求失败抛 [ApiException]；响应体解析失败时返回空默认对象（照 parseMinionJob 风格）。
     */
    suspend fun minionJobDetail(jobId: String): MinionJobDetail = network {
        val resp = api.getMinionJobDetail(jobId)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        val el = runCatching { ApiClient.json.parseToJsonElement(body ?: "{}") }.getOrNull()
        if (el == null) {
            MinionJobDetail()
        } else {
            runCatching { ApiClient.json.decodeFromJsonElement<MinionJobDetail>(el) }
                .getOrDefault(MinionJobDetail())
        }
    }

    /**
     * 轮询便捷方法：从任务 notes 提取 (progress, pages)，无则返回 null。
     * 失败（网络/任务不存在/notes 无进度数据）返回 null；协程取消时向上抛出。
     * 0.9.81 page_thumbnails 的 notes 实际结构为
     * `{ "<页号1起>": "processed", ..., "total_pages": N, "id": "<arcid>" }`，
     * progress = 已标记 "processed" 的页数；同时兼容 openapi 文本所述的
     * notes.progress / notes.pages 结构。
     */
    suspend fun minionPageThumbProgress(jobId: String): Pair<Int, Int>? = try {
        pageThumbProgressFromNotes(minionJobDetail(jobId).notes)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun pageThumbProgressFromNotes(notes: JsonObject?): Pair<Int, Int>? {
        if (notes == null) return null
        val totalPages = (notes["total_pages"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (totalPages != null && totalPages > 0) {
            val processed = notes.count { (key, value) ->
                key.toIntOrNull() != null && value is JsonPrimitive && value.content == "processed"
            }
            return processed to totalPages
        }
        val progress = (notes["progress"] as? JsonPrimitive)?.content?.toIntOrNull()
        val pages = (notes["pages"] as? JsonPrimitive)?.content?.toIntOrNull()
        return if (progress != null && pages != null) progress to pages else null
    }

    /**
     * A7 重复检测入队（POST /api/minion/find_duplicates/queue，🔑）。
     * 契约要求 args 为必填 query 参数（JSON 数组字符串）；服务器 find_duplicates 任务
     * 取 args[0] 为封面哈希汉明距离阈值，官方前端默认 "[5]"，此处 [threshold] 默认一致。
     * 不抛业务异常：失败返回 [DuplicateDetectionQueue.Failed]；协程取消时向上抛出。
     */
    suspend fun queueDuplicateDetection(threshold: Int = 5): DuplicateDetectionQueue = try {
        network {
            val resp = api.queueDuplicateDetection(args = "[${threshold.coerceAtLeast(0)}]")
            val body = resp.body()?.string()
            if (!resp.isSuccessful) {
                val msg = resp.message()
                val failMessage =
                    if (msg.contains("尚未配置") || msg.contains("格式无效")) msg else toUserMessage(resp.code())
                DuplicateDetectionQueue.Failed(failMessage)
            } else {
                val root = runCatching {
                    body?.let(ApiClient.json::parseToJsonElement) as? JsonObject
                }.getOrNull()
                val jobId = (root?.get("job") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                if (jobId != null) {
                    notifyJobQueued(jobId, DUPLICATE_JOB_LABEL)
                    DuplicateDetectionQueue.Queued(jobId)
                } else {
                    DuplicateDetectionQueue.Failed(null)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DuplicateDetectionQueue.Failed(e.message)
    }

    /**
     * 服务器 Minion 任务入队回调：任何成功入队的异步任务都会在这里上报一次，
     * 由 [com.lanraragi.reader.data.JobTracker] 统一登记并在下载页「服务器任务」区展示进度。
     * A4/A7/A8/A10 四项异步能力的进度统一出口——此前没有任何地方调用 JobTracker.track，
     * 导致该区域永远为空。
     */
    var onJobQueued: ((jobid: String, label: String) -> Unit)? = null

    private fun notifyJobQueued(jobid: String, label: String) {
        try {
            onJobQueued?.invoke(jobid, label)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 进度展示属尽力而为，绝不能影响入队本身。
        }
    }

    /** A4 轮询任务直至完成/失败(默认最长 120s)。终态 state: finished|failed|inactive。 */
    suspend fun pollJobUntilDone(
        jobid: String,
        maxMillis: Long = 120_000L,
        pollMillis: Long = 2_000L,
    ): MinionJob? {
        val deadline = System.currentTimeMillis() + maxMillis
        while (System.currentTimeMillis() < deadline) {
            val job = runCatching { getMinionJob(jobid) }.getOrNull()
            if (job == null) return null
            val s = job.state.lowercase()
            if (s.contains("finish") || s.contains("done") || s.contains("fail") || s.contains("error") ||
                s.contains("inactive") || s == "dead" || s.isBlank()
            ) {
                return job
            }
            delay(pollMillis)
        }
        return null
    }

    /**
     * A5 整本页缩略图入队（POST /api/archives/{id}/files/thumbnails，参数 force 为 query）。
     * 服务器读取 `param('force')` 并与字符串 "true" 精确比较，故此处发送 "true"/不发送。
     * 不抛业务异常：失败返回 [PageThumbQueue.Failed]；协程取消时向上抛出。
     */
    suspend fun queuePageThumbnails(arcid: String, force: Boolean = false): PageThumbQueue = try {
        network {
            val resp = api.queuePageThumbnails(arcid, if (force) "true" else null)
            val body = resp.body()?.string()
            if (!resp.isSuccessful) {
                val msg = resp.message()
                val failMessage =
                    if (msg.contains("尚未配置") || msg.contains("格式无效")) msg else toUserMessage(resp.code())
                PageThumbQueue.Failed(failMessage)
            } else {
                val root = runCatching {
                    body?.let(ApiClient.json::parseToJsonElement) as? JsonObject
                }.getOrNull()
                val jobId = (root?.get("job") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                if (jobId != null) {
                    notifyJobQueued(jobId, PAGE_THUMB_JOB_LABEL)
                    PageThumbQueue.Queued(jobId)
                } else {
                    PageThumbQueue.AlreadyAvailable
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PageThumbQueue.Failed(e.message)
    }

    /**
     * 每页缩略图 URL（GET api/archives/{id}/thumbnail?page=N）。
     * [page] 为 0 起页号；服务器端 page 参数为 1 起（0 = 封面），这里与
     * [ApiClient.pageThumbnailUrl] 一致地 +1 转换（0.9.81 extract_thumbnail：
     * `$filelist[page > 0 ? page - 1 : 0]`，官方阅读器亦按 0 基 +1 取用）。
     * 返回占位 host URL，经 ServerInterceptor 改写为真实服务器并附加鉴权头。
     */
    fun pageThumbnailUrl(arcid: String, page: Int): String = ApiClient.pageThumbnailUrl(arcid, page)

    suspend fun regenAllThumbnails() {
        network {
            val resp = api.regenThumbnails(force = null)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun setThumbnailFromPage(arcid: String, page: Int) {
        network {
            val resp = api.setThumbnailFromPage(arcid, page.coerceAtLeast(1))
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /**
     * A6 随机搜索(`/api/search/random`)：在给定筛选/分类范围内随机取 count 本。
     * 响应结构与 `/api/search` 相同（`{ data: [...], recordsTotal }`），返回解析后的档案列表。
     */
    suspend fun searchRandom(
        count: Int,
        category: String? = null,
        filter: String? = null,
        newOnly: Boolean = false,
        untaggedOnly: Boolean = false,
        hideCompleted: Boolean = false,
    ): List<Archive> = network {
        val resp = api.getRandomArchives(
            category = category?.takeIf { it.isNotBlank() },
            filter = filter?.takeIf { it.isNotBlank() },
            count = count.coerceAtLeast(1),
            newonly = if (newOnly) "true" else null,
            untaggedonly = if (untaggedOnly) "true" else null,
            hidecompleted = if (hideCompleted) "true" else null,
        )
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseRandomArchives(body ?: "[]")
    }

    /** A7 新标记管理。 */
    suspend fun clearArchiveNew(arcid: String) {
        network {
            val resp = api.clearArchiveNew(arcid)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /**
     * A11 全库清除「New」标记：单次服务器调用（DELETE /api/database/isnew），
     * 不受客户端当前已加载页数限制。
     */
    suspend fun clearAllNew() {
        network {
            val resp = api.clearAllNew()
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun setArchiveNew(arcid: String) {
        network {
            val resp = api.setArchiveNew(arcid)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /** A9 插件列表(type: metadata / download)。 */
    suspend fun listPlugins(type: String = "metadata"): List<PluginInfo> = network {
        val resp = api.getPlugins(type)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parsePluginList(body ?: "[]")
    }

    /** A9 对档案执行插件(同步)。 */
    suspend fun usePlugin(arcid: String, pluginNamespace: String, arg: String? = null): String = network {
        val resp = api.usePlugin(arcid, pluginNamespace, arg)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        body ?: ""
    }

    /** A9 异步执行插件(排队 Minion 任务,返回 jobid)。 */
    suspend fun usePluginAsync(arcid: String, pluginNamespace: String, arg: String? = null): String = network {
        val resp = api.usePluginAsync(arcid, pluginNamespace, arg, null)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        val jobid = parseJobId(body) ?: throw ApiException("未获得插件任务 ID")
        notifyJobQueued(jobid, PLUGIN_JOB_LABEL)
        jobid
    }

    /** A10 Shinobu 状态(原始文本,通常为 JSON)。 */
    suspend fun getShinobuStatus(): String = network {
        val resp = api.getShinobuStatus()
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        body ?: ""
    }

    suspend fun shinobuRescan() {
        network {
            val resp = api.shinobuRescan()
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /** A12 服务器信息。 */
    suspend fun getServerInfo(): ServerInfo = network {
        val resp = api.getServerInfo()
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseServerInfo(body ?: "{}")
    }

    /** A13 目录(长本分章)。 */
    suspend fun addTocEntry(arcid: String, page: Int, title: String) {
        network {
            val resp = api.addTocEntry(arcid, page.coerceAtLeast(1), title)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun deleteTocEntry(arcid: String, page: Int) {
        network {
            val resp = api.deleteTocEntry(arcid, page.coerceAtLeast(1))
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    // ============ A11 单行本 ============

    /** A11 单行本列表（page 透传给服务端分页）。 */
    suspend fun getTankoubons(page: Int? = null): List<Tankoubon> = network {
        val resp = api.getTankoubons(page = page)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        JsonHelpers.parseTankoubons(body ?: "[]")
    }

    /** A11 档案所属单行本 ID 列表。 */
    suspend fun getArchiveTankoubons(arcid: String): List<String> = network {
        val resp = api.getArchiveTankoubons(arcid)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        val el = ApiClient.json.parseToJsonElement(body ?: "{}") as? kotlinx.serialization.json.JsonObject
        val arr = el?.get("tankoubons") as? kotlinx.serialization.json.JsonArray
        arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
    }

    /** A11 单行本详情（含成员档案元数据）。`/full` 返回 `{ result: {...}, total, filtered }`。 */
    suspend fun getTankoubonFull(id: String): Tankoubon = network {
        val resp = api.getTankoubonFull(id)
        val body = resp.body()?.string()
        ensureSuccess(resp, body)
        val el = ApiClient.json.parseToJsonElement(body ?: "{}")
        val result = (el as? kotlinx.serialization.json.JsonObject)?.get("result") ?: el
        runCatching { ApiClient.json.decodeFromJsonElement<Tankoubon>(result) }
            .getOrDefault(Tankoubon(id = id))
    }

    /** A11 更新单行本全局阅读进度（page 为跨档案全局 1 起页号）。 */
    suspend fun updateTankoubonProgress(id: String, page: Int) {
        network {
            val resp = api.updateTankoubonProgress(id, page.coerceAtLeast(1))
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun createTankoubon(name: String) {
        network {
            val resp = api.createTankoubon(name)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    /** A11 重命名单行本（PUT /tankoubons/{id}，仅传 name）。 */
    suspend fun renameTankoubon(id: String, name: String) {
        network {
            val resp = api.updateTankoubon(id, name, null)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun deleteTankoubon(id: String) {
        network {
            val resp = api.deleteTankoubon(id)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun addArchiveToTankoubon(id: String, arcid: String) {
        network {
            val resp = api.addArchiveToTankoubon(id, arcid)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    suspend fun removeArchiveFromTankoubon(id: String, arcid: String) {
        network {
            val resp = api.removeArchiveFromTankoubon(id, arcid)
            ensureSuccess(resp, resp.body()?.string())
        }
    }

    // ============ A3/A8 需要流式/多部分的操作(手写 okhttp,复用拦截器鉴权) ============

    /**
     * A3 上传本地档案(zip/cbz…)。input 由调用方从 SAF/文件打开。
     */
    suspend fun uploadArchive(
        fileName: String,
        input: InputStream,
        title: String? = null,
        tags: String? = null,
    ): String = network {
        val bodyStream = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: okio.BufferedSink) {
                input.use { it.copyTo(sink.outputStream()) }
            }
        }
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bodyStream)
            .apply {
                title?.let { addFormDataPart("title", it) }
                tags?.let { addFormDataPart("tags", it) }
            }
            .build()
        val url = ApiClient.toAbsoluteUrl("/api/archives/upload")
        ApiClient.okHttpClient.newCall(
            Request.Builder().url(url).put(mb).build()
        ).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(body?.trim()?.takeIf { it.isNotBlank() }
                ?: toUserMessage(resp.code))
            body ?: ""
        }
    }

    /**
     * A3 让服务器从 URL 下载档案入库(catid 可选,加入指定分类)。
     */
    suspend fun downloadFromUrl(url: String, catid: String? = null) {
        network {
            var abs = ApiClient.toAbsoluteUrl("/api/download_url")
            val encoded = URLEncoder.encode(url, "UTF-8")
            abs = if (catid.isNullOrBlank()) "$abs?url=$encoded" else "$abs?url=$encoded&catid=${URLEncoder.encode(catid, "UTF-8")}"
            ApiClient.okHttpClient.newCall(
                Request.Builder().url(abs).post("".toRequestBody(null)).build()
            ).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful) throw ApiException(body?.trim()?.takeIf { it.isNotBlank() }
                    ?: toUserMessage(resp.code))
            }
        }
    }

    /**
     * A8 排队生成服务器备份(JSON),返回 jobid;用 [getMinionJob] 轮询直至完成,
     * 再调用 [downloadBackup] 取回 JSON。
     */
    suspend fun queueBackup(): String = network {
        ApiClient.okHttpClient.newCall(
            Request.Builder().url(ApiClient.toAbsoluteUrl("/api/database/backup")).post("".toRequestBody(null)).build()
        ).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(body?.trim()?.takeIf { it.isNotBlank() }
                ?: toUserMessage(resp.code))
            val jobid = parseJobId(body) ?: throw ApiException("未获得备份任务 ID")
            notifyJobQueued(jobid, BACKUP_JOB_LABEL)
            jobid
        }
    }

    /** A8 下载已完成备份的 JSON 内容。 */
    suspend fun downloadBackup(jobid: String): String = network {
        ApiClient.okHttpClient.newCall(
            Request.Builder().url(ApiClient.toAbsoluteUrl("/api/database/backup/$jobid")).get().build()
        ).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(toUserMessage(resp.code))
            body ?: ""
        }
    }

    /**
     * A8 上传备份 JSON 触发恢复(重操作,UI 需强确认)。
     *
     * 服务器把恢复排进 Minion 队列后立刻返回，真正的整库覆盖发生在后台；
     * 因此这里返回 jobid，由调用方用 [pollJobUntilDone] 等到终态再报成功，
     * 否则「恢复成功」会先于实际执行（甚至在实际失败时）弹出。
     */
    suspend fun restoreBackup(jsonText: String): String {
        return network {
            val bodyBytes = jsonText.toByteArray()
            val bodyStream = bodyBytes.toRequestBody("application/json".toMediaType())
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "backup.json", bodyStream)
                .build()
            ApiClient.okHttpClient.newCall(
                Request.Builder().url(ApiClient.toAbsoluteUrl("/api/database/restore")).post(mb).build()
            ).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful) throw ApiException(body?.trim()?.takeIf { it.isNotBlank() }
                    ?: toUserMessage(resp.code))
                val jobid = parseJobId(body) ?: throw ApiException("未获得恢复任务 ID")
                notifyJobQueued(jobid, RESTORE_JOB_LABEL)
                jobid
            }
        }
    }

    /** 从 `{operation, success, job}` 形状的应答里取 minion 任务 ID。 */
    private fun parseJobId(body: String?): String? = runCatching {
        val el = ApiClient.json.parseToJsonElement(body ?: "") as? JsonObject
        val p = (el?.get("job") ?: el?.get("id") ?: el?.get("jobid")) as? JsonPrimitive
        p?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

// 下载页「服务器任务」区的任务名（JobTracker 展示用）。
private const val PAGE_THUMB_JOB_LABEL = "整本页缩略图生成"
private const val DUPLICATE_JOB_LABEL = "重复检测"
private const val PLUGIN_JOB_LABEL = "插件批量执行"
private const val BACKUP_JOB_LABEL = "数据库备份"
private const val RESTORE_JOB_LABEL = "数据库恢复"
