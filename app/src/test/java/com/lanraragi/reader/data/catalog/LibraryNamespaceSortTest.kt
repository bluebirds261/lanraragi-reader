package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 按命名空间排序（作者 / 语言 / 系列）的回归测试。
 *
 * 背景：`entryComparator` 原先对这三个字段落到 `else` 分支，用
 * `tags.firstOrNull()`（标签列表里的**第一个**标签，可能是语言、可能是时间戳）
 * 去排 —— 相当于用无关字段重排一遍，用户看到的是「排序规则没生效」。
 * 这里改成按目标命名空间取值，并且命中不到该命名空间的条目留在末尾、
 * 不随升/降序翻转（与服务端 `Search.pm:616-621` 一致：先 reverse，再把未命中项 push 到末尾）。
 */
class LibraryNamespaceSortTest {

    @Test
    fun sortsByTheNamespaceValueNotByTheFirstTag() = runTest {
        val repository = repository(
            entry("b", listOf("date_added:1", "artist:zoe")),
            entry("c", listOf("date_added:2", "artist:amy")),
            entry("a", listOf("date_added:3", "artist:moe")),
        )
        val page = repository.load(
            LibraryQuery(sort = LibrarySort.ARTIST, direction = SortDirection.ASC, source = LibrarySource.LOCAL),
        )
        assertEquals(listOf("c", "a", "b"), page.items.map { (it.identity as ArchiveIdentity.LocalSaf).uri })
    }

    @Test
    fun descendingReversesOnlyTheKeyedEntries() = runTest {
        val repository = repository(
            entry("b", listOf("artist:zoe")),
            entry("c", listOf("artist:amy")),
            entry("a", listOf("artist:moe")),
        )
        val page = repository.load(
            LibraryQuery(sort = LibrarySort.ARTIST, direction = SortDirection.DESC, source = LibrarySource.LOCAL),
        )
        assertEquals(listOf("b", "a", "c"), page.items.map { (it.identity as ArchiveIdentity.LocalSaf).uri })
    }

    @Test
    fun entriesMissingTheNamespaceStayLastInBothDirections() = runTest {
        val rows = arrayOf(
            entry("no1", listOf("language:chinese")),
            entry("keyed1", listOf("artist:amy")),
            entry("no2", listOf("language:japanese")),
            entry("keyed2", listOf("artist:zoe")),
        )
        val asc = repository(*rows).load(
            LibraryQuery(sort = LibrarySort.ARTIST, direction = SortDirection.ASC, source = LibrarySource.LOCAL),
        )
        assertEquals(listOf("keyed1", "keyed2", "no1", "no2"), asc.items.map { (it.identity as ArchiveIdentity.LocalSaf).uri })

        val desc = repository(*rows).load(
            LibraryQuery(sort = LibrarySort.ARTIST, direction = SortDirection.DESC, source = LibrarySource.LOCAL),
        )
        assertEquals(listOf("keyed2", "keyed1", "no1", "no2"), desc.items.map { (it.identity as ArchiveIdentity.LocalSaf).uri })
    }

    /**
     * 关键回归：库里根本没有该命名空间的标签时，必须**保持原顺序**，
     * 而不是拿 `tags.firstOrNull()` 把顺序搅乱。
     */
    @Test
    fun missingNamespaceEverywherePreservesOriginalOrder() = runTest {
        val repository = repository(
            entry("zzz", listOf("语言:汉语")),
            entry("aaa", listOf("语言:翻译")),
            entry("mmm", listOf("语言:日语")),
        )
        val page = repository.load(
            LibraryQuery(sort = LibrarySort.SERIES, direction = SortDirection.ASC, source = LibrarySource.LOCAL),
        )
        assertEquals(listOf("zzz", "aaa", "mmm"), page.items.map { (it.identity as ArchiveIdentity.LocalSaf).uri })
    }

    @Test
    fun namespaceMatchIsCaseInsensitiveAndIgnoresSurroundingSpaces() = runTest {
        val repository = repository(
            entry("b", listOf(" Artist : zoe ")),
            entry("a", listOf("artist:amy")),
        )
        val page = repository.load(
            LibraryQuery(sort = LibrarySort.ARTIST, direction = SortDirection.ASC, source = LibrarySource.LOCAL),
        )
        assertEquals(listOf("a", "b"), page.items.map { (it.identity as ArchiveIdentity.LocalSaf).uri })
    }

    private fun entry(key: String, tags: List<String>) = LibraryEntry(
        identity = ArchiveIdentity.LocalSaf(key),
        title = key,
        tags = tags,
    )

    private fun repository(vararg rows: LibraryEntry) = MixedLibraryRepository(
        remote = object : LibraryRemoteGateway {
            override suspend fun fetch(query: LibraryQuery): List<LibraryEntry> = emptyList()
        },
        local = object : LibraryLocalGateway {
            override suspend fun fetch(): List<LibraryEntry> = rows.toList()
        },
    )
}
