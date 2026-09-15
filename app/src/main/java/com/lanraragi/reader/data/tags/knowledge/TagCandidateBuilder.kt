package com.lanraragi.reader.data.tags.knowledge

import com.lanraragi.reader.data.model.TagStat
import java.util.Locale

/**
 * 一条搜索联想候选：词库命中与本库命中合并后，**保留来源与频次**的统一模型。
 *
 * 为什么不能直接把 [TagSuggestion] 当 UI 模型用：
 * 1. 词库（EhTagTranslation）的命名空间是英文（`artist` / `language` / …），而服务器上
 *    常常是中文命名空间（`艺术家` 422 个标签、`语言`、`原作` …），二者是**两套平行词汇**；
 * 2. 历史上把词库候选无差别排在本库候选之前，用户点第一条很可能搜出 0 条结果；
 * 3. 词库频次（全站）与本库频次（本库档案数）语义不同，混在一起会误导用户。
 *
 * 排序原则：**先看文本相关度，同级时本库真的有这个标签的排前面**，并把「本库 N 本」/
 * 「词库」如实标在行上，让用户自己判断哪一条能搜到东西。
 */
data class TagCandidate(
    /** 会被插入查询串的完整标签，例如 `艺术家:kakao`。 */
    val full: String,
    val namespace: String,
    val tagKey: String,
    /** 词库给出的译名；本库独有的候选为 null（中文命名空间的值本身通常已是中文）。 */
    val translatedName: String?,
    /** 文本相关度档位，见 [TagCandidateBuilder] 的 `R_*` 常量；越大越相关。 */
    val relevance: Int,
    /** 词库给出的排序分；本库独有的候选为 0。 */
    val score: Double,
    /** 本库中带该标签的档案数；null 表示本库词表里没有这个标签。 */
    val libraryCount: Int?,
    /** 用户自己的搜索历史里出现过该标签的次数。 */
    val personalCount: Long,
) {
    val inLibrary: Boolean get() = libraryCount != null

    /** 行右侧的来源标记：本库频次是全库档案数，词库频次是全站数据，两者不能混为一谈。 */
    val sourceLabel: String get() = libraryCount?.let { "本库 $it" } ?: "词库"
}

object TagCandidateBuilder {

    /*
     * 相关度档位。
     *
     * 这里刻意**不直接用** [TagMatchQuality.baseScore]：词库排序器把「命名空间的**前缀**命中」
     * （如输入 `art` 命中命名空间 `artist`）排在「标签值的前缀命中」之上（450 > 400）。
     * 在双词汇的库里这会稳定地把「本库真的有、值也真以 art 开头」的 `艺术家:art jam`
     * 压到 `artist:*` 一整片候选之后 —— 正是「点第一条搜不到」的成因。
     * 对「我该点哪一条」这个问题来说，用户打的字命中**值**比命中命名空间更有说服力。
     */
    /** 命名空间精确命中，或标签值前缀命中。 */
    const val R_VALUE_PREFIX = 4

    /** 标签值任意位置命中，或词库译名前缀命中。 */
    const val R_VALUE_CONTAINS = 3

    /** 命名空间前缀命中（用户只打了一半命名空间）。 */
    const val R_NAMESPACE_PREFIX = 2

    /** 只有词库的译名/简介命中。 */
    const val R_WEAK = 1

