package com.lanraragi.reader.data.reader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.OfflineCacheManager
import com.lanraragi.reader.data.download.ArtifactLease
import com.lanraragi.reader.data.download.SavedArtifactRepository
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** A stable model understood by a reader image loader, without retaining an input stream. */
sealed interface PageModel {
    val index: Int
    val revision: String
    val cacheKey: String

    /** A page served by LANraragi. [url] is already an absolute, interceptor-safe URL. */
    data class RemotePage(
        val url: String,
        override val index: Int,
        val thumbnail: Boolean,
        override val revision: String,
    ) : PageModel {
        override val cacheKey: String get() = "remote:$revision:$index:${if (thumbnail) "thumb" else "full"}"
    }

    /** An entry inside a local archive or SAF folder. The stream is opened per request by the loader. */
    data class LocalEntry(
        val uri: Uri,
        val entryName: String,
        override val index: Int,
        val thumbnail: Boolean,
        override val revision: String,
    ) : PageModel {
        override val cacheKey: String get() = "local:$revision:$index:${if (thumbnail) "thumb" else "full"}"
    }
}

data class PageSourceSnapshot(
    val pageCount: Int,
    val revision: String,
)

class PageSourceUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reader page source. Page indexes are zero based throughout this API; server progress conversion stays
 * at the repository boundary. Implementations are refreshable and never retain consumed streams.
 */
interface PageSource {
    val identity: ArchiveIdentity
    val pageCount: Int
    val revision: String

    suspend fun refresh(): PageSource

    fun pageModel(index: Int): PageModel

    fun thumbnailModel(index: Int): PageModel

    suspend fun recover(): PageSource = refresh()

    fun close() = Unit

    fun snapshot(): PageSourceSnapshot = PageSourceSnapshot(pageCount, revision)
}

fun interface RemotePageLoader {
    suspend fun load(arcid: String): List<String>
}

/** Remote LANraragi pages. The loader indirection keeps this source deterministic in JVM tests. */
class RemotePageSource(
    override val identity: ArchiveIdentity.Remote,
    private val loader: RemotePageLoader,
) : PageSource {
    constructor(arcid: String, loader: RemotePageLoader) : this(ArchiveIdentity.Remote(arcid), loader)

    constructor(repository: LanraragiRepository, arcid: String) : this(
        arcid,
        RemotePageLoader { repository.getPageUrls(arcid) },
    )

    private var urls: List<String> = emptyList()

    override val pageCount: Int get() = urls.size
    override var revision: String = unloadedRevision(identity.sourceKey)
        private set

    override suspend fun refresh(): PageSource = withContext(Dispatchers.IO) {
        val loaded = loader.load(identity.arcid)
            .map(String::trim)
            .filter(String::isNotEmpty)
        urls = loaded
        revision = revisionFor(identity.sourceKey, loaded)
        this@RemotePageSource
    }

    override fun pageModel(index: Int): PageModel {
        val url = urls.requireIndex(index)
        return PageModel.RemotePage(url, index, thumbnail = false, revision = revision)
    }

    override fun thumbnailModel(index: Int): PageModel {
        urls.requireIndex(index)
        return PageModel.RemotePage(
            url = com.lanraragi.reader.data.api.ApiClient.pageThumbnailUrl(identity.arcid, index),
            index = index,
            thumbnail = true,
            revision = revision,
        )
    }
}

/** A complete original archive saved by [OfflineCacheManager]. */
class SavedArchivePageSource(
    private val context: Context,
    private val offlineCache: OfflineCacheManager,
    override val identity: ArchiveIdentity.Remote,
    private val savedArtifacts: SavedArtifactRepository? = null,
) : ArchiveBackedPageSource(context) {
    constructor(
        context: Context,
        offlineCache: OfflineCacheManager,
        arcid: String,
        savedArtifacts: SavedArtifactRepository? = null,
    ) : this(
        context,
        offlineCache,
        ArchiveIdentity.Remote(arcid),
        savedArtifacts,
    )

    private var artifactLease: ArtifactLease? = null

    override suspend fun sourceUri(): Uri = withContext(Dispatchers.IO) {
        val file = offlineCache.archiveFile(identity.arcid)
        if (!file.isFile || file.length() <= 0L) {
            throw PageSourceUnavailableException("已保存档案不可用: ${identity.arcid}")
        }
        if (artifactLease == null) {
            val artifact = savedArtifacts?.findBySource(
                com.lanraragi.reader.data.download.DownloadSourceIdentity(identity.arcid, identity.serverScope),
            )
            artifactLease = artifact?.let { savedArtifacts.acquire(it.artifactKey) }
        }
        Uri.fromFile(file)
    }

    override fun close() {
        artifactLease?.close()
        artifactLease = null
    }
}

