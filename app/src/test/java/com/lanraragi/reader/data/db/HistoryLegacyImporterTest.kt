package com.lanraragi.reader.data.db

import com.lanraragi.reader.domain.model.ArchiveIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLegacyImporterTest {
    private val importer = HistoryLegacyImporter()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("legacy/$name")) { name }.readText()

    @Test
    fun decodesArrayAndObjectAliases() {
        val entries = importer.decode(fixture("history-valid.json"))
        assertEquals(2, entries.size)
        assertEquals("remote:abc", entries[0].sourceKey)
        assertEquals("Gallery A", entries[0].title)
        assertEquals(3, entries[0].page)
        assertEquals(10, entries[0].pageCount)
    }

    @Test
    fun duplicateEntriesMergeBySourceKeepingLatestProgressAndEarliestFirstRead() {
        val entries = importer.decode(fixture("history-duplicate.json"))
        assertEquals(1, entries.size)
        val result = entries.single()
        assertEquals("remote:dup", result.sourceKey)
        assertEquals("Newest title", result.title)
        assertEquals(7, result.page)
        assertEquals(12, result.pageCount)
        assertEquals(100L, result.firstReadAt)
        assertEquals(300L, result.lastReadAt)
    }

    @Test
    fun localArchiveUsesUriIdentityAndKeepsLegacyNavigationId() {
        val entries = importer.decode(fixture("history-valid.json"), fixture("local-index.json"))
        val local = entries.first { it.title == "Local archive" }
        assertEquals(ArchiveIdentity.LocalSaf("content://reader/archive/42").sourceKey, local.sourceKey)
        assertEquals("local_42", local.archiveId)
    }

    @Test
    fun malformedInputIsIgnoredWithoutThrowing() {
        assertTrue(importer.decode(fixture("history-corrupt.json")).isEmpty())
        assertTrue(importer.decode("not-json").isEmpty())
    }

    @Test
    fun validEmptyArrayIsNotTreatedAsCorrupt() {
        val result = importer.decodeResult("[]")
        assertTrue(result.validDocument)
        assertTrue(result.entries.isEmpty())
    }
}
