package com.lanraragi.reader.data.local.incremental

import com.lanraragi.reader.data.readTextAtomically
import com.lanraragi.reader.data.writeTextAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Durable sidecar for SAF tree state. This is deliberately independent from
 * Room so a bad/corrupt snapshot only causes a conservative rescan.
 */
@Serializable
data class SafSnapshot(
    val version: Int = VERSION,
    val roots: List<SafNode> = emptyList(),
    val fallbacks: List<SafFallback> = emptyList(),
) {
    companion object { const val VERSION = 1 }
}

/** Why a scan could not trust directory metadata and had to descend. */
@Serializable
data class SafFallback(
    val uri: String,
    val reason: String,
)

interface SafSnapshotStore {
    fun load(): SafSnapshot
    fun save(snapshot: SafSnapshot)
}

class FileSafSnapshotStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SafSnapshotStore {
    override fun load(): SafSnapshot = runCatching {
        if (!file.exists()) SafSnapshot() else json.decodeFromString<SafSnapshot>(file.readTextAtomically())
    }.getOrElse { SafSnapshot() }.takeIf { it.version == SafSnapshot.VERSION } ?: SafSnapshot()

    override fun save(snapshot: SafSnapshot) {
        file.writeTextAtomically(json.encodeToString(snapshot))
    }
}

/** Test and transient implementation for callers that do not need a sidecar. */
class MemorySafSnapshotStore(initial: SafSnapshot = SafSnapshot()) : SafSnapshotStore {
    @Volatile private var snapshot = initial
    override fun load(): SafSnapshot = snapshot
    override fun save(snapshot: SafSnapshot) { this.snapshot = snapshot }
}
