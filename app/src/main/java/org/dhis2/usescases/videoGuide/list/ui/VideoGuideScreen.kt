package org.dhis2.usescases.videoGuide.list.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                    items(videoList) { video ->
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
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Column {
                Text(
                    text = video.title,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "ID: ${video.id}",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (video.description.isNotEmpty()) {
                    Text(
                        text = video.description,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (video.thumbnailUrl != null) {
                    Text(
                        text = "Thumbnail: ${video.thumbnailUrl}",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (video.tag != null) {
                    Text(
                        text = "Tag: ${video.tag}",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (video.category != null) {
                    Text(
                        text = "Category: ${video.category}",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = "URL: ${video.videoUrl}",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (video.duration != null) {
                    Text(text = "Duration: ${video.duration}")
                }
            }
        }
        
        // ダウンロード状態に応じたUI表示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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

