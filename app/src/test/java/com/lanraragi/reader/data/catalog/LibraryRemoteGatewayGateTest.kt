package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.data.LanraragiRepository
import com.lanraragi.reader.data.api.LanraragiApi
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「仅本地书架」降级：未配置服务器时图库不得打网络。
 *
 * 背景：`AppRoot` 允许用户不配服务器就进入主界面（向导第 4 步「跳过，先使用本地书架」）。
 * 若图库仍然去请求远端，界面会被「尚未配置 LANraragi 服务器地址」的错误态占满，
 * 本地档案也看不到。`LanraragiLibraryRemoteGateway.serverConfigured` 就是这道闸门，
 * 本测试钉住「不配置 → 一个请求都不发，且返回空列表而不是抛错」。
 */
class LibraryRemoteGatewayGateTest {

    @Test
    fun unconfiguredServerReturnsNoRowsAndIssuesNoRequest() = runTest {
        val calls = AtomicInteger(0)
        val gateway = LanraragiLibraryRemoteGateway(
            repository = LanraragiRepository(failingApi(calls)),
            serverConfigured = { false },
        )

        val rows = gateway.fetch(LibraryQuery(sort = LibrarySort.DATE_ADDED))

        assertTrue("未配置服务器时不应有任何远端行", rows.isEmpty())
        assertEquals("未配置服务器时不得发出任何 API 调用", 0, calls.get())
    }

    @Test
    fun configuredServerDoesAttemptTheRequest() = runTest {
        val calls = AtomicInteger(0)
        val gateway = LanraragiLibraryRemoteGateway(
            repository = LanraragiRepository(failingApi(calls)),
            serverConfigured = { true },
        )

        // 配置了服务器就该真的去请求；这里刻意的假 API 会抛错，
        // 只要能观察到「调用发生过」即可（异常类型不作断言，避免与仓储的错误包装耦合）。
        runCatching { gateway.fetch(LibraryQuery()) }
        assertTrue("已配置服务器时应发出 API 调用", calls.get() > 0)
    }

    /**
     * 用一个动态代理实现 [LanraragiApi]：任何方法调用都记一次数并抛错。
     * 这样无需为四十多个端点写桩，也能精确区分「有没有发请求」。
     */
    private fun failingApi(calls: AtomicInteger): LanraragiApi =
        Proxy.newProxyInstance(
            LanraragiApi::class.java.classLoader,
            arrayOf(LanraragiApi::class.java),
        ) { _, _, _ ->
            calls.incrementAndGet()
            throw IllegalStateException("测试用 API：不应被调用")
        } as LanraragiApi
}
