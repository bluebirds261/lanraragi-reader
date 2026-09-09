package com.lanraragi.reader.data.metadata.providers

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.TagSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMetadataFetchTest {
    @Test
    fun coordinatorThrottlesSameProviderAndReturnsReviewPatch() = runTest {
        var now = 1_000L
        val waits = mutableListOf<Long>()
        val coordinator = NativeMetadataFetchCoordinator(
            gateway = NativeMetadataGateway { provider, id, url, _ ->
                NativeGalleryMetadata(
                    provider,
                    id,
                    url,
                    title = "Fetched",
                    tags = setOf(CanonicalTag.parse("artist:Alice", TagSource.EHENTAI, 0.98f)),
                    observedAt = now,
                )
            },
            clock = { now },
            minimumIntervalMs = 1_000L,
            wait = { delay -> waits += delay; now += delay },
        )

        coordinator.fetch("ehentai", "123", "https://e-hentai.org/g/123/token")
        val fetched = coordinator.fetch("ehentai", "123", "https://e-hentai.org/g/123/token")

        assertEquals(listOf(1_000L), waits)
        assertEquals("Fetched", fetched.toPatch().title?.value)
        assertTrue(fetched.toPatch().addTags.any { it.full == "source:https://e-hentai.org/g/123/token" })
        assertTrue(fetched.toPatch().addTags.any { it.raw == "artist:Alice" })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedToProviderFailure() = runTest {
        val coordinator = NativeMetadataFetchCoordinator(
            gateway = NativeMetadataGateway { _, _, _, _ -> throw CancellationException("cancelled") },
        )
        coordinator.fetch("nhentai", "123", "https://nhentai.net/g/123")
    }

    @Test
    fun nativeWireParsersKeepNamespacedTagsAndIgnoreUnknownFields() {
        val nh = NativeMetadataWireParser.parseNhentai(
            """
            {
              "title":{"english":"English title","japanese":"Japanese title"},
              "tags":[{"type":"artist","name":"Alice","unknown":1},{"type":"language","name":"english"}],
              "unknown":{"future":true}
            }
            """.trimIndent(),
            requireNotNull(NHentaiMetadataProvider.parseExact("123")),
            7L,
        )
        val eh = NativeMetadataWireParser.parseEhentai(
            """
            {"gmetadata":[{"gid":456,"token":"abc","title":"EH title","tags":["artist:Bob","female:glasses"],"future":"field"}]}
            """.trimIndent(),
            requireNotNull(EHentaiMetadataProvider.parseExact("456/abc")),
            8L,
        )

        assertEquals("English title", nh.title)
        assertEquals(setOf("artist:alice", "language:english"), nh.tags.map { it.full }.toSet())
        assertEquals("EH title", eh.title)
        assertEquals(setOf("artist:bob", "female:glasses"), eh.tags.map { it.full }.toSet())
    }
}
