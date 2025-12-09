# 動画ダウンロードの永続化実装計画

## 📋 要件

ダウンロードした動画データをデバイス上で永続化し、以下を実現する：

1. ✅ システムによる自動削除を防止
2. ✅ アプリのキャッシュクリア時に削除されない
3. ✅ アプリのアップデート後も保持される
4. ✅ アンインストール時のみ削除される

---

## ⚠️ 現状の問題

### 現在の実装

**ファイル**: `VideoCacheManager.kt`

```kotlin
object VideoCacheManager {
    fun getOrCreateSimpleCache(context: Context): SimpleCache {
        if (simpleCacheInstance == null) {
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCacheInstance = SimpleCache(
                File(context.cacheDir, "video_downloads"),  // ← 問題の箇所
                NoOpCacheEvictor(),
                databaseProvider
            )
        }
        return simpleCacheInstance!!
    }
}
```

### ❌ `context.cacheDir` の問題点

| 問題 | 説明 | 影響 |
|------|------|------|
| **自動削除** | システムがストレージ容量不足時に自動的に削除する可能性がある | ユーザーが意図せずダウンロードが消える |
| **キャッシュクリア** | ユーザーが「設定」→「ストレージ」→「キャッシュをクリア」で削除できる | せっかくダウンロードした動画が消える |
| **一時的なデータ用** | 一時的なキャッシュデータ用のディレクトリであり、永続化には不適切 | データの永続性が保証されない |
| **ユーザーの誤解** | ユーザーは「ダウンロード」=「永続的に保存」と認識するが、実際は消える可能性がある | UX/信頼性の問題 |

### 現状のデータフロー

```
ユーザーがダウンロード
    ↓
ExoPlayer DownloadManager
    ↓
SimpleCache (context.cacheDir/video_downloads)  ← 一時キャッシュ
    ↓
⚠️ システムやユーザーが削除可能
    ↓
❌ ダウンロードが消える
```

---

## ✅ 解決策：永続化ストレージの使用

### オプション1: 内部ストレージ（推奨）

#### 概要

`context.filesDir`を使用してアプリ専用の永続ストレージに保存する。

#### 実装

```kotlin
// 変更前
File(context.cacheDir, "video_downloads")

// 変更後
File(context.filesDir, "video_downloads")
```

#### メリット

| メリット | 説明 |
|---------|------|
| ✅ **永続性保証** | システムによって自動削除されない |
| ✅ **パーミッション不要** | アプリ専用領域なので追加のパーミッション不要 |
| ✅ **セキュリティ** | 他のアプリからアクセスできない（プライベート） |
| ✅ **シンプル** | 実装が簡単で、既存コードの変更が最小限 |
| ✅ **信頼性** | アンインストール時のみ削除される |

#### デメリット

| デメリット | 説明 | 対策 |
|----------|------|------|
| ⚠️ **容量制限** | 内部ストレージの容量を使用する | ストレージ容量チェック機能を追加 |
| ⚠️ **ユーザー削除不可** | ユーザーが直接削除できない | アプリ内に削除機能を実装 |

#### ディレクトリパス例

```
/data/data/org.dhis2/files/video_downloads/
```

---

### オプション2: 外部ストレージ（大容量対応）

#### 概要

`context.getExternalFilesDir()`を使用して外部ストレージに保存する。

#### 実装

```kotlin
// 変更後
File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "video_downloads")
```

または

```kotlin
File(context.getExternalFilesDir(null), "video_downloads")
```

#### メリット

| メリット | 説明 |
|---------|------|
| ✅ **大容量** | 内部ストレージより容量が大きい |
| ✅ **永続性** | アンインストール時のみ削除される |
| ✅ **パーミッション不要** | Android 10以降はScoped Storageで保護されており、パーミッション不要 |
| ✅ **ユーザー確認可能** | ファイルマネージャーからユーザーが確認できる（透明性） |

#### デメリット

| デメリット | 説明 | 対策 |
|----------|------|------|
| ⚠️ **外部ストレージ必須** | SDカードや外部ストレージがない端末では使えない場合がある | `null`チェックとフォールバック処理 |
| ⚠️ **取り外し可能** | SDカードが取り外される可能性がある | マウント状態のチェック |

