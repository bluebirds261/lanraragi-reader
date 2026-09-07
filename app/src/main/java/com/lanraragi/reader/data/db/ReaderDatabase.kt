package com.lanraragi.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReadingHistoryEntity::class,
        DownloadTaskEntity::class,
        LocalArchiveEntity::class,
        SavedArtifactEntity::class,
        LocalMetadataEntity::class,
        MetadataScrapeJobEntity::class,
        MetadataProvenanceEntity::class,
        TagDictionaryEntity::class,
        TagFrequencyEntity::class,
        EhFavoriteSlotEntity::class,
        EhFavoriteMappingEntity::class,
        LegacyImportStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun localArchiveDao(): LocalArchiveDao
    abstract fun savedArtifactDao(): SavedArtifactDao
    abstract fun localMetadataDao(): LocalMetadataDao
    abstract fun metadataDao(): MetadataDao
    abstract fun tagKnowledgeDao(): TagKnowledgeDao
    abstract fun ehFavoriteDao(): EhFavoriteDao
    abstract fun legacyImportStateDao(): LegacyImportStateDao

    companion object {
        fun build(context: Context): ReaderDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReaderDatabase::class.java,
            "reader.db",
        ).build()
    }
}
