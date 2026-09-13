package com.lanraragi.reader.data.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A stable server/archive identity.  It deliberately includes the server scope. */
@Serializable
data class DownloadSourceIdentity(
    val archiveId: String,
    val serverScope: String? = null,
)

@Serializable
data class DownloadDestination(
    /** Absolute private path or a persisted document URI, interpreted by the runner. */
    val value: String,
)

@Serializable
data class DownloadRange(
    val startInclusive: Long = 0L,
    val endInclusive: Long? = null,
)

/**
 * Durable work description.  It contains no callback or Context, so a task can be reconstructed
 * after a process restart and handed to a runner selected by [DownloadRunnerRegistry].
 */
@Serializable
sealed interface DownloadTaskSpec {
    val source: DownloadSourceIdentity
    val destination: DownloadDestination
    val label: String
    val expectedRevision: String?
    val priority: Int

    @Serializable
    @SerialName("archive")
    data class Archive(
        override val source: DownloadSourceIdentity,
        override val destination: DownloadDestination,
        override val expectedRevision: String? = null,
        override val priority: Int = DEFAULT_PRIORITY,
        val range: DownloadRange? = null,
        override val label: String = "",
    ) : DownloadTaskSpec

    /** A private offline copy. It has the same physical artifact identity as an archive save. */
    @Serializable
    @SerialName("cache")
    data class Cache(
        override val source: DownloadSourceIdentity,
        override val destination: DownloadDestination,
        override val expectedRevision: String? = null,
        override val priority: Int = DEFAULT_PRIORITY,
        val range: DownloadRange? = null,
        override val label: String = "",
    ) : DownloadTaskSpec

    companion object {
        const val DEFAULT_PRIORITY = 0
    }
}

@Serializable
enum class DurableDownloadState {
    WAITING,
    RUNNING,
    PAUSED,
    RETRYABLE,
    FAILED,
    DONE,
}

@Serializable
data class DurableDownloadTask(
    val id: String,
    val spec: DownloadTaskSpec,
    val state: DurableDownloadState = DurableDownloadState.WAITING,
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val retryCount: Int = 0,
    val retryAtEpochMs: Long? = null,
    val error: String? = null,
    val entityTag: String? = null,
    val lastModified: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
) {
    init {
        require(completedBytes >= 0L)
        require(totalBytes == null || totalBytes >= completedBytes)
        require(retryCount >= 0)
    }
}
