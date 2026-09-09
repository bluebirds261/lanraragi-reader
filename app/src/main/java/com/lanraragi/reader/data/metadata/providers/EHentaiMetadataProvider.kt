package com.lanraragi.reader.data.metadata.providers

import com.lanraragi.reader.domain.metadata.TagSource
import java.net.URI

/** Offline E-Hentai/ExHentai candidate parser. It deliberately does not search either site. */
object EHentaiMetadataProvider : MetadataCandidateProvider {
    override val providerId: String = "ehentai"

    override fun findCandidates(input: MetadataCandidateInput): List<MetadataCandidate> {
        val candidates = buildList {
            input.explicitSourceValues().forEach { value ->
                parseExact(value)?.let { exact ->
                    add(exact.toCandidate(input.observedAt))
                }
            }

            listOfNotNull(input.fileName, input.archiveTitle).forEach { value ->
                findTitleGid(value)?.let { gid ->
                    add(
                        MetadataCandidate(
                            providerId = providerId,
                            sourceId = gid,
                            confidence = TITLE_IDENTIFIER_CONFIDENCE,
                            match = MetadataCandidateMatch.TITLE_IDENTIFIER,
                            normalizedTitle = normalizeCandidateTitle(value),
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

    /** Accepts only a full supported gallery URL or an unambiguous `gid/token` identifier. */
    fun parseExact(value: String): EHentaiGallery? {
        val trimmed = value.trim()
        GID_TOKEN.matchEntire(trimmed)?.let { match ->
            return EHentaiGallery(
                gid = match.groupValues[1],
                token = match.groupValues[2],
                host = DEFAULT_HOST,
                explicitUrl = false,
            )
        }

        val url = trustedUri(trimmed) ?: return null
        val host = url.host.lowercase()
        if (host !in SUPPORTED_HOSTS || url.query != null || url.fragment != null) return null
        val match = GALLERY_PATH.matchEntire(url.rawPath ?: return null) ?: return null
        return EHentaiGallery(
            gid = match.groupValues[1],
            token = match.groupValues[2],
            host = host.removePrefix("www."),
            explicitUrl = true,
        )
    }

    private fun findTitleGid(value: String): String? = TITLE_GID.find(value)?.groupValues?.get(1)

    private fun EHentaiGallery.toCandidate(observedAt: Long): MetadataCandidate {
        val confidence = if (explicitUrl) EXPLICIT_URL_CONFIDENCE else EXPLICIT_IDENTIFIER_CONFIDENCE
        val sourceUrl = "https://$host/g/$gid/$token"
        return MetadataCandidate(
            providerId = providerId,
            sourceId = gid,
            sourceUrl = sourceUrl,
            confidence = confidence,
            match = if (explicitUrl) {
                MetadataCandidateMatch.EXPLICIT_SOURCE_URL
            } else {
                MetadataCandidateMatch.EXPLICIT_IDENTIFIER
            },
            patch = buildCandidatePatch(
                providerId = providerId,
                sourceId = gid,
                sourceUrl = sourceUrl,
                confidence = confidence,
                observedAt = observedAt,
                tagSource = TagSource.EHENTAI,
            ),
        )
    }

    private fun trustedUri(value: String): URI? {
        if (value.contains('\\')) return null
        val urlValue = if (value.startsWith("e-hentai.org/", ignoreCase = true) ||
            value.startsWith("www.e-hentai.org/", ignoreCase = true) ||
            value.startsWith("exhentai.org/", ignoreCase = true) ||
            value.startsWith("www.exhentai.org/", ignoreCase = true)
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

    data class EHentaiGallery internal constructor(
        val gid: String,
        val token: String,
        val host: String,
        internal val explicitUrl: Boolean,
    ) {
        val canonicalUrl: String get() = "https://$host/g/$gid/$token"
    }

    private const val DEFAULT_HOST = "e-hentai.org"
    private const val EXPLICIT_URL_CONFIDENCE = 0.99f
    private const val EXPLICIT_IDENTIFIER_CONFIDENCE = 0.97f
    private const val TITLE_IDENTIFIER_CONFIDENCE = 0.72f
    private const val TITLE_SEARCH_CONFIDENCE = 0.55f
    private val SUPPORTED_HOSTS = setOf("e-hentai.org", "www.e-hentai.org", "exhentai.org", "www.exhentai.org")
    private val GALLERY_PATH = Regex("^/g/([1-9][0-9]{0,11})/([A-Za-z0-9]{1,64})/?$")
    private val GID_TOKEN = Regex("^([1-9][0-9]{0,11})/([A-Za-z0-9]{1,64})$")
    private val TITLE_GID = Regex("\\[([1-9][0-9]{0,11})\\]")
}
