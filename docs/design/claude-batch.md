# Claudeスケジュールタスク連携＆キャッシュデータモデル設計書 (claude-batch.md)

- ドキュメントID: `DOC-WF-2`
- プロジェクト: `baby-talk-tv`
- 改訂日: 2026-10-28 (PLレビュー反映版: H5, M1, M2, M6対応)

---

## 1. 概要とアーキテクチャ
本アプリでは、リアルタイム対話時のネットワーク遅延（レイテンシ）を解消し、かつAnthropic APIのコストを削減するため、**Claude Message Batches API** と **Android WorkManager** による定期スケジュール生成＆ローカルキャッシュ（Room DB）方式を採用します。

```
[Android TV: WorkManager (定時/深夜)]
              │ 
              ▼ (非同期バッチ投入)
[Anthropic Claude: Message Batches API (/v1/messages/batches)]
   - 語りかけ・相槌フレーズを一括生成（利用コスト50%オフ）
              │
              ▼ (状態監視・ポーリング完了検知・JSONL取得)
[安全フィルタ & アプリ側SSMLテンプレート生成]
              │
              ▼
[Android TV: Room Database (Local SQLite) & 音声ファイルキャッシュ]
   - カテゴリ・時間帯別に数十〜数百件のフレーズを蓄積
              │
              ▼ (赤ちゃん発声時 / 無音タイマー時: 0ms低遅延再生)
[会話オーケストレーター] ──> [TTS再生エンジン (事前合成ファイル再生)]
```

---

## 2. Claude Message Batches API 連携仕様

### 2.1 バッチ生成リクエスト仕様 (H5対応)
- **モデル設定**: モデルIDはコードにハードコードせず、`BuildConfig.CLAUDE_MODEL_ID`（デフォルト: `claude-3-5-haiku-20241022` 等、最新版を設定可能）から注入。
- **リクエスト構造**:
```json
{
  "requests": [
    {
      "custom_id": "batch_morning_talk_01",
      "params": {
        "model": "claude-3-5-haiku-20241022",
        "max_tokens": 1024,
        "system": "あなたは0〜2歳の赤ちゃんに優しく語りかける保育士AIです。短く、リズミカルで、赤ちゃんが心地よくなる平仮名中心の言葉（オノマトペ多め）を生成してください。危険な単語や否定的な言葉は含めないでください。",
        "messages": [
          {
            "role": "user",
            "content": "朝の時間帯に使える、赤ちゃんの喃語への相槌フレーズと、自律語りかけフレーズをJSON形式で20個生成してください。キーは category, time_of_day, tone, text のみとし、SSMLタグは出力しないでください。"
          }
        ]
      }
    }
  ]
}
```

### 2.2 出力JSONスキーマと安全フィルタ (M2対応)
- LLMには生のSSMLを出力させず、平文テキスト `text` のみを出力させます。
- アプリ側で以下のバリデーションを実行：
  1. 文字数制限（最大40文字以内の短い発話であること）
  2. 禁止語・不適切語フィルタ（ネガティブワード検知）
  3. テキストの重複排除（`text` のハッシュ値で重複チェック）
  4. アプリ側定義のSSMLテンプレート（`<speak><prosody pitch="+3st" rate="88%">{text}</prosody></speak>`）へ安全に埋め込み。

---

## 3. WorkManager バッチ同期状態マシン (M1対応)

バッチ処理は最大24時間かかる非同期処理であるため、バッチ同期状態をRoomで厳密に管理します。

### 3.1 バッチ管理テーブル: `batch_sync_jobs`
```kotlin
@Entity(tableName = "batch_sync_jobs")
data class BatchSyncJob(
    @PrimaryKey
    val batchId: String,
    val status: String, // SUBMITTED, IN_PROGRESS, ENDED, IMPORTED, FAILED
    val createdAt: Long,
    val resultsUrl: String? = null,
    val errorMessage: String? = null
)
```

### 3.2 同期フロー
1. `BatchPhraseSyncWorker` 起動（1日1回、またはキャッシュ不足時）。
2. 未完了ジョブ（`SUBMITTED` / `IN_PROGRESS`）が存在するか確認。
   - 存在する場合: `GET /v1/messages/batches/{batch_id}` でステータス取得。
   - `processing_status == "ended"` の場合: `results_url` からJSONLをストリーム取得し、Roomへ取り込み（`IMPORTED` へ更新）。
   - まだ処理中の場合: 次回実行へ持ち越し。
3. 有効キャッシュ数が閾値（合計30件）未満の場合、新規バッチを作成して投入（`SUBMITTED` 記録）。

---

## 4. ローカルキャッシュ（Room Database）スキーマ

### 4.1 テーブル定義: `cached_phrases`
```kotlin
@Entity(tableName = "cached_phrases", indices = [Index(value = ["textHash"], unique = true)])
data class CachedPhrase(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val textHash: String, // 重複防止用ハッシュ
    val category: String, // REACTIVE / IDLE_AUTONOMOUS / CLOSING
    val tone: String,     // COOING_HAPPY / BABBLING_NEUTRAL / FUSS_WHINE / GENTLE / SLEEPY
    val timeOfDay: String,// MORNING / DAY / EVENING / NIGHT / ANY
    val text: String,
    val audioFilePath: String? = null, // 事前合成音声ファイルへのパス (M3対応)
    val usedCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 4.2 フレーズ選定の段階的フォールバック (M6対応)
条件に完全に一致するフレーズが0件の場合でも無言やエラーを回避するため、段階的に条件を緩和して必ず1件抽出する安全DAOロジックを規定します。

1. **Step 1 (完全一致)**: 指定の `category` ＋ `tone` ＋ `timeOfDay` で直近最小使用フレーズ
2. **Step 2 (トーン緩和)**: 指定の `category` ＋ `timeOfDay` ＋ 任意トーン
3. **Step 3 (時間帯緩和)**: 指定の `category` 内の任意フレーズ
4. **Step 4 (最終セーフティネット)**: 内蔵のアセット（`preloaded_phrases.json`）の組み込み定型文を即時返却
