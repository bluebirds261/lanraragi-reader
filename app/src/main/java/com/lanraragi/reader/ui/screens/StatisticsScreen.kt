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
import androidx.navigation.NavController
import com.lanraragi.reader.data.model.TagStat
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.rememberTagText

// 女性 tag 粉色系：低权重浅粉 → 高权重深粉。
private val FemaleLight = Color(0xFFF8BBD0)
private val FemaleDark = Color(0xFFC2185B)

/** 统计页：只统计「女性：XX」类型 tag，圆形渐变词云 + 排名横柱图，中文显示。 */
@Composable
fun StatisticsScreen(container: AppContainer, navController: NavController) {
    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = { AppTopBar(title = "统计", onBack = { navController.popBackStack() }) },
    ) { padding ->
        StatisticsContent(container, Modifier.padding(padding))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsContent(container: AppContainer, modifier: Modifier = Modifier) {
    var tags by remember { mutableStateOf<List<TagStat>>(emptyList()) }
    var topN by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        tags = runCatching { container.repository.getTags() }.getOrDefault(emptyList())
    }

    // 只统计 female namespace 的 tag，按权重降序。
    val femaleTags = remember(tags) {
        tags.filter { it.namespace?.lowercase() == "female" }.sortedByDescending { it.weight }
    }
    val maxWeight = (femaleTags.maxOfOrNull { it.weight } ?: 0).coerceAtLeast(1)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
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
