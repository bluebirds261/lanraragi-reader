package com.lanraragi.reader.data.history

import android.content.Context
import androidx.room.withTransaction
import com.lanraragi.reader.data.db.HistoryLegacyImporter
import com.lanraragi.reader.data.db.LegacyImportStateEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import java.io.File
import com.lanraragi.reader.data.readTextAtomically

data class HistoryBootstrapResult(
    val imported: Boolean,
    val count: Int = 0,
    val error: Throwable? = null,
)

/** Idempotently imports history.json; legacy remains untouched for rollback. */
class HistoryBootstrapper(
    context: Context,
    private val database: ReaderDatabase,
    private val file: File = File(context.filesDir, "history.json"),
    private val localIndexFile: File = File(context.filesDir, "local/index.json"),
    private val importer: HistoryLegacyImporter = HistoryLegacyImporter(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): HistoryBootstrapResult {
        if (!file.exists()) return HistoryBootstrapResult(imported = false, count = 0)
        val historyText = runCatching { file.readTextAtomically() }
            .getOrElse { return HistoryBootstrapResult(false, error = it) }
        val localIndexText = runCatching {
            localIndexFile.takeIf(File::exists)?.readTextAtomically()
        }.getOrNull()
        val decoded = importer.decodeResult(historyText, localIndexText)
        if (!decoded.validDocument) {
            return HistoryBootstrapResult(
                false,
                error = IllegalArgumentException("history.json contains no valid entries"),
            )
        }
        val entries = decoded.entries
        return runCatching {
            database.withTransaction {
                val markerDao = database.legacyImportStateDao()
                val marker = markerDao.find(IMPORT_KEY)
                if (marker != null && marker.sourceVersion >= SOURCE_VERSION) {
                    return@withTransaction false
                }
                if (entries.isNotEmpty()) database.readingHistoryDao().upsertAll(entries)
                markerDao.upsert(LegacyImportStateEntity(IMPORT_KEY, SOURCE_VERSION, clock()))
                true
            }.let { imported -> HistoryBootstrapResult(imported, if (imported) entries.size else 0) }
        }.getOrElse { HistoryBootstrapResult(false, error = it) }
    }

    companion object {
        private const val IMPORT_KEY = "history.json"
        private const val SOURCE_VERSION = 1
    }

    /** Select Room after a successful bootstrap; retain legacy as a safe rollback path on failure. */
    suspend fun bootstrapOrFallback(
        roomStore: RoomHistoryStore,
        legacyStore: LegacyHistoryStore,
    ): HistoryStore {
        val result = run()
        return if (result.error == null) roomStore else legacyStore
    }
}
