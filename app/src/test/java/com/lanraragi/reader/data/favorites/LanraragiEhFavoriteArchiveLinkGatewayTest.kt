package com.lanraragi.reader.data.favorites

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.api.LanraragiApi
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class LanraragiEhFavoriteArchiveLinkGatewayTest {
    @Test
    fun resolvesExactTrustedSourceIdentityAcrossCatalogPages() = runTest {
        val requestedStarts = mutableListOf<Int?>()
        val repository = LanraragiRepository(fakeApi { start ->
            requestedStarts += start
            when (start) {
                0 -> success(
                    """
                    {
                      "data": [
                        {
                          "arcid": "exact-a",
                          "title": "unrelated",
                          "tags": "artist:Alice,source:https://e-hentai.org/g/123/abc123/"
                        },
                        {
                          "arcid": "wrong-host",
                          "title": "[456] def456",
                          "tags": "source:https://example.test/g/456/def456/"
                        }
                      ],
                      "recordsTotal": 3
                    }
                    """.trimIndent(),
                )
                2 -> success(
                    """
                    {
                      "data": [
                        {
                          "arcid": "exact-b",
                          "title": "another title",
                          "tags": "source:https://exhentai.org/g/456/def456"
                        }
                      ],
                      "recordsTotal": 3
                    }
                    """.trimIndent(),
                )
                else -> error("unexpected catalog offset $start")
            }
        })
        val gateway = LanraragiEhFavoriteArchiveLinkGateway(repository)

        val links = gateway.resolve(
            setOf(
                EhFavoriteSourceIdentity("123", "abc123"),
                EhFavoriteSourceIdentity("456", "def456"),
            ),
        )

        assertEquals(listOf(0, 2), requestedStarts)
        assertEquals(
            mapOf(
                EhFavoriteSourceIdentity("123", "abc123") to "exact-a",
                EhFavoriteSourceIdentity("456", "def456") to "exact-b",
            ),
            links.associate { it.sourceIdentity to it.lanraragiArchiveId },
        )
    }

    @Test
    fun emptyIdentitySetDoesNotScanCatalog() = runTest {
        var calls = 0
        val gateway = LanraragiEhFavoriteArchiveLinkGateway(
            LanraragiRepository(fakeApi {
                calls += 1
                success("[]")
            }),
        )

        assertEquals(emptyList<EhFavoriteArchiveLink>(), gateway.resolve(emptySet()))
        assertEquals(0, calls)
    }

    private fun fakeApi(
        getArchives: (Int?) -> Response<okhttp3.ResponseBody>,
    ): LanraragiApi {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            LanraragiApi::class.java.classLoader,
            arrayOf(LanraragiApi::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getArchives" -> getArchives(args?.get(0) as Int?)
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "EhArchiveLinkGatewayTestApi"
                else -> error("Unexpected API method: ${method.name}")
            }
        } as LanraragiApi
    }

    private fun success(body: String): Response<okhttp3.ResponseBody> =
        Response.success(body.toResponseBody(JSON))

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
