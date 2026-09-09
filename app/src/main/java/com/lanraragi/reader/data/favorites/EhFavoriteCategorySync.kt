package com.lanraragi.reader.data.favorites

import kotlinx.coroutines.CancellationException

enum class EhFavoriteMappingMode {
    ONE_WAY_TO_LANRARAGI,
}

data class EhFavoriteMapping(
    val slotIndex: Int,
    val lanraragiCategoryId: String,
    val mode: EhFavoriteMappingMode = EhFavoriteMappingMode.ONE_WAY_TO_LANRARAGI,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(slotIndex in EhFavoriteSlots.RANGE)
        require(lanraragiCategoryId.isNotBlank())
    }
}

interface EhFavoriteMappingStore {
    suspend fun read(slotIndex: Int): EhFavoriteMapping?
    suspend fun write(mapping: EhFavoriteMapping)
    suspend fun clear(slotIndex: Int)
}

/** LANraragi adapter boundary. The absent remove operation enforces F02's additive-only policy. */
interface EhFavoriteCategoryGateway {
    suspend fun archivesInCategory(categoryId: String): Set<String>
    suspend fun addArchiveToCategory(categoryId: String, archiveId: String)
}

/** Read-only identity index supplied by the LANraragi metadata/catalog integration. */
interface EhFavoriteArchiveLinkGateway {
    suspend fun resolve(identities: Set<EhFavoriteSourceIdentity>): List<EhFavoriteArchiveLink>
}

data class EhFavoriteArchiveLink(
    val sourceIdentity: EhFavoriteSourceIdentity,
    val lanraragiArchiveId: String,
) {
    init { require(lanraragiArchiveId.isNotBlank()) { "LANraragi archive id must not be blank" } }
}

/**
 * Service-level preview. The confirmation token is produced only after a snapshot,
 * persisted mapping and exact identity join have all been read together.
 */
data class EhFavoriteCategorySyncPreview(
    val snapshotFetchedAt: Long,
    val plan: EhFavoriteCategoryPlan,
    internal val confirmation: EhFavoriteCategorySyncConfirmation,
) {
    val addCount: Int get() = plan.addCount
    val keepCount: Int get() = plan.keepCount
    val unmatchedCount: Int get() = plan.unmatchedCount
}

class EhFavoriteCategorySyncConfirmation internal constructor(
    internal val snapshotFetchedAt: Long,
    internal val mapping: EhFavoriteMapping,
    internal val plan: EhFavoriteCategoryPlan,
)

sealed interface EhFavoriteCategoryPreviewResult {
    data class Ready(val preview: EhFavoriteCategorySyncPreview) : EhFavoriteCategoryPreviewResult
    data object NoSnapshot : EhFavoriteCategoryPreviewResult
    data object NoMapping : EhFavoriteCategoryPreviewResult
}

/**
 * F02 application service. It persists only slot/category mappings, reads the cached E-H
 * snapshot, joins only exact gid/token identities, and performs additions after explicit approval.
 */
