package com.lanraragi.reader.data.security

import kotlinx.coroutines.flow.Flow

/** Storage boundary for security switches. Values stored here must never contain credentials. */
interface SecuritySettingsStore {
    val settings: Flow<SecuritySettings>
    suspend fun replace(settings: SecuritySettings)
}

