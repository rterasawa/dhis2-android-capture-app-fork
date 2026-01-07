# オフライン専用動画再生実装計画

## 📋 要件定義

### 主要要件

1. **ダウンロードされていない場合**
   - ユーザーに対してダウンロードを要求する
   - 再生を開始しない
   - ダウンロードボタンを表示する

2. **動画を再生する際**
   - 必ずダウンロードされた動画のみを再生する
   - オンラインストリーミングは行わない
   - ローカルキャッシュからの再生を保証する

---

## 🔍 現状分析

### ✅ 実装済みの機能

1. **ダウンロード基盤**
   - `VideoDownloadService`: バックグラウンドダウンロード
   - `VideoDownloadManager`: ダウンロード管理
   - `DownloadTracker`: ダウンロード状態監視
   - ExoPlayer Media3の`DownloadManager`統合

2. **Roomデータベース基盤** ✅
   - `DownloadedVideoEntity`: ダウンロード済み動画のエンティティ
   - `DownloadedVideoDao`: データベース操作のDAO
   - `VideoDatabase`: Roomデータベース
   - `VideoLocalDataSource`: ローカルデータソースインターフェース
   - `RoomVideoLocalDataSource`: Room実装
   - `VideoGuideRepository`: Roomと連携済み

3. **ダウンロード完了時のRoom保存** ✅
   - `VideoPlayerViewModel`でダウンロード完了時に自動的にRoomに保存
   - `repository.saveDownloadedVideo()`で永続化

4. **キャッシュ機能**
   - `SimpleCache`を使用したローカルキャッシュ
   - `ExoPlayerManager`でのキャッシュ統合

5. **UI実装**
   - ダウンロード状態表示
   - ダウンロード進捗バー
   - ダウンロードボタン/キャンセルボタン

### ⚠️ 現状の問題点

#### 問題1: ダウンロード状態に関わらず再生を試みる

**場所**: `VideoPlayerActivity.observeViewModel()`

```kotlin
// 現在の実装
viewModel.videoItem.observe(this) { videoItem ->
    videoItem?.let {
        playVideo(it.videoUrl)  // ← ダウンロード状態をチェックせずに再生
    }
}
```

**問題**:
- `videoItem`が取得できた時点ですぐに`playVideo()`を呼んでいる
- ダウンロード完了を確認していない
- オンラインURLから再生を試みる可能性がある

#### 問題2: PlayerViewの表示制御が不十分

**場所**: `VideoPlayerActivity.updateDownloadUI()`

```kotlin
// 現在の実装
when {
    downloadState == null -> {
        // Not downloaded
        downloadContainer?.visibility = View.VISIBLE
        // ← playerViewの表示制御がない
    }
}
```

**問題**:
- ダウンロードされていない状態でもPlayerViewが表示される
- ユーザーが再生しようとしてもエラーになるだけ

#### 問題3: ダウンロード完了後の自動再生がない

**問題**:
- ダウンロードが完了しても、ユーザーが画面を操作しないと再生が始まらない
- UXが悪い

#### 問題4: Roomを活用したダウンロード状態判定が不十分

**場所**: `VideoPlayerActivity.checkAndPlayIfDownloaded()`

**問題**:
- ExoPlayerの`Download.STATE_COMPLETED`のみを確認している
- Roomデータベースを信頼できる情報源として活用していない
- アプリ再起動後やExoPlayerの状態がリセットされた場合に不整合が発生する可能性

**解決策**:
- Roomデータベースを**信頼できる唯一の情報源（Single Source of Truth）**として使用
- ダウンロード済みかどうかの判定はRoomで確認
- ExoPlayerの状態はリアルタイムの進捗表示にのみ使用

---

## 🛠️ 修正実装計画

### 修正1: `VideoPlayerActivity.observeViewModel()` の変更

#### 目的
- ダウンロード済みの場合のみ再生を許可する
- ダウンロード状態を確認してから再生判断

#### 実装

