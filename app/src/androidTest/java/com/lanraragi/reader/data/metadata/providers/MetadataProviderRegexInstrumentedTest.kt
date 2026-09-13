package com.lanraragi.reader.data.metadata.providers

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 元数据候选提供者在 **Android 运行时**上的正则可用性守卫。
 *
 * 为什么必须放在 androidTest：`EHentaiMetadataProvider` / `NHentaiMetadataProvider` 把正则写成
 * 对象属性，在 `<clinit>` 里编译；而 Android 的 `java.util.regex`（Harmony/ICU 实现）比 OpenJDK
 * 严格——同一个模式在 JVM 单测里能编译、在 Android 上却抛
 * `PatternSyntaxException: Syntax error in regexp pattern near index N`。
 * 曾经因此出现过「打开元数据工作台即崩溃」：`NativeMetadataProviders.<clinit>` →
 * 某个未转义的字面 `}`（`(?:^|[\s_-])\{([1-9][0-9]{0,8})}`）——JVM 单测全绿也照样崩。
 *
 * 本测试只需要**触碰**这些对象并跑一次候选匹配，就能在 Android 上把每个正则编译一遍。
 */
@RunWith(AndroidJUnit4::class)
class MetadataProviderRegexInstrumentedTest {

    @Test
    fun allNativeProvidersInitializeAndMatchOnAndroid() {
        // 触碰 <clinit>：任何非法正则都会在这里抛 PatternSyntaxException。
        val providers = NativeMetadataProviders.all
        assertTrue("至少应注册两个原生刮削来源", providers.size >= 2)

        val input = MetadataCandidateInput(
            sourceUrls = listOf(
                "https://e-hentai.org/g/1234567/abcdef1234/",
                "https://nhentai.net/g/1234567/",
            ),
            archiveTitle = "(C100) [作者] 标题 {1234567} [中国翻訳]",
            fileName = "[作者] 标题 [1234567].cbz",
        )

        for (provider in providers) {
            assertTrue("providerId 不应为空", provider.providerId.isNotBlank())
            // 触发该 provider 的全部正则（URL / gid-token / 花括号文件名 / 后缀剥离）。
            val candidates = provider.findCandidates(input)
            assertNotNull(candidates)
        }
    }

    @Test
    fun nhentaiBracedFileIdentifierStillMatchesAfterEscaping() {
        // `{1234567}` 曾因未转义的 `}` 让整个 provider 类初始化失败；这里同时钉住转义后的语义。
        // 注意：输入只有 fileName 时，provider 会同时给出「文件名里的花括号 id」与
        // 「归一化标题搜索」两个候选（见 NHentaiMetadataProvider.findCandidates），
        // 因此这里断言的是前者存在且 id 正确，而不是候选总数。
        val provider = NativeMetadataProviders.all.firstOrNull { it.providerId == "nhentai" }
        assertNotNull("应注册 nhentai 提供者", provider)

        val candidates = provider!!.findCandidates(
            MetadataCandidateInput(fileName = "[作者] 标题 {1234567}.cbz"),
        )
        assertTrue("所有候选都应来自 nhentai", candidates.all { it.providerId == "nhentai" })
        val filenameCandidate = candidates.firstOrNull {
            it.match == MetadataCandidateMatch.FILENAME_IDENTIFIER
        }
        assertNotNull("应从 `{1234567}` 解析出文件名标识候选", filenameCandidate)
        assertEquals("1234567", filenameCandidate!!.sourceId)
        assertEquals("https://nhentai.net/g/1234567", filenameCandidate.sourceUrl)
    }

    @Test
    fun ehentaiGalleryUrlStillMatchesAfterEscaping() {
        val provider = NativeMetadataProviders.all.firstOrNull { it.providerId == "ehentai" }
        assertNotNull("应注册 ehentai 提供者", provider)

        val candidates = provider!!.findCandidates(
            MetadataCandidateInput(sourceUrls = listOf("https://e-hentai.org/g/1234567/abcdef1234/")),
        )
        assertEquals(1, candidates.size)
    }
}
