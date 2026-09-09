package com.lanraragi.reader.data.history

import com.lanraragi.reader.data.db.ProgressTaskEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.history.core.ProgressTask
import com.lanraragi.reader.data.history.core.ProgressTaskStore
import com.lanraragi.reader.domain.model.ArchiveIdentity

/** Room-backed progress outbox. Local identities are rejected at this boundary. */
class RoomProgressTaskStore(private val database: ReaderDatabase) : ProgressTaskStore {
    override suspend fun load(): List<ProgressTask> = database.progressTaskDao().loadAll().mapNotNull { row ->
        runCatching {
            ProgressTask(
                identity = ArchiveIdentity.Remote(row.archiveId, row.serverScope),
                page = row.page,
                pageCount = row.pageCount,
                updatedAt = row.updatedAt,
            )
        }.getOrNull()
    }

    override suspend fun upsert(task: ProgressTask) {
        val remote = task.identity as? ArchiveIdentity.Remote ?: return
        database.progressTaskDao().upsert(
            ProgressTaskEntity(
                sourceKey = task.sourceKey,
                archiveId = remote.arcid,
                serverScope = remote.serverScope,
                page = task.page,
                pageCount = task.pageCount,
                updatedAt = task.updatedAt,
            ),
        )
    }

    override suspend fun remove(sourceKey: String) = database.progressTaskDao().delete(sourceKey)
}
