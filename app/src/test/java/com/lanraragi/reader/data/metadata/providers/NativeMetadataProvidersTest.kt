package com.lanraragi.reader.data.metadata.providers

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMetadataProvidersTest {
    @Test
    fun ehentaiCanonicalizesSupportedUrlVariants() {
        val canonical = EHentaiMetadataProvider.parseExact("https://E-HENTAI.org/g/12345/AaB09/")
        val bare = EHentaiMetadataProvider.parseExact("e-hentai.org/g/12345/AaB09")
        val exhentai = EHentaiMetadataProvider.parseExact("http://exhentai.org/g/12345/AaB09")

        assertEquals("https://e-hentai.org/g/12345/AaB09", canonical?.canonicalUrl)
        assertEquals(canonical?.canonicalUrl, bare?.canonicalUrl)
        assertEquals("https://exhentai.org/g/12345/AaB09", exhentai?.canonicalUrl)
    }

    @Test
    fun ehentaiRejectsAmbiguousAndHostConfusionUrls() {
        listOf(
            "https://e-hentai.org/g/123/token?next=1",
            "https://e-hentai.org/g/123/token#fragment",
            "https://e-hentai.org:443/g/123/token",
            "https://e-hentai.org.evil.test/g/123/token",
            "https://e-hentai.org@evil.test/g/123/token",
            "https://e-hentai.org/g/0/token",
            "javascript://e-hentai.org/g/123/token",
        ).forEach { value -> assertNull(value, EHentaiMetadataProvider.parseExact(value)) }
    }

    @Test
    fun nhentaiExtractsExplicitAndBracedFilenameIdentifiers() {
        assertEquals("321", NHentaiMetadataProvider.parseExact("https://nhentai.net/g/321/")?.id)

        val fromFile = NHentaiMetadataProvider.findCandidates(
            MetadataCandidateInput(fileName = "{321} Sample Title.cbz", observedAt = 100L),
        ).first { it.match == MetadataCandidateMatch.FILENAME_IDENTIFIER }

        assertEquals("321", fromFile.sourceId)
        assertEquals("https://nhentai.net/g/321", fromFile.sourceUrl)
        assertEquals(0.92f, fromFile.confidence)
        assertNotNull(fromFile.patch)
    }

    @Test
    fun nhentaiRejectsHostConfusionAndNonGalleryUrls() {
        listOf(
            "https://nhentai.net/g/321?redirect=evil",
            "https://nhentai.net/g/321#fragment",
            "https://nhentai.net:443/g/321",
            "https://nhentai.net.evil.test/g/321",
            "https://nhentai.net@evil.test/g/321",
            "https://nhentai.net/search/?q=321",
            "0",
        ).forEach { value -> assertNull(value, NHentaiMetadataProvider.parseExact(value)) }
    }

    @Test
    fun sourceTagInputBeatsTitleCandidateAndCarriesTagProvenance() {
        val source = CanonicalTag.parse(
            "source:https://e-hentai.org/g/7788/Token9",
            source = TagSource.LANRARAGI,
        )
        val candidates = EHentaiMetadataProvider.findCandidates(
            MetadataCandidateInput(
                existingTags = listOf(source),
                archiveTitle = "[7788] An Archive Title",
                observedAt = 400L,
            ),
        )

        val exact = candidates.first()
        assertEquals(MetadataCandidateMatch.EXPLICIT_SOURCE_URL, exact.match)
        assertEquals("7788", exact.sourceId)
        assertEquals("https://e-hentai.org/g/7788/Token9", exact.sourceUrl)
        assertEquals("ehentai", exact.patch?.sourceUrl?.provenance?.providerId)
        assertEquals(400L, exact.patch?.sourceUrl?.provenance?.fetchedAt)
        val sourceTag = exact.patch?.addTags?.single()
        assertEquals(TagSource.EHENTAI, sourceTag?.source)
        assertEquals(
            exact.patch?.sourceUrl?.provenance,
            sourceTag?.full?.let { exact.patch?.tagProvenance?.get(it) },
        )
    }

    @Test
    fun titleCandidatesAreNormalizedStableAndCannotWriteBack() {
        val input = MetadataCandidateInput(
            archiveTitle = "  [Circle]  Fullwidth\u3000Title  .cbz ",
            observedAt = 5L,
        )

        val first = NHentaiMetadataProvider.findCandidates(input)
        val second = NHentaiMetadataProvider.findCandidates(input)
        val title = first.single { it.match == MetadataCandidateMatch.TITLE_SEARCH }

        assertEquals(first, second)
        assertEquals("[Circle] Fullwidth Title", title.normalizedTitle)
        assertTrue(title.isLowConfidence)
        assertNull(title.patch)
        assertTrue(title.requiresUserConfirmation)
    }

    @Test
    fun exactStandaloneIdentifierReceivesPatchWhileTitleBracketIdDoesNot() {
        val exact = NHentaiMetadataProvider.findCandidates(
            MetadataCandidateInput(sourceUrls = listOf("123456"), observedAt = 10L),
        ).single()
        val titleId = EHentaiMetadataProvider.findCandidates(
            MetadataCandidateInput(archiveTitle = "Album [123456]", observedAt = 10L),
        ).first { it.match == MetadataCandidateMatch.TITLE_IDENTIFIER }

        assertEquals(MetadataCandidateMatch.EXPLICIT_IDENTIFIER, exact.match)
        assertFalse(exact.isLowConfidence)
        assertNotNull(exact.patch)
        assertEquals(MetadataCandidateMatch.TITLE_IDENTIFIER, titleId.match)
        assertTrue(titleId.isLowConfidence)
        assertNull(titleId.patch)
    }

    @Test
    fun providerRegistryIsStableAndOffline() {
        assertEquals(listOf("ehentai", "nhentai"), NativeMetadataProviders.all.map { it.providerId })
    }
}
