package com.lanraragi.reader.data

import com.lanraragi.reader.data.history.core.InMemoryProgressTaskStore
import com.lanraragi.reader.data.history.core.ProgressTransport
import com.lanraragi.reader.data.model.ServerInfo
import com.lanraragi.reader.data.reader.OutboxProgressWriter
import com.lanraragi.reader.domain.model.ArchiveIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A3 能力门控：`/info` 的 `server_tracks_progress` 决定是否回传阅读进度。
 *
 * `server_tracks_progress = false` 表示服务端开了「本地进度」模式，
 * `PUT /api/archives/{id}/progress/{page}` 会被服务端拒绝，发了只会堆积永远失败的任务。
 */
class ServerCapabilitiesTest {

    @Test
    fun progressIsOptimisticUntilServerInfoArrives() {
        // /info 未就绪时按「支持」处理：宁可多发一次请求，也不要因为能力未知而把进度压在本地。
        assertTrue(ServerCapabilities(null).supportsProgress)
    }

    @Test
    fun progressFollowsTheRealServerAcceptanceRule() {
        // 服务端拒绝写入的条件是 `enable_localprogress && !enable_authprogress`
        // （Archive.pm:450 / Tankoubon.pm:245），换算成 /info 的两个布尔字段后：
        // 接受 ⟺ server_tracks_progress || authenticated_progress。
        assertTrue(
            "两者都开：接受",
            ServerCapabilities(
                ServerInfo(server_tracks_progress = true, authenticated_progress = true),
            ).supportsProgress,
        )
        assertTrue(
            "只开服务端进度：接受",
            ServerCapabilities(
                ServerInfo(server_tracks_progress = true, authenticated_progress = false),
            ).supportsProgress,
        )
        assertTrue(
            "开了本地进度但要求量鉴权：服务端仍接受（本地进度落在客户端）",
            ServerCapabilities(
                ServerInfo(server_tracks_progress = false, authenticated_progress = true),
            ).supportsProgress,
        )
        assertFalse(
            "本地进度 + 无鉴权：服务端唯一会拒绝的组合",
            ServerCapabilities(
                ServerInfo(server_tracks_progress = false, authenticated_progress = false),
            ).supportsProgress,
        )
    }

    @Test
    fun authenticatedProgressFollowsServerFlag() {
        assertTrue(
            ServerCapabilities(ServerInfo(authenticated_progress = true)).requiresAuthenticatedProgress,
        )
        assertFalse(
            ServerCapabilities(ServerInfo(authenticated_progress = false)).requiresAuthenticatedProgress,
        )
        assertFalse(ServerCapabilities(null).requiresAuthenticatedProgress)
    }

    @Test
    fun versionGatesResolveFromServerVersion() {
        val old = ServerCapabilities(ServerInfo(version = "0.8.9"))
        assertFalse(old.supportsTankoubons)
        assertFalse(old.supportsToc)
        assertFalse(old.supportsStamps)

        val current = ServerCapabilities(ServerInfo(version = "0.9.81"))
        assertTrue(current.supportsTankoubons)
        assertTrue(current.supportsToc)
        assertTrue(current.supportsStamps)

        // 版本缺失时一律不启用版本门控（info 为空 → atLeast 返回 false）。
        assertFalse(ServerCapabilities(null).supportsToc)
    }

    @Test
    fun writerDoesNotEnqueueOrSendWhenServerDoesNotTrackProgress() = runTest {
        val store = InMemoryProgressTaskStore()
        var writes = 0
        val writer = OutboxProgressWriter(
            store = store,
            transport = ProgressTransport { _, _, _ -> writes++ },
            progressSupported = { false },
        )
        val identity = ArchiveIdentity.Remote("a".repeat(40))

        assertNull("不支持时必须不入队", writer.record(identity, page = 3, pageCount = 41))
        assertTrue(writer.pending().isEmpty())

        val report = writer.flush()
        assertEquals("不得发出任何回传", 0, writes)
        assertEquals(0, report.attempted)
        assertEquals(0, report.retained)
    }

    @Test
    fun writerEnqueuesAndSendsWhenProgressIsSupported() = runTest {
        val store = InMemoryProgressTaskStore()
        var writes = 0
        val writer = OutboxProgressWriter(
            store = store,
            transport = ProgressTransport { _, _, _ -> writes++ },
            progressSupported = { true },
        )
        val identity = ArchiveIdentity.Remote("b".repeat(40))

        writer.record(identity, page = 3, pageCount = 41)
        assertEquals(1, writer.pending().size)

        val report = writer.flush()
        assertEquals(1, writes)
        assertEquals(1, report.attempted)
        assertEquals(1, report.succeeded)
        assertTrue(writer.pending().isEmpty())
    }
}
