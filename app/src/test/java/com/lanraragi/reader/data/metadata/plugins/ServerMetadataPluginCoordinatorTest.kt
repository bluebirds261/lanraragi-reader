package com.lanraragi.reader.data.metadata.plugins

import com.lanraragi.reader.domain.model.ArchiveIdentity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerMetadataPluginCoordinatorTest {
    @Test
    fun discoversAndRunsSynchronousPluginAsPreviewOnly() = runTest {
        val gateway = FakeGateway(syncBody = syncResult())
        val store = MemoryStore()
        val coordinator = coordinator(gateway, store)

        assertEquals(listOf(plugin), coordinator.discover())
        val candidate = coordinator.run(request())

        requireNotNull(candidate)
        assertEquals("Scraped title", candidate.patch.title?.value)
        assertTrue(candidate.patch.addTags.any { it.full == "artist:tester" })
        assertEquals("https://example.test/1", candidate.patch.title?.provenance?.sourceId)
        assertEquals(12L, candidate.patch.title?.provenance?.fetchedAt)
        assertEquals(0, gateway.detailCalls)
        assertEquals(MetadataPluginJobPhase.READY, store.latest.phase)
    }

    @Test
    fun asyncWaitsForTerminalThenReadsDetailAndReturnsCandidate() = runTest {
        val gateway = FakeGateway(
            states = ArrayDeque(listOf("queued", "running", "finished")),
            detailBody = detailResult(),
        )
        val store = MemoryStore()
        val coordinator = coordinator(gateway, store)

        val candidate = coordinator.run(request(MetadataPluginExecution.ASYNCHRONOUS))

        requireNotNull(candidate)
        assertEquals("job-7", candidate.jobId)
        assertEquals("Pretty title", candidate.patch.title?.value)
        assertEquals(1, gateway.detailCalls)
        assertEquals(MetadataPluginJobPhase.READY, store.latest.phase)
    }

    @Test
    fun asyncAcceptsVersionedResultDataWrapper() = runTest {
        val gateway = FakeGateway(
            states = ArrayDeque(listOf("finished")),
            detailBody = """{"result":{"data":{"new_tags":"language:english","title":"Wrapped title"}}}""",
            queueBody = """{"job":86,"operation":"queue_plugin_exec","success":1}""",
        )
        val candidate = coordinator(gateway, MemoryStore()).run(request(MetadataPluginExecution.ASYNCHRONOUS))

        assertEquals("86", candidate?.jobId)
        assertEquals("Wrapped title", candidate?.patch?.title?.value)
    }

    @Test
    fun resumesPersistedJobWithoutQueueingAgain() = runTest {
        val gateway = FakeGateway(states = ArrayDeque(listOf("finished")), detailBody = detailResult())
        val store = MemoryStore().apply {
            writes += MetadataPluginJobRecord(
                key = ServerMetadataPluginCoordinator.recordKey(ArchiveIdentity.Remote("arcid"), "ehplugin"),
                target = ArchiveIdentity.Remote("arcid"), pluginNamespace = "ehplugin",
                argument = "https://example.test/1", jobId = "job-existing",
                phase = MetadataPluginJobPhase.RUNNING, updatedAt = 3L, createdAt = 2L,
            )
        }
        val candidate = coordinator(gateway, store).run(request(MetadataPluginExecution.ASYNCHRONOUS))
        assertEquals("job-existing", candidate?.jobId)
        assertEquals(0, gateway.queueCalls)
        assertEquals(2L, store.latest.createdAt)
    }

    @Test
    fun processRecoveryResumesEveryPersistedJobWithoutQueueingAgain() = runTest {
        val gateway = FakeGateway(states = ArrayDeque(listOf("finished")), detailBody = detailResult())
        val record = MetadataPluginJobRecord(
            key = ServerMetadataPluginCoordinator.recordKey(ArchiveIdentity.Remote("arcid"), "ehplugin"),
            target = ArchiveIdentity.Remote("arcid"),
            pluginNamespace = "ehplugin",
            argument = "https://example.test/1",
            jobId = "job-existing",
            phase = MetadataPluginJobPhase.RUNNING,
            updatedAt = 3L,
            createdAt = 2L,
        )
        val store = MemoryStore().apply {
            writes += record
            recoverable += record
        }

        val recovered = coordinator(gateway, store).recover()

        assertEquals(listOf("job-existing"), recovered.mapNotNull(MetadataPluginCandidate::jobId))
        assertEquals(0, gateway.queueCalls)
        assertEquals(MetadataPluginJobPhase.READY, store.latest.phase)
    }

    @Test
    fun retryAfterCooldownPreventsImmediateResume() = runTest {
        val store = MemoryStore().apply {
            writes += MetadataPluginJobRecord(
                key = ServerMetadataPluginCoordinator.recordKey(ArchiveIdentity.Remote("arcid"), "ehplugin"),
                target = ArchiveIdentity.Remote("arcid"), pluginNamespace = "ehplugin",
                phase = MetadataPluginJobPhase.RETRYABLE, updatedAt = 10L, createdAt = 10L, nextRunAt = 100L,
            )
        }
        val gateway = FakeGateway()
        val result = ServerMetadataPluginCoordinator(gateway, store, clock = { 50L }, wait = {})
            .run(request(MetadataPluginExecution.ASYNCHRONOUS))
        assertNull(result)
        assertEquals(0, gateway.queueCalls)
    }

    @Test
    fun recoveryArmsRetryWakeupAndResumesAfterCooldown() = runTest {
        var now = 50L
        val record = MetadataPluginJobRecord(
            key = ServerMetadataPluginCoordinator.recordKey(ArchiveIdentity.Remote("arcid"), "ehplugin"),
            target = ArchiveIdentity.Remote("arcid"), pluginNamespace = "ehplugin",
            argument = "https://example.test/1", phase = MetadataPluginJobPhase.RETRYABLE,
            updatedAt = 10L, createdAt = 10L, nextRunAt = 100L,
        )
        val store = MemoryStore().apply {
            writes += record
            recoverable += record
        }
        val waits = mutableListOf<Long>()
        val coordinator = ServerMetadataPluginCoordinator(
            FakeGateway(states = ArrayDeque(listOf("finished")), detailBody = detailResult()),
            store,
            clock = { now },
            wait = { duration -> waits += duration; now += duration },
            wakeScope = this,
        )

        assertTrue(coordinator.recover().isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(50L), waits)
        assertEquals(MetadataPluginJobPhase.READY, store.latest.phase)
    }

    @Test
    fun repeatedRecoveryReplacesPriorRetryWakeup() = runTest {
        val record = MetadataPluginJobRecord(
            key = ServerMetadataPluginCoordinator.recordKey(ArchiveIdentity.Remote("arcid"), "ehplugin"),
            target = ArchiveIdentity.Remote("arcid"), pluginNamespace = "ehplugin",
            phase = MetadataPluginJobPhase.RETRYABLE, updatedAt = 10L, createdAt = 10L, nextRunAt = 100L,
        )
        val store = MemoryStore().apply { writes += record; recoverable += record }
        val waits = mutableListOf<Long>()
        val holdWake = CompletableDeferred<Unit>()
        val wakeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = ServerMetadataPluginCoordinator(
            FakeGateway(), store, clock = { 50L }, wait = { duration -> waits += duration; holdWake.await() }, wakeScope = wakeScope,
        )

        coordinator.recover()
        coordinator.recover()
        advanceUntilIdle()

        assertEquals(1, waits.size)
        assertEquals(50L, waits.single())
        wakeScope.cancel()
    }

    @Test
    fun terminalFailureIsPersistedWithoutReadingDetail() = runTest {
        val gateway = FakeGateway(states = ArrayDeque(listOf("failed")))
        val store = MemoryStore()

        assertNull(coordinator(gateway, store).run(request(MetadataPluginExecution.ASYNCHRONOUS)))
        assertEquals(MetadataPluginJobPhase.FAILED, store.latest.phase)
        assertEquals(0, gateway.detailCalls)
    }

    @Test
    fun timeoutRetainsJobForLaterRetry() = runTest {
        var now = 0L
        val gateway = FakeGateway(states = ArrayDeque(listOf("running", "running")))
        val store = MemoryStore()
        val coordinator = ServerMetadataPluginCoordinator(
            gateway, store, clock = { now }, wait = { now += 10L },
            polling = MetadataPluginPollingPolicy(timeoutMillis = 5L, intervalMillis = 10L),
        )

        assertNull(coordinator.run(request(MetadataPluginExecution.ASYNCHRONOUS)))
        assertEquals(MetadataPluginJobPhase.TIMED_OUT, store.latest.phase)
        assertEquals("job-7", store.latest.jobId)
    }

    @Test
    fun cancellationIsPropagatedAndNeverPersistedAsFailure() = runTest {
        val gateway = FakeGateway(syncFailure = CancellationException("stop"))
        val store = MemoryStore()
        val result = runCatching { coordinator(gateway, store).run(request()) }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(store.writes.isNotEmpty())
    }

    @Test
    fun cancellationAfterQueueRetainsResumableJob() = runTest {
        val gateway = object : FakeGateway() {
            override suspend fun getMinionStatus(jobId: String): ServerPluginJobStatus {
                throw CancellationException("stop")
            }
        }
        val store = MemoryStore()
        val result = runCatching { coordinator(gateway, store).run(request(MetadataPluginExecution.ASYNCHRONOUS)) }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(MetadataPluginJobPhase.QUEUED, store.latest.phase)
        assertEquals("job-7", store.latest.jobId)
    }

    @Test
    fun rateLimitAndNetworkFailuresAreRetryable() = runTest {
        val rateStore = MemoryStore()
        assertNull(coordinator(FakeGateway(syncFailure = MetadataPluginRemoteException(429, "limited")), rateStore).run(request()))
        assertEquals(MetadataPluginJobPhase.RETRYABLE, rateStore.latest.phase)

        val networkStore = MemoryStore()
        assertNull(coordinator(FakeGateway(syncFailure = IOException("offline")), networkStore).run(request()))
        assertEquals(MetadataPluginJobPhase.RETRYABLE, networkStore.latest.phase)
    }

    @Test
    fun localAndTankTargetsNeverReachServer() = runTest {
        val gateway = FakeGateway()
        val store = MemoryStore()
        val coordinator = coordinator(gateway, store)

        assertTrue(runCatching {
            coordinator.run(request(target = ArchiveIdentity.LocalSaf("content://library/1")))
        }.exceptionOrNull() is UnsupportedMetadataPluginTargetException)
        assertTrue(runCatching {
            coordinator.run(request(target = ArchiveIdentity.Tankoubon("TANK_x")))
        }.exceptionOrNull() is UnsupportedMetadataPluginTargetException)
        assertEquals(0, gateway.useCalls + gateway.queueCalls)
        assertTrue(store.writes.isEmpty())
    }

    private fun coordinator(gateway: FakeGateway, store: MemoryStore) = ServerMetadataPluginCoordinator(
        gateway, store, clock = { 12L }, wait = {},
    )

    private fun request(
        execution: MetadataPluginExecution = MetadataPluginExecution.SYNCHRONOUS,
        target: ArchiveIdentity = ArchiveIdentity.Remote("arcid"),
    ) = MetadataPluginRequest(target, plugin, argument = "https://example.test/1", execution = execution)

    private class MemoryStore : MetadataPluginJobStore {
        val writes = mutableListOf<MetadataPluginJobRecord>()
        val recoverable = mutableListOf<MetadataPluginJobRecord>()
        val latest: MetadataPluginJobRecord get() = writes.last()
        override suspend fun write(record: MetadataPluginJobRecord) { writes += record }
        override suspend fun read(key: String): MetadataPluginJobRecord? = writes.lastOrNull { it.key == key }
        override suspend fun loadRecoverable(): List<MetadataPluginJobRecord> = recoverable.toList()
    }

    private open class FakeGateway(
        private val syncBody: String = "{\"data\":{}}",
        private val detailBody: String = "{\"result\":{}}",
        private val states: ArrayDeque<String> = ArrayDeque(listOf("finished")),
        private val syncFailure: Throwable? = null,
        private val queueBody: String = "job-7",
    ) : ServerMetadataPluginGateway {
        var useCalls = 0
        var queueCalls = 0
        var detailCalls = 0
        override suspend fun discoverMetadataPlugins(): List<ServerMetadataPlugin> = listOf(plugin)
        override suspend fun usePlugin(arcid: String, pluginNamespace: String, argument: String?): String {
            useCalls++
            syncFailure?.let { throw it }
            return syncBody
        }
        override suspend fun queuePlugin(arcid: String, pluginNamespace: String, argument: String?): String {
            queueCalls++
            return queueBody
        }
        override suspend fun getMinionStatus(jobId: String): ServerPluginJobStatus =
            ServerPluginJobStatus(jobId, states.removeFirstOrNull() ?: "running", "failed note")
        override suspend fun getMinionDetail(jobId: String): String { detailCalls++; return detailBody }
    }

    private companion object {
        val plugin = ServerMetadataPlugin("ehplugin", "EHentai")
        fun syncResult() = """{"data":{"new_tags":"artist:tester, source:https://example.test/1","title":"Scraped title"}}"""
        fun detailResult() = """{"result":{"new_tags":"language:english","title":"Pretty title","summary":"Summary"}}"""
    }
}
