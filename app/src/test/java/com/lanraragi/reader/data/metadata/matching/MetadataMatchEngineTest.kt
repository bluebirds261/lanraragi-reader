package com.lanraragi.reader.data.metadata.matching

import com.lanraragi.reader.domain.metadata.CanonicalTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMatchEngineTest {
    @Test fun combinesEvidenceAndSortsDeterministically() {
        val target = MatchTarget(sourceUrls = setOf("https://x.test/g/1/"), title = "Sample Book", tags = setOf(CanonicalTag.parse("artist:alice")), coverHash = "abc")
        val weak = MatchCandidate("p", "2", title = "Unrelated")
        val strong = MatchCandidate("p", "1", sourceUrl = "https://x.test/g/1", title = "Sample Book", tags = setOf(CanonicalTag.parse("artist:alice")), coverHash = "abc")
        val result = MetadataMatchEngine.rank(target, listOf(weak, strong))
        assertEquals("1", result.first().candidate.sourceId)
        assertTrue(result.first().evidence.contains(MatchEvidence.URL))
        assertTrue(result.first().autoApplicable)
        assertFalse(result.last().autoApplicable)
    }

    @Test fun deduplicatesByProviderAndSourceIdentity() {
        val target = MatchTarget(title = "same")
        val a = MatchCandidate("provider", "id", title = "same")
        val b = MatchCandidate("provider", "id", title = "same other")
        assertEquals(1, MetadataMatchEngine.rank(target, listOf(a, b)).size)
    }
}
