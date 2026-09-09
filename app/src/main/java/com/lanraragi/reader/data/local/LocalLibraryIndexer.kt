package com.lanraragi.reader.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.db.LocalArchiveEntity
import com.lanraragi.reader.data.db.LegacyImportStateEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.local.incremental.FileSafSnapshotStore
import com.lanraragi.reader.data.local.incremental.IncrementalScanner
import com.lanraragi.reader.data.local.incremental.SafNode
import com.lanraragi.reader.data.local.incremental.SafSnapshot
import com.lanraragi.reader.data.local.incremental.SafSnapshotStore
import com.lanraragi.reader.data.local.incremental.isArchive
import com.lanraragi.reader.data.local.incremental.isImage
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Room-backed SAF local archive index.
 *
 * This class deliberately owns only the local-index boundary. UI and legacy
 * LocalScanManager can consume [observeArchives] without knowing how SAF
 * traversal or stable identities are implemented.
 */
class LocalLibraryIndexer(
    private val context: Context,
    private val database: ReaderDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val snapshotStore: SafSnapshotStore = FileSafSnapshotStore(
        File(context.filesDir, "local/saf-snapshot.json"),
    ),
) {
    private val scanMutex = Mutex()
    private val snapshotAdapter = SafTreeSnapshotAdapter(context)
    private val incrementalScanner = IncrementalScanner()

    fun observeArchives(): Flow<List<LocalArchiveEntity>> =
        database.localArchiveDao().observeAll()

    /** Import one legacy snapshot exactly once; the source file remains untouched. */
    suspend fun importLegacy(
        entries: Collection<LocalArchiveEntity>,
        importKey: String = "local/index.json",
        sourceVersion: Int = 1,
    ): Boolean = withContext(Dispatchers.IO) {
        require(importKey.isNotBlank()) { "importKey must not be blank" }
        scanMutex.withLock {
            database.withTransaction {
                val marker = database.legacyImportStateDao().find(importKey)
                if (marker != null && marker.sourceVersion >= sourceVersion) {
                    return@withTransaction false
                }
                if (entries.isNotEmpty()) database.localArchiveDao().upsertAll(entries.toList())
                database.legacyImportStateDao().upsert(
                    LegacyImportStateEntity(
                        importKey = importKey,
                        sourceVersion = sourceVersion,
                        completedAt = clock(),
                    ),
                )
                true
            }
        }
    }

    suspend fun scan(
        roots: Collection<String>,
        force: Boolean = false,
    ): ScanOutcome = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            coroutineContext.ensureActive()
            val normalizedRoots = roots.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSortedSet()
            val previous = snapshotStore.load()
            val existing = database.localArchiveDao().observeAll().first()
            val capture = snapshotAdapter.capture(normalizedRoots, previous.roots, force)
            val delta = incrementalScanner.scan(previous.roots, capture.roots)
            val currentCandidates = archiveCandidates(capture.roots)
            val previousCandidates = archiveCandidates(previous.roots)
            val existingByKey = existing.associateBy(LocalArchiveEntity::sourceKey)
            val changedIdentities = (delta.added + delta.changed).mapTo(mutableSetOf()) { it.identity }
            val changedCandidates = currentCandidates.asSequence()
                .filter { candidate ->
                    candidate.identity in changedIdentities ||
                        existingByKey[candidate.sourceKey]?.availability != Availability.AVAILABLE.value
                }
                .toList()
            val availableRows = buildList {
                for (candidate in changedCandidates) {
                    add(candidate.toEntity(context, clock()))
                }
            }

            // Items missing from a successful snapshot, and all children under
            // a revoked root, become unavailable. We retain their progress and
            // metadata rather than deleting their Room row.
            val previousKeys = previousCandidates.mapTo(mutableSetOf()) { it.sourceKey }
            val currentKeys = currentCandidates.mapTo(mutableSetOf()) { it.sourceKey }
            val now = clock()
            val stale = existing.asSequence()
                .filter { it.sourceKey in previousKeys }
                .filter { it.sourceKey !in currentKeys }
                .filter { it.availability != Availability.UNAVAILABLE.value }
                .map { it.copy(availability = Availability.UNAVAILABLE.value, lastVerifiedAt = now) }
                .toList()
            val rows = (availableRows + stale)
                .distinctBy(LocalArchiveEntity::sourceKey)

            database.withTransaction {
                if (rows.isNotEmpty()) database.localArchiveDao().upsertAll(rows)
            }
            snapshotStore.save(SafSnapshot(roots = capture.roots, fallbacks = capture.fallbacks))
            ScanOutcome(
                roots = normalizedRoots,
                indexed = currentCandidates.size,
                markedUnavailable = stale.size,
                unreadableRoots = capture.roots.count { !it.readable },
                skipped = !force && rows.isEmpty() && delta.added.isEmpty() && delta.changed.isEmpty() && delta.removed.isEmpty(),
            )
        }
    }

    suspend fun markUnavailable(sourceKeys: Collection<String>): Int = withContext(Dispatchers.IO) {
        val keys = sourceKeys.toSet()
        if (keys.isEmpty()) return@withContext 0
        val current = database.localArchiveDao().observeAll().first()
        val now = clock()
        val changed = current.filter { it.sourceKey in keys && it.availability != Availability.UNAVAILABLE.value }
            .map { it.copy(availability = Availability.UNAVAILABLE.value, lastVerifiedAt = now) }
        database.withTransaction {
            if (changed.isNotEmpty()) database.localArchiveDao().upsertAll(changed)
        }
        changed.size
    }

    /** Re-check one SAF URI and restore availability when permission is valid. */
    suspend fun reauthorize(uri: String): ReauthorizeResult = withContext(Dispatchers.IO) {
        require(uri.isNotBlank()) { "uri must not be blank" }
        val sourceKey = ArchiveIdentity.LocalSaf(uri).sourceKey
        val current = database.localArchiveDao().observeAll().first()
            .firstOrNull { it.sourceKey == sourceKey }
        val file = documentFor(uri)
        if (file == null || !file.exists() || !file.canRead()) {
            if (current != null) markUnavailable(listOf(sourceKey))
            return@withContext ReauthorizeResult(sourceKey, available = false, entity = current)
        }
        val entity = describe(file, clock())
        database.withTransaction { database.localArchiveDao().upsertAll(listOf(entity)) }
        ReauthorizeResult(sourceKey, available = true, entity = entity)
    }

    private fun archiveCandidates(roots: Collection<SafNode>): List<ArchiveCandidate> = buildList {
        fun visit(node: SafNode) {
            if (!node.readable) return
            if (node.isArchive()) {
                add(ArchiveCandidate(node, directoryGallery = false))
                return
            }
            if (!node.directory) return
            val hasImage = node.children.any { it.readable && it.isImage() }
            val hasSubdirectory = node.children.any { it.readable && it.directory }
            if (hasImage && !hasSubdirectory) {
                add(ArchiveCandidate(node, directoryGallery = true))
            } else {
                node.children.forEach(::visit)
            }
        }
        roots.forEach(::visit)
    }

    private data class ArchiveCandidate(val node: SafNode, val directoryGallery: Boolean) {
        val identity: String get() = node.identity
        val sourceKey: String get() = ArchiveIdentity.LocalSaf(node.uri).sourceKey

        suspend fun toEntity(context: Context, verifiedAt: Long): LocalArchiveEntity {
            val entries = try {
                ArchiveFileReader.getImages(context, Uri.parse(node.uri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            val firstEntry = entries.firstOrNull()?.name
            return LocalArchiveEntity(
                sourceKey = sourceKey,
                uri = node.uri,
                title = node.name.ifBlank { "未知画廊" },
                fingerprint = node.fingerprint,
                pageCount = entries.size,
                coverEntry = if (directoryGallery) {
                    node.children.firstOrNull { it.name == firstEntry }?.uri
                } else {
                    firstEntry
                },
                availability = Availability.AVAILABLE.value,
                lastVerifiedAt = verifiedAt,
            )
        }
    }

    private fun documentFor(uri: String): DocumentFile? = try {
        DocumentFile.fromSingleUri(context, Uri.parse(uri))
            ?: DocumentFile.fromTreeUri(context, Uri.parse(uri))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun describe(file: DocumentFile, verifiedAt: Long): LocalArchiveEntity {
        val uri = file.uri.toString()
        val title = file.name?.takeIf(String::isNotBlank) ?: "未知画廊"
        val fingerprint = fingerprint(file)
        val entries = try {
            ArchiveFileReader.getImages(context, file.uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val firstEntry = entries.firstOrNull()?.name
        val firstDirectoryImageUri = if (file.isDirectory && firstEntry != null) {
            runCatching {
                file.listFiles().firstOrNull { child -> child.isFile && child.name == firstEntry }?.uri?.toString()
            }.getOrNull()
        } else null
        return LocalArchiveEntity(
            sourceKey = ArchiveIdentity.LocalSaf(uri).sourceKey,
            uri = uri,
            title = title,
            fingerprint = fingerprint,
            pageCount = entries.size,
            coverEntry = if (file.isDirectory) {
                firstDirectoryImageUri
            } else {
                firstEntry
            },
            availability = Availability.AVAILABLE.value,
            lastVerifiedAt = verifiedAt,
        )
    }

    private suspend fun fingerprint(file: DocumentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        suspend fun visit(node: DocumentFile) {
            coroutineContext.ensureActive()
            digest.update(node.uri.toString().toByteArray())
            digest.update(node.name.orEmpty().toByteArray())
            digest.update(byteArrayOf(if (node.isDirectory) 1 else 0))
            digest.update(node.length().toString().toByteArray())
            digest.update(node.lastModified().toString().toByteArray())
            if (node.isDirectory) {
                val children = runCatching { node.listFiles().sortedBy { it.uri.toString() } }.getOrNull()
                if (children == null) {
                    digest.update("unreadable".toByteArray())
                } else {
                    children.forEach { visit(it) }
                }
            }
        }
        visit(file)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isArchive(name: String): Boolean = name.lowercase().let {
        it.endsWith(".zip") || it.endsWith(".cbz") || it.endsWith(".rar") || it.endsWith(".cbr")
    }

    private fun isImage(name: String): Boolean = name.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") ||
            it.endsWith(".webp") || it.endsWith(".gif") || it.endsWith(".bmp")
    }
}

enum class Availability(val value: String) {
    AVAILABLE("AVAILABLE"),
    UNAVAILABLE("UNAVAILABLE"),
}

data class ScanOutcome(
    val roots: Set<String>,
    val indexed: Int,
    val markedUnavailable: Int,
    val unreadableRoots: Int,
    val skipped: Boolean,
)

data class ReauthorizeResult(
    val sourceKey: String,
    val available: Boolean,
    val entity: LocalArchiveEntity?,
)
