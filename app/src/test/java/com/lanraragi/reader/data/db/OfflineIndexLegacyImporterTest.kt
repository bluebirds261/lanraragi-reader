package com.lanraragi.reader.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineIndexLegacyImporterTest {
    private val importer = OfflineIndexLegacyImporter()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("legacy/$name")) { name }.readText()

    private fun facts(
        arcid: String,
        path: String = "/offline/$arcid/original.archive",
        exists: Boolean = true,
        bytes: Long = 40L,
        modified: Long = 9L,
    ) = OfflineIndexLegacyImporter.ArtifactFacts(arcid, path, exists, bytes, modified)

    @Test
    fun importsOnlyRemoteEntriesBackedByOriginalArchiveFacts() {
        val result = importer.decodeResult(
            fixture("offline-index-valid.json"),
            listOf(facts("remote-one", bytes = 100L, modified = 5L), facts("remote-two", bytes = 200L, modified = 6L)),
        )

        assertTrue(result.validDocument)
        assertEquals(2, result.entries.size)
        val first = result.entries.first { it.archiveId == "remote-one" }
        assertEquals("offline:remote-one", first.artifactId)
        assertEquals("/offline/remote-one/original.archive", first.filePath)
        assertEquals("legacy-offline:100:5", first.revision)
        assertEquals(100L, first.byteSize)
        assertEquals(12, first.pageCount)
        assertEquals(100L, first.lastAccessAt)
        assertFalse(first.pinned)
    }

    @Test
    fun validEmptyMalformedAndUnsupportedDocumentsRemainDistinct() {
        val empty = importer.decodeResult("{\"items\":[]}", emptyList())
        val malformed = importer.decodeResult("{not-json", emptyList())
        val unsupported = importer.decodeResult("{}", emptyList())

        assertTrue(empty.validDocument)
        assertTrue(empty.entries.isEmpty())
        assertFalse(malformed.validDocument)
        assertEquals("malformed_json", malformed.error)
        assertFalse(unsupported.validDocument)
        assertEquals("unsupported_format", unsupported.error)
    }

    @Test
    fun skipsLocalMissingAndIncompleteArtifacts() {
        val json = """
            {"items":[
              {"arcid":"local_1","pageCount":1},
              {"arcid":"explicit-local","isLocal":true,"pageCount":2},
              {"arcid":"missing"},
              {"arcid":"zero"},
              {"arcid":"valid","pageCount":-3,"lastAccess":-5}
            ]}
        """.trimIndent()
        val result = importer.decodeResult(
            json,
            listOf(
                facts("missing", exists = false),
                facts("zero", bytes = 0),
                facts("valid", bytes = 10),
            ),
        )

        assertTrue(result.validDocument)
        assertEquals(4, result.skippedEntries)
        assertEquals(1, result.entries.size)
        assertEquals(0, result.entries.single().pageCount)
        assertEquals(0L, result.entries.single().lastAccessAt)
    }

    @Test
    fun duplicateArcidsMergeDeterministicallyWithLatestAccessAndLargestPageCount() {
        val json = """
            {"items":[
              {"arcid":"DUP","pageCount":4,"lastAccess":10},
              {"arcid":"dup","pagecount":9,"lastAccessAt":20},
              {"arcid":"dup","pageCount":6,"lastAccess":15}
            ]}
        """.trimIndent()
        val first = importer.decodeResult(json, listOf(facts("dup", modified = 12L))).entries.single()
        val second = importer.decodeResult(json, listOf(facts("dup", modified = 12L))).entries.single()

        assertEquals("offline:dup", first.artifactId)
        assertEquals("dup", first.archiveId)
        assertEquals(9, first.pageCount)
        assertEquals(20L, first.lastAccessAt)
        assertEquals(first, second)
    }

    @Test
    fun artifactIdentityAndRevisionAreStableAndDerivedFromFacts() {
        val json = "{\"items\":[{\"arcid\":\"stable\",\"pageCount\":2}]}"
        val input = listOf(facts("stable", bytes = 777L, modified = 333L))

        val first = importer.decode(json, input).single()
        val second = importer.decode(json, input).single()

        assertEquals(first.artifactId, second.artifactId)
        assertEquals(first.revision, second.revision)
        assertEquals("legacy-offline:777:333", first.revision)
    }
}
