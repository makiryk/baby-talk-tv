package com.babytalk.tv.orchestrator

import com.babytalk.tv.audio.UtteranceUnderstander
import com.babytalk.tv.audio.VoiceEvent
import com.babytalk.tv.data.Phrase
import com.babytalk.tv.data.PhraseSource
import com.babytalk.tv.tts.Speaker
import kotlinx.coroutines.CancellationException
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
    private val phraseRepository: PhraseSource,
    private val ttsManager: Speaker,
    private val speechUnderstandingEngine: UtteranceUnderstander,
    private val onMuteAudioInput: (Boolean) -> Unit,
    private val onSessionFinished: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {
    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var idleAutonomousTimerJob: Job? = null
    private var understandingJob: Job? = null
    private var postSpeechJob: Job? = null
    private var sessionDurationMinutes = 10

    // セッション・発話の世代番号。非同期の理解結果が古い世代のものなら破棄する
    private var sessionId = 0
    private var utteranceId = 0

    /**
     * セッション開始
     */
    fun startSession(durationMinutes: Int = 10) {
        sessionId++
        understandingJob?.cancel()
        postSpeechJob?.cancel()
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

        val currentSession = sessionId
        val currentUtterance = ++utteranceId
        understandingJob?.cancel()
        understandingJob = scope.launch {
            val understanding = try {
                speechUnderstandingEngine.understand(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 理解に失敗しても従来どおりトーンで相槌を返す
                return@launch triggerReactiveResponse(currentSession, currentUtterance) {
                    phraseRepository.getPhrase(category = "REACTIVE", tone = event.tone)
                }
            }
            triggerReactiveResponse(currentSession, currentUtterance) {
                phraseRepository.getUnderstoodPhrase(understanding)
            }
        }
    }

    /**
     * 赤ちゃんの声に対する発話。セッション・発話が古くなっていたら何もしない
     */
    private fun triggerReactiveResponse(session: Int, utterance: Int, selectPhrase: () -> Phrase) {
        if (session != sessionId || utterance != utteranceId) return
        if (_state.value != ConversationState.LISTENING) return

        enterSpeakingState()

        val phrase = selectPhrase()
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
            postSpeechJob?.cancel()
            postSpeechJob = scope.launch {
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
        understandingJob?.cancel()
        postSpeechJob?.cancel()
        _state.value = ConversationState.CLOSING
        onMuteAudioInput(true)

        // 終了フレーズを発話
        val closingPhrase = phraseRepository.getPhrase(category = "CLOSING", tone = null)
        ttsManager.speak(closingPhrase.text, closingPhrase.audioPath)
    }

    fun stopSession() {
        sessionId++
        utteranceId++
        sessionTimerJob?.cancel()
        idleAutonomousTimerJob?.cancel()
        understandingJob?.cancel()
        postSpeechJob?.cancel()
        ttsManager.stop()
        _state.value = ConversationState.STANDBY
        onMuteAudioInput(true)
    }
}
