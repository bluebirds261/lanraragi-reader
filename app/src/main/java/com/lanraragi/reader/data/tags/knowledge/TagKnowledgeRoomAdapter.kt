package com.lanraragi.reader.data.tags.knowledge

import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.db.TagDictionaryEntity
import com.lanraragi.reader.data.db.TagDictionaryFtsEntity
import com.lanraragi.reader.data.db.TagFrequencyEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room implementation of the staging/active boundary. Its DAO exposes legacy synchronous methods,
 * so every DAO access is contained in the IO facade below; callers only see suspend operations.
 */
class RoomTagKnowledgeStore(
    private val database: ReaderDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : TagKnowledgeStore, TagKnowledgeSearchStore {
    override suspend fun current(): TagKnowledgeSnapshot? = withContext(Dispatchers.IO) {
        loadActive()
    }

    override suspend fun searchCandidates(match: String, limit: Int): List<TagDictionaryRecord> = withContext(Dispatchers.IO) {
        database.tagKnowledgeDao().searchFts(match, limit.coerceIn(1, 200)).map {
            TagDictionaryRecord(
                namespace = it.namespace,
                tagKey = it.tagKey,
                translatedName = it.translatedName,
                fullName = it.fullName,
                intro = it.intro,
                links = it.links,
                dataVersion = it.dataVersion,
            )
        }
    }

    override suspend fun stage(candidate: TagKnowledgeSnapshot): TagKnowledgeStage = withContext(Dispatchers.IO) {
        val normalized = candidate.normalized()
        val active = loadActive()
        require(active == null || compareTagKnowledgeVersions(normalized.version, active.version) > 0) {
            "version ${normalized.version} is not newer than active ${active?.version}"
        }
        TagKnowledgeStage(normalized)
    }

    override suspend fun activate(staged: TagKnowledgeStage): TagKnowledgeUpdate = withContext(Dispatchers.IO) {
        val candidate = try {
            staged.snapshot.normalized()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withContext TagKnowledgeUpdate.Rejected(error.message ?: "invalid staged snapshot", loadActive())
        }
        try {
            val now = clock()
            database.runInTransaction {
                val dao = database.tagKnowledgeDao()
                val retained = snapshotFromDao()
                require(retained == null || compareTagKnowledgeVersions(candidate.version, retained.version) > 0) {
                    "version ${candidate.version} is not newer than active ${retained?.version}"
                }
                dao.clearDictionarySync()
                dao.clearFrequenciesSync()
                dao.clearSearchIndexSync()
                dao.replaceDictionarySync(candidate.dictionary.map {
                    TagDictionaryEntity(
                        namespace = it.namespace,
                        tagKey = it.tagKey,
                        translatedName = it.translatedName,
                        fullName = it.fullName,
                        intro = it.intro,
                        links = it.links,
                        dataVersion = candidate.version,
                        updatedAt = now,
                    )
                })
                dao.replaceFrequenciesSync(candidate.frequencies.map {
                    TagFrequencyEntity(it.namespace, it.tagKey, it.source, it.count, candidate.version, now)
                })
                dao.replaceSearchIndexSync(candidate.dictionary.map {
                    TagDictionaryFtsEntity(
                        canonicalKey = "${it.namespace}:${it.tagKey}",
                        namespace = it.namespace,
                        tagKey = it.tagKey,
                        translatedName = it.translatedName.orEmpty(),
                        fullName = it.fullName.orEmpty(),
                        intro = it.intro.orEmpty(),
                    )
                })
            }
            TagKnowledgeUpdate.Accepted(candidate.copy(updatedAt = now))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            TagKnowledgeUpdate.Rejected(error.message ?: "unable to activate staged tag knowledge", loadActive())
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        database.runInTransaction {
            database.tagKnowledgeDao().clearDictionarySync()
            database.tagKnowledgeDao().clearFrequenciesSync()
            database.tagKnowledgeDao().clearSearchIndexSync()
        }
    }

    /** Must only be called from the IO facade methods above. */
    private fun loadActive(): TagKnowledgeSnapshot? = snapshotFromDao()

    /** Must only be called from the IO facade methods above or the Room transaction it opens. */
    private fun snapshotFromDao(): TagKnowledgeSnapshot? {
        val dao = database.tagKnowledgeDao()
        val dictionaries = dao.loadDictionarySync()
        if (dictionaries.isEmpty()) return null
        val version = dictionaries.first().dataVersion
        return TagKnowledgeSnapshot(
            version = version,
            dictionary = dictionaries.map {
                TagDictionaryRecord(
                    namespace = it.namespace,
                    tagKey = it.tagKey,
                    translatedName = it.translatedName,
                    fullName = it.fullName,
                    intro = it.intro,
                    links = it.links,
                    dataVersion = it.dataVersion,
                )
            },
            frequencies = dao.loadFrequenciesSync().map {
                TagFrequencyRecord(it.namespace, it.tagKey, it.source, it.count, it.dataVersion)
            },
            updatedAt = dictionaries.maxOf { it.updatedAt },
        ).normalized()
    }
}

internal fun compareTagKnowledgeVersions(left: String, right: String): Int {
    val a = left.trim().split('.', '-', '_')
    val b = right.trim().split('.', '-', '_')
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrNull(i).orEmpty()
        val y = b.getOrNull(i).orEmpty()
        val comparison = if (x.all(Char::isDigit) && y.all(Char::isDigit)) {
            (x.toLongOrNull() ?: Long.MAX_VALUE).compareTo(y.toLongOrNull() ?: Long.MAX_VALUE)
        } else {
            x.compareTo(y, ignoreCase = true)
        }
        if (comparison != 0) return comparison
    }
    return 0
}
