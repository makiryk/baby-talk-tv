package com.babytalk.tv.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class AcousticPatternAnalyzerTest {
    private val analyzer = AcousticPatternAnalyzer()
    private fun wave(ms: Int, pitch: (Int) -> Double = { 400.0 }, envelope: (Int) -> Double = { 1.0 }) =
        ShortArray(ms * 16) { i ->
            (sin(2 * Math.PI * pitch(i / 16) * i / 16000) * 14000 * envelope(i / 16)).toInt().toShort()
        }

    @Test fun repeatedSyllablesAtTwoHundredToFourHundredMs() {
        for (period in listOf(200, 300, 400)) {
            val pcm = wave(period * 3) { ms -> if (ms % period < 100) 1.0 else 0.0 }
            val result = analyzer.analyze(pcm, period * 3L)
            assertEquals(UtteranceIntent.BABBLE_REPETITION, result.intent)
            assertEquals(UnderstandingSource.ACOUSTIC, result.source)
            assertEquals(UnderstandingConfidence.MEDIUM, result.confidence)
            assertNull(result.recognizedText)
            assertNull(result.detectedKeyword)
        }
    }

    @Test fun stableSustainedVowel() {
        val result = analyzer.analyze(wave(600), 600)
        assertEquals(UtteranceIntent.BABBLE_VOWEL, result.intent)
        assertEquals(600L, result.durationMs)
    }

    @Test fun shortSoundsUsePitchAndToneFallback() {
        for ((pitch, intent) in listOf(700.0 to UtteranceIntent.GENERAL_HAPPY,
            280.0 to UtteranceIntent.GENERAL_FUSS, 450.0 to UtteranceIntent.GENERAL_NEUTRAL)) {
            assertEquals(intent, analyzer.analyze(wave(200, pitch = { pitch }), 200).intent)
        }
    }

    @Test fun unstablePitchIsNotSustainedVowel() {
        assertEquals(UtteranceIntent.GENERAL_NEUTRAL,
            analyzer.analyze(wave(800, pitch = { if (it < 400) 300.0 else 700.0 }), 800).intent)
    }

    @Test fun irregularPulsesAreNotRepetition() {
        val pcm = wave(1000) { if (it < 100 || it in 300..399 || it in 800..899) 1.0 else 0.0 }
        assertEquals(UtteranceIntent.GENERAL_NEUTRAL, analyzer.analyze(pcm, 1000).intent)
    }

    @Test fun silenceAndEmptyInputAreLowConfidence() {
        for (pcm in listOf(shortArrayOf(), ShortArray(32000))) {
            val result = analyzer.analyze(pcm, 2000)
            assertEquals(UtteranceIntent.GENERAL_NEUTRAL, result.intent)
            assertEquals(UnderstandingSource.FALLBACK, result.source)
            assertEquals(UnderstandingConfidence.LOW, result.confidence)
        }
    }
}
