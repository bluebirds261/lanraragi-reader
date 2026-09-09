package com.lanraragi.reader.data.favorites

import com.lanraragi.reader.data.db.EhFavoriteDao
import com.lanraragi.reader.data.db.EhFavoriteMappingEntity
import com.lanraragi.reader.data.db.EhFavoriteSlotEntity
import com.lanraragi.reader.data.db.EhFavoriteEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoomEhFavoriteStoreTest {
    @Test fun writeAlwaysPersistsAllTenStableSlots() = runTest {
        val dao = FakeDao()
        val store = RoomEhFavoriteStore(dao, clock = { 50L })

        store.write(EhFavoriteSnapshot(listOf(EhFavoriteSlot(4, "Reading", 6)), fetchedAt = 0L))

        assertEquals((0..9).toList(), dao.slots.value.map { it.slotIndex })
        assertEquals("Reading", dao.slots.value.single { it.slotIndex == 4 }.remoteName)
        assertEquals(50L, dao.slots.value.single { it.slotIndex == 0 }.updatedAt)
    }

    @Test fun slotAndOneWayMappingRoundTrip() = runTest {
        val dao = FakeDao()
        val store = RoomEhFavoriteStore(dao)
        store.write(EhFavoriteSnapshot(listOf(EhFavoriteSlot(2, "Pinned", 3, 99L, 12L))))
        store.write(EhFavoriteMapping(2, "SET_1234567890", updatedAt = 14L))

        val snapshot = store.read()
        val mapping = store.read(2)

        assertNotNull(snapshot)
        assertEquals("Pinned", snapshot!!.normalizedSlots[2].remoteName)
        assertEquals(3, snapshot.normalizedSlots[2].remoteCount)
        assertEquals("SET_1234567890", mapping!!.lanraragiCategoryId)
        assertEquals(EhFavoriteMappingMode.ONE_WAY_TO_LANRARAGI, mapping.mode)
    }

    private class FakeDao : EhFavoriteDao {
        val slots = MutableStateFlow<List<EhFavoriteSlotEntity>>(emptyList())
        val mappings = MutableStateFlow<List<EhFavoriteMappingEntity>>(emptyList())
        val entries = MutableStateFlow<List<EhFavoriteEntryEntity>>(emptyList())

        override fun observeSlots(): Flow<List<EhFavoriteSlotEntity>> = slots
        override fun observeMappings(): Flow<List<EhFavoriteMappingEntity>> = mappings
        override fun observeEntries(): Flow<List<EhFavoriteEntryEntity>> = entries

        override suspend fun upsertSlots(slots: List<EhFavoriteSlotEntity>) {
            this.slots.value = slots
        }

        override suspend fun upsertEntries(entries: List<EhFavoriteEntryEntity>) {
            this.entries.value = entries
        }

        override suspend fun deleteAllEntries() {
            entries.value = emptyList()
        }

        override suspend fun upsertMapping(mapping: EhFavoriteMappingEntity) {
            mappings.value = mappings.value.filterNot { it.slotIndex == mapping.slotIndex } + mapping
        }

        override suspend fun deleteMapping(slotIndex: Int) {
            mappings.value = mappings.value.filterNot { it.slotIndex == slotIndex }
        }
    }
}
