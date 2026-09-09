package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataPatchMergerTest {
    private val scraper = MetadataProvenance(providerId = "ehentai", sourceId = "123")

    @Test
    fun fillsBlankFieldsAndRecordsFieldProvenance() {
        val result = MetadataPatchMerger.merge(
            latest = MetadataSnapshot(title = "", summary = null),
            patch = MetadataPatch(
                title = MetadataField("Scraped title", scraper),
                summary = MetadataField("Summary", scraper),
            ),
        )

        assertEquals("Scraped title", result.snapshot.title)
        assertEquals("Summary", result.snapshot.summary)
        assertTrue(result.unappliedPatch == MetadataPatch())
        assertEquals(scraper, result.provenance["title"])
        assertEquals(MetadataChangeKind.FIELD_APPLIED, result.changes.first { it.key == "title" }.kind)
    }

    @Test
    fun preservesExistingFieldAndExposesPatchForReview() {
        val result = MetadataPatchMerger.merge(
            latest = MetadataSnapshot(title = "User title"),
            patch = MetadataPatch(title = MetadataField("Scraped title", scraper)),
        )

        assertEquals("User title", result.snapshot.title)
        assertEquals("Scraped title", result.unappliedPatch.title?.value)
        val change = result.changes.single()
        assertEquals(MetadataChangeKind.CONFLICT, change.kind)
        assertEquals(MetadataConflictReason.EXISTING_VALUE, change.reason)
        assertTrue(change.explanation!!.contains("不静默覆盖"))
    }

    @Test
    fun userOverrideWinsEvenWhenOverwritePolicyIsEnabled() {
        val result = MetadataPatchMerger.merge(
            latest = MetadataSnapshot(
                title = "User title",
                userOverrides = setOf(MetadataFieldName.TITLE),
            ),
            patch = MetadataPatch(title = MetadataField("Scraped title", scraper)),
            policy = MetadataMergePolicy(overwriteExistingFields = true),
        )

        assertEquals("User title", result.snapshot.title)
        assertEquals(MetadataConflictReason.USER_OVERRIDE, result.changes.single().reason)
        assertEquals("Scraped title", result.unappliedPatch.title?.value)
    }

    @Test
    fun tagOperationsUseCanonicalIdentityAndProtectUserTags() {
        val userTag = CanonicalTag("artist", "alice", "artist:Alice", source = TagSource.USER)
        val oldTag = CanonicalTag.parse("language:English", TagSource.LANRARAGI)
        val added = CanonicalTag.parse("artist:Bob", TagSource.EHENTAI)
        val result = MetadataPatchMerger.merge(
            latest = MetadataSnapshot(tags = setOf(userTag, oldTag)),
            patch = MetadataPatch(
                addTags = setOf(added),
                removeTags = setOf(userTag, oldTag),
                tagProvenance = mapOf(added.full to scraper, userTag.full to scraper),
            ),
        )

        assertEquals(setOf(userTag.full, added.full), result.snapshot.tags.map { it.full }.toSet())
        assertEquals(setOf(userTag), result.unappliedPatch.removeTags)
        assertFalse(result.unappliedPatch.addTags.contains(added))
        assertEquals(scraper, result.provenance["tag:${added.full}"])
        val conflict = result.changes.first { it.key == userTag.full }
        assertEquals(MetadataConflictReason.USER_TAG, conflict.reason)
    }

    @Test
    fun explicitTagsOverrideProtectsWholeTagCollection() {
        val existing = CanonicalTag.parse("artist:Alice", TagSource.LANRARAGI)
        val added = CanonicalTag.parse("artist:Bob", TagSource.EHENTAI)
        val result = MetadataPatchMerger.merge(
            latest = MetadataSnapshot(
                tags = setOf(existing),
                userOverrides = setOf(MetadataFieldName.TAGS),
            ),
            patch = MetadataPatch(
                addTags = setOf(added),
                removeTags = setOf(existing),
                tagProvenance = mapOf(added.full to scraper, existing.full to scraper),
            ),
        )

        assertEquals(setOf(existing.full), result.snapshot.tags.map { it.full }.toSet())
        assertEquals(setOf(added), result.unappliedPatch.addTags)
        assertEquals(setOf(existing), result.unappliedPatch.removeTags)
        assertEquals(2, result.changes.count { it.reason == MetadataConflictReason.USER_OVERRIDE })
    }
}
