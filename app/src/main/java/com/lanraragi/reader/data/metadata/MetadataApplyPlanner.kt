package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.model.ArchiveIdentity

data class MetadataApplyPolicy(
    val mergePolicy: MetadataMergePolicy = MetadataMergePolicy(),
    val approveRebasedSnapshot: Boolean = false,
    val allowPartialApply: Boolean = false,
)

enum class MetadataApplyBlockReason {
    TARGET_IS_NOT_REMOTE,
    BASE_CHANGED_REVIEW_REQUIRED,
    UNAPPLIED_PATCH_REVIEW_REQUIRED,
    NO_EFFECTIVE_CHANGES,
}

/**
 * Complete replacement payload required by LANraragi's metadata PUT.
 *
 * [tags] is intentionally a Set rather than nullable: the gateway always
 * serializes it as the `tags` request argument, including when empty. This
 * keeps a summary-only update representable when title is also null.
 */
data class MetadataPutPayload(
    val arcid: String,
    val title: String?,
    val summary: String?,
    val tags: Set<CanonicalTag>,
)

data class MetadataApplyPlan(
    val target: ArchiveIdentity,
    val version: MetadataVersionComparison,
    val mergedSnapshot: MetadataSnapshot,
    val diff: MetadataReviewDiff,
    val unappliedPatch: MetadataPatch,
    val blockReasons: Set<MetadataApplyBlockReason>,
    val putPayload: MetadataPutPayload?,
) {
    val baseChanged: Boolean
        get() = version.baseChanged

    val canPut: Boolean
        get() = putPayload != null

    val requiresLatestReview: Boolean
        get() = MetadataApplyBlockReason.BASE_CHANGED_REVIEW_REQUIRED in blockReasons
}

/** Pure planning step. Network errors, including HTTP 423, remain repository concerns. */
object MetadataApplyPlanner {
    fun plan(
        baseline: MetadataSnapshot,
        latest: MetadataSnapshot,
        patch: MetadataPatch,
        policy: MetadataApplyPolicy = MetadataApplyPolicy(),
        target: ArchiveIdentity,
    ): MetadataApplyPlan {
        val version = MetadataDiff.compareVersions(baseline, latest)
        // Always merge against the latest read, never against the scrape baseline.
        val merge = MetadataPatchMerger.merge(latest, patch, policy.mergePolicy)
        val diff = MetadataDiff.review(merge)
        val blockReasons = linkedSetOf<MetadataApplyBlockReason>()
        val remote = target as? ArchiveIdentity.Remote

        if (remote == null) {
            blockReasons += MetadataApplyBlockReason.TARGET_IS_NOT_REMOTE
        }
        if (version.baseChanged && !policy.approveRebasedSnapshot) {
            blockReasons += MetadataApplyBlockReason.BASE_CHANGED_REVIEW_REQUIRED
        }
        if (merge.hasUnappliedPatch && !policy.allowPartialApply) {
            blockReasons += MetadataApplyBlockReason.UNAPPLIED_PATCH_REVIEW_REQUIRED
        }
        if (!diff.hasEffectiveChanges) {
            blockReasons += MetadataApplyBlockReason.NO_EFFECTIVE_CHANGES
        }

        val payload = if (remote != null && blockReasons.isEmpty()) {
            MetadataPutPayload(
                arcid = remote.arcid,
                title = merge.snapshot.title,
                summary = merge.snapshot.summary,
                tags = tagsForPut(latest, merge.snapshot, diff),
            )
        } else {
            null
        }

        return MetadataApplyPlan(
            target = target,
            version = version,
            mergedSnapshot = merge.snapshot,
            diff = diff,
            unappliedPatch = merge.unappliedPatch,
            blockReasons = blockReasons,
            putPayload = payload,
        )
    }

    /** LANraragi stores source URLs as `source:` tags, not a PUT field. */
    private fun tagsForPut(
        latest: MetadataSnapshot,
        merged: MetadataSnapshot,
        diff: MetadataReviewDiff,
    ): Set<CanonicalTag> {
        val tagsByKey = merged.canonicalTags.toMutableMap()
        val sourceChanged = diff.fields.any { it.field == MetadataFieldName.SOURCE_URL }
        if (sourceChanged) {
            latest.sourceUrl
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(::sourceTag)
                ?.let { tagsByKey.remove(it.full) }
        }
        merged.sourceUrl
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::sourceTag)
            ?.let { tagsByKey[it.full] = it }
        return tagsByKey.values.toSet()
    }

    private fun sourceTag(url: String): CanonicalTag = CanonicalTag.parse("source:$url")
}
