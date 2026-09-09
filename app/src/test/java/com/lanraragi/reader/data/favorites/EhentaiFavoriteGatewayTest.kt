package com.lanraragi.reader.data.favorites

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EhentaiFavoriteGatewayTest {
    @Test fun parsesTenSlotsAndGalleryIdentityWithGetOnlyRequests() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val html = """
            <a href="favorites.php?favcat=0">Artists (12)</a>
            <a href="favorites.php?favcat=1">Read Later [3]</a>
            <div><a href="https://e-hentai.org/g/12345/AbC9/">Gallery</a></div>
        """.trimIndent()
        val gateway = EhentaiFavoriteGateway(object : EhFavoriteHttpClient {
            override suspend fun get(url: String, cookieHeader: String): EhFavoriteHttpResponse {
                calls += url to cookieHeader
                return EhFavoriteHttpResponse(200, html)
            }
        }, clock = { 77L })

        val snapshot = gateway.fetchFavorites(EhCredentials(EhCookieJar("ipb_member_id=secret")))

        assertEquals((0..9).toList(), snapshot.normalizedSlots.map { it.slotIndex })
        assertEquals("Artists", snapshot.normalizedSlots[0].remoteName)
        assertEquals(12, snapshot.normalizedSlots[0].remoteCount)
        assertEquals("12345", snapshot.entries.first().gid)
        assertEquals("AbC9", snapshot.entries.first().token)
        assertEquals(10, calls.size)
        assertTrue(calls.all { it.second == "ipb_member_id=secret" })
    }

    @Test fun mapsLoginAndRateLimitResponsesWithoutExposingCookie() = runTest {
        val login = runCatching {
            EhentaiFavoriteGateway(object : EhFavoriteHttpClient {
                override suspend fun get(url: String, cookieHeader: String) = EhFavoriteHttpResponse(200, "Please log in")
            }).fetchFavorites(EhCredentials(EhCookieJar("do-not-leak")))
        }.exceptionOrNull() as EhFavoriteSyncException
        assertTrue(login.failure is EhFavoriteSyncFailure.CookieInvalid)
        assertTrue(login.message?.contains("do-not-leak") != true)

        val limited = runCatching {
            EhentaiFavoriteGateway(object : EhFavoriteHttpClient {
                override suspend fun get(url: String, cookieHeader: String) = EhFavoriteHttpResponse(429, "", mapOf("Retry-After" to "4"))
            }).fetchFavorites(EhCredentials(EhCookieJar("do-not-leak")))
        }.exceptionOrNull() as EhFavoriteSyncException
        assertEquals(4_000L, (limited.failure as EhFavoriteSyncFailure.RateLimited).retryAfterMillis)
    }

    @Test fun mapsUnauthorizedAndNetworkFailures() = runTest {
        val unauthorized = runCatching {
            EhentaiFavoriteGateway(object : EhFavoriteHttpClient {
                override suspend fun get(url: String, cookieHeader: String) = EhFavoriteHttpResponse(403, "")
            }).fetchFavorites(EhCredentials(EhCookieJar("secret")))
        }.exceptionOrNull() as EhFavoriteSyncException
        assertTrue(unauthorized.failure is EhFavoriteSyncFailure.CookieInvalid)

        val network = runCatching {
            EhentaiFavoriteGateway(object : EhFavoriteHttpClient {
                override suspend fun get(url: String, cookieHeader: String): EhFavoriteHttpResponse = throw IOException("offline")
            }).fetchFavorites(EhCredentials(EhCookieJar("secret")))
        }.exceptionOrNull() as EhFavoriteSyncException
        assertTrue(network.failure is EhFavoriteSyncFailure.Network)
        assertTrue(network.failure.retryable)
    }
}
