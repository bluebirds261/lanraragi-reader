package com.lanraragi.reader.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * 离线批量产物（离线档案 / 封面 / 元数据）的存储根抽象。
 *
 * 约定：
 * - [relPath] 一律是相对存储根的 `/` 分隔相对路径（如 `arcid/pages/0001.img`），
 *   不接受 `..` 或 `\`，实现必须拒绝越出根目录的路径。
 * - 所有挂起方法应在内部完成 IO 切换，调用方无需持有特定调度器。
 * - 写失败统一返回 `false`/`null`，不向上抛异常，便于门禁与缓存侧静默降级。
 */
interface StorageRoot {
    /** `true` 表示用户自定义目录（SAF），`false` 表示应用默认私有目录。 */
    val isCustom: Boolean

    /** 确保相对子目录存在；失败返回 `false`。 */
    suspend fun ensureDir(relPath: String): Boolean

    /**
     * 覆盖写一个文件（父目录自动创建）。不关闭 [data]，由调用方负责其生命周期。
     * 返回 `false` 表示写入失败（磁盘满 / 授权失效 / 介质移除等）。
     */
    suspend fun writeFile(relPath: String, data: InputStream): Boolean

    /** 打开只读流；不存在时返回 `null`。 */
    suspend fun readFile(relPath: String): InputStream?

    /** 删除单个文件（不存在视为成功）。 */
    suspend fun delete(relPath: String): Boolean

    /** 递归删除目录（或单个文件）；不存在视为成功。 */
    suspend fun deleteDir(relPath: String): Boolean

    suspend fun exists(relPath: String): Boolean

    /** 文件大小；目录 = 递归求和；不存在返回 `0L`。 */
    suspend fun sizeOf(relPath: String): Long

    /** 最后修改时间（epoch ms）；不存在或 provider 不提供时返回 `0L`。 */
    suspend fun lastModifiedOf(relPath: String): Long

    /**
     * 相对路径文件清单（递归），供用量统计与清理使用；[relPath] 为空表示整个根。
     * 目录本身不出现在结果中。
     */
    suspend fun walkFiles(relPath: String): List<String>

    /**
     * 返回一个可写的持久化下载目标：默认根返回绝对文件路径，
     * SAF 根返回 `content://` 文档 URI（不存在时创建空文档）。
     * 该目标供 [com.lanraragi.reader.data.download.DownloadDestination] 消费，
     * 两种形态均被下载 runner 与 SavedArtifactExporter 支持。失败返回 `null`。
     */
    suspend fun ensureFile(relPath: String): String?

    /**
     * 解析 [relPath] 已存在文件的外部引用串（默认根 = 绝对路径；SAF = `content://` 文档 URI），
     * 不创建任何文件；缺失时返回 `null`。供 SavedArtifact.path 等持久化引用使用。
     */
    suspend fun pathOf(relPath: String): String?

    /** 设置页展示用名称。 */
    fun displayName(): String
}

/**
 * 存储门禁统一判定：已降级（授权失效回退默认目录）视为不可用，
 * 否则对当前根做可写探测。返回拒绝原因（中文），可用返回 `null`。
 */
suspend fun StorageRootState.storageUnavailableReason(): String? = when {
    degraded -> degradedReason ?: "存储目录不可用，已回退到默认目录"
    else -> when (val availability = root.probeWritable()) {
        is StorageAvailability.Available -> null
        is StorageAvailability.Unavailable -> availability.reason
    }
}

/** 存储门禁探测结果：`Unavailable.reason` 直接面向用户展示（中文）。 */
sealed interface StorageAvailability {
    data object Available : StorageAvailability
    data class Unavailable(val reason: String) : StorageAvailability
}

/**
 * 存储可用性探测：SAF 根先做廉价持久化授权校验，再写入并删除探针文件。
 * 授权被系统回收、介质被移除、目录只读都会归为 [StorageAvailability.Unavailable]。
 */
