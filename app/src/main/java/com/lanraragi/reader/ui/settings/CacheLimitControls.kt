package com.lanraragi.reader.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OfflineCacheLimitEditor(value: Long, onSelect: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("离线缓存上限", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text(if (value == 0L) "不自动淘汰" else formatByteLimit(value)) }
            DropdownMenu(menu, { menu = false }) {
                listOf(
                    0L to "不自动淘汰",
                    512L * MIB to "512 MB",
                    2L * GIB to "2 GB",
                    5L * GIB to "5 GB",
                ).forEach { (bytes, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(bytes); menu = false })
                }
                DropdownMenuItem(text = { Text("自定义") }, onClick = { menu = false; input = ""; invalid = false; custom = true })
            }
        }
    }
    if (custom) AlertDialog(
        onDismissRequest = { custom = false },
        title = { Text("自定义离线缓存上限") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; invalid = false },
                    label = { Text("容量，例如 768 MB 或 2 GB") },
                    isError = invalid,
                    supportingText = if (invalid) ({ Text("请输入不溢出的正整数 MB/GB") }) else null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                parseCacheLimitBytes(input)?.let { onSelect(it); custom = false } ?: run { invalid = true }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = { custom = false }) { Text("取消") } },
    )
}

fun parseCacheLimitBytes(value: String): Long? {
    val match = Regex("^(\\d+)\\s*(MB|GB)?$", RegexOption.IGNORE_CASE).matchEntire(value.trim()) ?: return null
    val amount = match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
    val multiplier = if (match.groupValues[2].equals("MB", true)) MIB else GIB
    return runCatching { Math.multiplyExact(amount, multiplier) }.getOrNull()
}

private fun formatByteLimit(bytes: Long): String = if (bytes >= GIB) "%.1f GB".format(bytes.toDouble() / GIB) else "%.0f MB".format(bytes.toDouble() / MIB)
private const val MIB = 1024L * 1024L
private const val GIB = 1024L * MIB
