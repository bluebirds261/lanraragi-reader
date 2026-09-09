package com.lanraragi.reader.data.tags.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagQueryAndRankingTest {
    private val entries = listOf(
        TagDictionaryRecord("artist", "alice", "爱丽丝", dataVersion = "v1"),
        TagDictionaryRecord("artist", "alice cooper", "爱丽丝·库珀", dataVersion = "v1"),
        TagDictionaryRecord("character", "alice", "爱丽丝", dataVersion = "v1"),
        TagDictionaryRecord("group", "wonderland", "仙境", intro = "Alice group", dataVersion = "v1"),
    )

    @Test fun parsesEnglishChineseNamespacesModifiersAndQuotes() {
        val query = TagQueryParser.parse("作者:\"爱丽丝\" -group:old ~rabbit")
        assertEquals(3, query.tokens.size)
        assertEquals("artist", query.tokens[0].namespace)
        assertTrue(query.tokens[0].quoted)
        assertEquals(TagQueryModifier.EXCLUDE, query.tokens[1].modifier)
        assertEquals(TagQueryModifier.FUZZY, query.tokens[2].modifier)
    }

    @Test fun ranksCanonicalPrefixBeforeTranslationAndSubstring() {
        val ranked = TagSuggestionRanker.rank(entries, TagQueryParser.parse("alice"), limit = 10)
        assertEquals(listOf("alice", "alice cooper", "alice"), ranked.take(3).map { it.entry.tagKey })
        assertEquals("artist", ranked.first().entry.namespace)
        assertEquals(TagMatchQuality.CANONICAL_PREFIX, ranked.first().quality)
    }

    @Test fun appliesNamespaceFilterAndExclusion() {
        val ranked = TagSuggestionRanker.rank(entries, TagQueryParser.parse("artist:alice -artist:\"alice cooper\""))
        assertEquals(listOf("alice"), ranked.map { it.entry.tagKey })
    }

    @Test fun chineseSearchUsesTranslatedNameAndTieBreakIsStable() {
        val ranked = TagSuggestionRanker.rank(entries, TagQueryParser.parse("爱丽"), limit = 10)
        assertEquals(listOf("artist:alice", "artist:alice cooper", "character:alice"), ranked.map { it.entry.namespace + ":" + it.entry.tagKey })
        assertEquals(ranked, TagSuggestionRanker.rank(entries.reversed(), TagQueryParser.parse("爱丽"), limit = 10))
    }

    @Test fun emptyQueryReturnsDeterministicallySortedEntries() {
        val ranked = TagSuggestionRanker.rank(entries, TagQueryParser.parse(""), limit = 2)
        assertEquals(2, ranked.size)
        assertEquals("artist", ranked.first().entry.namespace)
    }

    @Test fun namespaceAliasAndCanonicalPrefixBeatVeryHighFrequency() {
        val ranked = TagSuggestionRanker.rank(
            entries,
            TagQueryParser.parse("alice"),
            frequencies = listOf(
                TagFrequencyRecord("group", "wonderland", "global", 10_000_000, "v1"),
            ),
        )
        assertEquals("alice", ranked.first().entry.tagKey)
        assertEquals(TagMatchQuality.CANONICAL_PREFIX, ranked.first().quality)

        val namespaceRanked = TagSuggestionRanker.rank(entries, TagQueryParser.parse("作者"))
        assertTrue(namespaceRanked.all { it.entry.namespace == "artist" })
        assertEquals(TagMatchQuality.NAMESPACE_EXACT, namespaceRanked.first().quality)
    }

    @Test fun allPositiveTokensMustMatch() {
        val ranked = TagSuggestionRanker.rank(entries, TagQueryParser.parse("alice wonder"))
        assertTrue(ranked.isEmpty())
    }
}
