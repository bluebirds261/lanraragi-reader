package com.lanraragi.reader.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排序键有效性判定的单元测试。
 *
 * 背景：LANraragi 把 `sortby` 当**字面正则**去标签串里匹配命名空间
 * （0.9.81 `lib/LANraragi/Model/Search.pm:576` `my $re = qr/$sortkey/;` 与 `:595`
 * `$tags =~ m/.*${re}:(.*?)(\,.*|$)/`）。命中不到时所有档案都进 `@unkeyed_ids`
 * 并保持底层任意顺序（`:621`），既不报错也不退回上一个顺序 —— 客户端只能靠
 * 服务器自己的标签统计判真伪。
 */
class LibrarySortResolverTest {

    private val counts = mapOf(
        "艺术家" to 422,
        "女性" to 394,
        "artist" to 132,
        "语言" to 12,
    )

    @Test
    fun serverBuiltInKeysAreAlwaysEffective() {
        listOf("title", "lastread", "date_added").forEach { key ->
            val report = LibrarySortResolver.report(key, emptyMap())
            assertEquals(key, LibrarySortResolver.Availability.EFFECTIVE, report.availability)
            assertFalse(report.ineffective)
        }
    }

    @Test
    fun unknownStatisticsNeverBlockAnOption() {
        val report = LibrarySortResolver.report("series", null)
        assertEquals(LibrarySortResolver.Availability.UNKNOWN, report.availability)
        assertFalse(report.ineffective)
        assertNull(LibrarySortResolver.explain("series", "系列", report))
        assertNull(LibrarySortResolver.shortNote("series", report))
    }

    @Test
    fun presentNamespaceIsEffectiveAndReportsItsTagCount() {
        val report = LibrarySortResolver.report("artist", counts)
        assertEquals(LibrarySortResolver.Availability.EFFECTIVE, report.availability)
        assertEquals(132, report.tagCount)
        assertNull(report.similarNamespace)
    }

    @Test
    fun namespaceLookupIsCaseInsensitive() {
        assertEquals(LibrarySortResolver.Availability.EFFECTIVE, LibrarySortResolver.report("Artist", counts).availability)
        assertEquals(
            LibrarySortResolver.Availability.EFFECTIVE,
            LibrarySortResolver.report("artist", mapOf("ARTIST" to 3)).availability,
        )
    }

    @Test
    fun absentNamespaceIsIneffective() {
        val report = LibrarySortResolver.report("series", counts)
        assertEquals(LibrarySortResolver.Availability.INEFFECTIVE, report.availability)
        assertTrue(report.ineffective)
        assertEquals("排序未生效：本库无 series 命名空间", LibrarySortResolver.shortNote("series", report))
    }

    /**
     * 库里存在「语义相同、写法不同」的命名空间时，提示里要带上服务端实际使用的写法，
     * 让用户知道不是应用坏了。注意这只是提示：`sortby` 仍然原样发送。
     */
    @Test
    fun similarNamespaceComesFromTheRegistryLabelOnly() {
        // language 的展示中文名是「语言」，服务器用的正是「语言」→ 给出提示。
        assertEquals("语言", LibrarySortResolver.report("language", counts).similarNamespace)
        // artist 的展示中文名是「作者」，服务器用的是「艺术家」→ 名称对不上，不猜。
        assertNull(LibrarySortResolver.report("artist", mapOf("艺术家" to 422)).similarNamespace)
    }

    @Test
    fun explainMentionsTheSimilarSpellingWhenKnown() {
        val text = LibrarySortResolver.explain("language", "语言", LibrarySortResolver.report("language", counts))
        assertEquals("本库没有 language 命名空间的标签，「语言」排序不会生效（库里相近的写法是「语言」）", text)
    }

    @Test
    fun emptyStatisticsMeanEveryNamespaceKeyIsIneffective() {
        // 空表 = 服务器上一个带命名空间的标签都没有（例如全新的空库），
        // 这与「还没取到统计」是两回事，必须区分开。
        assertTrue(LibrarySortResolver.report("artist", emptyMap()).ineffective)
        assertEquals(LibrarySortResolver.Availability.UNKNOWN, LibrarySortResolver.report("artist", null).availability)
    }

    @Test
    fun blankKeyIsTreatedAsBuiltIn() {
        assertEquals(LibrarySortResolver.Availability.EFFECTIVE, LibrarySortResolver.report("  ", null).availability)
    }

    @Test
    fun namespaceCountsIgnoreUnnamespacedTagsAndNormalizeCase() {
        val stats = listOf(
            com.lanraragi.reader.data.model.TagStat("Artist", "alice", 3),
            com.lanraragi.reader.data.model.TagStat("artist", "bob", 2),
            com.lanraragi.reader.data.model.TagStat("艺术家", "kakao", 27),
            com.lanraragi.reader.data.model.TagStat(null, "游离标签", 1),
            com.lanraragi.reader.data.model.TagStat("  ", "空白命名空间", 1),
        )
        assertEquals(
            mapOf("artist" to 2, "艺术家" to 1),
            com.lanraragi.reader.data.SearchDiscoveryRepository.namespaceCountsOf(stats),
        )
    }
}
