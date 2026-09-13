package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lanraragi.reader.data.HistoryEntry
import com.lanraragi.reader.data.catalog.isTankArchiveId
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.RemoteThumbnailImage
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.edgeSwipeBack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** 打开档案历史：按天分组，每块右下角显示 24 小时制打开时间。 */
@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    val entries by container.historyRepository.entries.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(
                title = "历史",
                onBack = { navController.popBackStack() },
                actions = {
                    OutlinedButton(onClick = { container.historyRepository.clear() }) {
                        Text("清空")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyBox("暂无历史记录。\n打开档案后会自动记录。", Modifier.padding(padding))
        } else {
            val grouped = remember(entries) {
                entries.groupBy { dateFormat.format(Date(it.timestamp)) }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (date, dayEntries) ->
                    item(key = "header-$date") {
                        Text(
                            date,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(dayEntries, key = { it.timestamp }) { entry ->
                        HistoryRow(container, entry) {
                            // 历史里理论上只有档案 arcid，但 TANK_ id 一旦落进来，
                            // 走档案详情必然失败——统一按单行本路由兜住。
                            if (isTankArchiveId(entry.arcid)) {
                                navController.navigate(Routes.tankReader(entry.arcid))
                            } else {
                                navController.navigate(Routes.detail(entry.arcid))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(container: AppContainer, entry: HistoryEntry, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                RemoteThumbnailImage(
                    container = container,
                    arcid = entry.arcid,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { entry.arcid },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        timeFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
