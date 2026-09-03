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
}