#### ディレクトリパス例

```
/storage/emulated/0/Android/data/org.dhis2/files/Movies/video_downloads/
```

---

### オプション3: ハイブリッドアプローチ（推奨・ベスト）

#### 概要

外部ストレージが利用可能な場合は外部ストレージを使用し、利用不可能な場合は内部ストレージにフォールバックする。

#### 実装

```kotlin
object VideoCacheManager {
    
    /**
     * 永続化された動画保存用のディレクトリを取得
     * 外部ストレージが利用可能な場合は外部ストレージを使用し、
     * 利用不可能な場合は内部ストレージにフォールバック
     */
    private fun getVideoDownloadDirectory(context: Context): File {
        // 外部ストレージの状態を確認
        val externalStorageState = Environment.getExternalStorageState()
        
        return if (externalStorageState == Environment.MEDIA_MOUNTED) {
            // 外部ストレージが利用可能な場合
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (externalDir != null) {
                File(externalDir, "video_downloads")
            } else {
                // 外部ストレージがnullの場合は内部ストレージにフォールバック
                File(context.filesDir, "video_downloads")
            }
        } else {
            // 外部ストレージが利用不可能な場合は内部ストレージを使用
            File(context.filesDir, "video_downloads")
        }
    }
    
    @Synchronized
    fun getOrCreateSimpleCache(context: Context): SimpleCache {
        if (simpleCacheInstance == null) {
            val databaseProvider = StandaloneDatabaseProvider(context)
            val downloadDir = getVideoDownloadDirectory(context)
            
            // ディレクトリが存在しない場合は作成
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
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
}
```

#### メリット

| メリット | 説明 |
|---------|------|
| ✅ **柔軟性** | 端末の状況に応じて最適なストレージを使用 |
| ✅ **大容量優先** | 可能な限り外部ストレージの大容量を活用 |
| ✅ **信頼性** | フォールバックにより確実に動作 |
| ✅ **永続性** | どちらの場合も永続化される |

---

## 🔧 実装手順

### ステップ1: `VideoCacheManager.kt`の修正

#### 変更が必要な箇所

**ファイル**: `app/src/main/java/org/dhis2/usescases/videoGuide/video/VideoCacheManager.kt`

```kotlin
@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dhis2.usescases.videoGuide.video

import android.content.Context
import android.os.Environment
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber
import java.io.File

/**
 * SimpleCacheのシングルトン管理クラス
 * 動画を永続化ストレージに保存するため、context.filesDirまたはgetExternalFilesDir()を使用
 */
object VideoCacheManager {
    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    /**
     * 永続化された動画保存用のディレクトリを取得
     * 外部ストレージが利用可能な場合は外部ストレージを使用し、
     * 利用不可能な場合は内部ストレージにフォールバック
     */
    private fun getVideoDownloadDirectory(context: Context): File {
        val externalStorageState = Environment.getExternalStorageState()
        
        return if (externalStorageState == Environment.MEDIA_MOUNTED) {
            // 外部ストレージが利用可能な場合
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (externalDir != null) {
                File(externalDir, "video_downloads").also {
                    Timber.d("Using external storage for video downloads: ${it.absolutePath}")
                }
            } else {
                // 外部ストレージがnullの場合は内部ストレージにフォールバック
                File(context.filesDir, "video_downloads").also {
                    Timber.w("External storage is null, falling back to internal storage: ${it.absolutePath}")
                }
            }
        } else {
            // 外部ストレージが利用不可能な場合は内部ストレージを使用
            File(context.filesDir, "video_downloads").also {
                Timber.d("Using internal storage for video downloads: ${it.absolutePath}")
            }
        }
    }

    @Synchronized
    fun getOrCreateSimpleCache(context: Context): SimpleCache {
        if (simpleCacheInstance == null) {
            val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
            val downloadDir = getVideoDownloadDirectory(context)
            
            // ディレクトリが存在しない場合は作成
            if (!downloadDir.exists()) {
                val created = downloadDir.mkdirs()
                if (created) {
                    Timber.d("Created video download directory: ${downloadDir.absolutePath}")
                } else {
                    Timber.e("Failed to create video download directory: ${downloadDir.absolutePath}")
                }
            }
            
            simpleCacheInstance = SimpleCache(
                downloadDir,
                NoOpCacheEvictor(),
                databaseProvider
            )
            
            Timber.i("SimpleCache initialized with directory: ${downloadDir.absolutePath}")
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
        Timber.d("SimpleCache released")
    }
    
    /**
     * 現在使用中のダウンロードディレクトリのパスを取得（デバッグ用）
     */
    fun getCurrentDownloadDirectoryPath(context: Context): String {
        return getVideoDownloadDirectory(context).absolutePath
    }
    
    /**
     * ストレージ情報を取得（デバッグ・UI表示用）
     */
    fun getStorageInfo(context: Context): StorageInfo {
        val downloadDir = getVideoDownloadDirectory(context)
        val totalSpace = downloadDir.totalSpace
        val freeSpace = downloadDir.freeSpace
        val usedSpace = totalSpace - freeSpace
        
        return StorageInfo(
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            downloadDirectoryPath = downloadDir.absolutePath,
            isExternalStorage = downloadDir.path.contains("emulated") || downloadDir.path.contains("sdcard")
        )
    }
}

/**
 * ストレージ情報を保持するデータクラス
 */
data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val downloadDirectoryPath: String,
    val isExternalStorage: Boolean
) {
    fun getTotalSpaceGB(): Double = totalSpace / (1024.0 * 1024.0 * 1024.0)
    fun getFreeSpaceGB(): Double = freeSpace / (1024.0 * 1024.0 * 1024.0)
    fun getUsedSpaceGB(): Double = usedSpace / (1024.0 * 1024.0 * 1024.0)
}
```

