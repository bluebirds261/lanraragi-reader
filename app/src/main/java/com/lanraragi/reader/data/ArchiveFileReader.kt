package com.lanraragi.reader.data

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive as RarArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

/**
 * 统一处理 Zip/Cbz/Rar 文件及本地文件夹的图片读取。
 * 优化方案：缓存最近访问的压缩包临时文件及文件夹列表，提升加载与预载速度。
 */
object ArchiveFileReader {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    data class ArchiveEntry(val name: String, val index: Int)

    // 缓存：Uri.toString() -> 临时文件 File
    private val tempFileCache = ConcurrentHashMap<String, File>()
    // 缓存：Uri.toString() -> 图片列表
    private val imageListCache = ConcurrentHashMap<String, List<ArchiveEntry>>()
    // 缓存：Uri.toString() -> 最近访问的位图/数据，采用 LRU 思想（简单实现）
    private val entryCache = Collections.synchronizedMap(LinkedHashMap<String, InputStream>(16, 0.75f, true))
    private var lastUri: String? = null

    fun isImage(name: String): Boolean {
        val ext = name.substringAfterLast(".").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private suspend fun getOrDownloadTempFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        tempFileCache[uriStr]?.let { if (it.exists()) return@withContext it }

        // 清理旧缓存（只保留最近一个以节省空间）
        lastUri?.let { old ->
            if (old != uriStr) {
                tempFileCache.remove(old)?.delete()
                imageListCache.remove(old)
            }
        }

        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val file = File.createTempFile("arc_cache", ".tmp", context.cacheDir)
            file.outputStream().use { output -> input.copyTo(output) }
            tempFileCache[uriStr] = file
            lastUri = uriStr
            file
        } catch (e: Exception) {
            null
        }
    }

    /** 获取压缩包或文件夹内的图片列表，按文件名排序。 */
    suspend fun getImages(context: Context, uri: Uri): List<ArchiveEntry> = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        imageListCache[uriStr]?.let { return@withContext it }

        val list = mutableListOf<String>()

        // 1. 检查是否为本地文件夹 (file:// 协议)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.isDirectory) {
                file.listFiles()?.forEach { f ->
                    if (f.isFile && isImage(f.name)) list.add(f.name)
                }
                return@withContext list.toEntries().also { imageListCache[uriStr] = it }
            }
        }

        // 2. 检查是否为 SAF DocumentTree 文件夹
        try {
            val df = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            if (df != null && df.isDirectory) {
                df.listFiles().forEach { f ->
                    if (f.isFile && isImage(f.name ?: "")) list.add(f.name ?: "")
                }
                return@withContext list.toEntries().also { imageListCache[uriStr] = it }
            }
        } catch (e: Exception) {}

        // 3. 压缩包模式
        val tempFile = getOrDownloadTempFile(context, uri) ?: return@withContext emptyList()
        val type = context.contentResolver.getType(uri)
        val extension = type ?: uri.path?.substringAfterLast(".")?.lowercase()

        try {
            if (extension == "application/x-rar-compressed" || extension == "rar" || uri.path?.endsWith(".cbr", true) == true) {
                val archive = RarArchive(tempFile)
                archive.fileHeaders.forEach { header ->
                    if (!header.isDirectory && isImage(header.fileName)) list.add(header.fileName)
                }
                archive.close()
            } else {
                val zipFile = ZipFile(tempFile)
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && isImage(entry.name)) list.add(entry.name)
                }
                zipFile.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list.toEntries().also { imageListCache[uriStr] = it }
    }

    private fun List<String>.toEntries(): List<ArchiveEntry> {
        return this.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .mapIndexed { index, name -> ArchiveEntry(name, index) }
    }

    /** 读取压缩包或文件夹内指定索引的图片流。 */
    suspend fun openImageStream(context: Context, uri: Uri, entryName: String): InputStream? = withContext(Dispatchers.IO) {
        val cacheKey = "$uri|$entryName"
        entryCache[cacheKey]?.let { 
            it.reset() // 如果支持 reset
            return@withContext it 
        }

        if (uri.scheme == "file") {
            val f = File(File(uri.path ?: ""), entryName)
            if (f.exists()) return@withContext f.inputStream()
        }

        try {
            val df = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            if (df != null && df.isDirectory) {
                val file = df.findFile(entryName)
                if (file != null) return@withContext context.contentResolver.openInputStream(file.uri)
            }
        } catch (e: Exception) {}

        val tempFile = getOrDownloadTempFile(context, uri) ?: return@withContext null
        val type = context.contentResolver.getType(uri)
        val extension = type ?: uri.path?.substringAfterLast(".")?.lowercase()

        try {
            if (extension == "application/x-rar-compressed" || extension == "rar" || uri.path?.endsWith(".cbr", true) == true) {
                val archive = RarArchive(tempFile)
                val header = archive.fileHeaders.find { it.fileName == entryName }
                if (header != null) {
                    val stream = archive.getInputStream(header).readBytes().inputStream()
                    archive.close()
                    return@withContext stream
                }
                archive.close()
            } else {
                val zipFile = ZipFile(tempFile)
                val entry = zipFile.getEntry(entryName)
                if (entry != null) {
                    val stream = zipFile.getInputStream(entry).readBytes().inputStream()
                    zipFile.close()
                    return@withContext stream
                }
                zipFile.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun clearCache() {
        tempFileCache.values.forEach { it.delete() }
        tempFileCache.clear()
        imageListCache.clear()
        entryCache.clear()
        lastUri = null
    }
}
