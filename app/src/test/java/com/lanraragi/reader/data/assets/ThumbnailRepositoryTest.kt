package com.lanraragi.reader.data.assets

import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import com.lanraragi.reader.data.diagnostics.PerformanceMetric
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailRepositoryTest {
    @Test
    fun directReadyUsesRevisionZero() = runTest {
        var probeCount = 0
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe {
                    probeCount += 1
                    return ThumbnailProbe.Ready(ThumbnailResource("cover://$arcid"))
                }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()
        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(1, probeCount)
        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://arc-1"), revision = 0L),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun reusingReadyCoverRecordsAnonymousCacheHitSample() = runTest {
        val diagnostics = DiagnosticsFacade()
        val repository = ThumbnailRepository(
            gateway = readyGateway(),
            scope = this,
            wait = {},
            diagnostics = diagnostics,
        )

        repository.requestCover("private-archive-id")
        advanceUntilIdle()
        repository.requestCover("private-archive-id")

        val hit = diagnostics.metrics.snapshot().single {
            it.metric == PerformanceMetric.THUMBNAIL_CACHE_HIT_RATE
        }
        assertEquals(2, hit.count)
        assertEquals(100.0, hit.latest!!, 0.0)
        assertEquals(50.0, hit.average!!, 0.0)
        assertTrue(diagnostics.log.snapshot.value.none { it.toString().contains("private-archive-id") })
    }

    @Test
    fun serverKeySwitchIsolatesSameArchiveStateAndRestoresPreviousServer() = runTest {
        var serverKey = "server-a"
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) =
                    ThumbnailProbe.Ready(ThumbnailResource("$serverKey://$arcid"))

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
            serverKeyProvider = { serverKey },
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()
        assertEquals(
            CoverState.Ready(ThumbnailResource("server-a://arc-1"), revision = 0L),
            repository.cover("arc-1").value,
        )

        serverKey = "server-b"
        assertEquals(CoverState.Unrequested(), repository.cover("arc-1").value)
        repository.requestCover("arc-1")
        advanceUntilIdle()
        assertEquals(
            CoverState.Ready(ThumbnailResource("server-b://arc-1"), revision = 0L),
            repository.cover("arc-1").value,
        )

        serverKey = "server-a"
        assertEquals(
            CoverState.Ready(ThumbnailResource("server-a://arc-1"), revision = 0L),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun completedIdleEntriesEvictLeastRecentlyUsedWhenCapacityIsExceeded() = runTest {
        val repository = ThumbnailRepository(
            gateway = readyGateway(),
            scope = this,
            wait = {},
            maxEntries = 2,
        )

        repository.requestCover("arc-a")
        advanceUntilIdle()
        repository.requestCover("arc-b")
        advanceUntilIdle()
        repository.cover("arc-a") // Make arc-b the least-recently-used idle entry.
        repository.requestCover("arc-c")
        advanceUntilIdle()

        // A removed entry's old flow retains its last value, so inspect the
        // freshly looked-up key to verify eviction recreated Unrequested state.
        assertTrue(repository.cover("arc-b").value is CoverState.Unrequested)
    }

    @Test
    fun retainedEntrySurvivesCapacityPruningUntilReleased() = runTest {
        val repository = ThumbnailRepository(
            gateway = readyGateway(),
            scope = this,
            wait = {},
            maxEntries = 2,
        )

        repository.requestCover("arc-a")
        advanceUntilIdle()
        repository.retainCover("arc-a")
        repository.requestCover("arc-b")
        advanceUntilIdle()
        repository.requestCover("arc-c")
        advanceUntilIdle()

        assertTrue(repository.cover("arc-a").value is CoverState.Ready)
        assertTrue(repository.cover("arc-b").value is CoverState.Unrequested)

        repository.releaseCover("arc-a")
        repository.cover("arc-c") // Keep arc-c newer than released arc-a.
        repository.requestCover("arc-d")
        advanceUntilIdle()

        assertTrue(repository.cover("arc-a").value is CoverState.Unrequested)
        assertTrue(repository.cover("arc-d").value is CoverState.Ready)
    }

    @Test
    fun delayedOldServerResultIsDiscardedAfterServerSwitch() = runTest {
        var serverKey = "server-a"
        val probeStarted = CompletableDeferred<Unit>()
        val releaseProbe = CompletableDeferred<Unit>()
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe =
                    withContext(NonCancellable) {
                        probeStarted.complete(Unit)
                        releaseProbe.await()
                        ThumbnailProbe.Ready(ThumbnailResource("server-a://stale"))
                    }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
            serverKeyProvider = { serverKey },
        )

        repository.requestCover("arc-1")
        probeStarted.await()
        serverKey = "server-b"
        releaseProbe.complete(Unit)
        advanceUntilIdle()

        assertEquals(CoverState.Unrequested(), repository.cover("arc-1").value)
        serverKey = "server-a"
        assertEquals(CoverState.Unrequested(), repository.cover("arc-1").value)
    }

    @Test
    fun forceRefreshOfReadyCoverIncrementsRevisionAfterReadyResponse() = runTest {
        var request = 0
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe {
                    request += 1
                    return ThumbnailProbe.Ready(ThumbnailResource("cover://$arcid-v$request"))
                }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()
        repository.refreshCover("arc-1")
        advanceUntilIdle()

        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://arc-1-v2"), revision = 1L),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun concurrentRequestsForSameArchiveProbeOnlyOnce() = runTest {
        val probeCount = AtomicInteger(0)
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe {
                    probeCount.incrementAndGet()
                    return ThumbnailProbe.Ready(ThumbnailResource("cover://$arcid"))
                }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
        )

        repository.requestCover("arc-1")
        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(1, probeCount.get())
        assertTrue(repository.cover("arc-1").value is CoverState.Ready)
    }

    @Test
    fun finishedJobReprobesAndIncrementsOnlyThatArchiveRevision() = runTest {
        val jobs = ArrayDeque<ThumbnailJobState>(
            listOf(ThumbnailJobState.Active, ThumbnailJobState.Finished),
        )
        val arcOneProbes = ArrayDeque<ThumbnailProbe>(
            listOf(
                ThumbnailProbe.Queued("job-1"),
                ThumbnailProbe.Ready(ThumbnailResource("cover://arc-1-v2")),
            ),
        )
        val arcTwoProbeCount = AtomicInteger(0)
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe = if (arcid == "arc-1") {
                    arcOneProbes.removeFirst()
                } else {
                    arcTwoProbeCount.incrementAndGet()
                    ThumbnailProbe.Ready(ThumbnailResource("cover://arc-2"))
                }

                override suspend fun job(jobId: String) = jobs.removeFirst()
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(maxPolls = 3, initialDelayMillis = 0L),
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://arc-1-v2"), revision = 1L),
            repository.cover("arc-1").value,
        )

        repository.requestCover("arc-2")
        advanceUntilIdle()

        assertEquals(1, arcTwoProbeCount.get())
        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://arc-2"), revision = 0L),
            repository.cover("arc-2").value,
        )
    }

    @Test
    fun chainedQueuedJobsPublishOnlyFinalReadyResourceWithOneRevisionIncrement() = runTest {
        val probes = ArrayDeque<ThumbnailProbe>(
            listOf(
                ThumbnailProbe.Queued("job-1"),
                ThumbnailProbe.Queued("job-2"),
                ThumbnailProbe.Ready(ThumbnailResource("cover://final")),
            ),
        )
        val jobs = ArrayDeque<ThumbnailJobState>(
            listOf(ThumbnailJobState.Finished, ThumbnailJobState.Finished),
        )
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = probes.removeFirst()

                override suspend fun job(jobId: String) = jobs.removeFirst()
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(
                maxPolls = 1,
                initialDelayMillis = 0L,
                maxChainedJobs = 1,
            ),
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://final"), revision = 1L),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun forceRefreshPreventsOldQueuedJobFromPublishingItsReadyResource() = runTest {
        val oldJobStarted = CompletableDeferred<Unit>()
        val releaseOldJob = CompletableDeferred<Unit>()
        var probes = 0
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe = when (++probes) {
                    1 -> ThumbnailProbe.Queued("old-job")
                    2 -> ThumbnailProbe.Ready(ThumbnailResource("cover://new"))
                    else -> ThumbnailProbe.Ready(ThumbnailResource("cover://old"))
                }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    withContext(NonCancellable) {
                        oldJobStarted.complete(Unit)
                        releaseOldJob.await()
                        ThumbnailJobState.Finished
                    }
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(maxPolls = 1, initialDelayMillis = 0L),
            wait = {},
        )

        repository.requestCover("arc-1")
        oldJobStarted.await()
        repository.refreshCover("arc-1")
        advanceUntilIdle()
        releaseOldJob.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://new"), revision = 0L),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun transientNullStatusJobFailureCanRecoverOnLaterFinishedPoll() = runTest {
        val jobs = ArrayDeque<ThumbnailJobState>(
            listOf(
                ThumbnailJobState.Failed(status = null, message = "temporary transport failure"),
                ThumbnailJobState.Finished,
            ),
        )
        val probes = ArrayDeque<ThumbnailProbe>(
            listOf(
                ThumbnailProbe.Queued("job-1"),
                ThumbnailProbe.Ready(ThumbnailResource("cover://recovered")),
            ),
        )
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = probes.removeFirst()

                override suspend fun job(jobId: String) = jobs.removeFirst()
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(maxPolls = 2, initialDelayMillis = 0L),
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://recovered"), revision = 1L),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun retryableFailureRecoversButUnauthorizedFailureIsTerminalWithoutExtraPoll() = runTest {
        val retryableJobs = ArrayDeque<ThumbnailJobState>(
            listOf(
                ThumbnailJobState.Failed(status = 503, message = "temporary outage"),
                ThumbnailJobState.Finished,
            ),
        )
        val retryableProbes = ArrayDeque<ThumbnailProbe>(
            listOf(
                ThumbnailProbe.Queued("retryable-job"),
                ThumbnailProbe.Ready(ThumbnailResource("cover://recovered")),
            ),
        )
        val retryableRepository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = retryableProbes.removeFirst()

                override suspend fun job(jobId: String) = retryableJobs.removeFirst()
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(maxPolls = 2, initialDelayMillis = 0L),
            wait = {},
        )

        retryableRepository.requestCover("arc-1")
        advanceUntilIdle()
        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://recovered"), revision = 1L),
            retryableRepository.cover("arc-1").value,
        )

        var unauthorizedPolls = 0
        val unauthorizedRepository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = ThumbnailProbe.Queued("unauthorized-job")

                override suspend fun job(jobId: String): ThumbnailJobState {
                    unauthorizedPolls += 1
                    return ThumbnailJobState.Failed(status = 401, message = "unauthorized")
                }
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(maxPolls = 4, initialDelayMillis = 0L),
            wait = {},
        )

        unauthorizedRepository.requestCover("arc-1")
        advanceUntilIdle()
        assertEquals(1, unauthorizedPolls)
        assertEquals(
            CoverState.Failed(message = "unauthorized", status = 401),
            unauthorizedRepository.cover("arc-1").value,
        )
    }

    @Test
    fun failurePreservesLastGoodAndDoesNotIncrementRevision() = runTest {
        var probeNumber = 0
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe {
                    probeNumber += 1
                    return if (probeNumber == 1) {
                        ThumbnailProbe.Ready(ThumbnailResource("cover://good"))
                    } else {
                        ThumbnailProbe.Failed(status = 503, message = "server unavailable")
                    }
                }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()
        repository.refreshCover("arc-1")
        advanceUntilIdle()

        assertEquals(
            CoverState.Failed(
                message = "server unavailable",
                status = 503,
                revision = 0L,
                lastGood = CoverState.Ready(ThumbnailResource("cover://good"), revision = 0L),
            ),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun emptyJobIdIsFailed() = runTest {
        val repository = repositoryFor(this,
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = ThumbnailProbe.Queued("")

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        val state = repository.cover("arc-1").value
        assertTrue(state is CoverState.Failed)
        assertEquals("Thumbnail generation returned an empty job id", (state as CoverState.Failed).message)
    }

    @Test
    fun malformedProbeIsFailed() = runTest {
        val repository = repositoryFor(this,
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe =
                    throw IllegalArgumentException("malformed response")

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(
            CoverState.Failed(message = "Unable to load thumbnail"),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun activeJobTimesOutAfterBoundedPolls() = runTest {
        val pollCount = AtomicInteger(0)
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = ThumbnailProbe.Queued("job-1")

                override suspend fun job(jobId: String): ThumbnailJobState {
                    pollCount.incrementAndGet()
                    return ThumbnailJobState.Active
                }
            },
            scope = this,
            retryPolicy = ThumbnailRetryPolicy(maxPolls = 2, initialDelayMillis = 0L),
            wait = {},
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(2, pollCount.get())
        assertEquals(
            CoverState.Failed(message = "Thumbnail generation did not finish in time"),
            repository.cover("arc-1").value,
        )
    }

    @Test
    fun cancellationIsNotConvertedIntoFailure() = runTest {
        val repository = repositoryFor(this,
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String): ThumbnailProbe =
                    throw CancellationException("test cancellation")

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
        )

        repository.requestCover("arc-1")
        advanceUntilIdle()

        assertEquals(CoverState.Unrequested(), repository.cover("arc-1").value)
    }

    @Test
    fun differentArchivesHaveIndependentState() = runTest {
        val repository = ThumbnailRepository(
            gateway = object : ThumbnailGateway {
                override suspend fun probeCover(arcid: String) = when (arcid) {
                    "arc-good" -> ThumbnailProbe.Ready(ThumbnailResource("cover://good"))
                    else -> ThumbnailProbe.Failed(status = 404, message = "not found")
                }

                override suspend fun job(jobId: String): ThumbnailJobState =
                    error("job should not be queried")
            },
            scope = this,
            wait = {},
        )

        repository.requestCover("arc-good")
        repository.requestCover("arc-missing")
        advanceUntilIdle()

        assertEquals(
            CoverState.Ready(ThumbnailResource("cover://good"), revision = 0L),
            repository.cover("arc-good").value,
        )
        assertEquals(
            CoverState.Failed(message = "not found", status = 404),
            repository.cover("arc-missing").value,
        )
    }

    private fun repositoryFor(scope: CoroutineScope, gateway: ThumbnailGateway): ThumbnailRepository =
        ThumbnailRepository(gateway = gateway, scope = scope, wait = {})

    private fun readyGateway(): ThumbnailGateway = object : ThumbnailGateway {
        override suspend fun probeCover(arcid: String) =
            ThumbnailProbe.Ready(ThumbnailResource("cover://$arcid"))

        override suspend fun job(jobId: String): ThumbnailJobState =
            error("job should not be queried")
    }
}
