package com.lanraragi.reader.data.favorites

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.metadata.providers.EHentaiMetadataProvider

/**
 * Resolves E-H favorite entries against LANraragi by exact source identity.
 * The explicit sync preview is allowed to scan the catalog, but it never uses
 * titles or fuzzy metadata and therefore cannot associate an unrelated book.
 */
class LanraragiEhFavoriteArchiveLinkGateway(
    private val repository: LanraragiRepository,
) : EhFavoriteArchiveLinkGateway {
    override suspend fun resolve(
        identities: Set<EhFavoriteSourceIdentity>,
    ): List<EhFavoriteArchiveLink> {
        if (identities.isEmpty()) return emptyList()
        val wanted = identities.mapTo(linkedSetOf()) { it.normalized() }
        val links = linkedMapOf<EhFavoriteSourceIdentity, EhFavoriteArchiveLink>()
        var start = 0
        var total: Int? = null

        while (total == null || start < total) {
            val page = repository.getArchives(page = start)
            total = page.total ?: total
            if (page.items.isEmpty()) break
            page.items.forEach { archive ->
                archive.tagList.asSequence()
                    .filter { it.substringBefore(':').equals("source", ignoreCase = true) }
                    .map { it.substringAfter(':').trim() }
                    .mapNotNull(EHentaiMetadataProvider::parseExact)
                    .map { gallery -> EhFavoriteSourceIdentity(gallery.gid, gallery.token).normalized() }
                    .filter(wanted::contains)
                    .forEach { identity ->
                        links.putIfAbsent(identity, EhFavoriteArchiveLink(identity, archive.arcid))
                    }
            }
            start += page.items.size
        }
        return links.values.toList()
    }
}
