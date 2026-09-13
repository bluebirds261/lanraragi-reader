package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单行本（Tankoubon）在库页的建模与路由判据。
 *
 * 服务端 `/api/search` 与 `/api/search/random` 默认 `groupby_tanks=true`，会把属于单行本的档案
 * **替换成** `TANK_xxxxxxxxxx` 条目（`Model/Search.pm:132-135`），与普通 40 位 arcid 混在同一份结果里。
 * 这些 TANK_ id 送到任何「只要 40 位 arcid」的端点都会失败，因此必须：
 * 1. 在网关层保持身份区分；2. 在批量操作前剔除；3. 导航时走单行本阅读器。
 *
 * 注意：当前服务器单行本数量为 0，所以这些判据只能靠单测钉住，无法真机验证。
 */
class LibraryTankEntryTest {

    private val hex = "c68e858c9afe2999b146d37ee022549645137472"
    private val tankId = "TANK_1700000000"

    @Test
    fun tankArcidMapsToTankoubonIdentity() {
        val identity = remoteLibraryIdentity(tankId, serverScopeValue = "https://example.test")
        assertTrue("TANK_ 前缀必须是 Tankoubon 身份", identity is ArchiveIdentity.Tankoubon)
        assertEquals(tankId, (identity as ArchiveIdentity.Tankoubon).tankId)
    }

    @Test
    fun ordinaryArcidStaysRemoteAndKeepsServerScope() {
        val identity = remoteLibraryIdentity(hex, serverScopeValue = "https://example.test")
        assertTrue(identity is ArchiveIdentity.Remote)
        val remote = identity as ArchiveIdentity.Remote
        assertEquals(hex, remote.arcid)
        assertEquals("https://example.test", remote.serverScope)
    }

    @Test
    fun tankDetectionIsCaseInsensitiveAndRejectsLookalikes() {
        assertTrue(isTankArchiveId(tankId))
        assertTrue("前缀大小写不敏感", isTankArchiveId("tank_1700000000"))
        assertFalse("空 id 不是单行本", isTankArchiveId(""))
        assertFalse("本地档案不是单行本", isTankArchiveId("local_abc"))
        assertFalse("普通 aricd 不是单行本", isTankArchiveId(hex))
        // 相似但不是：必须整段前缀匹配，而不是包含。
        assertFalse(isTankArchiveId("XTANK_1700000000"))
    }

    @Test
    fun batchTargetsDropLocalAndTankEntriesAndCountOnlyTanks() {
        val targets = archiveActionTargets(listOf(hex, tankId, "local_deadbeef", "TANK_1700000001"))
        assertEquals("只保留 40 位 arcid", listOf(hex), targets.ids)
        assertEquals("本地档案不计入单行本跳过数", 2, targets.skippedTankCount)
    }

    @Test
    fun batchTargetsKeepOrdinaryEntriesUntouched() {
        val targets = archiveActionTargets(listOf(hex, "a".repeat(40)))
        assertEquals(2, targets.ids.size)
        assertEquals(0, targets.skippedTankCount)
    }

    @Test
    fun archiveCountDecodesFromTankJsonAndDefaultsToZero() {
        val json = Json { ignoreUnknownKeys = true }
        val tank = json.decodeFromString<Archive>(
            """{"arcid":"$tankId","title":"合集","pagecount":300,"archive_count":7}""",
        )
        assertEquals(7, tank.archive_count)
        assertEquals(300, tank.pagecount)

        // 普通档案 JSON 没有该字段 → 默认 0，卡片不会渲染「单行本 · N 卷」角标。
        val plain = json.decodeFromString<Archive>("""{"arcid":"$hex","title":"单本","pagecount":41}""")
        assertEquals(0, plain.archive_count)
    }
}
