package com.lanraragi.reader.domain.metadata

data class MetadataProvenance(
    val providerId: String,
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val confidence: Float? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(confidence == null || confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }
}

data class MetadataField<T>(
    val value: T,
    val provenance: MetadataProvenance,
)

data class MetadataPatch(
    val title: MetadataField<String>? = null,
    val summary: MetadataField<String>? = null,
    val sourceUrl: MetadataField<String>? = null,
    val addTags: Set<CanonicalTag> = emptySet(),
    val removeTags: Set<CanonicalTag> = emptySet(),
    val tagProvenance: Map<String, MetadataProvenance> = emptyMap(),
) {
    init {
        require(addTags.map { it.full }.intersect(removeTags.map { it.full }.toSet()).isEmpty()) {
            "the same canonical tag cannot be added and removed"
        }
    }
}
