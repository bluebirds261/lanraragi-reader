package com.lanraragi.reader.data.download

import com.lanraragi.reader.data.ApiException
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.api.ApiClient
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Registers the protocol runners used by durable archive/cache tasks.
 *
 * `repository` is retained so existing call sites (AppContainer) stay source-compatible;
 * the single-page favorite ("收藏单页") task type was removed and no longer needs it.
 */
class LanraragiDownloadRunnerFactory(
    @Suppress("unused") private val repository: LanraragiRepository,
    private val contentResolver: android.content.ContentResolver? = null,
) {
    fun registry(): DownloadRunnerRegistry = DownloadRunnerRegistry.Builder()
        .register(DownloadTaskSpec.Archive::class.java, ArchiveRunner(contentResolver))
        .register(DownloadTaskSpec.Cache::class.java, ArchiveRunner(contentResolver))
        .build()

    private class ArchiveRunner(private val contentResolver: android.content.ContentResolver?) : DownloadTaskRunner {
        override suspend fun run(task: DurableDownloadTask, reporter: DownloadProgressReporter): DownloadRunResult =
            download(
                url = ApiClient.toAbsoluteUrl("/api/archives/${task.spec.source.archiveId}/download"),
                destination = task.spec.destination.value,
                existing = task.completedBytes,
                expectedRevision = task.spec.expectedRevision,
                expectedEntityTag = task.entityTag,
                contentResolver = contentResolver,
                reporter = reporter,
            )
    }
}

private suspend fun download(
    url: String,
    destination: String,
    existing: Long,
    expectedRevision: String? = null,
    expectedEntityTag: String? = null,
    contentResolver: android.content.ContentResolver? = null,
    reporter: DownloadProgressReporter,
): DownloadRunResult = withContext(Dispatchers.IO) {
    if (destination.startsWith("content://")) {
        return@withContext contentResolver?.let {
            downloadSaf(url, Uri.parse(destination), it, expectedRevision, expectedEntityTag, reporter)
        } ?: DownloadRunResult.Failed(DownloadFailure.Storage("SAF destination unavailable: ContentResolver not configured"))
    }
    val target = File(destination)
    val part = File("${target.absolutePath}.part")
    target.parentFile?.mkdirs()
    var offset = part.takeIf { it.isFile }?.length()?.coerceAtLeast(existing) ?: existing.coerceAtLeast(0L)
    if (offset > 0L && part.length() < offset) offset = part.length()
    val request = Request.Builder().url(url).apply {
        if (offset > 0L) header("Range", "bytes=$offset-")
    }.build()
    try {
        ApiClient.downloadClient.newCall(request).execute().use { response ->
            if (response.code == 416 && part.isFile) {
                val total = response.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
                if (total != null && part.length() == total && publishLocalPart(part, target, total)) {
                    return@withContext DownloadRunResult.Completed(total, total)
                }
                // A 416 is not proof of completion; discard the unverifiable partial.
                part.delete()
                return@withContext DownloadRunResult.Retryable(DownloadFailure.Network("Range rejected; partial reset"))
            }
            if (!response.isSuccessful && response.code != 206) {
                val failure = if (response.code == 429 || response.code >= 500) {
                    DownloadFailure.Http(response.code, "HTTP ${response.code}", response.header("Retry-After")?.toLongOrNull()?.times(1000))
                } else DownloadFailure.Http(response.code, "HTTP ${response.code}")
                return@withContext DownloadRunResult.Retryable(failure).takeIf { response.code == 429 || response.code >= 500 }
                    ?: DownloadRunResult.Failed(failure)
            }
            val append = response.code == 206 && offset > 0L
            val contentRange = response.header("Content-Range")
            if (response.code == 206) {
                val start = contentRange?.substringAfter("bytes ")?.substringBefore("-")?.toLongOrNull()
                if (start == null || start != offset) {
                    return@withContext DownloadRunResult.Failed(DownloadFailure.Http(206, "Invalid Content-Range for resumed download"))
                }
            }
            val responseTag = response.header("ETag")
            if (expectedEntityTag != null && expectedEntityTag != responseTag) {
                return@withContext DownloadRunResult.Failed(DownloadFailure.Unknown("Source revision changed during download"))
            }
            if (expectedRevision != null && expectedRevision != responseTag && expectedRevision != response.header("Last-Modified")) {
                return@withContext DownloadRunResult.Failed(DownloadFailure.Unknown("Source revision does not match expected revision"))
            }
            if (!append) {
                part.delete()
                offset = 0L
            }
            val body = response.body ?: return@withContext DownloadRunResult.Failed(DownloadFailure.Network("Empty download response"))
            val total = contentRange?.substringAfter("/")?.toLongOrNull()
                ?: body.contentLength().takeIf { it >= 0L }?.let { it + offset }
            body.byteStream().use { input ->
                RandomAccessFile(part, "rw").use { output ->
                    output.seek(offset)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var completed = offset
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        completed += count
                        reporter.report(completed, total, response.header("ETag"), response.header("Last-Modified"))
                    }
                }
            }
            if (!publishLocalPart(part, target, total ?: part.length())) {
                return@withContext DownloadRunResult.Failed(DownloadFailure.Storage("Unable to atomically publish download"))
            }
            DownloadRunResult.Completed(target.length(), total ?: target.length(), response.header("ETag"), response.header("Last-Modified"))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        DownloadRunResult.Retryable(DownloadFailure.Network(error.message ?: "Network interrupted"))
    } catch (error: ApiException) {
        DownloadRunResult.Failed(DownloadFailure.Unknown(error.message ?: "Download failed"))
    }
}

