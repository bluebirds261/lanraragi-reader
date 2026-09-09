package com.lanraragi.reader.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lanraragi.reader.data.local.incremental.SafFallback
import com.lanraragi.reader.data.local.incremental.SafNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Reads only direct children of unchanged directories. Providers commonly
 * report a zero directory mtime, so those branches intentionally fall back to
 * a deep scan and leave a durable diagnostic in the snapshot sidecar.
 */
internal class SafTreeSnapshotAdapter(private val context: Context) {
    suspend fun capture(
        roots: Collection<String>,
        previous: Collection<SafNode>,
        force: Boolean,
    ): CaptureResult {
        val oldByIdentity = buildMap {
            fun visit(node: SafNode) {
                put(node.identity, node)
                if (node.directory) node.children.forEach(::visit)
            }
            previous.forEach(::visit)
        }
        val fallbacks = mutableListOf<SafFallback>()
        val nodes = roots.map { rootUri ->
            coroutineContext.ensureActive()
            val root = try {
                DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (root == null) {
                SafNode(rootUri, rootUri, rootUri, directory = true, readable = false)
            } else {
                captureNode(root, oldByIdentity[root.uri.toString()], force, fallbacks, listDirect = true)
            }
        }
        return CaptureResult(nodes, fallbacks)
    }

    private suspend fun captureNode(
        document: DocumentFile,
        previous: SafNode?,
        force: Boolean,
        fallbacks: MutableList<SafFallback>,
        listDirect: Boolean,
    ): SafNode {
        coroutineContext.ensureActive()
        val readable = try {
            document.exists() && document.canRead()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        val identity = document.uri.toString()
        val base = SafNode(
            identity = identity,
            uri = identity,
            name = document.name.orEmpty(),
            directory = document.isDirectory,
            mimeType = document.type,
            size = document.length(),
            lastModified = document.lastModified(),
            readable = readable,
        )
        if (!base.directory) return base
        if (!readable) return base.copy(children = previous?.children.orEmpty())

        val metadataReliable = base.lastModified > 0L
        if (!force && metadataReliable && previous != null && previous.metadataFingerprint == base.metadataFingerprint) {
            if (!listDirect) return base.copy(children = previous.children)
            // Root/directly changed branches list one level to find additions/removals.
            val direct = listFiles(document) ?: return base.copy(readable = false, children = previous.children)
            val oldChildren = previous.children.associateBy(SafNode::identity)
            return base.copy(children = direct.map { child ->
                captureNode(
                    child,
                    oldChildren[child.uri.toString()],
                    force = false,
                    fallbacks = fallbacks,
                    listDirect = false,
                )
            })
        }

        if (!metadataReliable) {
            fallbacks += SafFallback(identity, "directory_last_modified_unavailable")
        }
        val direct = listFiles(document) ?: return base.copy(readable = false, children = previous?.children.orEmpty())
        val oldChildren = previous?.children.orEmpty().associateBy(SafNode::identity)
        return base.copy(children = direct.map { child ->
            captureNode(
                child,
                oldChildren[child.uri.toString()],
                force = force,
                fallbacks = fallbacks,
                listDirect = false,
            )
        })
    }

    private suspend fun listFiles(document: DocumentFile): List<DocumentFile>? {
        coroutineContext.ensureActive()
        return try {
            document.listFiles().sortedBy { it.uri.toString() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}

internal data class CaptureResult(
    val roots: List<SafNode>,
    val fallbacks: List<SafFallback>,
)
