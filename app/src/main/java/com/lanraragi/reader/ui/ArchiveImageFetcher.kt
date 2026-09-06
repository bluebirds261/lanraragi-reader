package com.lanraragi.reader.ui

import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.ui.screens.ArchivePageModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.buffer
import okio.source

class ArchiveImageFetcher(
    private val data: ArchivePageModel,
    private val options: Options,
    private val diskCache: DiskCache?,
) : Fetcher {

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun fetch(): FetchResult? {
        // A Keyer cannot query SAF metadata without blocking Coil's main-thread interceptor.
        // Resolve the revision here on the fetcher dispatcher and never trust a URI-only key.
        val diskCacheKey = ArchiveFileReader.imageCacheKey(
            data.context,
            data.archiveUri,
            data.pageIndex,
        )
        val lockEntry = retainFetchLock(diskCacheKey)

        try {
            return lockEntry.mutex.withLock {
                readCached(diskCacheKey)?.let { return@withLock it }

                val images = ArchiveFileReader.getImages(data.context, data.archiveUri)
                if (data.pageIndex !in images.indices) return@withLock null
                val stream = ArchiveFileReader.openImageStream(
                    data.context,
                    data.archiveUri,
                    images[data.pageIndex].name,
                ) ?: return@withLock null

                val cache = diskCache
                if (cache == null || !options.diskCachePolicy.writeEnabled) {
                    return@withLock SourceResult(
                        source = ImageSource(stream.source().buffer(), data.context.applicationContext),
                        mimeType = null,
                        dataSource = DataSource.DISK,
                    )
                }

                val editor = cache.openEditor(diskCacheKey)
                if (editor == null) {
                    return@withLock SourceResult(
                        source = ImageSource(stream.source().buffer(), data.context.applicationContext),
                        mimeType = null,
                        dataSource = DataSource.DISK,
                    )
                }

                try {
                    stream.use { input ->
                        cache.fileSystem.write(editor.metadata) {}
                        cache.fileSystem.write(editor.data) {
                            writeAll(input.source())
                        }
                    }
                    val snapshot = editor.commitAndOpenSnapshot() ?: return@withLock null
                    SourceResult(
                        source = ImageSource(snapshot.data, cache.fileSystem, diskCacheKey, snapshot),
                        mimeType = null,
                        dataSource = DataSource.DISK,
                    )
                } catch (e: CancellationException) {
                    editor.abort()
                    throw e
                } catch (e: Exception) {
                    editor.abort()
                    throw e
                }
            }
        } finally {
            releaseFetchLock(diskCacheKey, lockEntry)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun readCached(diskCacheKey: String): SourceResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val cache = diskCache ?: return null
        val snapshot = cache.openSnapshot(diskCacheKey) ?: return null
        return SourceResult(
            source = ImageSource(snapshot.data, cache.fileSystem, diskCacheKey, snapshot),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<ArchivePageModel> {
        override fun create(
            data: ArchivePageModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ArchiveImageFetcher(data, options, imageLoader.diskCache)
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
