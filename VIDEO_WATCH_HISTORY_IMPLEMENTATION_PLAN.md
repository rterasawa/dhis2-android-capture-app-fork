# 動画視聴履歴機能の実装計画

## 📋 要件

動画の視聴履歴を管理し、以下の機能を実現する：

1. ✅ **視聴履歴の記録**
   - どの動画を視聴したか
   - いつ視聴したか（初回視聴日時、最終視聴日時）
   - 何回視聴したか

2. ✅ **未視聴動画の優先表示**
   - ダウンロード済みの動画一覧で、未視聴の動画を優先的に表示
   - 視聴済みの動画は後ろに配置

3. ✅ **視聴状態の管理**
   - 最後に視聴した位置の記録（続きから再生用）
   - 完全視聴の記録

4. ✅ **ダウンロード情報との分離**
   - ダウンロード情報（`DownloadedVideoEntity`）と視聴履歴（`VideoWatchHistoryEntity`）を別々のテーブルで管理
   - 責務の明確化とデータの独立性を保証

---

## 🎯 設計方針

### 2つのテーブルで管理する理由

#### 1. 責務の分離（Single Responsibility Principle）

```
ダウンロード情報 ≠ 視聴履歴

例：
- ダウンロードしたけどまだ視聴していない
  → DownloadedVideoEntity: 存在する
  → VideoWatchHistoryEntity: 存在しない

- ダウンロードして1回視聴した
  → DownloadedVideoEntity: 1レコード
  → VideoWatchHistoryEntity: 1レコード（watchCount=1）

- ダウンロードして何度も視聴した
  → DownloadedVideoEntity: 1レコード（更新されない）
  → VideoWatchHistoryEntity: 1レコード（watchCountが増加）
```

#### 2. データのライフサイクルが異なる

| 情報 | 作成タイミング | 更新頻度 | 削除タイミング |
|------|---------------|---------|---------------|
| **ダウンロード情報** | ダウンロード完了時 | ほとんどない | ユーザーがダウンロードを削除した時 |
| **視聴履歴** | 初回視聴時 | 視聴するたびに頻繁に更新 | 履歴をクリアした時（ファイルは残す） |

#### 3. クエリの効率性

別々のテーブルにすることで、必要な情報だけを効率的に取得できる：

```kotlin
// 未視聴の動画を優先表示する場合
// → 視聴済みのvideoIdだけを取得（軽量なクエリ）
val watchedVideoIds = watchHistoryDao.getAllWatchedVideoIds().toSet()

// ダウンロード済みかどうかを確認する場合
// → ダウンロード情報だけを取得
val isDownloaded = downloadedVideoDao.getById(videoId) != null
```

#### 4. データの独立性

- ダウンロードを削除しても視聴履歴は残す（履歴として保持）
- 視聴履歴をクリアしてもダウンロードファイルは残す
- 各テーブルが独立して管理できる

---

## 📊 データモデル設計

### 既存テーブル：`DownloadedVideoEntity`

**役割**: ダウンロード情報の管理

```kotlin
@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val localFilePath: String,      // ダウンロード先のローカルパス
    val downloadedAt: Long,         // ダウンロード日時（ミリ秒）
    val fileSize: Long,             // ファイルサイズ（バイト）
    val duration: Long? = null,     // 動画の長さ（ミリ秒）
    val tag: String? = null,        // タグ
    val category: String? = null,   // カテゴリ
)
```

**保存される情報**:
- ✅ どの動画がダウンロード済みか
- ✅ ダウンロードしたファイルの場所
- ✅ いつダウンロードしたか
- ✅ ファイルサイズ

### 新規テーブル：`VideoWatchHistoryEntity`

**役割**: 視聴履歴の管理

```kotlin
@Entity(tableName = "video_watch_history")
data class VideoWatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val firstWatchedAt: Long,       // 初回視聴日時（ミリ秒）
    val lastWatchedAt: Long,        // 最終視聴日時（ミリ秒）
    val watchCount: Int = 1,         // 視聴回数
    val lastWatchProgress: Long? = null,  // 最後に視聴した位置（ミリ秒）- 続きから再生用
    val isCompleted: Boolean = false      // 完全に視聴したかどうか
)
```

**保存される情報**:
- ✅ どの動画を視聴したか（未視聴判定に使用）
- ✅ いつ視聴したか（初回・最終）
- ✅ 何回視聴したか
- ✅ 続きから再生する位置
- ✅ 完全視聴の記録