---

### ステップ2: RoomデータベースのlocalFilePathを更新

#### 問題

現在、`VideoPlayerViewModel`でダウンロード完了時にRoomに保存する際、以下のようにキャッシュパスを保存しています：

```kotlin
val cachePath = File(context.cacheDir, "video_downloads").absolutePath
repository.saveDownloadedVideo(video, cachePath)
```

これは`cacheDir`を使用しているため、永続化ディレクトリと一致しません。

#### 修正

**ファイル**: `VideoPlayerViewModel.kt`

```kotlin
private val downloadStatesObserver = Observer<Map<String, Download>> { downloads ->
    downloads.values.forEach { download ->
        if (download.state == Download.STATE_COMPLETED) {
            viewModelScope.launch {
                val video = repository.getVideoById(download.request.id)
                if (video != null) {
                    // 永続化ディレクトリのパスを取得
                    val downloadPath = VideoCacheManager.getCurrentDownloadDirectoryPath(context)
                    repository.saveDownloadedVideo(video, downloadPath)
                    Timber.d("Download completed and saved to database: ${video.id}, path: $downloadPath")
                }
            }
        }
    }
    // ... 残りのコード
}
```

**同様の修正が必要な場所**:
- `VideoGuideViewModel.kt`（もし同様の処理があれば）

---

### ステップ3: ストレージ容量チェック機能の追加（オプション・推奨）

#### ダウンロード前の容量チェック

**ファイル**: `VideoDownloadManager.kt`

```kotlin
/**
 * ストレージ容量が十分かどうかをチェック
 * @param estimatedSize 推定ファイルサイズ（バイト）
 * @return 十分な空き容量があればtrue
 */
private fun hasEnoughStorage(estimatedSize: Long): Boolean {
    val storageInfo = VideoCacheManager.getStorageInfo(context)
    val requiredSpace = estimatedSize * 1.2 // 20%のバッファを追加
    
    return storageInfo.freeSpace >= requiredSpace
}

/**
 * 動画のダウンロードを開始（容量チェック付き）
 */
fun downloadVideo(videoItem: VideoItem, estimatedSize: Long = 100 * 1024 * 1024) {
    try {
        // ストレージ容量チェック
        if (!hasEnoughStorage(estimatedSize)) {
            Timber.w("Insufficient storage space for video: ${videoItem.id}")
            // エラー通知（LiveDataなどで通知）
            return
        }
        
        val downloadRequest = DownloadRequest.Builder(videoItem.id, Uri.parse(videoItem.videoUrl))
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setData(videoItem.title.toByteArray())
            .build()

        DownloadService.sendAddDownload(
            context,
            VideoDownloadService::class.java,
            downloadRequest,
            false
        )

        Timber.d("Download started for video: ${videoItem.id}")
    } catch (e: Exception) {
        Timber.e(e, "Failed to start download for video: ${videoItem.id}")
    }
}
```

