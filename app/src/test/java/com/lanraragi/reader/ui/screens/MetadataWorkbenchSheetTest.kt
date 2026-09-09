package com.lanraragi.reader.ui.screens

import com.lanraragi.reader.data.metadata.MetadataFieldName
import com.lanraragi.reader.data.metadata.MetadataPatchMerger
import com.lanraragi.reader.data.metadata.MetadataSnapshot
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataWorkbenchSheetTest {
    @Test
    fun manualSummaryBlankIsAnExplicitUserOwnedClear() {
        val baseline = MetadataSnapshot(summary = "Server summary")

        val result = buildManualMetadataPatch(
            baseline = baseline,
            submission = MetadataEditSubmission(summary = "   ", title = null, tags = null),
            timestamp = 123L,
        )

        assertEquals("", result.patch.summary?.value)
        assertEquals("user", result.patch.summary?.provenance?.providerId)
        assertEquals(123L, result.patch.summary?.provenance?.fetchedAt)
        assertTrue(MetadataFieldName.SUMMARY in result.baseline.userOverrides)
        assertEquals("", MetadataPatchMerger.merge(result.baseline, result.patch, userMetadataApplyPolicy().mergePolicy).snapshot.summary)
    }

    @Test
    fun manualTagEditUsesCanonicalDeltasAndRecordsUserOwnershipAndProvenance() {
        val retained = CanonicalTag.parse("artist:Alice", source = TagSource.LANRARAGI)
        val removed = CanonicalTag.parse("group:Old", source = TagSource.LANRARAGI)
        val baseline = MetadataSnapshot(tags = linkedSetOf(retained, removed))

        val result = buildManualMetadataPatch(
            baseline = baseline,
            submission = MetadataEditSubmission(
                title = null,
                tags = " artist:alice , language:English , language: english ",
                summary = null,
            ),
            timestamp = 456L,
        )

        val added = result.patch.addTags.single()
        val removedTag = result.patch.removeTags.single()
        assertEquals("language:english", added.full)
        assertEquals(TagSource.USER, added.source)
        assertEquals("group:old", removedTag.full)
        assertTrue(MetadataFieldName.TAGS in result.baseline.userOverrides)
        assertEquals(setOf(added.full, removedTag.full), result.patch.tagProvenance.keys)
        assertTrue(result.patch.tagProvenance.values.all { it.providerId == "user" && it.fetchedAt == 456L })

        val merged = MetadataPatchMerger.merge(result.baseline, result.patch, userMetadataApplyPolicy().mergePolicy)
        assertEquals(setOf(retained.full, added.full), merged.snapshot.tags.map { it.full }.toSet())
        assertEquals(result.patch.tagProvenance.getValue(added.full), merged.snapshot.provenance["tag:${added.full}"])
        assertFalse("tag:${removedTag.full}" in merged.snapshot.provenance)
    }
}