### データの関係性

```
動画一覧（Drupal API）
    ↓
    ├─ ダウンロード → DownloadedVideoEntity に保存
    │                  (videoId, localFilePath, downloadedAt, fileSize)
    │
    └─ 視聴 → VideoWatchHistoryEntity に保存
               (videoId, lastWatchedAt, watchCount, isCompleted)

例：
videoId: "123"
- DownloadedVideoEntity: あり → ダウンロード済み
- VideoWatchHistoryEntity: なし → 未視聴

videoId: "456"  
- DownloadedVideoEntity: あり → ダウンロード済み
- VideoWatchHistoryEntity: あり (watchCount=3) → 3回視聴済み

videoId: "789"
- DownloadedVideoEntity: なし → 未ダウンロード
- VideoWatchHistoryEntity: なし → 未視聴（そもそもダウンロードされていない）
```

---

## 🔧 実装手順

### ステップ1: 視聴履歴エンティティの作成

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/data/local/VideoWatchHistoryEntity.kt`

```kotlin
package org.dhis2.usescases.videoGuide.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_watch_history")
data class VideoWatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val firstWatchedAt: Long,       // 初回視聴日時（ミリ秒）
    val lastWatchedAt: Long,        // 最終視聴日時（ミリ秒）
    val watchCount: Int = 1,         // 視聴回数
    val lastWatchProgress: Long? = null,  // 最後に視聴した位置（ミリ秒）
    val isCompleted: Boolean = false      // 完全に視聴したかどうか
)
```

---

### ステップ2: DAOの作成

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/data/local/VideoWatchHistoryDao.kt`

```kotlin
package org.dhis2.usescases.videoGuide.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface VideoWatchHistoryDao {
    
    @Query("SELECT * FROM video_watch_history")
    suspend fun getAll(): List<VideoWatchHistoryEntity>
    
    @Query("SELECT * FROM video_watch_history WHERE videoId = :videoId")
    suspend fun getByVideoId(videoId: String): VideoWatchHistoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: VideoWatchHistoryEntity)
    
    @Update
    suspend fun update(history: VideoWatchHistoryEntity)
    
    @Query("SELECT videoId FROM video_watch_history")
    suspend fun getAllWatchedVideoIds(): List<String>
    
    @Query("DELETE FROM video_watch_history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
    
    @Query("DELETE FROM video_watch_history")
    suspend fun deleteAll()
}
```

---

