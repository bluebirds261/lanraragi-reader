package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.edgeSwipeBack

@Composable
fun OfflineScreen(container: AppContainer, navController: NavController) {
    val index by container.offlineCache.index.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(title = "离线缓存", onBack = { navController.popBackStack() })
        },
    ) { padding ->
        if (index.items.isEmpty()) {
            EmptyBox("还没有离线缓存。\n在档案详情页点「离线缓存」即可把整本下载到本地。", Modifier.padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "共 ${index.items.size} 本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { container.offlineCache.clearAll() }) {
                        Text("清空全部", color = MaterialTheme.colorScheme.error)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    items(index.items, key = { it.arcid }) { item ->
                        Card(
                            onClick = { navController.navigate(Routes.reader(item.arcid)) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(64.dp)
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    AsyncImage(
                                        model = container.offlineCache.coverFile(item.arcid),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title.ifBlank { item.arcid }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    Text("${item.pageCount} 页", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { container.offlineCache.delete(item.arcid) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除缓存", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
