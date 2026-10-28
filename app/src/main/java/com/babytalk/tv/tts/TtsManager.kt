package com.babytalk.tv.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 音声合成（TTS）および事前合成音声ファイル再生マネージャー。
 * - M3対応: 事前合成オーディオ再生＋組み込みTTSフォールバック
 * - M5対応: タイムアウト付き発話完了監視
 */
class TtsManager(
    private val context: Context,
    private val onUtteranceStart: () -> Unit,
    private val onUtteranceComplete: () -> Unit
) {
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timeoutJob: Job? = null

    companion object {
        private const val MAX_UTTERANCE_TIMEOUT_MS = 15000L // 最大15秒タイムアウト (M5)
    }

    init {
        initBuiltinTts()
    }

    private fun initBuiltinTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.JAPANESE)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setPitch(1.3f)     // 高めピッチ（マザーリーズ）
                    textToSpeech?.setSpeechRate(0.85f) // ゆっくりテンポ
                    isTtsInitialized = true
                }
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch { onUtteranceStart() }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch { handleComplete() }
            }

            override fun onError(utteranceId: String?) {
                scope.launch { handleComplete() }
            }
        })
    }

    /**
     * テキストまたは事前合成ファイルを発話再生
     */
    fun speak(text: String, audioFilePath: String? = null) {
        // タイムアウト監視を開始 (M5)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(MAX_UTTERANCE_TIMEOUT_MS)
            handleComplete()
        }

        // 事前合成ファイルが存在する場合は MediaPlayer で再生
        if (!audioFilePath.isNullOrEmpty() && File(audioFilePath).exists()) {
            playAudioFile(audioFilePath)
            return
        }

        // フォールバック: Android組み込み TextToSpeech で発話
        if (isTtsInitialized && textToSpeech != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utterance_${System.currentTimeMillis()}")
        } else {
            // TTS未準備時は即時完了
            handleComplete()
        }
    }

    private fun playAudioFile(path: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(path)
                prepare()
                setOnPreparedListener {
                    onUtteranceStart()
                    start()
                }
                setOnCompletionListener {
                    handleComplete()
                }
                setOnErrorListener { _, _, _ ->
                    handleComplete()
                    true
                }
            }
        } catch (e: Exception) {
            // 再生失敗時は完了扱い
            handleComplete()
        }
    }

    private fun handleComplete() {
        timeoutJob?.cancel()
        timeoutJob = null
        onUtteranceComplete()
    }

    fun stop() {
        timeoutJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaPlayer = null
        }
        textToSpeech?.stop()
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
