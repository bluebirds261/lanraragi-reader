package com.lanraragi.reader.data.assets

/**
 * Transport-neutral value returned by the thumbnail gateway. The gateway owns
 * turning this opaque value into a URL or image request; the repository only
 * tracks its identity and revision.
 */
data class ThumbnailResource(val value: String)

sealed interface ThumbnailProbe {
    data class Ready(val resource: ThumbnailResource) : ThumbnailProbe

    data class Queued(val jobId: String) : ThumbnailProbe

    /** A non-success response or a response that could not be decoded safely. */
    data class Failed(val status: Int? = null, val message: String) : ThumbnailProbe
}

sealed interface ThumbnailJobState {
    data object Active : ThumbnailJobState

    data object Finished : ThumbnailJobState

    data class Failed(val status: Int? = null, val message: String) : ThumbnailJobState
}

/** The only server-facing dependency required by [ThumbnailRepository]. */
interface ThumbnailGateway {
    suspend fun probeCover(arcid: String): ThumbnailProbe

    suspend fun job(jobId: String): ThumbnailJobState
}

sealed interface CoverState {
    val revision: Long
    val lastGood: Ready?

    data class Unrequested(
        override val revision: Long = 0L,
        override val lastGood: Ready? = null,
    ) : CoverState

    data class Ready(
        val resource: ThumbnailResource,
        override val revision: Long,
    ) : CoverState {
        override val lastGood: Ready = this
    }

    data class Generating(
        val jobId: String,
        override val revision: Long,
        override val lastGood: Ready? = null,
    ) : CoverState

    data class Failed(
        val message: String,
        val status: Int? = null,
        override val revision: Long = 0L,
        override val lastGood: Ready? = null,
    ) : CoverState
}

/** Bounded polling policy used after the server queues thumbnail generation. */
data class ThumbnailRetryPolicy(
    val maxPolls: Int = 8,
    val initialDelayMillis: Long = 500L,
    val maxDelayMillis: Long = 8_000L,
    val backoffMultiplier: Int = 2,
    val maxChainedJobs: Int = 2,
) {
    init {
        require(maxPolls > 0) { "maxPolls must be positive" }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must not be negative" }
        require(maxDelayMillis >= initialDelayMillis) {
            "maxDelayMillis must be at least initialDelayMillis"
        }
        require(backoffMultiplier >= 1) { "backoffMultiplier must be positive" }
        require(maxChainedJobs >= 0) { "maxChainedJobs must not be negative" }
    }

    fun delayMillis(pollNumber: Int): Long {
        if (initialDelayMillis == 0L) return 0L
        var delay = initialDelayMillis
        repeat((pollNumber - 1).coerceAtLeast(0)) {
            delay = (delay * backoffMultiplier).coerceAtMost(maxDelayMillis)
        }
        return delay
    }
}
