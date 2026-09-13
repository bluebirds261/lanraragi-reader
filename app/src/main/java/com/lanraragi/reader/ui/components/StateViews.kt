package com.lanraragi.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("重试")
            }
        }
    }
}

/**
 * D5 空态提示：可选的粗体标题 + 正文，文字下方可挂一排次要动作（如 扫描本地 / 上传 / 随机一本）。
 * 标题与动作都有默认值，保持既有调用点行为不变。
 *
 * 占位尺寸由 [modifier] 决定：默认铺满可用空间；底部抽屉等高度受限的容器
 * 可传 `Modifier.height(...)` 收窄（与 TagStatsScreen / StatisticsScreen 的用法一致），
 * 否则 `fillMaxSize` 会把容器撑到最大高度。
 */
@Composable
fun EmptyBox(
    message: String,
    modifier: Modifier = Modifier,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    title: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (actions.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                actions.forEach { (label, onClick) ->
                    TextButton(onClick = onClick) {
                        Text(label)
                    }
                }
            }
        }
    }
}
