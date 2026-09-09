package com.lanraragi.reader.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRegistryEdgeCaseTest {
    @Test
    fun nullBlankAndColonNamespacesKeepTheirExplicitMeaning() {
        assertNull(TagNamespaceRegistry.canonicalNamespace(null))
        assertNull(TagNamespaceRegistry.canonicalNamespace(" \t\n　"))
        assertNull(TagNamespaceRegistry.descriptor(""))

        assertEquals("artist:", TagNamespaceRegistry.canonicalNamespace(" ARTIST: "))
        assertEquals("artist:", TagNamespaceRegistry.descriptor("artist:")!!.name)
        assertEquals("", TagNamespaceRegistry.sortKey(" ").namespace)
    }

    @Test
    fun unknownNamespaceFallbackIsNormalizedAndDeterministic() {
        val first = TagNamespaceRegistry.descriptor("  Custom　Namespace ")!!
        val second = TagNamespaceRegistry.descriptor("custom namespace")!!

        assertEquals(first, second)
        assertEquals("custom namespace", first.name)
        assertEquals("custom namespace", first.labelZh)
        assertEquals(TagNamespaceRegistry.UNKNOWN_COLOR_TOKEN, first.defaultColorToken)
        assertEquals(Int.MAX_VALUE, first.displayOrder)
        assertEquals(0, first.completionWeight)
    }

    @Test
    fun aliasesThatNormalizeToTheSameTextResolveToTheSameNamespace() {
        val aliases = listOf(
            "date added",
            " DATE　ADDED ",
            "date\n added",
            "date-added",
        )

        assertEquals(
            List(aliases.size) { "date_added" },
            aliases.map(TagNamespaceRegistry::canonicalNamespace),
        )
    }

    @Test
    fun hidingMetadataPreservesTheRelativeBuiltInOrder() {
        val all = TagNamespaceRegistry.allDescriptors(includeHidden = true)
        val visible = TagNamespaceRegistry.allDescriptors(includeHidden = false)

        assertTrue(all.size > visible.size)
        assertFalse(visible.any(TagNamespaceDescriptor::defaultHidden))
        assertEquals("custom", TagNamespaceRegistry.descriptor("custom")!!.name)
        assertTrue(visible.map(TagNamespaceDescriptor::name).containsAll(listOf("date", "date_added")))
        assertEquals(
            all.filterNot(TagNamespaceDescriptor::defaultHidden).map(TagNamespaceDescriptor::name),
            visible.map(TagNamespaceDescriptor::name),
        )
        assertEquals(
            listOf("source", "timestamp"),
            all.filter(TagNamespaceDescriptor::defaultHidden).map(TagNamespaceDescriptor::name),
        )
    }

    @Test
    fun completionOrderUsesWeightThenDisplayOrderAsItsDeterministicTieBreaker() {
        val completion = TagNamespaceRegistry.completionDescriptors(includeHidden = true)

        assertEquals(
            completion.sortedWith(
                compareByDescending<TagNamespaceDescriptor> { it.completionWeight }
                    .thenBy(TagNamespaceDescriptor::displayOrder),
            ),
            completion,
        )
        assertEquals(
            completion.map(TagNamespaceDescriptor::name).toSet().size,
            completion.size,
        )
    }
}
