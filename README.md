# BabyTalk TV (赤ちゃんと会話するAndroid TVアプリ)

赤ちゃんの言語発達やご機嫌な時間をサポートするため、リビングのAndroid TVを活用して赤ちゃんに優しく語りかけ・双方向の会話を行う安心・安全テレビアプリです。

---

## 🌟 主な特徴

1. **赤ちゃんに見せない完全黒画面 UI (#000000)**:
   - 画面全体を純黒にし、赤ちゃんの視覚刺激（ブルーライト・注視）を徹底防止。
   - 画面端に**極小の動作インジケーター（14dp）** のみを表示（待機中はゆっくり呼吸するようにパルス明滅、検知時は淡青、発話中は淡緑）。
   - 有機ELテレビ（OLED）の焼き付きを防ぐため、**60秒ごとのピクセルシフト（位置微動）** を搭載。

2. **タイマー設定 ＆ 自動テレビOFF (スタンバイ)**:
   - 稼働タイマー（デフォルト10分、5/10/15/30/60/120分から選択可能）。
   - 赤ちゃんに数字を見せないよう、起動時5秒間またはリモコンの十字キー長押し時のみ小さく表示され、5秒後に自動フェードアウト。
   - タイマー終了時は、優しい終わりの挨拶を発話した後、`AccessibilityService` (GLOBAL_ACTION_LOCK_SCREEN) によりテレビをスタンバイ/HDMI-CEC電源OFFにします。
   - アプリ終了時は、テレビ本来の通常輝度・通常音量設定に100%自動復帰します。

3. **双方向会話 ＆ 無音時の自律語りかけ**:
   - **VAD（音声区間検出）＋音響特徴解析**: 250Hz〜1000Hzの帯域で赤ちゃんの喃語・クーイング・泣き声を捉え、トーン（ご機嫌／呼びかけ／ぐずり）に合わせた相槌を自動選択。
   - **エコー遮断ミュートゲート**: TVスピーカーからの自分の発話音をマイクが拾って自己対話ループに陥るのを防ぐため、発話中および余韻500msはマイク入力を完全遮断。
   - **無音時自律語りかけ**: 赤ちゃんが声を出さなくても、120〜180秒（2〜3分）のランダム間隔で優しく見守り語りかけを自動発話。

4. **Claude Message Batches API 連携 ＆ Room DB キャッシュ**:
   - Anthropic公式の非同期バッチ処理（Message Batches API、利用コスト50%オフ）を活用。
   - Androidの `WorkManager` が1日1回バックグラウンドで会話フレーズをスケジュール取得し、ローカルの `Room Database` に事前蓄積。
   - 対話時はキャッシュから即座に発話するため、**「0msの超低遅延」** と **「APIコスト半額」** を両立。
   - ネガティブ語・長文を排除する安全フィルタを搭載。

5. **マザーリーズ音声合成 (TTS)**:
   - Google Cloud TTS (Neural2 / Journey) をメイン、Android組み込みTTSをオフラインフォールバックとして採用。
   - 高めのピッチ（+3st）とゆっくりテンポ（0.88倍）で赤ちゃんが最も安心する包み込むような女性声を実現。

---

## 🛠 プロジェクト構成

```
baby-talk-tv/
├── app/
│   ├── src/main/java/com/babytalk/tv/
│   │   ├── MainActivity.kt              # 純黒画面、Compose呼吸インジケーター、タイマーUI
│   │   ├── BabyTalkApplication.kt       # WorkManager定期同期、通知チャネル設定
│   │   ├── audio/
│   │   │   ├── AudioRecordService.kt    # Android 14+ FGS マイクキャプチャ
│   │   │   ├── VadDetector.kt           # エコー遮断ミュートゲート、適応型ノイズフロアVAD
│   │   │   └── VoiceTone.kt             # 声トーン定義 (COOING_HAPPY, BABBLING_NEUTRAL, FUSS_WHINE)
│   │   ├── tts/
│   │   │   └── TtsManager.kt            # 事前合成オーディオ再生 ＆ 組み込みTTSフォールバック
│   │   ├── data/
│   │   │   ├── PhraseRepository.kt      # 段階的フォールバック付きキャッシュリポジトリ
│   │   │   └── db/                      # Room Database (CachedPhraseEntity, BatchSyncJobEntity)
│   │   ├── network/
│   │   │   └── ClaudeBatchApiClient.kt  # Anthropic Message Batches API クライアント ＆ 安全フィルタ
│   │   ├── worker/
│   │   │   └── BatchPhraseSyncWorker.kt # 1日1回バッチ同期スケジュールワーカー
│   │   └── power/
│   │       ├── TvPowerAccessibilityService.kt # TVスタンバイ/CEC電源OFFアクセシビリティ
│   │       └── PowerManagerHelper.kt    # 安全消灯フォールバック
│   └── src/test/java/com/babytalk/tv/   # 単体テスト (VadDetector, Orchestrator, BatchClient)
└── docs/
    ├── design/                          # 基本設計書 (architecture, claude-batch, ui-power-spec)
    └── research/                        # 音声技術調査書 (audio)
```

---

## 🚀 ビルドと動作確認

1. **前提条件**:
   - Android Studio Iguana / Jellyfish 以降
   - JDK 17
   - Android SDK 34 (Android TV エミュレータまたは実機、API 28+)

2. **ビルド手順**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **APIキー設定 (オプション)**:
   - Claude Message Batches APIを利用する場合、アプリ内設定または `SharedPreferences` の `anthropic_api_key` にキーを設定します。
   - 未設定の場合でも、アプリに内蔵された約50件の定型フレーズ（`preloaded_phrases.json`）により、完全オフラインで会話機能が動作します。
