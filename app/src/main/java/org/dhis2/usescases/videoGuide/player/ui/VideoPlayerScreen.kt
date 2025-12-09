@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dhis2.usescases.videoGuide.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.ui.PlayerView
import org.dhis2.R
import org.dhis2.usescases.videoGuide.player.ExoPlayerManager
import org.dhis2.usescases.videoGuide.player.VideoPlayerViewModel
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicator
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicatorType

@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    exoPlayerManager: ExoPlayerManager,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val videoItem by viewModel.videoItem.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val downloadState by viewModel.downloadState.observeAsState()
    val downloadProgress by viewModel.downloadProgress.observeAsState(0)

    // ダウンロード状態を確認
    LaunchedEffect(videoItem) {
        videoItem?.let {
            viewModel.checkDownloadState()
        }
    }

    // ダウンロード済みかどうかを確認して再生
    var isDownloaded by remember { mutableStateOf(false) }
    LaunchedEffect(videoItem?.id, downloadState) {
        videoItem?.let { video ->
            isDownloaded = viewModel.isVideoDownloaded()
            if (isDownloaded) {
                exoPlayerManager.prepareMediaItem(video.videoUrl)
                exoPlayerManager.getPlayer()?.playWhenReady = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // PlayerView
        val player = remember { exoPlayerManager.initializePlayer() }
        
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerShowTimeoutMs = 3000
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // ダウンロード状態に応じて表示/非表示を切り替え
                val shouldShowPlayer = isDownloaded && 
                    (downloadState == null || downloadState?.state == Download.STATE_COMPLETED)
                view.visibility = if (shouldShowPlayer) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
            }
        )

        // ライフサイクル管理
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> player.play()
                    Lifecycle.Event.ON_DESTROY -> {
                        exoPlayerManager.releasePlayer()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // ローディングインジケーター
        if (isLoading) {
            ProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                type = ProgressIndicatorType.CIRCULAR,
            )
        }

        // エラーメッセージ
        errorMessage?.let { error ->
            Text(
                text = error,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }

        // ダウンロードUI
        videoItem?.let { video ->
            val isDownloading = downloadState?.state == Download.STATE_DOWNLOADING ||
                downloadState?.state == Download.STATE_QUEUED

            if (!isDownloaded) {
                DownloadUI(
                    downloadState = downloadState,
                    downloadProgress = downloadProgress,
                    isDownloading = isDownloading,
                    onDownloadClick = { viewModel.startDownload() },
                    onCancelDownloadClick = { viewModel.cancelDownload() },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // オフラインインジケーター
                Text(
                    text = stringResource(R.string.video_offline_available),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color(0x80000000))
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadUI(
    downloadState: Download?,
    downloadProgress: Int,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isDownloading -> {
                // ダウンロード中
                Text(
                    text = stringResource(R.string.video_downloading),
                    color = Color.White
                )
                if (downloadProgress > 0) {
                    LinearProgressIndicator(
                        progress = downloadProgress / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        backgroundColor = Color.Gray
                    )
                    Text(
                        text = "$downloadProgress%",
                        color = Color.White
                    )
                }
                Button(
                    style = ButtonStyle.TEXT,
                    text = stringResource(R.string.video_cancel_download),
                    onClick = onCancelDownloadClick
                )
            }
            downloadState?.state == Download.STATE_FAILED -> {
                // ダウンロード失敗
                Text(
                    text = stringResource(R.string.video_download_failed_retry),
                    color = Color.White
                )
                Button(
                    style = ButtonStyle.TONAL,
                    text = stringResource(R.string.video_download),
                    onClick = onDownloadClick
                )
            }
            else -> {
                // 未ダウンロード
                Text(
                    text = stringResource(R.string.video_download_required),
                    color = Color.White
                )
                Button(
                    style = ButtonStyle.TONAL,
                    text = stringResource(R.string.video_download),
                    onClick = onDownloadClick
                )
            }
        }
    }
}

