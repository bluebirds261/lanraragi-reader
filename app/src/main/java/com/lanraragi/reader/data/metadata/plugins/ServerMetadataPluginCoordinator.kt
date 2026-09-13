package com.lanraragi.reader.data.metadata.plugins

import com.lanraragi.reader.data.metadata.PluginMetadataResult
import com.lanraragi.reader.data.metadata.PluginResultParser
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource
import com.lanraragi.reader.domain.model.ArchiveIdentity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A metadata plugin advertised by LANraragi's `/api/plugins/metadata` endpoint. */
data class ServerMetadataPlugin(
    val namespace: String,
    val name: String = namespace,
    val version: String? = null,
) {
    init { require(namespace.isNotBlank()) { "plugin namespace must not be blank" } }
}

/** The minimum wire contract needed by the plugin coordinator. */
interface ServerMetadataPluginGateway {
    suspend fun discoverMetadataPlugins(): List<ServerMetadataPlugin>
    suspend fun usePlugin(arcid: String, pluginNamespace: String, argument: String? = null): String
    suspend fun queuePlugin(arcid: String, pluginNamespace: String, argument: String? = null): String
    suspend fun getMinionStatus(jobId: String): ServerPluginJobStatus
    suspend fun getMinionDetail(jobId: String): String
}

data class ServerPluginJobStatus(
    val id: String,
    val state: String,
    val note: String? = null,
    val retryAfterMillis: Long? = null,
)

enum class ServerPluginJobState { QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN }

enum class MetadataPluginExecution { SYNCHRONOUS, ASYNCHRONOUS }

data class MetadataPluginRequest(
    val target: ArchiveIdentity,
    val plugin: ServerMetadataPlugin,
    val argument: String? = null,
    val execution: MetadataPluginExecution = MetadataPluginExecution.SYNCHRONOUS,
)

data class MetadataPluginCandidate(
    val target: ArchiveIdentity.Remote,
    val plugin: ServerMetadataPlugin,
    val jobId: String? = null,
    val patch: MetadataPatch,
    val raw: PluginMetadataResult,
)

enum class MetadataPluginJobPhase { QUEUED, RUNNING, READY, FAILED, RETRYABLE, TIMED_OUT }

data class MetadataPluginJobRecord(
    val key: String,
    val target: ArchiveIdentity.Remote,
    val pluginNamespace: String,
    val argument: String? = null,
    val jobId: String? = null,
    val phase: MetadataPluginJobPhase,
    val candidate: MetadataPluginCandidate? = null,
    val error: String? = null,
    val updatedAt: Long,
    val createdAt: Long = updatedAt,
    val nextRunAt: Long? = null,
)

/** Durable boundary: Room integration owns encoding and transaction details. */
interface MetadataPluginJobStore {
    suspend fun write(record: MetadataPluginJobRecord)
    suspend fun read(key: String): MetadataPluginJobRecord?
    suspend fun loadRecoverable(): List<MetadataPluginJobRecord> = emptyList()
}

data class MetadataPluginPollingPolicy(
    val timeoutMillis: Long = 90_000L,
    val intervalMillis: Long = 1_000L,
) {
    init {
        require(timeoutMillis >= 0L)
        require(intervalMillis >= 0L)
    }
}

class MetadataPluginRemoteException(
    val statusCode: Int? = null,
    override val message: String,
    val retryAfterMillis: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val retryable: Boolean get() = statusCode == 429 || statusCode == null
}

class UnsupportedMetadataPluginTargetException(target: ArchiveIdentity) : IllegalArgumentException(
    "Server metadata plugins only support remote archives; got ${target::class.simpleName}",
)

/**
 * Coordinates the LANraragi plugin APIs only. It deliberately never calls the
 * metadata PUT endpoint: callers must preview [MetadataPluginCandidate.patch]
 * and pass it through MetadataRepository's conflict-safe apply flow.
 */
