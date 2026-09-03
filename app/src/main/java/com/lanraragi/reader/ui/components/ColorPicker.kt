package com.lanraragi.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 标签颜色选取器：RGB 三滑杆 + 十六进制输入 + 预览色块，双向同步。
 * 参考 JHenTai 的颜色自定义设置，同时适配 RGB 与 #RRGGBB 十六进制两种调色方式。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var color by remember { mutableStateOf(initialColor) }
    var hexText by remember { mutableStateOf(initialColor.toHexString()) }

    fun updateFromColor(c: Color) {
        color = c
        hexText = c.toHexString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
                ColorSlider("红", color.red) { r -> updateFromColor(color.copy(red = r)) }
                ColorSlider("绿", color.green) { g -> updateFromColor(color.copy(green = g)) }
                ColorSlider("蓝", color.blue) { b -> updateFromColor(color.copy(blue = b)) }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t
                        parseHexColor(t)?.let { c -> color = Color(c) }
                    },
                    label = { Text("十六进制") },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                    isError = parseHexColor(hexText) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "RGB(${(color.red * 255).toInt()}, ${(color.green * 255).toInt()}, ${(color.blue * 255).toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("恢复默认") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp))
        Slider(
            value = value * 255f,
            onValueChange = { onChange(it / 255f) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            (value * 255).toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End,
        )
    }
}
