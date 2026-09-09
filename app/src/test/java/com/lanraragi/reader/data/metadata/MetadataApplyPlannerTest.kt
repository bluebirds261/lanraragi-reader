package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.model.ArchiveIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataApplyPlannerTest {
    private val provenance = MetadataProvenance(providerId = "ehentai", sourceId = "42")

    @Test
    fun unchangedRemoteBaseProducesFullReplacementPayload() {
        val tag = CanonicalTag.parse("artist:Alice")
        val baseline = MetadataSnapshot(title = "Existing", tags = setOf(tag), revision = "r1")

        val plan = MetadataApplyPlanner.plan(
            baseline = baseline,
            latest = baseline,
            patch = MetadataPatch(summary = MetadataField("Summary", provenance)),
            target = ArchiveIdentity.Remote("abc"),
        )

        assertTrue(plan.canPut)
        assertFalse(plan.baseChanged)
        assertEquals("Existing", plan.putPayload?.title)
        assertEquals("Summary", plan.putPayload?.summary)
        assertEquals(setOf(tag), plan.putPayload?.tags)
        assertEquals(MetadataFieldName.SUMMARY, plan.diff.fields.single().field)
    }

    @Test
    fun summaryOnlyUpdateRetainsExplicitEmptyTagsArgument() {
        val snapshot = MetadataSnapshot(revision = "r1")

        val plan = MetadataApplyPlanner.plan(
            baseline = snapshot,
            latest = snapshot,
            patch = MetadataPatch(summary = MetadataField("Summary", provenance)),
            target = ArchiveIdentity.Remote("abc"),
        )

        assertTrue(plan.canPut)
        assertNull(plan.putPayload?.title)
        assertEquals("Summary", plan.putPayload?.summary)
        // The gateway serializes this as tags="", rather than omitting tags.
        assertTrue(plan.putPayload?.tags?.isEmpty() == true)
    }

    @Test
    fun changedRevisionRebasesOnLatestAndRequiresReview() {
        val baseline = MetadataSnapshot(title = null, revision = "r1")
        val latest = MetadataSnapshot(title = "Edited elsewhere", revision = "r2")

        val plan = MetadataApplyPlanner.plan(
            baseline = baseline,
            latest = latest,
            patch = MetadataPatch(title = MetadataField("Scraped", provenance)),
            policy = MetadataApplyPolicy(
                mergePolicy = MetadataMergePolicy(overwriteExistingFields = true),
            ),
            target = ArchiveIdentity.Remote("abc"),
        )

        assertTrue(plan.baseChanged)
        assertTrue(plan.version.revisionChanged)
        assertEquals("Edited elsewhere", plan.diff.fields.single().before)
        assertEquals("Scraped", plan.mergedSnapshot.title)
        assertTrue(plan.requiresLatestReview)
        assertNull(plan.putPayload)

        val approved = MetadataApplyPlanner.plan(
            baseline = baseline,
            latest = latest,
            patch = MetadataPatch(title = MetadataField("Scraped", provenance)),
            policy = MetadataApplyPolicy(
                mergePolicy = MetadataMergePolicy(overwriteExistingFields = true),
                approveRebasedSnapshot = true,
            ),
            target = ArchiveIdentity.Remote("abc"),
        )
        assertNotNull(approved.putPayload)
    }

    @Test
    fun changedContentWithoutRevisionIsDetectedByFingerprint() {
        val baseline = MetadataSnapshot(summary = "old")
        val latest = MetadataSnapshot(summary = "new")

        val comparison = MetadataDiff.compareVersions(baseline, latest)

        assertFalse(comparison.revisionChanged)
        assertTrue(comparison.fingerprintChanged)
        assertTrue(comparison.baseChanged)
    }

    @Test
    fun changedUserOwnershipIsDetectedByFingerprint() {
        val baseline = MetadataSnapshot(title = "same")
        val latest = baseline.copy(userOverrides = setOf(MetadataFieldName.TITLE))

        val comparison = MetadataDiff.compareVersions(baseline, latest)

        assertTrue(comparison.fingerprintChanged)
        assertTrue(comparison.baseChanged)
    }

    @Test
    fun sourceUrlIsEncodedAsSourceTagWithoutDroppingOtherProviders() {
        val ehSource = CanonicalTag.parse("source:https://e-hentai.org/g/1/token")
        val nhSource = CanonicalTag.parse("source:https://nhentai.net/g/2")
        val baseline = MetadataSnapshot(
            sourceUrl = "https://e-hentai.org/g/1/token",
            tags = setOf(ehSource, nhSource),
            revision = "r1",
        )
        val plan = MetadataApplyPlanner.plan(
            baseline = baseline,
            latest = baseline,
            patch = MetadataPatch(
                sourceUrl = MetadataField("https://e-hentai.org/g/3/new", provenance),
            ),
            policy = MetadataApplyPolicy(
                mergePolicy = MetadataMergePolicy(overwriteExistingFields = true),
            ),
            target = ArchiveIdentity.Remote("abc"),
        )

        val putTagKeys = plan.putPayload!!.tags.map(CanonicalTag::full).toSet()
        assertFalse(ehSource.full in putTagKeys)
        assertTrue(nhSource.full in putTagKeys)
        assertTrue("source:https://e-hentai.org/g/3/new" in putTagKeys)
    }

    @Test
    fun unresolvedPatchBlocksPartialPutByDefault() {
        val current = MetadataSnapshot(title = "User title", revision = "r1")
        val patch = MetadataPatch(
            title = MetadataField("Scraped title", provenance),
            summary = MetadataField("New summary", provenance),
        )

        val blocked = MetadataApplyPlanner.plan(
            baseline = current,
            latest = current,
            patch = patch,
            target = ArchiveIdentity.Remote("abc"),
        )

        assertEquals("New summary", blocked.mergedSnapshot.summary)
        assertEquals("Scraped title", blocked.unappliedPatch.title?.value)
        assertTrue(blocked.diff.conflicts.isNotEmpty())
        assertNull(blocked.putPayload)

        val approvedPartial = MetadataApplyPlanner.plan(
            baseline = current,
            latest = current,
            patch = patch,
            policy = MetadataApplyPolicy(allowPartialApply = true),
            target = ArchiveIdentity.Remote("abc"),
        )
        assertNotNull(approvedPartial.putPayload)
    }

    @Test
    fun localTargetNeverCreatesServerUploadPlan() {
        val snapshot = MetadataSnapshot(revision = "local-r1")
        val plan = MetadataApplyPlanner.plan(
            baseline = snapshot,
            latest = snapshot,
            patch = MetadataPatch(title = MetadataField("Local title", provenance)),
            target = ArchiveIdentity.LocalSaf("content://library/archive.cbz"),
        )

        assertEquals("Local title", plan.mergedSnapshot.title)
        assertEquals(1, plan.diff.fields.size)
        assertFalse(plan.canPut)
        assertNull(plan.putPayload)
        assertTrue(MetadataApplyBlockReason.TARGET_IS_NOT_REMOTE in plan.blockReasons)
    }

    @Test
    fun noOpPatchDoesNotIssuePut() {
        val snapshot = MetadataSnapshot(title = "Same", revision = "r1")
        val plan = MetadataApplyPlanner.plan(
            baseline = snapshot,
            latest = snapshot,
            patch = MetadataPatch(title = MetadataField("Same", provenance)),
            target = ArchiveIdentity.Remote("abc"),
        )

        assertTrue(plan.diff.noops.isNotEmpty())
        assertTrue(MetadataApplyBlockReason.NO_EFFECTIVE_CHANGES in plan.blockReasons)
        assertNull(plan.putPayload)
    }
}
