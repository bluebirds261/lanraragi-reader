package com.lanraragi.reader.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * LANraragi REST API。
 *
 * 说明：这里统一返回 [ResponseBody] 并在仓库层用 kotlinx.serialization 手动解析，
 * 以避免对 Retrofit JSON converter 的依赖，同时兼容不同版本的响应结构。
 * 所有相对路径会经 [ServerInterceptor] 把占位 host 替换为真实服务器地址并附加鉴权头。
 */
interface LanraragiApi {

    /** 搜索/筛选档案（分页）。start 为起始位置；filter 支持命名空间与通配符；sortby 可为 title/lastread/命名空间。 */
    @GET("api/search")
    suspend fun getArchives(
        @Query("start") start: Int? = null,
        @Query("filter") filter: String? = null,
        @Query("sortby") sortby: String? = null,
        @Query("order") order: String? = null,
        @Query("category") category: String? = null,
        @Query("newonly") newonly: String? = null,
        @Query("untaggedonly") untaggedonly: String? = null,
        @Query("hidecompleted") hidecompleted: String? = null,
    ): Response<ResponseBody>

    /** 获取档案完整元数据（含 pages 列表、tags、progress 等）。 */
    @GET("api/archives/{id}/metadata")
    suspend fun getMetadata(@Path("id") id: String): Response<ResponseBody>

    /** 获取档案的分页图片 URL 列表（阅读器用，返回 `{pages: [...urls]}`）。 */
    @GET("api/archives/{id}/files")
    suspend fun getFiles(@Path("id") id: String): Response<ResponseBody>

    /** 下载档案原档（zip/cbz/rar…）。 */
    @Streaming
    @GET("api/archives/{id}/download")
    suspend fun downloadArchive(@Path("id") id: String): Response<ResponseBody>

    /** 设置阅读进度（1 起）。 */
    @PUT("api/archives/{id}/progress/{page}")
    suspend fun setProgress(@Path("id") id: String, @Path("page") page: Int): Response<ResponseBody>

    /** 删除档案。 */
    @DELETE("api/archives/{id}")
    suspend fun deleteArchive(@Path("id") id: String): Response<ResponseBody>

    /** 全部标签（含命名空间与权重）。 */
    @GET("api/database/stats")
    suspend fun getTagStats(): Response<ResponseBody>

    /** 全部分类（Category 插件配置）。 */
    @GET("api/categories")
    suspend fun getCategories(): Response<ResponseBody>

    /** 当前与服务器收藏功能绑定的分类 ID。 */
    @GET("api/categories/bookmark_link")
    suspend fun getBookmarkCategoryLink(): Response<ResponseBody>

    // ============ A 系列:服务器能力 ============

    /** A1 更新档案标题/标签/简介(PUT 表单参数,Mojolicious param 兼容)。 */
    @PUT("api/archives/{id}/metadata")
    suspend fun updateMetadata(
        @Path("id") id: String,
        @Query("title") title: String? = null,
        @Query("tags") tags: String? = null,
        @Query("summary") summary: String? = null,
    ): Response<ResponseBody>

    /** A2 档案所属分类。 */
    @GET("api/archives/{id}/categories")
    suspend fun getArchiveCategories(@Path("id") id: String): Response<ResponseBody>

    /** A2 新建分类。 */
    @PUT("api/categories")
    suspend fun createCategory(@Query("name") name: String): Response<ResponseBody>

    /** A2 重命名分类。 */
    @PUT("api/categories/{id}")
    suspend fun renameCategory(
        @Path("id") id: String,
        @Query("name") name: String,
        @Query("pinned") pinned: Boolean,
    ): Response<ResponseBody>