class ServerMetadataPluginCoordinator(
    private val gateway: ServerMetadataPluginGateway,
    private val store: MetadataPluginJobStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val polling: MetadataPluginPollingPolicy = MetadataPluginPollingPolicy(),
    private val wakeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val runMutex = Mutex()
    private val wakeMutex = Mutex()
    private var retryWakeJob: Job? = null
    private var retryWakeGeneration = 0L

    suspend fun discover(): List<ServerMetadataPlugin> = gateway.discoverMetadataPlugins()

    /** Resumes persisted Minion jobs without applying any resulting patch. */
    suspend fun recover(): List<MetadataPluginCandidate> {
        val recovered = mutableListOf<MetadataPluginCandidate>()
        val now = clock()
        store.loadRecoverable()
            .sortedWith(compareBy<MetadataPluginJobRecord> { it.nextRunAt ?: Long.MIN_VALUE }.thenBy { it.createdAt })
            .forEach { record ->
                // Startup recovery must not block the application on a long retry cooldown.
                // The record remains durable and a later coordinator wake-up/manual retry can
                // process it once its cooldown has elapsed.
                if (record.nextRunAt?.let { it > now } == true) return@forEach
                val candidate = run(
                    MetadataPluginRequest(
                        target = record.target,
                        plugin = record.candidate?.plugin
                            ?: ServerMetadataPlugin(record.pluginNamespace),
                        argument = record.argument,
                        execution = MetadataPluginExecution.ASYNCHRONOUS,
                    ),
                )
                if (candidate != null) recovered += candidate
            }
        armRetryWakeup()
        return recovered
    }

    suspend fun run(request: MetadataPluginRequest): MetadataPluginCandidate? {
        val result = runMutex.withLock {
            val target = request.target as? ArchiveIdentity.Remote
                ?: throw UnsupportedMetadataPluginTargetException(request.target)
            when (request.execution) {
                MetadataPluginExecution.SYNCHRONOUS -> runSynchronously(target, request)
                MetadataPluginExecution.ASYNCHRONOUS -> runAsynchronously(target, request)
            }
        }
        armRetryWakeup()
        return result
    }

    /** Exactly one cancellable timer wakes the durable retry queue at its earliest due time. */
    private suspend fun armRetryWakeup() = wakeMutex.withLock {
        val now = clock()
        val dueAt = store.loadRecoverable().asSequence()
            .filter { it.phase == MetadataPluginJobPhase.RETRYABLE }
            .mapNotNull { it.nextRunAt }
            .filter { it > now }
            .minOrNull()
        retryWakeJob?.cancel()
        retryWakeJob = null
        if (dueAt == null) return@withLock
        val generation = ++retryWakeGeneration
        retryWakeJob = wakeScope.launch {
            wait((dueAt - clock()).coerceAtLeast(0L))
            val shouldRecover = wakeMutex.withLock {
                if (generation != retryWakeGeneration) false else {
                    retryWakeJob = null
                    true
                }
            }
            if (shouldRecover) recover()
        }
    }

    private suspend fun runSynchronously(
        target: ArchiveIdentity.Remote,
        request: MetadataPluginRequest,
    ): MetadataPluginCandidate? = try {
        val result = PluginResultParser.parseSyncResponse(
            gateway.usePlugin(target.arcid, request.plugin.namespace, request.argument),
        )
        if (!result.error.isNullOrBlank()) {
            write(target, request, MetadataPluginJobPhase.FAILED, error = result.error)
            null
        } else {
            val candidate = result.toCandidate(target, request.plugin, request.argument, jobId = null, fetchedAt = clock())
            write(target, request, MetadataPluginJobPhase.READY, candidate = candidate)
            candidate
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        persistFailure(target, request, error)
        null
    }

    private suspend fun runAsynchronously(
        target: ArchiveIdentity.Remote,
        request: MetadataPluginRequest,
    ): MetadataPluginCandidate? {
        val previous = store.read(recordKey(target, request.plugin.namespace))
        if (previous?.phase == MetadataPluginJobPhase.READY && previous.candidate != null) return previous.candidate
        if (previous?.nextRunAt?.let { it > clock() } == true) return null
        var queuedJobId: String? = previous?.jobId
        return try {
            val jobId = previous?.jobId ?: parseQueueJobId(
                gateway.queuePlugin(target.arcid, request.plugin.namespace, request.argument),
            )
            queuedJobId = jobId
            write(target, request, MetadataPluginJobPhase.QUEUED, jobId = jobId, previous = previous)
            poll(target, request, jobId)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                write(
                    target,
                    request,
                    MetadataPluginJobPhase.QUEUED,
                    jobId = queuedJobId,
                    previous = previous,
                    error = "Cancelled; resumable job retained",
                )
            }
            throw error
        } catch (error: Exception) {
            persistFailure(target, request, error, queuedJobId)
            null
        }
    }

    private suspend fun poll(
        target: ArchiveIdentity.Remote,
        request: MetadataPluginRequest,
        jobId: String,
    ): MetadataPluginCandidate? {
        val startedAt = clock()
        while (true) {
            val status = gateway.getMinionStatus(jobId)
            when (status.state.toJobState()) {
                ServerPluginJobState.SUCCEEDED -> {
                    val result = parsePluginJobDetail(gateway.getMinionDetail(jobId))
                    if (!result.error.isNullOrBlank()) {
                        write(target, request, MetadataPluginJobPhase.FAILED, jobId, error = result.error)
                        return null
                    }
                    val candidate = result.toCandidate(target, request.plugin, request.argument, jobId, clock())
                    write(target, request, MetadataPluginJobPhase.READY, jobId, candidate)
                    return candidate
                }
                ServerPluginJobState.FAILED -> {
                    val retryAt = status.retryAfterMillis?.let { clock() + it.coerceAtLeast(0L) }
                    write(
                        target,
                        request,
                        if (retryAt == null) MetadataPluginJobPhase.FAILED else MetadataPluginJobPhase.RETRYABLE,
                        jobId,
                        error = status.note ?: "Plugin job failed",
                        nextRunAt = retryAt,
                    )
                    return null
                }
                else -> {
                    if (clock() - startedAt >= polling.timeoutMillis) {
                        write(target, request, MetadataPluginJobPhase.TIMED_OUT, jobId, error = "Plugin job timed out")
                        return null
                    }
                    write(target, request, MetadataPluginJobPhase.RUNNING, jobId)
                    wait(polling.intervalMillis)
                }
            }
        }
    }

    private suspend fun persistFailure(
        target: ArchiveIdentity.Remote,
        request: MetadataPluginRequest,
        error: Exception,
        jobId: String? = null,
    ) {
        val retryable = error is IOException || (error as? MetadataPluginRemoteException)?.retryable == true
        val retryAfter = (error as? MetadataPluginRemoteException)?.retryAfterMillis
        write(
            target,
            request,
            if (retryable) MetadataPluginJobPhase.RETRYABLE else MetadataPluginJobPhase.FAILED,
            jobId = jobId,
            error = error.message ?: error::class.simpleName,
            nextRunAt = retryAfter?.let { clock() + it.coerceAtLeast(0L) },
        )
    }

    private suspend fun write(
        target: ArchiveIdentity.Remote,
        request: MetadataPluginRequest,
        phase: MetadataPluginJobPhase,
        jobId: String? = null,
        candidate: MetadataPluginCandidate? = null,
        error: String? = null,
        previous: MetadataPluginJobRecord? = null,
        nextRunAt: Long? = null,
    ) {
        val key = recordKey(target, request.plugin.namespace)
        val prior = previous ?: store.read(key)
        val now = clock()
        store.write(
            MetadataPluginJobRecord(
                key = key,
                target = target,
                pluginNamespace = request.plugin.namespace,
                argument = request.argument,
                jobId = jobId,
                phase = phase,
                candidate = candidate,
                error = error,
                updatedAt = now,
                createdAt = prior?.createdAt ?: now,
                nextRunAt = nextRunAt,
            ),
        )
    }

    companion object {
        fun recordKey(target: ArchiveIdentity.Remote, pluginNamespace: String): String =
            "${target.sourceKey}:plugin:${pluginNamespace.trim().lowercase()}"
    }
}

