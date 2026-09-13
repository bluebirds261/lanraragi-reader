package com.lanraragi.reader.data.metadata.providers

import com.lanraragi.reader.domain.metadata.TagSource
import java.net.URI

/** Offline nHentai candidate parser. A title search is a low-confidence suggestion only. */
object NHentaiMetadataProvider : MetadataCandidateProvider {
    override val providerId: String = "nhentai"

    override fun findCandidates(input: MetadataCandidateInput): List<MetadataCandidate> {
        val candidates = buildList {
            input.explicitSourceValues().forEach { value ->
                parseExact(value)?.let { exact -> add(exact.toCandidate(input.observedAt)) }
            }

            listOfNotNull(input.fileName, input.archiveTitle).forEach { value ->
                filenameId(value)?.let { id ->
                    add(
                        candidateFor(
                            id = id,
                            confidence = FILENAME_IDENTIFIER_CONFIDENCE,
                            match = MetadataCandidateMatch.FILENAME_IDENTIFIER,
                            observedAt = input.observedAt,
                        ),
                    )
                }
            }

            normalizeCandidateTitle(input.archiveTitle ?: input.fileName)?.let { title ->
                add(
                    MetadataCandidate(
                        providerId = providerId,
                        sourceId = null,
                        confidence = TITLE_SEARCH_CONFIDENCE,
                        match = MetadataCandidateMatch.TITLE_SEARCH,
                        normalizedTitle = title,
                    ),
                )
            }
        }
        return candidates.stableDistinct()
    }

    /** Accepts only an exact nHentai gallery URL or a standalone positive numeric gallery id. */
    fun parseExact(value: String): NHentaiGallery? {
        val trimmed = value.trim()
        NUMERIC_ID.matchEntire(trimmed)?.let { return NHentaiGallery(it.value, explicitUrl = false) }

        val url = trustedUri(trimmed) ?: return null
        if (url.host.lowercase() !in SUPPORTED_HOSTS || url.query != null || url.fragment != null) return null
        val match = GALLERY_PATH.matchEntire(url.rawPath ?: return null) ?: return null
        return NHentaiGallery(match.groupValues[1], explicitUrl = true)
    }

    private fun filenameId(value: String): String? = BRACED_FILE_ID.find(value)?.groupValues?.get(1)

    private fun NHentaiGallery.toCandidate(observedAt: Long): MetadataCandidate = candidateFor(
        id = id,
        confidence = if (explicitUrl) EXPLICIT_URL_CONFIDENCE else EXPLICIT_IDENTIFIER_CONFIDENCE,
        match = if (explicitUrl) MetadataCandidateMatch.EXPLICIT_SOURCE_URL else MetadataCandidateMatch.EXPLICIT_IDENTIFIER,
        observedAt = observedAt,
    )

    private fun candidateFor(
        id: String,
        confidence: Float,
        match: MetadataCandidateMatch,
        observedAt: Long,
    ): MetadataCandidate {
        val sourceUrl = "https://nhentai.net/g/$id"
        return MetadataCandidate(
            providerId = providerId,
            sourceId = id,
            sourceUrl = sourceUrl,
            confidence = confidence,
            match = match,
            patch = buildCandidatePatch(
                providerId = providerId,
                sourceId = id,
                sourceUrl = sourceUrl,
                confidence = confidence,
                observedAt = observedAt,
                tagSource = TagSource.NHENTAI,
            ),
        )
    }

    private fun trustedUri(value: String): URI? {
        if (value.contains('\\')) return null
        val urlValue = if (value.startsWith("nhentai.net/", ignoreCase = true) ||
            value.startsWith("www.nhentai.net/", ignoreCase = true)
        ) {
            "https://$value"
        } else {
            value
        }
        return runCatching { URI(urlValue) }
            .getOrNull()
            ?.takeIf { uri ->
                uri.isAbsolute &&
                    uri.scheme.lowercase() in setOf("http", "https") &&
                    uri.userInfo == null &&
                    uri.port == -1 &&
                    !uri.host.isNullOrBlank()
            }
    }

    private fun List<MetadataCandidate>.stableDistinct(): List<MetadataCandidate> = asSequence()
        .sortedWith(
            compareByDescending<MetadataCandidate> { it.confidence }
                .thenBy { it.match.order }
                .thenBy { it.sourceUrl.orEmpty() }
                .thenBy { it.sourceId.orEmpty() },
        )
        .distinctBy { candidate -> candidate.sourceUrl ?: "${candidate.match}:${candidate.sourceId}:${candidate.normalizedTitle}" }
        .toList()

    /**
     * nHentai 画廊的精确身份。同 [EHentaiMetadataProvider.EHentaiGallery]：
     * 构造函数是 internal，因此不能用 data class（会生成公开的 `copy()` 旁路，
     * Kotlin 2.5 起属于编译错误）。
     */
    class NHentaiGallery internal constructor(
        val id: String,
        internal val explicitUrl: Boolean,
    ) {
        val canonicalUrl: String get() = "https://nhentai.net/g/$id"
    }

    private const val EXPLICIT_URL_CONFIDENCE = 0.99f
    private const val EXPLICIT_IDENTIFIER_CONFIDENCE = 0.97f
    private const val FILENAME_IDENTIFIER_CONFIDENCE = 0.92f
    private const val TITLE_SEARCH_CONFIDENCE = 0.55f
    private val SUPPORTED_HOSTS = setOf("nhentai.net", "www.nhentai.net")
    private val GALLERY_PATH = Regex("^/g/([1-9][0-9]{0,8})/?$")
    private val NUMERIC_ID = Regex("[1-9][0-9]{0,8}")
    // 末尾的 `}` 必须转义：它闭合的是 `\{`，不是量词。Android 的 java.util.regex 实现
    // （与 OpenJDK 不同）会因未转义的字面 `}` 抛 PatternSyntaxException: Syntax error in
    // regexp pattern，而这个对象在 <clinit> 里编译正则，一旦抛错就是「打开元数据工作台即崩」。
    private val BRACED_FILE_ID = Regex("(?:^|[\\s_-])\\{([1-9][0-9]{0,8})\\}")
}
