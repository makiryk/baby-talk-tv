package com.babytalk.tv.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // 1回の発話につき開始・完了通知を1度だけ行うためのフラグ
    private var isStartNotified = false
    private var isCompleted = true

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
                scope.launch { notifyStart() }
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
        // 前回の再生を停止（コールバックなし）
        releaseMediaPlayer()
        isCompleted = false
        isStartNotified = false

        // タイムアウト監視を開始 (M5)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(MAX_UTTERANCE_TIMEOUT_MS)
            handleComplete()
        }

        requestAudioFocus()

        // 事前合成ファイルが存在する場合は MediaPlayer で再生
        if (!audioFilePath.isNullOrEmpty() && File(audioFilePath).exists()) {
            playAudioFile(text, audioFilePath)
        } else {
            speakWithBuiltinTts(text)
        }
    }

    private fun playAudioFile(text: String, path: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnPreparedListener { player ->
                    notifyStart()
                    player.start()
                }
                setOnCompletionListener {
                    handleComplete()
                }
                setOnErrorListener { _, _, _ ->
                    // 再生エラー時は組み込み TTS にフォールバック
                    releaseMediaPlayer()
                    speakWithBuiltinTts(text)
                    true
                }
                setDataSource(path)
                prepareAsync()
            }
        } catch (e: Exception) {
            releaseMediaPlayer()
            speakWithBuiltinTts(text)
        }
    }

    private fun speakWithBuiltinTts(text: String) {
        if (isCompleted) return
        val tts = textToSpeech
        if (isTtsInitialized && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utterance_${System.currentTimeMillis()}")
            if (result == TextToSpeech.ERROR) {
                notifyStart()
                handleComplete()
            }
        } else {
            // TTS未準備時は開始・完了を通知して終了
            notifyStart()
            handleComplete()
        }
    }

    private fun notifyStart() {
        if (isStartNotified) return
        isStartNotified = true
        onUtteranceStart()
    }

    private fun handleComplete() {
        if (isCompleted) return
        isCompleted = true
        timeoutJob?.cancel()
        timeoutJob = null
        releaseMediaPlayer()
        abandonAudioFocus()
        onUtteranceComplete()
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .build()
        if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            // コールバックを外してから解放（解放後のイベントでフォールバックが走らないように）
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // 未準備状態では stop() が例外を投げるが無視してよい
            }
            it.release()
        }
        mediaPlayer = null
    }

    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        releaseMediaPlayer()
        textToSpeech?.stop()
        abandonAudioFocus()
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
