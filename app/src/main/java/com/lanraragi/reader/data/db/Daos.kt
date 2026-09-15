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

    /** Batch variant used by legacy migration and restore flows. */
    @Upsert
    suspend fun upsertAll(entries: List<ReadingHistoryEntity>)

    @Query("SELECT * FROM reading_history WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun find(sourceKey: String): ReadingHistoryEntity?

    @Query("SELECT * FROM reading_history WHERE archiveId = :archiveId LIMIT 1")
    suspend fun findByArchiveId(archiveId: String): ReadingHistoryEntity?

    @Query("DELETE FROM reading_history WHERE sourceKey = :sourceKey")
    suspend fun delete(sourceKey: String)

    @Query("DELETE FROM reading_history WHERE sourceKey IN (:sourceKeys)")
    suspend fun deleteAll(sourceKeys: List<String>)

    @Query("DELETE FROM reading_history WHERE archiveId = :archiveId")
    suspend fun deleteByArchiveId(archiveId: String)

    @Query("DELETE FROM reading_history")
    suspend fun clear()
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_task ORDER BY priority DESC, createdAt ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE state IN (:states) ORDER BY priority DESC, createdAt ASC")
    suspend fun loadByStates(states: List<String>): List<DownloadTaskEntity>

    @Upsert
    suspend fun upsert(task: DownloadTaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<DownloadTaskEntity>)

    @Query("SELECT * FROM download_task WHERE taskId = :taskId LIMIT 1")
    suspend fun find(taskId: String): DownloadTaskEntity?

    @Query("DELETE FROM download_task")
    suspend fun clear()

    @Query("DELETE FROM download_task WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}

@Dao
interface LocalArchiveDao {
    @Query("SELECT * FROM local_archive ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalArchiveEntity>>

    @Query("SELECT * FROM local_archive WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun find(sourceKey: String): LocalArchiveEntity?

    @Query("SELECT * FROM local_archive WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): LocalArchiveEntity?

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

    @Upsert
    suspend fun upsertAll(artifacts: List<SavedArtifactEntity>)

    @Query("DELETE FROM saved_artifact WHERE artifactId = :artifactId")
    suspend fun deleteById(artifactId: String)

    @Query("DELETE FROM saved_artifact WHERE artifactId LIKE 'offline:%'")
    suspend fun clearLegacyOffline()
}

@Dao
interface ProgressTaskDao {
    @Query("SELECT * FROM progress_outbox ORDER BY updatedAt ASC")
    suspend fun loadAll(): List<ProgressTaskEntity>

    @Upsert
    suspend fun upsert(task: ProgressTaskEntity)

    @Query("DELETE FROM progress_outbox WHERE sourceKey = :sourceKey")
    suspend fun delete(sourceKey: String)
}

@Dao
interface LocalMetadataDao {
    @Query("SELECT * FROM local_metadata")
    suspend fun all(): List<LocalMetadataEntity>

    @Query("SELECT * FROM local_metadata WHERE sourceKey = :sourceKey")
    fun observe(sourceKey: String): Flow<LocalMetadataEntity?>

    @Query("SELECT * FROM local_metadata WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun find(sourceKey: String): LocalMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: LocalMetadataEntity)
}

@Dao
interface MetadataStateDao {
    @Query("SELECT * FROM metadata_state WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun find(sourceKey: String): MetadataStateEntity?

    @Upsert
    suspend fun upsert(state: MetadataStateEntity)
}

@Dao
interface MetadataDao {
    @Query("SELECT * FROM metadata_scrape_job ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<MetadataScrapeJobEntity>>

    @Upsert
    suspend fun upsertJob(job: MetadataScrapeJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvenance(entries: List<MetadataProvenanceEntity>)

    @Query("DELETE FROM metadata_provenance WHERE sourceKey = :sourceKey")
    suspend fun deleteProvenance(sourceKey: String)

    @Query("SELECT * FROM metadata_provenance WHERE sourceKey = :sourceKey ORDER BY fetchedAt DESC")
    fun observeProvenance(sourceKey: String): Flow<List<MetadataProvenanceEntity>>

    @Query("SELECT * FROM metadata_scrape_job WHERE jobId = :jobId LIMIT 1")
    suspend fun findJob(jobId: String): MetadataScrapeJobEntity?

    @Query("SELECT * FROM metadata_scrape_job WHERE jobId = :key OR (sourceKey || ':plugin:' || lower(providerId)) = :key ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findJobByKey(key: String): MetadataScrapeJobEntity?

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

    @Query(
        """
        SELECT d.* FROM tag_dictionary AS d
        INNER JOIN tag_dictionary_fts AS f
            ON d.namespace = f.namespace AND d.tagKey = f.tagKey
        WHERE tag_dictionary_fts MATCH :match
        LIMIT :limit
        """,
    )
    suspend fun searchFts(match: String, limit: Int): List<TagDictionaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceDictionary(entries: List<TagDictionaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceFrequencies(entries: List<TagFrequencyEntity>)

    @Query("SELECT * FROM tag_dictionary ORDER BY namespace, tagKey")
    fun loadDictionarySync(): List<TagDictionaryEntity>

    @Query("SELECT * FROM tag_frequency ORDER BY namespace, tagKey, source")
    fun loadFrequenciesSync(): List<TagFrequencyEntity>

    @Query("DELETE FROM tag_dictionary")
    fun clearDictionarySync()

    @Query("DELETE FROM tag_frequency")
    fun clearFrequenciesSync()

    @Query("DELETE FROM tag_dictionary_fts")
    fun clearSearchIndexSync()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceDictionarySync(entries: List<TagDictionaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceFrequenciesSync(entries: List<TagFrequencyEntity>)

    @Insert
    fun replaceSearchIndexSync(entries: List<TagDictionaryFtsEntity>)
}

@Dao
interface LegacyImportStateDao {
    @Query("SELECT * FROM legacy_import_state WHERE importKey = :key LIMIT 1")
    suspend fun find(key: String): LegacyImportStateEntity?

    /** Upsert is required when a newer sourceVersion re-runs the same import key. */
    @Upsert
    suspend fun upsert(state: LegacyImportStateEntity)
}
