package com.lanraragi.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRulesCompatibilityTest {
    @Test
    fun namespaceAliasesUseRegistryIdentityAndLabels() {
        assertEquals("artist", TagRules.nsOf("ARTISTS:Alice"))
        assertEquals("作者", TagRules.label("artists"))
        assertEquals("custom ns", TagRules.label(" Custom　NS "))
    }

    @Test
    fun defaultGroupingHidesOnlyMachineMetadataAndKeepsStableOrder() {
        val tags = listOf(
            "custom:value",
            "timestamp:123",
            "artist:alice",
            "plain",
            "date:2024-01-01",
            "source:https://example.invalid",
            "parody:original",
        )

        val grouped = TagRules.groupTags(tags)
        val namespaces = grouped.map { it.first }
        val visibleTags = grouped.flatMap { it.second }

        assertEquals(listOf("parody", "artist", "date", "custom", ""), namespaces)
        assertFalse(visibleTags.any { it.startsWith("source:") })
        assertFalse(visibleTags.any { it.startsWith("timestamp:") })
        assertTrue("date:2024-01-01" in visibleTags)
    }

    @Test
    fun explicitHiddenGroupingRetainsEveryOriginalTag() {
        val tags = listOf(
            "source:https://example.invalid",
            "timestamp:123",
            "date_added:456",
            "unknown:value",
            "plain",
        )

        val retained = TagRules.groupTags(tags, includeHidden = true).flatMap { it.second }

        assertEquals(tags.size, retained.size)
        assertEquals(tags.toSet(), retained.toSet())
    }
}
