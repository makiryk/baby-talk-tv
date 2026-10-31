package com.babytalk.tv.audio

/** 音声イベントを言葉として認識する（認識できなければ null）。 */
interface SpeechRecognizer {
    suspend fun recognize(event: VoiceEvent): UtteranceUnderstanding?
}

/** オーケストレーターが依存する理解エンジンの最小インターフェース。 */
interface UtteranceUnderstander {
    suspend fun understand(event: VoiceEvent): UtteranceUnderstanding
}

/**
 * STT（言葉の理解）を優先し、使えない／認識できないときは音響判定（喃語・トーン）へフォールバックする。
 */
class SpeechUnderstandingEngine(
    private val acousticAnalyzer: AcousticPatternAnalyzer,
    private val recognizer: SpeechRecognizer?
) : UtteranceUnderstander {
    override suspend fun understand(event: VoiceEvent): UtteranceUnderstanding =
        recognizer?.recognize(event) ?: acousticAnalyzer.analyze(event)
}
