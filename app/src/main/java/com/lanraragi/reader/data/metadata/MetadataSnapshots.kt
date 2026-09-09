package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.TagSource

fun Archive.toMetadataSnapshot(): MetadataSnapshot {
    val canonicalTags = tagList.mapTo(linkedSetOf()) {
        CanonicalTag.parse(it, source = TagSource.LANRARAGI)
    }
    val sourceUrl = canonicalTags
        .firstOrNull { it.identity.namespace == "source" }
        ?.raw
        ?.substringAfter(':', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return MetadataSnapshot(
        title = title,
        summary = summary,
        sourceUrl = sourceUrl,
        tags = canonicalTags,
    )
}

fun MetadataSnapshot.applyTo(archive: Archive): Archive = archive.copy(
    title = title.orEmpty(),
    summary = summary.orEmpty(),
    tags = tags
        .sortedBy(CanonicalTag::full)
        .joinToString(",") { it.raw.trim().ifEmpty { it.full } },
)
