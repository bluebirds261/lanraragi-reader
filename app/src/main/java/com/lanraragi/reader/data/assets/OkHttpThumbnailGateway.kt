package com.lanraragi.reader.data.assets

import com.lanraragi.reader.data.api.ApiClient
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** LANraragi thumbnail transport, isolated from thumbnail state coordination. */
class OkHttpThumbnailGateway(
    private val client: OkHttpClient = ApiClient.okHttpClient,
    private val coverUrl: (String) -> String = ApiClient::thumbnailUrl,
    private val jobUrl: (String) -> String = { jobId ->
        "${ApiClient.SENTINEL_BASE}api/minion/$jobId"
    },
) : ThumbnailGateway {
    override suspend fun probeCover(arcid: String): ThumbnailProbe {
        val url = runCatching {
            coverUrl(arcid).toHttpUrl().newBuilder()
                .addQueryParameter("no_fallback", "true")
                .build()
        }.getOrElse { return ThumbnailProbe.Failed(message = "Invalid thumbnail URL") }
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    when (response.code) {
                        200 -> ThumbnailProbe.Ready(ThumbnailResource(url.toString()))
                        202 -> {
                            val jobId = response.body?.string()?.let(ThumbnailWireParser::queuedJobId)
                            if (jobId.isNullOrBlank()) {
                                ThumbnailProbe.Failed(202, "Malformed thumbnail queue response")
                            } else {
                                ThumbnailProbe.Queued(jobId)
                            }
                        }
                        else -> ThumbnailProbe.Failed(response.code, "Thumbnail request failed")
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            ThumbnailProbe.Failed(message = io.message ?: "Thumbnail request failed")
        }
    }

    override suspend fun job(jobId: String): ThumbnailJobState {
        val url = runCatching { jobUrl(jobId).toHttpUrl() }
            .getOrElse { return ThumbnailJobState.Failed(message = "Invalid thumbnail job URL") }
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (response.code != 200) {
                        ThumbnailJobState.Failed(response.code, "Thumbnail job request failed")
                    } else {
                        ThumbnailWireParser.jobState(response.body?.string().orEmpty())
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            ThumbnailJobState.Failed(message = io.message ?: "Thumbnail job request failed")
        }
    }
}
