package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.TagRules
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.rememberTagColor
import com.lanraragi.reader.ui.rememberTagText

/** tag 词条统计与词云。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagStatsScreen(container: AppContainer, navController: NavController) {
    var tags by remember { mutableStateOf<List<TagStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        tags = runCatching { container.repository.getTags() }.getOrDefault(emptyList())
        loading = false
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = { AppTopBar(title = "统计", onBack = { navController.popBackStack() }) },
    ) { padding ->
        if (loading) {
            LoadingBox(Modifier.padding(padding))
        } else {
            val namespaceCount = remember(tags) {
                tags.mapNotNull { it.namespace }.distinct().size
            }
            val maxWeight = remember(tags) { (tags.maxOfOrNull { it.weight } ?: 0).coerceAtLeast(1) }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Card(Modifier.fillMaxSize()) {
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
                    tags.sortedByDescending { it.weight }.take(120).forEach { tag ->
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
    }
}
