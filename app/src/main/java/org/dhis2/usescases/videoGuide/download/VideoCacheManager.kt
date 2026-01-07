@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dhis2.usescases.videoGuide.download

import android.content.Context
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber
import java.io.File

/**
 * SimpleCacheのシングルトン管理クラス
 * 動画を永続化ストレージ（内部ストレージ）に保存
 */
object VideoCacheManager {
    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    /**
     * 永続化された動画保存用のディレクトリを取得
     * 内部ストレージを使用（権限不要、永続化、アンインストール時のみ削除）
     */
    private fun getVideoDownloadDirectory(context: Context): File {
        // context.filesDir を使用（永続化ストレージ）
        return File(context.filesDir, "video_downloads")
    }

    /**
     * ビデオダウンロードディレクトリのパスを取得（他のクラスから参照可能）
     */
    fun getVideoDownloadDirectoryPath(context: Context): String {
        return getVideoDownloadDirectory(context).absolutePath
    }

    @Synchronized
    fun getOrCreateSimpleCache(context: Context): SimpleCache {
        if (simpleCacheInstance == null) {
            val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
            val downloadDir = getVideoDownloadDirectory(context)
            
            // ディレクトリが存在しない場合は作成
            if (!downloadDir.exists()) {
                val created = downloadDir.mkdirs()
                Timber.d("Video download directory created: $created at ${downloadDir.absolutePath}")
            }
            
            simpleCacheInstance = SimpleCache(
                downloadDir,
                NoOpCacheEvictor(),
                databaseProvider
            )
            
            Timber.d("Video download directory: ${downloadDir.absolutePath}")
        }
        return simpleCacheInstance!!
    }

    /**
     * SimpleCacheインスタンスをリリース（テスト用など）
     */
    @Synchronized
    fun releaseCache() {
        simpleCacheInstance?.release()
        simpleCacheInstance = null
    }
}