suspend fun StorageRoot.probeWritable(): StorageAvailability {
    if (this is SafStorageRoot) {
        persistedPermissionError()?.let { return StorageAvailability.Unavailable(it) }
    }
    return withContext(Dispatchers.IO) {
        try {
            val probeName = ".storage-probe.tmp"
            if (!writeFile(probeName, java.io.ByteArrayInputStream(ByteArray(0)))) {
                return@withContext StorageAvailability.Unavailable("存储目录不可写：写入探测失败")
            }
            delete(probeName)
            StorageAvailability.Available
        } catch (e: CancellationException) {
            // writeFile 是挂起函数：取消必须向上传播，否则会被误判成「存储不可用」并写入降级态。
            throw e
        } catch (e: Exception) {
            StorageAvailability.Unavailable(e.message?.takeIf(String::isNotBlank) ?: "存储目录不可用")
        }
    }
}

/** 存储根的单一权威状态：当前根实例 + 降级标记 + 最近一次存储错误（供 UI 横幅观察）。 */
data class StorageRootState(
    val root: StorageRoot,
    /** `true` = 期望使用自定义 SAF 根但校验失败，已回退到默认目录。 */
    val degraded: Boolean = false,
    /** 降级原因（中文，可直接展示）；未降级为 `null`。 */
    val degradedReason: String? = null,
    /** 最近一次运行时存储写入失败的原因（`null` = 正常）。 */
    val lastError: String? = null,
)

/**
 * 根据 settings.storageRootUri 解析当前存储根：
 * - `null`/空白 → [DefaultStorageRoot]（应用默认私有目录，行为与历史版本一致）；
 * - SAF URI 无持久化授权或目录不可写 → 回退 [DefaultStorageRoot] 并标记 degraded。
 */
fun resolveStorageRoot(context: Context, uriString: String?, runtimeError: String? = null): StorageRootState {
    if (uriString.isNullOrBlank()) {
        return StorageRootState(root = DefaultStorageRoot(context), lastError = runtimeError)
    }
    return runCatching {
        val saf = SafStorageRoot(context.applicationContext, Uri.parse(uriString))
        val unavailable = saf.persistenceError()
            ?: if (saf.treeWritable() == true) null else "SAF 目录不可写，已回退到默认目录"
        if (unavailable != null) {
            StorageRootState(
                root = DefaultStorageRoot(context),
                degraded = true,
                degradedReason = unavailable,
                lastError = runtimeError ?: unavailable,
            )
        } else {
            StorageRootState(root = saf, lastError = runtimeError)
        }
    }.getOrElse {
        val reason = it.message?.takeIf(String::isNotBlank) ?: "存储目录不可用，已回退到默认目录"
        StorageRootState(
            root = DefaultStorageRoot(context),
            degraded = true,
            degradedReason = reason,
            lastError = runtimeError ?: reason,
        )
    }
}

/** 应用默认私有存储根：`filesDir/offline`，与历史版本的硬编码路径完全一致。 */
class DefaultStorageRoot(context: Context) : StorageRoot {
    private val base: File = File(context.applicationContext.filesDir, "offline")

    override val isCustom: Boolean get() = false

    override fun displayName(): String = "默认目录（应用私有空间）"

    private fun fileFor(relPath: String): File {
        val rel = relPath.trim().trimStart('/')
        require(rel.isNotEmpty()) { "相对路径不能为空" }
        require(!rel.contains("..") && !rel.contains('\\')) { "非法相对路径: $relPath" }
        return File(base, rel)
    }

    override suspend fun ensureDir(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { fileFor(relPath).isDirectory || fileFor(relPath).mkdirs() }.getOrDefault(false)
    }

