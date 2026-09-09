package com.lanraragi.reader.data.db

import android.content.Context
import com.lanraragi.reader.data.readTextAtomically
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class LegacySourceStatus {
    MISSING,
    IMPORTED,
    ALREADY_IMPORTED,
    INVALID,
    FAILED,
}

data class LegacySourceResult(
    val key: String,
    val status: LegacySourceStatus,
    val count: Int = 0,
    val error: String? = null,
) {
    val roomUsable: Boolean
        get() = status == LegacySourceStatus.MISSING ||
            status == LegacySourceStatus.IMPORTED ||
            status == LegacySourceStatus.ALREADY_IMPORTED
}

data class AppDataBootstrapReport(
    val sources: Map<String, LegacySourceResult>,
) {
    fun source(key: String): LegacySourceResult =
        sources[key] ?: LegacySourceResult(key, LegacySourceStatus.MISSING)
}

/**
 * Single application-scoped owner for importing pre-Room JSON state.
 *
 * File reads and artifact inspection happen before each Room transaction.
 * Every valid source commits its rows and version marker atomically, while a
 * corrupt source is isolated from the remaining imports. Legacy files remain
 * untouched for the rollback window.
 */
class AppDataBootstrapper(
    private val filesDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val historyImporter: HistoryLegacyImporter = HistoryLegacyImporter(),
    private val localImporter: LocalIndexLegacyImporter = LocalIndexLegacyImporter(),
    private val downloadImporter: DownloadTaskLegacyImporter = DownloadTaskLegacyImporter(),
    private val offlineImporter: OfflineIndexLegacyImporter = OfflineIndexLegacyImporter(),
    private val commitImport: suspend (LegacyImport) -> Boolean,
) {
    constructor(
        context: Context,
        database: ReaderDatabase,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        filesDir = context.filesDir,
        clock = clock,
        commitImport = { legacyImport ->
            LegacyJsonImportCoordinator(database, clock)
                .run(listOf(legacyImport))
                .single()
                .imported
        },
    )

    suspend fun run(): AppDataBootstrapReport = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, LegacySourceResult>()
        val localText = readOptional(File(filesDir, LOCAL_INDEX_KEY))

        results[LOCAL_INDEX_KEY] = importLocal(localText)
        coroutineContext.ensureActive()
        results[HISTORY_KEY] = importHistory(readOptional(File(filesDir, HISTORY_KEY)), localText)
        coroutineContext.ensureActive()
        results[DOWNLOAD_TASKS_KEY] = importDownloads(readOptional(File(filesDir, DOWNLOAD_TASKS_KEY)))
        coroutineContext.ensureActive()
        results[OFFLINE_INDEX_KEY] = importOffline(readOptional(File(filesDir, OFFLINE_INDEX_KEY)))

        AppDataBootstrapReport(results)
    }

    private suspend fun importHistory(source: SourceText, localSource: SourceText): LegacySourceResult =
        importSource(HISTORY_KEY, source) { text ->
            val decoded = historyImporter.decodeResult(text, localSource.text)
            if (!decoded.validDocument) return@importSource InvalidDecode("invalid_history_json")
            ValidDecode(decoded.entries.size) { db ->
                if (decoded.entries.isNotEmpty()) db.readingHistoryDao().upsertAll(decoded.entries)
            }
        }

    private suspend fun importLocal(source: SourceText): LegacySourceResult =
        importSource(LOCAL_INDEX_KEY, source) { text ->
            val decoded = localImporter.decodeResult(text)
            if (!decoded.validDocument) return@importSource InvalidDecode(decoded.error ?: "invalid_local_index")
            ValidDecode(decoded.entries.size) { db ->
                if (decoded.entries.isNotEmpty()) db.localArchiveDao().upsertAll(decoded.entries)
            }
        }

    private suspend fun importDownloads(source: SourceText): LegacySourceResult =
        importSource(DOWNLOAD_TASKS_KEY, source) { text ->
            val decoded = downloadImporter.decodeResult(text, clock())
            if (!decoded.validDocument) return@importSource InvalidDecode(decoded.error ?: "invalid_download_tasks")
            ValidDecode(decoded.entries.size) { db ->
                if (decoded.entries.isNotEmpty()) db.downloadTaskDao().upsertAll(decoded.entries)
            }
        }

    private suspend fun importOffline(source: SourceText): LegacySourceResult =
        importSource(OFFLINE_INDEX_KEY, source) { text ->
            val decoded = offlineImporter.decodeResult(text, collectOfflineFacts())
            if (!decoded.validDocument) return@importSource InvalidDecode(decoded.error ?: "invalid_offline_index")
            ValidDecode(decoded.entries.size) { db ->
                if (decoded.entries.isNotEmpty()) db.savedArtifactDao().upsertAll(decoded.entries)
            }
        }

    private suspend fun importSource(
        key: String,
        source: SourceText,
        decode: (String) -> DecodedSource,
    ): LegacySourceResult {
        if (!source.exists) return LegacySourceResult(key, LegacySourceStatus.MISSING)
        source.error?.let { return LegacySourceResult(key, LegacySourceStatus.FAILED, error = it) }
        val decoded = try {
            decode(source.text.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return LegacySourceResult(key, LegacySourceStatus.INVALID, error = e.message ?: "decode_failed")
        }
        if (decoded is InvalidDecode) {
            return LegacySourceResult(key, LegacySourceStatus.INVALID, error = decoded.reason)
        }
        decoded as ValidDecode
        return try {
            val imported = commitImport(
                LegacyImport(
                    key = key,
                    sourceVersion = SOURCE_VERSION,
                    importer = LegacyJsonImporter(decoded.import),
                ),
            )
            LegacySourceResult(
                key = key,
                status = if (imported) LegacySourceStatus.IMPORTED else LegacySourceStatus.ALREADY_IMPORTED,
                count = if (imported) decoded.count else 0,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LegacySourceResult(key, LegacySourceStatus.FAILED, error = e.message ?: "database_import_failed")
        }
    }

    private fun readOptional(file: File): SourceText {
        if (!file.exists()) return SourceText(exists = false)
        return try {
            SourceText(exists = true, text = file.readTextAtomically())
        } catch (e: Exception) {
            SourceText(exists = true, error = e.message ?: "read_failed")
        }
    }

    private fun collectOfflineFacts(): List<OfflineIndexLegacyImporter.ArtifactFacts> {
        val root = File(filesDir, "offline")
        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .map { archiveDir ->
                val original = File(archiveDir, "original.archive")
                OfflineIndexLegacyImporter.ArtifactFacts(
                    arcid = archiveDir.name,
                    filePath = original.absolutePath,
                    exists = original.isFile,
                    byteSize = if (original.isFile) original.length() else 0L,
                    lastModified = if (original.isFile) original.lastModified() else 0L,
                    isOriginalArchive = true,
                )
            }
            .toList()
    }

    private sealed interface DecodedSource

    private data class InvalidDecode(val reason: String) : DecodedSource

    private data class ValidDecode(
        val count: Int,
        val import: suspend (ReaderDatabase) -> Unit,
    ) : DecodedSource

    private data class SourceText(
        val exists: Boolean,
        val text: String? = null,
        val error: String? = null,
    )

    companion object {
        const val HISTORY_KEY = "history.json"
        const val LOCAL_INDEX_KEY = "local/index.json"
        const val DOWNLOAD_TASKS_KEY = "download_manager/tasks.json"
        const val OFFLINE_INDEX_KEY = "offline/index.json"
        const val SOURCE_VERSION = 1
    }
}
