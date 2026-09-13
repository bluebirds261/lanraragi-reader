package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.ServerStats
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.rememberTagText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 女性 tag 粉色系：低权重浅粉 → 高权重深粉。
private val FemaleLight = Color(0xFFF8BBD0)
private val FemaleDark = Color(0xFFC2185B)

/** 分区级加载/错误/空态的统一占位高度，避免状态块把整页撑满。 */
internal val SectionStateHeight = 180.dp

/** 统计页：纵向单页三分区 —— 本地使用统计 / 服务器统计 / 标签词云，各分区独立加载互不影响。 */
@Composable
fun StatisticsScreen(container: AppContainer, navController: NavController) {
    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = { AppTopBar(title = "统计", onBack = { navController.popBackStack() }) },
    ) { padding ->
        StatisticsContent(container, Modifier.padding(padding))
    }
}

@Composable
fun StatisticsContent(container: AppContainer, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        // 第一区：本地使用统计（原统计页内容）。
        SectionHeader("本地使用统计")
        LocalUsageStatsSection(container)

        Spacer(Modifier.height(28.dp))

        // 第二区：服务器统计（并入原无入口的 StatsScreen）。
        SectionHeader("服务器统计")
        ServerStatsSection(container)

        Spacer(Modifier.height(28.dp))

        // 第三区：标签词云（接入原孤儿 TagStatsScreen 的内容）。
        SectionHeader("标签词云")
        TagStatsContent(container)
    }
}

/** 各分区的小标题。 */
@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
}

/** 第一区：本地使用统计 —— 女性 Tag 词云与热度排行，数据取自仓库标签数据，独立加载/错误/空态。 */
@Composable
private fun LocalUsageStatsSection(container: AppContainer) {
    var tags by remember { mutableStateOf<List<TagStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        try {
            tags = container.repository.getTags()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    when {
        loading -> LoadingBox(Modifier.fillMaxWidth().height(SectionStateHeight))
        error != null -> ErrorBox(error!!, onRetry = { retryKey++ }, modifier = Modifier.fillMaxWidth().height(SectionStateHeight))
        else -> FemaleTagStatsBody(tags)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FemaleTagStatsBody(tags: List<TagStat>) {
    var topN by remember { mutableIntStateOf(10) }

    // 只统计 female namespace 的 tag，按权重降序。
    val femaleTags = remember(tags) {
        tags.filter { it.namespace?.lowercase() == "female" }.sortedByDescending { it.weight }
    }
    if (femaleTags.isEmpty()) {
        EmptyBox("暂无女性 Tag 数据", Modifier.fillMaxWidth().height(SectionStateHeight))
        return
    }
    val maxWeight = (femaleTags.maxOfOrNull { it.weight } ?: 0).coerceAtLeast(1)

    Column {
        // 顶部统计卡片
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("女性 Tag 统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "共 ${femaleTags.size} 个女性词条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 圆形词云：字号与颜色随权重渐变，中文显示。
        Text("词云", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                femaleTags.take(60).forEach { tag ->
                    val norm = tag.weight.toFloat() / maxWeight
                    Text(
                        rememberTagText("female", tag.text),
                        color = lerp(FemaleLight, FemaleDark, norm),
                        fontSize = (12 + norm * 20).sp,
                        fontWeight = if (norm > 0.7f) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 热度排行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("热度排行", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            listOf(5, 10, 20).forEach { n ->
                FilterChip(
                    selected = topN == n,
                    onClick = { topN = n },
                    label = { Text("前$n") },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        femaleTags.take(topN).forEachIndexed { index, tag ->
            val norm = tag.weight.toFloat() / maxWeight
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 5.dp),
            ) {
                // 排名徽章
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < 3) lerp(FemaleLight, FemaleDark, 0.8f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index < 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    rememberTagText("female", tag.text),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(10.dp))
                // 渐变横柱
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(lerp(FemaleLight, FemaleDark, norm).copy(alpha = 0.25f + 0.75f * norm)),
                )
                Spacer(Modifier.width(10.dp))
                Text(tag.weight.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 服务器统计 VM（自原 StatsScreen 迁入，逻辑不变）。 */
class StatsViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val stats: ServerStats? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val stats = container.repository.getStats()
                _state.update { it.copy(loading = false, stats = stats) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }
}

/** 第二区：服务器统计 —— 档案总数/标签数走 repository.getStats()，累计阅读页数与服务器名/版本取自 ApiClient 缓存的 /api/info。 */
@Composable
private fun ServerStatsSection(container: AppContainer) {
    val vm: StatsViewModel = viewModel { StatsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val serverInfo by ApiClient.config.serverInfo.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "数据来源：LANraragi 服务器",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.loading -> LoadingBox(Modifier.fillMaxWidth().height(SectionStateHeight))
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load, modifier = Modifier.fillMaxWidth().height(SectionStateHeight))
            state.stats != null -> {
                val s = state.stats!!
                val info = serverInfo
                StatCard("档案总数", s.total_archives.toString())
                StatCard("累计阅读页数", info?.total_pages_read?.toString() ?: "未知")
                StatCard("标签数", s.tags_count.toString())
                Text(
                    if (info == null) {
                        "服务器：未知（尚未获取到服务器信息）"
                    } else {
                        "服务器：${info.name.ifBlank { "（未命名）" }} · 版本：${info.version_name.ifBlank { info.version }.ifBlank { "未知" }}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
