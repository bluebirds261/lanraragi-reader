package com.lanraragi.reader.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanraragi.reader.ui.AppTopBar

/** 「入门指南」页：连接服务器 / 导入本地漫画 / 阅读器手势 / 元数据刮削 四张静态说明卡。 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    // 独立路由与向导内嵌两种场景下，系统返回键都走 onBack（向导内返回后停留在当前步）。
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "入门指南", onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuideCard(
                icon = Icons.Filled.Link,
                title = "连接服务器",
                description = "在「设置 → 连接」中填入 LANraragi 的服务器地址与 API Key" +
                    "（API Key 可在网页端「设置 → 服务器设置」中查看），测试通过后即可浏览服务器书架并同步阅读进度。",
            )
            GuideCard(
                icon = Icons.Filled.Folder,
                title = "导入本地漫画",
                description = "在「设置 → 数据」中选择存储根目录，把漫画文件放入其中即可自动扫描进本地书架；" +
                    "下载的原档也会保存在该目录下。",
            )
            GuideCard(
                icon = Icons.Filled.TouchApp,
                title = "阅读器手势",
                description = "阅读时左右滑动或点按屏幕边缘翻页，点按屏幕中央唤出菜单；" +
                    "支持音量键翻页、双页拼合与点击区域自定义，可在「设置 → 阅读」中调整。",
            )
            GuideCard(
                icon = Icons.Filled.AutoAwesome,
                title = "元数据刮削",
                description = "在详情页打开「编辑元数据」即可从插件刮削标题与标签；" +
                    "支持多候选匹配与批量刮削，还能把英文标签翻译成中文后回写服务器。",
            )
        }
    }
}

@Composable
private fun GuideCard(icon: ImageVector, title: String, description: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
