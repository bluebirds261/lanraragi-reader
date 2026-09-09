package com.lanraragi.reader.data.favorites

import kotlinx.coroutines.test.runTest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EhFavoritesTest {
    @Test fun normalizeAlwaysReturnsTenStableSlots() {
        val slots = EhFavoriteSlots.normalize(listOf(EhFavoriteSlot(3, "Work", 4)))
        assertEquals((0..9).toList(), slots.map { it.slotIndex })
        assertEquals("Work", slots[3].remoteName)
    }

    @Test fun syncReadsCredentialsAndPersistsRemoteRenameAndCount() = runTest {
        val store = MemoryFavoriteStore()
        var invalidated = false
        val repo = EhFavoritesRepository(
            store,
            object : EhAccountConnector {
                override suspend fun currentCredentials() = EhCredentials(Any())
                override suspend fun invalidateCredentials() { invalidated = true }
            },
            object : EhFavoriteGateway {
                override suspend fun fetchFavorites(credentials: EhCredentials) = EhFavoriteSnapshot(listOf(EhFavoriteSlot(0, "Renamed", 8)))
            },
            clock = { 22L },
        )
        val state = repo.sync()
        assertTrue(state is EhFavoriteSyncState.Ready)
        assertEquals("Renamed", store.snapshot!!.normalizedSlots[0].remoteName)
        assertEquals(8, store.snapshot!!.normalizedSlots[0].remoteCount)
        assertEquals(22L, store.snapshot!!.fetchedAt)
        assertTrue(!invalidated)
    }

    @Test fun invalidCookieIsClassifiedAndInvalidatesConnector() = runTest {
        val store = MemoryFavoriteStore()
        var invalidated = false
        val state = EhFavoritesRepository(
            store,
            object : EhAccountConnector {
                override suspend fun currentCredentials() = EhCredentials(Any())
                override suspend fun invalidateCredentials() { invalidated = true }
            },
            object : EhFavoriteGateway {
                override suspend fun fetchFavorites(credentials: EhCredentials): EhFavoriteSnapshot = throw EhFavoriteSyncException(EhFavoriteSyncFailure.CookieInvalid())
            },
        ).sync()
        assertTrue(state is EhFavoriteSyncState.Failed)
        assertTrue((state as EhFavoriteSyncState.Failed).failure is EhFavoriteSyncFailure.CookieInvalid)
        assertTrue(invalidated)
    }

    @Test(expected = CancellationException::class)
    fun syncPropagatesCancellation() = runTest {
        EhFavoritesRepository(
            MemoryFavoriteStore(),
            object : EhAccountConnector {
                override suspend fun currentCredentials() = EhCredentials(Any())
                override suspend fun invalidateCredentials() = Unit
            },
            object : EhFavoriteGateway {
                override suspend fun fetchFavorites(credentials: EhCredentials): EhFavoriteSnapshot {
                    throw CancellationException("caller left")
                }
            },
        ).sync()
    }

    @Test fun categoryPlanIsAdditiveAndLeavesUnmatchedExplicit() {
        val mapping = EhFavoriteMapping(2, "cat")
        val entries = listOf(
            EhFavoriteEntry(2, "a", linkedLanraragiArchiveId = "arc-2"),
            EhFavoriteEntry(2, "b", linkedLanraragiArchiveId = "arc-1"),
            EhFavoriteEntry(2, "c"),
        )
        val plan = EhFavoriteCategoryPlanner.plan(mapping, entries, setOf("arc-1"))
        assertEquals(listOf("arc-2"), plan.additions)
        assertEquals(listOf("arc-1"), plan.alreadyPresent)
        assertEquals(1, plan.unmatchedCount)
    }

    @Test fun executorNeverCallsRemovalAndReturnsRetryableRemainder() = runTest {
        val calls = mutableListOf<String>()
        val executor = EhFavoriteCategorySyncExecutor(object : EhFavoriteCategoryGateway {
            override suspend fun archivesInCategory(categoryId: String) = emptySet<String>()
            override suspend fun addArchiveToCategory(categoryId: String, archiveId: String) {
                calls += archiveId
                if (archiveId == "b") throw EhFavoriteSyncException(EhFavoriteSyncFailure.RateLimited(1000))
            }
        })
        val plan = EhFavoriteCategoryPlan(EhFavoriteMapping(0, "cat"), listOf("a", "b", "c"), emptyList(), emptyList())
        val result = executor.execute(plan)
        assertTrue(result is EhFavoriteCategorySyncResult.Retryable)
        assertEquals(listOf("a", "b"), calls)
        assertEquals(listOf("b", "c"), (result as EhFavoriteCategorySyncResult.Retryable).remaining)
    }

    @Test fun serviceJoinsByExactSourceIdentityAndRequiresFreshConfirmation() = runTest {
        val mappingStore = InMemoryMappingStore(EhFavoriteMapping(1, "cat"))
        val snapshotStore = MemoryFavoriteStore().also {
            it.snapshot = EhFavoriteSnapshot(
                slots = listOf(EhFavoriteSlot(1, "Work")),
                entries = listOf(
                    EhFavoriteEntry(1, "gid-1", "token-1"),
                    EhFavoriteEntry(1, "gid-2", "token-2"),
                ),
                fetchedAt = 5L,
            )
        }
        val added = mutableListOf<String>()
        val service = EhFavoriteCategorySyncService(
            mappings = mappingStore,
            snapshots = snapshotStore,
            categoryGateway = object : EhFavoriteCategoryGateway {
                override suspend fun archivesInCategory(categoryId: String) = setOf("arc-existing")
                override suspend fun addArchiveToCategory(categoryId: String, archiveId: String) { added += archiveId }
            },
            archiveLinks = object : EhFavoriteArchiveLinkGateway {
                override suspend fun resolve(identities: Set<EhFavoriteSourceIdentity>) = listOf(
                    EhFavoriteArchiveLink(EhFavoriteSourceIdentity("gid-1", "token-1"), "arc-new"),
                )
            },
        )
        val preview = (service.preview(1) as EhFavoriteCategoryPreviewResult.Ready).preview
        assertEquals(1, preview.addCount)
        assertEquals(1, preview.unmatchedCount)
        service.confirmAdditiveSync(preview)
        assertEquals(listOf("arc-new"), added)
    }

    private class InMemoryMappingStore(initial: EhFavoriteMapping?) : EhFavoriteMappingStore {
        private var value = initial
        override suspend fun read(slotIndex: Int) = value?.takeIf { it.slotIndex == slotIndex }
        override suspend fun write(mapping: EhFavoriteMapping) { value = mapping }
        override suspend fun clear(slotIndex: Int) { if (value?.slotIndex == slotIndex) value = null }
    }

    private class MemoryFavoriteStore : EhFavoriteStore {
        var snapshot: EhFavoriteSnapshot? = null
        override suspend fun read() = snapshot
        override suspend fun write(snapshot: EhFavoriteSnapshot) { this.snapshot = snapshot }
        override suspend fun clear() { snapshot = null }
    }
}
