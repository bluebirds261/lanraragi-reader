package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanraragi.reader.data.diagnostics.DiagnosticEvent
import com.lanraragi.reader.data.diagnostics.DiagnosticReportCodec
import com.lanraragi.reader.data.diagnostics.DiagnosticReportContext
import com.lanraragi.reader.data.diagnostics.DiagnosticsFacade
import com.lanraragi.reader.data.diagnostics.MetricSnapshot
import com.lanraragi.reader.ui.AppTopBar

/**
 * UI-only surface. The host owns SAF document creation and writes the supplied JSON on success.
 */
@Composable
fun DiagnosticsScreen(
    diagnostics: DiagnosticsFacade,
    reportContext: DiagnosticReportContext,
    onExportToSaf: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val metrics by diagnostics.metrics.snapshot.collectAsStateWithLifecycle()
    val logs by diagnostics.log.snapshot.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppTopBar(
                title = "诊断信息",
                onBack = onBack,
                actions = {
                    IconButton(onClick = diagnostics::clearLogs) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空诊断日志")
                    }
                    IconButton(onClick = {
                        onExportToSaf(DiagnosticReportCodec.encode(diagnostics.exportReport(reportContext)))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "导出诊断报告")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "metrics-heading") { Text("性能指标", style = MaterialTheme.typography.titleMedium) }
            if (metrics.isEmpty()) {
                item(key = "metrics-empty") { Text("尚无性能采样", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(metrics, key = { it.metric.name }) { MetricRow(it) }
            }
            item(key = "logs-heading") { Text("事件日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (logs.isEmpty()) {
                item(key = "logs-empty") { Text("尚无诊断事件", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(logs, key = { it.id }) { EventRow(it) }
            }
        }
    }
}

@Composable
private fun MetricRow(snapshot: MetricSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(snapshot.metric.name, style = MaterialTheme.typography.labelLarge)
                Text("样本 ${snapshot.count}", style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text("P50 ${snapshot.p50?.format()} ${snapshot.unit}", style = MaterialTheme.typography.bodyMedium)
                Text("P95 ${snapshot.p95?.format()} ${snapshot.unit}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EventRow(event: DiagnosticEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${event.level} ${event.event}", style = MaterialTheme.typography.labelLarge)
            if (event.fields.isNotEmpty()) {
                Text(
                    event.fields.entries.joinToString("  ") { "${it.key}=${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Double.format(): String = "%.1f".format(java.util.Locale.ROOT, this)
