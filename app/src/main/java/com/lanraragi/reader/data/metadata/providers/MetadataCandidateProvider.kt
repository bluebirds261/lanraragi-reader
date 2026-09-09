package com.lanraragi.reader.data.metadata.providers

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import java.text.Normalizer

/**
 * A deterministic match suggestion. Providers never fetch a gallery or apply
 * [patch]; the caller must present it for review and use MetadataRepository to
 * perform the eventual conflict-safe write.
 */
data class MetadataCandidate(
    val providerId: String,
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val confidence: Float,
    val match: MetadataCandidateMatch,
    val normalizedTitle: String? = null,
    val patch: MetadataPatch? = null,
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(sourceId != null || normalizedTitle != null) {
            "a candidate must identify a source or normalized title"
        }
        require(confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }

    /** Even exact URL matches need a user review before any write-back. */
    val requiresUserConfirmation: Boolean = true

    val isLowConfidence: Boolean
        get() = confidence < MetadataCandidatePolicy.LOW_CONFIDENCE_CUTOFF
}

enum class MetadataCandidateMatch(val order: Int) {
    EXPLICIT_SOURCE_URL(0),
    EXPLICIT_IDENTIFIER(1),
    FILENAME_IDENTIFIER(2),
    TITLE_IDENTIFIER(3),
    TITLE_SEARCH(4),
}

/** Inputs intentionally contain only local archive metadata, never credentials or network clients. */
data class MetadataCandidateInput(
    val sourceUrls: Collection<String> = emptyList(),
    val existingTags: Collection<CanonicalTag> = emptyList(),
    val archiveTitle: String? = null,
    val fileName: String? = null,
    val observedAt: Long = System.currentTimeMillis(),
)

interface MetadataCandidateProvider {
    val providerId: String

    fun findCandidates(input: MetadataCandidateInput): List<MetadataCandidate>
}

/** Shared gates prevent an uncertain title search result from becoming a write-back patch. */
object MetadataCandidatePolicy {
    const val LOW_CONFIDENCE_CUTOFF: Float = 0.80f
    const val PATCH_CONFIDENCE_CUTOFF: Float = 0.85f
}

/** Registry kept deliberately local and offline; network/provider enablement belongs to the integration layer. */
object NativeMetadataProviders {
    val all: List<MetadataCandidateProvider> = listOf(
        EHentaiMetadataProvider,
        NHentaiMetadataProvider,
    )
}

internal fun MetadataCandidateInput.explicitSourceValues(): Sequence<String> = sequence {
    yieldAll(sourceUrls.asSequence())
    for (value in existingTags
        .asSequence()
        .filter { it.identity.namespace == "source" }
        .map { it.raw.substringAfter(':', missingDelimiterValue = "") }
        .filter(String::isNotBlank)) {
        yield(value)
    }
}

internal fun buildCandidatePatch(
    providerId: String,
    sourceId: String,
    sourceUrl: String?,
    confidence: Float,
    observedAt: Long,
    tagSource: TagSource,
): MetadataPatch? {
    if (sourceUrl == null || confidence < MetadataCandidatePolicy.PATCH_CONFIDENCE_CUTOFF) {
        return null
    }
    val provenance = MetadataProvenance(
        providerId = providerId,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        confidence = confidence,
        fetchedAt = observedAt,
    )
    val sourceTag = CanonicalTag.parse(
        raw = "source:$sourceUrl",
        source = tagSource,
        confidence = confidence,
    )
    return MetadataPatch(
        sourceUrl = MetadataField(sourceUrl, provenance),
        addTags = setOf(sourceTag),
        tagProvenance = mapOf(sourceTag.full to provenance),
    )
}

internal fun normalizeCandidateTitle(value: String?): String? {
    val normalized = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.replace(ARCHIVE_SUFFIX, "")
        ?.replace(LEADING_GALLERY_IDENTIFIER, "")
        ?.trim()
        .orEmpty()
    return normalized.takeIf(String::isNotBlank)
}

private val ARCHIVE_SUFFIX = Regex("(?i)\\.(?:cbz|cbr|cb7|zip|rar|7z|pdf)$")
private val LEADING_GALLERY_IDENTIFIER = Regex("^(?:\\{[1-9][0-9]*}|\\[[1-9][0-9]*])\\s*")
