package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.db.LocalArchiveEntity
import com.lanraragi.reader.data.db.LocalMetadataEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.flow.first

/** LANraragi adapter. It exhausts the server result set so cross-source paging remains stable. */
class LanraragiLibraryRemoteGateway(
    private val repository: LanraragiRepository,
    private val serverScope: () -> String? = { null },
    /**
     * 「仅本地书架」路径：向导第 4 步可跳过服务器配置，此时不该再打网络，
     * 否则图库会被「尚未配置服务器地址」的错误态占满，本地档案也不可见。
     * 已配置但连不通的服务器仍照常抛错，保留错误提示与重试。
     */
    private val serverConfigured: () -> Boolean = { true },
) : LibraryRemoteGateway {
    override suspend fun fetch(query: LibraryQuery): List<LibraryEntry> {
        if (!serverConfigured()) return emptyList()
        val request = query.remoteRequest()
        val rows = mutableListOf<LibraryEntry>()
        var offset = 0
        var expected: Int? = null
        while (true) {
            val result = repository.getArchives(
                page = offset,
                filter = request.filter,
                sortby = request.sort.wireValue,
                order = request.direction.name.lowercase(),
                categoryId = request.categoryId,
                newonly = request.newOnly,
                untaggedonly = request.untaggedOnly,
                hideCompleted = request.hideCompleted,
            )
            expected = result.total ?: expected
            val received = result.items
            rows += received.asSequence().filter { it.arcid.isNotBlank() }.map { it.toLibraryEntry(serverScope()) }
            offset += received.size
            if (received.isEmpty() || (expected != null && offset >= expected)) break
        }
        return rows.distinctBy(LibraryEntry::sourceKey)
    }
}

/** Room-only adapter. It intentionally has no dependency on a network repository. */
class RoomLibraryLocalGateway(
    private val database: ReaderDatabase,
) : LibraryLocalGateway {
    override suspend fun fetch(): List<LibraryEntry> {
        return database.localArchiveDao().observeAll().first().map { archive ->
            archive.toLibraryEntry(database.localMetadataDao().find(archive.sourceKey))
        }
    }
}

private fun Archive.toLibraryEntry(serverScopeValue: String?): LibraryEntry = LibraryEntry(
    identity = remoteLibraryIdentity(arcid, serverScopeValue),
    title = title,
    tags = tagList,
    summary = summary,
    categoryId = category.takeIf(String::isNotBlank),
    isNew = isNew,
    pageCount = pagecount,
    progress = progress,
    // LANraragi 的档案 JSON 里**没有** dateadded 字段（Utils/Database.pm build_json 只输出
    // arcid/title/tags/summary/isnew/progress/pagecount/lastreadtime/size/toc），
    // 添加日期是以 `date_added:<epoch 秒>` 标签的形式存放的（add_timestamp_tag），
    // 所以这里必须从标签解析；否则 dateAdded 恒为 0，按添加日期排序会退化成乱序。
    dateAdded = parseDateAddedMillis(tagList),
    // 单行本 members 数量（build_tank_json 的 archive_count）；普通档案 JSON 无此字段 → 0。
    volumeCount = archive_count,
)

/**
 * 远端行的身份判定：`TANK_xxxxxxxxxx` 是单行本（不是一个 40 位 arcid 的档案），
 * 否则仍是带服务器作用域的 [ArchiveIdentity.Remote]。
 *
 * `/api/search` 与 `/api/search/random` 默认 `groupby_tanks=true`，会把单行本以 TANK_ id
 * 混进档案结果里；这类条目不能按普通档案处理（`/api/archives/{id}/metadata` 等接口
 * 要求 40 位 arcid），因此必须在数据层就区分出来。
 * 前缀判定统一复用 [ArchiveIdentity.fromArchiveId]，不在这里另写一遍。
 */
internal fun remoteLibraryIdentity(arcid: String, serverScopeValue: String?): ArchiveIdentity =
    when (val identified = ArchiveIdentity.fromArchiveId(arcid)) {
        is ArchiveIdentity.Tankoubon -> identified
        else -> ArchiveIdentity.Remote(arcid, serverScopeValue)
    }

