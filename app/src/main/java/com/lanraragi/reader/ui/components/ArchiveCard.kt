package com.lanraragi.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import com.lanraragi.reader.data.assets.CoverState
import com.lanraragi.reader.data.model.Archive
import com.lanraragi.reader.di.AppContainer
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
    thumbnailState: CoverState? = null,
    thumbnailContainer: AppContainer? = null,
) {
    if (thumbnailContainer != null && offlineCover == null && !isOffline && firstPageUrl == null) {
        RemoteThumbnailImage(
            container = thumbnailContainer,
            arcid = archive.arcid,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }
    val ready = when (val state = thumbnailState) {
        is CoverState.Ready -> state
        is CoverState.Generating -> state.lastGood
        is CoverState.Failed -> state.lastGood
        else -> null
    }
    val remoteRequest = ready?.resource?.value
    val waitingForRemote = thumbnailState != null &&
        offlineCover == null &&
        !isOffline &&
        firstPageUrl == null &&
        remoteRequest == null
    if (waitingForRemote) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = "封面生成中",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )
        }
    } else {
        LoadingImage(
            model = when {
                offlineCover != null -> offlineCover
                isOffline -> firstPageUrl // 离线但没传文件，回退
                firstPageUrl != null -> firstPageUrl
                remoteRequest != null -> remoteRequest
                else -> ApiClient.thumbnailUrl(archive.arcid)
            },
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
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
    thumbnailState: CoverState? = null,
    thumbnailContainer: AppContainer? = null,
    isCached: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    // D2 多选：选择模式显示勾选圈；非选择模式长按进入选择
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    LaunchedEffect(archive.arcid) { onRequestCover?.invoke() }
    Card(
        modifier = modifier.cardClick(
            onClick = onClick,
            onLongClick = if (selectionMode) null else onLongPress,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        if (compact) {
            CoverBox(
                archive,
                coverUrl,
                isOffline,
                offlineCover,
                isCached,
                isFavorite,
                onToggleFavorite,
                isPinned,
                onTogglePin,
                selectionMode,
                isSelected,
                thumbnailState,
                thumbnailContainer,
            )
        } else {
            Column {
                CoverBox(
                    archive,
                    coverUrl,
                    isOffline,
                    offlineCover,
                    isCached,
                    isFavorite,
                    onToggleFavorite,
                    isPinned,
                    onTogglePin,
                    selectionMode,
                    isSelected,
                    thumbnailState,
                    thumbnailContainer,
                )
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

/**
 * D2 卡片勾选圈（左上角）：选中实心主色 + 对勾，未选空心白圈。
 * 多选模式下取代 D1 的 CoverBadges（已缓存 / 收藏角标），避免左上角重叠。
 */
@Composable
private fun SelectionCheck(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Black.copy(alpha = 0.42f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector =
                if (isSelected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.RadioButtonUnchecked
                },
            contentDescription = if (isSelected) "已选择" else "未选择",
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    Color.White
                },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CoverBox(
    archive: Archive,
    coverUrl: String?,
    isOffline: Boolean,
    offlineCover: Any?,
    isCached: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    isPinned: Boolean,
    onTogglePin: (() -> Unit)?,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    thumbnailState: CoverState? = null,
    thumbnailContainer: AppContainer? = null,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
        ArchiveCover(
            archive = archive,
            modifier = Modifier.fillMaxSize(),
            firstPageUrl = coverUrl,
            isOffline = isOffline,
            offlineCover = offlineCover,
            thumbnailState = thumbnailState,
            thumbnailContainer = thumbnailContainer,
        )
        if (selectionMode) {
            SelectionCheck(
                isSelected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        } else {
            CoverBadges(
                isCached = isCached,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                isPinned = isPinned,
                onTogglePin = onTogglePin,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
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

/**
 * 封面左上角的角标叠加层：已缓存（下载完成小图标）+ 已收藏红心。
 * 红心始终展示（空心=未收藏，实心红=已收藏），点击只切换收藏，不触发整卡跳转。
 */
@Composable
private fun CoverBadges(
    isCached: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isCached) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = "已缓存",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (onTogglePin != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(onClick = onTogglePin)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = if (isPinned) "取消固定" else "固定保存资源",
                    tint = if (isPinned) MaterialTheme.colorScheme.tertiary else Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (onToggleFavorite != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(onClick = onToggleFavorite)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) Color(0xFFE53935) else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
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
    thumbnailState: CoverState? = null,
    thumbnailContainer: AppContainer? = null,
    isCached: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    // D2 多选：选择模式显示勾选圈；非选择模式长按进入选择
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    LaunchedEffect(archive.arcid) { onRequestCover?.invoke() }
    Card(
        modifier = modifier.cardClick(
            onClick = onClick,
            onLongClick = if (selectionMode) null else onLongPress,
        ),
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
                    thumbnailState = thumbnailState,
                    thumbnailContainer = thumbnailContainer,
                )
                if (selectionMode) {
                    SelectionCheck(
                        isSelected = isSelected,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    )
                } else {
                    CoverBadges(
                        isCached = isCached,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        isPinned = isPinned,
                        onTogglePin = onTogglePin,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    )
                }
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

/**
 * D2 卡片点击语义：单击与长按并存。
 * onLongClick 为空时退化为普通 clickable（不影响 DownloadScreen 既有外部长按用法）。
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.cardClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier =
    if (onLongClick != null) {
        combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        clickable(onClick = onClick)
    }
