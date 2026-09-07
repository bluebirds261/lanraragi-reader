package com.lanraragi.reader.domain.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

sealed interface ArchiveIdentity {
    val sourceKey: String

    data class Remote(val arcid: String) : ArchiveIdentity {
        init {
            require(arcid.isNotBlank()) { "arcid must not be blank" }
        }

        override val sourceKey: String = "remote:${arcid.lowercase()}"
    }

    data class LocalSaf(val uri: String) : ArchiveIdentity {
        init {
            require(uri.isNotBlank()) { "uri must not be blank" }
        }

        override val sourceKey: String = "local:${sha256(uri)}"
    }

    data class Tankoubon(val tankId: String) : ArchiveIdentity {
        init {
            require(tankId.isNotBlank()) { "tankId must not be blank" }
        }

        override val sourceKey: String = "tank:${tankId.uppercase()}"
    }

    companion object {
        fun fromArchiveId(archiveId: String, localUri: String? = null): ArchiveIdentity = when {
            archiveId.startsWith("local_") && !localUri.isNullOrBlank() -> LocalSaf(localUri)
            archiveId.startsWith("TANK_", ignoreCase = true) -> Tankoubon(archiveId)
            else -> Remote(archiveId)
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
