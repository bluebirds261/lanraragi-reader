package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.model.ArchiveIdentity

/**
 * Durable state used by [MetadataRepository].  A store may map this value to
 * Room (or another durable store); keeping the patch typed here lets callers
 * decide how to encode it without making the repository aware of JSON.
 */
data class MetadataStoredState(
    val sourceKey: String,
    val snapshot: MetadataSnapshot,
    val baseline: MetadataSnapshot? = null,
    val pendingPatch: MetadataPatch = MetadataPatch(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Persistence boundary for metadata snapshots and unapplied patches. */
interface MetadataStateStore {
    suspend fun read(sourceKey: String): MetadataStoredState?

    suspend fun write(state: MetadataStoredState)
}

/** Codec/Room adapters use this to surface corrupt records without discarding them. */
class MetadataStateStoreException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class MetadataStateStatus {
    UNLOADED,
    LOADING,
    READY,
    PREVIEW_READY,
    SAVING,
    SAVED,
    LOCKED,
    OFFLINE,
    ERROR,
}

/**
 * UI-facing, immutable state for one archive identity.  [baseline] is the
 * snapshot against which the pending patch was originally reviewed; [latest]
 * is always the most recently fetched/local snapshot.  They intentionally
 * remain separate so a remote refresh can reveal a rebase conflict.
 */
data class MetadataState(
    val target: ArchiveIdentity,
    val baseline: MetadataSnapshot? = null,
    val latest: MetadataSnapshot? = null,
    val pendingPatch: MetadataPatch = MetadataPatch(),
    val plan: MetadataApplyPlan? = null,
    val status: MetadataStateStatus = MetadataStateStatus.UNLOADED,
    val error: MetadataStateError? = null,
    val updatedAt: Long = 0L,
) {
    val sourceKey: String get() = target.sourceKey
    val hasPendingPatch: Boolean get() = pendingPatch != MetadataPatch()
    val isRemote: Boolean get() = target is ArchiveIdentity.Remote
}

sealed interface MetadataStateError {
    val message: String

    data class Remote(
        val statusCode: Int? = null,
        override val message: String,
    ) : MetadataStateError

    data class Persistence(override val message: String) : MetadataStateError

    data class InvalidTarget(override val message: String) : MetadataStateError
}

/** Result of an apply attempt. Pending patches are deliberately returned. */
sealed interface MetadataApplyResult {
    val state: MetadataState

    data class Applied(
        override val state: MetadataState,
        val plan: MetadataApplyPlan,
    ) : MetadataApplyResult

    data class Blocked(
        override val state: MetadataState,
        val plan: MetadataApplyPlan,
    ) : MetadataApplyResult

    data class Pending(
        override val state: MetadataState,
        val plan: MetadataApplyPlan?,
        val reason: MetadataPendingReason,
        val error: MetadataStateError,
    ) : MetadataApplyResult

    data class NoPending(override val state: MetadataState) : MetadataApplyResult
}

enum class MetadataPendingReason {
    LOCKED,
    NETWORK,
    REMOTE_ERROR,
    INVALID_RESPONSE,
    PERSISTENCE_ERROR,
}

/** Network/API boundary. Implementations translate Retrofit responses here. */
interface MetadataRemoteGateway {
    suspend fun fetch(arcid: String): MetadataSnapshot

    /** PUT is a complete replacement of title/tags/summary on LANraragi. */
    suspend fun put(payload: MetadataPutPayload)
}

enum class MetadataRemoteFailureReason {
    NETWORK,
    LOCKED,
    HTTP,
    INVALID_RESPONSE,
}

/** Typed gateway failure; 423 must be reported as [MetadataRemoteFailureReason.LOCKED]. */
class MetadataRemoteException(
    val reason: MetadataRemoteFailureReason,
    val statusCode: Int? = null,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
