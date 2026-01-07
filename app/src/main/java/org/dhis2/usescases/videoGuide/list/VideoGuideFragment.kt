package org.dhis2.usescases.videoGuide.list

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.offline.Download
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.dhis2.R
import org.dhis2.usescases.general.FragmentGlobalAbstract
import org.dhis2.usescases.main.MainActivity
import org.dhis2.usescases.videoGuide.list.ui.VideoGuideScreen
import org.dhis2.usescases.videoGuide.player.VideoPlayerActivity
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import javax.inject.Inject

class VideoGuideFragment : FragmentGlobalAbstract() {

    @Inject
    lateinit var videoGuideViewModelFactory: VideoGuideViewModelFactory

    private val videoGuideViewModel: VideoGuideViewModel by viewModels {
        videoGuideViewModelFactory
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            context.mainComponent.plus(
                VideoGuideModule()
            ).inject(this)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                DHIS2Theme {
                    VideoGuideScreen(
                        viewModel = videoGuideViewModel,
                        onVideoClick = { videoId ->
                            handleVideoClick(videoId)
                        },
                        onDownloadClick = { videoId ->
                            videoGuideViewModel.startDownload(videoId)
                        },
                        onCancelDownloadClick = { videoId ->
                            videoGuideViewModel.cancelDownload(videoId)
                        }
                    )
                }
            }
        }
    }

    private fun handleVideoClick(videoId: String) {
        lifecycleScope.launch {
            // ダウンロード状態を確認
            val downloadState = videoGuideViewModel.getDownloadStateForVideo(videoId)
            val isDownloaded = downloadState?.state == Download.STATE_COMPLETED
            
            if (isDownloaded) {
                navigateToVideoPlayer(videoId)
            } else {
                // 未ダウンロードの場合は確認ダイアログを表示
                showDownloadConfirmationDialog(videoId)
            }
        }
    }

    private fun showDownloadConfirmationDialog(videoId: String) {
        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialDialog)
            .setTitle(getString(R.string.video_download_confirmation_title))
            .setMessage(getString(R.string.video_download_confirmation_message))
            .setPositiveButton(getString(R.string.video_download)) { _, _ ->
                videoGuideViewModel.startDownload(videoId)
            }
            .setNegativeButton(getString(R.string.video_cancel_download), null)
            .show()
    }

    private fun navigateToVideoPlayer(videoId: String) {
        VideoPlayerActivity.start(requireContext(), videoId)
    }
}

