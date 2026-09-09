package com.lanraragi.reader.data.history

import com.lanraragi.reader.data.HistoryEntry
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.db.ReadingHistoryEntity
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Room-backed full history; optional legacy mirror is updated after successful writes. */
class RoomHistoryStore(
    private val database: ReaderDatabase,
    private val legacyMirror: LegacyHistoryStore? = null,
    scope: CoroutineScope? = null,
) : HistoryStore {
    private val dao = database.readingHistoryDao()
    private val mutex = Mutex()
    private val scopeRef = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val entries: StateFlow<List<HistoryEntry>> = dao.observeAll()
        .map { rows -> rows.map(::toHistoryEntry) }
        .stateIn(scopeRef, SharingStarted.Eagerly, emptyList())

    override suspend fun record(arcid: String, title: String, page: Int, pageCount: Int) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val safePage = page.coerceAtLeast(0)
            val safeCount = pageCount.coerceAtLeast(0)
            val existingRow = findRow(arcid)
            val sourceKey = existingRow?.sourceKey ?: identityFor(arcid).sourceKey
            val existing = entries.value.firstOrNull { it.arcid == arcid }
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val entity = if (existing != null && existing.timestamp >= todayStart) {
                ReadingHistoryEntity(sourceKey, arcid, title, safePage,
                    if (safeCount > 0) safeCount else existing.pageCount,
                    existing.timestamp, now)
            } else {
                ReadingHistoryEntity(sourceKey, arcid, title, safePage, safeCount,
                    existingRow?.firstReadAt ?: now, now)
            }
            withContext(Dispatchers.IO) { dao.upsert(entity) }
            legacyMirror?.record(arcid, title, safePage, safeCount)
        }
    }

    override suspend fun recordProgress(arcid: String, page: Int, pageCount: Int, title: String?) {
        mutex.withLock {
            val existingRow = findRow(arcid)
            val sourceKey = existingRow?.sourceKey ?: identityFor(arcid).sourceKey
            val current = entries.value.firstOrNull { it.arcid == arcid }
            if (current == null) {
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) { dao.upsert(ReadingHistoryEntity(
                    sourceKey, arcid, title?.takeIf(String::isNotBlank) ?: arcid,
                    page.coerceAtLeast(0), pageCount.coerceAtLeast(0), now, now,
                )) }
            } else {
                withContext(Dispatchers.IO) { dao.upsert(ReadingHistoryEntity(
                    sourceKey, arcid, title?.takeIf(String::isNotBlank) ?: current.title,
                    page.coerceAtLeast(0), if (pageCount > 0) pageCount else current.pageCount,
                    current.timestamp, current.timestamp,
                )) }
            }
            legacyMirror?.recordProgress(arcid, page, pageCount, title)
        }
    }

    override suspend fun remove(arcid: String) {
        mutex.withLock {
            val identity = identityFor(arcid)
            withContext(Dispatchers.IO) {
                dao.deleteAll(listOf(identity.sourceKey))
                dao.deleteByArchiveId(arcid)
            }
            legacyMirror?.remove(arcid)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) { dao.clear() }
            legacyMirror?.clear()
        }
    }

    override fun recentDeduped(n: Int): List<HistoryEntry> = LegacyHistoryStore.dedupe(entries.value, n)

    private fun toHistoryEntry(row: ReadingHistoryEntity) = HistoryEntry(
        arcid = row.archiveId ?: row.sourceKey.removePrefix("remote:"),
        title = row.title,
        timestamp = row.lastReadAt,
        page = row.page.coerceAtLeast(0),
        pageCount = row.pageCount.coerceAtLeast(0),
    )

    private fun identityFor(arcid: String): ArchiveIdentity = when {
        arcid.startsWith("local_", ignoreCase = true) -> ArchiveIdentity.LocalSaf("legacy-local:$arcid")
        arcid.startsWith("legacy-local:", ignoreCase = true) -> ArchiveIdentity.LocalSaf(arcid)
        else -> ArchiveIdentity.Remote(arcid)
    }

    private suspend fun findRow(arcid: String): ReadingHistoryEntity? = withContext(Dispatchers.IO) {
        dao.findByArchiveId(arcid) ?: dao.find(identityFor(arcid).sourceKey)
    }

    suspend fun mergeEntries(entries: List<HistoryEntry>) {
        mutex.withLock {
            entries.forEach { entry ->
                val existing = findRow(entry.arcid)
                val identity = identityFor(entry.arcid)
                val row = ReadingHistoryEntity(
                    sourceKey = existing?.sourceKey ?: identity.sourceKey,
                    archiveId = entry.arcid,
                    title = entry.title,
                    page = entry.page.coerceAtLeast(0),
                    pageCount = entry.pageCount.coerceAtLeast(0),
                    firstReadAt = minOf(existing?.firstReadAt ?: entry.timestamp, entry.timestamp),
                    lastReadAt = maxOf(existing?.lastReadAt ?: entry.timestamp, entry.timestamp),
                )
                withContext(Dispatchers.IO) { dao.upsert(row) }
            }
        }
    }
}
