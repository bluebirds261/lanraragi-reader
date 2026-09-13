package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「添加日期」排序的回归测试。
 *
 * 背景：LANraragi 的档案 JSON **没有** dateadded 字段（`Utils/Database.pm build_json` 只输出
 * arcid/title/tags/summary/isnew/progress/pagecount/lastreadtime/size/toc），添加日期是以
 * `date_added:<epoch 秒>` 标签存放的。此前客户端拿不到真实时间戳，排序键全为 0，
 * 而 `entryComparator` 在并列时会退回按 arcid 排——把服务端已经算好的 sortby=date_added
 * 顺序整个打乱，用户看到的就是「添加日期规则没生效」。
 */
class LibraryDateAddedSortTest {

    @Test
    fun parsesSecondsTagIntoEpochMillis() {
        assertEquals(1_700_000_000_000L, parseDateAddedMillis(listOf("date_added:1700000000")))
    }

    @Test
    fun parsesTagAlongsideOtherTagsAndIsNamespaceCaseInsensitive() {
        val tags = listOf("artist:alice", "DATE_ADDED:1600000000", "language:chinese")
        assertEquals(1_600_000_000_000L, parseDateAddedMillis(tags))
    }

    @Test
    fun acceptsAlreadyMillisecondValuesWithoutRescaling() {
        assertEquals(1_700_000_000_123L, parseDateAddedMillis(listOf("date_added:1700000000123")))
    }

    @Test
    fun missingOrInvalidTagYieldsZero() {
        assertEquals(0L, parseDateAddedMillis(emptyList()))
        assertEquals(0L, parseDateAddedMillis(listOf("artist:alice", "date_added:abc", "date_added:")))
        assertEquals(0L, parseDateAddedMillis(listOf("date_added:0", "date_added:-5")))
        // 前缀相近但命名空间不同，不应被误认。
        assertEquals(0L, parseDateAddedMillis(listOf("not_date_added:1700000000")))
    }

    @Test
    fun firstValidTagWinsWhenRepeated() {
        assertEquals(1_500_000_000_000L, parseDateAddedMillis(listOf("date_added:1500000000", "date_added:1600000000")))
    }

    /** 有真实时间戳时按时间排，而不是按 arcid。 */
    @Test
    fun descendingDateAddedSortUsesTheParsedTimestamps() = runTest {
        val repository = MixedLibraryRepository(
            remote = remoteGateway(
                entry("aaa", 1_000_000_000_000L),
                entry("bbb", 3_000_000_000_000L),
                entry("ccc", 2_000_000_000_000L),
            ),
            local = emptyLocal(),
        )
        val page = repository.load(
            LibraryQuery(sort = LibrarySort.DATE_ADDED, direction = SortDirection.DESC, source = LibrarySource.REMOTE),
        )
        assertEquals(listOf("bbb", "ccc", "aaa"), page.items.map { (it.identity as ArchiveIdentity.Remote).arcid })
    }

    /**
     * 关键回归：服务端按 date_added 排好序、但客户端解析不到时间戳（服务器关了 usedateadded、
     * 或标签被排除）时，客户端必须**保持服务器返回顺序**，而不是退回按 arcid 重排。
     */
    @Test
    fun unknownTimestampsPreserveServerOrderInsteadOfResortingByArcid() = runTest {
        val repository = MixedLibraryRepository(
            remote = remoteGateway(
                entry("zzz", 0L),
                entry("aaa", 0L),
                entry("mmm", 0L),
            ),
            local = emptyLocal(),
        )
        val page = repository.load(
            LibraryQuery(sort = LibrarySort.DATE_ADDED, direction = SortDirection.DESC, source = LibrarySource.REMOTE),
        )
        assertEquals(listOf("zzz", "aaa", "mmm"), page.items.map { (it.identity as ArchiveIdentity.Remote).arcid })
    }

    private fun entry(arcid: String, dateAdded: Long) = LibraryEntry(
        identity = ArchiveIdentity.Remote(arcid),
        title = arcid,
        dateAdded = dateAdded,
    )

    private fun remoteGateway(vararg rows: LibraryEntry) = object : LibraryRemoteGateway {
        override suspend fun fetch(query: LibraryQuery): List<LibraryEntry> = rows.toList()
    }

    private fun emptyLocal() = object : LibraryLocalGateway {
        override suspend fun fetch(): List<LibraryEntry> = emptyList()
    }
}
