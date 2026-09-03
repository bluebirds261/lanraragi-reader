package com.lanraragi.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.Archive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchiveCover(
    archive: Archive,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    firstPageUrl: String? = null,
    isOffline: Boolean = false,
    offlineCover: Any? = null,
) {
    LoadingImage(
        model = when {
            offlineCover != null -> offlineCover
            isOffline -> firstPageUrl // 离线但没传文件，回退
            else -> firstPageUrl ?: ApiClient.thumbnailUrl(archive.arcid)
        },
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/** 网格视图卡片：松散有标题（只显示标题），紧凑无文字。 */
@Composable
fun ArchiveCard(
    archive: Archive,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    onRequestCover: (() -> Unit)? = null,
    compact: Boolean = false,
    isOffline: Boolean = false,
    offlineCover: Any? = null,
) {
    LaunchedEffect(archive.arcid) { onRequestCover?.invoke() }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
    ) {
        if (compact) {
            CoverBox(archive, coverUrl, isOffline, offlineCover)
        } else {
            Column {
                CoverBox(archive, coverUrl, isOffline, offlineCover)
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(
                        text = archive.displayTitle.ifBlank { archive.arcid },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverBox(archive: Archive, coverUrl: String?, isOffline: Boolean, offlineCover: Any?) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
        ArchiveCover(
            archive = archive,
            modifier = Modifier.fillMaxSize(),
            firstPageUrl = coverUrl,
            isOffline = isOffline,
            offlineCover = offlineCover,
        )
        if (archive.isNew) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("新", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (archive.progress > 0 && archive.pagecount > 0) {
            LinearProgressIndicator(
                progress = { archive.progressPercent },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

/** 列表视图行：左侧封面大图 + 右侧标题。 */
@Composable
fun ArchiveListRow(
    archive: Archive,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    onRequestCover: (() -> Unit)? = null,
    isOffline: Boolean = false,
    offlineCover: Any? = null,
) {
    LaunchedEffect(archive.arcid) { onRequestCover?.invoke() }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(112.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                ArchiveCover(
                    archive = archive,
                    modifier = Modifier.fillMaxSize(),
                    firstPageUrl = coverUrl,
                    isOffline = isOffline,
                    offlineCover = offlineCover,
                )
                if (archive.isNew) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "新",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (archive.progress > 0 && archive.pagecount > 0) {
                    LinearProgressIndicator(
                        progress = { archive.progressPercent },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = archive.displayTitle.ifBlank { archive.arcid },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = remember(archive.dateadded) {
                        if (archive.dateadded > 0) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(archive.dateadded * 1000))
                        } else {
                            "添加日期未知"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (archive.pagecount > 0) "${archive.pagecount} 页" else "页数未知",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}
