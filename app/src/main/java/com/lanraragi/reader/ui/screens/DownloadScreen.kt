package com.lanraragi.reader.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveCard
import com.lanraragi.reader.ui.ArchiveListRow
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DownloadViewModel(private val container: AppContainer) : ViewModel() {
    var isRefreshing by mutableStateOf(false)
        private set

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            // 重新扫描本地路径
            val settings = container.settingsRepository.settings.first()
            container.localScanManager.scan(settings.extraScanDirUris)
            // 重新加载离线索引
            container.offlineCache.loadIndex()
            isRefreshing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    container: AppContainer,
    navController: NavController? = null,
    onBackToHome: () -> Unit = {}
) {
    val vm: DownloadViewModel = viewModel { DownloadViewModel(container) }
    val index by container.offlineCache.index.collectAsStateWithLifecycle()
    val localArchives by container.localScanManager.localArchives.collectAsStateWithLifecycle()
    val isScanning by container.localScanManager.isScanning.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 全部, 1: 云端, 2: 本地

    val context = LocalContext.current
    val favDir = remember {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        File(base, "收藏")
    }
    var favFiles by remember { mutableStateOf(favDir.listFiles()?.toList().orEmpty()) }

    val viewMode = settings?.galleryViewMode ?: "grid"
    val columns = settings?.galleryColumns ?: 3

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                SegmentedControl(
                    tabs = listOf("全部", "云端", "本地"),
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.isRefreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding)
        ) {
            // 云端下载的档案列表
            val cloudItems = index.items.mapNotNull { it.metadata }
            
            // 合并列表用于“全部”页
            val allItems = (cloudItems + localArchives).sortedBy { it.title.lowercase() }

            val displayedItems = when (selectedTab) {
                0 -> allItems
                1 -> cloudItems
                2 -> localArchives
                else -> emptyList()
            }

            if (displayedItems.isEmpty() && favFiles.isEmpty()) {
                if (isScanning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("正在扫描本地档案…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    EmptyBox("暂无内容")
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (isScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (viewMode == "list") {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(displayedItems, key = { it.arcid }) { item ->
                                val isLocal = item.arcid.startsWith("local_")
                                ArchiveListRow(
                                    archive = item,
                                    onClick = { navController?.navigate(Routes.reader(item.arcid)) },
                                    isOffline = true,
                                    offlineCover = if (isLocal) {
                                        ArchivePageModel(Uri.parse(item.summary), 0, context)
                                    } else {
                                        container.offlineCache.coverFile(item.arcid)
                                    },
                                )
                            }
                            
                            if (selectedTab == 0 && favFiles.isNotEmpty()) {
                                item { FavoriteSection(favFiles, favDir) { favFiles = it } }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItems(displayedItems, key = { it.arcid }) { item ->
                                val isLocal = item.arcid.startsWith("local_")
                                ArchiveCard(
                                    archive = item,
                                    onClick = { navController?.navigate(Routes.reader(item.arcid)) },
                                    compact = viewMode == "compact",
                                    isOffline = true,
                                    offlineCover = if (isLocal) {
                                        ArchivePageModel(Uri.parse(item.summary), 0, context)
                                    } else {
                                        container.offlineCache.coverFile(item.arcid)
                                    },
                                )
                            }

                            if (selectedTab == 0 && favFiles.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                                    FavoriteSection(favFiles, favDir) { favFiles = it }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSection(favFiles: List<File>, favDir: File, onUpdate: (List<File>) -> Unit) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text("收藏图片（${favFiles.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(240.dp),
            userScrollEnabled = false,
        ) {
            gridItems(favFiles, key = { it.absolutePath }) { f ->
                Box(Modifier.aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))) {
                    AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    IconButton(
                        onClick = { f.delete(); onUpdate(favDir.listFiles()?.toList().orEmpty()) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Filled.Delete, "删除", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
