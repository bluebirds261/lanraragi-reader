package com.lanraragi.reader.data.metadata.providers

import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Network-neutral metadata returned by an explicit native provider fetch. */
data class NativeGalleryMetadata(
    val providerId: String,
    val sourceId: String,
    val sourceUrl: String,
    val title: String? = null,
    val summary: String? = null,
    val tags: Set<CanonicalTag> = emptySet(),
    val observedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(providerId.isNotBlank())
        require(sourceId.isNotBlank())
        require(sourceUrl.isNotBlank())
    }

    fun toPatch(): MetadataPatch {
        val provenance = MetadataProvenance(
            providerId = providerId,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            confidence = 0.98f,
            fetchedAt = observedAt,
        )
        val sourceTag = CanonicalTag.parse("source:$sourceUrl", source = when (providerId) {
            "nhentai" -> TagSource.NHENTAI
            else -> TagSource.EHENTAI
        }, confidence = 0.98f)
        val allTags = tags + sourceTag
        return MetadataPatch(
            title = title?.takeIf(String::isNotBlank)?.let { MetadataField(it, provenance) },
            summary = summary?.takeIf(String::isNotBlank)?.let { MetadataField(it, provenance) },
            sourceUrl = MetadataField(sourceUrl, provenance),
            addTags = allTags,
            tagProvenance = allTags.associate { it.full to provenance },
        )
    }
}

fun interface NativeMetadataGateway {
    suspend fun fetch(providerId: String, sourceId: String, sourceUrl: String, cookieHeader: String?): NativeGalleryMetadata
}

/** Explicit fetch coordinator with per-provider cooldown. It never applies a patch itself. */
class NativeMetadataFetchCoordinator(
    private val gateway: NativeMetadataGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minimumIntervalMs: Long = 1_000L,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val lastFetch = mutableMapOf<String, Long>()
    private val limiter = Mutex()

    suspend fun fetch(
        providerId: String,
        sourceId: String,
        sourceUrl: String,
        cookieHeader: String? = null,
    ): NativeGalleryMetadata {
        val key = providerId.trim().lowercase()
        limiter.withLock {
            val elapsed = clock() - (lastFetch[key] ?: Long.MIN_VALUE)
            if (elapsed in 0 until minimumIntervalMs) wait(minimumIntervalMs - elapsed)
            lastFetch[key] = clock()
        }
        val result = try {
            gateway.fetch(key, sourceId.trim(), sourceUrl.trim(), cookieHeader)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            throw NativeMetadataFetchException("$key provider network request failed", error)
        }
        return result
    }
}

open class NativeMetadataFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)
class NativeMetadataRateLimitException(val retryAfterMs: Long?) :
    NativeMetadataFetchException("Native metadata provider rate limited the request")

/** Default HTTP gateway for the public, exact-identity APIs. Callers still need explicit confirmation. */
class HttpNativeMetadataGateway(
    // Deliberately not ApiClient.okHttpClient: its interceptor carries the LANraragi bearer key.
    private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
) : NativeMetadataGateway {
    override suspend fun fetch(providerId: String, sourceId: String, sourceUrl: String, cookieHeader: String?): NativeGalleryMetadata = withContext(Dispatchers.IO) {
        val exact = when (providerId) {
            "ehentai" -> EHentaiMetadataProvider.parseExact(sourceUrl)
                ?: throw NativeMetadataFetchException("E-Hentai source URL is not exact")
            "nhentai" -> NHentaiMetadataProvider.parseExact(sourceId)
                ?: NHentaiMetadataProvider.parseExact(sourceUrl)
                ?: throw NativeMetadataFetchException("nHentai source identifier is not exact")
            else -> throw NativeMetadataFetchException("Unsupported native provider: $providerId")
        }
        val request = when (providerId) {
            "nhentai" -> Request.Builder()
                .url("https://nhentai.net/api/gallery/${(exact as NHentaiMetadataProvider.NHentaiGallery).id}")
                .get()
            else -> {
                val gallery = exact as EHentaiMetadataProvider.EHentaiGallery
                if (gallery.host == "exhentai.org" && cookieHeader.isNullOrBlank()) {
                    throw NativeMetadataFetchException("ExHentai requires an authenticated Cookie")
                }
                val body = "{\"method\":\"gdata\",\"gidlist\":[[${gallery.gid},\"${gallery.token}\"]],\"namespace\":1}"
                    .toRequestBody(JSON)
                Request.Builder().url("https://api.e-hentai.org/api.php").post(body)
            }
        }.apply { cookieHeader?.takeIf(String::isNotBlank)?.let { header("Cookie", it) } }.build()
        val response = client.newCall(request).execute()
        response.use {
            if (it.code == 429) {
                throw NativeMetadataRateLimitException(it.header("Retry-After")?.toLongOrNull()?.times(1_000L))
            }
            if (!it.isSuccessful) throw NativeMetadataFetchException("$providerId returned HTTP ${it.code}")
            val body = it.body?.string().orEmpty()
            return@withContext if (providerId == "nhentai") {
                NativeMetadataWireParser.parseNhentai(body, exact as NHentaiMetadataProvider.NHentaiGallery, now())
            } else {
                NativeMetadataWireParser.parseEhentai(body, exact as EHentaiMetadataProvider.EHentaiGallery, now())
            }
        }
    }

    private companion object { val JSON = "application/json".toMediaType() }
}

internal object NativeMetadataWireParser {
    fun parseNhentai(body: String, gallery: NHentaiMetadataProvider.NHentaiGallery, observedAt: Long): NativeGalleryMetadata {
        val root = ApiClient.json.parseToJsonElement(body) as? JsonObject ?: error("Invalid nHentai response")
        val titles = root["title"] as? JsonObject
        val title = titles?.string("english") ?: titles?.string("pretty") ?: titles?.string("japanese")
        val tags = (root["tags"] as? JsonArray).orEmpty().mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val namespace = obj.string("type") ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            CanonicalTag.parse("$namespace:$name", TagSource.NHENTAI, 0.98f)
        }.toSet()
        return NativeGalleryMetadata("nhentai", gallery.id, gallery.canonicalUrl, title, root.string("description"), tags, observedAt)
    }

    fun parseEhentai(body: String, gallery: EHentaiMetadataProvider.EHentaiGallery, observedAt: Long): NativeGalleryMetadata {
        val root = ApiClient.json.parseToJsonElement(body) as? JsonObject ?: error("Invalid E-Hentai response")
        val row = (root["gmetadata"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw NativeMetadataFetchException("E-Hentai response has no gallery metadata")
        val title = row.string("title") ?: row.string("title_jpn")
        val tags = (row["tags"] as? JsonArray).orEmpty().mapNotNull { value ->
            (value as? JsonPrimitive)?.contentOrNull?.let { CanonicalTag.parse(it, TagSource.EHENTAI, 0.98f) }
        }.toSet()
        return NativeGalleryMetadata("ehentai", gallery.gid, gallery.canonicalUrl, title, null, tags, observedAt)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}
