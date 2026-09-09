package com.lanraragi.reader.data.download

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.db.DownloadTaskEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.db.SavedArtifactEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

/** Room bridge for durable task specs; the legacy DownloadManager remains a compatibility facade. */
class RoomDownloadTaskStore(private val database: ReaderDatabase) : DownloadTaskStore {
    override suspend fun loadAll(): List<DurableDownloadTask> = database.downloadTaskDao()
        .loadByStates(DurableDownloadState.entries.map { it.name })
        .mapNotNull(::decode)

    override fun observeAll(): Flow<List<DurableDownloadTask>> = database.downloadTaskDao()
        .observeAll().map { rows -> rows.mapNotNull(::decode) }

    override suspend fun insert(task: DurableDownloadTask) = database.downloadTaskDao().upsert(encode(task))

    override suspend fun replace(task: DurableDownloadTask) = database.downloadTaskDao().upsert(encode(task))

    override suspend fun delete(taskId: String) = database.downloadTaskDao().delete(taskId)

    override suspend fun transition(
        taskId: String,
        expected: Set<DurableDownloadState>,
        transform: (DurableDownloadTask) -> DurableDownloadTask,
    ): DurableDownloadTask? {
        return database.withTransaction {
            val current = database.downloadTaskDao().find(taskId)?.let(::decode) ?: return@withTransaction null
            if (current.state !in expected) return@withTransaction null
            val updated = transform(current)
            database.downloadTaskDao().upsert(encode(updated))
            updated
        }
    }

    private fun encode(task: DurableDownloadTask): DownloadTaskEntity = DownloadTaskEntity(
        taskId = task.id,
        type = task.spec::class.simpleName.orEmpty(),
        archiveId = task.spec.source.archiveId,
        payloadJson = ApiClient.json.encodeToString<DownloadTaskSpec>(task.spec),
        state = task.state.name,
        completedBytes = task.completedBytes,
        totalBytes = task.totalBytes,
        retryCount = task.retryCount,
        priority = task.spec.priority,
        error = task.error,
        retryAt = task.retryAtEpochMs,
        entityTag = task.entityTag,
        lastModified = task.lastModified,
        createdAt = task.createdAtEpochMs,
        updatedAt = task.updatedAtEpochMs,
    )

    private fun decode(entity: DownloadTaskEntity): DurableDownloadTask? = runCatching {
        DurableDownloadTask(
            id = entity.taskId,
            spec = ApiClient.json.decodeFromString<DownloadTaskSpec>(entity.payloadJson),
            state = DurableDownloadState.valueOf(entity.state),
            completedBytes = entity.completedBytes,
            totalBytes = entity.totalBytes,
            retryCount = entity.retryCount,
            retryAtEpochMs = entity.retryAt,
            entityTag = entity.entityTag,
            lastModified = entity.lastModified,
            error = entity.error,
            createdAtEpochMs = entity.createdAt,
            updatedAtEpochMs = entity.updatedAt,
        )
    }.getOrNull()
}

class RoomSavedArtifactStore(private val database: ReaderDatabase) : SavedArtifactStore {
    override suspend fun loadAll(): List<SavedArtifact> = database.savedArtifactDao().loadForEviction().map {
        SavedArtifact(
            artifactKey = it.artifactId,
            source = DownloadSourceIdentity(it.archiveId, it.serverScope),
            path = it.filePath,
            revision = it.revision,
            byteSize = it.byteSize,
            pinned = it.pinned,
            lastAccessAtEpochMs = it.lastAccessAt,
        )
    }

    override suspend fun upsert(artifact: SavedArtifact) = database.savedArtifactDao().upsert(
        SavedArtifactEntity(
            artifactId = artifact.artifactKey,
            archiveId = artifact.source.archiveId,
            serverScope = artifact.source.serverScope,
            filePath = artifact.path,
            revision = artifact.revision,
            byteSize = artifact.byteSize,
            pinned = artifact.pinned,
            lastAccessAt = artifact.lastAccessAtEpochMs,
        ),
    )

    override suspend fun delete(artifactKey: String) = database.savedArtifactDao().deleteById(artifactKey)
}
