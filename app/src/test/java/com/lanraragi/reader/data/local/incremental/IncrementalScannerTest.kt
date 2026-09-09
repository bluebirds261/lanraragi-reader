package com.lanraragi.reader.data.local.incremental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalScannerTest {
    private fun file(id: String, name: String, size: Long = 1, readable: Boolean = true) =
        SafNode(id, "content://$id", name, false, size = size, lastModified = 1, readable = readable)

    @Test fun unchangedTreeProducesNoArchiveWork() {
        val root = SafNode("root", "content://root", "Gallery", true, children = (1..10_000).map { file("f$it", "page-$it.jpg") })
        val delta = IncrementalScanner().scan(listOf(root), listOf(root))
        assertEquals(0, delta.archivesToParse.size)
        assertTrue(delta.unchanged.size >= 10_001)
    }

    @Test fun largeUnchangedDirectoryKeepsArchiveWorkBoundedToChangedBranch() {
        val old = SafNode("root", "r", "root", true, lastModified = 9, children =
            (1..5_000).map { file("f$it", "book-$it.cbz") },
        )
        val current = old.copy(children = old.children.map {
            if (it.identity == "f2500") it.copy(size = 2) else it
        })
        val delta = IncrementalScanner().scan(listOf(old), listOf(current))
        assertEquals(listOf("f2500"), delta.archivesToParse.map { it.identity })
    }

    @Test fun oneArchiveChangeOnlyParsesThatArchive() {
        val old = SafNode("root", "r", "root", true, children = listOf(file("a", "a.cbz"), file("b", "b.cbz")))
        val current = old.copy(children = listOf(file("a", "a.cbz", size = 2), old.children[1]))
        val delta = IncrementalScanner().scan(listOf(old), listOf(current))
        assertEquals(listOf("a"), delta.archivesToParse.map { it.identity })
    }

    @Test fun removedAndPermissionRevokedAreDistinctAndRetained() {
        val old = listOf(file("gone", "gone.cbz"), file("locked", "locked.cbz"))
        val current = listOf(file("locked", "locked.cbz", readable = false))
        val delta = IncrementalScanner().scan(old, current)
        assertEquals(listOf("gone"), delta.removed.map { it.identity })
        assertEquals(listOf("locked"), delta.unavailable.map { it.identity })
    }

    @Test fun naturalSortUsesNumericSegmentsAndStableIndexTieBreak() {
        val entries = listOf(ArchiveEntry("page10.jpg", 2), ArchiveEntry("page2.jpg", 1), ArchiveEntry("page2.jpg", 0))
        assertEquals(listOf(0, 1, 2), entries.naturalSorted().map { it.index })
    }
}
