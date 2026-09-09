package com.lanraragi.reader.data.favorites

/** Stable external identity: E-Hentai always exposes exactly ten favorite slots. */
data class EhFavoriteSlot(
    val slotIndex: Int,
    val remoteName: String = "",
    val remoteCount: Int = 0,
    val color: Long? = null,
    val updatedAt: Long = 0L,
) {
    init {
        require(slotIndex in EhFavoriteSlots.RANGE) { "E-Hentai favorite slot must be 0..9" }
        require(remoteCount >= 0) { "remoteCount must not be negative" }
    }
}

object EhFavoriteSlots {
    val RANGE: IntRange = 0..9

    /** Fills omitted server rows while retaining the fixed slot identity. */
    fun normalize(slots: Iterable<EhFavoriteSlot>): List<EhFavoriteSlot> {
        val source = slots.toList()
        require(source.map { it.slotIndex }.distinct().size == source.size) {
            "E-Hentai favorite snapshot contains duplicate slot indices"
        }
        val byIndex = source.associateBy { it.slotIndex }
        return RANGE.map { byIndex[it] ?: EhFavoriteSlot(it) }
    }
}

/** A favorite item is only eligible for category sync when linked by source identity. */
data class EhFavoriteEntry(
    val slotIndex: Int,
    val gid: String,
    val token: String? = null,
    val sourceUrl: String? = null,
    val linkedLanraragiArchiveId: String? = null,
) {
    init {
        require(slotIndex in EhFavoriteSlots.RANGE)
        require(gid.isNotBlank())
    }

    /**
     * The only identifier that can associate an E-H favorite with an archive.
     * Titles and tags are intentionally excluded because they are not stable identities.
     */
    val sourceIdentity: EhFavoriteSourceIdentity get() = EhFavoriteSourceIdentity(gid, token).normalized()
}

/** Normalized E-H gallery identity used for exact cross-service matching. */
data class EhFavoriteSourceIdentity(
    val gid: String,
    val token: String? = null,
) {
    init { require(gid.trim().isNotEmpty()) { "E-H gallery gid must not be blank" } }

    val normalizedGid: String = gid.trim()
    val normalizedToken: String? = token?.trim()?.takeIf(String::isNotEmpty)

    fun normalized(): EhFavoriteSourceIdentity =
        if (gid == normalizedGid && token == normalizedToken) this else EhFavoriteSourceIdentity(normalizedGid, normalizedToken)
}

data class EhFavoriteSnapshot(
    val slots: List<EhFavoriteSlot>,
    val entries: List<EhFavoriteEntry> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val normalizedSlots: List<EhFavoriteSlot> get() = EhFavoriteSlots.normalize(slots)
}

/** Opaque credential value. Implementations must keep cookie material out of logs and ordinary app stores. */
data class EhCredentials internal constructor(val cookieJar: Any)

interface EhAccountConnector {
    suspend fun currentCredentials(): EhCredentials?
    suspend fun invalidateCredentials()
}

sealed interface EhFavoriteSyncFailure {
    val message: String
    val retryable: Boolean

    data class CookieInvalid(override val message: String = "E-Hentai session cookie is invalid") : EhFavoriteSyncFailure {
        override val retryable: Boolean = false
    }
    data class RateLimited(val retryAfterMillis: Long? = null, override val message: String = "E-Hentai rate limit") : EhFavoriteSyncFailure {
        override val retryable: Boolean = true
    }
    data class Network(override val message: String, val cause: Throwable? = null) : EhFavoriteSyncFailure {
        override val retryable: Boolean = true
    }
    data class Http(val statusCode: Int, override val message: String) : EhFavoriteSyncFailure {
        override val retryable: Boolean = statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
    }
}

class EhFavoriteSyncException(val failure: EhFavoriteSyncFailure) : Exception(failure.message)

/** Read-only gateway. There are deliberately no favorite mutation methods here. */
interface EhFavoriteGateway {
    suspend fun fetchFavorites(credentials: EhCredentials): EhFavoriteSnapshot
}

interface EhFavoriteStore {
    suspend fun read(): EhFavoriteSnapshot?
    suspend fun write(snapshot: EhFavoriteSnapshot)
    suspend fun clear()
}

sealed interface EhFavoriteSyncState {
    data object Idle : EhFavoriteSyncState
    data object Loading : EhFavoriteSyncState
    data class Ready(val snapshot: EhFavoriteSnapshot) : EhFavoriteSyncState
    data class Failed(val failure: EhFavoriteSyncFailure, val previous: EhFavoriteSnapshot? = null) : EhFavoriteSyncState
}
