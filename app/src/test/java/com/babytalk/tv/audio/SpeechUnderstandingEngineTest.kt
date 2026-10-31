package com.babytalk.tv.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SpeechUnderstandingEngineTest {

    private fun sine(freq: Double, ms: Int): ShortArray =
        ShortArray(16 * ms) { (8000 * Math.sin(2 * Math.PI * freq * it / 16000)).toInt().toShort() }

    private val event = VoiceEvent(VoiceTone.COOING_HAPPY, 600, audioPcm = sine(440.0, 600))

    private fun stt(intent: UtteranceIntent) = UtteranceUnderstanding(
        intent, "テキスト", "kw", VoiceTone.COOING_HAPPY,
        UnderstandingConfidence.HIGH, UnderstandingSource.STT, 600
    )

    @Test
    fun sttResultWinsWhenAvailable() = runTest {
        val expected = stt(UtteranceIntent.WORD_WANWAN)
        val engine = SpeechUnderstandingEngine(AcousticPatternAnalyzer(), object : SpeechRecognizer {
            override suspend fun recognize(event: VoiceEvent) = expected
        })
        assertSame(expected, engine.understand(event))
    }

    @Test
    fun fallsBackToAcousticWhenSttReturnsNull() = runTest {
        val engine = SpeechUnderstandingEngine(AcousticPatternAnalyzer(), object : SpeechRecognizer {
            override suspend fun recognize(event: VoiceEvent): UtteranceUnderstanding? = null
        })
        val result = engine.understand(event)
        assertEquals(UnderstandingSource.ACOUSTIC, result.source)
        assertEquals(AcousticPatternAnalyzer().analyze(event).intent, result.intent)
    }

    @Test
    fun fallsBackToAcousticWhenNoRecognizer() = runTest {
        val result = SpeechUnderstandingEngine(AcousticPatternAnalyzer(), null).understand(event)
        assertEquals(AcousticPatternAnalyzer().analyze(event), result)
    }
}