### ステップ3: VideoDatabaseの更新

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/data/local/VideoDatabase.kt`

```kotlin
package org.dhis2.usescases.videoGuide.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadedVideoEntity::class,
        VideoWatchHistoryEntity::class  // 追加
    ],
    version = 2,  // バージョンアップ（1 → 2）
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun downloadedVideoDao(): DownloadedVideoDao
    abstract fun videoWatchHistoryDao(): VideoWatchHistoryDao  // 追加
    
    companion object {
        /**
         * バージョン1から2へのマイグレーション
         * video_watch_historyテーブルを追加
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS video_watch_history (
                        videoId TEXT NOT NULL PRIMARY KEY,
                        firstWatchedAt INTEGER NOT NULL,
                        lastWatchedAt INTEGER NOT NULL,
                        watchCount INTEGER NOT NULL,
                        lastWatchProgress INTEGER,
                        isCompleted INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
```

**注意**: Roomのマイグレーションを追加する必要があります。`Room.databaseBuilder()`に`.addMigrations()`を追加してください。

---

### ステップ4: Repositoryに視聴履歴メソッドを追加

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/VideoGuideRepository.kt`

```kotlin
package org.dhis2.usescases.videoGuide

import org.dhis2.usescases.videoGuide.data.datasource.VideoLocalDataSource
import org.dhis2.usescases.videoGuide.data.datasource.VideoRemoteDataSource
import org.dhis2.usescases.videoGuide.data.local.VideoWatchHistoryDao
import org.dhis2.usescases.videoGuide.data.local.VideoWatchHistoryEntity
import org.dhis2.usescases.videoGuide.domain.model.VideoItem
import javax.inject.Inject

class VideoGuideRepository @Inject constructor(
    private val dataSource: VideoRemoteDataSource,
    private val localDataSource: VideoLocalDataSource,
    private val watchHistoryDao: VideoWatchHistoryDao,  // 追加
) {
    suspend fun getVideoList(): List<VideoItem> {
        return dataSource.getVideoList()
    }

    suspend fun getVideoById(videoId: String): VideoItem? {
        // まずローカルDBから取得を試みる
        val localVideo = localDataSource.getDownloadedVideoById(videoId)
        if (localVideo != null) {
            return localVideo
        }
        // ローカルにない場合はリモートから取得
        return dataSource.getVideoById(videoId)
    }

    suspend fun getDownloadedVideoById(videoId: String): VideoItem? {
        return localDataSource.getDownloadedVideoById(videoId)
    }

    suspend fun getDownloadedVideoList(): List<VideoItem> {
        return localDataSource.getAllDownloadedVideos()
    }

    suspend fun isVideoDownloaded(videoId: String): Boolean {
        return localDataSource.isDownloaded(videoId)
    }

    suspend fun getLocalFilePath(videoId: String): String? {
        return localDataSource.getLocalFilePath(videoId)
    }

    suspend fun saveDownloadedVideo(videoItem: VideoItem, localFilePath: String) {
        localDataSource.saveDownloadedVideo(videoItem, localFilePath)
    }
    
    // ========== 視聴履歴関連のメソッド ==========
    
    /**
     * 視聴履歴を記録
     * @param videoId 動画ID
     * @param watchProgress 視聴位置（ミリ秒）、nullの場合は更新しない
     * @param isCompleted 完全視聴したかどうか
     */
    suspend fun recordWatchHistory(
        videoId: String,
        watchProgress: Long? = null,
        isCompleted: Boolean = false
    ) {
        val existing = watchHistoryDao.getByVideoId(videoId)
        val now = System.currentTimeMillis()
        
        if (existing != null) {
            // 既存の履歴を更新
            watchHistoryDao.update(
                existing.copy(
                    lastWatchedAt = now,
                    watchCount = existing.watchCount + 1,
                    lastWatchProgress = watchProgress ?: existing.lastWatchProgress,
                    isCompleted = isCompleted || existing.isCompleted
                )
            )
        } else {
            // 新規の履歴を作成
            watchHistoryDao.insert(
                VideoWatchHistoryEntity(
                    videoId = videoId,
                    firstWatchedAt = now,
                    lastWatchedAt = now,
                    watchCount = 1,
                    lastWatchProgress = watchProgress,
                    isCompleted = isCompleted
                )
            )
        }
    }
    
    /**
     * 視聴履歴を取得
     */
    suspend fun getWatchHistory(videoId: String): VideoWatchHistoryEntity? {
        return watchHistoryDao.getByVideoId(videoId)
    }
    
    /**
     * ダウンロード済みの動画一覧を、未視聴を優先して取得
     */
    suspend fun getDownloadedVideosWithUnwatchedFirst(): List<VideoItem> {
        // 1. ダウンロード済みの動画を取得
        val downloadedVideos = localDataSource.getAllDownloadedVideos()
        
        // 2. 視聴済みのvideoIdを取得
        val watchedVideoIds = watchHistoryDao.getAllWatchedVideoIds().toSet()
        
        // 3. 未視聴と視聴済みに分ける
        val unwatchedVideos = downloadedVideos.filter { it.id !in watchedVideoIds }
        val watchedVideos = downloadedVideos.filter { it.id in watchedVideoIds }
        
        // 4. 未視聴を先に、視聴済みを後ろに配置
        return unwatchedVideos + watchedVideos
    }
    
    /**
     * 視聴履歴付きの動画情報を取得
     */
    data class VideoWithHistory(
        val video: VideoItem,
        val history: VideoWatchHistoryEntity?
    )
    
    /**
     * 視聴ステータス付きの動画情報（UI表示用）
     * 視聴履歴から視聴済み/未視聴を判定済みのデータクラス
     */
    data class VideoWithWatchStatus(
        val video: VideoItem,
        val watchHistory: VideoWatchHistoryEntity?,
        val isWatched: Boolean,           // 視聴済みかどうか
        val isCompleted: Boolean,         // 完全視聴かどうか
        val watchCount: Int,              // 視聴回数
        val lastWatchProgress: Long?      // 最後の視聴位置
    ) {
        companion object {
            fun from(video: VideoItem, history: VideoWatchHistoryEntity?): VideoWithWatchStatus {
                return VideoWithWatchStatus(
                    video = video,
                    watchHistory = history,
                    isWatched = history != null,
                    isCompleted = history?.isCompleted ?: false,
                    watchCount = history?.watchCount ?: 0,
                    lastWatchProgress = history?.lastWatchProgress
                )
            }
        }
    }
    
    /**
     * ダウンロード済み動画を視聴ステータス付きで取得（UI表示用）
     */
    suspend fun getDownloadedVideosWithWatchStatus(): List<VideoWithWatchStatus> {
        val downloadedVideos = localDataSource.getAllDownloadedVideos()
        val allHistories = watchHistoryDao.getAll().associateBy { it.videoId }
        
        return downloadedVideos.map { video ->
            val history = allHistories[video.id]
            VideoWithWatchStatus.from(video, history)
        }
    }
    
    /**
     * ダウンロード済み動画を視聴ステータス付きで取得し、未視聴を優先表示
     */
    suspend fun getDownloadedVideosWithWatchStatusUnwatchedFirst(): List<VideoWithWatchStatus> {
        val videosWithStatus = getDownloadedVideosWithWatchStatus()
        
        // 未視聴と視聴済みに分ける
        val unwatched = videosWithStatus.filter { !it.isWatched }
        val watched = videosWithStatus.filter { it.isWatched }
        
        // 未視聴を先に、視聴済みを後ろに配置
        return unwatched + watched
    }
    
    /**
     * ダウンロード済み動画を視聴履歴付きで取得
     */
    suspend fun getDownloadedVideosWithHistory(): List<VideoWithHistory> {
        val downloadedVideos = localDataSource.getAllDownloadedVideos()
        return downloadedVideos.map { video ->
            VideoWithHistory(
                video = video,
                history = watchHistoryDao.getByVideoId(video.id)
            )
        }
    }
    
    /**
     * 視聴履歴を削除（動画ファイルは残す）
     */
    suspend fun deleteWatchHistory(videoId: String) {
        watchHistoryDao.deleteByVideoId(videoId)
    }
    
    /**
     * すべての視聴履歴を削除
     */
    suspend fun deleteAllWatchHistory() {
        watchHistoryDao.deleteAll()
    }
}
```

---

### ステップ5: DIモジュールの更新

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/list/VideoGuideModule.kt`

