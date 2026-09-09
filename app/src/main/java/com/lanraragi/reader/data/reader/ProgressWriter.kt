package com.lanraragi.reader.data.reader

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.history.core.ProgressFlushReport
import com.lanraragi.reader.data.history.core.ProgressOutbox
import com.lanraragi.reader.data.history.core.ProgressTask
import com.lanraragi.reader.data.history.core.ProgressTaskStore
import com.lanraragi.reader.data.history.core.ProgressTransport
import com.lanraragi.reader.domain.model.ArchiveIdentity
import com.lanraragi.reader.domain.reader.ReaderPageMapping

/** Reader-facing progress boundary. Local/SAF identities are intentionally ignored. */
interface ProgressWriter {
    suspend fun record(identity: ArchiveIdentity, page: Int, pageCount: Int = 0): ProgressTask?
    suspend fun pending(): List<ProgressTask>
    suspend fun flush(): ProgressFlushReport
}

class OutboxProgressWriter(
    store: ProgressTaskStore,
    private val transport: ProgressTransport,
) : ProgressWriter {
    private val outbox = ProgressOutbox(store)
    override suspend fun record(identity: ArchiveIdentity, page: Int, pageCount: Int): ProgressTask? =
        outbox.enqueue(identity, page, pageCount)
    override suspend fun pending(): List<ProgressTask> = outbox.pending()
    override suspend fun flush(): ProgressFlushReport = outbox.flush(transport)
}

/** Production transport; conversion to LANraragi's one-based progress endpoint stays here. */
class RepositoryProgressTransport(
    private val repository: LanraragiRepository,
) : ProgressTransport {
    override suspend fun write(identity: ArchiveIdentity.Remote, page: Int, pageCount: Int) {
        repository.setProgress(identity.arcid, ReaderPageMapping.uiPageToApiPage(page))
    }
}

/** Useful for local-only sessions and previews; it never performs any network work. */
object NoopProgressWriter : ProgressWriter {
    override suspend fun record(identity: ArchiveIdentity, page: Int, pageCount: Int): ProgressTask? = null
    override suspend fun pending(): List<ProgressTask> = emptyList()
    override suspend fun flush(): ProgressFlushReport = ProgressFlushReport(0, 0, 0, 0)
}
