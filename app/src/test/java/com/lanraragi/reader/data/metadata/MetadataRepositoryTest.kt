package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import com.lanraragi.reader.domain.model.ArchiveIdentity
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataRepositoryTest {
    private val provenance = MetadataProvenance(providerId = "test", sourceId = "42")

    @Test
    fun remoteApplyUsesFreshReadThenPutThenVerificationRead() = runTest {
        val baseline = MetadataSnapshot(title = "", revision = "r1")
        val verified = MetadataSnapshot(title = "Scraped", revision = "r2")
        val events = mutableListOf<String>()
        var fetchCount = 0
        val gateway = gateway(
            fetch = {
                events += "get"
                if (fetchCount++ == 0) baseline else verified
            },
            put = {
                events += "put:${it.title}"
                assertEquals("arc-1", it.arcid)
            },
        )
        val store = MemoryStore()
        val repository = MetadataRepository(gateway, store, clock = { 10L })
        val target = ArchiveIdentity.Remote("arc-1")
        repository.stagePatch(target, baseline, titlePatch("Scraped"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Applied)
        assertEquals(listOf("get", "put:Scraped", "get"), events)
        assertEquals(verified.title, result.state.latest?.title)
        assertEquals(verified.revision, result.state.latest?.revision)
        assertEquals(provenance, result.state.latest?.provenance?.get("title"))
        assertFalse(result.state.hasPendingPatch)
        assertEquals(MetadataPatch(), store.records.getValue(target.sourceKey).pendingPatch)
    }

    @Test
    fun changedRemoteBaseIsRemergedAndBlockedUntilReviewed() = runTest {
        val baseline = MetadataSnapshot(title = "", summary = "old", revision = "r1")
        val latest = baseline.copy(summary = "edited elsewhere", revision = "r2")
        var puts = 0
        val store = MemoryStore()
        val target = ArchiveIdentity.Remote("arc-2")
        val repository = MetadataRepository(
            remoteGateway = gateway(fetch = { latest }, put = { puts += 1 }),
            stateStore = store,
        )
        repository.stagePatch(target, baseline, titlePatch("Scraped"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Blocked)
        result as MetadataApplyResult.Blocked
        assertTrue(result.plan.requiresLatestReview)
        assertEquals("edited elsewhere", result.plan.mergedSnapshot.summary)
        assertTrue(result.state.hasPendingPatch)
        assertEquals(0, puts)
    }

    @Test
    fun remoteRebaseKeepsUserOverrideProtectionFromStoredSnapshot() = runTest {
        val baseline = MetadataSnapshot(
            title = "User title",
            userOverrides = setOf(MetadataFieldName.TITLE),
        )
        val target = ArchiveIdentity.Remote("arc-owned")
        var puts = 0
        val store = MemoryStore()
        val repository = MetadataRepository(
            remoteGateway = gateway(
                // A server response cannot contain client ownership markers.
                fetch = { MetadataSnapshot(title = "User title") },
                put = { puts += 1 },
            ),
            stateStore = store,
        )
        repository.stagePatch(target, baseline, titlePatch("Scraped"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Blocked)
        result as MetadataApplyResult.Blocked
        assertTrue(MetadataFieldName.TITLE in result.state.latest!!.userOverrides)
        assertTrue(
            result.plan.diff.conflicts.any {
                it.reason == MetadataConflictReason.USER_OVERRIDE
            },
        )
        assertEquals(0, puts)
        assertTrue(result.state.hasPendingPatch)
    }

    @Test
    fun stagingAddsBaselineOwnershipToAnExistingStoredSnapshot() = runTest {
        val target = ArchiveIdentity.Remote("arc-new-ownership")
        val store = MemoryStore().apply {
            records[target.sourceKey] = MetadataStoredState(
                sourceKey = target.sourceKey,
                snapshot = MetadataSnapshot(title = "User title"),
            )
        }
        var puts = 0
        val repository = MetadataRepository(
            remoteGateway = gateway(
                fetch = { MetadataSnapshot(title = "User title") },
                put = { puts += 1 },
            ),
            stateStore = store,
        )
        val baselineWithNewOwnership = MetadataSnapshot(
            title = "User title",
            userOverrides = setOf(MetadataFieldName.TITLE),
            provenance = mapOf("title" to provenance),
        )

        repository.stagePatch(
            target = target,
            baseline = baselineWithNewOwnership,
            patch = titlePatch("Scraped"),
        )

        val staged = store.records.getValue(target.sourceKey)
        assertEquals("User title", staged.snapshot.title)
        assertEquals(setOf(MetadataFieldName.TITLE), staged.snapshot.userOverrides)
        assertEquals(provenance, staged.snapshot.provenance["title"])

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Blocked)
        result as MetadataApplyResult.Blocked
        assertTrue(
            result.plan.diff.conflicts.any {
                it.reason == MetadataConflictReason.USER_OVERRIDE
            },
        )
        assertEquals(0, puts)
    }

    @Test
    fun refreshPreservesClientProvenanceAndFiltersRemovedTagProvenance() = runTest {
        val retainedTag = CanonicalTag.parse("artist:alice", source = TagSource.USER)
        val removedTag = CanonicalTag.parse("group:old", source = TagSource.EHENTAI)
        val titleProvenance = provenance.copy(sourceId = "title")
        val tagProvenance = provenance.copy(sourceId = "tag")
        val target = ArchiveIdentity.Remote("arc-provenance")
        val store = MemoryStore().apply {
            records[target.sourceKey] = MetadataStoredState(
                sourceKey = target.sourceKey,
                snapshot = MetadataSnapshot(
                    title = "Title",
                    tags = setOf(retainedTag, removedTag),
                    userOverrides = setOf(MetadataFieldName.TITLE),
                    provenance = mapOf(
                        "title" to titleProvenance,
                        "tag:${retainedTag.full}" to tagProvenance,
                        "tag:${removedTag.full}" to tagProvenance,
                    ),
                ),
            )
        }
        val repository = MetadataRepository(
            remoteGateway = gateway(
                fetch = {
                    MetadataSnapshot(
                        title = "Title",
                        tags = setOf(CanonicalTag.parse(retainedTag.full)),
                        revision = "server-r2",
                    )
                },
            ),
            stateStore = store,
        )

        val state = repository.refresh(target)
        val latest = requireNotNull(state.latest)

        assertEquals(setOf(MetadataFieldName.TITLE), latest.userOverrides)
        assertEquals(titleProvenance, latest.provenance["title"])
        assertEquals(tagProvenance, latest.provenance["tag:${retainedTag.full}"])
        assertFalse("tag:${removedTag.full}" in latest.provenance)
        assertEquals(TagSource.USER, latest.tags.single().source)
        assertEquals("server-r2", latest.revision)
    }

    @Test
    fun lockedPutKeepsDurablePendingPatch() = runTest {
        val baseline = MetadataSnapshot(title = "")
        val target = ArchiveIdentity.Remote("arc-3")
        val store = MemoryStore()
        val repository = MetadataRepository(
            remoteGateway = gateway(
                fetch = { baseline },
                put = {
                    throw MetadataRemoteException(
                        reason = MetadataRemoteFailureReason.LOCKED,
                        statusCode = 423,
                        message = "Locked resource",
                    )
                },
            ),
            stateStore = store,
        )
        repository.stagePatch(target, baseline, titlePatch("Scraped"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Pending)
        result as MetadataApplyResult.Pending
        assertEquals(MetadataPendingReason.LOCKED, result.reason)
        assertEquals(MetadataStateStatus.LOCKED, result.state.status)
        assertTrue(result.state.hasPendingPatch)
        assertTrue(store.records.getValue(target.sourceKey).pendingPatch != MetadataPatch())
    }

    @Test
    fun failedVerificationReadKeepsPatchAfterPut() = runTest {
        val baseline = MetadataSnapshot(title = "")
        var reads = 0
        val target = ArchiveIdentity.Remote("arc-4")
        val store = MemoryStore()
        val repository = MetadataRepository(
            remoteGateway = gateway(
                fetch = {
                    if (reads++ == 0) baseline else throw IOException("disconnected")
                },
            ),
            stateStore = store,
        )
        repository.stagePatch(target, baseline, titlePatch("Scraped"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Pending)
        result as MetadataApplyResult.Pending
        assertEquals(MetadataPendingReason.NETWORK, result.reason)
        assertEquals(MetadataStateStatus.OFFLINE, result.state.status)
        assertTrue(result.state.hasPendingPatch)
        assertTrue(store.records.getValue(target.sourceKey).pendingPatch != MetadataPatch())
    }

    @Test
    fun verificationMismatchDoesNotClaimSaved() = runTest {
        val baseline = MetadataSnapshot(title = "")
        var reads = 0
        val target = ArchiveIdentity.Remote("arc-5")
        val store = MemoryStore()
        val repository = MetadataRepository(
            remoteGateway = gateway(
                fetch = {
                    if (reads++ == 0) baseline else MetadataSnapshot(title = "Different")
                },
            ),
            stateStore = store,
        )
        repository.stagePatch(target, baseline, titlePatch("Scraped"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Pending)
        result as MetadataApplyResult.Pending
        assertEquals(MetadataPendingReason.INVALID_RESPONSE, result.reason)
        assertEquals(MetadataStateStatus.ERROR, result.state.status)
        assertTrue(result.state.hasPendingPatch)
        assertTrue(store.records.getValue(target.sourceKey).pendingPatch != MetadataPatch())
    }

    @Test
    fun localSafApplyPersistsLocallyWithoutGatewayCalls() = runTest {
        var reads = 0
        var puts = 0
        val gateway = gateway(
            fetch = {
                reads += 1
                error("local metadata must not be fetched remotely")
            },
            put = {
                puts += 1
                error("local metadata must not be uploaded")
            },
        )
        val store = MemoryStore()
        val target = ArchiveIdentity.LocalSaf("content://library/book.cbz")
        val baseline = MetadataSnapshot(title = "")
        val repository = MetadataRepository(gateway, store)
        repository.stagePatch(target, baseline, titlePatch("Local title"))

        val result = repository.applyPending(target)

        assertTrue(result is MetadataApplyResult.Applied)
        assertEquals(0, reads)
        assertEquals(0, puts)
        assertEquals("Local title", result.state.latest?.title)
        assertEquals("Local title", store.records.getValue(target.sourceKey).snapshot.title)
        assertFalse(result.state.hasPendingPatch)
    }

    @Test
    fun corruptPersistedPatchIsReportedAndNotReplacedWithEmptyState() = runTest {
        val target = ArchiveIdentity.Remote("arc-corrupt")
        val store = object : MetadataStateStore {
            var writes = 0

            override suspend fun read(sourceKey: String): MetadataStoredState? {
                throw MetadataPatchCodecException(
                    reason = MetadataPatchCodecError.MALFORMED_JSON,
                    message = "bad pending patch",
                )
            }

            override suspend fun write(state: MetadataStoredState) {
                writes += 1
            }
        }
        val repository = MetadataRepository(gateway(), store)

        val state = repository.stagePatch(
            target = target,
            baseline = MetadataSnapshot(),
            patch = titlePatch("Scraped"),
        )

        assertEquals(MetadataStateStatus.ERROR, state.status)
        assertTrue(state.error is MetadataStateError.Persistence)
        assertEquals(0, store.writes)
    }

    private fun titlePatch(title: String): MetadataPatch = MetadataPatch(
        title = MetadataField(title, provenance),
    )

    private fun gateway(
        fetch: suspend (String) -> MetadataSnapshot = { error("unexpected fetch") },
        put: suspend (MetadataPutPayload) -> Unit = {},
    ): MetadataRemoteGateway = object : MetadataRemoteGateway {
        override suspend fun fetch(arcid: String): MetadataSnapshot = fetch(arcid)

        override suspend fun put(payload: MetadataPutPayload) = put(payload)
    }

    private class MemoryStore : MetadataStateStore {
        val records = linkedMapOf<String, MetadataStoredState>()

        override suspend fun read(sourceKey: String): MetadataStoredState? = records[sourceKey]

        override suspend fun write(state: MetadataStoredState) {
            records[state.sourceKey] = state
        }
    }
}
