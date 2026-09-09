package com.lanraragi.reader.data.favorites

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Minimal, GET-only HTTP boundary for the E-Hentai favorites listing. */
interface EhFavoriteHttpClient {
    suspend fun get(url: String, cookieHeader: String): EhFavoriteHttpResponse
}

data class EhFavoriteHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Reads E-Hentai favorite lists without exposing any mutation endpoint.
 * Cookie material is passed only to the request header and never included in
 * errors, logging, persistence, or returned values.
 */
class EhentaiFavoriteGateway(
    private val httpClient: EhFavoriteHttpClient = OkHttpEhFavoriteHttpClient(),
    private val favoritesUrl: String = DEFAULT_FAVORITES_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) : EhFavoriteGateway {
    override suspend fun fetchFavorites(credentials: EhCredentials): EhFavoriteSnapshot {
        val cookie = (credentials.cookieJar as? EhCookieJar)?.cookieHeader
            ?: throw EhFavoriteSyncException(EhFavoriteSyncFailure.CookieInvalid("E-Hentai credential is unavailable"))

        try {
            val pages = EhFavoriteSlots.RANGE.map { slotIndex ->
                slotIndex to getFavoritesPage(slotIndex, cookie)
            }
            val firstPage = pages.first().second
            val slots = parseSlots(firstPage.body)
            val entries = pages.flatMap { (slotIndex, page) -> parseEntries(page.body, slotIndex) }
                .distinctBy { Triple(it.slotIndex, it.gid, it.token) }
            return EhFavoriteSnapshot(slots = slots, entries = entries, fetchedAt = clock())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: EhFavoriteSyncException) {
            throw failure
        } catch (error: IOException) {
            throw EhFavoriteSyncException(EhFavoriteSyncFailure.Network(error.message ?: "E-Hentai request failed", error))
        }
    }

    private suspend fun getFavoritesPage(slotIndex: Int, cookie: String): EhFavoriteHttpResponse {
        val response = httpClient.get(favoritesUrl.withFavoriteSlot(slotIndex), cookie)
        when (response.statusCode) {
            401, 403 -> throw EhFavoriteSyncException(EhFavoriteSyncFailure.CookieInvalid())
            429 -> throw EhFavoriteSyncException(
                EhFavoriteSyncFailure.RateLimited(response.headers.header("Retry-After").retryAfterMillis()),
            )
        }
        if (response.statusCode !in 200..299) {
            throw EhFavoriteSyncException(EhFavoriteSyncFailure.Http(response.statusCode, "E-Hentai request failed (HTTP ${response.statusCode})"))
        }
        if (response.body.isLoginPage()) throw EhFavoriteSyncException(EhFavoriteSyncFailure.CookieInvalid())
        return response
    }

    private fun parseSlots(body: String): List<EhFavoriteSlot> {
        val slots = mutableMapOf<Int, EhFavoriteSlot>()
        SLOT_LINK.findAll(body).forEach { match ->
            val index = match.groupValues[1].toIntOrNull()?.takeIf { it in EhFavoriteSlots.RANGE } ?: return@forEach
            val text = match.groupValues[2].stripHtml().decodeHtml()
            val count = text.extractCount()
            val name = text.removeCount().ifBlank { "Favorites $index" }
            slots[index] = EhFavoriteSlot(index, name, count, updatedAt = clock())
        }
        return EhFavoriteSlots.normalize(slots.values)
    }

    private fun parseEntries(body: String, slotIndex: Int): List<EhFavoriteEntry> = GALLERY_LINK.findAll(body)
        .map { match ->
            val host = match.groupValues[1].ifBlank { "e-hentai.org" }
            val gid = match.groupValues[2]
            val token = match.groupValues[3].takeIf(String::isNotBlank)
            EhFavoriteEntry(slotIndex, gid, token, "https://$host/g/$gid/${token.orEmpty()}/")
        }
        .distinctBy { it.gid to it.token }
        .toList()

    companion object {
        const val DEFAULT_FAVORITES_URL = "https://e-hentai.org/favorites.php"
        private val SLOT_LINK = Regex(
            """(?is)<a\b[^>]*href=["'][^"']*?[?&]favcat=(\d+)[^"']*["'][^>]*>(.*?)</a>""",
        )
        private val GALLERY_LINK = Regex("""(?is)href=["'](?:https?://((?:e-hentai|exhentai)\.org))?/g/(\d+)/([a-z0-9]+)/?["']""")
    }
}

private class OkHttpEhFavoriteHttpClient(
    private val client: OkHttpClient = OkHttpClient(),
) : EhFavoriteHttpClient {
    override suspend fun get(url: String, cookieHeader: String): EhFavoriteHttpResponse = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder()
                .url(url)
                .get()
                .header("Cookie", cookieHeader)
                .header("Accept", "text/html")
                .build(),
        ).execute().use { response ->
            EhFavoriteHttpResponse(
                statusCode = response.code,
                body = response.body?.string().orEmpty(),
                headers = response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() },
            )
        }
    }
}

private fun String.withFavoriteSlot(slotIndex: Int): String {
    val separator = if ('?' in this) '&' else '?'
    return "$this${separator}favcat=$slotIndex"
}

private fun Map<String, String>.header(name: String): String? = entries.firstOrNull {
    it.key.equals(name, ignoreCase = true)
}?.value

private fun String?.retryAfterMillis(): Long? = this?.trim()?.toLongOrNull()?.coerceAtLeast(0L)?.times(1_000L)

private fun String.isLoginPage(): Boolean {
    val normalized = lowercase()
    return "act=login" in normalized || "please log in" in normalized || "you are not logged in" in normalized
}

private fun String.stripHtml(): String = replace(Regex("(?is)<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

private fun String.decodeHtml(): String = replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")

private fun String.extractCount(): Int = Regex("""(?:\(|\[)\s*([\d,]+)\s*(?:\)|\])""")
    .find(this)?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

private fun String.removeCount(): String = replace(Regex("""(?:\(|\[)\s*[\d,]+\s*(?:\)|\])"""), "").trim()
