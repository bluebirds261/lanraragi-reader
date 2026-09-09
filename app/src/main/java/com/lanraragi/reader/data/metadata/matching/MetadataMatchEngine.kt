package com.lanraragi.reader.data.metadata.matching

import com.lanraragi.reader.data.metadata.providers.MetadataCandidate
import com.lanraragi.reader.data.metadata.providers.MetadataCandidatePolicy
import com.lanraragi.reader.domain.metadata.CanonicalTag
import java.text.Normalizer
import kotlin.math.max

enum class MatchEvidence { URL, SOURCE_TAG, FILENAME_ID, TITLE_EXACT, TAG_INTERSECTION, COVER_HASH, FUZZY_TITLE }

data class MatchTarget(
    val sourceUrls: Set<String> = emptySet(),
    val sourceTags: Set<String> = emptySet(),
    val fileName: String? = null,
    val title: String? = null,
    val tags: Set<CanonicalTag> = emptySet(),
    val coverHash: String? = null,
)

data class MatchCandidate(
    val providerId: String,
    val sourceId: String?,
    val sourceUrl: String? = null,
    val fileName: String? = null,
    val title: String? = null,
    val tags: Set<CanonicalTag> = emptySet(),
    val coverHash: String? = null,
    val patch: com.lanraragi.reader.domain.metadata.MetadataPatch? = null,
) {
    init { require(providerId.isNotBlank()) }
    val identity: String get() = "$providerId:${sourceId ?: title.orEmpty()}"
}

data class MatchResult(
    val candidate: MatchCandidate,
    val score: Float,
    val evidence: Set<MatchEvidence>,
    val autoApplicable: Boolean,
) {
    val lowConfidence: Boolean get() = score < MetadataCandidatePolicy.LOW_CONFIDENCE_CUTOFF
}

object MetadataMatchEngine {
    fun rank(target: MatchTarget, candidates: Collection<MatchCandidate>): List<MatchResult> = candidates
        .asSequence()
        .map { score(target, it) }
        .groupBy { it.candidate.identity }
        .values
        .map { it.sortedWith(resultComparator).first() }
        .sortedWith(resultComparator)
        .toList()

    fun fromProviderCandidates(candidates: Collection<MetadataCandidate>): List<MatchCandidate> = candidates.map {
        MatchCandidate(it.providerId, it.sourceId, it.sourceUrl, title = it.normalizedTitle, patch = it.patch)
    }

    private fun score(target: MatchTarget, candidate: MatchCandidate): MatchResult {
        val evidence = linkedSetOf<MatchEvidence>()
        var score = 0f
        if (candidate.sourceUrl != null && target.sourceUrls.any { normalizeUrl(it) == normalizeUrl(candidate.sourceUrl) }) {
            evidence += MatchEvidence.URL; score += 0.45f
        }
        if (candidate.sourceId != null && target.sourceTags.any { normalize(it) == normalize(candidate.sourceId) }) {
            evidence += MatchEvidence.SOURCE_TAG; score += 0.35f
        }
        if (candidate.sourceId != null && target.fileName != null && normalize(target.fileName).contains(normalize(candidate.sourceId))) {
            evidence += MatchEvidence.FILENAME_ID; score += 0.30f
        }
        val targetTitle = normalizeTitle(target.title)
        val candidateTitle = normalizeTitle(candidate.title)
        if (targetTitle != null && targetTitle == candidateTitle) { evidence += MatchEvidence.TITLE_EXACT; score += 0.25f }
        if (target.coverHash != null && candidate.coverHash != null && normalize(target.coverHash) == normalize(candidate.coverHash)) {
            evidence += MatchEvidence.COVER_HASH; score += 0.40f
        }
        val overlap = target.tags.map { it.identity }.toSet().intersect(candidate.tags.map { it.identity }.toSet()).size
        if (overlap > 0) { evidence += MatchEvidence.TAG_INTERSECTION; score += (0.10f * overlap.coerceAtMost(3)) }
        if (targetTitle != null && candidateTitle != null && targetTitle != candidateTitle) {
            val similarity = titleSimilarity(targetTitle, candidateTitle)
            if (similarity >= 0.55f) { evidence += MatchEvidence.FUZZY_TITLE; score += 0.20f * similarity }
        }
        val bounded = score.coerceIn(0f, 1f)
        return MatchResult(candidate, bounded, evidence, autoApplicable = bounded >= MetadataCandidatePolicy.PATCH_CONFIDENCE_CUTOFF && evidence.isNotEmpty())
    }

    private val resultComparator = compareByDescending<MatchResult> { it.score }
        .thenBy { it.candidate.providerId }
        .thenBy { it.candidate.sourceId.orEmpty() }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase()
    private fun normalizeUrl(value: String): String = normalize(value).removeSuffix("/")
    private fun normalizeTitle(value: String?): String? = value?.let(::normalize)?.replace(Regex("\\.(cbz|cbr|zip|rar|7z|pdf)$"), "")?.takeIf(String::isNotBlank)
    private fun titleSimilarity(a: String, b: String): Float {
        val distance = levenshtein(a, b)
        return 1f - distance.toFloat() / max(a.length, b.length).coerceAtLeast(1)
    }
    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1); cur[0] = i + 1
            for (j in b.indices) cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}
