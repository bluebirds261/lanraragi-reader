package com.lanraragi.reader.data.history.core

import com.lanraragi.reader.domain.model.ArchiveIdentity

/** A complete, durable reading-history record. Pages are zero based. */
data class HistoryRecord(
    val identity: ArchiveIdentity,
    val title: String,
    val page: Int = 0,
    val pageCount: Int = 0,
    val firstReadAt: Long,
    val lastReadAt: Long,
    val coverRef: String? = null,
) {
    val sourceKey: String get() = identity.sourceKey
    val archiveId: String? get() = (identity as? ArchiveIdentity.Remote)?.arcid

    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(page >= 0 && pageCount >= 0) { "page and pageCount must be non-negative" }
        require(firstReadAt <= lastReadAt) { "firstReadAt must not be after lastReadAt" }
    }

    fun progress(page: Int, pageCount: Int = this.pageCount, at: Long = lastReadAt): HistoryRecord =
        copy(page = page.coerceAtLeast(0), pageCount = pageCount.coerceAtLeast(0), lastReadAt = maxOf(lastReadAt, at))
}

/** Pure history index. It keeps every identity and derives a bounded recent projection. */
class HistoryIndex {
    private val records = LinkedHashMap<String, HistoryRecord>()

    fun recordOpen(record: HistoryRecord): HistoryRecord {
        val old = records[record.sourceKey]
        val merged = if (old == null) record else old.copy(
            title = record.title,
            page = record.page,
            pageCount = if (record.pageCount > 0) record.pageCount else old.pageCount,
            firstReadAt = minOf(old.firstReadAt, record.firstReadAt),
            lastReadAt = maxOf(old.lastReadAt, record.lastReadAt),
            coverRef = record.coverRef ?: old.coverRef,
        )
        records[record.sourceKey] = merged
        return merged
    }

    fun updateProgress(identity: ArchiveIdentity, page: Int, pageCount: Int = 0, at: Long = System.currentTimeMillis(), title: String? = null): HistoryRecord {
        val old = records[identity.sourceKey]
        val result = if (old == null) HistoryRecord(identity, title?.takeIf { it.isNotBlank() } ?: identity.sourceKey, page.coerceAtLeast(0), pageCount.coerceAtLeast(0), at, at) else old.copy(
            title = title?.takeIf { it.isNotBlank() } ?: old.title,
            page = page.coerceAtLeast(0),
            pageCount = if (pageCount > 0) pageCount else old.pageCount,
            lastReadAt = maxOf(old.lastReadAt, at),
        )
        records[identity.sourceKey] = result
        return result
    }

    fun remove(identity: ArchiveIdentity) { records.remove(identity.sourceKey) }
    fun clear() { records.clear() }
    fun all(): List<HistoryRecord> = records.values.sortedWith(compareByDescending<HistoryRecord> { it.lastReadAt }.thenBy { it.sourceKey })
    fun recent(limit: Int = 3): List<HistoryRecord> = if (limit <= 0) emptyList() else all().take(limit)
    fun get(identity: ArchiveIdentity): HistoryRecord? = records[identity.sourceKey]
}

/** Stateless helpers useful when records are loaded from more than one store. */
object HistoryQueries {
    fun dedupe(records: Iterable<HistoryRecord>): List<HistoryRecord> = records
        .groupBy { it.sourceKey }
        .values
        .map { same -> same.reduce(::merge) }
        .sortedWith(compareByDescending<HistoryRecord> { it.lastReadAt }.thenBy { it.sourceKey })

    fun recent(records: Iterable<HistoryRecord>, limit: Int = 3): List<HistoryRecord> =
        if (limit <= 0) emptyList() else dedupe(records).take(limit)

    fun merge(a: HistoryRecord, b: HistoryRecord): HistoryRecord {
        require(a.sourceKey == b.sourceKey) { "cannot merge different identities" }
        val latest = if (b.lastReadAt >= a.lastReadAt) b else a
        return latest.copy(
            firstReadAt = minOf(a.firstReadAt, b.firstReadAt),
            lastReadAt = maxOf(a.lastReadAt, b.lastReadAt),
            pageCount = maxOf(a.pageCount, b.pageCount),
            coverRef = latest.coverRef ?: a.coverRef ?: b.coverRef,
        )
    }
}
