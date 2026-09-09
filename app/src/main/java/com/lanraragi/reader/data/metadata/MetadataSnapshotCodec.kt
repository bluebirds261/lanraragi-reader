package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Versioned persistence format for the client-owned parts of a metadata snapshot. */
object MetadataSnapshotCodec {
    private const val CURRENT_VERSION = 1

    fun encode(snapshot: MetadataSnapshot): String = ApiClient.json.encodeToString(
        SnapshotEnvelope(
            version = CURRENT_VERSION,
            snapshot = SnapshotWire.fromDomain(snapshot),
        ),
    )

    fun decode(value: String): MetadataSnapshot = try {
        val envelope = ApiClient.json.decodeFromString<SnapshotEnvelope>(value)
        if (envelope.version != CURRENT_VERSION) {
            throw MetadataStateStoreException("Unsupported metadata snapshot version: ${envelope.version}")
        }
        envelope.snapshot.toDomain()
    } catch (error: MetadataStateStoreException) {
        throw error
    } catch (error: Exception) {
        throw MetadataStateStoreException("Invalid persisted metadata snapshot", error)
    }

    @Serializable
    private data class SnapshotEnvelope(
        val version: Int,
        val snapshot: SnapshotWire,
    )

    @Serializable
    private data class SnapshotWire(
        val title: String? = null,
        val summary: String? = null,
        val sourceUrl: String? = null,
        val tags: List<TagWire> = emptyList(),
        val userOverrides: List<String> = emptyList(),
        val provenance: Map<String, ProvenanceWire> = emptyMap(),
        val revision: String? = null,
    ) {
        fun toDomain(): MetadataSnapshot = MetadataSnapshot(
            title = title,
            summary = summary,
            sourceUrl = sourceUrl,
            tags = tags.mapTo(linkedSetOf(), TagWire::toDomain),
            userOverrides = userOverrides.mapTo(linkedSetOf()) { value ->
                MetadataFieldName.entries.firstOrNull { it.name == value }
                    ?: throw MetadataStateStoreException("Unknown metadata field ownership: $value")
            },
            provenance = provenance.mapValues { it.value.toDomain() },
            revision = revision,
        )

        companion object {
            fun fromDomain(value: MetadataSnapshot) = SnapshotWire(
                title = value.title,
                summary = value.summary,
                sourceUrl = value.sourceUrl,
                tags = value.tags.sortedBy(CanonicalTag::full).map(TagWire::fromDomain),
                userOverrides = value.userOverrides.map(MetadataFieldName::name).sorted(),
                provenance = value.provenance.toSortedMap().mapValues { ProvenanceWire.fromDomain(it.value) },
                revision = value.revision,
            )
        }
    }

    @Serializable
    private data class TagWire(
        val namespace: String? = null,
        val key: String,
        val raw: String,
        val displayNameZh: String? = null,
        val source: String,
        val confidence: Float? = null,
    ) {
        fun toDomain(): CanonicalTag = CanonicalTag(
            namespace = namespace,
            key = key,
            raw = raw,
            displayNameZh = displayNameZh,
            source = TagSource.entries.firstOrNull { it.name == source }
                ?: throw MetadataStateStoreException("Unknown metadata tag source: $source"),
            confidence = confidence,
        )

        companion object {
            fun fromDomain(value: CanonicalTag) = TagWire(
                namespace = value.namespace,
                key = value.key,
                raw = value.raw,
                displayNameZh = value.displayNameZh,
                source = value.source.name,
                confidence = value.confidence,
            )
        }
    }

    @Serializable
    private data class ProvenanceWire(
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
            fun fromDomain(value: MetadataProvenance) = ProvenanceWire(
                providerId = value.providerId,
                sourceId = value.sourceId,
                sourceUrl = value.sourceUrl,
                confidence = value.confidence,
                fetchedAt = value.fetchedAt,
                dataVersion = value.dataVersion,
            )
        }
    }
}