---

### ステップ4: ダウンロード済みファイルのサイズ計算（オプション）

**ファイル**: `VideoDownloadManager.kt`に追加

```kotlin
/**
 * ダウンロード済み動画の合計ファイルサイズを取得
 */
suspend fun getTotalDownloadedSize(): Long {
    return try {
        val downloads = getAllDownloads()
        downloads.filter { it.state == Download.STATE_COMPLETED }
            .sumOf { it.contentLength }
    } catch (e: Exception) {
        Timber.e(e, "Failed to calculate total downloaded size")
        0L
    }
}

/**
 * ファイルサイズを人間が読みやすい形式に変換
 */
fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes bytes"
    }
}
```

---

## 📊 データフローの比較

### 変更前（現状）

```
ダウンロード
    ↓
context.cacheDir/video_downloads/  [一時キャッシュ]
    ↓
⚠️ システムが自動削除可能
⚠️ ユーザーがキャッシュクリアで削除可能
    ↓
❌ データが消える
```

### 変更後（永続化）

```
ダウンロード
    ↓
外部ストレージ利用可能？
    ↓ YES                        ↓ NO
getExternalFilesDir()      context.filesDir/
    /Movies/video_downloads/     video_downloads/
    ↓                            ↓
✅ 永続化ストレージに保存
    ↓
✅ システムが自動削除しない
✅ キャッシュクリアで削除されない
✅ アンインストール時のみ削除
```

---

## 🎯 実装後の動作

### 1. ダウンロード時

```
ユーザーがダウンロードボタンをクリック
    ↓
ストレージ容量チェック（オプション）
    ↓
ExoPlayer DownloadManager がダウンロード開始
    ↓
永続化ストレージに保存
    - 外部ストレージ（優先）
    - または内部ストレージ（フォールバック）
    ↓
ダウンロード完了時にRoomに記録
    - ファイルパス
    - ファイルサイズ
    - ダウンロード日時
```

### 2. アプリ再起動時

```
アプリ起動
    ↓
VideoCacheManager.getOrCreateSimpleCache()
    ↓
永続化ストレージのパスを取得
    ↓
既存のダウンロードファイルを認識
    ↓
✅ ダウンロード済み動画が利用可能
```

### 3. ストレージ容量確認時

```
設定画面を開く（実装する場合）
    ↓
VideoCacheManager.getStorageInfo()
    ↓
ストレージ情報を表示
    - 合計容量
    - 使用容量
    - 空き容量
    - ダウンロードディレクトリのパス
```

---

## ✅ 実装チェックリスト

### 必須実装

- [ ] `VideoCacheManager.kt`の修正
  - [ ] `getVideoDownloadDirectory()`メソッドの追加
  - [ ] 外部ストレージのチェックとフォールバック処理
  - [ ] ディレクトリ作成処理の追加
  - [ ] ログ出力の追加

- [ ] `VideoPlayerViewModel.kt`の修正
  - [ ] `cachePath`の取得方法を変更
  - [ ] `VideoCacheManager.getCurrentDownloadDirectoryPath()`を使用

- [ ] テスト
  - [ ] 外部ストレージが利用可能な端末でのテスト
  - [ ] 外部ストレージがない端末でのテスト
  - [ ] ダウンロード後のアプリ再起動テスト
  - [ ] キャッシュクリア後もダウンロードが残ることを確認

### 推奨実装（オプション）

- [ ] ストレージ容量チェック機能
  - [ ] `hasEnoughStorage()`メソッドの追加
  - [ ] ダウンロード前の容量チェック
  - [ ] 容量不足時のエラー通知

- [ ] ストレージ情報表示機能
  - [ ] `getStorageInfo()`メソッドの追加
  - [ ] `StorageInfo`データクラスの追加
  - [ ] 設定画面での表示（UI実装）

