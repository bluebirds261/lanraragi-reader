package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.model.ArchiveIdentity
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns metadata patch staging and the final read/remerge/write boundary.
 *
 * A remote apply never trusts a preview snapshot: it fetches metadata again,
 * rebases the durable patch, and only then sends a complete LANraragi PUT.
 * Local SAF identities take a separate persistence-only path and cannot reach
 * [MetadataRemoteGateway].
 */
class MetadataRepository(
    private val remoteGateway: MetadataRemoteGateway,
    private val stateStore: MetadataStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val states = ConcurrentHashMap<String, MutableStateFlow<MetadataState>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun observe(target: ArchiveIdentity): StateFlow<MetadataState> =
        stateFlow(target).asStateFlow()

    /** Persist a candidate without changing local or server metadata. */
    suspend fun stagePatch(
        target: ArchiveIdentity,
        baseline: MetadataSnapshot,
        patch: MetadataPatch,
    ): MetadataState = locked(target) { state ->
        val stored = readStored(target)
        stored.error?.let { return@locked persistenceReadFailure(target, state, it) }
        val restored = restoredState(target, stored.value, state)
        if (target is ArchiveIdentity.Tankoubon) {
            return@locked restored.copy(
                status = MetadataStateStatus.ERROR,
                error = MetadataStateError.InvalidTarget("Tankoubon metadata is not an archive metadata target"),
            ).publish(target)
        }
        if (patch == MetadataPatch()) {
            return@locked restored.copy(
                status = MetadataStateStatus.READY,
                error = null,
            ).publish(target)
        }

        val snapshot = stored.value?.snapshot
            ?.let { withClientAnnotations(it, baseline) }
            ?: baseline
        val staged = MetadataState(
            target = target,
            baseline = baseline,
            latest = snapshot,
            pendingPatch = patch,
            status = MetadataStateStatus.READY,
            updatedAt = clock(),
        )
        persist(staged)?.let { error ->
            return@locked staged.copy(
                status = MetadataStateStatus.ERROR,
                error = error,
            ).publish(target)
        }
        staged.publish(target)
    }

    /**
     * Load the local record and, for a remote identity, refresh its latest
     * server snapshot. A local SAF identity never invokes the remote gateway.
     */
    suspend fun refresh(target: ArchiveIdentity): MetadataState = locked(target) { current ->
        current.copy(status = MetadataStateStatus.LOADING, error = null).publish(target)
        val stored = readStored(target)
        stored.error?.let { return@locked persistenceReadFailure(target, current, it) }
        val restored = restoredState(target, stored.value, current)

        when (target) {
            is ArchiveIdentity.Remote -> when (val fetched = fetch(target)) {
                is FetchResult.Failure -> remoteFailureState(restored, fetched).publish(target)
                is FetchResult.Success -> {
                    val latest = withClientAnnotations(fetched.snapshot, restored.latest)
                    val refreshed = restored.copy(
                        latest = latest,
                        status = MetadataStateStatus.READY,
                        error = null,
                        updatedAt = clock(),
                    )
                    persist(refreshed)?.let { error ->
                        return@locked refreshed.copy(
                            status = MetadataStateStatus.ERROR,
                            error = error,
                        ).publish(target)
                    }
                    refreshed.publish(target)
                }
            }

            is ArchiveIdentity.LocalSaf -> restored.copy(
                status = MetadataStateStatus.READY,
                error = null,
            ).publish(target)

            is ArchiveIdentity.Tankoubon -> restored.copy(
                status = MetadataStateStatus.ERROR,
                error = MetadataStateError.InvalidTarget("Tankoubon metadata is not an archive metadata target"),
            ).publish(target)
        }
    }

    /**
     * Build a review plan from a fresh server snapshot (or the current local
     * snapshot). This method never writes metadata.
     */
    suspend fun preview(
        target: ArchiveIdentity,
        policy: MetadataApplyPolicy = MetadataApplyPolicy(),
    ): MetadataState = locked(target) { current ->
        val stored = readStored(target)
        stored.error?.let { return@locked persistenceReadFailure(target, current, it) }
        val restored = restoredState(target, stored.value, current)
        if (!restored.hasPendingPatch) {
            return@locked restored.copy(
                plan = null,
                status = MetadataStateStatus.READY,
                error = null,
            ).publish(target)
        }

        val latest = when (target) {
            is ArchiveIdentity.Remote -> when (val fetched = fetch(target)) {
                is FetchResult.Failure -> return@locked remoteFailureState(restored, fetched).publish(target)
                is FetchResult.Success -> withClientAnnotations(fetched.snapshot, restored.latest)
            }

            is ArchiveIdentity.LocalSaf -> restored.latest ?: restored.baseline ?: MetadataSnapshot()
            is ArchiveIdentity.Tankoubon -> return@locked restored.copy(
                status = MetadataStateStatus.ERROR,
                error = MetadataStateError.InvalidTarget("Tankoubon metadata is not an archive metadata target"),
            ).publish(target)
        }

        val plan = plan(restored, latest, policy)
        val preview = restored.copy(
            latest = latest,
            plan = plan,
            status = MetadataStateStatus.PREVIEW_READY,
            error = null,
            updatedAt = clock(),
        )
        persist(preview)?.let { error ->
            return@locked preview.copy(
                status = MetadataStateStatus.ERROR,
                error = error,
            ).publish(target)
        }
        preview.publish(target)
    }

    /**
     * Apply the pending patch. Remote targets always perform another GET here,
     * even if [preview] just ran, so the write plan cannot accidentally reuse
     * stale UI state.
     */
    suspend fun applyPending(
        target: ArchiveIdentity,
        policy: MetadataApplyPolicy = MetadataApplyPolicy(),
    ): MetadataApplyResult = locked(target) { current ->
        val stored = readStored(target)
        stored.error?.let { error ->
            return@locked MetadataApplyResult.Pending(
                state = persistenceReadFailure(target, current, error),
                plan = null,
                reason = MetadataPendingReason.PERSISTENCE_ERROR,
                error = error,
            )
        }
        val restored = restoredState(target, stored.value, current)
        if (!restored.hasPendingPatch) {
            val ready = restored.copy(status = MetadataStateStatus.READY, error = null).publish(target)
            return@locked MetadataApplyResult.NoPending(ready)
        }

        val latest = when (target) {
            is ArchiveIdentity.Remote -> when (val fetched = fetch(target)) {
                is FetchResult.Failure -> {
                    val failed = remoteFailureState(restored, fetched)
                    persist(failed)
                    val published = failed.publish(target)
                    return@locked MetadataApplyResult.Pending(
                        state = published,
                        plan = null,
                        reason = fetched.pendingReason,
                        error = fetched.error,
                    )
                }

                is FetchResult.Success -> withClientAnnotations(fetched.snapshot, restored.latest)
            }

            is ArchiveIdentity.LocalSaf,
            is ArchiveIdentity.Tankoubon -> restored.latest ?: restored.baseline ?: MetadataSnapshot()
        }

        val plan = plan(restored, latest, policy)
        val noEffectiveChange = !plan.diff.hasEffectiveChanges &&
            plan.unappliedPatch == MetadataPatch()
        if (noEffectiveChange) {
            val complete = restored.copy(
                baseline = null,
                latest = latest,
                pendingPatch = MetadataPatch(),
                plan = plan,
                status = MetadataStateStatus.SAVED,
                error = null,
                updatedAt = clock(),
            )
            persist(complete)?.let { error ->
                val failed = restored.copy(
                    latest = latest,
                    plan = plan,
                    status = MetadataStateStatus.ERROR,
                    error = error,
                ).publish(target)
                return@locked MetadataApplyResult.Pending(
                    state = failed,
                    plan = plan,
                    reason = MetadataPendingReason.PERSISTENCE_ERROR,
                    error = error,
                )
            }
            val published = complete.publish(target)
            return@locked MetadataApplyResult.Applied(published, plan)
        }

        val blockingReasons = when (target) {
            is ArchiveIdentity.LocalSaf -> plan.blockReasons - MetadataApplyBlockReason.TARGET_IS_NOT_REMOTE
            else -> plan.blockReasons
        }
        if (blockingReasons.isNotEmpty()) {
            val blocked = restored.copy(
                latest = latest,
                plan = plan,
                status = MetadataStateStatus.PREVIEW_READY,
                error = null,
                updatedAt = clock(),
            )
            persist(blocked)?.let { error ->
                return@locked MetadataApplyResult.Pending(
                    state = blocked.copy(status = MetadataStateStatus.ERROR, error = error).publish(target),
                    plan = plan,
                    reason = MetadataPendingReason.PERSISTENCE_ERROR,
                    error = error,
                )
            }
            return@locked MetadataApplyResult.Blocked(blocked.publish(target), plan)
        }

        val saving = restored.copy(
            latest = latest,
            plan = plan,
            status = MetadataStateStatus.SAVING,
            error = null,
        ).publish(target)

        var acceptedSnapshot = plan.mergedSnapshot
        if (target is ArchiveIdentity.Remote) {
            val payload = requireNotNull(plan.putPayload) {
                "remote apply without a planner PUT payload"
            }
            when (val written = put(payload)) {
                is PutResult.Failure -> {
                    val failed = remoteFailureState(saving, written)
                    persist(failed)
                    val published = failed.publish(target)
                    return@locked MetadataApplyResult.Pending(
                        state = published,
                        plan = plan,
                        reason = written.pendingReason,
                        error = written.error,
                    )
                }

                PutResult.Success -> Unit
            }

            val verified = when (val fetched = fetch(target)) {
                is FetchResult.Failure -> {
                    val failed = remoteFailureState(saving, fetched)
                    persist(failed)
                    val published = failed.publish(target)
                    return@locked MetadataApplyResult.Pending(
                        state = published,
                        plan = plan,
                        reason = fetched.pendingReason,
                        error = fetched.error,
                    )
                }

                is FetchResult.Success -> withClientAnnotations(fetched.snapshot, plan.mergedSnapshot)
            }
            if (!matchesPutPayload(verified, payload)) {
                val error = MetadataStateError.Remote(
                    message = "Server metadata after PUT did not match the planned replacement",
                )
                val failed = saving.copy(
                    latest = verified,
                    status = MetadataStateStatus.ERROR,
                    error = error,
                    updatedAt = clock(),
                )
                persist(failed)
                val published = failed.publish(target)
                return@locked MetadataApplyResult.Pending(
                    state = published,
                    plan = plan,
                    reason = MetadataPendingReason.INVALID_RESPONSE,
                    error = error,
                )
            }
            acceptedSnapshot = verified
        }

        val remainingPatch = plan.unappliedPatch
        val saved = saving.copy(
            baseline = acceptedSnapshot.takeIf { remainingPatch != MetadataPatch() },
            latest = acceptedSnapshot,
            pendingPatch = remainingPatch,
            status = MetadataStateStatus.SAVED,
            error = null,
            updatedAt = clock(),
        )
        persist(saved)?.let { error ->
            // Keep the original pending patch in memory if durable cleanup
            // failed. A retry will GET first and collapse a remotely-applied
            // patch to a no-op instead of issuing a blind duplicate PUT.
            val failed = saving.copy(
                latest = acceptedSnapshot,
                status = MetadataStateStatus.ERROR,
                error = error,
            ).publish(target)
            return@locked MetadataApplyResult.Pending(
                state = failed,
                plan = plan,
                reason = MetadataPendingReason.PERSISTENCE_ERROR,
                error = error,
            )
        }
        MetadataApplyResult.Applied(saved.publish(target), plan)
    }

    /** Save user-edited SAF metadata without exposing any remote code path. */
    suspend fun saveLocalSnapshot(
        target: ArchiveIdentity.LocalSaf,
        snapshot: MetadataSnapshot,
    ): MetadataState = locked(target) { current ->
        val stored = readStored(target)
        stored.error?.let { return@locked persistenceReadFailure(target, current, it) }
        val restored = restoredState(target, stored.value, current)
        val saved = restored.copy(
            latest = snapshot,
            status = MetadataStateStatus.SAVED,
            error = null,
            updatedAt = clock(),
        )
        persist(saved)?.let { error ->
            return@locked saved.copy(status = MetadataStateStatus.ERROR, error = error).publish(target)
        }
        saved.publish(target)
    }

    suspend fun discardPending(target: ArchiveIdentity): MetadataState = locked(target) { current ->
        val stored = readStored(target)
        stored.error?.let { return@locked persistenceReadFailure(target, current, it) }
        val restored = restoredState(target, stored.value, current)
        val discarded = restored.copy(
            baseline = null,
            pendingPatch = MetadataPatch(),
            plan = null,
            status = MetadataStateStatus.READY,
            error = null,
            updatedAt = clock(),
        )
        persist(discarded)?.let { error ->
            return@locked restored.copy(status = MetadataStateStatus.ERROR, error = error).publish(target)
        }
        discarded.publish(target)
    }

    private fun plan(
        state: MetadataState,
        latest: MetadataSnapshot,
        policy: MetadataApplyPolicy,
    ): MetadataApplyPlan = MetadataApplyPlanner.plan(
        baseline = state.baseline ?: state.latest ?: latest,
        latest = latest,
        patch = state.pendingPatch,
        policy = policy,
        target = state.target,
    )

    private suspend fun fetch(target: ArchiveIdentity.Remote): FetchResult = try {
        FetchResult.Success(remoteGateway.fetch(target.arcid))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: MetadataRemoteException) {
        FetchResult.Failure(failure.toStateError(), failure.toPendingReason())
    } catch (failure: IOException) {
        FetchResult.Failure(
            MetadataStateError.Remote(message = failure.message ?: "Network request failed"),
            MetadataPendingReason.NETWORK,
        )
    } catch (failure: Exception) {
        FetchResult.Failure(
            MetadataStateError.Remote(message = failure.message ?: "Invalid metadata response"),
            MetadataPendingReason.INVALID_RESPONSE,
        )
    }

    private suspend fun put(payload: MetadataPutPayload): PutResult = try {
        remoteGateway.put(payload)
        PutResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: MetadataRemoteException) {
        PutResult.Failure(failure.toStateError(), failure.toPendingReason())
    } catch (failure: IOException) {
        PutResult.Failure(
            MetadataStateError.Remote(message = failure.message ?: "Network request failed"),
            MetadataPendingReason.NETWORK,
        )
    } catch (failure: Exception) {
        PutResult.Failure(
            MetadataStateError.Remote(message = failure.message ?: "Metadata update failed"),
            MetadataPendingReason.REMOTE_ERROR,
        )
    }

    private suspend fun readStored(target: ArchiveIdentity): StoredRead = try {
        StoredRead(value = stateStore.read(target.sourceKey))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        StoredRead(error = MetadataStateError.Persistence(failure.message ?: "Unable to read metadata state"))
    }

    private suspend fun persist(state: MetadataState): MetadataStateError.Persistence? {
        val snapshot = state.latest ?: state.baseline ?: MetadataSnapshot()
        val record = MetadataStoredState(
            sourceKey = state.sourceKey,
            snapshot = snapshot,
            baseline = state.baseline.takeIf { state.hasPendingPatch },
            pendingPatch = state.pendingPatch,
            updatedAt = state.updatedAt.takeIf { it > 0 } ?: clock(),
        )
        return try {
            stateStore.write(record)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            MetadataStateError.Persistence(failure.message ?: "Unable to persist metadata state")
        }
    }

    private fun restoredState(
        target: ArchiveIdentity,
        stored: MetadataStoredState?,
        current: MetadataState,
    ): MetadataState = stored?.let {
        MetadataState(
            target = target,
            baseline = it.baseline,
            latest = it.snapshot,
            pendingPatch = it.pendingPatch,
            status = MetadataStateStatus.READY,
            updatedAt = it.updatedAt,
        )
    } ?: current.copy(target = target)

    private fun persistenceReadFailure(
        target: ArchiveIdentity,
        current: MetadataState,
        error: MetadataStateError.Persistence = MetadataStateError.Persistence(
            "Unable to read persisted metadata state; the pending record was not discarded",
        ),
    ): MetadataState = current.copy(
        target = target,
        status = MetadataStateStatus.ERROR,
        error = error,
    ).publish(target)

    private fun remoteFailureState(
        state: MetadataState,
        failure: RemoteFailure,
    ): MetadataState = state.copy(
        status = when (failure.pendingReason) {
            MetadataPendingReason.LOCKED -> MetadataStateStatus.LOCKED
            MetadataPendingReason.NETWORK -> MetadataStateStatus.OFFLINE
            else -> MetadataStateStatus.ERROR
        },
        error = failure.error,
        updatedAt = clock(),
    )

    private fun MetadataRemoteException.toStateError(): MetadataStateError.Remote =
        MetadataStateError.Remote(statusCode = statusCode, message = message)

    private fun MetadataRemoteException.toPendingReason(): MetadataPendingReason = when {
        reason == MetadataRemoteFailureReason.LOCKED || statusCode == 423 -> MetadataPendingReason.LOCKED
        reason == MetadataRemoteFailureReason.NETWORK -> MetadataPendingReason.NETWORK
        else -> MetadataPendingReason.REMOTE_ERROR
    }

    /**
     * A LANraragi GET is authoritative for server fields but knows nothing
     * about client ownership. Carry those annotations across refreshes while
     * dropping tag provenance for tags that no longer exist remotely.
     */
    private fun withClientAnnotations(
        fetched: MetadataSnapshot,
        client: MetadataSnapshot?,
    ): MetadataSnapshot {
        if (client == null) return fetched

        val clientTags = client.canonicalTags
        val annotatedTags = fetched.tags.mapTo(linkedSetOf()) { serverTag ->
            val annotation = clientTags[serverTag.full]
            if (annotation == null) {
                serverTag
            } else {
                serverTag.copy(
                    displayNameZh = annotation.displayNameZh ?: serverTag.displayNameZh,
                    source = annotation.source,
                    confidence = annotation.confidence ?: serverTag.confidence,
                )
            }
        }
        val presentTags = annotatedTags.mapTo(hashSetOf()) { it.full }
        val provenance = (fetched.provenance + client.provenance).filterKeys { key ->
            !key.startsWith(TAG_PROVENANCE_PREFIX) ||
                key.removePrefix(TAG_PROVENANCE_PREFIX) in presentTags
        }
        return fetched.copy(
            tags = annotatedTags,
            // Ownership is additive at refresh/staging boundaries. Clearing a
            // user override must be an explicit edit, never a side effect of
            // receiving an annotation-free server snapshot.
            userOverrides = fetched.userOverrides + client.userOverrides,
            provenance = provenance,
        )
    }

    /**
     * Verify only values represented by LANraragi's PUT. Revision,
     * provenance and user-override ownership are client-side concerns.
     */
    private fun matchesPutPayload(
        verified: MetadataSnapshot,
        payload: MetadataPutPayload,
    ): Boolean {
        val expected = MetadataSnapshot(
            title = payload.title,
            summary = payload.summary,
            sourceUrl = verified.sourceUrl,
            tags = payload.tags,
        )
        val actual = MetadataSnapshot(
            title = verified.title,
            summary = verified.summary,
            sourceUrl = verified.sourceUrl,
            tags = verified.tags,
        )
        return MetadataDiff.fingerprint(expected) == MetadataDiff.fingerprint(actual)
    }

    private fun MetadataState.publish(target: ArchiveIdentity): MetadataState = also {
        stateFlow(target).value = it
    }

    private suspend fun <T> locked(
        target: ArchiveIdentity,
        block: suspend (MetadataState) -> T,
    ): T = lockFor(target).withLock {
        block(stateFlow(target).value)
    }

    private fun stateFlow(target: ArchiveIdentity): MutableStateFlow<MetadataState> =
        states.computeIfAbsent(target.sourceKey) { MutableStateFlow(MetadataState(target)) }

    private fun lockFor(target: ArchiveIdentity): Mutex =
        locks.computeIfAbsent(target.sourceKey) { Mutex() }

    private companion object {
        const val TAG_PROVENANCE_PREFIX = "tag:"
    }

    private data class StoredRead(
        val value: MetadataStoredState? = null,
        val error: MetadataStateError.Persistence? = null,
    )

    private sealed interface RemoteFailure {
        val error: MetadataStateError.Remote
        val pendingReason: MetadataPendingReason
    }

    private sealed interface FetchResult {
        data class Success(val snapshot: MetadataSnapshot) : FetchResult

        data class Failure(
            override val error: MetadataStateError.Remote,
            override val pendingReason: MetadataPendingReason,
        ) : FetchResult, RemoteFailure
    }

    private sealed interface PutResult {
        data object Success : PutResult

        data class Failure(
            override val error: MetadataStateError.Remote,
            override val pendingReason: MetadataPendingReason,
        ) : PutResult, RemoteFailure
    }
}
