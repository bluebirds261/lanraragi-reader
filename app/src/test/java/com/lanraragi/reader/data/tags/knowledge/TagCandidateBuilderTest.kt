package com.lanraragi.reader.data.tags.knowledge

import com.lanraragi.reader.data.model.TagStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联想候选合并与排序的回归测试。
 *
 * 背景：用户的服务器上标签是**中英双词汇并存**的（`艺术家` 422 个 / `artist` 132 个、
 * `原作` 的值是中文、`parody` 的值是英文），而 EhTagTranslation 词库只有英文命名空间。
 * 历史实现把词库命中无差别排在本库命中之前，用户点第一条常常搜出 0 条结果。
 *
 * 这里的排序原则：**先看文本相关度，同级时本库真的有这个标签的排前面**，
 * 并把来源与频次如实带在行上。
 */
class TagCandidateBuilderTest {

    @Test
    fun libraryHitOutranksDictionaryHitOfEqualRelevance() {
        // `艺术家:art jam` 的整串不以 art 开头，但**标签值**以 art 开头，
        // 必须与词库的 `artist:art jam` 同为前缀级命中，然后靠「本库有」胜出。
        val rows = TagCandidateBuilder.build(
            token = "art",
            dictionary = listOf(dict("artist", "art jam", TagMatchQuality.CANONICAL_PREFIX)),
            libraryTags = listOf(stat("艺术家", "art jam", 27)),
            limit = 10,
        )
        assertEquals(listOf("艺术家:art jam", "artist:art jam"), rows.map { it.full })
        assertEquals(27, rows.first().libraryCount)
        assertTrue(rows.first().inLibrary)
        assertFalse(rows.last().inLibrary)
        assertEquals("本库 27", rows.first().sourceLabel)
        assertEquals("词库", rows.last().sourceLabel)
    }

    @Test
    fun betterTextRelevanceStillWinsOverLibraryPresence() {
        // 词库是真前缀命中、本库只是任意位置包含：相关度优先，词库在前。
        val rows = TagCandidateBuilder.build(
            token = "jam",
            dictionary = listOf(dict("artist", "jam session", TagMatchQuality.CANONICAL_PREFIX)),
            libraryTags = listOf(stat("艺术家", "art jam", 27)),
            limit = 10,
        )
        assertEquals(listOf("artist:jam session", "艺术家:art jam"), rows.map { it.full })
    }

    @Test
    fun explicitNamespaceTokenFiltersBothSources() {
        val rows = TagCandidateBuilder.build(
            token = "artist:art",
            dictionary = listOf(dict("artist", "art jam", TagMatchQuality.CANONICAL_PREFIX)),
            libraryTags = listOf(stat("艺术家", "art jam", 27), stat("artist", "art boy", 4)),
            limit = 10,
        )
        assertEquals(listOf("artist:art boy", "artist:art jam"), rows.map { it.full })
    }

    @Test
    fun namespaceOnlyTokenListsThatNamespaceAndPrefersTheLibrary() {
        val rows = TagCandidateBuilder.build(
            token = "language:",
            dictionary = listOf(
                dict("language", "chinese", TagMatchQuality.NAMESPACE_EXACT),
                dict("language", "japanese", TagMatchQuality.NAMESPACE_EXACT),
            ),
            libraryTags = listOf(stat("语言", "汉语", 1494), stat("language", "chinese", 3)),
            limit = 10,
        )
        // 中文命名空间被 token 限定排除；本库里的 language:chinese 排在同级的词库候选前。
        assertEquals(listOf("language:chinese", "language:japanese"), rows.map { it.full })
        assertEquals(3, rows.first().libraryCount)
    }

    @Test
    fun sameTagFromBothSourcesIsMergedNotDuplicated() {
        val rows = TagCandidateBuilder.build(
            token = "kakao",
            dictionary = listOf(dict("artist", "kakao", TagMatchQuality.CANONICAL_PREFIX, translated = "卡考").copy(score = 12.0)),
            libraryTags = listOf(stat("artist", "kakao", 27)),
            limit = 10,
        )
        assertEquals(1, rows.size)
        assertEquals(27, rows.single().libraryCount)
        assertEquals("卡考", rows.single().translatedName)
        assertEquals(12.0, rows.single().score, 0.0001)
    }

    @Test
    fun personalFrequencyIsCarriedAndUsedAsATieBreak() {
        val rows = TagCandidateBuilder.build(
            token = "ama",
            dictionary = listOf(
                dict("group", "amam", TagMatchQuality.CANONICAL_PREFIX, personal = 0L),
                dict("artist", "amai", TagMatchQuality.CANONICAL_PREFIX, personal = 5L),
            ),
            libraryTags = emptyList(),
            limit = 10,
        )
        assertEquals(listOf("artist:amai", "group:amam"), rows.map { it.full })
        assertEquals(5L, rows.first().personalCount)
    }

    @Test
    fun unnamespacedTagsProduceAPlainValue() {
        val rows = TagCandidateBuilder.build(
            token = "游离",
            dictionary = emptyList(),
            libraryTags = listOf(stat(null, "游离标签", 2)),
            limit = 10,
        )
        assertEquals(listOf("游离标签"), rows.map { it.full })
        assertEquals("", rows.single().namespace)
        assertNull(rows.single().translatedName)
    }

    @Test
    fun limitIsRespectedAndOrderIsStable() {
        val many = (1..50).map { stat("female", "tag%02d".format(it), it) }
        val rows = TagCandidateBuilder.build("tag", emptyList(), many, limit = 12)
        assertEquals(12, rows.size)
        // 同级命中按本库频次降序，便于用户先看到更常见的标签。
        assertEquals(listOf("female:tag50", "female:tag49"), rows.take(2).map { it.full })
    }

    @Test
    fun blankTokenYieldsNothing() {
        assertTrue(TagCandidateBuilder.build("   ", listOf(dict("artist", "x")), listOf(stat("artist", "x", 1))).isEmpty())
    }

    @Test
    fun noMatchAtAllYieldsNothing() {
        assertTrue(
            TagCandidateBuilder.build(
                token = "zzzz",
                dictionary = emptyList(),
                libraryTags = listOf(stat("artist", "kakao", 27)),
            ).isEmpty(),
        )
    }

    private fun dict(
        namespace: String,
        key: String,
        quality: TagMatchQuality = TagMatchQuality.NONE,
        translated: String? = null,
        personal: Long = 0L,
    ) = TagSuggestion(
        entry = TagDictionaryRecord(namespace = namespace, tagKey = key, translatedName = translated, dataVersion = "test"),
        quality = quality,
        score = 0.0,
        frequency = 0L,
        personalFrequency = personal,
    )

    private fun stat(namespace: String?, text: String, weight: Int) = TagStat(namespace, text, weight)
}