    override suspend fun writeFile(relPath: String, data: InputStream): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = fileFor(relPath)
            val parent = target.parentFile
            requireNotNull(parent) { "目标没有父目录" }
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) return@runCatching false
            // 原子覆盖：先写临时文件再 move，避免半截 JSON/图片落盘。
            val temporary = File(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporary).use { output -> data.copyTo(output) }
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                true
            } finally {
                temporary.delete()
            }
        }.getOrDefault(false)
    }

    override suspend fun readFile(relPath: String): InputStream? = withContext(Dispatchers.IO) {
        runCatching { fileFor(relPath).takeIf(File::isFile)?.inputStream() }.getOrNull()
    }

    override suspend fun delete(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(relPath)
            !file.exists() || file.delete()
        }.getOrDefault(false)
    }

    override suspend fun deleteDir(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(relPath)
            !file.exists() || file.deleteRecursively()
        }.getOrDefault(false)
    }

    override suspend fun exists(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { fileFor(relPath).exists() }.getOrDefault(false)
    }

    override suspend fun sizeOf(relPath: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(relPath)
            when {
                file.isFile -> file.length()
                file.isDirectory -> file.walkTopDown().filter(File::isFile).sumOf(File::length)
                else -> 0L
            }
        }.getOrDefault(0L)
    }

    override suspend fun lastModifiedOf(relPath: String): Long = withContext(Dispatchers.IO) {
        runCatching { fileFor(relPath).takeIf(File::exists)?.lastModified() ?: 0L }.getOrDefault(0L)
    }

    override suspend fun walkFiles(relPath: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val start = if (relPath.isBlank()) base else fileFor(relPath)
            if (!start.isDirectory) return@runCatching emptyList()
            val baseCanonical = base.canonicalPath
            start.walkTopDown()
                .filter(File::isFile)
                .map { it.canonicalPath.removePrefix(baseCanonical).trimStart(File.separatorChar) }
                .filter(String::isNotEmpty)
                .toList()
        }.getOrDefault(emptyList())
    }

    override suspend fun ensureFile(relPath: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = fileFor(relPath)
            val parent = target.parentFile
            requireNotNull(parent) { "目标没有父目录" }
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) return@runCatching null
            if (target.isFile) return@runCatching target.absolutePath
            if (target.createNewFile()) target.absolutePath else null
        }.getOrNull()
    }

    override suspend fun pathOf(relPath: String): String? = withContext(Dispatchers.IO) {
        runCatching { fileFor(relPath).takeIf(File::isFile)?.absolutePath }.getOrNull()
    }
}

/** SAF tree 存储根：离线产物写入用户通过 OpenDocumentTree 授权的文件夹。 */
class SafStorageRoot(private val context: Context, val treeUri: Uri) : StorageRoot {

    private val resolver = context.applicationContext.contentResolver

    /** 根文档；URI 无效或 provider 不可用时为 `null`，此后所有操作安全返回失败。 */
    private val tree: DocumentFile? = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()

    override val isCustom: Boolean get() = true

    override fun displayName(): String = runCatching { tree?.name }.getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: treeUri.lastPathSegment?.takeIf(String::isNotBlank)
        ?: "SAF 目录"

    /** 持久化授权校验失败的原因；授权有效返回 `null`。 */
    fun persistedPermissionError(): String? {
        val persisted = runCatching { resolver.persistedUriPermissions }.getOrNull()
        val granted = persisted.orEmpty().any {
            it.uri.toString() == treeUri.toString() && it.isReadPermission && it.isWritePermission
        }
        return if (granted) null else "SAF 授权已失效，请重新选择存储目录"
    }

    /** 初始化期校验（授权 + 目录可写性）；失败返回中文原因。 */
    fun persistenceError(): String? = persistedPermissionError()

    /** 初始化期目录可写校验；`null` = 不可判定（tree 缺失）。 */
    fun treeWritable(): Boolean? = runCatching { tree?.canWrite() }.getOrNull()

    // ---- 相对路径 → DocumentFile 导航 ----

    private fun segmentsOf(relPath: String): List<String> {
        val rel = relPath.trim().trimStart('/')
        require(!rel.contains("..") && !rel.contains('\\')) { "非法相对路径: $relPath" }
        return rel.split('/').filter(String::isNotEmpty)
    }

    /** 沿相对路径逐级查找目录；[create] 为 `true` 时自动创建缺失层级。 */
    private suspend fun directory(relPath: String, create: Boolean): DocumentFile? =
        withContext(Dispatchers.IO) {
            val segments = segmentsOf(relPath)
            var current = tree ?: return@withContext null
            for (segment in segments) {
                val existing = runCatching { current.findFile(segment) }.getOrNull()
                current = when {
                    // 同名文件占位：不可作为目录穿透。
                    existing != null && existing.isDirectory -> existing
                    existing == null && create -> runCatching { current.createDirectory(segment) }.getOrNull()
                        ?: return@withContext null
                    else -> return@withContext null
                }
            }
            current
        }

