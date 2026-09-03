package com.lanraragi.reader.ui

import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.ui.screens.ArchivePageModel
import okio.buffer
import okio.source

class ArchiveImageFetcher(
    private val data: ArchivePageModel
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val images = ArchiveFileReader.getImages(data.context, data.archiveUri)
        if (data.pageIndex !in images.indices) return null
        
        val entryName = images[data.pageIndex].name
        val stream = ArchiveFileReader.openImageStream(data.context, data.archiveUri, entryName)
            ?: return null
        
        return SourceResult(
            source = coil.decode.ImageSource(stream.source().buffer(), data.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<ArchivePageModel> {
        override fun create(data: ArchivePageModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return ArchiveImageFetcher(data)
        }
    }
}