```kotlin
private fun observeViewModel() {
    viewModel.videoItem.observe(this) { videoItem ->
        // videoItemが取得できても、すぐには再生しない
        // ダウンロード状態を確認してから判断
        videoItem?.let {
            checkAndPlayIfDownloaded()
        }
    }

    viewModel.isLoading.observe(this) { isLoading ->
        loadingIndicator?.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    viewModel.errorMessage.observe(this) { error ->
        error?.let {
            showError(it)
        } ?: run {
            errorMessage?.visibility = View.GONE
        }
    }
    
    // Download state observation
    viewModel.downloadState.observe(this) { downloadState ->
        updateDownloadUI(downloadState)
    }
    
    viewModel.downloadProgress.observe(this) { progress ->
        updateDownloadProgress(progress)
    }
}
```

### 修正2: `checkAndPlayIfDownloaded()` メソッドの追加

#### 目的
- **Roomデータベース**を信頼できる情報源として使用
- ダウンロード完了状態を確認してから再生する
- ダウンロードされていない場合は再生しない

#### 実装

```kotlin
/**
 * ダウンロード済みの場合のみ再生する
 * Roomデータベースを信頼できる情報源として使用
 */
private fun checkAndPlayIfDownloaded() {
    val video = viewModel.videoItem.value ?: return
    
    // Roomデータベースで確認（信頼できる情報源）
    viewModelScope.launch {
        val isDownloaded = viewModel.repository.isVideoDownloaded(video.id)
        
        if (isDownloaded) {
            // Roomに記録あり = ダウンロード済み → 再生を許可
            playVideo(video.videoUrl)
        } else {
            // Roomに記録なし = 未ダウンロード → 再生しない
            // UIでダウンロードを案内（updateDownloadUIで処理）
        }
    }
}
```

**重要**: 
- ExoPlayerの`Download.STATE_COMPLETED`はリアルタイムの状態表示に使用
- ダウンロード済みかどうかの**最終判定はRoomで行う**
- これにより、アプリ再起動後も正確に動作する

### 修正3: `updateDownloadUI()` の拡張

#### 目的
- ダウンロード状態に応じてPlayerViewの表示を制御
- **Roomデータベース**を確認してダウンロード済みかどうかを判定
- ダウンロード完了後に自動的に再生を開始

#### 実装

```kotlin
private fun updateDownloadUI(downloadState: Download?) {
    val video = viewModel.videoItem.value ?: return
    
    // Roomデータベースでダウンロード済みかどうかを確認
    viewModelScope.launch {
        val isDownloadedInRoom = viewModel.repository.isVideoDownloaded(video.id)
        
        when {
            // Roomに記録なし = 未ダウンロード
            !isDownloadedInRoom && (downloadState == null || 
                downloadState.state == Download.STATE_FAILED ||
                downloadState.state == Download.STATE_STOPPED) -> {
                // Not downloaded - 再生をブロック
                playerView?.visibility = View.GONE
                downloadContainer?.visibility = View.VISIBLE
                downloadStatusText?.text = getString(R.string.video_download_required)
                downloadStatusText?.visibility = View.VISIBLE
                downloadProgressBar?.visibility = View.GONE
                downloadButton?.visibility = View.VISIBLE
                cancelDownloadButton?.visibility = View.GONE
                offlineIndicator?.visibility = View.GONE
            }
            // ダウンロード中
            !isDownloadedInRoom && (downloadState?.state == Download.STATE_DOWNLOADING || 
                downloadState?.state == Download.STATE_QUEUED) -> {
                // Downloading - 再生をブロック
                playerView?.visibility = View.GONE
                downloadContainer?.visibility = View.VISIBLE
                downloadStatusText?.text = getString(R.string.video_downloading)
                downloadStatusText?.visibility = View.VISIBLE
                downloadProgressBar?.visibility = View.VISIBLE
                downloadButton?.visibility = View.GONE
                cancelDownloadButton?.visibility = View.VISIBLE
                offlineIndicator?.visibility = View.GONE
            }
            // Roomに記録あり = ダウンロード完了済み
            isDownloadedInRoom -> {
                // Downloaded - 再生を許可
                playerView?.visibility = View.VISIBLE
                downloadContainer?.visibility = View.GONE
                offlineIndicator?.visibility = View.VISIBLE
                
                // ダウンロード完了後に再生開始
                checkAndPlayIfDownloaded()
            }
            else -> {
                // Other states - 再生をブロック
                playerView?.visibility = View.GONE
                downloadContainer?.visibility = View.VISIBLE
                downloadStatusText?.text = getString(R.string.video_download_failed_retry)
                downloadStatusText?.visibility = View.VISIBLE
                downloadProgressBar?.visibility = View.GONE
                downloadButton?.visibility = View.VISIBLE
                cancelDownloadButton?.visibility = View.GONE
                offlineIndicator?.visibility = View.GONE
            }
        }
    }
}
```