class EhFavoriteCategorySyncService(
    private val mappings: EhFavoriteMappingStore,
    private val snapshots: EhFavoriteStore,
    private val categoryGateway: EhFavoriteCategoryGateway,
    private val archiveLinks: EhFavoriteArchiveLinkGateway,
    private val executor: EhFavoriteCategorySyncExecutor = EhFavoriteCategorySyncExecutor(categoryGateway),
) {
    suspend fun saveMapping(mapping: EhFavoriteMapping) {
        mappings.write(mapping)
    }

    suspend fun clearMapping(slotIndex: Int) {
        mappings.clear(slotIndex)
    }

    suspend fun preview(slotIndex: Int): EhFavoriteCategoryPreviewResult {
        val mapping = mappings.read(slotIndex) ?: return EhFavoriteCategoryPreviewResult.NoMapping
        val snapshot = snapshots.read() ?: return EhFavoriteCategoryPreviewResult.NoSnapshot
        val slotEntries = snapshot.entries.filter { it.slotIndex == slotIndex }
        val links = archiveLinks.resolve(slotEntries.map(EhFavoriteEntry::sourceIdentity).toSet())
            .associateBy { it.sourceIdentity.normalized() }
        val linkedEntries = slotEntries.map { entry ->
            entry.copy(linkedLanraragiArchiveId = links[entry.sourceIdentity]?.lanraragiArchiveId)
        }
        val plan = EhFavoriteCategoryPlanner.plan(
            mapping = mapping,
            entries = linkedEntries,
            alreadyInCategory = categoryGateway.archivesInCategory(mapping.lanraragiCategoryId),
        )
        val confirmation = EhFavoriteCategorySyncConfirmation(snapshot.fetchedAt, mapping, plan)
        return EhFavoriteCategoryPreviewResult.Ready(
            EhFavoriteCategorySyncPreview(snapshot.fetchedAt, plan, confirmation),
        )
    }

    /** The caller must present a preview and deliberately pass its confirmation to mutate LANraragi. */
    suspend fun confirmAdditiveSync(
        preview: EhFavoriteCategorySyncPreview,
    ): EhFavoriteCategorySyncResult {
        val confirmation = preview.confirmation
        val current = mappings.read(confirmation.mapping.slotIndex)
        require(current == confirmation.mapping) { "favorite mapping changed; preview again before syncing" }
        val snapshot = snapshots.read()
        require(snapshot?.fetchedAt == confirmation.snapshotFetchedAt) {
            "favorite snapshot changed; preview again before syncing"
        }
        return executor.execute(confirmation.plan)
    }
}

data class EhFavoriteCategoryPlan(
    val mapping: EhFavoriteMapping,
    val additions: List<String>,
    val alreadyPresent: List<String>,
    val unmatched: List<EhFavoriteEntry>,
) {
    val addCount: Int get() = additions.size
    val keepCount: Int get() = alreadyPresent.size
    val unmatchedCount: Int get() = unmatched.size
}

/**
 * Produces a reviewable additive plan. No title matching appears here: entries
 * without a gid/token/source-derived archive link are explicitly unmatched.
 */
object EhFavoriteCategoryPlanner {
    fun plan(
        mapping: EhFavoriteMapping,
        entries: Iterable<EhFavoriteEntry>,
        alreadyInCategory: Set<String>,
    ): EhFavoriteCategoryPlan {
        val slotEntries = entries.filter { it.slotIndex == mapping.slotIndex }
        val linked = linkedArchives(slotEntries)
        return EhFavoriteCategoryPlan(
            mapping = mapping,
            additions = linked.filterNot(alreadyInCategory::contains),
            alreadyPresent = linked.filter(alreadyInCategory::contains),
            unmatched = slotEntries.filter { it.linkedLanraragiArchiveId.isNullOrBlank() },
        )
    }

    private fun linkedArchives(entries: List<EhFavoriteEntry>): List<String> = entries
        .mapNotNull { it.linkedLanraragiArchiveId?.takeIf(String::isNotBlank) }
        .distinct()
        .sorted()
}

sealed interface EhFavoriteCategorySyncResult {
    data class Applied(val added: List<String>, val kept: List<String>, val unmatched: List<EhFavoriteEntry>) : EhFavoriteCategorySyncResult
    data class Retryable(
        val added: List<String>,
        val remaining: List<String>,
        val failure: EhFavoriteSyncFailure,
    ) : EhFavoriteCategorySyncResult
}

/** Executes only an approved plan and never removes a LANraragi category membership. */
class EhFavoriteCategorySyncExecutor(
    private val categoryGateway: EhFavoriteCategoryGateway,
) {
    suspend fun execute(plan: EhFavoriteCategoryPlan): EhFavoriteCategorySyncResult {
        val added = mutableListOf<String>()
        for ((index, archiveId) in plan.additions.withIndex()) {
            try {
                categoryGateway.addArchiveToCategory(plan.mapping.lanraragiCategoryId, archiveId)
                added += archiveId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: EhFavoriteSyncException) {
                return EhFavoriteCategorySyncResult.Retryable(added, plan.additions.drop(index), error.failure)
            } catch (error: java.io.IOException) {
                return EhFavoriteCategorySyncResult.Retryable(
                    added,
                    plan.additions.drop(index),
                    EhFavoriteSyncFailure.Network(error.message ?: "Network request failed", error),
                )
            }
        }
        return EhFavoriteCategorySyncResult.Applied(added, plan.alreadyPresent, plan.unmatched)
    }
}