/**
 * 是否为单行本条目（arcid 形如 `TANK_xxxxxxxxxx`）。
 *
 * 判定同样复用 [ArchiveIdentity.fromArchiveId]：整个工程对「什么 id 是单行本」只此一处定义。
 * 调用方用于把单行本从一切「以 arcid 为参数的服务器接口」调用中剔除
 * （metadata / delete / isnew / 分类 / 下载 / 刮削 …），这些接口只接受 40 位 arcid。
 */
internal fun isTankArchiveId(arcid: String): Boolean =
    arcid.isNotBlank() && ArchiveIdentity.fromArchiveId(arcid) is ArchiveIdentity.Tankoubon

/** 多选批量操作的目标筛选结果：可交给服务器档案接口的 id + 被跳过的单行本数量。 */
internal data class ArchiveActionTargets(
    val ids: List<String>,
    /** 选中项里的单行本数量（这些条目不能进 arcid 档案接口，需在汇总文案里说明已跳过）。 */
    val skippedTankCount: Int,
)

/**
 * 多选选中项 → 可操作目标：剔除本地档案（`local_` 前缀）与单行本（`TANK_` 前缀）。
 *
 * 单行本被剔除的原因不是「不想做」，而是服务端这些接口硬要求 40 位 arcid：
 * `DELETE /api/archives/{id}`、`PUT|DELETE /api/archives/{id}/isnew`、
 * `/api/categories/{id}/{archive}`、`/api/archives/{id}/download`、`/metadata` 都会直接失败。
 */
internal fun archiveActionTargets(selected: Iterable<String>): ArchiveActionTargets {
    val all = selected.toList()
    return ArchiveActionTargets(
        ids = all.filter { !it.startsWith("local_") && !isTankArchiveId(it) },
        skippedTankCount = all.count(::isTankArchiveId),
    )
}

private fun LocalArchiveEntity.toLibraryEntry(metadata: LocalMetadataEntity?): LibraryEntry = LibraryEntry(
    identity = ArchiveIdentity.LocalSaf(uri),
    title = metadata?.title ?: title,
    tags = metadata?.tags.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty),
    summary = metadata?.summary.orEmpty(),
    pageCount = pageCount,
    isSaved = true,
    localUri = uri,
    // 本地档案没有 date_added 标签，用索引里的最后校验时间当作「加入本地书架」的时间。
    dateAdded = lastVerifiedAt,
    // volumeCount 保持默认 0：本地档案不是单行本，卡片不会渲染「单行本 · N 卷」角标。
)

/** 添加日期标签的命名空间（TagNamespaceRegistry 中同名，展示为「添加日期」）。 */
private const val DATE_ADDED_NAMESPACE = "date_added"

/**
 * 从标签里解析 `date_added:<epoch>`，统一返回 **epoch 毫秒**（0 表示未知）。
 *
 * 服务端写入的是秒（`time()` 或文件 mtime）；这里对已是毫秒的历史数据做一次量级兜底，
 * 避免本地/远端两种来源混排时出现 1970 或 +57000 年这类脏值。
 */
internal fun parseDateAddedMillis(tags: List<String>): Long {
    val raw = tags.asSequence()
        .mapNotNull { tag ->
            val sep = tag.indexOf(':')
            if (sep <= 0) return@mapNotNull null
            if (!tag.substring(0, sep).trim().equals(DATE_ADDED_NAMESPACE, ignoreCase = true)) return@mapNotNull null
            tag.substring(sep + 1).trim().toLongOrNull()
        }
        .firstOrNull { it > 0 }
        ?: return 0L
    return if (raw > MILLIS_THRESHOLD) raw else raw * 1000L
}

/** 大于该值即认为是毫秒时间戳（约等于公元 5138 年的秒级时间戳上限）。 */
private const val MILLIS_THRESHOLD = 100_000_000_000L
