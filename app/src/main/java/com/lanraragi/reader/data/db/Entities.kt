package com.lanraragi.reader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_history",
    indices = [Index("lastReadAt"), Index("archiveId")],
)
data class ReadingHistoryEntity(
    @PrimaryKey val sourceKey: String,
    val archiveId: String? = null,
    val title: String,
    val page: Int = 0,
    val pageCount: Int = 0,
    val firstReadAt: Long,
    val lastReadAt: Long,
)

@Entity(
    tableName = "download_task",
    indices = [Index("state"), Index("archiveId"), Index(value = ["priority", "createdAt"])],
)
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val type: String,
    val archiveId: String? = null,
    val payloadJson: String,
    val state: String,
    val completedBytes: Long = 0,
    val totalBytes: Long? = null,
    val retryCount: Int = 0,
    val priority: Int = 0,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "local_archive",
    indices = [Index(value = ["uri"], unique = true), Index("fingerprint"), Index("lastVerifiedAt")],
)
data class LocalArchiveEntity(
    @PrimaryKey val sourceKey: String,
    val uri: String,
    val title: String,
    val fingerprint: String,
    val pageCount: Int = 0,
    val coverEntry: String? = null,
    val availability: String = "AVAILABLE",
    val lastVerifiedAt: Long,
)

@Entity(
    tableName = "saved_artifact",
    indices = [Index("archiveId"), Index("lastAccessAt"), Index("pinned")],
)
data class SavedArtifactEntity(
    @PrimaryKey val artifactId: String,
    val archiveId: String,
    val filePath: String,
    val revision: String? = null,
    val byteSize: Long = 0,
    val pageCount: Int = 0,
    val pinned: Boolean = false,
    val lastAccessAt: Long,
)

@Entity(tableName = "local_metadata")
data class LocalMetadataEntity(
    @PrimaryKey val sourceKey: String,
    val title: String? = null,
    val tags: String? = null,
    val summary: String? = null,
    val updatedAt: Long,
)

@Entity(
    tableName = "metadata_scrape_job",
    indices = [Index("sourceKey"), Index("state"), Index(value = ["providerId", "nextRunAt"])],
)
data class MetadataScrapeJobEntity(
    @PrimaryKey val jobId: String,
    val sourceKey: String,
    val providerId: String,
    val input: String? = null,
    val state: String,
    val candidatesJson: String? = null,
    val patchJson: String? = null,
    val error: String? = null,
    val nextRunAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "metadata_provenance",
    indices = [Index("sourceKey"), Index(value = ["providerId", "sourceId"])],
)
data class MetadataProvenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val field: String,
    val canonicalTag: String? = null,
    val providerId: String,
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val confidence: Float? = null,
    val fetchedAt: Long,
    val dataVersion: String? = null,
)

@Entity(
    tableName = "tag_dictionary",
    primaryKeys = ["namespace", "tagKey"],
    indices = [Index("translatedName"), Index("updatedAt")],
)
data class TagDictionaryEntity(
    val namespace: String,
    val tagKey: String,
    val translatedName: String? = null,
    val fullName: String? = null,
    val intro: String? = null,
    val links: String? = null,
    val dataVersion: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "tag_frequency",
    primaryKeys = ["namespace", "tagKey", "source"],
    indices = [Index("count")],
)
data class TagFrequencyEntity(
    val namespace: String,
    val tagKey: String,
    val source: String,
    val count: Long,
    val dataVersion: String? = null,
    val updatedAt: Long,
)

@Entity(tableName = "eh_favorite_slot")
data class EhFavoriteSlotEntity(
    @PrimaryKey val slotIndex: Int,
    val remoteName: String,
    val remoteCount: Int = 0,
    val color: Long? = null,
    val updatedAt: Long,
)

@Entity(
    tableName = "eh_favorite_mapping",
    indices = [Index("lanraragiCategoryId")],
)
data class EhFavoriteMappingEntity(
    @PrimaryKey val slotIndex: Int,
    val lanraragiCategoryId: String,
    val mode: String = "ONE_WAY_TO_LANRARAGI",
    val updatedAt: Long,
)

@Entity(tableName = "legacy_import_state")
data class LegacyImportStateEntity(
    @PrimaryKey val importKey: String,
    val sourceVersion: Int,
    val completedAt: Long,
)