    /** 定位相对路径对应的文档（文件或目录）；不存在返回 `null`。 */
    private suspend fun document(relPath: String): DocumentFile? = withContext(Dispatchers.IO) {
        val segments = segmentsOf(relPath)
        if (segments.isEmpty()) return@withContext tree
        val parentDir = if (segments.size == 1) tree else directory(segments.dropLast(1).joinToString("/"), create = false)
        parentDir ?: return@withContext null
        runCatching { parentDir.findFile(segments.last()) }.getOrNull()
    }

    override suspend fun ensureDir(relPath: String): Boolean =
        if (segmentsOf(relPath).isEmpty()) tree != null else directory(relPath, create = true) != null

    override suspend fun writeFile(relPath: String, data: InputStream): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val segments = segmentsOf(relPath)
            require(segments.isNotEmpty()) { "相对路径不能为空" }
            val parent = directory(segments.dropLast(1).joinToString("/"), create = true) ?: return@runCatching false
            val name = segments.last()
            val document = runCatching { parent.findFile(name) }.getOrNull()
                ?: runCatching { parent.createFile("application/octet-stream", name) }.getOrNull()
                ?: return@runCatching false
            resolver.openOutputStream(document.uri, "w")?.use { output ->
                data.copyTo(output)
                output.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    override suspend fun readFile(relPath: String): InputStream? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = document(relPath) ?: return@runCatching null
            if (doc.isFile) resolver.openInputStream(doc.uri) else null
        }.getOrNull()
    }

    override suspend fun delete(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val doc = document(relPath) ?: return@runCatching true
            DocumentsContract.deleteDocument(resolver, doc.uri) || !doc.exists()
        }.getOrDefault(false)
    }

    override suspend fun deleteDir(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val segments = segmentsOf(relPath)
            if (segments.isEmpty()) return@runCatching false // 不允许删除根本身
            val doc = document(relPath) ?: return@runCatching true
            DocumentsContract.deleteDocument(resolver, doc.uri) || !doc.exists()
        }.getOrDefault(false)
    }

    override suspend fun exists(relPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { document(relPath)?.exists() ?: false }.getOrDefault(false)
    }

    override suspend fun sizeOf(relPath: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val doc = document(relPath) ?: return@runCatching 0L
            doc.sumOfBytes()
        }.getOrDefault(0L)
    }

    override suspend fun lastModifiedOf(relPath: String): Long = withContext(Dispatchers.IO) {
        runCatching { document(relPath)?.lastModified() ?: 0L }.getOrDefault(0L)
    }

    override suspend fun walkFiles(relPath: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val segments = segmentsOf(relPath)
            val prefix = if (relPath.isBlank()) "" else segments.joinToString("/").trimEnd('/') + "/"
            val start = document(relPath) ?: return@runCatching emptyList()
            val result = mutableListOf<String>()
            fun DocumentFile.walk(prefixSoFar: String) {
                listFiles().forEach { child ->
                    val name = child.name ?: return@forEach
                    if (child.isDirectory) {
                        child.walk("$prefixSoFar$name/")
                    } else {
                        result += "$prefixSoFar$name"
                    }
                }
            }
            start.walk(prefix)
            result
        }.getOrDefault(emptyList())
    }

    override suspend fun ensureFile(relPath: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val segments = segmentsOf(relPath)
            require(segments.isNotEmpty()) { "相对路径不能为空" }
            val parent = directory(segments.dropLast(1).joinToString("/"), create = true) ?: return@runCatching null
            val name = segments.last()
            val document = runCatching { parent.findFile(name) }.getOrNull()
                ?: runCatching { parent.createFile("application/octet-stream", name) }.getOrNull()
                ?: return@runCatching null
            document.uri.toString()
        }.getOrNull()
    }

    override suspend fun pathOf(relPath: String): String? = withContext(Dispatchers.IO) {
        runCatching { document(relPath)?.takeIf(DocumentFile::isFile)?.uri?.toString() }.getOrNull()
    }

    private fun DocumentFile.sumOfBytes(): Long =
        if (isFile) length() else listFiles().sumOf { it.sumOfBytes() }
}
