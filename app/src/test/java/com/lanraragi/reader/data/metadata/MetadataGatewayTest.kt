package com.lanraragi.reader.data.metadata

import com.lanraragi.reader.data.ApiException
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.api.LanraragiApi
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.TagSource
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class MetadataGatewayTest {
    @Test
    fun fetchReadsServerMetadataAndExtractsSourceTag() = runTest {
        val requestedIds = mutableListOf<String>()
        val gateway = gateway(
            getMetadata = { arcid ->
                requestedIds += arcid
                success(
                    """
                    {
                      "arcid": "arc-1",
                      "title": "Remote title",
                      "summary": "Remote summary",
                      "tags": " artist:Alice , source:https://Example.test/Books/42 , female:glasses "
                    }
                    """.trimIndent(),
                )
            },
        )

        val snapshot = gateway.fetch("arc-1")

        assertEquals(listOf("arc-1"), requestedIds)
        assertEquals("Remote title", snapshot.title)
        assertEquals("Remote summary", snapshot.summary)
        assertEquals("https://Example.test/Books/42", snapshot.sourceUrl)
        assertEquals(
            setOf(
                "artist:alice",
                "female:glasses",
                "source:https://example.test/books/42",
            ),
            snapshot.tags.map { it.full }.toSet(),
        )
        assertTrue(snapshot.tags.all { it.source == TagSource.LANRARAGI })
    }

    @Test
    fun putSendsCompleteTitleTagsSummaryAndPreservesRawSourceTag() = runTest {
        var request: UpdateRequest? = null
        val gateway = gateway(
            updateMetadata = {
                request = it
                success("""{"operation":"update_metadata","success":1}""")
            },
        )

        gateway.put(
            MetadataPutPayload(
                arcid = "arc-2",
                title = "Replacement title",
                summary = "Replacement summary",
                tags = linkedSetOf(
                    CanonicalTag.parse("source:https://Example.test/Books/42", source = TagSource.USER),
                    CanonicalTag.parse("artist:Alice", source = TagSource.EHENTAI),
                ),
            ),
        )

        assertEquals(
            UpdateRequest(
                arcid = "arc-2",
                title = "Replacement title",
                tags = "artist:Alice,source:https://Example.test/Books/42",
                summary = "Replacement summary",
            ),
            request,
        )
    }

    @Test
    fun putAlwaysSuppliesTitleAndTagsWhenPayloadValuesAreBlankOrNull() = runTest {
        var request: UpdateRequest? = null
        val gateway = gateway(
            updateMetadata = {
                request = it
                success("""{"operation":"update_metadata","success":1}""")
            },
        )

        gateway.put(
            MetadataPutPayload(
                arcid = "arc-empty",
                title = null,
                summary = null,
                tags = emptySet(),
            ),
        )

        assertEquals(
            UpdateRequest(arcid = "arc-empty", title = "", tags = "", summary = null),
            request,
        )
    }

    @Test
    fun fetchedTagsKeepClientAnnotationsThroughPersistenceAndWriteBackRawValues() = runTest {
        var request: UpdateRequest? = null
        val gateway = gateway(
            getMetadata = {
                success(
                    """
                    {
                      "arcid": "arc-roundtrip",
                      "title": "Original",
                      "summary": "Summary",
                      "tags": "artist:Alice,source:https://e-hentai.org/g/123/token"
                    }
                    """.trimIndent(),
                )
            },
            updateMetadata = {
                request = it
                success("""{"success":1}""")
            },
        )
        val fetched = gateway.fetch("arc-roundtrip")
        val annotated = fetched.copy(
            tags = fetched.tags.mapTo(linkedSetOf()) { tag ->
                if (tag.full == "artist:alice") {
                    tag.copy(displayNameZh = "爱丽丝", source = TagSource.EHENTAI, confidence = 0.91f)
                } else {
                    tag
                }
            },
        )

        val restored = MetadataSnapshotCodec.decode(MetadataSnapshotCodec.encode(annotated))
        gateway.put(
            MetadataPutPayload(
                arcid = "arc-roundtrip",
                title = restored.title,
                summary = restored.summary,
                tags = restored.tags,
            ),
        )

        val restoredArtist = restored.tags.single { it.full == "artist:alice" }
        assertEquals("爱丽丝", restoredArtist.displayNameZh)
        assertEquals(TagSource.EHENTAI, restoredArtist.source)
        assertEquals(0.91f, restoredArtist.confidence)
        assertEquals(
            "artist:Alice,source:https://e-hentai.org/g/123/token",
            request!!.tags,
        )
    }

    @Test
    fun putMapsHttp423ToLockedFailure() = runTest {
        val gateway = gateway(
            updateMetadata = { Response.error(423, "locked".toResponseBody(JSON)) },
        )

        val failure = expectRemoteFailure {
            gateway.put(MetadataPutPayload("arc-locked", "title", "summary", emptySet()))
        }

        assertEquals(MetadataRemoteFailureReason.LOCKED, failure.reason)
        assertEquals(423, failure.statusCode)
    }

    @Test
    fun fetchMapsOtherHttpErrorsToHttpFailure() = runTest {
        val gateway = gateway(
            getMetadata = { Response.error(500, "server error".toResponseBody(JSON)) },
        )

        val failure = expectRemoteFailure { gateway.fetch("arc-error") }

        assertEquals(MetadataRemoteFailureReason.HTTP, failure.reason)
        assertEquals(500, failure.statusCode)
    }

    @Test
    fun fetchMapsTransportIOExceptionToNetworkFailure() = runTest {
        val gateway = gateway(
            getMetadata = { Response.success(failingBody()) },
        )

        val failure = expectRemoteFailure { gateway.fetch("arc-offline") }

        assertEquals(MetadataRemoteFailureReason.NETWORK, failure.reason)
        assertEquals(null, failure.statusCode)
        assertTrue(failure.cause is ApiException)
    }

    private fun gateway(
        getMetadata: (String) -> Response<ResponseBody> = { error("unexpected GET metadata") },
        updateMetadata: (UpdateRequest) -> Response<ResponseBody> = { error("unexpected PUT metadata") },
    ): LanraragiMetadataGateway = LanraragiMetadataGateway(LanraragiRepository(fakeApi(getMetadata, updateMetadata)))

    private fun fakeApi(
        getMetadata: (String) -> Response<ResponseBody>,
        updateMetadata: (UpdateRequest) -> Response<ResponseBody>,
    ): LanraragiApi {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            LanraragiApi::class.java.classLoader,
            arrayOf(LanraragiApi::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getMetadata" -> getMetadata(args!![0] as String)
                "updateMetadata" -> updateMetadata(
                    UpdateRequest(
                        arcid = args!![0] as String,
                        title = args[1] as String?,
                        tags = args[2] as String?,
                        summary = args[3] as String?,
                    ),
                )
                "equals" -> proxy === args!![0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "MetadataGatewayTestApi"
                else -> error("Unexpected API method: ${method.name}")
            }
        } as LanraragiApi
    }

    private suspend fun expectRemoteFailure(block: suspend () -> Unit): MetadataRemoteException = try {
        block()
        throw AssertionError("Expected MetadataRemoteException")
    } catch (failure: MetadataRemoteException) {
        failure
    }

    private fun success(body: String): Response<ResponseBody> = Response.success(body.toResponseBody(JSON))

    private fun failingBody(): ResponseBody = object : ResponseBody() {
        override fun contentType() = JSON

        override fun contentLength() = -1L

        override fun source(): BufferedSource = throw IOException("socket reset")
    }

    private data class UpdateRequest(
        val arcid: String,
        val title: String?,
        val tags: String?,
        val summary: String?,
    )

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
