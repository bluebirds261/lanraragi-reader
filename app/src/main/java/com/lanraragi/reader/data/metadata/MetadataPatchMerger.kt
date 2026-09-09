package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource

/** Fields which may be protected by an explicit user edit. */
enum class MetadataFieldName {
    TITLE,
    SUMMARY,
    SOURCE_URL,
    TAGS,
}

/**
 * The latest metadata read from the server (or the local metadata store).
 *
 * [userOverrides] is deliberately explicit: a non-empty value alone is not
 * enough to infer that a user owns a field. Tag ownership can additionally be
 * represented by [CanonicalTag.source] == USER.
 */
data class MetadataSnapshot(
    val title: String? = null,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val tags: Set<CanonicalTag> = emptySet(),
    val userOverrides: Set<MetadataFieldName> = emptySet(),
    val provenance: Map<String, MetadataProvenance> = emptyMap(),
    val revision: String? = null,
) {
    /** Canonical tags are the identity; raw spelling and translation are not. */
    val canonicalTags: Map<String, CanonicalTag>
        get() = tags.associateBy { it.full }
}

enum class MetadataChangeKind {
    FIELD_APPLIED,
    TAG_ADDED,
    TAG_REMOVED,
    NOOP,
    CONFLICT,
}

enum class MetadataConflictReason {
    USER_OVERRIDE,
    EXISTING_VALUE,
    USER_TAG,
    PATCH_TAG_CONFLICT,
}

/** A human-readable, machine-checkable explanation for every decision. */
data class MetadataChange(
    val key: String,
    val kind: MetadataChangeKind,
    val before: Any? = null,
    val after: Any? = null,
    val reason: MetadataConflictReason? = null,
    val explanation: String? = null,
    val provenance: MetadataProvenance? = null,
)

/**
 * Controls the conservative defaults used when a patch was produced from an
 * older snapshot. Non-blank fields are never silently overwritten by default.
 */
data class MetadataMergePolicy(
    val overwriteExistingFields: Boolean = false,
    val preserveUserOverrides: Boolean = true,
    val protectUserTags: Boolean = true,
)

data class MetadataMergeResult(
    val snapshot: MetadataSnapshot,
    val changes: List<MetadataChange>,
    val unappliedPatch: MetadataPatch,
    val provenance: Map<String, MetadataProvenance>,
) {
    /** Alias useful to callers that name the result of a merge `merged`. */
    val merged: MetadataSnapshot get() = snapshot

    val hasUnappliedPatch: Boolean
        get() = unappliedPatch != MetadataPatch()
}

