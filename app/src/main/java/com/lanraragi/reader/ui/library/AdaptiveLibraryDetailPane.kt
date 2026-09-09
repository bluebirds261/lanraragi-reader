package com.lanraragi.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.model.Archive

@Composable
fun AdaptiveLibraryDetailPane(
    archive: Archive,
    onOpenDetail: () -> Unit,
    onRead: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(archive.displayTitle.ifBlank { archive.arcid }, style = MaterialTheme.typography.titleLarge)
        if (archive.tags.isNotBlank()) {
            Text(archive.tags, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (archive.pagecount > 0) "${archive.pagecount} 页 · 已读 ${archive.progress.coerceIn(0, archive.pagecount)} 页"
            else "页数将在本地/服务器索引可用后显示",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (archive.summary.isNotBlank()) {
            Text(archive.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenDetail) { Text("打开详情") }
            TextButton(onClick = onRead) { Text("开始阅读") }
        }
    }
}
