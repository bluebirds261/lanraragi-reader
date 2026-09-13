package com.lanraragi.reader.data.db

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyStateMigrationInstrumentedTest {
    private val targetContext = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var isolatedFilesDir: File
    private lateinit var isolatedContext: Context
    private lateinit var database: ReaderDatabase

    @Before
    fun setUp() {
        isolatedFilesDir = File(
            targetContext.cacheDir,
            "legacy-state-migration-${UUID.randomUUID()}",
        )
        check(isolatedFilesDir.mkdirs()) { "Unable to create test filesDir" }
        isolatedContext = FilesDirContext(targetContext, isolatedFilesDir)
        database = Room.inMemoryDatabaseBuilder(isolatedContext, ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::isolatedFilesDir.isInitialized) isolatedFilesDir.deleteRecursively()
    }

    @Test
    fun importsAllLegacySourcesAtomicallyAndKeepsSourceFilesForRollback() = runBlocking {
        writeLegacySources()

        val first = AppDataBootstrapper(isolatedContext, database, clock = { FIXED_CLOCK }).run()

        assertImported(first, AppDataBootstrapper.HISTORY_KEY, 1)
        assertImported(first, AppDataBootstrapper.LOCAL_INDEX_KEY, 1)
        assertImported(first, AppDataBootstrapper.DOWNLOAD_TASKS_KEY, 1)
        assertImported(first, AppDataBootstrapper.OFFLINE_INDEX_KEY, 2)

        val history = requireNotNull(database.readingHistoryDao().find("remote:remote-history"))
        assertEquals("History title", history.title)
        assertEquals(4, history.page)
        assertEquals(12, history.pageCount)

        val local = requireNotNull(database.localArchiveDao().findByUri("content://migration/local-one"))
        assertEquals("Local title", local.title)
        assertEquals(8, local.pageCount)
        assertEquals("AVAILABLE", local.availability)

        val waiting = requireNotNull(database.downloadTaskDao().find("waiting-task"))
        assertEquals("FAILED", waiting.state)
        assertEquals(DownloadTaskLegacyImporter.LEGACY_SPEC_UNAVAILABLE, waiting.error)
        assertEquals(FIXED_CLOCK, waiting.createdAt)

        val firstArtifact = requireNotNull(database.savedArtifactDao().findByArchiveId("offline-one"))
        assertEquals("offline:offline-one", firstArtifact.artifactId)
        assertEquals(3L, firstArtifact.byteSize)
        assertEquals(15, firstArtifact.pageCount)
        val secondArtifact = requireNotNull(database.savedArtifactDao().findByArchiveId("offline-two"))
        assertEquals("offline:offline-two", secondArtifact.artifactId)
        assertEquals(4L, secondArtifact.byteSize)
        assertEquals(6, secondArtifact.pageCount)

        listOf(
            AppDataBootstrapper.HISTORY_KEY,
            AppDataBootstrapper.LOCAL_INDEX_KEY,
            AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
            AppDataBootstrapper.OFFLINE_INDEX_KEY,
        ).forEach { key ->
            val marker = database.legacyImportStateDao().find(key)
            assertNotNull("missing import marker for $key", marker)
            assertEquals(AppDataBootstrapper.SOURCE_VERSION, marker?.sourceVersion)
            assertEquals(FIXED_CLOCK, marker?.completedAt)
        }

        assertTrue(legacyFile(AppDataBootstrapper.HISTORY_KEY).isFile)
        assertTrue(legacyFile(AppDataBootstrapper.LOCAL_INDEX_KEY).isFile)
        assertTrue(legacyFile(AppDataBootstrapper.DOWNLOAD_TASKS_KEY).isFile)
        assertTrue(legacyFile(AppDataBootstrapper.OFFLINE_INDEX_KEY).isFile)
        assertTrue(offlineOriginal("offline-one").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(offlineOriginal("offline-two").readBytes().contentEquals(byteArrayOf(4, 5, 6, 7)))

        // A Room edit after migration is authoritative; a repeated bootstrap
        // must neither overwrite it nor create duplicate projection rows.
        database.readingHistoryDao().upsert(history.copy(title = "Room edit"))
        val second = AppDataBootstrapper(isolatedContext, database, clock = { FIXED_CLOCK + 1 }).run()

        assertAlreadyImported(second, AppDataBootstrapper.HISTORY_KEY)
        assertAlreadyImported(second, AppDataBootstrapper.LOCAL_INDEX_KEY)
        assertAlreadyImported(second, AppDataBootstrapper.DOWNLOAD_TASKS_KEY)
        assertAlreadyImported(second, AppDataBootstrapper.OFFLINE_INDEX_KEY)
        assertEquals("Room edit", database.readingHistoryDao().find("remote:remote-history")?.title)
        assertEquals(1, database.readingHistoryDao().observeAll().first().size)
        assertEquals(1, database.localArchiveDao().observeAll().first().size)
        assertEquals(1, database.downloadTaskDao().observeAll().first().size)
        assertEquals(2, database.savedArtifactDao().loadForEviction().size)
    }

    @Test
    fun corruptHistoryDoesNotPreventValidDownloadsFromMigrating() = runBlocking {
        legacyFile(AppDataBootstrapper.HISTORY_KEY).writeText("{ definitely not json")
        legacyFile(AppDataBootstrapper.DOWNLOAD_TASKS_KEY).apply {
            parentFile?.mkdirs()
            writeText("""[{"id":"download-only","type":"OFFLINE_CACHE","state":"DONE"}]""")
        }

        val report = AppDataBootstrapper(isolatedContext, database, clock = { FIXED_CLOCK }).run()

        assertEquals(LegacySourceStatus.INVALID, report.source(AppDataBootstrapper.HISTORY_KEY).status)
        assertEquals(LegacySourceStatus.IMPORTED, report.source(AppDataBootstrapper.DOWNLOAD_TASKS_KEY).status)
        assertEquals(1, report.source(AppDataBootstrapper.DOWNLOAD_TASKS_KEY).count)
        assertNotNull(database.downloadTaskDao().find("download-only"))
        assertNull(database.legacyImportStateDao().find(AppDataBootstrapper.HISTORY_KEY))
        assertNotNull(database.legacyImportStateDao().find(AppDataBootstrapper.DOWNLOAD_TASKS_KEY))
        assertTrue(legacyFile(AppDataBootstrapper.HISTORY_KEY).isFile)
    }

    private fun writeLegacySources() {
        legacyFile(AppDataBootstrapper.HISTORY_KEY).writeText(
            """[{"arcid":"remote-history","title":"History title","timestamp":100,"page":4,"pageCount":12}]""",
        )
        legacyFile(AppDataBootstrapper.LOCAL_INDEX_KEY).apply {
            parentFile?.mkdirs()
            writeText(
                """{"archives":[{"summary":"content://migration/local-one","title":"Local title","pagecount":8,"availability":"AVAILABLE"}]}""",
            )
        }
        legacyFile(AppDataBootstrapper.DOWNLOAD_TASKS_KEY).apply {
            parentFile?.mkdirs()
            writeText(
                """[{"id":"waiting-task","type":"OFFLINE_CACHE","arcid":"remote-download","state":"WAITING"}]""",
            )
        }
        legacyFile(AppDataBootstrapper.OFFLINE_INDEX_KEY).apply {
            parentFile?.mkdirs()
            writeText(
                """{"items":[{"arcid":"offline-one","pageCount":15,"lastAccess":30},{"arcid":"offline-two","pageCount":6,"lastAccess":40}]}""",
            )
        }
        offlineOriginal("offline-one").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        offlineOriginal("offline-two").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
    }

    private fun assertImported(report: AppDataBootstrapReport, key: String, count: Int) {
        assertEquals(LegacySourceStatus.IMPORTED, report.source(key).status)
        assertEquals(count, report.source(key).count)
    }

    private fun assertAlreadyImported(report: AppDataBootstrapReport, key: String) {
        assertEquals(LegacySourceStatus.ALREADY_IMPORTED, report.source(key).status)
        assertEquals(0, report.source(key).count)
    }

    private fun legacyFile(relativePath: String): File = File(isolatedFilesDir, relativePath)

    private fun offlineOriginal(arcid: String): File = File(isolatedFilesDir, "offline/$arcid/original.archive")

    private class FilesDirContext(
        base: Context,
        private val testFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = testFilesDir
    }

    private companion object {
        const val FIXED_CLOCK = 12_345L
    }
}