    /** A2 删除分类。 */
    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String): Response<ResponseBody>

    /** A2 把档案加入分类。 */
    @PUT("api/categories/{id}/{archive}")
    suspend fun addArchiveToCategory(@Path("id") id: String, @Path("archive") archive: String): Response<ResponseBody>

    /** A2 把档案移出分类。 */
    @DELETE("api/categories/{id}/{archive}")
    suspend fun removeArchiveFromCategory(@Path("id") id: String, @Path("archive") archive: String): Response<ResponseBody>

    /** A4 查询后台任务状态。 */
    @GET("api/minion/{jobid}")
    suspend fun getMinionJob(@Path("jobid") jobid: String): Response<ResponseBody>

    /** A4 后台任务详情。 */
    @GET("api/minion/{jobid}/detail")
    suspend fun getMinionJobDetail(@Path("jobid") jobid: String): Response<ResponseBody>

    /**
     * A7 重复检测任务入队（POST api/minion/find_duplicates/queue，🔑）。
     * 契约 `POST /minion/{jobname}/queue`：args 为必填 query 参数（JSON 数组字符串，
     * 服务器 decode_json 后展开为任务参数），priority 可选；find_duplicates 任务取
     * args[0] 为封面哈希汉明距离阈值。成功返回 `{operation, success, job}`。
     */
    @POST("api/minion/find_duplicates/queue")
    suspend fun queueDuplicateDetection(
        @Query("args") args: String,
        @Query("priority") priority: Int? = null,
    ): Response<ResponseBody>

    /** A5 某档案页生成页码缩略图(入队任务)。 */
    @POST("api/archives/{id}/files/thumbnails")
    suspend fun queuePageThumbnails(
        @Path("id") id: String,
        @Query("force") force: String? = null,
    ): Response<ResponseBody>

    /** A5 全库重建缩略图。 */
    @POST("api/regen_thumbs")
    suspend fun regenThumbnails(@Query("force") force: String? = null): Response<ResponseBody>

    /** A5 以指定页作为档案封面。 */
    @PUT("api/archives/{id}/thumbnail")
    suspend fun setThumbnailFromPage(@Path("id") id: String, @Query("page") page: Int): Response<ResponseBody>

    /** A6 随机档案。 */
    @GET("api/search/random")
    suspend fun getRandomArchives(
        @Query("category") category: String? = null,
        @Query("filter") filter: String? = null,
        @Query("count") count: Int? = 1,
        @Query("newonly") newonly: String? = null,
        @Query("untaggedonly") untaggedonly: String? = null,
        @Query("hidecompleted") hidecompleted: String? = null,
    ): Response<ResponseBody>

    /** A7 设置“新”标记。 */
    @PUT("api/archives/{id}/isnew")
    suspend fun setArchiveNew(@Path("id") id: String): Response<ResponseBody>

    /** A7 清除“新”标记。 */
    @DELETE("api/archives/{id}/isnew")
    suspend fun clearArchiveNew(@Path("id") id: String): Response<ResponseBody>

    /**
     * A11 一次清除全库的「New」标记（契约 `clearNewAll`，DELETE /api/database/isnew）。
     * 逐本调用 [clearArchiveNew] 只能覆盖当前已加载/已选中的档案，全库清 New 必须走这个端点。
     */
    @DELETE("api/database/isnew")
    suspend fun clearAllNew(): Response<ResponseBody>

    /** A9 插件列表。 */
    @GET("api/plugins/{type}")
    suspend fun getPlugins(@Path("type") type: String): Response<ResponseBody>

    /** A9 执行插件(同步)。 */
    @POST("api/plugins/use")
    suspend fun usePlugin(
        @Query("id") id: String? = null,
        @Query("plugin") plugin: String? = null,
        @Query("arg") arg: String? = null,
    ): Response<ResponseBody>

    /** A9 异步执行插件(排队 Minion 任务,返回 jobid)。 */
    @POST("api/plugins/queue")
    suspend fun usePluginAsync(
        @Query("id") id: String? = null,
        @Query("plugin") plugin: String? = null,
        @Query("arg") arg: String? = null,
        @Query("priority") priority: Int? = null,
    ): Response<ResponseBody>

    /** A10 Shinobu 状态。 */
    @GET("api/shinobu")
    suspend fun getShinobuStatus(): Response<ResponseBody>

    /** A10 触发 Shinobu 重扫。 */
    @POST("api/shinobu/rescan")
    suspend fun shinobuRescan(): Response<ResponseBody>

    /** A12 服务器信息。 */
    @GET("api/info")
    suspend fun getServerInfo(): Response<ResponseBody>

    /** A13 目录:添加(按页)条目。 */
    @PUT("api/archives/{id}/toc")
    suspend fun addTocEntry(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("title") title: String,
    ): Response<ResponseBody>

    /** A13 目录:删除(按页)条目。 */
    @DELETE("api/archives/{id}/toc")
    suspend fun deleteTocEntry(@Path("id") id: String, @Query("page") page: Int): Response<ResponseBody>

    /** A11 档案所属单行本（返回 tankoubon ID 数组）。 */
    @GET("api/archives/{id}/tankoubons")
    suspend fun getArchiveTankoubons(@Path("id") id: String): Response<ResponseBody>

    /** A11 单行本列表。 */
    @GET("api/tankoubons")
    suspend fun getTankoubons(@Query("page") page: Int? = null): Response<ResponseBody>

    /** A11 整卷阅读数据。 */
    @GET("api/tankoubons/{id}/full")
    suspend fun getTankoubonFull(
        @Path("id") id: String,
        @Query("page") page: Int? = null,
    ): Response<ResponseBody>

    /** A11 新建单行本。 */
    @PUT("api/tankoubons")
    suspend fun createTankoubon(@Query("name") name: String): Response<ResponseBody>

    /** A11 更新单行本名称。 */
    @PUT("api/tankoubons/{id}")
    suspend fun updateTankoubon(
        @Path("id") id: String,
        @Query("name") name: String? = null,
        @Query("archives") archives: String? = null,
    ): Response<ResponseBody>

    /** A11 删除单行本。 */
    @DELETE("api/tankoubons/{id}")
    suspend fun deleteTankoubon(@Path("id") id: String): Response<ResponseBody>

    /** A11 把档案加入单行本。 */
    @PUT("api/tankoubons/{id}/{archive}")
    suspend fun addArchiveToTankoubon(@Path("id") id: String, @Path("archive") archive: String): Response<ResponseBody>

    /** A11 把档案移出单行本。 */
    @DELETE("api/tankoubons/{id}/{archive}")
    suspend fun removeArchiveFromTankoubon(@Path("id") id: String, @Path("archive") archive: String): Response<ResponseBody>

    /** A11 更新单行本全局阅读进度（page 为跨档案全局 1 起页号）。 */
    @PUT("api/tankoubons/{id}/progress/{page}")
    suspend fun updateTankoubonProgress(@Path("id") id: String, @Path("page") page: Int): Response<ResponseBody>
}