```kotlin
// 既存のコードに追加

@Provides
@PerFragment
fun provideVideoWatchHistoryDao(database: VideoDatabase): VideoWatchHistoryDao {
    return database.videoWatchHistoryDao()
}

// VideoDatabaseの提供時にマイグレーションを追加
@Provides
@PerFragment
fun provideVideoDatabase(context: Context): VideoDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        VideoDatabase::class.java,
        "video_database"
    )
    .addMigrations(VideoDatabase.MIGRATION_1_2)  // マイグレーション追加
    .build()
}
```

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/player/VideoPlayerModule.kt`

同様に、`VideoPlayerModule`にも追加：

```kotlin
@Provides
@PerActivity
fun provideVideoWatchHistoryDao(database: VideoDatabase): VideoWatchHistoryDao {
    return database.videoWatchHistoryDao()
}

@Provides
@PerActivity
fun provideVideoDatabase(): VideoDatabase {
    return Room.databaseBuilder(
        activity.applicationContext,
        VideoDatabase::class.java,
        "video_database"
    )
    .addMigrations(VideoDatabase.MIGRATION_1_2)  // マイグレーション追加
    .build()
}
```

---

### ステップ6: ViewModelで視聴履歴を記録

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/player/VideoPlayerViewModel.kt`

```kotlin
// 既存のコードに追加

class VideoPlayerViewModel @Inject constructor(
    private val repository: VideoGuideRepository,
    // ... 既存のコード ...
) : ViewModel() {
    
    // ... 既存のコード ...
    
    /**
     * 動画再生開始時に呼び出す
     */
    fun onVideoStarted(videoId: String) {
        viewModelScope.launch {
            try {
                repository.recordWatchHistory(videoId)
                Timber.d("Recorded watch history for video: $videoId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to record watch history")
            }
        }
    }
    
    /**
     * 動画再生中に定期的に呼び出す（再生位置を保存）
     * 例：5秒ごと、または再生位置が10%進むごと
     */
    fun onVideoProgressUpdate(videoId: String, currentPosition: Long) {
        viewModelScope.launch {
            try {
                repository.recordWatchHistory(
                    videoId = videoId,
                    watchProgress = currentPosition,
                    isCompleted = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to update watch progress")
            }
        }
    }
    
    /**
     * 動画を最後まで視聴したときに呼び出す
     */
    fun onVideoCompleted(videoId: String) {
        viewModelScope.launch {
            try {
                repository.recordWatchHistory(
                    videoId = videoId,
                    isCompleted = true
                )
                Timber.d("Marked video as completed: $videoId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark video as completed")
            }
        }
    }
}
```

