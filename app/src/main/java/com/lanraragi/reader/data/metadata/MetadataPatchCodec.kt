package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

enum class MetadataPatchCodecError {
    MALFORMED_JSON,
    INVALID_ENVELOPE,
    UNSUPPORTED_VERSION,
    INVALID_DATA,
}

class MetadataPatchCodecException(
    val reason: MetadataPatchCodecError,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Stable Room wire format for pending metadata patches. */
object MetadataPatchCodec {
    const val CURRENT_VERSION = 1

    private const val FORMAT = "metadata-patch"

    fun encode(patch: MetadataPatch): String = ApiClient.json.encodeToString(
        MetadataPatchEnvelopeV1(
            format = FORMAT,
            version = CURRENT_VERSION,
            patch = MetadataPatchWireV1.fromDomain(patch),
        ),
    )

    fun decode(encoded: String): MetadataPatch {
        val root = try {
            ApiClient.json.parseToJsonElement(encoded) as? JsonObject
                ?: throw codecError(
                    MetadataPatchCodecError.INVALID_ENVELOPE,
                    "metadata patch payload must be a JSON object",
                )
        } catch (error: MetadataPatchCodecException) {
            throw error
        } catch (error: Exception) {
            throw codecError(
                MetadataPatchCodecError.MALFORMED_JSON,
                "metadata patch payload is not valid JSON",
                error,
            )
        }

        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != FORMAT) {
            throw codecError(
                MetadataPatchCodecError.INVALID_ENVELOPE,
                "metadata patch payload has an invalid format marker",
            )
        }

        val versionPrimitive = root["version"] as? JsonPrimitive
        val version = versionPrimitive
            ?.takeIf { !it.isString }
            ?.intOrNull
            ?: throw codecError(
                MetadataPatchCodecError.INVALID_ENVELOPE,
                "metadata patch payload is missing an integer version",
            )
        if (version != CURRENT_VERSION) {
            throw codecError(
                MetadataPatchCodecError.UNSUPPORTED_VERSION,
                "unsupported metadata patch version: $version",
            )
        }
        if (root["patch"] !is JsonObject) {
            throw codecError(
                MetadataPatchCodecError.INVALID_ENVELOPE,
                "metadata patch payload is missing the patch object",
            )
        }

        return try {
            ApiClient.json.decodeFromString<MetadataPatchEnvelopeV1>(encoded).patch.toDomain()
        } catch (error: MetadataPatchCodecException) {
            throw error
        } catch (error: Exception) {
            throw codecError(
                MetadataPatchCodecError.INVALID_DATA,
                "metadata patch payload contains invalid data",
                error,
            )
        }
    }

    private fun codecError(
        reason: MetadataPatchCodecError,
        message: String,
        cause: Throwable? = null,
    ) = MetadataPatchCodecException(reason, message, cause)

    @Serializable
    private data class MetadataPatchEnvelopeV1(
        val format: String,
        val version: Int,
        val patch: MetadataPatchWireV1,
    )

    @Serializable
    private data class MetadataPatchWireV1(
        val title: MetadataFieldWireV1? = null,
        val summary: MetadataFieldWireV1? = null,
        val sourceUrl: MetadataFieldWireV1? = null,
        val addTags: List<CanonicalTagWireV1> = emptyList(),
        val removeTags: List<CanonicalTagWireV1> = emptyList(),
        val tagProvenance: Map<String, MetadataProvenanceWireV1> = emptyMap(),
    ) {
        fun toDomain(): MetadataPatch = MetadataPatch(
            title = title?.toDomain(),
            summary = summary?.toDomain(),
            sourceUrl = sourceUrl?.toDomain(),
            addTags = addTags.mapTo(linkedSetOf()) { it.toDomain() },
            removeTags = removeTags.mapTo(linkedSetOf()) { it.toDomain() },
            tagProvenance = tagProvenance.mapValues { it.value.toDomain() },
        )

        companion object {
            fun fromDomain(value: MetadataPatch) = MetadataPatchWireV1(
                title = value.title?.let(MetadataFieldWireV1::fromDomain),
                summary = value.summary?.let(MetadataFieldWireV1::fromDomain),
                sourceUrl = value.sourceUrl?.let(MetadataFieldWireV1::fromDomain),
                addTags = value.addTags
                    .sortedWith(CanonicalTagWireV1.DOMAIN_ORDER)
                    .map(CanonicalTagWireV1::fromDomain),
                removeTags = value.removeTags
                    .sortedWith(CanonicalTagWireV1.DOMAIN_ORDER)
                    .map(CanonicalTagWireV1::fromDomain),
                tagProvenance = value.tagProvenance.toSortedMap().mapValues {
                    MetadataProvenanceWireV1.fromDomain(it.value)
                },
            )
        }
    }

    @Serializable
    private data class MetadataFieldWireV1(
        val value: String,
        val provenance: MetadataProvenanceWireV1,
    ) {
        fun toDomain() = MetadataField(value, provenance.toDomain())

        companion object {
            fun fromDomain(value: MetadataField<String>) = MetadataFieldWireV1(
                value = value.value,
                provenance = MetadataProvenanceWireV1.fromDomain(value.provenance),
            )
        }
    }

    @Serializable
    private data class MetadataProvenanceWireV1(
        val providerId: String,
        val sourceId: String? = null,
        val sourceUrl: String? = null,
        val confidence: Float? = null,
        val fetchedAt: Long,
        val dataVersion: String? = null,
    ) {
        fun toDomain() = MetadataProvenance(
            providerId = providerId,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            confidence = confidence,
            fetchedAt = fetchedAt,
            dataVersion = dataVersion,
        )

        companion object {
            fun fromDomain(value: MetadataProvenance) = MetadataProvenanceWireV1(
                providerId = value.providerId,
                sourceId = value.sourceId,
                sourceUrl = value.sourceUrl,
                confidence = value.confidence,
                fetchedAt = value.fetchedAt,
                dataVersion = value.dataVersion,
            )
        }
    }

    @Serializable
    private data class CanonicalTagWireV1(
        val namespace: String? = null,
        val key: String,
        val raw: String,
        val translated: String? = null,
        val source: String,
        val confidence: Float? = null,
    ) {
        fun toDomain() = CanonicalTag(
            namespace = namespace,
            key = key,
            raw = raw,
            displayNameZh = translated,
            source = TagSource.entries.firstOrNull { it.name == source }
                ?: throw MetadataPatchCodecException(
                    reason = MetadataPatchCodecError.INVALID_DATA,
                    message = "unknown canonical tag source: $source",
                ),
            confidence = confidence,
        )

        companion object {
            val DOMAIN_ORDER = compareBy<CanonicalTag>(
                { it.full },
                { it.namespace },
                { it.key },
                { it.raw },
                { it.displayNameZh.orEmpty() },
                { it.source.name },
                { it.confidence },
            )

            fun fromDomain(value: CanonicalTag) = CanonicalTagWireV1(
                namespace = value.namespace,
                key = value.key,
                raw = value.raw,
                translated = value.displayNameZh,
                source = value.source.name,
                confidence = value.confidence,
            )
        }
    }
}
