package com.lanraragi.reader.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lanraragi.reader.data.FeatureFlags
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.edgeSwipeBack
import com.lanraragi.reader.ui.theme.ActivePill
import kotlinx.coroutines.launch

private data class NavEntry(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    container: AppContainer,
    navController: NavController,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val serverName = remember(settings?.serverName) {
        settings?.serverName?.takeIf { it.isNotBlank() } ?: "LANraragi"
    }
    val serverHost = remember(settings?.baseUrl) {
        settings?.baseUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: "未配置服务器"
    }
    val goBack: () -> Unit = { navController.popBackStack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var randomLoading by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { goBack() },
        topBar = { AppTopBar(title = "导航", onBack = goBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部品牌卡片：服务器名称 + 地址。
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        serverName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "LANraragi Reader · $serverHost",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 第一组：首页 / 设置。
            NavItem(NavEntry("首页", Icons.Filled.Home), selected = selectedTab == 0) {
                onTabChange(0)
                goBack()
            }
            NavItem(NavEntry("设置", Icons.Filled.Settings), selected = selectedTab == 2) {
                onTabChange(2)
                goBack()
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // 第二组：收藏的图片 / 统计 / 历史 / 下载。
            NavItem(NavEntry("收藏的图片", Icons.Filled.Favorite), selected = false) {
                navController.navigate(Routes.FAVORITES) {
                    popUpTo(Routes.NAVIGATION) { inclusive = true }
                }
            }
            NavItem(NavEntry("统计", Icons.Filled.BarChart), selected = false) {
                navController.navigate(Routes.STATISTICS) {
                    popUpTo(Routes.NAVIGATION) { inclusive = true }
                }
            }
            NavItem(NavEntry("历史", Icons.Filled.History), selected = false) {
                navController.navigate(Routes.HISTORY) {
                    popUpTo(Routes.NAVIGATION) { inclusive = true }
                }
            }
            NavItem(NavEntry("下载", Icons.Filled.Download), selected = selectedTab == 1) {
                onTabChange(1)
                goBack()
            }
            NavItem(NavEntry("分类", Icons.Filled.Category), selected = false) {
                navController.navigate(Routes.CATEGORY) {
                    popUpTo(Routes.NAVIGATION) { inclusive = true }
                }
            }
            if (settings != null && FeatureFlags.isEnabled(settings!!, FeatureFlags.TANKOUBONS)) {
                NavItem(NavEntry("单行本", Icons.AutoMirrored.Filled.MenuBook), selected = false) {
                    navController.navigate(Routes.TANKOUBONS) {
                        popUpTo(Routes.NAVIGATION) { inclusive = true }
                    }
                }
            }
            NavItem(NavEntry("用服务器下载链接", Icons.Filled.Link), selected = false) {
                urlText = ""
                showUrlDialog = true
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // 随机一本：打开任意一份档案。
            NavItem(
                NavEntry("随机一本", Icons.Filled.Shuffle),
                selected = false,
                enabled = !randomLoading,
            ) {
                if (randomLoading) {
                    return@NavItem
                }

                scope.launch {
                    randomLoading = true
                    val archive = runCatching { container.repository.getRandomArchive() }.getOrNull()
                    randomLoading = false

                    if (archive == null || archive.arcid.isBlank()) {
                        Toast.makeText(context, "没有可用档案", Toast.LENGTH_SHORT).show()
                    } else {
                        navController.navigate(Routes.reader(archive.arcid))
                    }
                }
            }

            // 底部留白：给内容与屏幕下缘之间留出舒适距离。
            Spacer(Modifier.height(56.dp))
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("用服务器下载链接") },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("下载链接（直链 zip）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = urlText.trim()
                        if (url.isEmpty()) {
                            Toast.makeText(context, "请输入链接", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        showUrlDialog = false
                        scope.launch {
                            runCatching { container.repository.downloadFromUrl(url) }
                                .onSuccess { Toast.makeText(context, "已提交下载", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "提交失败：${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun NavItem(
    entry: NavEntry,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(shape)
            .background(if (selected) ActivePill else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            entry.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Text(entry.label, style = MaterialTheme.typography.titleMedium)
    }
}
