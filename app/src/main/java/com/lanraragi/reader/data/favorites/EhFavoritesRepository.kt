package com.lanraragi.reader.data.favorites

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates the local E-H snapshot with a read-only remote connector.
 * Credential invalidation clears the secure connector only; cached slot names
 * remain available to explain existing mappings while the account is signed out.
 */
class EhFavoritesRepository(
    private val store: EhFavoriteStore,
    private val accountConnector: EhAccountConnector,
    private val gateway: EhFavoriteGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow<EhFavoriteSyncState>(EhFavoriteSyncState.Idle)
    val state: StateFlow<EhFavoriteSyncState> = mutableState.asStateFlow()

    suspend fun loadCached(): EhFavoriteSyncState {
        val cached = store.read()?.normalized()
        return EhFavoriteSyncState.Ready(cached ?: EhFavoriteSnapshot(EhFavoriteSlots.normalize(emptyList())))
            .also { mutableState.value = it }
    }

    suspend fun importSnapshot(snapshot: EhFavoriteSnapshot): EhFavoriteSyncState.Ready {
        val normalized = snapshot.normalized()
        store.write(normalized)
        return EhFavoriteSyncState.Ready(normalized).also { mutableState.value = it }
    }

    suspend fun sync(): EhFavoriteSyncState {
        val previous = store.read()?.normalized()
        mutableState.value = EhFavoriteSyncState.Loading
        val credentials = accountConnector.currentCredentials()
        if (credentials == null) {
            return failure(EhFavoriteSyncFailure.CookieInvalid("E-Hentai is not signed in"), previous)
        }
        return try {
            val fetched = gateway.fetchFavorites(credentials).normalized(clock())
            store.write(fetched)
            EhFavoriteSyncState.Ready(fetched).also { mutableState.value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: EhFavoriteSyncException) {
            if (error.failure is EhFavoriteSyncFailure.CookieInvalid) accountConnector.invalidateCredentials()
            failure(error.failure, previous)
        } catch (error: java.io.IOException) {
            failure(EhFavoriteSyncFailure.Network(error.message ?: "Network request failed", error), previous)
        }
    }

    suspend fun signOut() {
        accountConnector.invalidateCredentials()
        mutableState.value = EhFavoriteSyncState.Idle
    }

    private fun failure(
        failure: EhFavoriteSyncFailure,
        previous: EhFavoriteSnapshot?,
    ): EhFavoriteSyncState.Failed = EhFavoriteSyncState.Failed(failure, previous).also {
        mutableState.value = it
    }

    private fun EhFavoriteSnapshot.normalized(now: Long = fetchedAt): EhFavoriteSnapshot = copy(
        slots = normalizedSlots,
        fetchedAt = now,
    )
}
