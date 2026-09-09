package com.lanraragi.reader.data.assets

import java.net.URI
import java.util.Locale

/**
 * Identity of a cover image, independent of the transport used to obtain it.
 *
 * The cache key is deliberately derived only from stable inputs. In particular,
 * remote covers do not use request time or job ids, so a regenerated cover can
 * invalidate the previous image by advancing [RemoteCover.revision].
 */
sealed interface CoverModel {
    val cacheKey: String

    data class RemoteCover(
        val serverKey: String,
        val arcid: String,
        val revision: Long,
    ) : CoverModel {
        val normalizedServerKey: String
            get() = normalizeServerKey(serverKey)

        override val cacheKey: String
            get() = buildString {
                append("remote-cover|server=")
                append(keyPart(normalizedServerKey))
                append("|arcid=")
                append(keyPart(arcid.trim()))
                append("|revision=")
                append(revision)
            }
    }

    data class SavedCover(
        val filePath: String,
        val revision: Long,
    ) : CoverModel {
        val normalizedPath: String
            get() = normalizeLocalPath(filePath)

        override val cacheKey: String
            get() = buildString {
                append("saved-cover|path=")
                append(keyPart(normalizedPath))
                append("|revision=")
                append(revision)
            }
    }

    data class ArchiveEntryCover(
        val uri: String,
        val entry: String,
        val fingerprint: String,
    ) : CoverModel {
        override val cacheKey: String
            get() = buildString {
                append("archive-entry-cover|uri=")
                append(keyPart(normalizeLocalUri(uri)))
                append("|entry=")
                append(keyPart(entry.trim()))
                append("|fingerprint=")
                append(keyPart(fingerprint.trim()))
            }
    }

    data class FolderCover(
        val uri: String,
        val fingerprint: String,
    ) : CoverModel {
        override val cacheKey: String
            get() = buildString {
                append("folder-cover|uri=")
                append(keyPart(normalizeLocalUri(uri)))
                append("|fingerprint=")
                append(keyPart(fingerprint.trim()))
            }
    }
}

/**
 * Canonicalizes the server identity used in image cache keys.
 *
 * Scheme and host names are case-insensitive, while path segments are left
 * case-sensitive. A trailing slash is not significant for a server base URL.
 * User credentials, query parameters, and fragments are deliberately excluded
 * so private connection details never become part of an image cache key.
 * Invalid or non-URL keys still receive deterministic whitespace/slash cleanup.
 */
fun normalizeServerKey(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""

    val parsed = runCatching { URI(trimmed) }.getOrNull()
    if (parsed == null || parsed.scheme == null) {
        return normalizeLocalUri(trimmed).trimEnd('/')
    }

    val scheme = parsed.scheme.lowercase(Locale.ROOT)
    val authority = parsed.host?.let { host ->
        buildString {
            append(host.lowercase(Locale.ROOT))
            val isDefaultPort = (scheme == "http" && parsed.port == 80) ||
                (scheme == "https" && parsed.port == 443)
            if (parsed.port >= 0 && !isDefaultPort) {
                append(':')
                append(parsed.port)
            }
        }
    } ?: parsed.rawAuthority
        ?.substringAfterLast('@')
        ?.lowercase(Locale.ROOT)

    return buildString {
        append(scheme)
        append("://")
        authority?.let(::append)
        append(parsed.rawPath.orEmpty().trimEnd('/'))
    }.trimEnd('/')
}

/** Normalizes lexical local paths without touching the filesystem. */
fun normalizeLocalPath(value: String): String = normalizeLocalUri(value)

/** Normalizes persisted URI identity while preserving URI case and encoding. */
fun normalizeLocalUri(value: String): String = value.trim().replace('\\', '/')

/**
 * Length-prefixing prevents delimiters inside user-controlled values from
 * creating cache-key collisions while keeping each component inspectable.
 */
private fun keyPart(value: String): String = "${value.length}:$value"
