package com.lanraragi.reader.data.tags.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EhTagTranslationParserTest {
    private fun request(version: String = "2026.09.09") = EhTagTranslationImportRequest(
        version = version,
        source = TagKnowledgeSourceMetadata(
            sourceUrl = "https://github.com/EhTagTranslation/Database",
            retrievedAt = 1L,
            schemaVersion = "db.text.v1",
            license = "CC BY-NC-SA 4.0",
            attribution = "EhTagTranslation contributors",
        ),
    )

    @Test fun jsonImportKeepsCanonicalEnglishKeyAndNormalizesNamespace() {
        val imported = EhTagTranslationParser.parseJson(
            """[{"namespace":"artists","data":{"Alice":{"name":"爱丽丝","intro":"Painter"}}}]""",
            request(),
        ).snapshot

        assertEquals("artist", imported.dictionary.single().namespace)
        assertEquals("alice", imported.dictionary.single().tagKey)
        assertEquals("爱丽丝", imported.dictionary.single().translatedName)
        assertEquals("Painter", imported.dictionary.single().intro)
        assertEquals(EhTagTranslationParser.sha256("""[{"namespace":"artists","data":{"Alice":{"name":"爱丽丝","intro":"Painter"}}}]"""), imported.source?.checksumSha256)
    }

    @Test fun htmlImportAcceptsEmbeddedAuthoritativeJson() {
        val imported = EhTagTranslationParser.parseHtml(
            """<script id="eh-tag-translation-data" type="application/json">[{"namespace":"female","data":{"glasses":{"name":"眼镜"}}}]</script>""",
            request(),
        ).snapshot

        assertEquals("female", imported.dictionary.single().namespace)
        assertEquals("眼镜", imported.dictionary.single().translatedName)
    }

    @Test fun invalidChecksumRejectsImportBeforeItCanBeStaged() {
        val invalidRequest = request().copy(source = request().source.copy(checksumSha256 = "0".repeat(64)))
        val failure = runCatching {
            EhTagTranslationParser.parseJson("""[{"namespace":"artist","data":{"alice":{"name":"爱丽丝"}}}]""", invalidRequest)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun failedOrOlderStageRetainsActiveSnapshot() = runBlocking {
        val active = EhTagTranslationParser.parseJson(
            """[{"namespace":"artist","data":{"alice":{"name":"爱丽丝"}}}]""",
            request("2"),
        ).snapshot
        val store = InMemoryTagKnowledgeStore(active)
        val rejected = TagKnowledgeUpdater(store).update(active.copy(version = "1", dictionary = active.dictionary.map { it.copy(dataVersion = "1") }))

        assertTrue(rejected is TagKnowledgeUpdate.Rejected)
        assertEquals("2", store.current()?.version)
    }
}
