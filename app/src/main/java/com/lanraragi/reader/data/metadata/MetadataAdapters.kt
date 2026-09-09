package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.data.ApiException
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.db.MetadataStateEntity
import com.lanraragi.reader.data.db.MetadataProvenanceEntity
import com.lanraragi.reader.data.db.LocalMetadataEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Adapts the existing LANraragi repository to the metadata workbench boundary. */
class LanraragiMetadataGateway(
    private val repository: LanraragiRepository,
) : MetadataRemoteGateway {
    override suspend fun fetch(arcid: String): MetadataSnapshot =
        try {
            repository.getMetadata(arcid).toMetadataSnapshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiException) {
            throw error.toMetadataException()
        } catch (error: IOException) {
            throw MetadataRemoteException(
                reason = MetadataRemoteFailureReason.NETWORK,
                message = error.message ?: "Network request failed",
                cause = error,
            )
        }

    override suspend fun put(payload: MetadataPutPayload) {
        try {
            repository.updateArchiveMetadata(
                arcid = payload.arcid,
                title = payload.title.orEmpty(),
                tags = payload.tags.sortedBy { it.full }.joinToString(",") { it.raw.ifBlank { it.full } },
                summary = payload.summary,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiException) {
            throw error.toMetadataException()
        } catch (error: IOException) {
            throw MetadataRemoteException(
                reason = MetadataRemoteFailureReason.NETWORK,
                message = error.message ?: "Network request failed",
                cause = error,
            )
        }
    }

    private fun ApiException.toMetadataException(): MetadataRemoteException {
        val locked = statusCode == 423
        return MetadataRemoteException(
            reason = when {
                locked -> MetadataRemoteFailureReason.LOCKED
                statusCode == null -> MetadataRemoteFailureReason.NETWORK
                else -> MetadataRemoteFailureReason.HTTP
            },
            statusCode = statusCode,
            message = message ?: "Metadata request failed",
            cause = this,
        )
    }
}

/** Room adapter which keeps patch/snapshot codecs at the persistence edge. */
class RoomMetadataStateStore(
    private val database: ReaderDatabase,
) : MetadataStateStore {
    override suspend fun read(sourceKey: String): MetadataStoredState? {
        val entity = database.metadataStateDao().find(sourceKey)
        if (entity == null) {
            val local = database.localMetadataDao().find(sourceKey) ?: return null
            return MetadataStoredState(
                sourceKey = sourceKey,
                snapshot = MetadataSnapshot(
                    title = local.title,
                    summary = local.summary,
                    tags = parseStoredTags(local.tags),
                ),
                updatedAt = local.updatedAt,
            )
        }
        return try {
            MetadataStoredState(
                sourceKey = entity.sourceKey,
                snapshot = MetadataSnapshotCodec.decode(entity.snapshotJson),
                baseline = entity.baselineJson?.let(MetadataSnapshotCodec::decode),
                pendingPatch = MetadataPatchCodec.decode(entity.pendingPatchJson),
                updatedAt = entity.updatedAt,
            )
        } catch (error: MetadataStateStoreException) {
            throw error
        } catch (error: Exception) {
            throw MetadataStateStoreException("Invalid persisted metadata state", error)
        }
    }

    override suspend fun write(state: MetadataStoredState) {
        try {
            database.withTransaction {
                database.metadataStateDao().upsert(
                    MetadataStateEntity(
                        sourceKey = state.sourceKey,
                        snapshotJson = MetadataSnapshotCodec.encode(state.snapshot),
                        baselineJson = state.baseline?.let(MetadataSnapshotCodec::encode),
                        pendingPatchJson = MetadataPatchCodec.encode(state.pendingPatch),
                        updatedAt = state.updatedAt,
                    ),
                )
                database.metadataDao().deleteProvenance(state.sourceKey)
                val provenance = state.snapshot.provenance.map { (key, value) ->
                    MetadataProvenanceEntity(
                        sourceKey = state.sourceKey,
                        field = if (key.startsWith(TAG_PREFIX)) "tag" else key,
                        canonicalTag = key.removePrefix(TAG_PREFIX).takeIf { key.startsWith(TAG_PREFIX) },
                        providerId = value.providerId,
                        sourceId = value.sourceId,
                        sourceUrl = value.sourceUrl,
                        confidence = value.confidence,
                        fetchedAt = value.fetchedAt,
                        dataVersion = value.dataVersion,
                    )
                }
                if (provenance.isNotEmpty()) database.metadataDao().insertProvenance(provenance)
                if (state.sourceKey.startsWith(LOCAL_PREFIX)) {
                    database.localMetadataDao().upsert(
                        LocalMetadataEntity(
                            sourceKey = state.sourceKey,
                            title = state.snapshot.title,
                            tags = serializeTags(state.snapshot),
                            summary = state.snapshot.summary,
                            updatedAt = state.updatedAt,
                        ),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw MetadataStateStoreException("Unable to persist metadata state", error)
        }
    }

    private fun parseStoredTags(value: String?) = value
        .orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapTo(linkedSetOf()) { com.lanraragi.reader.domain.metadata.CanonicalTag.parse(it) }

    private fun serializeTags(snapshot: MetadataSnapshot): String = snapshot.tags
        .sortedBy { it.full }
        .joinToString(",") { it.raw.ifBlank { it.full } }

    private companion object {
        const val LOCAL_PREFIX = "local:"
        const val TAG_PREFIX = "tag:"
    }
}
