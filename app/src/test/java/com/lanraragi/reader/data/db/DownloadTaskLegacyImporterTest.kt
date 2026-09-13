package com.lanraragi.reader.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskLegacyImporterTest {
    private val importer = DownloadTaskLegacyImporter()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("legacy/$name")) { name }.readText()

    @Test
    fun decodesLegacyWrapperAndNormalizesStatesWithoutPersistingApiKey() {
        val entries = importer.decode(fixture("download-tasks-valid.json"), importedAt = 123L)

        assertEquals(3, entries.size)
        // "PAGE" (收藏单页) tasks were removed app-wide and are skipped on import.
        assertFalse(entries.any { it.taskId == "removed-page" })
        val waiting = entries[0]
        assertEquals("waiting-cache", waiting.taskId)
        assertEquals("OFFLINE_CACHE", waiting.type)
        assertEquals("archive-a", waiting.archiveId)
        assertEquals("FAILED", waiting.state)
        assertEquals(DownloadTaskLegacyImporter.LEGACY_SPEC_UNAVAILABLE, waiting.error)
        assertEquals(0L, waiting.completedBytes)
        assertNull(waiting.totalBytes)
        assertEquals(123L, waiting.createdAt)
        assertEquals(123L, waiting.updatedAt)
        assertFalse(waiting.payloadJson.contains("must-not-be-persisted"))
        assertFalse(waiting.payloadJson.contains("apiKey"))
        assertEquals(0.45, Json.parseToJsonElement(waiting.payloadJson).jsonObject["legacy"]!!
            .jsonObject["progress"]!!.toString().toDouble(), 0.0)

        val done = entries[1]
        assertEquals("ARCHIVE_FILE", done.type)
        assertEquals("DONE", done.state)
        assertEquals(100L, done.completedBytes)
        assertEquals(100L, done.totalBytes)
        assertEquals(0, done.retryCount)
        assertEquals(2, done.priority)

        val failed = entries[2]
        assertEquals("FAILED", failed.state)
        assertEquals("remote timeout", failed.error)
        assertEquals(1.0, Json.parseToJsonElement(failed.payloadJson).jsonObject["legacy"]!!
            .jsonObject["progress"]!!.toString().toDouble(), 0.0)
    }

    @Test
    fun validEmptyAndInvalidDocumentsAreDistinct() {
        val empty = importer.decodeResult("[]", importedAt = -1L)
        assertTrue(empty.validDocument)
        assertTrue(empty.entries.isEmpty())
        assertEquals(0, empty.skippedEntries)

        val malformed = importer.decodeResult("not-json", importedAt = 1L)
        assertFalse(malformed.validDocument)
        assertEquals("malformed_json", malformed.error)

        val unsupported = importer.decodeResult("{\"unknown\":[]}", importedAt = 1L)
        assertFalse(unsupported.validDocument)
        assertEquals("unsupported_format", unsupported.error)

        assertEquals("unsupported_format", importer.decodeResult("42", importedAt = 1L).error)
        assertEquals("unsupported_format", importer.decodeResult("null", importedAt = 1L).error)
        assertEquals("unsupported_format", importer.decodeResult("true", importedAt = 1L).error)
        assertEquals("unsupported_format", importer.decodeResult("false", importedAt = 1L).error)
    }

    @Test
    fun skipsMalformedEntriesAndKeepsFirstDuplicateDeterministically() {
        val result = importer.decodeResult(
            """[
                {"id":"removed-page","type":"PAGE","arcid":"gone","state":"DONE"},
                {"id":"duplicate","type":"OFFLINE_CACHE","arcid":"first","state":"DONE"},
                {"id":"duplicate","type":"ARCHIVE_FILE","arcid":"second","state":"DONE"},
                {"id":"","type":"OFFLINE_CACHE"},
                {"id":"unknown","type":"OTHER"}
            ]""",
            importedAt = 9L,
        )

        assertTrue(result.validDocument)
        assertEquals(1, result.entries.size)
        assertEquals("OFFLINE_CACHE", result.entries.single().type)
        assertEquals("first", result.entries.single().archiveId)
        assertEquals(3, result.skippedEntries)
    }

    @Test
    fun preservesOnlyExplicitByteProgressAndUsesInjectedNonNegativeTime() {
        val result = importer.decodeResult(
            """[{"id":"bytes","type":"OFFLINE_CACHE","state":"DONE","bytesDownloaded":7,"expectedBytes":-10,"progress":0.9}]""",
            importedAt = -99L,
        ).entries.single()

        assertEquals(7L, result.completedBytes)
        assertNull(result.totalBytes)
        assertEquals(0L, result.createdAt)
        assertEquals(0L, result.updatedAt)
        assertTrue(result.payloadJson.contains("0.9"))
    }

    @Test
    fun redactsPotentialSecretsInLegacyFailureText() {
        val result = importer.decode(
            """[{"id":"secret","type":"OFFLINE_CACHE","state":"FAILED","error":"api key abc"}]""",
            importedAt = 1L,
        ).single()

        assertEquals(DownloadTaskLegacyImporter.LEGACY_TASK_FAILED, result.error)
        assertFalse(result.payloadJson.contains("abc"))
    }
}
