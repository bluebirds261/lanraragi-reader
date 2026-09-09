package com.lanraragi.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.favorites.EhFavoriteCategoryPreviewResult
import com.lanraragi.reader.data.favorites.EhFavoriteCategorySyncPreview

/**
 * Stateless review surface for F02. It deliberately exposes no destructive or reverse-sync
 * command: the only mutation callback is an explicit approval of a rendered preview.
 */
@Composable
fun EhFavoriteSyncPanel(
    result: EhFavoriteCategoryPreviewResult,
    onConfirm: (EhFavoriteCategorySyncPreview) -> Unit,
    onChangeMapping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (result) {
            EhFavoriteCategoryPreviewResult.NoMapping -> {
                Text("尚未关联 LANraragi 分类", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onChangeMapping) { Text("关联分类") }
            }
            EhFavoriteCategoryPreviewResult.NoSnapshot -> {
                Text("尚无 E-H 收藏快照", style = MaterialTheme.typography.bodyLarge)
            }
            is EhFavoriteCategoryPreviewResult.Ready -> {
                val preview = result.preview
                Text("将添加 ${preview.addCount} 项", style = MaterialTheme.typography.titleMedium)
                Text("已存在 ${preview.keepCount} 项，未关联 ${preview.unmatchedCount} 项")
                Button(
                    onClick = { onConfirm(preview) },
                    enabled = preview.addCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("确认添加到分类") }
                OutlinedButton(onClick = onChangeMapping, modifier = Modifier.fillMaxWidth()) {
                    Text("更改关联分类")
                }
            }
        }
    }
}
