package com.lanraragi.reader.data.favorites

import com.lanraragi.reader.data.db.EhFavoriteDao
import com.lanraragi.reader.data.db.EhFavoriteEntryEntity
import com.lanraragi.reader.data.db.EhFavoriteMappingEntity
import com.lanraragi.reader.data.db.EhFavoriteSlotEntity
import kotlinx.coroutines.flow.first

/** Room-backed persistence for the fixed ten E-Hentai favorite slots. */
class RoomEhFavoriteStore(
    private val dao: EhFavoriteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : EhFavoriteStore, EhFavoriteMappingStore {

    override suspend fun read(): EhFavoriteSnapshot? {
        val rows = dao.observeSlots().first()
        if (rows.isEmpty()) return null
        val entries = dao.observeEntries().first().map {
            EhFavoriteEntry(
                slotIndex = it.slotIndex,
                gid = it.gid,
                token = it.token.takeIf(String::isNotBlank),
                sourceUrl = it.sourceUrl,
                linkedLanraragiArchiveId = it.linkedLanraragiArchiveId,
            )
        }
        return EhFavoriteSnapshot(
            slots = EhFavoriteSlots.normalize(rows.map { it.toModel() }),
            entries = entries,
            fetchedAt = rows.maxOfOrNull { it.updatedAt } ?: 0L,
        )
    }

    override suspend fun write(snapshot: EhFavoriteSnapshot) {
        val now = clock()
        val fetchedAt = snapshot.fetchedAt.takeIf { it > 0L } ?: now
        val slots = EhFavoriteSlots.normalize(snapshot.slots).map {
                EhFavoriteSlotEntity(
                    slotIndex = it.slotIndex,
                    remoteName = it.remoteName,
                    remoteCount = it.remoteCount,
                    color = it.color,
                    updatedAt = if (it.updatedAt == 0L) fetchedAt else it.updatedAt,
                )
            }
        val entries = snapshot.entries.map {
            EhFavoriteEntryEntity(
                slotIndex = it.slotIndex,
                gid = it.gid,
                token = it.token.orEmpty(),
                sourceUrl = it.sourceUrl,
                linkedLanraragiArchiveId = it.linkedLanraragiArchiveId,
                updatedAt = fetchedAt,
            )
        }
        dao.replaceSnapshot(slots, entries)
    }

    /** Clearing retains ten stable rows, avoiding identity churn in observers. */
    override suspend fun clear() {
        val now = clock()
        dao.upsertSlots(
            EhFavoriteSlots.RANGE.map {
                EhFavoriteSlotEntity(slotIndex = it, remoteName = "", updatedAt = now)
            },
        )
        dao.deleteAllEntries()
    }

    override suspend fun read(slotIndex: Int): EhFavoriteMapping? {
        require(slotIndex in EhFavoriteSlots.RANGE)
        return dao.observeMappings().first().firstOrNull { it.slotIndex == slotIndex }?.toModel()
    }

    override suspend fun write(mapping: EhFavoriteMapping) {
        dao.upsertMapping(
            EhFavoriteMappingEntity(
                slotIndex = mapping.slotIndex,
                lanraragiCategoryId = mapping.lanraragiCategoryId,
                mode = mapping.mode.name,
                updatedAt = mapping.updatedAt,
            ),
        )
    }

    override suspend fun clear(slotIndex: Int) {
        require(slotIndex in EhFavoriteSlots.RANGE)
        dao.deleteMapping(slotIndex)
    }

    private fun EhFavoriteSlotEntity.toModel() = EhFavoriteSlot(slotIndex, remoteName, remoteCount, color, updatedAt)
    private fun EhFavoriteMappingEntity.toModel() = EhFavoriteMapping(
        slotIndex,
        lanraragiCategoryId,
        runCatching { EhFavoriteMappingMode.valueOf(mode) }.getOrDefault(EhFavoriteMappingMode.ONE_WAY_TO_LANRARAGI),
        updatedAt,
    )
}
