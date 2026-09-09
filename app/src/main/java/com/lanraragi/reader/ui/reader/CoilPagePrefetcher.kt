package com.lanraragi.reader.ui.reader

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lanraragi.reader.data.reader.PageModel
import com.lanraragi.reader.data.reader.PagePrefetcher
import com.lanraragi.reader.ui.screens.ArchivePageModel

class CoilPagePrefetcher(context: Context) : PagePrefetcher {
    private val appContext = context.applicationContext
    private val imageLoader = appContext.imageLoader

    override fun prefetch(model: PageModel): AutoCloseable {
        val request = ImageRequest.Builder(appContext)
            .data(
                when (model) {
                    is PageModel.RemotePage -> model.url
                    is PageModel.LocalEntry -> ArchivePageModel(
                        model.uri,
                        model.index,
                        appContext,
                        thumbnail = model.thumbnail,
                        revision = model.cacheKey,
                    )
                },
            )
            .memoryCacheKey(model.cacheKey)
            // Local ArchiveImageFetcher owns its bounded derived disk cache.
            .diskCachePolicy(if (model is PageModel.LocalEntry) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .build()
        val disposable = imageLoader.enqueue(request)
        return AutoCloseable { disposable.dispose() }
    }
}
