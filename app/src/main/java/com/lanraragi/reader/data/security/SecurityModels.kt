package com.lanraragi.reader.data.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** User-visible security switches. Secrets are intentionally not part of this model. */
@Serializable
data class SecuritySettings(
    val biometricEnabled: Boolean = false,
    val maskRecentTasks: Boolean = false,
    val screenshotProtectionEnabled: Boolean = false,
)

enum class ConfigFieldSensitivity { NON_SENSITIVE, SECRET }

/** Conservative key classifier used at the export boundary. */
object SensitiveFieldClassifier {
    private val secretNames = setOf(
        "apiKey", "api_key", "apikey", "cookie", "cookies", "authorization",
        "password", "passwd", "secret", "token", "accessToken", "refreshToken",
        "session", "sessionId", "credential", "credentials",
    )

    fun classify(key: String): ConfigFieldSensitivity {
        val normalized = key.trim().replace("-", "_").lowercase()
        return if (normalized in secretNames || secretNames.any { normalized.contains(it.lowercase()) }) {
            ConfigFieldSensitivity.SECRET
        } else {
            ConfigFieldSensitivity.NON_SENSITIVE
        }
    }

    fun isSecret(key: String): Boolean = classify(key) == ConfigFieldSensitivity.SECRET
}

data class FilteredConfig(
    val value: JsonObject,
    val removedPaths: Set<String>,
)

/** Recursively removes secret-like fields, including secrets nested in arrays. */
fun filterSensitiveFields(config: JsonObject): FilteredConfig {
    val removed = linkedSetOf<String>()

    fun visit(element: JsonElement, path: String): JsonElement? = when (element) {
        is JsonObject -> buildJsonObject {
            element.entries.sortedBy { it.key }.forEach { (key, value) ->
                val childPath = if (path.isEmpty()) key else "$path.$key"
                if (SensitiveFieldClassifier.isSecret(key)) {
                    removed += childPath
                } else {
                    visit(value, childPath)?.let { put(key, it) }
                }
            }
        }
        is JsonArray -> buildJsonArray {
            element.forEachIndexed { index, value ->
                visit(value, "$path[$index]")?.let(::add)
            }
        }
        else -> element
    }

    return FilteredConfig((visit(config, "") as? JsonObject) ?: buildJsonObject { }, removed)
}

enum class ConfigTransferError {
    MALFORMED_JSON,
    INVALID_ENVELOPE,
    UNSUPPORTED_VERSION,
    INVALID_DATA,
    SECRET_FIELD,
}

class ConfigTransferException(
    val reason: ConfigTransferError,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

data class ImportedConfig(
    val config: JsonObject,
    val sourceVersion: Int,
    val migrated: Boolean,
)

/** Versioned, non-sensitive settings transfer format. */
object ConfigTransferCodec {
    const val FORMAT = "lanraragi-reader-config"
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun export(config: JsonObject): String {
        val filtered = filterSensitiveFields(config).value
        return buildJsonObject {
            put("format", FORMAT)
            put("version", CURRENT_VERSION)
            put("config", filtered)
        }.let { json.encodeToString(it) }
    }

    fun import(payload: String): ImportedConfig {
        val root = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (error: Exception) {
            throw ConfigTransferException(ConfigTransferError.MALFORMED_JSON, "Malformed configuration JSON", error)
        }
        val format = (root["format"] as? JsonPrimitive)?.content
        val versionPrimitive = root["version"] as? JsonPrimitive
        val version = versionPrimitive?.takeUnless { it.isString }?.intOrNull
        val config = root["config"] as? JsonObject
        if (format != FORMAT || version == null || config == null) {
            throw ConfigTransferException(ConfigTransferError.INVALID_ENVELOPE, "Invalid configuration envelope")
        }
        if (version > CURRENT_VERSION || version < 0) {
            throw ConfigTransferException(ConfigTransferError.UNSUPPORTED_VERSION, "Unsupported configuration version: $version")
        }
        val secretPaths = findSecretPaths(config)
        if (secretPaths.isNotEmpty()) {
            throw ConfigTransferException(
                ConfigTransferError.SECRET_FIELD,
                "Configuration contains secret fields: ${secretPaths.joinToString()}",
            )
        }
        val migrated = migrate(config, version)
        validate(migrated)
        return ImportedConfig(migrated, version, version != CURRENT_VERSION)
    }

    private fun migrate(config: JsonObject, version: Int): JsonObject {
        if (version == CURRENT_VERSION) return config
        // v0 used blurInRecents; retain compatibility without accepting credentials.
        return if (version == 0 && config["blurInRecents"] != null && config["maskRecentTasks"] == null) {
            buildJsonObject {
                config.forEach { (key, value) -> if (key != "blurInRecents") put(key, value) }
                put("maskRecentTasks", config["blurInRecents"]!!)
            }
        } else config
    }

    private fun validate(config: JsonObject) {
        config.entries.forEach { (key, value) ->
            if (key.isBlank() || value is JsonObject && value.size > 512) {
                throw ConfigTransferException(ConfigTransferError.INVALID_DATA, "Invalid configuration field: $key")
            }
        }
    }

    private fun findSecretPaths(config: JsonObject): Set<String> {
        val result = linkedSetOf<String>()
        fun scan(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    val child = if (path.isEmpty()) key else "$path.$key"
                    if (SensitiveFieldClassifier.isSecret(key)) result += child else scan(value, child)
                }
                is JsonArray -> element.forEachIndexed { index, value -> scan(value, "$path[$index]") }
                else -> Unit
            }
        }
        scan(config, "")
        return result
    }
}

/** Convenience names for adapters that do not need to know the codec implementation. */
object NonSensitiveConfigJson {
    fun export(config: JsonObject): String = ConfigTransferCodec.export(config)
    fun import(payload: String): ImportedConfig = ConfigTransferCodec.import(payload)
}