/** A ZIP/CBZ/RAR/CBR archive selected through Android's Storage Access Framework. */
class SafArchivePageSource(
    private val context: Context,
    override val identity: ArchiveIdentity.LocalSaf,
) : ArchiveBackedPageSource(context) {
    constructor(context: Context, uri: Uri) : this(context, ArchiveIdentity.LocalSaf(uri.toString()))

    override suspend fun sourceUri(): Uri = withContext(Dispatchers.IO) {
        val uri = Uri.parse(identity.uri)
        if (SafPageSourceSupport.isDirectory(context, uri)) {
            throw PageSourceUnavailableException("SAF 资源是文件夹而非归档: $uri")
        }
        uri
    }
}

/** An image folder selected through Android's Storage Access Framework. */
class SafFolderPageSource(
    private val context: Context,
    override val identity: ArchiveIdentity.LocalSaf,
) : ArchiveBackedPageSource(context) {
    constructor(context: Context, uri: Uri) : this(context, ArchiveIdentity.LocalSaf(uri.toString()))

    override suspend fun sourceUri(): Uri = withContext(Dispatchers.IO) {
        val uri = Uri.parse(identity.uri)
        if (!SafPageSourceSupport.isDirectory(context, uri)) {
            throw PageSourceUnavailableException("SAF 资源不是文件夹: $uri")
        }
        uri
    }
}

/** Shared local archive/folder implementation backed by ArchiveFileReader's bounded source cache. */
abstract class ArchiveBackedPageSource(
    private val context: Context,
) : PageSource {
    abstract override val identity: ArchiveIdentity

    private var entries: List<ArchiveFileReader.ArchiveEntry> = emptyList()
    private var loadedUri: Uri? = null
    override val pageCount: Int get() = entries.size
    private var revisionValue: String = "unloaded"
    override val revision: String get() = revisionValue

    protected abstract suspend fun sourceUri(): Uri

    override suspend fun refresh(): PageSource = withContext(Dispatchers.IO) {
        val uri = sourceUri()
        val loaded = ArchiveFileReader.getImages(context, uri)
        loadedUri = uri
        entries = loaded
        revisionValue = revisionFor(
            identity.sourceKey,
            listOf(ArchiveFileReader.sourceRevision(context, uri)) + loaded.map { it.name },
        )
        this@ArchiveBackedPageSource
    }

    override fun pageModel(index: Int): PageModel {
        val entry = entries.requireIndex(index)
        return PageModel.LocalEntry(
            uri = loadedUri ?: Uri.parse(identity.uriOrNull() ?: error("Local source URI missing")),
            entryName = entry.name,
            index = index,
            thumbnail = false,
            revision = revision,
        )
    }

    override fun thumbnailModel(index: Int): PageModel {
        val entry = entries.requireIndex(index)
        return PageModel.LocalEntry(
            uri = loadedUri ?: Uri.parse(identity.uriOrNull() ?: error("Local source URI missing")),
            entryName = entry.name,
            index = index,
            thumbnail = true,
            revision = revision,
        )
    }

    private fun ArchiveIdentity.uriOrNull(): String? = (this as? ArchiveIdentity.LocalSaf)?.uri
}

/** Selects the first usable local source, then falls back to remote pages. */
class PageSourceResolver(
    internal val context: Context,
    private val repository: LanraragiRepository,
    private val offlineCache: OfflineCacheManager? = null,
    private val savedArtifacts: SavedArtifactRepository? = null,
) {
    suspend fun resolve(arcid: String, localUri: Uri? = null, serverScope: String? = null): PageSource {
        require(arcid.isNotBlank()) { "arcid must not be blank" }
        val remoteIdentity = ArchiveIdentity.Remote(arcid, serverScope)

        offlineCache?.let { cache ->
            val file = cache.archiveFile(arcid)
            if (file.isFile && file.length() > 0L) {
                val source = SavedArchivePageSource(context, cache, remoteIdentity, savedArtifacts)
                return try {
                    source.refresh()
                } catch (error: Throwable) {
                    source.close()
                    throw error
                }
            }
        }

        if (localUri != null) {
            val localSource = if (SafPageSourceSupport.isDirectory(context, localUri)) {
                SafFolderPageSource(context, localUri)
            } else {
                SafArchivePageSource(context, localUri)
            }
            return localSource.refresh()
        }

        return RemotePageSource(
            remoteIdentity,
            RemotePageLoader { repository.getPageUrls(it) },
        ).refresh()
    }
}

internal object SafPageSourceSupport {
    fun isDirectory(context: Context, uri: Uri): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.isDirectory == true }.getOrDefault(false) ||
            runCatching { DocumentFile.fromTreeUri(context, uri)?.isDirectory == true }.getOrDefault(false)
}

private fun <T> List<T>.requireIndex(index: Int): T = getOrElse(index) {
    throw IndexOutOfBoundsException("page index $index outside 0 until $size")
}

private fun unloadedRevision(sourceKey: String): String = "${sourceKey}:unloaded"

private fun revisionFor(sourceKey: String, values: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(values.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "$sourceKey:$digest"
}
