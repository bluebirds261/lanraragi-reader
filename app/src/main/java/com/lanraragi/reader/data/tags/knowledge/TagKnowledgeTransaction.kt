package com.lanraragi.reader.data.tags.knowledge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Adapter boundary: Room implementation can wrap replace in a single withTransaction call. */
interface TagKnowledgeStore {
    suspend fun current(): TagKnowledgeSnapshot?
    /** Parse/validate without changing active state. */
    suspend fun stage(candidate: TagKnowledgeSnapshot): TagKnowledgeStage
    /** Commit a previously staged snapshot as the sole active snapshot. */
    suspend fun activate(staged: TagKnowledgeStage): TagKnowledgeUpdate
    suspend fun clear()
    suspend fun replaceAtomically(candidate: TagKnowledgeSnapshot): TagKnowledgeUpdate = try {
        activate(stage(candidate))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        TagKnowledgeUpdate.Rejected(error.message ?: "invalid snapshot", current())
    }
}

data class TagKnowledgeStage internal constructor(val snapshot: TagKnowledgeSnapshot)

sealed class TagKnowledgeUpdate {
    data class Accepted(val snapshot: TagKnowledgeSnapshot) : TagKnowledgeUpdate()
    data class Rejected(val reason: String, val retained: TagKnowledgeSnapshot?) : TagKnowledgeUpdate()
}

/** In-memory reference implementation used by tests and as a deterministic staging model. */
class InMemoryTagKnowledgeStore(initial: TagKnowledgeSnapshot? = null) : TagKnowledgeStore {
    private var snapshot: TagKnowledgeSnapshot? = initial?.normalized()
    private val mutex = Mutex()

    override suspend fun current(): TagKnowledgeSnapshot? = mutex.withLock { snapshot }

    override suspend fun stage(candidate: TagKnowledgeSnapshot): TagKnowledgeStage = mutex.withLock {
        val staged = candidate.normalized()
        val retained = snapshot
        require(retained == null || compareTagKnowledgeVersions(staged.version, retained.version) > 0) {
            "version ${staged.version} is not newer than active ${retained?.version}"
        }
        TagKnowledgeStage(staged)
    }

    override suspend fun activate(staged: TagKnowledgeStage): TagKnowledgeUpdate = mutex.withLock {
        val retained = snapshot
        if (retained != null && compareTagKnowledgeVersions(staged.snapshot.version, retained.version) <= 0) {
            return@withLock TagKnowledgeUpdate.Rejected("version ${staged.snapshot.version} is not newer than active ${retained.version}", retained)
        }
        snapshot = staged.snapshot
        TagKnowledgeUpdate.Accepted(staged.snapshot)
    }

    override suspend fun clear() = mutex.withLock {
        snapshot = null
    }
}

/** Pure transaction helper: parse/stage first; the old snapshot remains on every failure. */
class TagKnowledgeUpdater(private val store: TagKnowledgeStore) {
    suspend fun update(candidate: TagKnowledgeSnapshot): TagKnowledgeUpdate = store.replaceAtomically(candidate)

    suspend fun stage(candidate: TagKnowledgeSnapshot): TagKnowledgeStage = store.stage(candidate)

    suspend fun activate(staged: TagKnowledgeStage): TagKnowledgeUpdate = store.activate(staged)
}