---

### ステップ7: 一覧画面で未視聴優先表示とUI表示

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/list/VideoGuideViewModel.kt`

```kotlin
// 既存のコードに追加

class VideoGuideViewModel @Inject constructor(
    private val repository: VideoGuideRepository
) : ViewModel() {
    
    private val _videos = MutableLiveData<List<VideoItem>>()
    val videos: LiveData<List<VideoItem>> = _videos
    
    // 視聴ステータス付きの動画リスト（UI表示用）
    private val _videosWithStatus = MutableLiveData<List<VideoGuideRepository.VideoWithWatchStatus>>()
    val videosWithStatus: LiveData<List<VideoGuideRepository.VideoWithWatchStatus>> = _videosWithStatus
    
    // ... 既存のコード ...
    
    /**
     * ダウンロード済み動画を未視聴優先で読み込む（視聴ステータス付き）
     */
    fun loadDownloadedVideosWithUnwatchedFirst() {
        viewModelScope.launch {
            try {
                val videosWithStatus = repository.getDownloadedVideosWithWatchStatusUnwatchedFirst()
                _videosWithStatus.value = videosWithStatus
                // 後方互換性のため、videoListも更新
                _videos.value = videosWithStatus.map { it.video }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load videos with unwatched first")
            }
        }
    }
}
```

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/list/ui/VideoGuideScreen.kt`

```kotlin
@Composable
fun VideoGuideScreen(
    viewModel: VideoGuideViewModel,
    onVideoClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onCancelDownloadClick: (String) -> Unit,
) {
    val videosWithStatus by viewModel.videosWithStatus.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val downloadStates by viewModel.downloadStates.observeAsState(emptyMap())
    val downloadProgress by viewModel.downloadProgress.observeAsState(emptyMap())

    // ... 既存のコード ...
    
    items(videosWithStatus) { videoWithStatus ->
        val video = videoWithStatus.video
        val downloadState = downloadStates[video.id]
        val progress = downloadProgress[video.id] ?: 0
        val isDownloaded = downloadState?.state == Download.STATE_COMPLETED
        
        VideoItemCard(
            video = video,
            isWatched = videoWithStatus.isWatched,
            isCompleted = videoWithStatus.isCompleted,
            watchCount = videoWithStatus.watchCount,
            onClick = { onVideoClick(video.id) },
            onDownloadClick = { onDownloadClick(video.id) },
            onCancelDownloadClick = { onCancelDownloadClick(video.id) },
            downloadState = downloadState,
            downloadProgress = progress,
            isDownloaded = isDownloaded,
        )
    }
}

@Composable
fun VideoItemCard(
    video: VideoItem,
    isWatched: Boolean,
    isCompleted: Boolean,
    watchCount: Int,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    downloadState: Download?,
    downloadProgress: Int,
    isDownloaded: Boolean,
) {
    // ... 既存のコード ...
    
    Column {
        // タイトルと視聴ステータスを横並び
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = video.title,
                modifier = Modifier.weight(1f).padding(bottom = 4.dp),
            )
            
            // 視聴ステータスバッジ
            WatchStatusBadge(
                isWatched = isWatched,
                isCompleted = isCompleted,
                watchCount = watchCount,
            )
        }
        
        // 既存の情報表示
        // ...
    }
}

@Composable
fun WatchStatusBadge(
    isWatched: Boolean,
    isCompleted: Boolean,
    watchCount: Int,
) {
    when {
        !isWatched -> {
            // 未視聴バッジ
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = "未視聴",
                    tint = Color(0xFF2196F3), // Blue
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = "未視聴",
                    color = Color(0xFF2196F3),
                    fontSize = 12.sp,
                )
            }
        }
        isCompleted -> {
            // 完全視聴済みバッジ
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "視聴済み",
                    tint = Color(0xFF4CAF50), // Green
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = if (watchCount > 1) "視聴済み($watchCount回)" else "視聴済み",
                    color = Color(0xFF4CAF50),
                    fontSize = 12.sp,
                )
            }
        }
        else -> {
            // 途中まで視聴
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircleOutline,
                    contentDescription = "途中まで視聴",
                    tint = Color(0xFFFF9800), // Orange
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = if (watchCount > 1) "途中($watchCount回)" else "途中",
                    color = Color(0xFFFF9800),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
```

