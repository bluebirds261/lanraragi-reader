package com.lanraragi.reader.data.assets

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpThumbnailGatewayTest {
    @Test
    fun probeCoverRequestsCoverWithoutPageAndAddsNoFallback() = runBlocking {
        val seen = AtomicReference<okhttp3.HttpUrl>()
        val gateway = gateway(code = 200, body = "binary") { request -> seen.set(request.url) }

        val result = gateway.probeCover("arc-7")

        assertTrue(result is ThumbnailProbe.Ready)
        val requestUrl = seen.get()
        assertEquals("/api/archives/arc-7/thumbnail", requestUrl.encodedPath)
        assertEquals("true", requestUrl.queryParameter("no_fallback"))
        assertNull(requestUrl.queryParameter("page"))
        val resource = (result as ThumbnailProbe.Ready).resource.value
        assertEquals(requestUrl.toString(), resource)
        assertTrue("ready resource must not include page query", !resource.contains("page="))
    }

    @Test
    fun probeCoverMapsNumericQueuedJob() = runBlocking {
        val gateway = gateway(code = 202, body = "{\"success\":1,\"job\":123}")

        assertEquals(ThumbnailProbe.Queued("123"), gateway.probeCover("arc-7"))
    }

    @Test
    fun probeCoverPreservesStatusForMalformedAndErrorResponses() = runBlocking {
        val statuses = listOf(202, 401, 423, 429, 500)

        statuses.forEach { status ->
            val body = if (status == 202) "{\"success\":1}" else "error"
            val result = gateway(code = status, body = body).probeCover("arc-7")
            assertTrue("status $status should fail", result is ThumbnailProbe.Failed)
            assertEquals(status, (result as ThumbnailProbe.Failed).status)
        }
    }

    @Test
    fun jobRequestsMinionPathAndParsesInactiveAndFinished() = runBlocking {
        val seen = AtomicReference<okhttp3.HttpUrl>()
        val inactive = gateway(code = 200, body = "{\"state\":\"inactive\"}") { request ->
            seen.set(request.url)
        }

        assertEquals(ThumbnailJobState.Active, inactive.job("job-7"))
        assertEquals("/api/minion/job-7", seen.get().encodedPath)

        val finished = gateway(code = 200, body = "{\"state\":\"finished\"}")
        assertEquals(ThumbnailJobState.Finished, finished.job("job-7"))
    }

    private fun gateway(
        code: Int,
        body: String,
        onRequest: (okhttp3.Request) -> Unit = {},
    ): OkHttpThumbnailGateway {
        val interceptor = Interceptor { chain ->
            onRequest(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody())
                .build()
        }
        return OkHttpThumbnailGateway(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )
    }
}
