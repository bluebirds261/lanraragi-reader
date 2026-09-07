package com.lanraragi.reader.domain.model

data class ArchiveCapabilities(
    val canRead: Boolean = true,
    val canUpload: Boolean = false,
    val canEditServerMetadata: Boolean = false,
    val canEditServerToc: Boolean = false,
    val canDeleteServerCopy: Boolean = false,
    val canSaveOffline: Boolean = false,
    val canScrapeMetadataLocally: Boolean = true,
) {
    companion object {
        fun forIdentity(identity: ArchiveIdentity): ArchiveCapabilities = when (identity) {
            is ArchiveIdentity.Remote -> ArchiveCapabilities(
                canEditServerMetadata = true,
                canEditServerToc = true,
                canDeleteServerCopy = true,
                canSaveOffline = true,
            )
            is ArchiveIdentity.LocalSaf -> ArchiveCapabilities()
            is ArchiveIdentity.Tankoubon -> ArchiveCapabilities(
                canEditServerMetadata = true,
                canDeleteServerCopy = true,
                canSaveOffline = true,
            )
        }
    }
}
