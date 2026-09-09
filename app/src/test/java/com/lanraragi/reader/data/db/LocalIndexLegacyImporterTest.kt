package com.lanraragi.reader.data.db

import com.lanraragi.reader.domain.model.ArchiveIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIndexLegacyImporterTest {
    private val importer = LocalIndexLegacyImporter()

    @Test
    fun decodesLegacyWrappersAndFieldAliases() {
        val json = """
            {"items":[
              {"uri":" content://reader/a ","name":"Alias A","pageCount":12,"cover":"cover/a.jpg","lastVerified":9},
              {"localUri":"content://reader/b","title":"Canonical B","pagecount":2,"coverUri":"content://reader/b/1.jpg","availability":"UNAVAILABLE"},
              {"path":"content://reader/c","title":"Path C"}
            ]}
        """.trimIndent()

        val result = importer.decodeResult(json)

        assertTrue(result.validDocument)
        assertEquals(3, result.entries.size)
        assertEquals("Alias A", result.entries[0].title)
        assertEquals(12, result.entries[0].pageCount)
        assertEquals("cover/a.jpg", result.entries[0].coverEntry)
        assertEquals(ArchiveIdentity.LocalSaf("content://reader/a").sourceKey, result.entries[0].sourceKey)
        assertEquals("UNAVAILABLE", result.entries[1].availability)
        assertEquals(0, result.entries[2].pageCount)
    }

    @Test
    fun acceptsArrayAndSingleArchiveObject() {
        val array = importer.decode("[{\"summary\":\"content://reader/x\",\"title\":\"X\"}]")
        val single = importer.decode("{\"summary\":\"content://reader/y\",\"title\":\"Y\"}")

        assertEquals("X", array.single().title)
        assertEquals("Y", single.single().title)
    }

    @Test
    fun skipsBadEntriesAndKeepsValidDocument() {
        val result = importer.decodeResult("{\"archives\":[null,42,{}, {\"summary\":\"content://reader/ok\"}]}")

        assertTrue(result.validDocument)
        assertEquals(1, result.entries.size)
        assertEquals(3, result.skippedEntries)
    }

    @Test
    fun malformedOrUnsupportedJsonIsReportedWithoutThrowing() {
        val malformed = importer.decodeResult("{not-json")
        val scalar = importer.decodeResult("42")

        assertFalse(malformed.validDocument)
        assertEquals("malformed_json", malformed.error)
        assertFalse(scalar.validDocument)
        assertEquals("unsupported_format", scalar.error)
        assertTrue(importer.decode("not-json").isEmpty())
    }

    @Test
    fun duplicateUrisKeepFirstDeterministicallyAndPreserveFingerprint() {
        val json = """
            {"entries":[
              {"summary":"content://reader/dup","title":"first","fingerprint":"fp-001"},
              {"summary":"content://reader/dup","title":"second","fingerprint":"fp-002"}
            ]}
        """.trimIndent()

        val first = importer.decodeResult(json).entries.single()
        val second = importer.decodeResult(json).entries.single()

        assertEquals("first", first.title)
        assertEquals("fp-001", first.fingerprint)
        assertEquals(first, second)
        assertEquals(ArchiveIdentity.LocalSaf("content://reader/dup").sourceKey, first.sourceKey)
    }

    @Test
    fun uriIdentityAndStableFieldsRemainStableAcrossDecode() {
        val json = "{\"archives\":[{\"summary\":\"content://reader/stable\",\"fingerprint\":\"abc\",\"lastVerifiedAt\":123}]}"
        val a = importer.decode(json).single()
        val b = importer.decode(json).single()

        assertEquals(a.sourceKey, b.sourceKey)
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals(a.lastVerifiedAt, b.lastVerifiedAt)
        assertTrue(a.sourceKey.startsWith("local:"))
    }
}
