package com.lanraragi.reader.data.catalog

import com.lanraragi.reader.data.tags.TagNamespaceRegistry

/**
 * 「排序字段在当前库上到底有没有生效」的判定。
 *
 * 服务端把 `sortby` 当成**字面正则**去标签串里找命名空间（LANraragi 0.9.81
 * `lib/LANraragi/Model/Search.pm:576` 与 `:595`）：
 *
 *     my $re = qr/$sortkey/;
 *     $tmpfilter{$id} = ( $tags =~ m/.*${re}:(.*?)(\,.*|$)/ ) ? $1 : "zzzz";
 *     ...
 *     push @sorted, @unkeyed_ids;   # :621 命中不到命名空间的档案全被扔到末尾
 *
 * 于是当库里没有该命名空间的标签时，服务端**既不报错也不退回上一个顺序**：
 * 所有档案都进 `@unkeyed_ids`，保持 Redis 索引扫描的任意顺序 —— 用户看到的是
 * 「点了排序、顺序确实变了、但没有任何规律」。客户端拿不到任何错误信号，
 * 只能拿服务器自己的标签统计来判真伪。
 *
 * 这里**只判真伪，不改写**：把「艺术家」映射回 `artist` 属于元数据归一化的
 * 范畴（见 `docs/TAG-NORMALIZATION-DISCUSSION.md`），在结论出来之前，
 * 客户端不擅自替换服务器正在使用的命名空间写法。
 */
object LibrarySortResolver {

    /** 服务端专门处理、不依赖命名空间的键。 */
    private val SERVER_BUILT_IN = setOf("title", "lastread", "date_added")

    enum class Availability {
        /** 尚未取到服务器的标签统计，不阻断任何选项。 */
        UNKNOWN,

        /** 该命名空间在库里有标签，排序会按它生效。 */
        EFFECTIVE,

        /** 库里没有该命名空间的标签，服务端会退回任意顺序。 */
        INEFFECTIVE,
    }

    /**
     * @param tagCount 直接命中该键的标签条数（0 表示库里没有）。
     * @param similarNamespace 库里存在、但写法不同的相近命名空间（例如 `artist` 与「艺术家」）。
     *   只用于给用户一句解释，**不会被用作排序键**。
     */
    data class Report(
        val availability: Availability,
        val tagCount: Int = 0,
        val similarNamespace: String? = null,
    ) {
        val ineffective get() = availability == Availability.INEFFECTIVE
    }

    /**
     * @param namespaceCounts 服务器上每个命名空间的标签条数（键已小写）。
     *   `null` 表示尚未取到统计；空表按「库里一个带命名空间的标签都没有」处理。
     */
    fun report(sortKey: String, namespaceCounts: Map<String, Int>?): Report {
        val key = sortKey.trim()
        if (key.isEmpty() || key.lowercase() in SERVER_BUILT_IN) return Report(Availability.EFFECTIVE)
        val counts = namespaceCounts ?: return Report(Availability.UNKNOWN)
        val direct = tagCount(counts, key)
        if (direct > 0) return Report(Availability.EFFECTIVE, direct)
        return Report(Availability.INEFFECTIVE, 0, similarNamespace(key, counts))
    }

    /**
     * 命名空间条数查询，大小写不敏感。
     *
     * [com.lanraragi.reader.data.SearchDiscoveryRepository.namespaceCountsOf] 产出的键已经是
     * 小写，但这里不能假设调用方一定归一过：漏判会让一个本来能用的排序被误标成「不生效」，
     * 正是本类要消灭的那类错误。
     */
    private fun tagCount(counts: Map<String, Int>, namespace: String): Int =
        counts[namespace.lowercase()]
            ?: counts.entries.firstOrNull { it.key.equals(namespace, ignoreCase = true) }?.value
            ?: 0

    /**
     * 库里是否存在「语义相近但写法不同」的命名空间。
     *
     * 判定只用命名空间的**中文标签**（`TagNamespaceRegistry` 已有的展示元数据），
     * 属于「提示」而不是「等价关系」：命中时只告诉用户服务端在用什么写法，
     * 不改变发出去的 `sortby`。真正的中英归一化待讨论稿结论。
     */
    private fun similarNamespace(sortKey: String, counts: Map<String, Int>): String? {
        val label = TagNamespaceRegistry.descriptor(sortKey)?.labelZh ?: return null
        if (label.equals(sortKey, ignoreCase = true)) return null
        if (tagCount(counts, label) <= 0) return null
        return counts.keys.firstOrNull { it.equals(label, ignoreCase = true) }
    }

    /** 一句给用户看的说明；[Availability.EFFECTIVE] 返回 null。 */
    fun explain(sortKey: String, label: String, report: Report): String? = when (report.availability) {
        Availability.EFFECTIVE, Availability.UNKNOWN -> null
        Availability.INEFFECTIVE -> buildString {
            append("本库没有 ")
            append(sortKey)
            append(" 命名空间的标签，「")
            append(label)
            append("」排序不会生效")
            report.similarNamespace?.let { append("（库里相近的写法是「").append(it).append("」）") }
        }
    }

    /** 结果摘要这类窄位置用的短提示。 */
    fun shortNote(sortKey: String, report: Report): String? =
        if (report.ineffective) "排序未生效：本库无 $sortKey 命名空间" else null
}
