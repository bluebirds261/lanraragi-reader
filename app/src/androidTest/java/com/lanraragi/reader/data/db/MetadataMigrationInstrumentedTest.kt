package com.lanraragi.reader.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataMigrationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun v1ToV8CreatesAllRoadmapTablesAndColumnsAndBackfillsTagSearchIndex() {
        createV1Database()
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { v1 ->
            v1.execSQL(
                "INSERT INTO tag_dictionary(namespace,tagKey,translatedName,fullName,intro,dataVersion,updatedAt) VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any?>("artist", "alice", "Alice", "artist:alice", "Known artist", "legacy", 7L),
            )
        }
        val migrated = Room.databaseBuilder(context, ReaderDatabase::class.java, TEST_DB)
            .addMigrations(*allMigrations)
            .allowMainThreadQueries()
            .build()
        val sqlite = migrated.openHelper.writableDatabase

        sqlite.query("PRAGMA table_info(metadata_state)").use { cursor ->
            val columns = mutableListOf<String>()
            val notNull = mutableMapOf<String, Int>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                columns += name
                notNull[name] = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
            }
            assertEquals(
                listOf("sourceKey", "snapshotJson", "baselineJson", "pendingPatchJson", "updatedAt"),
                columns,
            )
            assertEquals(1, notNull["sourceKey"])
            assertEquals(1, notNull["snapshotJson"])
            assertEquals(0, notNull["baselineJson"])
            assertEquals(1, notNull["pendingPatchJson"])
            assertEquals(1, notNull["updatedAt"])
        }

        sqlite.query("PRAGMA table_info(metadata_state)").use { cursor ->
            var primaryKey: String? = null
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndexOrThrow("pk")) == 1) {
                    primaryKey = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
            }
            assertEquals("sourceKey", primaryKey)
        }
        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'metadata_state'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("metadata_state", cursor.getString(0))
        }
        sqlite.query("SELECT COUNT(*) FROM metadata_state").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.query("PRAGMA table_info(metadata_scrape_job)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertTrue("archiveId" in columns)
            assertTrue("serverScope" in columns)
        }
        sqlite.query("PRAGMA table_info(download_task)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertTrue("retryAt" in columns)
            assertTrue("entityTag" in columns)
            assertTrue("lastModified" in columns)
        }
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'eh_favorite_entry'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }

        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'tag_dictionary_fts'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("tag_dictionary_fts", cursor.getString(0))
            }
        sqlite.query(
            "SELECT canonicalKey,namespace,tagKey,translatedName,fullName,intro FROM tag_dictionary_fts",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("artist:alice", cursor.getString(0))
            assertEquals("artist", cursor.getString(1))
            assertEquals("alice", cursor.getString(2))
            assertEquals("Alice", cursor.getString(3))
            assertEquals("artist:alice", cursor.getString(4))
            assertEquals("Known artist", cursor.getString(5))
        }
        sqlite.query(
            "SELECT canonicalKey FROM tag_dictionary_fts WHERE tag_dictionary_fts MATCH ?",
            arrayOf("Alice"),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("artist:alice", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrationPreservesExistingV1Rows() {
        createV1Database()
        val v1 = SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        v1.execSQL(
            "INSERT INTO local_metadata(sourceKey,title,tags,summary,updatedAt) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("local:legacy", "Legacy title", "artist:alice", "Summary", 7L),
        )
        v1.close()

        val migrated = Room.databaseBuilder(context, ReaderDatabase::class.java, TEST_DB)
            .addMigrations(*allMigrations)
            .allowMainThreadQueries()
            .build()
        val sqlite = migrated.openHelper.writableDatabase
        sqlite.query("SELECT title,tags,summary,updatedAt FROM local_metadata WHERE sourceKey = 'local:legacy'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy title", cursor.getString(0))
                assertEquals("artist:alice", cursor.getString(1))
                assertEquals("Summary", cursor.getString(2))
                assertEquals(7L, cursor.getLong(3))
            }
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'metadata_state'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("metadata_state", cursor.getString(0))
            }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "metadata-migration-test.db"
        val allMigrations = arrayOf(
            ReaderDatabase.MIGRATION_1_2,
            ReaderDatabase.MIGRATION_2_3,
            ReaderDatabase.MIGRATION_3_4,
            ReaderDatabase.MIGRATION_4_5,
            ReaderDatabase.MIGRATION_5_6,
            ReaderDatabase.MIGRATION_6_7,
            ReaderDatabase.MIGRATION_7_8,
        )
    }

    /** Builds the exported v1 schema in-place, keeping this test independent of schema assets. */
    private fun createV1Database() {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file.path, null).use { db ->
            V1_TABLES.forEach(db::execSQL)
            V1_INDICES.forEach(db::execSQL)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '340207e74e022a1944268f9ef0c5e238')")
            db.execSQL("PRAGMA user_version = 1")
        }
    }

    private val V1_TABLES = listOf(
        "CREATE TABLE reading_history (sourceKey TEXT NOT NULL, archiveId TEXT, title TEXT NOT NULL, page INTEGER NOT NULL, pageCount INTEGER NOT NULL, firstReadAt INTEGER NOT NULL, lastReadAt INTEGER NOT NULL, PRIMARY KEY(sourceKey))",
        "CREATE TABLE download_task (taskId TEXT NOT NULL, type TEXT NOT NULL, archiveId TEXT, payloadJson TEXT NOT NULL, state TEXT NOT NULL, completedBytes INTEGER NOT NULL, totalBytes INTEGER, retryCount INTEGER NOT NULL, priority INTEGER NOT NULL, error TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(taskId))",
        "CREATE TABLE local_archive (sourceKey TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, fingerprint TEXT NOT NULL, pageCount INTEGER NOT NULL, coverEntry TEXT, availability TEXT NOT NULL, lastVerifiedAt INTEGER NOT NULL, PRIMARY KEY(sourceKey))",
        "CREATE TABLE saved_artifact (artifactId TEXT NOT NULL, archiveId TEXT NOT NULL, filePath TEXT NOT NULL, revision TEXT, byteSize INTEGER NOT NULL, pageCount INTEGER NOT NULL, pinned INTEGER NOT NULL, lastAccessAt INTEGER NOT NULL, PRIMARY KEY(artifactId))",
        "CREATE TABLE local_metadata (sourceKey TEXT NOT NULL, title TEXT, tags TEXT, summary TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(sourceKey))",
        "CREATE TABLE metadata_scrape_job (jobId TEXT NOT NULL, sourceKey TEXT NOT NULL, providerId TEXT NOT NULL, input TEXT, state TEXT NOT NULL, candidatesJson TEXT, patchJson TEXT, error TEXT, nextRunAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(jobId))",
        "CREATE TABLE metadata_provenance (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceKey TEXT NOT NULL, field TEXT NOT NULL, canonicalTag TEXT, providerId TEXT NOT NULL, sourceId TEXT, sourceUrl TEXT, confidence REAL, fetchedAt INTEGER NOT NULL, dataVersion TEXT)",
        "CREATE TABLE tag_dictionary (namespace TEXT NOT NULL, tagKey TEXT NOT NULL, translatedName TEXT, fullName TEXT, intro TEXT, links TEXT, dataVersion TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(namespace,tagKey))",
        "CREATE TABLE tag_frequency (namespace TEXT NOT NULL, tagKey TEXT NOT NULL, source TEXT NOT NULL, count INTEGER NOT NULL, dataVersion TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(namespace,tagKey,source))",
        "CREATE TABLE eh_favorite_slot (slotIndex INTEGER NOT NULL, remoteName TEXT NOT NULL, remoteCount INTEGER NOT NULL, color INTEGER, updatedAt INTEGER NOT NULL, PRIMARY KEY(slotIndex))",
        "CREATE TABLE eh_favorite_mapping (slotIndex INTEGER NOT NULL, lanraragiCategoryId TEXT NOT NULL, mode TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(slotIndex))",
        "CREATE TABLE legacy_import_state (importKey TEXT NOT NULL, sourceVersion INTEGER NOT NULL, completedAt INTEGER NOT NULL, PRIMARY KEY(importKey))",
    )

    private val V1_INDICES = listOf(
        "CREATE INDEX index_reading_history_lastReadAt ON reading_history(lastReadAt)",
        "CREATE INDEX index_reading_history_archiveId ON reading_history(archiveId)",
        "CREATE INDEX index_download_task_state ON download_task(state)",
        "CREATE INDEX index_download_task_archiveId ON download_task(archiveId)",
        "CREATE INDEX index_download_task_priority_createdAt ON download_task(priority,createdAt)",
        "CREATE UNIQUE INDEX index_local_archive_uri ON local_archive(uri)",
        "CREATE INDEX index_local_archive_fingerprint ON local_archive(fingerprint)",
        "CREATE INDEX index_local_archive_lastVerifiedAt ON local_archive(lastVerifiedAt)",
        "CREATE INDEX index_saved_artifact_archiveId ON saved_artifact(archiveId)",
        "CREATE INDEX index_saved_artifact_lastAccessAt ON saved_artifact(lastAccessAt)",
        "CREATE INDEX index_saved_artifact_pinned ON saved_artifact(pinned)",
        "CREATE INDEX index_metadata_scrape_job_sourceKey ON metadata_scrape_job(sourceKey)",
        "CREATE INDEX index_metadata_scrape_job_state ON metadata_scrape_job(state)",
        "CREATE INDEX index_metadata_scrape_job_providerId_nextRunAt ON metadata_scrape_job(providerId,nextRunAt)",
        "CREATE INDEX index_metadata_provenance_sourceKey ON metadata_provenance(sourceKey)",
        "CREATE INDEX index_metadata_provenance_providerId_sourceId ON metadata_provenance(providerId,sourceId)",
        "CREATE INDEX index_tag_dictionary_translatedName ON tag_dictionary(translatedName)",
        "CREATE INDEX index_tag_dictionary_updatedAt ON tag_dictionary(updatedAt)",
        "CREATE INDEX index_tag_frequency_count ON tag_frequency(count)",
        "CREATE INDEX index_eh_favorite_mapping_lanraragiCategoryId ON eh_favorite_mapping(lanraragiCategoryId)",
    )
}
