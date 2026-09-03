package com.lanraragi.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.TagTranslationStore
import com.lanraragi.reader.data.model.Archive
import kotlinx.coroutines.flow.MutableStateFlow

/** 全局可观察的自定义标签颜色覆盖（namespace -> ARGB Long）。由 AppContainer 从 DataStore 同步。 */
object TagColorStore {
    val overrides = MutableStateFlow<Map<String, Long>>(emptyMap())
}

/** 解析 "#RRGGBB" 为 ARGB Long；无效返回 null。 */
fun parseHexColor(hex: String): Long? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val value = h.toLongOrNull(16) ?: return null
    return 0xFF000000L or value
}

fun Color.toHexString(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

/** 读取某命名空间标签的最终颜色：优先用户自定义，回退默认色。 */
@Composable
fun rememberTagColor(ns: String): Color {
    val overrides by TagColorStore.overrides.collectAsState()
    overrides[ns]?.let { return Color(it) }
    return TagRules.color(ns)
}

/** 读取标签的展示文本：优先 EhTagTranslation 中文译名，回退格式化后的原名。 */
@Composable
fun rememberTagText(ns: String, value: String): String {
    val translations by TagTranslationStore.translations.collectAsState()
    val nsMap = translations[ns] ?: return TagRules.formatValue(ns, value)
    return nsMap[value]?.takeIf { it.isNotBlank() }
        ?: nsMap[value.lowercase()]?.takeIf { it.isNotBlank() }
        ?: TagRules.formatValue(ns, value)
}

/**
 * 借鉴 JHenTai / EhViewer 的标签规则：把 LANraragi 的 `namespace:value` 标签
 * 按命名空间映射中文名与颜色，并按固定顺序分组展示。
 *
 * E-Hentai 的命名空间着色是其标志性 UI —— 不同命名空间（作者/角色/原作/语言…）
 * 用不同颜色一眼区分。LANraragi 的标签同为 `namespace:value` 格式，因此可直接复用。
 */
object TagRules {

    /** 命名空间展示顺序（越靠前越优先）。 */
    val NAMESPACE_ORDER = listOf(
        "parody", "character", "group", "artist", "female", "male", "mixed",
        "language", "cosplayer", "reclass", "temp",
        "date", "date_added", "series", "category", "event",
    )

    /** 命名空间中文名。 */
    val NAMESPACE_LABELS = mapOf(
        "parody" to "原作",
        "character" to "角色",
        "group" to "社团",
        "artist" to "作者",
        "female" to "女性",
        "male" to "男性",
        "mixed" to "混合",
        "language" to "语言",
        "cosplayer" to "Coser",
        "reclass" to "重分类",
        "temp" to "临时",
        "date" to "日期",
        "date_added" to "添加日期",
        "series" to "系列",
        "category" to "分类",
        "event" to "活动",
    )

    /** 命名空间颜色（借鉴 EhViewer/JHenTai 深色主题下的标签配色）。 */
    val NAMESPACE_COLORS = mapOf(
        "parody" to Color(0xFFEF5350),     // 红
        "character" to Color(0xFF66BB6A),  // 绿
        "group" to Color(0xFF42A5F5),      // 蓝
        "artist" to Color(0xFFFFA726),     // 橙
        "female" to Color(0xFFEC407A),     // 粉红
        "male" to Color(0xFF5C9CE6),       // 蓝
        "mixed" to Color(0xFFFF7043),      // 橙红
        "language" to Color(0xFFBA68C8),   // 紫
        "cosplayer" to Color(0xFF26C6DA),  // 青
        "reclass" to Color(0xFFCE93D8),    // 淡紫
        "temp" to Color(0xFF9E9E9E),       // 灰
        "date" to Color(0xFF90A4AE),       // 蓝灰
        "date_added" to Color(0xFF90A4AE), // 蓝灰
        "series" to Color(0xFFFFCA28),     // 黄
        "category" to Color(0xFF90A4AE),   // 蓝灰
        "event" to Color(0xFF90A4AE),      // 蓝灰
        "other" to Color(0xFFBDBDBD),      // 灰
    )

    private val FallbackColor = Color(0xFF9E9E9E)

    /** 取命名空间（无命名空间返回空串）。 */
    fun nsOf(fullTag: String): String = fullTag.substringBefore(':', "").trim().lowercase()

    /** 取标签值（无命名空间时返回整个标签）。 */
    fun valueOf(fullTag: String): String = fullTag.substringAfter(':', fullTag).trim()

    /** 标签是否带命名空间。 */
    fun isNamespaced(fullTag: String): Boolean = fullTag.contains(':')

    fun label(ns: String): String = NAMESPACE_LABELS[ns] ?: if (ns.isEmpty()) "其他" else ns

    fun color(ns: String): Color = NAMESPACE_COLORS[ns] ?: FallbackColor

    /** 把完整标签列表按命名空间分组并排序。 */
    fun groupTags(tags: List<String>): List<Pair<String, List<String>>> {
        val byNs = LinkedHashMap<String, MutableList<String>>()
        tags.forEach { t ->
            val ns = nsOf(t)
            if (ns == "source") return@forEach
            byNs.getOrPut(ns) { mutableListOf() }.add(t)
        }
        val ordered = mutableListOf<Pair<String, List<String>>>()
        NAMESPACE_ORDER.forEach { ns -> byNs.remove(ns)?.let { ordered += ns to it } }
        byNs.entries.filter { it.key.isNotEmpty() }.sortedBy { it.key }
            .forEach { (ns, list) -> ordered += ns to list }
        byNs[""]?.let { ordered += "" to it }
        return ordered
    }

    /** 标签值的展示格式化（如 date_added 的时间戳转日期）。 */
    fun formatValue(ns: String, value: String): String {
        if (ns == "date_added") {
            value.toLongOrNull()?.let { ts ->
                return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(ts * 1000L))
            }
        }
        return value
    }

    /** 提取档案的作者（artist 命名空间），无则返回 null。 */
    fun artistOf(archive: Archive): String? =
        archive.tagList.firstOrNull { nsOf(it) == "artist" }?.let { valueOf(it) }
}

/** 详情页用的可点击标签（点击按该标签过滤），按命名空间着色。 */
@Composable
fun TagAssistChip(
    fullTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ns = TagRules.nsOf(fullTag)
    val color = rememberTagColor(ns)
    AssistChip(
        onClick = onClick,
        label = { Text(rememberTagText(ns, TagRules.valueOf(fullTag)), color = color) },
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        modifier = modifier,
    )
}

/** 筛选面板用的可选标签，按命名空间着色。 */
@Composable
fun TagFilterChip(
    fullTag: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ns = TagRules.nsOf(fullTag)
    val color = rememberTagColor(ns)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(rememberTagText(ns, TagRules.valueOf(fullTag))) },
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        colors = FilterChipDefaults.filterChipColors(
            labelColor = color,
            selectedLabelColor = color,
            selectedContainerColor = color.copy(alpha = 0.15f),
        ),
        modifier = modifier,
    )
}