**重要な変更点**:
- ExoPlayerの`downloadState`は進捗表示に使用
- **ダウンロード済みかどうかの判定はRoomで行う**
- Roomに記録があれば、ExoPlayerの状態に関わらず再生可能

---

## 📝 必要なリソース文字列

以下のリソース文字列が必要です（`strings.xml`に追加）：

```xml
<!-- 既存のリソース -->
<string name="video_download_required">ダウンロードが必要です</string>
<string name="video_downloading">ダウンロード中…</string>

<!-- 新規追加が必要なリソース -->
<string name="video_download_failed_retry">ダウンロードに失敗しました。もう一度お試しください</string>
<string name="please_download_video_first">動画を視聴するには、まずダウンロードしてください</string>
```

---

## 🎯 実装後の動作フロー

### シナリオ1: ダウンロード済み動画の再生

1. ユーザーが動画を選択
2. `VideoPlayerActivity`が起動
3. `loadVideo()`で動画情報を取得
4. `downloadState`が`STATE_COMPLETED`を確認
5. **PlayerViewを表示**
6. **ローカルキャッシュから再生開始**
7. オフラインインジケーター表示

### シナリオ2: 未ダウンロード動画の再生試行

1. ユーザーが動画を選択
2. `VideoPlayerActivity`が起動
3. `loadVideo()`で動画情報を取得
4. `downloadState`が`null`（未ダウンロード）
5. **PlayerViewを非表示**
6. **ダウンロードUIを表示**
7. "ダウンロードが必要です"メッセージ表示
8. ダウンロードボタン表示
9. ユーザーがダウンロードボタンをクリック
10. ダウンロード開始
11. 進捗表示
12. ダウンロード完了
13. **自動的にPlayerView表示**
14. **自動的に再生開始**

### シナリオ3: ダウンロード失敗

1. ダウンロード中にエラー発生
2. `downloadState.state`が`STATE_FAILED`
3. **PlayerViewを非表示**
4. "ダウンロードに失敗しました"メッセージ表示
5. 再試行ボタン表示

---

## ✅ 実装チェックリスト

### コード修正

- [ ] `VideoPlayerActivity.observeViewModel()` の修正
- [ ] `VideoPlayerActivity.checkAndPlayIfDownloaded()` メソッド追加（Room確認を追加）
- [ ] `VideoPlayerActivity.updateDownloadUI()` の拡張（Room確認を追加）
- [ ] `VideoPlayerViewModel.checkDownloadState()` でRoomも確認（オプション・推奨）

### リソース追加

- [ ] `strings.xml`に新しいメッセージ追加
  - [ ] `video_download_failed_retry`
  - [ ] `please_download_video_first`（必要に応じて）

### テスト項目

- [ ] **未ダウンロード動画**
  - [ ] PlayerViewが非表示になること
  - [ ] ダウンロードボタンが表示されること
  - [ ] 再生が開始されないこと

- [ ] **ダウンロード中**
  - [ ] PlayerViewが非表示のままであること
  - [ ] 進捗バーが表示されること
  - [ ] キャンセルボタンが表示されること

- [ ] **ダウンロード完了**
  - [ ] PlayerViewが表示されること
  - [ ] 自動的に再生が開始されること
  - [ ] オフラインインジケーターが表示されること
  - [ ] ローカルキャッシュから再生されること

