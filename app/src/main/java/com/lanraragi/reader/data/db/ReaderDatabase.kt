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
        MetadataStateEntity::class,
        MetadataScrapeJobEntity::class,
        MetadataProvenanceEntity::class,
        TagDictionaryEntity::class,
        TagDictionaryFtsEntity::class,
        TagFrequencyEntity::class,
        EhFavoriteSlotEntity::class,
        EhFavoriteMappingEntity::class,
        EhFavoriteEntryEntity::class,
        ProgressTaskEntity::class,
        LegacyImportStateEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun localArchiveDao(): LocalArchiveDao
    abstract fun savedArtifactDao(): SavedArtifactDao
    abstract fun progressTaskDao(): ProgressTaskDao
    abstract fun localMetadataDao(): LocalMetadataDao
    abstract fun metadataStateDao(): MetadataStateDao
    abstract fun metadataDao(): MetadataDao
    abstract fun tagKnowledgeDao(): TagKnowledgeDao
    abstract fun ehFavoriteDao(): EhFavoriteDao
    abstract fun legacyImportStateDao(): LegacyImportStateDao

    companion object {
        const val CURRENT_SCHEMA_VERSION = 8

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `metadata_state` (" +
                        "`sourceKey` TEXT NOT NULL, " +
                        "`snapshotJson` TEXT NOT NULL, " +
                        "`baselineJson` TEXT, " +
                        "`pendingPatchJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sourceKey`))",
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata_scrape_job` ADD COLUMN `archiveId` TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata_scrape_job` ADD COLUMN `serverScope` TEXT")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `progress_outbox` (" +
                        "`sourceKey` TEXT NOT NULL, " +
                        "`archiveId` TEXT NOT NULL, " +
                        "`serverScope` TEXT, " +
                        "`page` INTEGER NOT NULL, " +
                        "`pageCount` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sourceKey`))",
                )
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `retryAt` INTEGER")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `entityTag` TEXT")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `lastModified` TEXT")
                db.execSQL("ALTER TABLE `saved_artifact` ADD COLUMN `serverScope` TEXT")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `eh_favorite_entry` (" +
                        "`slotIndex` INTEGER NOT NULL, " +
                        "`gid` TEXT NOT NULL, " +
                        "`token` TEXT NOT NULL, " +
                        "`sourceUrl` TEXT, " +
                        "`linkedLanraragiArchiveId` TEXT, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`slotIndex`, `gid`, `token`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_eh_favorite_entry_slotIndex` ON `eh_favorite_entry` (`slotIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_eh_favorite_entry_linkedLanraragiArchiveId` ON `eh_favorite_entry` (`linkedLanraragiArchiveId`)")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `tag_dictionary_fts` USING fts4(" +
                        "`canonicalKey`, `namespace`, `tagKey`, `translatedName`, `fullName`, `intro`)",
                )
                db.execSQL(
                    "INSERT INTO `tag_dictionary_fts` (`canonicalKey`, `namespace`, `tagKey`, `translatedName`, `fullName`, `intro`) " +
                        "SELECT namespace || ':' || tagKey, namespace, tagKey, " +
                        "COALESCE(translatedName, ''), COALESCE(fullName, ''), COALESCE(intro, '') FROM `tag_dictionary`",
                )
            }
        }

        fun build(context: Context): ReaderDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReaderDatabase::class.java,
            "reader.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
    }
}
