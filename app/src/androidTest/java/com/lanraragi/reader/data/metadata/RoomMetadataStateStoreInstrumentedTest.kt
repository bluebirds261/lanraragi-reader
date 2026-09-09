package com.lanraragi.reader.data.metadata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanraragi.reader.data.db.LocalMetadataEntity
import com.lanraragi.reader.data.db.MetadataStateEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMetadataStateStoreInstrumentedTest {
    private lateinit var database: ReaderDatabase
    private lateinit var store: RoomMetadataStateStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomMetadataStateStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun snapshotBaselinePatchAndProvenanceRoundTrip() = runBlocking {
        val sourceKey = ArchiveIdentity.Remote("arc-42").sourceKey
        val provider = MetadataProvenance("ehentai", "gid-42", "https://eh.example/42", 0.91f, 100L)
        val tag = CanonicalTag("artist", "alice", "artist:Alice", "爱丽丝", TagSource.EHENTAI, 0.8f)
        val snapshot = MetadataSnapshot(
            title = "Remote title",
            summary = "Summary",
            sourceUrl = "https://source/42",
            tags = linkedSetOf(tag),
            userOverrides = setOf(MetadataFieldName.TITLE),
            provenance = mapOf("title" to provider, "tag:${tag.full}" to provider),
            revision = "rev-1",
        )
        val baseline = snapshot.copy(title = "Baseline title", revision = "rev-0")
        val patch = MetadataPatch(
            summary = MetadataField("Patched summary", provider),
            addTags = setOf(CanonicalTag.parse("parody:Drama", TagSource.EHENTAI)),
            tagProvenance = mapOf("parody:drama" to provider),
        )

        store.write(MetadataStoredState(sourceKey, snapshot, baseline, patch, 123L))
        val restored = requireNotNull(store.read(sourceKey))

        assertEquals(MetadataStoredState(sourceKey, snapshot, baseline, patch, 123L), restored)
        val provenance = database.metadataDao().observeProvenance(sourceKey).first()
        assertEquals(2, provenance.size)
        assertTrue(provenance.any { it.field == "title" && it.canonicalTag == null })
        assertTrue(provenance.any { it.field == "tag" && it.canonicalTag == tag.full })
    }

    @Test
    fun localMetadataMirrorIsPersistedAndReadWithoutRemoteState() = runBlocking {
        val target = ArchiveIdentity.LocalSaf("content://archives/local-1")
        val tag = CanonicalTag.parse("genre:Drama", TagSource.USER)
        val snapshot = MetadataSnapshot(
            title = "Local title",
            summary = "Local summary",
            tags = setOf(tag),
            revision = "local-rev",
        )

        store.write(MetadataStoredState(target.sourceKey, snapshot, updatedAt = 77L))

        val mirror = requireNotNull(database.localMetadataDao().find(target.sourceKey))
        assertEquals("Local title", mirror.title)
        assertEquals("genre:Drama", mirror.tags)
        assertEquals("Local summary", mirror.summary)
        assertEquals(77L, mirror.updatedAt)
        assertEquals(snapshot, requireNotNull(store.read(target.sourceKey)).snapshot)
        assertEquals(null, store.read("local:missing"))
    }

    @Test
    fun localMetadataMirrorCanBeLoadedWhenStateRowIsAbsent() = runBlocking {
        val target = ArchiveIdentity.LocalSaf("content://archives/local-fallback")
        database.localMetadataDao().upsert(
            LocalMetadataEntity(
                sourceKey = target.sourceKey,
                title = "Fallback title",
                tags = "artist:Alice, genre:Drama",
                summary = "Fallback summary",
                updatedAt = 88L,
            ),
        )

        val restored = requireNotNull(store.read(target.sourceKey))
        assertEquals("Fallback title", restored.snapshot.title)
        assertEquals("Fallback summary", restored.snapshot.summary)
        assertEquals(setOf("artist:alice", "genre:drama"), restored.snapshot.tags.map { it.full }.toSet())
        assertEquals(88L, restored.updatedAt)
        assertTrue(restored.pendingPatch == MetadataPatch())
    }

    @Test
    fun corruptPendingPatchFailsExplicitly() = runBlocking {
        val sourceKey = ArchiveIdentity.Remote("corrupt").sourceKey
        database.metadataStateDao().upsert(
            MetadataStateEntity(
                sourceKey = sourceKey,
                snapshotJson = MetadataSnapshotCodec.encode(MetadataSnapshot(title = "ok")),
                baselineJson = null,
                pendingPatchJson = "{ definitely not a metadata patch",
                updatedAt = 1L,
            ),
        )

        try {
            store.read(sourceKey)
            throw AssertionError("expected MetadataStateStoreException")
        } catch (error: MetadataStateStoreException) {
            assertTrue(error.message.orEmpty().contains("persisted metadata state", ignoreCase = true))
            assertTrue(error.cause != null)
        }
    }
}