- [ ] **ダウンロード失敗**
  - [ ] PlayerViewが非表示になること
  - [ ] エラーメッセージが表示されること
  - [ ] 再試行ボタンが表示されること

- [ ] **オフライン環境**
  - [ ] ダウンロード済み動画が再生できること
  - [ ] 未ダウンロード動画は再生できないこと

- [ ] **Roomデータベース連携**
  - [ ] ダウンロード完了時にRoomに保存されること
  - [ ] Roomに記録がある動画のみ再生できること
  - [ ] アプリ再起動後もRoomの記録が正しく反映されること
  - [ ] ExoPlayerの状態がリセットされても、Roomの記録で正しく動作すること

---

## 🔧 実装の影響範囲

### 変更が必要なファイル

1. **`VideoPlayerActivity.kt`** - メイン修正ファイル
   - `observeViewModel()` 修正
   - `checkAndPlayIfDownloaded()` 追加（Room確認を追加）
   - `updateDownloadUI()` 拡張（Room確認を追加）

2. **`VideoPlayerViewModel.kt`** - 軽微な修正（オプション）
   - `checkDownloadState()`でRoomも確認するように拡張（推奨）

3. **`app/src/main/res/values/strings.xml`** - リソース追加
   - 新しいメッセージ文字列追加

### 変更が不要なファイル（既存実装を活用）

- `VideoDownloadManager.kt` - 変更不要（Room保存は既に実装済み）
- `ExoPlayerManager.kt` - 変更不要
- `DownloadTracker.kt` - 変更不要
- `VideoDownloadService.kt` - 変更不要
- `VideoGuideRepository.kt` - 変更不要（Room連携は既に実装済み）
- `RoomVideoLocalDataSource.kt` - 変更不要

---

## 📊 技術的な詳細

### RoomデータベースとExoPlayerの役割分担

#### Roomデータベース（信頼できる情報源）
- **役割**: ダウンロード済み動画の**永続化された情報**を管理
- **用途**: 
  - ダウンロード済みかどうかの最終判定
  - アプリ再起動後の状態復元
  - ダウンロード済み動画一覧の取得
  - ファイルサイズ、ダウンロード日時などのメタデータ保存

#### ExoPlayer DownloadManager（リアルタイム状態管理）
- **役割**: ダウンロードの**リアルタイム状態**を管理
- **用途**:
  - ダウンロード中の進捗表示
  - ダウンロード開始・停止・キャンセル
  - ダウンロード完了の検知（その後Roomに保存）

#### データフロー

```
1. ユーザーがダウンロードボタンをクリック
   ↓
2. VideoDownloadManager.downloadVideo()
   ↓
3. ExoPlayer DownloadManager がダウンロード開始
   ↓
4. DownloadTracker が状態を監視
   ↓
5. STATE_COMPLETED を検知
   ↓
6. VideoPlayerViewModel が Room に保存
   ↓
7. repository.saveDownloadedVideo() → RoomVideoLocalDataSource
   ↓
8. DownloadedVideoEntity が Room DB に保存
   ↓
9. 以降、Room を信頼できる情報源として使用
```

### Media3のSimpleCacheの動作

現在の`ExoPlayerManager`では、`SimpleCache`を使用してダウンロード済みファイルを自動的に読み込みます：

```kotlin
// ExoPlayerManager.kt
val cacheDataSourceFactory = CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(httpDataSourceFactory)
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
```

**重要な点**：
- Media3では、常に**元のURL**を使用して再生する
- `SimpleCache`が自動的にキャッシュを確認する
- キャッシュにデータがあれば、それを使用する
- キャッシュになければ、オンラインから取得する（今回は防ぐ）

**オフライン専用再生の保証**：
- Roomに記録がある場合のみ再生を許可
- Roomに記録がなければ、SimpleCacheにデータがあっても再生しない
- これにより、意図しないオンラインストリーミングを防止

### ダウンロード状態の確認方法

#### 方法1: Roomデータベースで確認（推奨・信頼できる情報源）

