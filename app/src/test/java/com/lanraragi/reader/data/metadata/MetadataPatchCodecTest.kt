package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MetadataPatchCodecTest {
    @Test
    fun roundTripPreservesAllPatchDataAndUsesStableOrdering() {
        val titleProvenance = MetadataProvenance(
            providerId = "ehentai",
            sourceId = "gallery-42",
            sourceUrl = "https://example.test/g/42/token",
            confidence = 0.95f,
            fetchedAt = 1_725_000_000_000,
        )
        val tagProvenance = titleProvenance.copy(sourceId = "tag-source", confidence = null)
        val artist = CanonicalTag(
            namespace = "artist",
            key = "alice",
            raw = "artist:Alice",
            displayNameZh = "Alice translated",
            source = TagSource.EHENTAI,
            confidence = 0.8f,
        )
        val language = CanonicalTag.parse("language:English", TagSource.LANRARAGI, 0.7f)
        val patch = MetadataPatch(
            title = MetadataField("A title", titleProvenance),
            summary = MetadataField("", titleProvenance.copy(providerId = "user")),
            addTags = linkedSetOf(language, artist),
            removeTags = setOf(CanonicalTag.parse("status:tagged", TagSource.USER)),
            tagProvenance = linkedMapOf(language.full to tagProvenance, artist.full to tagProvenance),
        )

        val encoded = MetadataPatchCodec.encode(patch)
        val decoded = MetadataPatchCodec.decode(encoded)
        val reordered = patch.copy(
            addTags = linkedSetOf(artist, language),
            tagProvenance = linkedMapOf(artist.full to tagProvenance, language.full to tagProvenance),
        )

        assertEquals(patch, decoded)
        assertEquals(encoded, MetadataPatchCodec.encode(decoded))
        assertEquals(encoded, MetadataPatchCodec.encode(reordered))
        assertTrue(encoded.contains("\"format\":\"metadata-patch\""))
        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.indexOf("artist:Alice") < encoded.indexOf("language:English"))
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("authorization", ignoreCase = true))
    }

    @Test
    fun decodesFixtureWithNullFieldsAndIgnoresUnknownData() {
        val encoded = javaClass.getResource("/metadata/metadata-patch-v1.json")!!.readText()

        val patch = MetadataPatchCodec.decode(encoded)

        assertNull(patch.title)
        assertNull(patch.sourceUrl)
        assertEquals("Fixture summary", patch.summary?.value)
        assertEquals(1, patch.addTags.size)
        val tag = patch.addTags.single()
        assertNull(tag.namespace)
        assertNull(tag.displayNameZh)
        assertEquals(TagSource.UNKNOWN, tag.source)
        assertNull(tag.confidence)
        assertEquals(1_700_000_000_000, patch.summary?.provenance?.fetchedAt)
    }

    @Test
    fun rejectsUnknownVersionWithoutTryingToDecodeItsPayload() {
        val error = expectCodecError(
            """{"format":"metadata-patch","version":99,"patch":{}}""",
        )

        assertEquals(MetadataPatchCodecError.UNSUPPORTED_VERSION, error.reason)
        assertTrue(error.message!!.contains("99"))
    }

    @Test
    fun distinguishesMalformedJsonFromInvalidEnvelope() {
        assertEquals(
            MetadataPatchCodecError.MALFORMED_JSON,
            expectCodecError("{not-json").reason,
        )
        assertEquals(
            MetadataPatchCodecError.INVALID_ENVELOPE,
            expectCodecError("[]").reason,
        )
        assertEquals(
            MetadataPatchCodecError.INVALID_ENVELOPE,
            expectCodecError("""{"format":"metadata-patch","version":1,"patch":null}""").reason,
        )
        assertEquals(
            MetadataPatchCodecError.INVALID_ENVELOPE,
            expectCodecError("""{"format":"metadata-patch","version":"1","patch":{}}""").reason,
        )
    }

    @Test
    fun rejectsInvalidDomainDataAndUnknownTagSource() {
        val blankProvider = validEnvelope(
            """{"value":"Title","provenance":{"providerId":"","fetchedAt":1}}""",
        )
        assertEquals(MetadataPatchCodecError.INVALID_DATA, expectCodecError(blankProvider).reason)

        val invalidConfidence = validEnvelope(
            """{"value":"Title","provenance":{"providerId":"provider","confidence":1.5,"fetchedAt":1}}""",
        )
        assertEquals(MetadataPatchCodecError.INVALID_DATA, expectCodecError(invalidConfidence).reason)

        val unknownSource = """
            {
              "format":"metadata-patch",
              "version":1,
              "patch":{
                "addTags":[{"key":"tag","raw":"tag","source":"FUTURE_SOURCE"}]
              }
            }
        """.trimIndent()
        assertEquals(MetadataPatchCodecError.INVALID_DATA, expectCodecError(unknownSource).reason)
    }

    private fun validEnvelope(titleField: String): String = """
        {
          "format":"metadata-patch",
          "version":1,
          "patch":{"title":$titleField}
        }
    """.trimIndent()

    private fun expectCodecError(encoded: String): MetadataPatchCodecException = try {
        MetadataPatchCodec.decode(encoded)
        fail("expected MetadataPatchCodecException")
        error("unreachable")
    } catch (error: MetadataPatchCodecException) {
        error
    }
}