private fun String.toJobState(): ServerPluginJobState = when (trim().lowercase()) {
    "finished", "complete", "completed", "success", "succeeded", "done" -> ServerPluginJobState.SUCCEEDED
    "failed", "failure", "error", "cancelled", "canceled" -> ServerPluginJobState.FAILED
    "queued", "pending", "waiting" -> ServerPluginJobState.QUEUED
    "active", "running", "processing", "started" -> ServerPluginJobState.RUNNING
    else -> ServerPluginJobState.UNKNOWN
}

/** LANraragi 0.9.81 details may wrap the plugin payload as `result.data`. */
private fun parsePluginJobDetail(body: String): PluginMetadataResult {
    val root = runCatching { ApiClient.json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: return PluginResultParser.parseJobDetail(body)
    val result = root["result"] as? JsonObject ?: return PluginResultParser.parseJobDetail(body)
    val nestedData = result["data"] as? JsonObject ?: return PluginResultParser.parseJobDetail(body)
    // The shared parser preserves all plugin-owned unknown fields in raw.
    val error = result["error"] ?: root["error"]
    return PluginResultParser.parseSyncResponse("{\"data\":$nestedData,\"error\":${error ?: "null"}}")
}

private fun parseQueueJobId(body: String): String {
    val trimmed = body.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        val root = runCatching { ApiClient.json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
        val value = root?.get("job") ?: root?.get("jobid") ?: root?.get("job_id") ?: root?.get("id") ?: root?.get("data")
        val id = value?.toString()?.trim('"', ' ', '\n', '\r', '\t')
        if (!id.isNullOrBlank() && id != "null") return id
    }
    return trimmed.also { require(it.isNotEmpty()) { "plugin queue response did not include a job id" } }
}

private fun PluginMetadataResult.toCandidate(
    target: ArchiveIdentity.Remote,
    plugin: ServerMetadataPlugin,
    argument: String?,
    jobId: String?,
    fetchedAt: Long,
): MetadataPluginCandidate? {
    val tags = newTags.orEmpty().split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { CanonicalTag.parse(it, TagSource.LANRARAGI) }
        .toSet()
    val sourceUrl = tags.firstOrNull { it.identity.namespace == "source" }
        ?.raw?.substringAfter(':')?.trim()?.takeIf(String::isNotBlank)
    val provenance = MetadataProvenance(
        providerId = plugin.namespace,
        sourceId = argument?.takeIf(String::isNotBlank),
        sourceUrl = sourceUrl,
        fetchedAt = fetchedAt,
    )
    val patch = MetadataPatch(
        title = title?.takeIf(String::isNotBlank)?.let { MetadataField(it, provenance) },
        summary = summary?.let { MetadataField(it, provenance) },
        sourceUrl = sourceUrl?.let { MetadataField(it, provenance) },
        addTags = tags,
        tagProvenance = tags.associate { it.full to provenance },
    )
    if (patch == MetadataPatch()) return null
    return MetadataPluginCandidate(target, plugin, jobId, patch, this)
}
