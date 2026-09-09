package com.lanraragi.reader.ui

import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.ui.screens.ArchivePageModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.buffer
import okio.source

fun ArchivePageModel.asImageRequest(): ImageRequest = ImageRequest.Builder(context)
    .data(this)
    .memoryCacheKey(revision ?: "archive:${archiveUri}:$pageIndex:${if (thumbnail) "thumb" else "full"}")
    .diskCachePolicy(CachePolicy.DISABLED)
    .build()

class ArchiveImageFetcher(
    private val data: ArchivePageModel,
) : Fetcher {

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun fetch(): FetchResult? {
        val images = ArchiveFileReader.getImages(data.context, data.archiveUri)
        if (data.pageIndex !in images.indices) return null
        val entryName = images[data.pageIndex].name
        val revision = ArchiveFileReader.localArtifactRevision(data.context, data.archiveUri, entryName)
        val cover = data.thumbnail
        val cacheKey = "local-derived:${if (cover) "cover" else "page"}:${revision.value}"
        val lockEntry = retainFetchLock(cacheKey)

        try {
            return lockEntry.mutex.withLock {
                ArchiveFileReader.getDerivedImage(data.context, revision, cover)?.let { bytes ->
                    return@withLock byteSource(bytes)
                }
                val stream = ArchiveFileReader.openImageStream(
                    data.context,
                    data.archiveUri,
                    entryName,
                ) ?: return@withLock null
                try {
                    stream.use { input ->
                        val bytes = input.readBytes()
                        ArchiveFileReader.putDerivedImage(data.context, revision, cover, bytes)
                        byteSource(bytes)
                    }
                } catch (e: CancellationException) {
                    throw e
                }
            }
        } finally {
            releaseFetchLock(cacheKey, lockEntry)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun byteSource(bytes: ByteArray): SourceResult = SourceResult(
        source = ImageSource(Buffer().write(bytes), data.context.applicationContext),
        mimeType = null,
        dataSource = DataSource.DISK,
    )

    class Factory : Fetcher.Factory<ArchivePageModel> {
        override fun create(
            data: ArchivePageModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ArchiveImageFetcher(data)
    }

    private companion object {
        class FetchLockEntry(
            val mutex: Mutex = Mutex(),
            var users: Int = 0,
        )

        val fetchLockGuard = Any()
        val fetchLocks = mutableMapOf<String, FetchLockEntry>()

        fun retainFetchLock(key: String): FetchLockEntry = synchronized(fetchLockGuard) {
            fetchLocks.getOrPut(key, ::FetchLockEntry).also { it.users++ }
        }

        fun releaseFetchLock(key: String, entry: FetchLockEntry) {
            synchronized(fetchLockGuard) {
                entry.users--
                if (entry.users == 0) fetchLocks.remove(key, entry)
            }
        }
    }
}
