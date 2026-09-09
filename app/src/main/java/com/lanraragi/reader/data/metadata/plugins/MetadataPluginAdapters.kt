package com.lanraragi.reader.data.metadata.plugins

import androidx.room.withTransaction
import com.lanraragi.reader.data.ApiException
import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.db.MetadataScrapeJobEntity
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.metadata.MetadataPatchCodec
import com.lanraragi.reader.data.metadata.PluginMetadataResult
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

/** Bridges the existing repository/API implementation to the plugin coordinator contract. */
class LanraragiServerMetadataPluginGateway(
    private val repository: LanraragiRepository,
) : ServerMetadataPluginGateway {
    override suspend fun discoverMetadataPlugins(): List<ServerMetadataPlugin> = try {
        repository.listPlugins("metadata").map {
            ServerMetadataPlugin(namespace = it.namespace, name = it.name.ifBlank { it.namespace }, version = it.version)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiException) {
        throw error.toPluginException()
    }

    override suspend fun usePlugin(arcid: String, pluginNamespace: String, argument: String?): String = call {
        repository.usePlugin(arcid, pluginNamespace, argument)
    }

    override suspend fun queuePlugin(arcid: String, pluginNamespace: String, argument: String?): String = call {
        repository.usePluginAsync(arcid, pluginNamespace, argument)
    }

    override suspend fun getMinionStatus(jobId: String): ServerPluginJobStatus = call {
        repository.getMinionJob(jobId).let {
            ServerPluginJobStatus(it.id.ifBlank { jobId }, it.state, it.note, parseRetryAfterMillis(it.note))
        }
    }

    override suspend fun getMinionDetail(jobId: String): String = call {
        repository.getMinionJobDetail(jobId)
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiException) {
        throw error.toPluginException()
    }

    private fun ApiException.toPluginException() = MetadataPluginRemoteException(
        statusCode = statusCode,
        message = message ?: "Metadata plugin request failed",
        cause = this,
    )

    private fun parseRetryAfterMillis(note: String?): Long? {
        val seconds = Regex("(?i)retry[- ]after\\s*[:=]?\\s*(\\d+)").find(note.orEmpty())
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
        return seconds?.times(1000L)
    }
}

/** Room-backed durable job records; candidate application remains a separate user action. */
class RoomMetadataPluginJobStore(
    private val database: ReaderDatabase,
) : MetadataPluginJobStore {
    override suspend fun write(record: MetadataPluginJobRecord) {
        val candidate = record.candidate?.let(::CandidateWire)
        database.metadataDao().upsertJob(
            MetadataScrapeJobEntity(
                jobId = record.jobId ?: record.key,
                sourceKey = record.target.sourceKey,
                archiveId = record.target.arcid,
                serverScope = record.target.serverScope,
                providerId = record.pluginNamespace,
                input = record.argument,
                state = record.phase.name,
                candidatesJson = candidate?.let(ApiClient.json::encodeToString),
                patchJson = record.candidate?.patch?.let(MetadataPatchCodec::encode),
                error = record.error,
                updatedAt = record.updatedAt,
                nextRunAt = record.nextRunAt,
                createdAt = record.createdAt,
            ),
        )
    }

    override suspend fun read(key: String): MetadataPluginJobRecord? {
        val entity = database.metadataDao().findJobByKey(key) ?: return null
        return entity.toRecord(key)
    }

    override suspend fun loadRecoverable(): List<MetadataPluginJobRecord> = database.metadataDao()
        .observeJobs()
        .first()
        .filter { entity ->
            entity.state in setOf(
                MetadataPluginJobPhase.QUEUED.name,
                MetadataPluginJobPhase.RUNNING.name,
                MetadataPluginJobPhase.RETRYABLE.name,
            )
        }
        .map { entity -> entity.toRecord(recordKey(entity)) }

    private fun MetadataScrapeJobEntity.toRecord(key: String): MetadataPluginJobRecord {
        val entity = this
        val candidate = candidatesJson?.let { encoded ->
            runCatching { ApiClient.json.decodeFromString<CandidateWire>(encoded).toDomain(entity) }.getOrNull()
        }
        return MetadataPluginJobRecord(
            key = key,
            target = ArchiveIdentity.Remote(archiveId ?: arcidOrSourceKey(), serverScope),
            pluginNamespace = providerId,
            argument = input,
            jobId = jobId.takeUnless { it == key },
            phase = runCatching { MetadataPluginJobPhase.valueOf(state) }.getOrDefault(MetadataPluginJobPhase.FAILED),
            candidate = candidate,
            error = error,
            updatedAt = updatedAt,
            createdAt = createdAt,
            nextRunAt = nextRunAt,
        )
    }

    private fun recordKey(entity: MetadataScrapeJobEntity): String =
        ServerMetadataPluginCoordinator.recordKey(
            ArchiveIdentity.Remote(entity.archiveId ?: entity.arcidOrSourceKey(), entity.serverScope),
            entity.providerId,
        )

    private fun MetadataScrapeJobEntity.arcidOrSourceKey(): String =
        archiveId?.takeIf { it.isNotBlank() }
            ?: input?.substringAfter("arcid:")?.takeIf { it.isNotBlank() }
            ?: sourceKey.substringAfterLast(':').ifBlank { sourceKey }

    @Serializable
    private data class CandidateWire(
        val arcid: String,
        val serverScope: String? = null,
        val pluginNamespace: String,
        val pluginName: String,
        val pluginVersion: String? = null,
        val jobId: String? = null,
        val sourceId: String? = null,
        val sourceUrl: String? = null,
        val confidence: Float? = null,
        val newTags: String? = null,
        val title: String? = null,
        val summary: String? = null,
        val error: String? = null,
        val raw: String,
    ) {
        constructor(candidate: MetadataPluginCandidate) : this(
            arcid = candidate.target.arcid,
            serverScope = candidate.target.serverScope,
            pluginNamespace = candidate.plugin.namespace,
            pluginName = candidate.plugin.name,
            pluginVersion = candidate.plugin.version,
            jobId = candidate.jobId,
            sourceId = candidate.patch.title?.provenance?.sourceId
                ?: candidate.patch.summary?.provenance?.sourceId
                ?: candidate.patch.sourceUrl?.provenance?.sourceId,
            sourceUrl = candidate.patch.sourceUrl?.value,
            confidence = candidate.patch.sourceUrl?.provenance?.confidence,
            newTags = candidate.raw.newTags,
            title = candidate.raw.title,
            summary = candidate.raw.summary,
            error = candidate.raw.error,
            raw = candidate.raw.raw.toString(),
        )

        fun toDomain(entity: MetadataScrapeJobEntity): MetadataPluginCandidate? {
            val patchJson = entity.patchJson ?: return null
            val patch = runCatching { MetadataPatchCodec.decode(patchJson) }.getOrNull() ?: return null
            val rawObject = runCatching { ApiClient.json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                ?: JsonObject(emptyMap())
            return MetadataPluginCandidate(
                target = ArchiveIdentity.Remote(arcid, serverScope),
                plugin = ServerMetadataPlugin(pluginNamespace, pluginName, pluginVersion),
                jobId = jobId,
                patch = patch,
                raw = PluginMetadataResult(newTags, title, summary, error, rawObject),
            )
        }
    }
}
