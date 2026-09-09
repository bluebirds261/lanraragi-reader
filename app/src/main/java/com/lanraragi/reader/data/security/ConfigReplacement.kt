package com.lanraragi.reader.data.security

import kotlinx.serialization.json.JsonObject

/**
 * Applies an already validated, non-sensitive snapshot as one all-or-nothing replacement.
 * Implementations should use a transactional DataStore edit or an atomic file move.
 */
fun interface AtomicConfigReplacement {
    suspend fun replace(config: JsonObject)
}

/** Import orchestration keeps parse/validate/migrate separate from persistence. */
class ConfigImportCoordinator(private val replacement: AtomicConfigReplacement) {
    suspend fun import(payload: String): ImportedConfig {
        val result = ConfigTransferCodec.import(payload)
        replacement.replace(result.config)
        return result
    }
}