- [ ] ファイルサイズ管理
  - [ ] `getTotalDownloadedSize()`メソッドの追加
  - [ ] `formatFileSize()`メソッドの追加
  - [ ] ダウンロード一覧での表示

---

## 📌 注意事項

### 1. マイグレーション（既存ユーザー対応）

既存ユーザーが既に`cacheDir`にダウンロードしている場合、データを移行する必要があるかもしれません。

#### マイグレーション処理（オプション）

```kotlin
/**
 * 既存のキャッシュディレクトリから永続化ディレクトリにファイルを移行
 */
@Synchronized
fun migrateFromCacheToFiles(context: Context) {
    val oldCacheDir = File(context.cacheDir, "video_downloads")
    val newDownloadDir = getVideoDownloadDirectory(context)
    
    if (oldCacheDir.exists() && oldCacheDir.listFiles()?.isNotEmpty() == true) {
        Timber.i("Migrating video downloads from cache to persistent storage...")
        
        try {
            oldCacheDir.listFiles()?.forEach { file ->
                val targetFile = File(newDownloadDir, file.name)
                file.copyTo(targetFile, overwrite = true)
                file.delete()
                Timber.d("Migrated: ${file.name}")
            }
            Timber.i("Migration completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate video downloads")
        }
    }
}
```

### 2. ストレージパーミッション

- Android 10 (API 29) 以降：`getExternalFilesDir()`はScoped Storageで保護されており、パーミッション不要
- Android 9 (API 28) 以前：`getExternalFilesDir()`でもパーミッション不要（アプリ専用領域）

**結論**: 追加のパーミッション不要

### 3. ファイルサイズの考慮

動画ファイルは大きいため、以下を考慮：

- ダウンロード前に推定サイズをユーザーに表示
- ストレージ容量が十分かチェック
- ダウンロード済みファイルの合計サイズを表示
- 古いダウンロードを削除する機能を提供

### 4. テスト端末の種類

以下の端末でテストすることを推奨：

- 外部ストレージ（SDカード）がある端末
- 外部ストレージがない端末
- 内部ストレージの空き容量が少ない端末

---

## 🎉 期待される成果

この実装により、以下が実現されます：

1. ✅ **データの永続性保証**
   - システムによる自動削除を防止
   - キャッシュクリアの影響を受けない
   - アプリのアップデート後も保持される

2. ✅ **ユーザー体験の向上**
   - 一度ダウンロードした動画が消えない
   - ユーザーの期待に沿った動作
   - ストレージ情報の透明性

3. ✅ **柔軟なストレージ管理**
   - 外部ストレージの大容量を活用
   - 内部ストレージへのフォールバック
   - 端末の状況に応じた最適化

4. ✅ **信頼性の向上**
   - オフライン動作の保証
   - データの整合性維持
   - エラーハンドリングの強化

---

## 📚 参考資料

### Androidのストレージオプション

| ストレージタイプ | パス | 用途 | 削除タイミング |
|----------------|------|------|---------------|
| **キャッシュ** | `context.cacheDir` | 一時的なキャッシュデータ | システムが自動削除可能 |
| **内部ストレージ** | `context.filesDir` | 永続的なアプリデータ | アンインストール時のみ |
| **外部ストレージ** | `context.getExternalFilesDir()` | 大容量の永続データ | アンインストール時のみ |
| **共有ストレージ** | `Environment.getExternalStoragePublicDirectory()` | 共有ファイル | アンインストール後も残る |

### 推奨ストレージ選択フロー

```
動画ダウンロード
    ↓
大容量データ？
    ↓ YES
外部ストレージ利用可能？
    ↓ YES              ↓ NO
getExternalFilesDir()  context.filesDir
```

### 公式ドキュメント

- [Data and file storage overview](https://developer.android.com/training/data-storage)
- [Save to app-specific files](https://developer.android.com/training/data-storage/app-specific)
- [ExoPlayer Download](https://developer.android.com/guide/topics/media/exoplayer/downloading-media)

---

**作成日**: 2024年12月6日  
**ステータス**: 実装準備完了  
**優先度**: 高（ユーザー体験に直結）