```kotlin
// Repository経由で確認
val isDownloaded = repository.isVideoDownloaded(videoId)

if (isDownloaded) {
    // ダウンロード済み → 再生可能
} else {
    // 未ダウンロード → 再生不可
}
```

**メリット**:
- アプリ再起動後も正確に動作
- 永続化された情報を確認
- ExoPlayerの状態がリセットされても問題なし

#### 方法2: ExoPlayerのDownloadManagerで確認（リアルタイム進捗用）

```kotlin
val downloadState = downloadManager.getDownloadState(videoId)

when (downloadState?.state) {
    Download.STATE_COMPLETED -> // ダウンロード完了
    Download.STATE_DOWNLOADING -> // ダウンロード中（進捗表示用）
    Download.STATE_QUEUED -> // ダウンロード待機中
    Download.STATE_FAILED -> // ダウンロード失敗
    Download.STATE_STOPPED -> // ダウンロード停止
    Download.STATE_REMOVING -> // 削除中
    null -> // 未ダウンロード
}
```

**用途**:
- リアルタイムの進捗表示
- ダウンロード中のUI更新
- ダウンロード完了の検知（その後Roomに保存）

#### 推奨アプローチ: 両方を組み合わせる

```kotlin
// 1. Roomでダウンロード済みかどうかを確認（最終判定）
val isDownloaded = repository.isVideoDownloaded(videoId)

if (isDownloaded) {
    // ダウンロード済み → 再生可能
    playVideo(videoUrl)
} else {
    // 2. ExoPlayerでリアルタイム状態を確認（進捗表示用）
    val downloadState = downloadManager.getDownloadState(videoId)
    
    when (downloadState?.state) {
        Download.STATE_DOWNLOADING -> {
            // ダウンロード中 → 進捗表示
            showDownloadProgress(downloadState)
        }
        Download.STATE_FAILED -> {
            // ダウンロード失敗 → エラー表示
            showDownloadError()
        }
        else -> {
            // 未ダウンロード → ダウンロードボタン表示
            showDownloadButton()
        }
    }
}
```

---

## 🚀 実装の優先度

### 高優先度（必須）

1. ✅ `checkAndPlayIfDownloaded()` メソッド追加
2. ✅ `observeViewModel()` の修正
3. ✅ `updateDownloadUI()` でのPlayerView表示制御

### 中優先度（推奨）

4. ✅ ダウンロード完了後の自動再生
5. ✅ エラーメッセージの追加

### 低優先度（オプション）

6. ⬜ ダウンロード進捗の詳細表示（MB/総MB）
7. ⬜ ダウンロード速度の表示
8. ⬜ 推定残り時間の表示

---

## 📌 注意事項

### セキュリティ

- ダウンロードはHTTPS経由で行う
- ユーザーの同意を得てからダウンロードを開始する
- ストレージ容量を確認する

### パフォーマンス

- ダウンロード中はバックグラウンドで処理する
- UI更新は効率的に行う（過度な再描画を避ける）
- メモリリークに注意（Observer登録/解除を適切に行う）

### UX

- ダウンロードが必要な理由をユーザーに説明する
- ダウンロード進捗を視覚的に表示する
- ダウンロード完了後はスムーズに再生を開始する
- エラーが発生した場合は、わかりやすいメッセージを表示する

---

## 📅 実装スケジュール

### フェーズ1: コア機能実装（1-2時間）

- [ ] `VideoPlayerActivity.kt`の修正
- [ ] 基本的な動作確認

### フェーズ2: UI/UX改善（1時間）

- [ ] リソース文字列の追加
- [ ] エラーハンドリングの強化
- [ ] UI表示の調整

### フェーズ3: テストと検証（1-2時間）

- [ ] 各シナリオのテスト
- [ ] オフライン環境でのテスト
- [ ] バグ修正

---

## 🎉 期待される成果

この実装により、以下が実現されます：

1. ✅ **オフライン専用再生の保証**
   - ダウンロード済み動画のみ再生可能
   - オンラインストリーミングを防止
   - Roomデータベースを信頼できる情報源として使用

