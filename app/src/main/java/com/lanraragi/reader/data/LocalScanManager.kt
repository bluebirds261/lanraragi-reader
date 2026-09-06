package com.lanraragi.reader.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lanraragi.reader.data.model.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 扫描本地外部文件夹中的画廊（压缩包或图片文件夹）。
 *
 * 扫描分两阶段：先计算轻量文件系统签名，签名未变化时直接跳过完整递归扫描；
 * 只有根目录内容发生变化时才重新构建图库索引。
 */
class LocalScanManager(private val context: Context) {

    private val _localArchives = MutableStateFlow<List<Archive>>(emptyList())
    val localArchives = _localArchives.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private var lastScanSignature: String? = null
    private var lastScanUris: Set<String> = emptySet()

    suspend fun scan(uris: Set<String>, force: Boolean = false) = withContext(Dispatchers.IO) {
        val normalizedUris = uris.filter { it.isNotBlank() }.toSortedSet()
        if (normalizedUris.isEmpty()) {
            lastScanSignature = "empty"
            lastScanUris = emptySet()
            if (_localArchives.value.isNotEmpty()) _localArchives.value = emptyList()
            return@withContext
        }

        val signature = buildSignature(normalizedUris)
        if (!force && normalizedUris == lastScanUris && signature == lastScanSignature) {
            return@withContext
        }

        _isScanning.value = true
        try {
            val list = mutableListOf<Archive>()
            normalizedUris.forEach { uriString ->
                runCatching {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                    if (root != null && root.isDirectory) {
                        scanRecursive(root, list)
                    }
                }
            }
            _localArchives.value = list.distinctBy { it.arcid }.sortedBy { it.title.lowercase() }
            lastScanUris = normalizedUris
            lastScanSignature = buildSignature(normalizedUris)
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * 只读取目录元数据建立签名，不解析压缩包内容。
     * Android SAF 没有可靠的目录 mtime，因此这里组合 URI、名称、类型、大小和 mtime。
     */
    private fun buildSignature(uris: Set<String>): String {
        return uris.joinToString("|") { uriString ->
            val root = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
            if (root == null || !root.isDirectory) {
                "$uriString:missing"
            } else {
                signatureRecursive(root)
            }
        }
    }

    private fun signatureRecursive(file: DocumentFile): String {
        if (!file.isDirectory) {
            return listOf(file.uri.toString(), file.name.orEmpty(), file.length(), file.lastModified())
                .joinToString(":")
        }
        return try {
            file.listFiles()
                .sortedBy { it.uri.toString() }
                .joinToString(";") { child ->
                    signatureRecursive(child)
                }
        } catch (_: Exception) {
            "${file.uri}:unreadable"
        }
    }

    private fun scanRecursive(parent: DocumentFile, result: MutableList<Archive>) {
        val children = parent.listFiles()
        var hasImages = false
        var hasSubDirs = false

        children.forEach { file ->
            if (file.isDirectory) {
                hasSubDirs = true
                scanRecursive(file, result)
            } else if (file.isFile) {
                val name = file.name ?: ""
                if (isArchive(name)) {
                    result.add(createArchive(file))
                } else if (ArchiveFileReader.isImage(name)) {
                    hasImages = true
                }
            }
        }

        // 如果当前文件夹包含图片，且没有子文件夹（或者包含大量图片），将其视为一个画廊
        // 这里的逻辑参考 JHenTai：纯图片文件夹即为画廊
        if (hasImages && !hasSubDirs) {
            result.add(createArchive(parent))
        }
    }

    private fun createArchive(file: DocumentFile): Archive {
        val isDir = file.isDirectory
        return Archive(
            arcid = "local_${UUID.nameUUIDFromBytes(file.uri.toString().toByteArray())}",
            title = file.name ?: "未知画廊",
            tags = if (isDir) "local,folder" else "local,archive",
            pagecount = 0,
            summary = file.uri.toString() // 存入 URI 供读取时使用
        )
    }

    private fun isArchive(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".zip") || n.endsWith(".cbz") || n.endsWith(".rar") || n.endsWith(".cbr")
    }
}