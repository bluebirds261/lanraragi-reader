package com.lanraragi.reader.data

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Writes a rollback JSON snapshot without exposing a partially-written file. */
internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val temporary = File(parentFile, ".$name.${UUID.randomUUID()}.tmp")
    try {
        FileOutputStream(temporary).use { stream ->
            stream.write(text.toByteArray(StandardCharsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}

/** Reads the last fully replaced UTF-8 snapshot. */
internal fun File.readTextAtomically(): String =
    inputStream().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
