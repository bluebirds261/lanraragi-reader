package com.lanraragi.reader.data.db

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataBootstrapperTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validSourcesCommitOneVersionedPlanPerSource() = runBlocking {
        writeValidLegacyFiles()
        val imports = mutableListOf<LegacyImport>()

        val report = bootstrapper { legacyImport ->
            imports += legacyImport
            true
        }.run()

        assertEquals(
            listOf(
                AppDataBootstrapper.LOCAL_INDEX_KEY,
                AppDataBootstrapper.HISTORY_KEY,
                AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
                AppDataBootstrapper.OFFLINE_INDEX_KEY,
            ),
            imports.map(LegacyImport::key),
        )
        assertTrue(imports.all { it.sourceVersion == AppDataBootstrapper.SOURCE_VERSION })
        assertSource(report, AppDataBootstrapper.LOCAL_INDEX_KEY, LegacySourceStatus.IMPORTED, 1)
        assertSource(report, AppDataBootstrapper.HISTORY_KEY, LegacySourceStatus.IMPORTED, 2)
        assertSource(report, AppDataBootstrapper.DOWNLOAD_TASKS_KEY, LegacySourceStatus.IMPORTED, 3)
        assertSource(report, AppDataBootstrapper.OFFLINE_INDEX_KEY, LegacySourceStatus.IMPORTED, 2)
    }

    @Test
    fun commitFalseMapsToAlreadyImportedWithoutReportingRows() = runBlocking {
        writeValidLegacyFiles()
        val attempts = mutableMapOf<String, Int>()

        val first = bootstrapper { legacyImport ->
            attempts[legacyImport.key] = 1
            true
        }.run()
        val report = bootstrapper { legacyImport ->
            attempts[legacyImport.key] = 2
            false
        }.run()

        listOf(
            AppDataBootstrapper.LOCAL_INDEX_KEY,
            AppDataBootstrapper.HISTORY_KEY,
            AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
            AppDataBootstrapper.OFFLINE_INDEX_KEY,
        ).forEach { key ->
            assertSource(first, key, LegacySourceStatus.IMPORTED, first.source(key).count)
            assertSource(report, key, LegacySourceStatus.ALREADY_IMPORTED, 0)
            assertEquals(2, attempts[key])
        }
    }

    @Test
    fun malformedSourceIsInvalidButDoesNotPreventOtherValidSourcesFromCommitting() = runBlocking {
        writeValidLegacyFiles()
        write(AppDataBootstrapper.HISTORY_KEY, "{not-json")
        val committedKeys = mutableListOf<String>()

        val report = bootstrapper { legacyImport ->
            committedKeys += legacyImport.key
            true
        }.run()

        assertSource(report, AppDataBootstrapper.HISTORY_KEY, LegacySourceStatus.INVALID, 0)
        assertEquals(
            listOf(
                AppDataBootstrapper.LOCAL_INDEX_KEY,
                AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
                AppDataBootstrapper.OFFLINE_INDEX_KEY,
            ),
            committedKeys,
        )
        assertSource(report, AppDataBootstrapper.LOCAL_INDEX_KEY, LegacySourceStatus.IMPORTED, 1)
        assertSource(report, AppDataBootstrapper.DOWNLOAD_TASKS_KEY, LegacySourceStatus.IMPORTED, 3)
        assertSource(report, AppDataBootstrapper.OFFLINE_INDEX_KEY, LegacySourceStatus.IMPORTED, 2)
    }

    @Test
    fun commitFailureIsIsolatedAndLaterSourcesStillCommit() = runBlocking {
        writeValidLegacyFiles()
        val committedKeys = mutableListOf<String>()

        val report = bootstrapper { legacyImport ->
            committedKeys += legacyImport.key
            if (legacyImport.key == AppDataBootstrapper.HISTORY_KEY) {
                throw IllegalStateException("history transaction failed")
            }
            true
        }.run()

        assertSource(report, AppDataBootstrapper.HISTORY_KEY, LegacySourceStatus.FAILED, 0)
        assertEquals("history transaction failed", report.source(AppDataBootstrapper.HISTORY_KEY).error)
        assertEquals(
            listOf(
                AppDataBootstrapper.LOCAL_INDEX_KEY,
                AppDataBootstrapper.HISTORY_KEY,
                AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
                AppDataBootstrapper.OFFLINE_INDEX_KEY,
            ),
            committedKeys,
        )
        assertSource(report, AppDataBootstrapper.DOWNLOAD_TASKS_KEY, LegacySourceStatus.IMPORTED, 3)
        assertSource(report, AppDataBootstrapper.OFFLINE_INDEX_KEY, LegacySourceStatus.IMPORTED, 2)
    }

    @Test
    fun missingSourcesAreRoomUsableWithoutCommitAttempts() = runBlocking {
        var commitCount = 0

        val report = bootstrapper {
            commitCount += 1
            true
        }.run()

        assertEquals(0, commitCount)
        listOf(
            AppDataBootstrapper.LOCAL_INDEX_KEY,
            AppDataBootstrapper.HISTORY_KEY,
            AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
            AppDataBootstrapper.OFFLINE_INDEX_KEY,
        ).forEach { key ->
            assertSource(report, key, LegacySourceStatus.MISSING, 0)
            assertTrue(report.source(key).roomUsable)
        }
    }

    @Test
    fun cancellationFromCommitPropagatesUnchanged() {
        writeValidLegacyFiles()
        val cancellation = CancellationException("stop bootstrap")

        try {
            runBlocking {
                bootstrapper { throw cancellation }.run()
            }
            fail("Expected cancellation to propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
    }

    private fun bootstrapper(commitImport: suspend (LegacyImport) -> Boolean): AppDataBootstrapper =
        AppDataBootstrapper(
            filesDir = temporaryFolder.root,
            clock = { 123L },
            commitImport = commitImport,
        )

    private fun writeValidLegacyFiles() {
        write(
            AppDataBootstrapper.LOCAL_INDEX_KEY,
            """{"archives":[{"archiveId":"local_42","uri":"content://reader/archive/42"}]}""",
        )
        write(
            AppDataBootstrapper.HISTORY_KEY,
            """[{"arcid":"remote-a","title":"Remote A","page":3},{"arcid":"local_42","title":"Local A","page":1}]""",
        )
        write(
            AppDataBootstrapper.DOWNLOAD_TASKS_KEY,
            """[{"id":"task-a","type":"PAGE"},{"id":"task-b","type":"PAGE"},{"id":"task-c","type":"PAGE"}]""",
        )
        write(
            AppDataBootstrapper.OFFLINE_INDEX_KEY,
            """{"items":[{"arcid":"remote-one"},{"arcid":"remote-two"}]}""",
        )
        writeBytes("offline/remote-one/original.archive", byteArrayOf(1, 2, 3))
        writeBytes("offline/remote-two/original.archive", byteArrayOf(4, 5, 6))
    }

    private fun write(relativePath: String, text: String) {
        val target = File(temporaryFolder.root, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(text)
    }

    private fun writeBytes(relativePath: String, bytes: ByteArray) {
        val target = File(temporaryFolder.root, relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun assertSource(
        report: AppDataBootstrapReport,
        key: String,
        status: LegacySourceStatus,
        count: Int,
    ) {
        val source = report.source(key)
        assertEquals(status, source.status)
        assertEquals(count, source.count)
        assertTrue(source.roomUsable == (status != LegacySourceStatus.INVALID && status != LegacySourceStatus.FAILED))
    }
}