2. ✅ **データの永続化と整合性**
   - ダウンロード完了時にRoomに自動保存
   - アプリ再起動後も状態が保持される
   - ExoPlayerとRoomの状態が同期される

3. ✅ **明確なユーザーガイダンス**
   - ダウンロードが必要な場合は明確に案内
   - ダウンロード進捗を視覚的に表示
   - Roomの記録に基づいた正確な状態表示

4. ✅ **スムーズなUX**
   - ダウンロード完了後は自動的に再生開始
   - 不要な操作を削減
   - Roomの記録により、即座に再生可能かどうかを判定

5. ✅ **エラーハンドリング**
   - ダウンロード失敗時の適切な対応
   - 再試行オプションの提供
   - RoomとExoPlayerの状態不一致時の対処

---

## 📚 参考資料

- [ExoPlayer Media3 DownloadService](https://developer.android.com/guide/topics/media/exoplayer/downloading-media)
- [ExoPlayer Offline Playback](https://developer.android.com/guide/topics/media/exoplayer/offline)
- [SimpleCache Documentation](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/cache/SimpleCache.html)

---

---

## 📦 Roomデータベース活用の詳細

### 既存のRoom実装

#### Entity: `DownloadedVideoEntity`

```kotlin
@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val localFilePath: String,
    val downloadedAt: Long,
    val fileSize: Long,
    val duration: Long?,
    val tag: String?,
    val category: String?,
)
```

#### DAO: `DownloadedVideoDao`

```kotlin
@Dao
interface DownloadedVideoDao {
    @Query("SELECT * FROM downloaded_videos")
    suspend fun getAll(): List<DownloadedVideoEntity>
    
    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun getById(videoId: String): DownloadedVideoEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: DownloadedVideoEntity)
    
    @Query("DELETE FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)
}
```

#### Repository: `VideoGuideRepository`

```kotlin
suspend fun isVideoDownloaded(videoId: String): Boolean {
    return localDataSource.isDownloaded(videoId)
}

suspend fun saveDownloadedVideo(videoItem: VideoItem, localFilePath: String) {
    localDataSource.saveDownloadedVideo(videoItem, localFilePath)
}
```

### ダウンロード完了時のRoom保存処理

**場所**: `VideoPlayerViewModel.kt`

```kotlin
private val downloadStatesObserver = Observer<Map<String, Download>> { downloads ->
    downloads.values.forEach { download ->
        if (download.state == Download.STATE_COMPLETED) {
            viewModelScope.launch {
                val video = repository.getVideoById(download.request.id)
                if (video != null) {
                    val cachePath = File(context.cacheDir, "video_downloads").absolutePath
                    repository.saveDownloadedVideo(video, cachePath)  // ← Roomに保存
                    Timber.d("Download completed and saved to database: ${video.id}")
                }
            }
        }
    }
}
```

✅ **この処理は既に実装済み**

### Roomを活用した判定ロジック

#### 推奨パターン

```kotlin
// 1. Roomでダウンロード済みかどうかを確認（最終判定）
val isDownloaded = repository.isVideoDownloaded(videoId)

if (isDownloaded) {
    // ダウンロード済み → 再生可能
    playVideo(videoUrl)
} else {
    // 未ダウンロード → ダウンロードUIを表示
    showDownloadUI()
}
```

#### リアルタイム進捗表示との組み合わせ

```kotlin
// Roomでダウンロード済みかどうかを確認
val isDownloaded = repository.isVideoDownloaded(videoId)

if (isDownloaded) {
    // Roomに記録あり → 再生可能
    playVideo(videoUrl)
} else {
    // Roomに記録なし → ExoPlayerの状態で進捗表示
    val downloadState = downloadManager.getDownloadState(videoId)
    
    when (downloadState?.state) {
        Download.STATE_DOWNLOADING -> showProgress(downloadState)
        Download.STATE_FAILED -> showError()
        else -> showDownloadButton()
    }
}
```

---

**作成日**: 2024年12月6日  
**最終更新**: 2024年12月6日  
**ステータス**: 実装準備完了（Room活用を含む）

