package com.lanraragi.reader.data.download

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadCoordinatorTest {
    @Test
    fun completedTaskIsPersistedBeforeCatalogCallback() = runTest {
        val store = MemoryDownloadStore()
        val callbackStates = mutableListOf<DurableDownloadState>()
        val registry = DownloadRunnerRegistry.Builder()
            .register(DownloadTaskSpec.Archive::class.java) { _, _ -> DownloadRunResult.Completed(4, 4, "etag") }
            .build()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = DownloadCoordinator(
            scope = appScope,
            store = store,
            registry = registry,
            onCompleted = { task, _ -> callbackStates += requireNotNull(store.rows[task.id]).state },
        )
        val task = DurableDownloadTask(
            id = "one",
            spec = DownloadTaskSpec.Archive(
                DownloadSourceIdentity("arc", "server"),
                DownloadDestination("archive.bin"),
            ),
            createdAtEpochMs = 1,
        )

        coordinator.enqueue(task)
        advanceUntilIdle()

        assertEquals(DurableDownloadState.DONE, store.rows.getValue("one").state)
        assertEquals(listOf(DurableDownloadState.DONE), callbackStates)
        appScope.cancel()
    }

    @Test
    fun retryPolicyOnlyRetriesTransientFailures() {
        val policy = DownloadRetryPolicy(baseDelayMs = 100, maxDelayMs = 1_000)
        assertEquals(300L, policy.retryAt(100, 1, DownloadFailure.Network("offline")))
        assertEquals(700L, policy.retryAt(100, 5, DownloadFailure.Http(429, "limited", 600)))
        assertEquals(null, policy.retryAt(100, 0, DownloadFailure.Http(401, "auth")))
        assertEquals(null, policy.retryAt(100, 0, DownloadFailure.Storage("disk")))
    }

    @Test
    fun atomicFileWriterReplacesOn200AndAppendsOn206() {
        val directory = Files.createTempDirectory("download-atomic-test").toFile()
        try {
            val target = File(directory, "archive.bin")
            AtomicDownloadFile.openForResponse(target, 200).use { it.write(byteArrayOf(1, 2)) }
            AtomicDownloadFile.openForResponse(target, 206).use { it.write(byteArrayOf(3, 4)) }
            AtomicDownloadFile.complete(target)
            assertEquals(listOf<Byte>(1, 2, 3, 4), target.readBytes().toList())

            AtomicDownloadFile.openForResponse(target, 200).use { it.write(byteArrayOf(9)) }
            AtomicDownloadFile.complete(target)
            assertEquals(listOf<Byte>(9), target.readBytes().toList())
            assertFalse(AtomicDownloadFile.partPath(target).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private class MemoryDownloadStore : DownloadTaskStore {
        val rows = linkedMapOf<String, DurableDownloadTask>()
        private val flow = MutableStateFlow<List<DurableDownloadTask>>(emptyList())

        override suspend fun loadAll(): List<DurableDownloadTask> = rows.values.toList()
        override fun observeAll(): Flow<List<DurableDownloadTask>> = flow
        override suspend fun insert(task: DurableDownloadTask) { rows[task.id] = task; publish() }
        override suspend fun replace(task: DurableDownloadTask) { rows[task.id] = task; publish() }
        override suspend fun delete(taskId: String) { rows.remove(taskId); publish() }
        override suspend fun transition(
            taskId: String,
            expected: Set<DurableDownloadState>,
            transform: (DurableDownloadTask) -> DurableDownloadTask,
        ): DurableDownloadTask? {
            val current = rows[taskId] ?: return null
            if (current.state !in expected) return null
            return transform(current).also { rows[taskId] = it; publish() }
        }

        private fun publish() { flow.value = rows.values.toList() }
    }
}
