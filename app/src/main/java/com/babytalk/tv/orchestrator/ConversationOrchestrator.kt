package com.babytalk.tv.orchestrator

import com.babytalk.tv.audio.VoiceEvent
import com.babytalk.tv.audio.VoiceTone
import com.babytalk.tv.data.PhraseRepository
import com.babytalk.tv.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 会話オーケストレーター（5状態マシン ＆ タイマー制御）。
 * - H3対応: SPEAKING中のエコー遮断ミュートゲート
 * - M5対応: タイムアウト付き発話待機
 * - M7対応: 無音時自律語りかけのランダム化（120〜180秒）
 */
class ConversationOrchestrator(
    private val phraseRepository: PhraseRepository,
    private val ttsManager: TtsManager,
    private val onMuteAudioInput: (Boolean) -> Unit,
    private val onSessionFinished: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var idleAutonomousTimerJob: Job? = null
    private var sessionDurationMinutes = 10

    /**
     * セッション開始
     */
    fun startSession(durationMinutes: Int = 10) {
        sessionDurationMinutes = durationMinutes.coerceIn(5, 120) // L3対応: 最大120分
        _state.value = ConversationState.IDLE
        onMuteAudioInput(false)

        startSessionTimer()
        resetIdleAutonomousTimer()
    }

    /**
     * セッションタイマー開始
     */
    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            delay(sessionDurationMinutes * 60 * 1000L)
            handleSessionTimeout()
        }
    }

    /**
     * M7対応: 無音時自律語りかけタイマー（120〜180秒のランダム）
     */
    private fun resetIdleAutonomousTimer() {
        idleAutonomousTimerJob?.cancel()
        if (_state.value == ConversationState.STANDBY || _state.value == ConversationState.CLOSING) return

        val randomIntervalMs = Random.nextLong(120_000L, 180_000L)
        idleAutonomousTimerJob = scope.launch {
            delay(randomIntervalMs)
            triggerIdleAutonomousTalk()
        }
    }

    /**
     * 赤ちゃんの声検知イベント（AudioRecordServiceから通知）
     */
    fun onVoiceDetected(event: VoiceEvent) {
        if (_state.value != ConversationState.IDLE) return

        _state.value = ConversationState.LISTENING

        scope.launch {
            delay(200) // 短い解析マージン
            triggerReactiveResponse(event.tone)
        }
    }

    /**
     * 赤ちゃんの声に対する相槌発話
     */
    private fun triggerReactiveResponse(tone: VoiceTone) {
        if (_state.value != ConversationState.LISTENING) return

        enterSpeakingState()

        val phrase = phraseRepository.getPhrase(category = "REACTIVE", tone = tone)
        ttsManager.speak(phrase.text, phrase.audioPath)
    }

    /**
     * 無音時の自律語りかけ発話
     */
    private fun triggerIdleAutonomousTalk() {
        if (_state.value != ConversationState.IDLE) return

        enterSpeakingState()

        val phrase = phraseRepository.getPhrase(category = "IDLE_AUTONOMOUS", tone = null)
        ttsManager.speak(phrase.text, phrase.audioPath)
    }

    /**
     * H3対応: 発話中状態への突入とエコー遮断ミュートゲート
     */
    private fun enterSpeakingState() {
        _state.value = ConversationState.SPEAKING
        onMuteAudioInput(true) // マイク入力を破棄
    }

    /**
     * 発話完了通知（TtsManagerから受信）
     */
    fun onUtteranceCompleted() {
        if (_state.value == ConversationState.CLOSING) {
            // 終了挨拶完了 → スタンバイへ
            _state.value = ConversationState.STANDBY
            onMuteAudioInput(true)
            onSessionFinished()
            return
        }

        if (_state.value == ConversationState.SPEAKING) {
            scope.launch {
                // H3対応: 余韻マージン 500ms 待機後にミュート解除
                delay(500)
                onMuteAudioInput(false)
                _state.value = ConversationState.IDLE
                resetIdleAutonomousTimer()
            }
        }
    }

    /**
     * セッションタイマー満了時処理
     */
    private fun handleSessionTimeout() {
        idleAutonomousTimerJob?.cancel()
        _state.value = ConversationState.CLOSING
        onMuteAudioInput(true)

        // 終了フレーズを発話
        val closingPhrase = phraseRepository.getPhrase(category = "CLOSING", tone = null)
        ttsManager.speak(closingPhrase.text, closingPhrase.audioPath)
    }

    fun stopSession() {
        sessionTimerJob?.cancel()
        idleAutonomousTimerJob?.cancel()
        ttsManager.stop()
        _state.value = ConversationState.STANDBY
        onMuteAudioInput(true)
    }
}
