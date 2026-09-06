package com.lanraragi.reader.data

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache

/** Extends Coil's existing clear-cache action to include local archive source copies. */
@OptIn(ExperimentalCoilApi::class)
class ArchiveAwareDiskCache(
    delegate: DiskCache,
    context: Context,
) : DiskCache by delegate {
    private val diskCache = delegate
    private val applicationContext = context.applicationContext

    override fun clear() {
        diskCache.clear()
        ArchiveFileReader.clearCache(applicationContext)
    }
}
