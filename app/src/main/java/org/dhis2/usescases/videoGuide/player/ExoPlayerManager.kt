@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dhis2.usescases.videoGuide.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.Download
import timber.log.Timber

/**
 * ExoPlayerインスタンスの管理
 * ダウンロード済みの動画は必ず内部ストレージから再生する
 * オンラインURLからは再生しない
 */
class ExoPlayerManager(
    private val context: Context,
    private val cache: SimpleCache,
    private val downloadManager: DownloadManager,
) {

    private var exoPlayer: ExoPlayer? = null
    private val httpDataSourceFactory: HttpDataSource.Factory

    init {
        // HTTPデータソースファクトリ（ダウンロード済みでない場合のみ使用）
        httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(context, "DHIS2-Android-Capture"))
            .setAllowCrossProtocolRedirects(true)
    }

    /**
     * ExoPlayerインスタンスを初期化
     * Media3のCacheDataSourceFactoryを使用して、SimpleCacheから自動的に読み込む
     */
    fun initializePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            // CacheDataSourceFactoryを作成（SimpleCacheを使用）
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            // DefaultDataSourceFactoryでCacheDataSourceFactoryをラップ
            val dataSourceFactory = DefaultDataSourceFactory(
                context,
                cacheDataSourceFactory
            )

            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(dataSourceFactory)
                )
                .build()
        }
        return exoPlayer!!
    }

    /**
     * メディアアイテムを準備
     * ダウンロード済みの動画は必ず内部ストレージから再生する
     * ダウンロード済みでない場合は再生しない
     * 
     * @param videoId 動画のID（ダウンロード済みかどうかを確認するため）
     * @throws IllegalStateException ダウンロード済みでない場合
     */
    fun prepareMediaItem(videoId: String) {
        val player = exoPlayer ?: initializePlayer()

        // ダウンロード状態を確認
        val download = try {
            downloadManager.downloadIndex.getDownload(videoId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get download state for video: $videoId")
            null
        }

        if (download == null || download.state != Download.STATE_COMPLETED) {
            throw IllegalStateException("Video is not downloaded: $videoId")
        }

        // ダウンロード済みの場合は、DownloadRequestからMediaItemを作成
        // これにより、確実に内部ストレージ（キャッシュ）から読み込まれる
        Timber.d("Preparing downloaded video from internal storage: $videoId")
        
        // DownloadRequestのURIを使用（内部ストレージから確実に読み込まれる）
        val mediaItem = MediaItem.fromUri(download.request.uri)
        
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    /**
     * ExoPlayerインスタンスを取得
     */
    fun getPlayer(): ExoPlayer? {
        return exoPlayer
    }

    /**
     * ExoPlayerインスタンスをリリース
     */
    fun releasePlayer() {
        exoPlayer?.let { player ->
            player.release()
            exoPlayer = null
            Timber.d("ExoPlayer released")
        }
    }
}

