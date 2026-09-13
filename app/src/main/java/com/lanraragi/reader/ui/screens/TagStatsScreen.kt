package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.data.tags.TagNamespaceRegistry
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText
import kotlinx.coroutines.CancellationException

/**
 * 标签词云分区（原孤儿 TagStatsScreen 抽出的内容部分，去掉自带 Scaffold/TopBar）。
 * 作为统计页第三区复用：词条统计卡 + 全命名空间词云，独立加载/错误/空态。
 */
@Composable
fun TagStatsContent(container: AppContainer, modifier: Modifier = Modifier) {
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
        loading -> LoadingBox(modifier.fillMaxWidth().height(SectionStateHeight))
        error != null -> ErrorBox(error!!, onRetry = { retryKey++ }, modifier = modifier.fillMaxWidth().height(SectionStateHeight))
        else -> TagStatsBody(tags, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagStatsBody(tags: List<TagStat>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) {
        EmptyBox("暂无标签数据", modifier.fillMaxWidth().height(SectionStateHeight))
        return
    }
    val namespaceCount = remember(tags) {
        tags.mapNotNull { TagNamespaceRegistry.canonicalNamespace(it.namespace) }
            .distinct()
            .size
    }
    val visibleTags = remember(tags) {
        tags.filterNot {
            TagNamespaceRegistry.descriptor(it.namespace)?.defaultHidden == true
        }
    }
    val maxWeight = remember(visibleTags) {
        (visibleTags.maxOfOrNull { it.weight } ?: 0).coerceAtLeast(1)
    }
    Column(modifier) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("词条统计", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("共 ${tags.size} 个词条 · ${namespaceCount} 个命名空间", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("词云", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visibleTags.asSequence()
                .sortedByDescending { it.weight }
                .take(120)
                .forEach { tag ->
                    val ns = TagRules.nsOf(tag.full)
                    Text(
                        rememberTagText(ns, TagRules.valueOf(tag.full)),
                        color = rememberTagColor(ns),
                        fontSize = (12 + (tag.weight.toFloat() / maxWeight) * 16).sp,
                    )
                }
        }
    }
}
