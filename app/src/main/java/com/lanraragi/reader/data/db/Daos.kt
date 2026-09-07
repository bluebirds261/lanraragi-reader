package com.lanraragi.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReadingHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: ReadingHistoryEntity)
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_task ORDER BY priority DESC, createdAt ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE state IN (:states) ORDER BY priority DESC, createdAt ASC")
    suspend fun loadByStates(states: List<String>): List<DownloadTaskEntity>

    @Upsert
    suspend fun upsert(task: DownloadTaskEntity)
}

@Dao
interface LocalArchiveDao {
    @Query("SELECT * FROM local_archive ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalArchiveEntity>>

    @Upsert
    suspend fun upsertAll(archives: List<LocalArchiveEntity>)
}

@Dao
interface SavedArtifactDao {
    @Query("SELECT * FROM saved_artifact WHERE archiveId = :archiveId LIMIT 1")
    suspend fun findByArchiveId(archiveId: String): SavedArtifactEntity?

    @Query("SELECT * FROM saved_artifact ORDER BY lastAccessAt ASC")
    suspend fun loadForEviction(): List<SavedArtifactEntity>

    @Upsert
    suspend fun upsert(artifact: SavedArtifactEntity)
}

@Dao
interface LocalMetadataDao {
    @Query("SELECT * FROM local_metadata WHERE sourceKey = :sourceKey")
    fun observe(sourceKey: String): Flow<LocalMetadataEntity?>

    @Upsert
    suspend fun upsert(metadata: LocalMetadataEntity)
}

@Dao
interface MetadataDao {
    @Query("SELECT * FROM metadata_scrape_job ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<MetadataScrapeJobEntity>>

    @Upsert
    suspend fun upsertJob(job: MetadataScrapeJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvenance(entries: List<MetadataProvenanceEntity>)

    @Query("SELECT * FROM metadata_provenance WHERE sourceKey = :sourceKey ORDER BY fetchedAt DESC")
    fun observeProvenance(sourceKey: String): Flow<List<MetadataProvenanceEntity>>
}

@Dao
interface TagKnowledgeDao {
    @Query(
        """
        SELECT * FROM tag_dictionary
        WHERE namespace = :namespace AND tagKey = :tagKey
        LIMIT 1
        """,
    )
    suspend fun find(namespace: String, tagKey: String): TagDictionaryEntity?

    @Query(
        """
        SELECT * FROM tag_dictionary
        WHERE tagKey LIKE :query OR translatedName LIKE :query OR intro LIKE :query
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<TagDictionaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceDictionary(entries: List<TagDictionaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceFrequencies(entries: List<TagFrequencyEntity>)
}

@Dao
interface EhFavoriteDao {
    @Query("SELECT * FROM eh_favorite_slot ORDER BY slotIndex")
    fun observeSlots(): Flow<List<EhFavoriteSlotEntity>>

    @Query("SELECT * FROM eh_favorite_mapping")
    fun observeMappings(): Flow<List<EhFavoriteMappingEntity>>

    @Upsert
    suspend fun upsertSlots(slots: List<EhFavoriteSlotEntity>)

    @Upsert
    suspend fun upsertMapping(mapping: EhFavoriteMappingEntity)
}

@Dao
interface LegacyImportStateDao {
    @Query("SELECT * FROM legacy_import_state WHERE importKey = :key LIMIT 1")
    suspend fun find(key: String): LegacyImportStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(state: LegacyImportStateEntity)
}