/** Pure, deterministic merge logic. No repository, API, or Room dependency. */
object MetadataPatchMerger {
    fun merge(
        latest: MetadataSnapshot,
        patch: MetadataPatch,
        policy: MetadataMergePolicy = MetadataMergePolicy(),
    ): MetadataMergeResult {
        var title = latest.title
        var summary = latest.summary
        var sourceUrl = latest.sourceUrl
        val tagsByKey = latest.canonicalTags.toMutableMap()
        val provenance = latest.provenance.toMutableMap()
        val changes = mutableListOf<MetadataChange>()

        var unappliedTitle = patch.title
        var unappliedSummary = patch.summary
        var unappliedSourceUrl = patch.sourceUrl

        fun fieldIsProtected(field: MetadataFieldName): Boolean =
            policy.preserveUserOverrides && field in latest.userOverrides

        fun mergeField(
            field: MetadataFieldName,
            key: String,
            current: String?,
            candidate: com.lanraragi.reader.domain.metadata.MetadataField<String>?,
            allowBlank: Boolean = false,
            assign: (String?) -> Unit,
            clearUnapplied: () -> Unit,
        ) {
            if (candidate == null) return
            val value = candidate.value.trim()
            if (value.isEmpty() && !allowBlank) {
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    before = current,
                    after = value,
                    reason = MetadataConflictReason.EXISTING_VALUE,
                    explanation = "空值 patch 未应用",
                    provenance = candidate.provenance,
                )
                return
            }
            if (current == value) {
                clearUnapplied()
                changes += MetadataChange(key, MetadataChangeKind.NOOP, current, current, provenance = candidate.provenance)
                return
            }
            if (fieldIsProtected(field)) {
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    before = current,
                    after = value,
                    reason = MetadataConflictReason.USER_OVERRIDE,
                    explanation = "当前字段由用户覆盖，保留最新快照值",
                    provenance = candidate.provenance,
                )
                return
            }
            if (!current.isNullOrBlank() && !policy.overwriteExistingFields) {
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    before = current,
                    after = value,
                    reason = MetadataConflictReason.EXISTING_VALUE,
                    explanation = "当前字段已有值，默认不静默覆盖",
                    provenance = candidate.provenance,
                )
                return
            }
            assign(value)
            clearUnapplied()
            provenance[key] = candidate.provenance
            changes += MetadataChange(
                key = key,
                kind = MetadataChangeKind.FIELD_APPLIED,
                before = current,
                after = value,
                provenance = candidate.provenance,
            )
        }

        mergeField(MetadataFieldName.TITLE, FIELD_TITLE, title, patch.title, assign = { title = it }, clearUnapplied = { unappliedTitle = null })
        mergeField(MetadataFieldName.SUMMARY, FIELD_SUMMARY, summary, patch.summary, allowBlank = true, assign = { summary = it }, clearUnapplied = { unappliedSummary = null })
        mergeField(MetadataFieldName.SOURCE_URL, FIELD_SOURCE_URL, sourceUrl, patch.sourceUrl, allowBlank = true, assign = { sourceUrl = it }, clearUnapplied = { unappliedSourceUrl = null })

        val addKeys = patch.addTags.mapTo(mutableSetOf()) { it.full }
        val removeKeys = patch.removeTags.mapTo(mutableSetOf()) { it.full }
        val internalTagConflict = addKeys.intersect(removeKeys)
        if (internalTagConflict.isNotEmpty()) {
            // MetadataPatch currently rejects this state, but retaining an
            // explicit decision keeps this merger safe if construction changes.
            internalTagConflict.forEach { key ->
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    reason = MetadataConflictReason.PATCH_TAG_CONFLICT,
                    explanation = "同一 canonical tag 同时 add/remove，未应用",
                )
            }
        }

        val unappliedAdds = linkedSetOf<CanonicalTag>()
        val tagsProtected = policy.preserveUserOverrides && MetadataFieldName.TAGS in latest.userOverrides
        patch.addTags.forEach { tag ->
            val key = tag.full
            if (key in internalTagConflict) {
                unappliedAdds += tag
                return@forEach
            }
            if (tagsProtected) {
                unappliedAdds += tag
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    reason = MetadataConflictReason.USER_OVERRIDE,
                    explanation = "当前标签集合由用户覆盖，未自动添加",
                    provenance = patch.tagProvenance[key],
                )
                return@forEach
            }
            val existing = tagsByKey[key]
            if (existing != null) {
                changes += MetadataChange(key, MetadataChangeKind.NOOP, existing, existing, provenance = patch.tagProvenance[key])
                return@forEach
            }
            tagsByKey[key] = tag
            patch.tagProvenance[key]?.let { provenance[keyForTag(key)] = it }
            changes += MetadataChange(key, MetadataChangeKind.TAG_ADDED, null, tag, provenance = patch.tagProvenance[key])
        }

        val unappliedRemoves = linkedSetOf<CanonicalTag>()
        patch.removeTags.forEach { tag ->
            val key = tag.full
            if (key in internalTagConflict) {
                unappliedRemoves += tag
                return@forEach
            }
            if (tagsProtected) {
                unappliedRemoves += tag
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    reason = MetadataConflictReason.USER_OVERRIDE,
                    explanation = "当前标签集合由用户覆盖，未自动删除",
                    provenance = patch.tagProvenance[key],
                )
                return@forEach
            }
            val existing = tagsByKey[key]
            if (existing == null) {
                changes += MetadataChange(key, MetadataChangeKind.NOOP, null, null, provenance = patch.tagProvenance[key])
                return@forEach
            }
            val userOwned = existing.source == TagSource.USER
            if (policy.protectUserTags && userOwned) {
                unappliedRemoves += tag
                changes += MetadataChange(
                    key = key,
                    kind = MetadataChangeKind.CONFLICT,
                    before = existing,
                    after = null,
                    reason = MetadataConflictReason.USER_TAG,
                    explanation = "用户标签不可由刮削 patch 自动删除",
                    provenance = patch.tagProvenance[key],
                )
                return@forEach
            }
            tagsByKey.remove(key)
            provenance.remove(keyForTag(key))
            changes += MetadataChange(key, MetadataChangeKind.TAG_REMOVED, existing, null, provenance = patch.tagProvenance[key])
        }

        val unappliedTagKeys = buildSet {
            addAll(unappliedAdds.map { it.full })
            addAll(unappliedRemoves.map { it.full })
        }
        val unapplied = MetadataPatch(
            title = unappliedTitle,
            summary = unappliedSummary,
            sourceUrl = unappliedSourceUrl,
            addTags = unappliedAdds,
            removeTags = unappliedRemoves,
            tagProvenance = patch.tagProvenance.filterKeys { it in unappliedTagKeys },
        )
        val merged = latest.copy(
            title = title,
            summary = summary,
            sourceUrl = sourceUrl,
            tags = tagsByKey.values.toSet(),
            provenance = provenance.toMap(),
        )
        return MetadataMergeResult(merged, changes.toList(), unapplied, provenance.toMap())
    }

    fun apply(
        latest: MetadataSnapshot,
        patch: MetadataPatch,
        policy: MetadataMergePolicy = MetadataMergePolicy(),
    ): MetadataMergeResult = merge(latest, patch, policy)

    fun merge(
        latest: MetadataSnapshot,
        patch: MetadataPatch,
        userOverrides: Set<MetadataFieldName>,
        policy: MetadataMergePolicy = MetadataMergePolicy(),
    ): MetadataMergeResult = merge(latest.copy(userOverrides = userOverrides), patch, policy)

    private const val FIELD_TITLE = "title"
    private const val FIELD_SUMMARY = "summary"
    private const val FIELD_SOURCE_URL = "sourceUrl"
    private fun keyForTag(canonical: String): String = "tag:$canonical"
}
