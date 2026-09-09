package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class MetadataTagOperation {
    ADD,
    REMOVE,
}

data class MetadataFieldDiff(
    val field: MetadataFieldName,
    val before: String?,
    val after: String?,
    val provenance: MetadataProvenance? = null,
)

data class MetadataTagDiff(
    val operation: MetadataTagOperation,
    val tag: CanonicalTag,
    val provenance: MetadataProvenance? = null,
)

/** Decisions suitable for a metadata review screen. */
data class MetadataReviewDiff(
    val fields: List<MetadataFieldDiff>,
    val tags: List<MetadataTagDiff>,
    val conflicts: List<MetadataChange>,
    val noops: List<MetadataChange>,
) {
    val hasEffectiveChanges: Boolean
        get() = fields.isNotEmpty() || tags.isNotEmpty()
}

data class MetadataSnapshotVersion(
    val revision: String?,
    val fingerprint: String,
)

data class MetadataVersionComparison(
    val baseline: MetadataSnapshotVersion,
    val latest: MetadataSnapshotVersion,
    val revisionChanged: Boolean,
    val fingerprintChanged: Boolean,
) {
    val baseChanged: Boolean
        get() = revisionChanged || fingerprintChanged
}

object MetadataDiff {
    fun review(result: MetadataMergeResult): MetadataReviewDiff {
        val fields = mutableListOf<MetadataFieldDiff>()
        val tags = mutableListOf<MetadataTagDiff>()
        val conflicts = mutableListOf<MetadataChange>()
        val noops = mutableListOf<MetadataChange>()

        result.changes.forEach { change ->
            when (change.kind) {
                MetadataChangeKind.FIELD_APPLIED -> fieldName(change.key)?.let { field ->
                    fields += MetadataFieldDiff(
                        field = field,
                        before = change.before as? String,
                        after = change.after as? String,
                        provenance = change.provenance,
                    )
                }

                MetadataChangeKind.TAG_ADDED -> (change.after as? CanonicalTag)?.let { tag ->
                    tags += MetadataTagDiff(MetadataTagOperation.ADD, tag, change.provenance)
                }

                MetadataChangeKind.TAG_REMOVED -> (change.before as? CanonicalTag)?.let { tag ->
                    tags += MetadataTagDiff(MetadataTagOperation.REMOVE, tag, change.provenance)
                }

                MetadataChangeKind.CONFLICT -> conflicts += change
                MetadataChangeKind.NOOP -> noops += change
            }
        }

        return MetadataReviewDiff(
            fields = fields,
            tags = tags,
            conflicts = conflicts,
            noops = noops,
        )
    }

    fun compareVersions(
        baseline: MetadataSnapshot,
        latest: MetadataSnapshot,
    ): MetadataVersionComparison {
        val baselineVersion = versionOf(baseline)
        val latestVersion = versionOf(latest)
        return MetadataVersionComparison(
            baseline = baselineVersion,
            latest = latestVersion,
            revisionChanged = baselineVersion.revision != latestVersion.revision,
            fingerprintChanged = baselineVersion.fingerprint != latestVersion.fingerprint,
        )
    }

    fun versionOf(snapshot: MetadataSnapshot): MetadataSnapshotVersion = MetadataSnapshotVersion(
        revision = snapshot.revision,
        fingerprint = fingerprint(snapshot),
    )

    /**
     * Fingerprints values which affect a merge, excluding provenance and the
     * revision itself. Length-prefixing avoids delimiter-dependent collisions.
     */
    fun fingerprint(snapshot: MetadataSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")

        fun update(value: String?) {
            // Distinguish null from an explicitly empty value; both can be
            // meaningful to a metadata PUT and must not share a fingerprint.
            val bytes = value?.toByteArray(StandardCharsets.UTF_8)
            digest.update((if (bytes == null) 0 else 1).toByte())
            val payload = bytes ?: byteArrayOf()
            digest.update(payload.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(payload)
        }

        update(snapshot.title)
        update(snapshot.summary)
        update(snapshot.sourceUrl)
        val canonicalTags = snapshot.tags
            .asSequence()
            .map(CanonicalTag::full)
            .distinct()
            .sorted()
            .toList()
        update("tags:${canonicalTags.size}")
        canonicalTags.forEach(::update)
        val overrides = snapshot.userOverrides
            .asSequence()
            .map(MetadataFieldName::name)
            .sorted()
            .toList()
        update("overrides:${overrides.size}")
        overrides.forEach(::update)

        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun fieldName(key: String): MetadataFieldName? = when (key) {
        "title" -> MetadataFieldName.TITLE
        "summary" -> MetadataFieldName.SUMMARY
        "sourceUrl" -> MetadataFieldName.SOURCE_URL
        else -> null
    }
}