/**
 * A document URI has no portable replace operation.  Stage beside it, retain the original as a
 * backup, and only publish when the provider supports the required sibling create/rename calls.
 */
private suspend fun downloadSaf(
    url: String,
    destination: Uri,
    resolver: ContentResolver,
    expectedRevision: String?,
    expectedEntityTag: String?,
    reporter: DownloadProgressReporter,
): DownloadRunResult {
    val name = documentName(resolver, destination)
        ?: return DownloadRunResult.Failed(DownloadFailure.Storage("SAF destination has no display name"))
    val parent = safParent(destination)
        ?: return DownloadRunResult.Failed(DownloadFailure.Storage("SAF provider cannot create a sibling .part document"))
    return try {
        ApiClient.downloadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful || response.code != 200) {
                val failure = DownloadFailure.Http(response.code, "HTTP ${response.code}", response.retryAfterMs())
                return if (response.code == 429 || response.code >= 500) DownloadRunResult.Retryable(failure) else DownloadRunResult.Failed(failure)
            }
            val responseTag = response.header("ETag")
            if (expectedEntityTag != null && responseTag != null && expectedEntityTag != responseTag) {
                return DownloadRunResult.Failed(DownloadFailure.Unknown("Source revision changed during download"))
            }
            if (expectedRevision != null && expectedRevision != responseTag && expectedRevision != response.header("Last-Modified")) {
                return DownloadRunResult.Failed(DownloadFailure.Unknown("Source revision does not match expected revision"))
            }
            val backup = DocumentsContract.renameDocument(resolver, destination, "$name.backup")
                ?: return DownloadRunResult.Failed(DownloadFailure.Storage("SAF provider cannot stage the existing destination"))
            var part: Uri? = null
            var published = false
            try {
                part = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$name.part")
                    ?: return DownloadRunResult.Failed(DownloadFailure.Storage("SAF provider cannot create a .part document"))
                val body = response.body ?: return DownloadRunResult.Failed(DownloadFailure.Network("Empty download response"))
                val total = body.contentLength().takeIf { it >= 0L }
                var completed = 0L
                body.byteStream().use { input ->
                    resolver.openOutputStream(part, "w")?.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            completed += count
                            reporter.report(completed, total, responseTag, response.header("Last-Modified"))
                        }
                    } ?: return DownloadRunResult.Failed(DownloadFailure.Storage("SAF provider cannot open .part document"))
                }
                if (total != null && completed != total) {
                    return DownloadRunResult.Failed(DownloadFailure.Network("Incomplete SAF download"))
                }
                DocumentsContract.renameDocument(resolver, part, name)
                    ?: return DownloadRunResult.Failed(DownloadFailure.Storage("SAF provider cannot publish .part document"))
                part = null
                published = true
                DocumentsContract.deleteDocument(resolver, backup)
                DownloadRunResult.Completed(completed, total ?: completed, responseTag, response.header("Last-Modified"))
            } finally {
                part?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                // A failed staged write must not leave the caller without the original document.
                if (!published) runCatching { DocumentsContract.renameDocument(resolver, backup, name) }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        DownloadRunResult.Retryable(DownloadFailure.Network(error.message ?: "Network interrupted"))
    } catch (error: SecurityException) {
        DownloadRunResult.Failed(DownloadFailure.Storage("SAF permission was revoked"))
    } catch (error: Exception) {
        DownloadRunResult.Failed(DownloadFailure.Storage(error.message ?: "SAF staging failed"))
    }
}

private fun documentName(resolver: ContentResolver, document: Uri): String? = resolver.query(
    document,
    arrayOf(OpenableColumns.DISPLAY_NAME),
    null,
    null,
    null,
)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

private fun safParent(document: Uri): Uri? = runCatching {
    val documentId = DocumentsContract.getDocumentId(document)
    val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
    parentId.takeIf { it.isNotBlank() }?.let { authority ->
        document.authority?.let { DocumentsContract.buildDocumentUri(it, authority) }
    }
}.getOrNull()

private fun okhttp3.Response.retryAfterMs(): Long? = header("Retry-After")?.toLongOrNull()?.times(1_000L)

private fun publishLocalPart(part: File, target: File, expectedLength: Long): Boolean {
    if (!part.isFile || part.length() != expectedLength) return false
    target.parentFile?.mkdirs()
    return try {
        try {
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            val temp = File(target.path + ".publish")
            Files.copy(part.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (temp.length() != expectedLength) {
                temp.delete()
                return false
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            part.delete()
        }
        target.isFile && target.length() == expectedLength
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: Exception) {
        false
    }
}
