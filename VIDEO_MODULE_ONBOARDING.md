# 動画再生モジュール - 開発者ガイド

## 📋 目次

1. [目的](#目的)
2. [アーキテクチャ](#アーキテクチャ)
3. [ディレクトリ構造](#ディレクトリ構造)
4. [各ファイルの役割](#各ファイルの役割)
5. [使用ライブラリ](#使用ライブラリ)
6. [データフロー](#データフロー)
7. [重要なポイント](#重要なポイント)
8. [開発時の注意点](#開発時の注意点)
9. [よくある質問](#よくある質問)
10. [参考ドキュメント](#参考ドキュメント)
11. [次のステップ](#次のステップ)

---

## 🎯 目的

このモジュールは、DHIS2 Androidアプリに**動画チュートリアル機能**を提供します。

### 主な機能

- ✅ **動画一覧表示**: Drupal APIから動画情報を取得して表示
- ✅ **動画ダウンロード**: ExoPlayerを使用してオフライン視聴用に動画を保存
- ✅ **オフライン再生**: ダウンロード済み動画を内部ストレージから再生
- 🔄 **視聴履歴管理**: 視聴状況の記録と未視聴動画の優先表示（計画中）

### 技術スタック

- **MVVMパターン** + **クリーンアーキテクチャ**
- **ExoPlayer (Media3)** - 動画再生・ダウンロード
- **Room Database** - ローカルデータ永続化
- **Retrofit + Moshi** - API通信
- **Jetpack Compose** - UI実装
- **Dagger 2** - 依存性注入

---

## 🏗️ アーキテクチャ

### レイヤー構造

```
┌─────────────────────────────────────┐
│  Presentation Layer                 │
│  - Fragment (Compose UI)             │
│  - Activity (動画再生)               │
├─────────────────────────────────────┤
│  ViewModel Layer                    │
│  - VideoGuideViewModel              │
│  - VideoPlayerViewModel             │
├─────────────────────────────────────┤
│  Domain Layer                       │
│  - VideoGuideRepository             │
│  - VideoItem (ドメインモデル)        │
├─────────────────────────────────────┤
│  Data Layer                         │
│  - Remote: Drupal API               │
│  - Local: Room Database             │
│  - Download: ExoPlayer              │
└─────────────────────────────────────┘
```

### アーキテクチャの特徴

- **責務の分離**: 各レイヤーが明確な責務を持つ
- **テスト容易性**: 各レイヤーを独立してテスト可能
- **拡張性**: 新しい機能追加が容易
- **保守性**: 変更の影響範囲が限定的

---

## 📁 ディレクトリ構造

```
app/src/main/java/org/dhis2/usescases/videoGuide/
├── VideoGuideRepository.kt          # データアクセス層（Repository）
│
├── data/                             # データ層
│   ├── api/
│   │   └── VideoApiService.kt       # Retrofit APIインターフェース
│   ├── datasource/
│   │   ├── VideoRemoteDataSource.kt # リモートデータソース（インターフェース）
│   │   ├── DrupalVideoApiDataSource.kt # Drupal API実装
│   │   ├── DummyVideoDataSource.kt # 開発用ダミーデータソース
│   │   ├── VideoLocalDataSource.kt  # ローカルデータソース（インターフェース）
│   │   └── RoomVideoLocalDataSource.kt # Room DB実装
│   ├── dto/
│   │   ├── VideoListResponseDto.kt  # APIレスポンスDTO（一覧用）
│   │   ├── VideoResponseDto.kt      # APIレスポンスDTO（個別取得用）
│   │   ├── VideoMediaDto.kt         # メディア情報DTO
│   │   └── VideoFileDto.kt          # ファイル情報DTO
│   ├── local/                        # Room Database
│   │   ├── VideoDatabase.kt         # Room Database定義
│   │   ├── DownloadedVideoEntity.kt  # ダウンロード済み動画エンティティ
│   │   └── DownloadedVideoDao.kt    # DAOインターフェース
│   └── mapper/
│       └── VideoMapper.kt           # DTO → Domain Model変換
│
├── domain/                           # ドメイン層
│   └── model/
│       └── VideoItem.kt             # 動画ドメインモデル
│
├── download/                         # ダウンロード機能
│   ├── VideoDownloadManager.kt      # ダウンロード管理
│   ├── VideoDownloadService.kt      # フォアグラウンドサービス
│   ├── DownloadTracker.kt           # ダウンロード状態監視
│   └── VideoCacheManager.kt         # キャッシュ管理（Singleton）
│
├── list/                             # 動画一覧画面
│   ├── VideoGuideFragment.kt        # フラグメント
│   ├── VideoGuideViewModel.kt       # ViewModel
│   ├── VideoGuideViewModelFactory.kt
│   ├── VideoGuideModule.kt          # Dagger DIモジュール
│   ├── VideoGuideComponent.kt       # Dagger DIコンポーネント
│   └── ui/
│       └── VideoGuideScreen.kt      # Compose UI
│
└── player/                           # 動画再生画面
    ├── VideoPlayerActivity.kt       # Activity
    ├── VideoPlayerViewModel.kt       # ViewModel
    ├── VideoPlayerViewModelFactory.kt
    ├── VideoPlayerModule.kt         # Dagger DIモジュール
    ├── VideoPlayerComponent.kt     # Dagger DIコンポーネント
    ├── ExoPlayerManager.kt          # ExoPlayer管理
    └── ui/
        └── VideoPlayerScreen.kt     # Compose UI
```

---

## 📝 各ファイルの役割

### 📁 Data Layer（データ層）

#### `VideoApiService.kt`
- **役割**: Drupal JSON:APIとの通信を定義（Retrofit）
- **エンドポイント**:
  - `GET /jsonapi/media/video` - 動画一覧取得
  - `GET /jsonapi/media/video/{id}` - 個別動画取得
- **特徴**: Moshi Converterを使用してJSONを自動変換

#### `VideoRemoteDataSource.kt` / `DrupalVideoApiDataSource.kt`
- **役割**: リモートデータ取得の実装
- **処理フロー**: 
  1. API呼び出し（`VideoApiService`）
  2. DTO取得
  3. `VideoMapper`でDomain Model変換
  4. エラーハンドリング（`runCatching`）
- **特徴**: `DummyVideoDataSource`と切り替え可能（開発用）

#### `VideoLocalDataSource.kt` / `RoomVideoLocalDataSource.kt`
- **役割**: ローカルデータ（Room DB）の読み書き
- **処理**: ダウンロード済み動画の保存・取得・削除
- **エンティティ**: `DownloadedVideoEntity`を使用

#### `VideoMapper.kt`
- **役割**: DTO → Domain Model変換
- **特徴**: 
  - 純粋関数として実装
  - エラーハンドリングはDataSource層で実施
  - `included`配列からファイルマップを作成

#### `VideoDatabase.kt` / `DownloadedVideoEntity.kt`
- **役割**: Room Databaseとダウンロード済み動画の永続化
- **保存情報**: 
  - `videoId` (Primary Key)
  - `title`, `description`
  - `videoUrl`, `thumbnailUrl`
  - `localFilePath` - ダウンロード先パス
  - `downloadedAt` - ダウンロード日時
  - `fileSize` - ファイルサイズ

---

### 📁 Domain Layer（ドメイン層）

#### `VideoGuideRepository.kt`
- **役割**: データアクセスの抽象化
- **依存**: `VideoRemoteDataSource`, `VideoLocalDataSource`
- **主なメソッド**:
  - `getVideoList()` - 動画一覧取得（リモート）
  - `getVideoById(id)` - 個別動画取得（ローカル優先、なければリモート）
  - `saveDownloadedVideo()` - ダウンロード済み動画を保存
  - `getDownloadedVideoList()` - ダウンロード済み一覧取得
  - `isVideoDownloaded(id)` - ダウンロード済みかチェック

#### `VideoItem.kt`
- **役割**: 動画情報のドメインモデル
- **フィールド**: 
  - `id: String` - 動画ID
  - `title: String` - タイトル
  - `description: String` - 説明
  - `videoUrl: String` - 動画URL
  - `thumbnailUrl: String?` - サムネイルURL
  - `duration: String?` - 再生時間
  - `tag: String?` - タグ
  - `category: String?` - カテゴリ

---

### 📁 Download Layer（ダウンロード機能）

#### `VideoDownloadManager.kt`
- **役割**: ExoPlayerのDownloadServiceとの連携
- **主なメソッド**:
  - `downloadVideo(videoItem)` - ダウンロード開始
  - `cancelDownload(videoId)` - ダウンロードキャンセル
  - `getDownloadState(videoId)` - ダウンロード状態取得
  - `getDownloadProgress(videoId)` - ダウンロード進捗取得（0-100%）
- **LiveData**: `downloadStates`, `downloadProgress`を公開

#### `VideoDownloadService.kt`
- **役割**: フォアグラウンドサービスとしてダウンロード実行
- **継承**: ExoPlayerの`DownloadService`
- **通知**: ダウンロード進捗を通知バーに表示
- **特徴**: アプリを閉じてもバックグラウンドでダウンロード継続

#### `DownloadTracker.kt`
- **役割**: `DownloadManager.Listener`を実装し、状態変化を監視
- **LiveData**: 
  - `downloadStates: LiveData<Map<String, Download>>`
  - `downloadProgress: LiveData<Map<String, Int>>`
- **状態**: `STATE_QUEUED`, `STATE_DOWNLOADING`, `STATE_COMPLETED`, `STATE_FAILED`等

#### `VideoCacheManager.kt`
- **役割**: `SimpleCache`のシングルトン管理
- **保存先**: 
  - 優先: 外部ストレージ（`getExternalFilesDir()`）
  - フォールバック: 内部ストレージ（`context.filesDir`）
- **永続化**: `cacheDir`ではなく`filesDir`を使用（システム削除を防止）
- **特徴**: Singletonパターンでアプリ全体で1つのインスタンス

---

### 📁 Presentation Layer（プレゼンテーション層）

#### `VideoGuideFragment.kt` / `VideoGuideViewModel.kt`
- **役割**: 動画一覧画面の管理
- **機能**:
  - 動画一覧の表示
  - ダウンロードボタンの制御
  - ダウンロード進捗の表示
  - ダウンロード完了時のRoom DB保存
- **UI**: Jetpack Compose（`VideoGuideScreen`）

#### `VideoPlayerActivity.kt` / `VideoPlayerViewModel.kt`
- **役割**: 動画再生画面の管理
- **機能**:
  - ExoPlayerでの動画再生
  - ダウンロード済み動画のみ再生可能
  - 内部ストレージから確実に読み込み
  - ダウンロード状態の確認
- **UI**: Jetpack Compose（`VideoPlayerScreen`）

#### `ExoPlayerManager.kt`
- **役割**: ExoPlayerインスタンスの管理
- **機能**:
  - `CacheDataSourceFactory`を使用してキャッシュから自動読み込み
  - ダウンロード済みチェック（`Download.STATE_COMPLETED`）
  - メディアアイテムの準備と再生
  - ライフサイクル管理（`releasePlayer()`）
- **特徴**: ダウンロード済みでない場合は`IllegalStateException`をスロー

---

## 📚 使用ライブラリ

### メディア再生・ダウンロード

```kotlin
// ExoPlayer（Media3）
implementation("androidx.media3:media3-exoplayer:1.2.0")
implementation("androidx.media3:media3-ui:1.2.0")
implementation("androidx.media3:media3-common:1.2.0")
implementation("androidx.media3:media3-database:1.2.0")
implementation("androidx.media3:media3-datasource:1.2.0")
```

**パッケージ名の注意**:
- ダウンロード関連: `androidx.media3.exoplayer.offline`
- キャッシュ関連: `androidx.media3.datasource.cache`

### ネットワーク通信

```kotlin
// Retrofit & Moshi
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
```

### データベース

```kotlin
// Room（既存のプロジェクト設定を使用）
// バージョン: 1（DownloadedVideoEntityのみ）
```

### UI

```kotlin
// Jetpack Compose（既存のプロジェクト設定を使用）
// Coil（画像読み込み、既存）
```

### 依存性注入

```kotlin
// Dagger 2（既存のプロジェクト設定を使用）
// スコープ: @PerFragment, @PerActivity
```

---

## 🔄 データフロー

### 動画一覧表示

```
1. VideoGuideFragment起動
   ↓
2. VideoGuideViewModel.loadVideos()
   ↓
3. VideoGuideRepository.getVideoList()
   ↓
4. DrupalVideoApiDataSource.getVideoList()
   ↓
5. VideoApiService（Retrofit）→ Drupal API呼び出し
   ↓
6. VideoListResponseDto取得
   ↓
7. VideoMapper → DTO変換（VideoItem）
   ↓
8. LiveDataでUIに反映（VideoGuideScreen）
```

### 動画ダウンロード

```
1. ユーザーがダウンロードボタンをタップ
   ↓
2. VideoGuideViewModel.startDownload(videoId)
   ↓
3. VideoDownloadManager.downloadVideo(videoItem)
   ↓
4. DownloadRequest作成（videoId, videoUrl）
   ↓
5. VideoDownloadService（フォアグラウンドサービス）
   ↓
6. ExoPlayer DownloadManager → 内部ストレージに保存
   ↓
7. DownloadTracker → 状態変化を監視
   ↓
8. ダウンロード完了時（STATE_COMPLETED）
   ↓
9. VideoGuideViewModel → Room DBに記録
   ↓
10. DownloadedVideoEntity保存完了
```

### オフライン再生

```
1. VideoPlayerActivity起動（videoId）
   ↓
2. VideoPlayerViewModel.loadVideo(videoId)
   ↓
3. VideoGuideRepository.getVideoById()
   → ローカルDB優先、なければAPI取得
   ↓
4. ExoPlayerManager.prepareMediaItem(videoId)
   ↓
5. DownloadIndexでダウンロード状態確認
   → Download.STATE_COMPLETEDかチェック
   ↓
6. ダウンロード済みの場合のみ再生
   ↓
7. CacheDataSourceFactory → SimpleCacheから自動読み込み
   ↓
8. ExoPlayerで再生開始
```

---

## ⚠️ 重要なポイント

### ✅ 永続化ストレージ

**問題**: `context.cacheDir`はシステムが自動削除する可能性がある

**解決策**: 
- ダウンロードファイルは`context.filesDir`（内部）または`getExternalFilesDir()`（外部）に保存
- `VideoCacheManager`が自動的に最適なストレージを選択
- システムによる削除を防ぎ、アンインストール時のみ削除される

**保存先の例**:
- 外部ストレージ: `/storage/emulated/0/Android/data/org.dhis2/files/Movies/video_downloads/`
- 内部ストレージ: `/data/data/org.dhis2/files/video_downloads/`

### ✅ オフライン再生の仕組み

**重要な理解**: 
- ExoPlayerの`CacheDataSource`が自動的にキャッシュから読み込む
- 実際のファイルパスを取得する必要はない
- 元のURLを使用し、ExoPlayerが自動的にキャッシュを検索

**実装**:
```kotlin
// ExoPlayerManager.kt
val cacheDataSourceFactory = CacheDataSource.Factory()
    .setCache(cache)  // SimpleCache
    .setUpstreamDataSourceFactory(httpDataSourceFactory)
```

**ダウンロード済みチェック**:
```kotlin
val download = downloadIndex.getDownload(videoId)
if (download?.state == Download.STATE_COMPLETED) {
    // 再生可能
}
```

### ✅ ダウンロード状態管理

**仕組み**:
- `DownloadTracker`が`DownloadManager.Listener`を実装
- LiveDataでリアルタイムにUI更新
- ダウンロード完了時にRoom DBに自動保存

**状態の種類**:
- `STATE_QUEUED` - キューに追加済み
- `STATE_DOWNLOADING` - ダウンロード中
- `STATE_COMPLETED` - 完了
- `STATE_FAILED` - 失敗
- `STATE_STOPPED` - 停止
- `STATE_REMOVING` - 削除中

### ✅ DIパターン

**モジュール構成**:
- `VideoGuideModule`/`VideoGuideComponent` - フラグメント用（`@PerFragment`）
- `VideoPlayerModule`/`VideoPlayerComponent` - Activity用（`@PerActivity`）
- `VideoCacheManager` - Singleton（SimpleCache管理）

**注入の流れ**:
```
AppComponent
  └── MainComponent
       ├── VideoGuideComponent (Fragment)
       └── VideoPlayerComponent (Activity)
```

---

## 🔧 開発時の注意点

### データソースの切り替え

開発時はダミーデータを使用できます：

```kotlin
// VideoGuideModule.kt
fun provideDataSource(...): VideoRemoteDataSource {
    // 本番環境
    return DrupalVideoApiDataSource(api, mapper, baseUrl)
    
    // 開発時（APIなしでテスト）
    // return DummyVideoDataSource()
}
```

### BuildConfig設定

```kotlin
// app/build.gradle.kts
buildConfigField("String", "DRUPAL_BASE_URL", "\"https://your-drupal-site.com/\"")
```

### 権限

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

### サービス登録

```xml
<!-- AndroidManifest.xml -->
<service
    android:name=".usescases.videoGuide.download.VideoDownloadService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />
```

### デバッグ時のログ

```kotlin
// Timberを使用
Timber.d("Download started for video: ${videoItem.id}")
Timber.e(exception, "Failed to download video")
```

---

## ❓ よくある質問

### Q: ダウンロードした動画はどこに保存されますか？

A: 外部ストレージが利用可能な場合は`/Android/data/org.dhis2/files/Movies/video_downloads/`、利用不可能な場合は内部ストレージの`/data/data/org.dhis2/files/video_downloads/`に保存されます。

**確認方法**:
```kotlin
val path = VideoCacheManager.getVideoDownloadDirectoryPath(context)
Timber.d("Download directory: $path")
```

### Q: オンライン再生はサポートしていますか？

A: 現在はダウンロード済み動画のオフライン再生のみサポートしています。オンライン再生機能は計画中です。

### Q: ダウンロード中にアプリを閉じても大丈夫ですか？

A: はい。`VideoDownloadService`がフォアグラウンドサービスとして動作するため、バックグラウンドでもダウンロードが継続されます。通知バーで進捗を確認できます。

### Q: 視聴履歴機能はありますか？

A: 計画中ですが、まだ実装されていません。`VIDEO_WATCH_HISTORY_IMPLEMENTATION_PLAN.md`を参照してください。

### Q: ダウンロード済み動画を削除するには？

A: 現在は実装されていませんが、以下の方法で実装可能です：
1. `VideoDownloadManager.cancelDownload(videoId)`でダウンロードを削除
2. `VideoGuideRepository.deleteDownloadedVideo(videoId)`でRoom DBから削除

### Q: ストレージ容量が不足した場合は？

A: 現在は容量チェック機能がありませんが、`VideoCacheManager.getStorageInfo(context)`でストレージ情報を取得できます。実装予定です。

### Q: 複数の動画を同時にダウンロードできますか？

A: はい。ExoPlayerの`DownloadManager`が自動的にキュー管理します。複数のダウンロードを同時に実行できます。

---

## 📖 参考ドキュメント

プロジェクトには詳細なドキュメントが用意されています：

1. **VIDEO_GUIDE_IMPLEMENTATION.md** - 基本構造とデータフロー
2. **VIDEO_PLAYER_IMPLEMENTATION_PLAN.md** - ExoPlayer統合とフェーズ別実装計画
3. **VIDEO_METADATA_IMPLEMENTATION.md** - メタ情報追加ガイド
4. **VIDEO_DOWNLOAD_PERSISTENCE_PLAN.md** - 永続化ストレージの実装
5. **VIDEO_WATCH_HISTORY_IMPLEMENTATION_PLAN.md** - 視聴履歴機能（計画）

### 外部リソース

- [ExoPlayer公式ドキュメント](https://developer.android.com/guide/topics/media/exoplayer)
- [ExoPlayer Download機能](https://developer.android.com/guide/topics/media/exoplayer/downloading-media)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Drupal JSON:API](https://www.drupal.org/docs/core-modules-and-modules/jsonapi-module)

---

## 🚀 次のステップ

実装に参加する前に：

1. ✅ **既存の実装を確認**
   - `app/src/main/java/org/dhis2/usescases/videoGuide/`ディレクトリを探索
   - 各ファイルの役割を理解

2. ✅ **ExoPlayer公式ドキュメントを読む**
   - 基本的な使い方を理解
   - Download機能の仕組みを理解

3. ✅ **Drupal JSON:APIの仕様を理解する**
   - APIレスポンスの構造を確認
   - `included`配列の扱いを理解

4. ✅ **実際に動画をダウンロード・再生してみる**
   - アプリをビルドして実行
   - ダウンロードフローを確認
   - オフライン再生を確認

5. ✅ **コードレビューに参加**
   - 既存のPRをレビュー
   - コードスタイルを理解

### 開発環境のセットアップ

```bash
# 1. リポジトリをクローン
git clone <repository-url>

# 2. Android Studioで開く
# 3. Gradle Syncを実行
# 4. アプリをビルド
./gradlew assembleDebug

# 5. 実機またはエミュレータで実行
```

### デバッグのヒント

- **ログ確認**: `adb logcat | grep -i video`
- **ストレージ確認**: `adb shell ls -la /data/data/org.dhis2/files/video_downloads/`
- **ダウンロード状態確認**: `DownloadTracker`のログを確認

---

## 📝 まとめ

このモジュールは、以下の特徴を持っています：

- ✅ **明確なアーキテクチャ**: MVVM + クリーンアーキテクチャ
- ✅ **永続化ストレージ**: システム削除を防止
- ✅ **オフライン対応**: ダウンロード済み動画の再生
- ✅ **拡張性**: 新しい機能追加が容易

質問がある場合は、チームメンバーに相談してください！

---

**最終更新**: 2024年12月  
**バージョン**: 1.0  
**対象**: 新規参加エンジニア向け