    /**
     * @param token 光标所在片段（去掉 `-` 与首尾空白），例如 `art`、`language:chi`。
     * @param dictionary 词库候选，已由 [TagSuggestionRanker] 排好序。
     * @param libraryTags 本库标签词表（含频次），来自 `/api/database/stats` 或本地索引。
     * @param limit 上限；多出来的由调用方用「更多」展开。
     */
    fun build(
        token: String,
        dictionary: List<TagSuggestion>,
        libraryTags: List<TagStat>,
        limit: Int = 40,
    ): List<TagCandidate> {
        if (limit <= 0) return emptyList()
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return emptyList()
        val namespaceToken = trimmed.substringBefore(':', "").trim().takeIf { ':' in trimmed }
        val valueToken = (if (namespaceToken == null) trimmed else trimmed.substringAfter(':')).trim()
        val needle = valueToken.lowercase(Locale.ROOT)
        // 只输了一半命名空间（`language:`）时不是「没有查询」，而是「列出这个命名空间下的标签」：
        // 两边都按命名空间精确命中对待，于是排序退回「本库优先」而不是让词库独自占满。
        if (needle.isEmpty() && namespaceToken == null) return emptyList()

        val rows = LinkedHashMap<String, TagCandidate>()

        // 1) 本库候选：用**标签值**而不是 `ns:value` 整串做前缀判断。
        //    `艺术家:art jam` 的整串并不以 `art` 开头，若按整串判断就只能算「包含」，
        //    于是本库命中在排序里被词库的 `artist:art jam`（真前缀）稳定压住。
        libraryTags.forEach { stat ->
            val namespace = stat.namespace.orEmpty().trim()
            val value = stat.text.trim()
            if (value.isEmpty()) return@forEach
            if (namespaceToken != null && !namespace.equals(namespaceToken, ignoreCase = true)) return@forEach
            val relevance = relevance(needle, namespace, value)
            if (relevance < R_NAMESPACE_PREFIX) return@forEach
            val full = if (namespace.isEmpty()) value else "$namespace:$value"
            val key = full.lowercase(Locale.ROOT)
            // 同一个标签在词表里理论上只出现一次；真重复时取频次更高的那条。
            val previousCount = rows[key]?.libraryCount
            if (previousCount == null || previousCount < stat.weight) {
                rows[key] = TagCandidate(
                    full = full, namespace = namespace, tagKey = value,
                    translatedName = rows[key]?.translatedName, relevance = relevance,
                    score = rows[key]?.score ?: 0.0, libraryCount = stat.weight,
                    personalCount = rows[key]?.personalCount ?: 0L,
                )
            }
        }

        // 2) 词库候选：同一条标签已由本库给出频次时合并进去（保留本库频次），
        //    相关度取两边的较优者 —— 词库还知道译名命中，本库知道标签值的真实前缀关系。
        dictionary.forEach { suggestion ->
            val entry = suggestion.entry
            val namespace = entry.namespace.orEmpty().trim()
            val value = entry.tagKey.trim()
            if (value.isEmpty()) return@forEach
            if (namespaceToken != null && !namespace.equals(namespaceToken, ignoreCase = true)) return@forEach
            val full = if (namespace.isEmpty()) value else "$namespace:$value"
            val key = full.lowercase(Locale.ROOT)
            val previous = rows[key]
            rows[key] = TagCandidate(
                full = full, namespace = namespace, tagKey = value,
                translatedName = entry.translatedName?.takeIf(String::isNotBlank) ?: previous?.translatedName,
                relevance = maxOf(previous?.relevance ?: 0, relevance(needle, namespace, value), fromQuality(suggestion.quality)),
                score = maxOf(previous?.score ?: 0.0, suggestion.score),
                libraryCount = previous?.libraryCount,
                personalCount = maxOf(previous?.personalCount ?: 0L, suggestion.personalFrequency),
            )
        }

        return rows.values.sortedWith(ORDER).take(limit)
    }

    private val ORDER: Comparator<TagCandidate> =
        compareByDescending<TagCandidate> { it.relevance }
            .thenByDescending { it.inLibrary }
            .thenByDescending { it.libraryCount ?: 0 }
            .thenByDescending { it.personalCount }
            .thenByDescending { it.score }
            .thenBy { it.full.lowercase(Locale.ROOT) }

    /** 文本相关度：值命中优先于命名空间命中。 */
    private fun relevance(needle: String, namespace: String, value: String): Int {
        if (needle.isEmpty()) return R_VALUE_PREFIX
        val ns = namespace.lowercase(Locale.ROOT)
        val v = value.lowercase(Locale.ROOT)
        return when {
            ns == needle -> R_VALUE_PREFIX
            v.startsWith(needle) -> R_VALUE_PREFIX
            v.contains(needle) -> R_VALUE_CONTAINS
            ns.isNotEmpty() && ns.startsWith(needle) -> R_NAMESPACE_PREFIX
            else -> 0
        }
    }

    /** 把词库排序器的判定折算到本类的档位，保留它对译名/简介的额外信息。 */
    private fun fromQuality(quality: TagMatchQuality): Int = when (quality) {
        TagMatchQuality.NAMESPACE_EXACT, TagMatchQuality.CANONICAL_PREFIX -> R_VALUE_PREFIX
        TagMatchQuality.TRANSLATED_PREFIX, TagMatchQuality.CANONICAL_CONTAINS -> R_VALUE_CONTAINS
        TagMatchQuality.NAMESPACE_PREFIX -> R_NAMESPACE_PREFIX
        TagMatchQuality.TRANSLATED_CONTAINS -> R_WEAK
        TagMatchQuality.NONE -> 0
    }
}
