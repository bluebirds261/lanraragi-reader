package com.lanraragi.reader.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * GET /api/minion/{jobid}/detail（🔑）的完整任务信息。
 *
 * 字段对齐 openapi `minionJobDetail` 响应示例与 Minion `$job->info`：
 * args/attempts/children/created/delayed/expires/finished/id/lax/notes/parents/
 * priority/queue/result/retried/retries/started/state/task/time/worker。
 * 全部字段带默认值 + ignoreUnknownKeys，容忍不同 Minion 版本的键差异。
 *
 * `notes` 为任务自定义对象（可为 null）。0.9.81 的页缩略图任务（page_thumbnails，
 * 见 lib/LANraragi/Utils/Minion.pm）实际写入的结构为：
 * `{ "<页号>(1 起)": "processed", ..., "total_pages": 总页数, "id": 档案ID }`
 * —— openapi 文本所述的 notes.progress / notes.pages 与实现不符，以实现为准；
 * 进度提取见 [com.lanraragi.reader.data.LanraragiRepository.minionPageThumbProgress]，
 * 该方法对两种结构均兼容。
 */
@Serializable
data class MinionJobDetail(
    val id: Long = 0,
    val task: String = "",
    /** inactive / active / finished / failed。 */
    val state: String = "",
    val priority: Int = 0,
    val queue: String = "default",
    val attempts: Int = 1,
    val retries: Int = 0,
    val lax: Int = 0,
    val created: Long? = null,
    val delayed: Long? = null,
    val started: Long? = null,
    val finished: Long? = null,
    val expires: Long? = null,
    val retried: Long? = null,
    val worker: Long? = null,
    val time: Long? = null,
    /** 任务自定义进度数据；页缩略图任务含 total_pages 与逐页 "processed" 键。 */
    val notes: JsonObject? = null,
    /** 任务结果（任意 JSON：字符串或对象，如 handle_upload 的 {id, message, success}）。 */
    val result: JsonElement? = null,
    /** 失败信息（部分 Minion 版本写入 info；可为字符串或对象）。 */
    val error: JsonElement? = null,
    val args: JsonArray = JsonArray(emptyList()),
    val children: JsonArray = JsonArray(emptyList()),
    val parents: JsonArray = JsonArray(emptyList()),
)
