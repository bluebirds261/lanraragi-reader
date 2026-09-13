package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MetadataSnapshotCodecTest {
    @Test
    fun encodeIsStableAcrossTagOwnershipAndProvenanceInsertionOrder() {
        val artist = CanonicalTag(
            namespace = "artist",
            key = "alice",
            raw = "artist:Alice",
            displayNameZh = "Alice",
            source = TagSource.USER,
            confidence = 0.9f,
        )
        val language = CanonicalTag.parse("language:English", source = TagSource.LANRARAGI)
        val provenance = MetadataProvenance(
            providerId = "user",
            sourceId = "manual",
            fetchedAt = 1_725_000_000_000L,
        )
        val first = MetadataSnapshot(
            title = "Title",
            summary = "Summary",
            sourceUrl = "https://example.test/gallery/1",
            tags = linkedSetOf(language, artist),
            userOverrides = linkedSetOf(MetadataFieldName.TAGS, MetadataFieldName.SUMMARY),
            provenance = linkedMapOf(
                "tag:${language.full}" to provenance,
                "summary" to provenance,
                "tag:${artist.full}" to provenance,
            ),
            revision = "r1",
        )
        val reordered = first.copy(
            tags = linkedSetOf(artist, language),
            userOverrides = linkedSetOf(MetadataFieldName.SUMMARY, MetadataFieldName.TAGS),
            provenance = linkedMapOf(
                "tag:${artist.full}" to provenance,
                "summary" to provenance,
                "tag:${language.full}" to provenance,
            ),
        )

        val encoded = MetadataSnapshotCodec.encode(first)

        assertEquals(encoded, MetadataSnapshotCodec.encode(reordered))
        assertEquals(first, MetadataSnapshotCodec.decode(encoded))
        assertEquals(encoded, MetadataSnapshotCodec.encode(MetadataSnapshotCodec.decode(encoded)))
        assertTrue(encoded.indexOf("artist:Alice") < encoded.indexOf("language:English"))
        assertTrue(encoded.indexOf("SUMMARY") < encoded.indexOf("TAGS"))
    }

    @Test
    fun decodeClassifiesUnsupportedAndCorruptSnapshotsAsExplicitStoreErrors() {
        val unsupported = expectStoreError("""{"version":2,"snapshot":{}}""")
        val malformed = expectStoreError("{not-json")
        val invalidDomain = expectStoreError(
            """{"version":1,"snapshot":{"tags":[{"key":"tag","raw":"tag","source":"FUTURE"}]}}""",
        )

        assertTrue(unsupported.message.orEmpty().contains("Unsupported metadata snapshot version: 2"))
        assertEquals("Invalid persisted metadata snapshot", malformed.message)
        assertTrue(invalidDomain.message.orEmpty().contains("Unknown metadata tag source: FUTURE"))
        assertTrue(malformed.cause != null)
        assertTrue(invalidDomain.cause == null)
    }

    @Test
    fun decodeRejectsMissingSnapshotEnvelopeAsPersistedStateError() {
        val error = expectStoreError("""{"version":1}""")

        assertEquals("Invalid persisted metadata snapshot", error.message)
        assertTrue(error.cause != null)
    }

    private fun expectStoreError(encoded: String): MetadataStateStoreException = try {
        MetadataSnapshotCodec.decode(encoded)
        fail("expected MetadataStateStoreException")
        error("unreachable")
    } catch (error: MetadataStateStoreException) {
        error
    }
}
