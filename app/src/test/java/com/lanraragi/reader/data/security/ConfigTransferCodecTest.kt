package com.lanraragi.reader.data.security

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigTransferCodecTest {

    @Test
    fun exportRecursivelyRemovesSecretsAndIsStable() {
        val config = buildJsonObject {
            put("theme", "dark")
            put("apiKey", "must-not-leak")
            putJsonArray("profiles") {
                add(buildJsonObject {
                    put("url", "https://example.test")
                    put("cookie", "session=abc")
                })
            }
        }
        val encoded = ConfigTransferCodec.export(config)
        assertTrue(encoded.contains("lanraragi-reader-config"))
        assertTrue(encoded.contains("dark"))
        assertFalse(encoded.contains("must-not-leak"))
        assertFalse(encoded.contains("session=abc"))
        assertFalse(encoded.contains("apiKey"))
        assertFalse(encoded.contains("cookie"))
    }

    @Test
    fun importRejectsSecretFieldsInsteadOfSilentlyPersistingThem() {
        val payload = """
            {"format":"lanraragi-reader-config","version":1,
             "config":{"theme":"dark","nested":{"authorization":"Bearer x"}}}
        """.trimIndent()
        val error = expectError(payload)
        assertEquals(ConfigTransferError.SECRET_FIELD, error.reason)
    }

    @Test
    fun importMigratesVersionZeroAndReportsMigration() {
        val payload = """
            {"format":"lanraragi-reader-config","version":0,
             "config":{"blurInRecents":true,"theme":"system"}}
        """.trimIndent()
        val imported = ConfigTransferCodec.import(payload)
        assertTrue(imported.migrated)
        assertEquals(true, imported.config["maskRecentTasks"]?.toString()?.toBoolean())
        assertFalse(imported.config.containsKey("blurInRecents"))
    }

    @Test
    fun malformedAndUnsupportedEnvelopesHaveDistinctErrors() {
        assertEquals(ConfigTransferError.MALFORMED_JSON, expectError("not-json").reason)
        assertEquals(
            ConfigTransferError.UNSUPPORTED_VERSION,
            expectError("{\"format\":\"lanraragi-reader-config\",\"version\":99,\"config\":{}}" ).reason,
        )
        assertEquals(
            ConfigTransferError.INVALID_ENVELOPE,
            expectError("{\"format\":\"wrong\",\"version\":1,\"config\":{}}" ).reason,
        )
    }

    @Test
    fun coordinatorReplacesOnlyAfterSuccessfulValidation() = runTest {
        var replaced: Map<String, String>? = null
        val coordinator = ConfigImportCoordinator(AtomicConfigReplacement { config ->
            replaced = config.mapValues { it.value.toString() }
        })
        coordinator.import("""{"format":"lanraragi-reader-config","version":1,"config":{"theme":"dark"}}""")
        assertEquals("\"dark\"", replaced!!["theme"])
    }

    private fun expectError(payload: String): ConfigTransferException = try {
        ConfigTransferCodec.import(payload)
        fail("expected ConfigTransferException")
        error("unreachable")
    } catch (error: ConfigTransferException) {
        error
    }
}
