package com.lanraragi.reader.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PluginResultParserTest {
    @Test
    fun parsesSynchronousPluginEnvelope() {
        val result = PluginResultParser.parseSyncResponse(
            javaClass.getResource("/plugin/ehplugin-sync.json")!!.readText(),
        )
        assertEquals("artist:tester, source:e-hentai.org/g/1/token", result.newTags)
        assertEquals("Scraped title", result.title)
        assertNotNull(result.raw["provider_extra"])
    }

    @Test
    fun parsesAsynchronousJobDetailResult() {
        val result = PluginResultParser.parseJobDetail(
            javaClass.getResource("/plugin/nhplugin-job-detail.json")!!.readText(),
        )
        assertEquals("language:english, source:nhentai.net/g/123", result.newTags)
        assertEquals("Pretty title", result.title)
    }
}
