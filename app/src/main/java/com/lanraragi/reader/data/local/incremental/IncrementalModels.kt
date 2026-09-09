package com.lanraragi.reader.data.local.incremental

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/** Immutable SAF node metadata used by the incremental scanner. */
@Serializable
data class SafNode(
    val identity: String,
    val uri: String,
    val name: String,
    val directory: Boolean,
    val mimeType: String? = null,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val readable: Boolean = true,
    val children: List<SafNode> = emptyList(),
) {
    val fingerprint: String get() = Fingerprints.node(this)
    val metadataFingerprint: String get() = Fingerprints.metadata(this)
}

object Fingerprints {
    /** Fingerprint excluding children, used to decide whether a directory can be reused. */
    fun metadata(node: SafNode): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun put(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(byteArrayOf(0))
        }
        put(node.identity); put(node.uri); put(node.name); put(if (node.directory) "d" else "f")
        put(node.mimeType.orEmpty()); put(node.size.toString()); put(node.lastModified.toString())
        put(if (node.readable) "r" else "u")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun node(node: SafNode): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun put(value: String) { digest.update(value.toByteArray(Charsets.UTF_8)); digest.update(byteArrayOf(0)) }
        put(node.metadataFingerprint)
        if (node.directory) {
            node.children.sortedWith(Comparator { left, right ->
                compareNatural(left.name, right.name).takeIf { it != 0 }
                    ?: left.identity.compareTo(right.identity)
            })
                .forEach { put(it.identity); put(it.fingerprint) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

enum class ScanDisposition { ADDED, CHANGED, REMOVED, UNCHANGED, UNAVAILABLE }

data class ScanItem(val identity: String, val node: SafNode?, val disposition: ScanDisposition)

data class ScanDelta(
    val added: List<ScanItem>,
    val changed: List<ScanItem>,
    val removed: List<ScanItem>,
    val unchanged: List<ScanItem>,
    val unavailable: List<ScanItem>,
) {
    val all: List<ScanItem> get() = added + changed + removed + unchanged + unavailable
    val archivesToParse: List<ScanItem> get() = (added + changed).filter { it.node?.isArchive() == true }
}

fun SafNode.isArchive(): Boolean = !directory && name.substringAfterLast('.', "").lowercase() in setOf("zip", "cbz", "rar", "cbr")
fun SafNode.isImage(): Boolean = !directory && name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

data class ArchiveEntry(val name: String, val index: Int, val size: Long = 0L, val mimeType: String? = null) {
    val isImage: Boolean get() = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
}

data class ArchiveDescription(
    val identity: String,
    val fingerprint: String,
    val format: ArchiveFormat,
    val entries: List<ArchiveEntry>,
) {
    val imageEntries: List<ArchiveEntry> get() = entries.filter { it.isImage }
    val pageCount: Int get() = imageEntries.size
    val coverEntry: ArchiveEntry? get() = imageEntries.firstOrNull()
}

enum class ArchiveFormat { DIRECTORY, ZIP, RAR, UNKNOWN }

data class NaturalPart(val numeric: Long?, val text: String)

fun String.naturalSortKey(): List<NaturalPart> = Regex("\\d+|\\D+").findAll(lowercase()).map {
    NaturalPart(it.value.toLongOrNull(), it.value)
}.toList()

fun List<ArchiveEntry>.naturalSorted(): List<ArchiveEntry> = sortedWith(
    Comparator { a, b ->
        compareNatural(a.name, b.name).takeIf { it != 0 } ?: a.index.compareTo(b.index)
    },
)

private fun compareNatural(a: String, b: String): Int {
    val aa = a.naturalSortKey(); val bb = b.naturalSortKey()
    for (i in 0 until minOf(aa.size, bb.size)) {
        val av = aa[i]; val bv = bb[i]
        val c = when {
            av.numeric != null && bv.numeric != null -> av.numeric.compareTo(bv.numeric)
            av.numeric != null -> -1
            bv.numeric != null -> 1
            else -> av.text.compareTo(bv.text)
        }
        if (c != 0) return c
    }
    return aa.size.compareTo(bb.size)
}
