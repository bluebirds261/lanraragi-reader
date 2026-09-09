package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.db.LocalArchiveEntity
import com.lanraragi.reader.data.db.LocalMetadataEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.flow.first

/** LANraragi adapter. It exhausts the server result set so cross-source paging remains stable. */
class LanraragiLibraryRemoteGateway(
    private val repository: LanraragiRepository,
    private val serverScope: () -> String? = { null },
) : LibraryRemoteGateway {
    override suspend fun fetch(query: LibraryQuery): List<LibraryEntry> {
        val request = query.remoteRequest()
        val rows = mutableListOf<LibraryEntry>()
        var offset = 0
        var expected: Int? = null
        while (true) {
            val result = repository.getArchives(
                page = offset,
                filter = request.filter,
                sortby = request.sort.wireValue,
                order = request.direction.name.lowercase(),
                categoryId = request.categoryId,
                newonly = request.newOnly,
                untaggedonly = request.untaggedOnly,
            )
            expected = result.total ?: expected
            val received = result.items
            rows += received.asSequence().filter { it.arcid.isNotBlank() }.map { it.toLibraryEntry(serverScope()) }
            offset += received.size
            if (received.isEmpty() || (expected != null && offset >= expected)) break
        }
        return rows.distinctBy(LibraryEntry::sourceKey)
    }
}

/** Room-only adapter. It intentionally has no dependency on a network repository. */
class RoomLibraryLocalGateway(
    private val database: ReaderDatabase,
) : LibraryLocalGateway {
    override suspend fun fetch(): List<LibraryEntry> {
        return database.localArchiveDao().observeAll().first().map { archive ->
            archive.toLibraryEntry(database.localMetadataDao().find(archive.sourceKey))
        }
    }
}

private fun Archive.toLibraryEntry(serverScopeValue: String?): LibraryEntry = LibraryEntry(
    identity = ArchiveIdentity.Remote(arcid, serverScopeValue),
    title = title,
    tags = tagList,
    summary = summary,
    categoryId = category.takeIf(String::isNotBlank),
    isNew = isNew,
    pageCount = pagecount,
    progress = progress,
    dateAdded = dateadded,
)

private fun LocalArchiveEntity.toLibraryEntry(metadata: LocalMetadataEntity?): LibraryEntry = LibraryEntry(
    identity = ArchiveIdentity.LocalSaf(uri),
    title = metadata?.title ?: title,
    tags = metadata?.tags.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty),
    summary = metadata?.summary.orEmpty(),
    pageCount = pageCount,
    isSaved = true,
    localUri = uri,
)
