package com.lanraragi.reader.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lanraragi.reader.data.ArchiveFileReader
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.edgeSwipeBack

@Composable
fun LocalDetailScreen(
    container: AppContainer,
    arcid: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val archive = remember(arcid) {
        container.localScanManager.localArchives.value.find { it.arcid == arcid }
    }
    var pageCount by remember(arcid) { mutableIntStateOf(0) }
    var loading by remember(arcid) { mutableStateOf(true) }

    LaunchedEffect(archive) {
        if (archive == null) {
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            pageCount = ArchiveFileReader.getImages(context, Uri.parse(archive.summary)).size
        }
        loading = false
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = archive?.title ?: "本地档案",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        if (archive == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("本地档案不存在或已被移除")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 220.dp, height = 310.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = com.lanraragi.reader.ui.screens.ArchivePageModel(Uri.parse(archive.summary), 0, context),
                    contentDescription = archive.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading) CircularProgressIndicator()
            }

            Spacer(Modifier.height(18.dp))
            Text(
                archive.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (pageCount > 0) "本地档案 · $pageCount 页" else "本地档案",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { navController.navigate(Routes.reader(arcid)) },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("开始阅读")
                }
            }
        }
    }
}