---

### ステップ8: VideoPlayerActivityで視聴履歴を記録

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/player/VideoPlayerActivity.kt`

```kotlin
// 既存のコードに追加

class VideoPlayerActivity : AppCompatActivity() {
    
    // ... 既存のコード ...
    
    private fun observeViewModel() {
        // ... 既存のコード ...
        
        // 動画再生開始時に視聴履歴を記録
        exoPlayerManager.player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // 再生開始時
                        viewModel.videoItem.value?.let { video ->
                            viewModel.onVideoStarted(video.id)
                        }
                    }
                    Player.STATE_ENDED -> {
                        // 再生完了時
                        viewModel.videoItem.value?.let { video ->
                            viewModel.onVideoCompleted(video.id)
                        }
                    }
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // 再生位置が変わったとき（シーク時など）
            }
        })
        
        // 定期的に再生位置を更新（例：5秒ごと）
        exoPlayerManager.player?.let { player ->
            // HandlerやCoroutineで定期的に呼び出す
            // 例：5秒ごとに onVideoProgressUpdate() を呼び出す
        }
    }
}
```

---

## 📊 データフロー

### 1. 動画ダウンロード時

```
ユーザーがダウンロードボタンをクリック
    ↓
ExoPlayer DownloadManager がダウンロード開始
    ↓
ダウンロード完了
    ↓
VideoPlayerViewModel が検知
    ↓
repository.saveDownloadedVideo()
    ↓
DownloadedVideoEntity が Room DB に保存
    ↓
✅ ダウンロード情報が記録される
```

### 2. 動画視聴時

```
ユーザーが動画を再生
    ↓
VideoPlayerActivity が再生開始を検知
    ↓
viewModel.onVideoStarted(videoId)
    ↓
repository.recordWatchHistory(videoId)
    ↓
VideoWatchHistoryEntity が Room DB に保存/更新
    ↓
✅ 視聴履歴が記録される
```

### 3. 未視聴動画の優先表示

```
ユーザーが動画一覧を開く
    ↓
viewModel.loadDownloadedVideosWithUnwatchedFirst()
    ↓
repository.getDownloadedVideosWithUnwatchedFirst()
    ↓
1. localDataSource.getAllDownloadedVideos() → ダウンロード済み動画を取得
2. watchHistoryDao.getAllWatchedVideoIds() → 視聴済みのvideoIdを取得
    ↓
未視聴と視聴済みに分ける
    ↓
未視聴を先に、視聴済みを後ろに配置
    ↓
