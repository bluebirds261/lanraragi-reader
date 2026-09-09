package com.lanraragi.reader.data.security

/**
 * Secret material boundary. Implementations must use Android Keystore (or an equivalent
 * hardware-backed vault); callers must never persist values through DataStore, Room, or logs.
 */
interface SecretStore {
    suspend fun read(name: String): String?
    suspend fun write(name: String, value: String)
    suspend fun remove(name: String)
}

/** Safe default for builds that have not wired an Android Keystore implementation yet. */
class UnavailableSecretStore : SecretStore {
    override suspend fun read(name: String): String? = null
    override suspend fun write(name: String, value: String) {
        throw SecretStoreUnavailableException("No secure secret store configured")
    }
    override suspend fun remove(name: String) = Unit
}

class SecretStoreUnavailableException(message: String) : IllegalStateException(message)

