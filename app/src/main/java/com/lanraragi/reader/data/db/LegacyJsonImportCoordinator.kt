package com.lanraragi.reader.data.db

import androidx.room.withTransaction

fun interface LegacyJsonImporter {
    suspend fun import(database: ReaderDatabase)
}

data class LegacyImport(
    val key: String,
    val sourceVersion: Int,
    val importer: LegacyJsonImporter,
)

data class LegacyImportResult(
    val key: String,
    val imported: Boolean,
)

class LegacyJsonImportCoordinator(
    private val database: ReaderDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(imports: List<LegacyImport>): List<LegacyImportResult> {
        return imports.map { legacyImport ->
            require(legacyImport.key.isNotBlank()) { "legacy import key must not be blank" }
            val imported = database.withTransaction {
                val previous = database.legacyImportStateDao().find(legacyImport.key)
                if (previous != null && previous.sourceVersion >= legacyImport.sourceVersion) {
                    return@withTransaction false
                }
                legacyImport.importer.import(database)
                database.legacyImportStateDao().upsert(
                    LegacyImportStateEntity(
                        importKey = legacyImport.key,
                        sourceVersion = legacyImport.sourceVersion,
                        completedAt = clock(),
                    ),
                )
                true
            }
            LegacyImportResult(legacyImport.key, imported)
        }
    }
}
