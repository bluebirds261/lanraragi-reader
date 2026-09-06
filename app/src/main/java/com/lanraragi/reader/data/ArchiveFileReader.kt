package com.lanraragi.reader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive as RarArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/** Reads images from local folders and ZIP/CBZ/RAR/CBR archives. */
object ArchiveFileReader {

    private const val SOURCE_CACHE_DIRECTORY = "archive_sources"
    private const val MAX_SOURCE_FILES = 8
    private const val MAX_SOURCE_BYTES = 512L * 1024 * 1024
    private const val MAX_IMAGE_LISTS = 64

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val RAR_MIME_TYPES = setOf(
        "application/vnd.rar",
        "application/x-rar",
        "application/x-rar-compressed",
        "application/x-cbr",
    )

    data class ArchiveEntry(val name: String, val index: Int)

    private data class SourceIdentity(
        val fingerprint: String,
        val displayName: String,
    )

    private class SourceLease(
        val file: File,
        private val onClose: (() -> Unit)? = null,
    ) : AutoCloseable {
        private var closed = false

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            onClose?.invoke()
        }
    }

    private class LeaseInputStream(
        input: InputStream,
        private var closeResources: (() -> Unit)?,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                closeResources?.invoke()
                closeResources = null
            }
        }
    }

    private class SourceLockEntry(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )

    private val sourceLockGuard = Any()
    private val sourceLocks = mutableMapOf<String, SourceLockEntry>()
    private val sourceStateGuard = Any()
    private val activeSourceFiles = mutableMapOf<String, Int>()
    private val pendingSourceDeletes = mutableSetOf<String>()
    private val knownCacheDirectories = ConcurrentHashMap.newKeySet<File>()
    private val imageListCache = object : LinkedHashMap<String, List<ArchiveEntry>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<ArchiveEntry>>,
        ): Boolean = size > MAX_IMAGE_LISTS
    }

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** Stable key for Coil's bounded disk cache. Source metadata invalidates replaced archives. */
    suspend fun imageCacheKey(context: Context, uri: Uri, pageIndex: Int): String =
        withContext(Dispatchers.IO) {
            val identity = sourceIdentity(context.applicationContext, uri)
            "local-archive-page-v2:${identity.fingerprint}:$pageIndex"
        }

    /** Gets image entries in case-insensitive filename order. */
    suspend fun getImages(context: Context, uri: Uri): List<ArchiveEntry> =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val identity = sourceIdentity(appContext, uri)
            synchronized(imageListCache) {
                imageListCache[identity.fingerprint]?.let { return@withContext it }
            }

            localDirectory(uri)?.let { directory ->
                val entries = directory.listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile && isImage(it.name) }
                    ?.map(File::getName)
                    ?.toList()
                    .orEmpty()
                    .toEntries()
                cacheImageList(identity.fingerprint, entries)
                return@withContext entries
            }

            listSafDirectoryImages(appContext, uri)?.let { names ->
                val entries = names.toEntries()
                cacheImageList(identity.fingerprint, entries)
                return@withContext entries
            }

            val lease = acquireArchiveFile(appContext, uri, identity) ?: return@withContext emptyList()
            val names = mutableListOf<String>()
            try {
                if (isRarArchive(appContext, uri, identity.displayName)) {
                    val archive = RarArchive(lease.file)
                    try {
                        archive.fileHeaders.forEach { header ->
                            if (!header.isDirectory && isImage(header.fileName)) names += header.fileName
                        }
                    } finally {
                        archive.close()
                    }
                } else {
                    ZipFile(lease.file).use { zipFile ->
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && isImage(entry.name)) names += entry.name
                        }
                    }
                }
            } finally {
                lease.close()
            }

            names.toEntries().also { cacheImageList(identity.fingerprint, it) }
        }

    /**
     * Opens a fresh image stream. The returned stream owns any archive handle and must be closed.
     * Consumed streams are never retained or reused.
     */
    suspend fun openImageStream(
        context: Context,
        uri: Uri,
        entryName: String,
    ): InputStream? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        localDirectory(uri)?.let { directory ->
            val image = File(directory, entryName)
            if (image.isFile) return@withContext image.inputStream()
        }

        findSafDirectoryImage(appContext, uri, entryName)?.let { imageUri ->
            return@withContext appContext.contentResolver.openInputStream(imageUri)
        }

        val identity = sourceIdentity(appContext, uri)
        val lease = acquireArchiveFile(appContext, uri, identity) ?: return@withContext null
        try {
            if (isRarArchive(appContext, uri, identity.displayName)) {
                val archive = RarArchive(lease.file)
                val header = archive.fileHeaders.firstOrNull { it.fileName == entryName }
                if (header == null) {
                    archive.close()
                    lease.close()
                    return@withContext null
                }
                return@withContext LeaseInputStream(archive.getInputStream(header)) {
                    try {
                        archive.close()
                    } finally {
                        lease.close()
                    }
                }
            }

            val zipFile = ZipFile(lease.file)
            val entry = zipFile.getEntry(entryName)
            if (entry == null) {
                zipFile.close()
                lease.close()
                return@withContext null
            }
            return@withContext LeaseInputStream(zipFile.getInputStream(entry)) {
                try {
                    zipFile.close()
                } finally {
                    lease.close()
                }
            }
        } catch (e: CancellationException) {
            lease.close()
            throw e
        } catch (_: Exception) {
            lease.close()
            null
        }
    }

    /** Clears both in-memory indexes and all persisted archive source copies. */
    fun clearCache(context: Context) {
        knownCacheDirectories += sourceCacheDirectory(context.applicationContext)
        clearCache()
    }

    fun clearCache() {
        synchronized(imageListCache) { imageListCache.clear() }
        knownCacheDirectories.forEach { directory ->
            directory.listFiles()?.forEach { file ->
                val sourcePath = file.absolutePath.removeSuffix(".partial")
                synchronized(sourceLockGuard) {
                    val inUse = sourceLocks[sourcePath]?.users?.let { it > 0 } == true ||
                        synchronized(sourceStateGuard) { activeSourceFiles.containsKey(sourcePath) }
                    if (inUse) {
                        synchronized(sourceStateGuard) { pendingSourceDeletes += sourcePath }
                    } else {
                        runCatching { file.delete() }
                    }
                }
            }
            runCatching { directory.delete() }
        }
    }

    private suspend fun acquireArchiveFile(
        context: Context,
        uri: Uri,
        identity: SourceIdentity,
    ): SourceLease? {
        if (uri.scheme == "file") {
            val source = File(uri.path.orEmpty())
            if (source.isFile) return SourceLease(source)
        }

        val directory = sourceCacheDirectory(context)
        knownCacheDirectories += directory
        val cacheFile = File(directory, "${sha256(identity.fingerprint)}.archive")
        val lockEntry = retainSourceLock(cacheFile.absolutePath)

        try {
            return lockEntry.mutex.withLock {
                if (!cacheFile.isFile || cacheFile.length() == 0L) {
                    directory.mkdirs()
                    val partial = File(directory, "${cacheFile.name}.partial")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            partial.outputStream().buffered().use(input::copyTo)
                        } ?: return@withLock null
                        if (!partial.renameTo(cacheFile)) {
                            partial.copyTo(cacheFile, overwrite = true)
                            partial.delete()
                        }
                    } catch (e: CancellationException) {
                        partial.delete()
                        throw e
                    } catch (_: Exception) {
                        partial.delete()
                        return@withLock null
                    }
                }

                cacheFile.setLastModified(System.currentTimeMillis())
                markSourceActive(cacheFile)
                trimSourceCache(directory, cacheFile)
                SourceLease(cacheFile) { releaseSource(cacheFile, directory) }
            }
        } finally {
            releaseSourceLock(cacheFile.absolutePath, lockEntry)
        }
    }

    private fun markSourceActive(file: File) {
        synchronized(sourceStateGuard) {
            activeSourceFiles[file.absolutePath] = (activeSourceFiles[file.absolutePath] ?: 0) + 1
        }
    }

    private fun releaseSource(file: File, directory: File) {
        val deleteNow = synchronized(sourceStateGuard) {
            val remaining = (activeSourceFiles[file.absolutePath] ?: 1) - 1
            if (remaining <= 0) activeSourceFiles.remove(file.absolutePath)
            else activeSourceFiles[file.absolutePath] = remaining
            remaining <= 0 && pendingSourceDeletes.remove(file.absolutePath)
        }
        if (deleteNow) {
            file.delete()
            directory.delete()
            return
        }
        file.setLastModified(System.currentTimeMillis())
        trimSourceCache(directory, file)
    }

    private fun trimSourceCache(directory: File, protectedFile: File) {
        synchronized(sourceStateGuard) {
            val files = directory.listFiles()
                ?.filter { it.isFile && it.extension == "archive" }
                ?.sortedBy(File::lastModified)
                ?.toMutableList()
                ?: return
            var totalBytes = files.sumOf(File::length)

            val iterator = files.iterator()
            while ((files.size > MAX_SOURCE_FILES || totalBytes > MAX_SOURCE_BYTES) && iterator.hasNext()) {
                val candidate = iterator.next()
                if (
                    candidate == protectedFile ||
                    activeSourceFiles.containsKey(candidate.absolutePath) ||
                    isSourceLocked(candidate.absolutePath)
                ) continue
                val length = candidate.length()
                if (candidate.delete()) {
                    totalBytes -= length
                    iterator.remove()
                }
            }
        }
    }

    private fun retainSourceLock(path: String): SourceLockEntry = synchronized(sourceLockGuard) {
        sourceLocks.getOrPut(path, ::SourceLockEntry).also { it.users++ }
    }

    private fun releaseSourceLock(path: String, entry: SourceLockEntry) {
        val deleteNow = synchronized(sourceLockGuard) {
            entry.users--
            if (entry.users == 0) sourceLocks.remove(path, entry)
            entry.users == 0
        } && synchronized(sourceStateGuard) {
            !activeSourceFiles.containsKey(path) && pendingSourceDeletes.remove(path)
        }
        if (deleteNow) {
            File(path).delete()
            File("$path.partial").delete()
        }
    }

    private fun isSourceLocked(path: String): Boolean = synchronized(sourceLockGuard) {
        sourceLocks[path]?.users?.let { it > 0 } == true
    }

    private fun sourceCacheDirectory(context: Context): File =
        File(context.cacheDir, SOURCE_CACHE_DIRECTORY)

    private fun sourceIdentity(context: Context, uri: Uri): SourceIdentity {
        if (uri.scheme == "file") {
            val file = File(uri.path.orEmpty())
            return SourceIdentity(
                fingerprint = "${uri}|${file.length()}|${file.lastModified()}",
                displayName = file.name,
            )
        }

        val document = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
            ?: runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        val name = runCatching { document?.name }.getOrNull().orEmpty()
        val length = runCatching { document?.length() }.getOrNull() ?: 0L
        val modified = runCatching { document?.lastModified() }.getOrNull() ?: 0L
        return SourceIdentity(
            fingerprint = "$uri|$length|$modified",
            displayName = name.ifBlank { uri.lastPathSegment.orEmpty() },
        )
    }

    private fun localDirectory(uri: Uri): File? {
        if (uri.scheme != "file") return null
        return File(uri.path.orEmpty()).takeIf(File::isDirectory)
    }

    /** Returns null for non-directories and a possibly empty list for SAF directories. */
    private fun listSafDirectoryImages(context: Context, uri: Uri): List<String>? {
        if (uri.scheme != "content" || !isSafDirectory(context, uri)) return null
        return querySafChildren(context, uri)
            .filter { (_, name) -> isImage(name) }
            .map { it.second }
    }

    private fun findSafDirectoryImage(context: Context, uri: Uri, entryName: String): Uri? {
        if (uri.scheme != "content" || !isSafDirectory(context, uri)) return null
        return querySafChildren(context, uri).firstOrNull { it.second == entryName }?.first
    }

    private fun isSafDirectory(context: Context, uri: Uri): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.isDirectory == true }.getOrDefault(false) ||
            runCatching {
                !uri.path.orEmpty().contains("/document/") &&
                    DocumentFile.fromTreeUri(context, uri)?.isDirectory == true
            }.getOrDefault(false)

    private fun querySafChildren(context: Context, parentUri: Uri): List<Pair<Uri, String>> {
        val parentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(parentUri) }.getOrNull()
            ?: return emptyList()
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
        }.getOrNull() ?: return emptyList()
        val result = mutableListOf<Pair<Uri, String>>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    if (cursor.getString(typeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(
                        parentUri,
                        cursor.getString(idColumn),
                    )
                    result += childUri to cursor.getString(nameColumn).orEmpty()
                }
            }
        }
        return result
    }

    private fun isRarArchive(context: Context, uri: Uri, displayName: String): Boolean {
        val mimeType = runCatching { context.contentResolver.getType(uri)?.lowercase() }.getOrNull()
        val extension = displayName.substringAfterLast('.', uri.lastPathSegment.orEmpty().substringAfterLast('.', ""))
            .lowercase()
        return mimeType in RAR_MIME_TYPES || extension == "rar" || extension == "cbr"
    }

    private fun cacheImageList(key: String, entries: List<ArchiveEntry>) {
        synchronized(imageListCache) { imageListCache[key] = entries }
    }

    private fun List<String>.toEntries(): List<ArchiveEntry> =
        sortedWith(String.CASE_INSENSITIVE_ORDER)
            .mapIndexed { index, name -> ArchiveEntry(name, index) }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
