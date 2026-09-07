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

class LegacyJsonImportCoordinator(
    private val database: ReaderDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(imports: List<LegacyImport>) {
        imports.forEach { legacyImport ->
            require(legacyImport.key.isNotBlank()) { "legacy import key must not be blank" }
            database.withTransaction {
                val previous = database.legacyImportStateDao().find(legacyImport.key)
                if (previous != null && previous.sourceVersion >= legacyImport.sourceVersion) {
                    return@withTransaction
                }
                legacyImport.importer.import(database)
                database.legacyImportStateDao().insert(
                    LegacyImportStateEntity(
                        importKey = legacyImport.key,
                        sourceVersion = legacyImport.sourceVersion,
                        completedAt = clock(),
                    ),
                )
            }
        }
    }
}