✅ 未視聴動画が優先的に表示される
```

---

## ✅ 実装チェックリスト

### 必須実装

- [ ] `VideoWatchHistoryEntity.kt`の作成
  - [ ] エンティティクラスの定義
  - [ ] 必要なフィールドの追加

- [ ] `VideoWatchHistoryDao.kt`の作成
  - [ ] DAOインターフェースの定義
  - [ ] 必要なクエリメソッドの追加

- [ ] `VideoDatabase.kt`の更新
  - [ ] `VideoWatchHistoryEntity`をエンティティに追加
  - [ ] バージョンを1→2に更新
  - [ ] `videoWatchHistoryDao()`メソッドの追加
  - [ ] マイグレーション（`MIGRATION_1_2`）の追加

- [ ] `VideoGuideRepository.kt`の更新
  - [ ] `VideoWatchHistoryDao`の注入
  - [ ] `recordWatchHistory()`メソッドの追加
  - [ ] `getWatchHistory()`メソッドの追加
  - [ ] `getDownloadedVideosWithUnwatchedFirst()`メソッドの追加
  - [ ] `getDownloadedVideosWithHistory()`メソッドの追加

- [ ] DIモジュールの更新
  - [ ] `VideoGuideModule.kt`に`provideVideoWatchHistoryDao()`を追加
  - [ ] `VideoPlayerModule.kt`に`provideVideoWatchHistoryDao()`を追加
  - [ ] `VideoDatabase`の提供時にマイグレーションを追加

- [ ] `VideoPlayerViewModel.kt`の更新
  - [ ] `onVideoStarted()`メソッドの追加
  - [ ] `onVideoProgressUpdate()`メソッドの追加
  - [ ] `onVideoCompleted()`メソッドの追加

- [ ] `VideoPlayerActivity.kt`の更新
  - [ ] 再生開始時の視聴履歴記録
  - [ ] 再生完了時の視聴履歴記録
  - [ ] 再生位置の定期更新（オプション）

- [ ] `VideoGuideViewModel.kt`の更新
  - [ ] `loadDownloadedVideosWithUnwatchedFirst()`メソッドの追加
  - [ ] `VideoWithWatchStatus`を使用した視聴ステータス管理
  - [ ] 一覧画面で未視聴優先表示を実装

- [ ] `VideoGuideScreen.kt`の更新（UI表示）
  - [ ] `VideoWithWatchStatus`を受け取るように変更
  - [ ] `WatchStatusBadge`コンポーネントの追加
  - [ ] 視聴ステータスに応じたバッジ表示

### 推奨実装（オプション）

- [ ] UI改善の詳細
  - [ ] 未視聴動画にバッジを表示（青）
  - [ ] 視聴済み動画にバッジを表示（緑）
  - [ ] 途中まで視聴の動画にバッジを表示（オレンジ）
  - [ ] 視聴回数の表示
  - [ ] 視聴済み動画のグレーアウト（オプション）

- [ ] 続きから再生機能
  - [ ] `lastWatchProgress`を使用した再生位置の復元
  - [ ] 「続きから再生」ボタンの表示

- [ ] 視聴履歴管理機能
  - [ ] 視聴履歴のクリア機能
  - [ ] 視聴履歴の一覧表示

---

## 📌 注意事項

### 1. マイグレーション

既存のユーザーが既に`VideoDatabase`を使用している場合、マイグレーションが必要です。

```kotlin
// VideoDatabase.kt
companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS video_watch_history (
                    videoId TEXT NOT NULL PRIMARY KEY,
                    firstWatchedAt INTEGER NOT NULL,
                    lastWatchedAt INTEGER NOT NULL,
                    watchCount INTEGER NOT NULL,
                    lastWatchProgress INTEGER,
                    isCompleted INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
}
```

### 2. パフォーマンス

視聴履歴の更新は頻繁に行われる可能性があるため、以下の点に注意：

- **バッチ更新**: 再生位置の更新は5秒ごとなど、適度な間隔で行う
- **非同期処理**: Roomの操作はsuspend関数で非同期に実行
- **インデックス**: 必要に応じて`videoId`にインデックスを追加（Roomが自動で追加）

### 3. データの整合性

- ダウンロードを削除しても視聴履歴は残す（履歴として保持）
- 視聴履歴をクリアしてもダウンロードファイルは残す
- `videoId`をキーとして両テーブルを関連付ける

### 4. テスト

以下のシナリオでテストすることを推奨：

- ✅ ダウンロード済みで未視聴の動画が優先表示されること
- ✅ 視聴開始時に履歴が記録されること
- ✅ 視聴完了時に`isCompleted`が`true`になること
- ✅ 複数回視聴した場合に`watchCount`が増加すること
- ✅ マイグレーションが正常に動作すること

---

## 🎉 期待される成果

この実装により、以下が実現されます：

1. ✅ **視聴履歴の記録**
   - どの動画を視聴したかが記録される
   - 視聴回数や視聴日時が管理される

2. ✅ **未視聴動画の優先表示**
   - ダウンロード済みで未視聴の動画が一覧の上部に表示される
   - ユーザーが次に見るべき動画が明確になる

3. ✅ **データの独立性**
   - ダウンロード情報と視聴履歴が独立して管理される
   - 各テーブルが独立して更新・削除できる

4. ✅ **拡張性**
   - 続きから再生機能の実装が容易
   - 視聴統計機能の追加が容易

---

## 📚 参考資料

- [Room Database](https://developer.android.com/training/data-storage/room)
- [Room Migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Single Responsibility Principle](https://en.wikipedia.org/wiki/Single-responsibility_principle)

---

**作成日**: 2024年12月  
**ステータス**: 実装準備完了  
**優先度**: 中（UX向上のため）

