package com.lanraragi.reader.data.download

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

class SavedArtifactExporter(private val contentResolver: ContentResolver? = null) {
    suspend fun export(artifact: SavedArtifact, destination: DownloadDestination) = withContext(Dispatchers.IO) {
        val source = File(artifact.path)
        require(source.isFile && source.length() > 0L) { "已保存原档不可用" }
        try {
            if (destination.value.startsWith("content://")) {
                requireNotNull(contentResolver) { "SAF export requires a ContentResolver" }
                    .openOutputStream(Uri.parse(destination.value), "w")?.use { output ->
                    source.inputStream().buffered().use { it.copyTo(output) }
                } ?: error("无法打开导出目标")
            } else {
                val target = File(destination.value)
                target.parentFile?.mkdirs()
                val part = File(target.path + ".part")
                FileOutputStream(part).use { output ->
                    source.inputStream().buffered().use { it.copyTo(output) }
                    output.fd.sync()
                }
                try {
                    Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }
}
