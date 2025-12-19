package org.dhis2.usescases.videoGuide.list.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import org.dhis2.R
import org.dhis2.usescases.videoGuide.list.VideoGuideViewModel
import org.dhis2.usescases.videoGuide.domain.model.VideoItem
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicator
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicatorType
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor

/**
 * プレースホルダー表示
 */
@Composable
fun ThumbnailPlaceholder(
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isError) {
                Icons.Default.BrokenImage
            } else {
                Icons.Default.VideoLibrary
            },
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * メタ情報を表示する小さなチップ
 */
@Composable
fun MetadataChip(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * サムネイル画像を表示するコンポーネント
 * ローディング、エラー、プレースホルダーを自動処理
 */
@Composable
fun VideoThumbnail(
    thumbnailUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = thumbnailUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    // ローディング中の表示
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                error = {
                    // エラー時の表示
                    ThumbnailPlaceholder(isError = true)
                }
            )
        } else {
            // サムネイルURLがnullの場合
            ThumbnailPlaceholder(isError = false)
        }
    }
}

@UnstableApi
@Composable
fun VideoGuideScreen(
    viewModel: VideoGuideViewModel,
    onVideoClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onCancelDownloadClick: (String) -> Unit,
) {
    val videoList by viewModel.videoList.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val downloadStates by viewModel.downloadStates.observeAsState(emptyMap())
    val downloadProgress by viewModel.downloadProgress.observeAsState(emptyMap())

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            ProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                type = ProgressIndicatorType.CIRCULAR,
            )
        } else {
            if (videoList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No videos available")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = videoList,
                        key = { video -> video.id }
                    ) { video ->
                        val downloadState = downloadStates[video.id]
                        val progress = downloadProgress[video.id] ?: 0
                        val isDownloaded = downloadState?.state == Download.STATE_COMPLETED
                        
                        VideoItemCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onDownloadClick = { onDownloadClick(video.id) },
                            onCancelDownloadClick = { onCancelDownloadClick(video.id) },
                            downloadState = downloadState,
                            downloadProgress = progress,
                            isDownloaded = isDownloaded,
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun VideoItemCard(
    video: VideoItem,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    downloadState: Download?,
    downloadProgress: Int,
    isDownloaded: Boolean,
) {
    val isDownloading = downloadState?.state == Download.STATE_DOWNLOADING || 
                        downloadState?.state == Download.STATE_QUEUED
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // サムネイル画像（左側）
                VideoThumbnail(
                    thumbnailUrl = video.thumbnailUrl,
                    title = video.title,
                    modifier = Modifier
                        .size(width = 120.dp, height = 90.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                
                // 動画情報（右側）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // タイトル
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // 説明
                    if (video.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // メタ情報（タグ、カテゴリ、再生時間）
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (video.duration != null) {
                            MetadataChip(
                                label = video.duration,
                                icon = Icons.Default.PlayCircle
                            )
                        }
                        if (video.category != null) {
                            MetadataChip(
                                label = video.category,
                                icon = Icons.Default.Folder
                            )
                        }
                    }
                }
            }
            
            // ダウンロード状態に応じたUI表示
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isDownloaded -> {
                        // ダウンロード済みバッジ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(R.string.video_downloaded),
                                tint = Color(0xFF4CAF50), // Success color (green)
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text(
                                text = stringResource(R.string.video_downloaded),
                                color = Color(0xFF4CAF50), // Success color (green)
                            )
                        }
                    }
                    isDownloading -> {
                        // ダウンロード中
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    type = ProgressIndicatorType.CIRCULAR_SMALL,
                                )
                                Text(
                                    text = stringResource(R.string.video_downloading),
                                )
                            }
                            if (downloadProgress > 0) {
                                Text(
                                    text = "${downloadProgress}%",
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Button(
                            style = ButtonStyle.TEXT,
                            text = stringResource(R.string.video_cancel_download),
                            onClick = onCancelDownloadClick,
                        )
                    }
                    else -> {
                        // 未ダウンロード
                        Button(
                            style = ButtonStyle.TONAL,
                            text = stringResource(R.string.video_download),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = stringResource(R.string.video_download),
                                    tint = SurfaceColor.Primary,
                                )
                            },
                            onClick = onDownloadClick,
                        )
                    }
                }
            }
        }
    }
}

