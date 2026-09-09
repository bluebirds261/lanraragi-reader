package com.lanraragi.reader.data.tags

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNamespaceRegistryTest {
    @Test
    fun canonicalizesAliasesCaseAndUnicodeWhitespace() {
        assertEquals("artist", TagNamespaceRegistry.canonicalNamespace("  ARTISTS  "))
        assertEquals("date_added", TagNamespaceRegistry.canonicalNamespace("Date　Added"))
        assertEquals("uploader", TagNamespaceRegistry.canonicalNamespace("uploaded\n by"))
    }

    @Test
    fun preservesUnknownNamespacesWithStableFallbackAndLexicalSort() {
        val descriptor = TagNamespaceRegistry.descriptor("  Custom　Namespace ")!!

        assertEquals("custom namespace", descriptor.name)
        assertEquals("custom namespace", descriptor.labelZh)
        assertEquals(TagNamespaceRegistry.UNKNOWN_COLOR_TOKEN, descriptor.defaultColorToken)
        assertTrue(TagNamespaceRegistry.sortKey("artist") < TagNamespaceRegistry.sortKey("custom namespace"))
        assertTrue(TagNamespaceRegistry.sortKey("alpha") < TagNamespaceRegistry.sortKey("zeta"))
    }

    @Test
    fun exposesBuiltInsWithStableOrderWeightsColorsAndLabels() {
        val descriptors = TagNamespaceRegistry.allDescriptors()

        assertEquals(
            listOf("parody", "character", "group", "artist", "female"),
            descriptors.take(5).map(TagNamespaceDescriptor::name),
        )
        val artist = TagNamespaceRegistry.descriptor("artist")!!
        assertEquals("作者", artist.labelZh)
        assertEquals("#FFA726", artist.defaultColorToken)
        assertEquals(85, artist.completionWeight)
        assertEquals(3, artist.displayOrder)
        assertEquals(
            listOf("parody", "character", "group"),
            TagNamespaceRegistry.completionDescriptors().take(3).map(TagNamespaceDescriptor::name),
        )
        val series = TagNamespaceRegistry.descriptor("SERIES")!!
        val category = TagNamespaceRegistry.descriptor("category")!!
        val event = TagNamespaceRegistry.descriptor("event")!!
        val timestamp = TagNamespaceRegistry.descriptor("timestamp")!!
        assertEquals("系列", series.labelZh)
        assertEquals("#FFCA28", series.defaultColorToken)
        assertEquals(30, series.completionWeight)
        assertEquals(19, series.displayOrder)
        assertEquals("分类", category.labelZh)
        assertEquals("#90A4AE", category.defaultColorToken)
        assertEquals(29, category.completionWeight)
        assertEquals(20, category.displayOrder)
        assertEquals("活动", event.labelZh)
        assertEquals("#90A4AE", event.defaultColorToken)
        assertEquals(28, event.completionWeight)
        assertEquals(21, event.displayOrder)
        assertEquals("时间戳", timestamp.labelZh)
        assertEquals("#90A4AE", timestamp.defaultColorToken)
        assertEquals(1, timestamp.completionWeight)
        assertEquals(22, timestamp.displayOrder)
        assertTrue(TagNamespaceRegistry.sortKey("parody") < TagNamespaceRegistry.sortKey("series"))
        assertEquals("series", series.name)
    }

    @Test
    fun hidesMachineMetadataOnlyWhenRequested() {
        val visible = TagNamespaceRegistry.allDescriptors(includeHidden = false).map(TagNamespaceDescriptor::name)

        assertFalse("source" in visible)
        assertFalse("timestamp" in visible)
        assertTrue("artist" in visible)
        assertTrue("date" in visible)
        assertTrue("date_added" in visible)
        assertTrue(TagNamespaceRegistry.descriptor("source")!!.defaultHidden)
        assertFalse(TagNamespaceRegistry.descriptor("date")!!.defaultHidden)
        assertFalse(TagNamespaceRegistry.descriptor("date_added")!!.defaultHidden)
        assertTrue(TagNamespaceRegistry.descriptor("timestamp")!!.defaultHidden)
    }

    @Test
    fun everyNormalizedAliasHasExactlyOneCanonicalOwner() {
        val owners = TagNamespaceRegistry.allDescriptors().flatMap { descriptor ->
            (listOf(descriptor.name) + descriptor.aliases).map { it to descriptor.name }
        }.groupBy({ it.first }, { it.second })

        assertTrue(owners.values.all { it.distinct().size == 1 })
    }

    @Test
    fun namespaceLessTagsRemainNamespaceLess() {
        assertNull(TagNamespaceRegistry.canonicalNamespace("　 "))
        assertNull(TagNamespaceRegistry.descriptor(null))
        assertEquals("", TagNamespaceRegistry.sortKey(null).namespace)
    }

    @Test
    fun namespaceCanonicalizationDoesNotDependOnRawTranslationOrSource() {
        val first = CanonicalTag("ARTIST", "alice", "artist:Alice", "爱丽丝", TagSource.EHENTAI)
        val second = CanonicalTag("artist", "alice", "other raw", "另一译名", TagSource.USER)

        assertEquals(
            TagNamespaceRegistry.canonicalNamespace(first.namespace),
            TagNamespaceRegistry.canonicalNamespace(second.namespace),
        )
        assertEquals("artist", TagNamespaceRegistry.descriptor(first.namespace)!!.name)
    }
}
