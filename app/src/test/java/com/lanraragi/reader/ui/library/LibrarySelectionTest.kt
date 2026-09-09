package com.lanraragi.reader.ui.library

import com.lanraragi.reader.data.catalog.LibraryCapability
import com.lanraragi.reader.data.catalog.LibraryEntry
import com.lanraragi.reader.domain.model.ArchiveIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibrarySelectionTest {
    @Test fun selectionUsesStableSourceKey() {
        val a = LibraryEntry(ArchiveIdentity.Remote("a"), "same")
        val b = LibraryEntry(ArchiveIdentity.Remote("b"), "same")
        val s = LibrarySelection().toggle(a).toggle(b)
        assertEquals(setOf(a.sourceKey, b.sourceKey), s.sourceKeys)
        assertEquals(listOf(a, b), s.selected(listOf(a, b)))
    }

    @Test fun localAndRemoteOnlyExposeCommonCapabilities() {
        val remote = LibraryEntry(ArchiveIdentity.Remote("r"), "r")
        val local = LibraryEntry(ArchiveIdentity.LocalSaf("content://l"), "l")
        val summary = summarizeBatchCapabilities(listOf(remote, local))
        assertFalse(summary.supports(LibraryCapability.DELETE_SERVER_COPY))
        assertEquals(2, summary.selectedCount)
    }

    @Test fun detailSelectionSurvivesReordering() {
        val a = LibraryEntry(ArchiveIdentity.Remote("a"), "A")
        val b = LibraryEntry(ArchiveIdentity.Remote("b"), "B")
        val selection = LibraryDetailSelection().select(b)
        assertEquals(b, selection.resolve(listOf(b, a)))
    }
}
