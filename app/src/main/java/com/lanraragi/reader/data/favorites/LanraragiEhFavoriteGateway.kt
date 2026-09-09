package com.lanraragi.reader.data.favorites

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.ApiException
import java.io.IOException

/**
 * LANraragi side of E-Hentai favorite synchronization.
 *
 * Category reads are performed through the regular repository and additions use
 * the server's `PUT /api/categories/{id}/{archive}` endpoint. There is deliberately
 * no removal method, preserving the additive-only F02 contract.
 */
class LanraragiEhFavoriteGateway(
    private val repository: LanraragiRepository,
) : EhFavoriteCategoryGateway {
    override suspend fun archivesInCategory(categoryId: String): Set<String> = request {
        repository.getCategories()
            .firstOrNull { it.id == categoryId }
            ?.archives
            ?.toSet()
            ?: emptySet()
    }

    override suspend fun addArchiveToCategory(categoryId: String, archiveId: String): Unit = request {
        repository.addArchiveToCategory(categoryId, archiveId)
    }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (error: ApiException) {
        throw EhFavoriteSyncException(
            EhFavoriteSyncFailure.Http(error.statusCode ?: 0, error.message ?: "LANraragi request failed"),
        )
    } catch (error: IOException) {
        throw EhFavoriteSyncException(
            EhFavoriteSyncFailure.Network(error.message ?: "LANraragi network request failed", error),
        )
    }
}
