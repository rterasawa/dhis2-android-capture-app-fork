package org.dhis2.usescases.videoGuide.player

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.dhis2.bindings.app
import org.dhis2.usescases.general.ActivityGlobalAbstract
import org.dhis2.usescases.videoGuide.player.ui.VideoPlayerScreen
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import javax.inject.Inject

class VideoPlayerActivity : ActivityGlobalAbstract() {

    companion object {
        private const val EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID"

        fun start(context: android.content.Context, videoId: String) {
            val intent = android.content.Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_ID, videoId)
            }
            context.startActivity(intent)
        }
    }

    lateinit var videoPlayerComponent: VideoPlayerComponent

    @Inject
    lateinit var videoPlayerViewModelFactory: VideoPlayerViewModelFactory

    @Inject
    lateinit var exoPlayerManager: ExoPlayerManager

    private val viewModel: VideoPlayerViewModel by viewModels { videoPlayerViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DI設定
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: run {
            finish()
            return
        }

        videoPlayerComponent = app().appComponent().plus(
            VideoPlayerModule(
                activity = this,
                viewModelStoreOwner = this,
            )
        )
        videoPlayerComponent.inject(this)

        // 動画情報の読み込み
        viewModel.loadVideo(videoId)

        setContent {
            DHIS2Theme {
                VideoPlayerScreen(
                    viewModel = viewModel,
                    exoPlayerManager = exoPlayerManager,
                    lifecycleOwner = this,
                )
            }
        }
    }
}

