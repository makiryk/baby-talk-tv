package com.babytalk.tv.audio

import kotlin.math.abs
import kotlin.math.sqrt

/** Acoustic heuristics detect babbling patterns, not lexical meaning. */
class AcousticPatternAnalyzer(private val sampleRate: Int = 16000) {
    init { require(sampleRate >= 1000) }

    fun analyze(event: VoiceEvent): UtteranceUnderstanding = analyze(event.audioPcm, event.durationMs)

    fun analyze(audioPcm: ShortArray, durationMs: Long): UtteranceUnderstanding {
        require(durationMs >= 0)
        val window = (sampleRate / 50).coerceAtLeast(1) // 20 ms energy envelope
        val energy = audioPcm.asList().chunked(window).map { frame ->
            sqrt(frame.sumOf { it.toDouble() * it } / frame.size)
        }
        val maximum = energy.maxOrNull() ?: 0.0
        val threshold = maxOf(100.0, maximum * 0.3)
        val peaks = mutableListOf<Int>()
        // Hysteresis prevents small amplitude ripples from splitting a sustained vowel.
        var inPeak = false
        var peakIndex = 0
        var peakEnergy = 0.0
        for (i in energy.indices) {
            if (!inPeak && energy[i] >= threshold) {
                inPeak = true
                peakIndex = i
                peakEnergy = energy[i]
            } else if (inPeak && energy[i] > peakEnergy) {
                peakIndex = i
                peakEnergy = energy[i]
            }
            if (inPeak && energy[i] < threshold * 0.65) {
                peaks.add(peakIndex)
                inPeak = false
            }
        }
        if (inPeak) peaks.add(peakIndex)
        val pitches = mutableListOf<Double>()
        var voicedWindows = 0
        for (i in energy.indices) {
            if (energy[i] < threshold) continue
            voicedWindows++
            estimatePitch(audioPcm.copyOfRange(i * window, minOf((i + 1) * window, audioPcm.size)), sampleRate)
                ?.let { pitches.add(it) }
        }
        val averagePitch = pitches.takeIf { it.isNotEmpty() }?.average() ?: 450.0
        val stablePitch = pitches.size >= 5 && pitches.size >= voicedWindows * 0.7 &&
            sqrt(pitches.sumOf { (it - averagePitch) * (it - averagePitch) } / pitches.size) / averagePitch < 0.12
        val intervals = peaks.zipWithNext { a, b -> (b - a) * window * 1000.0 / sampleRate }
        val repetition = intervals.isNotEmpty() && intervals.all { it in 200.0..400.0 } &&
            intervals.all { abs(it - intervals.average()) <= intervals.average() * 0.25 }
        val voicedDuration = energy.count { it >= threshold } * window * 1000L / sampleRate
        val tone = if (maximum < threshold) VoiceTone.BABBLING_NEUTRAL
            else classifyTone(averagePitch, durationMs)
        val intent = when {
            repetition -> UtteranceIntent.BABBLE_REPETITION
            peaks.size == 1 && durationMs >= 400 && voicedDuration >= 400 && stablePitch -> UtteranceIntent.BABBLE_VOWEL
            tone == VoiceTone.COOING_HAPPY -> UtteranceIntent.GENERAL_HAPPY
            tone == VoiceTone.FUSS_WHINE -> UtteranceIntent.GENERAL_FUSS
            else -> UtteranceIntent.GENERAL_NEUTRAL
        }
        return UtteranceUnderstanding(
            intent = intent,
            recognizedText = null,
            detectedKeyword = null,
            tone = tone,
            confidence = if (repetition || intent == UtteranceIntent.BABBLE_VOWEL)
                UnderstandingConfidence.MEDIUM else UnderstandingConfidence.LOW,
            source = if (maximum >= threshold) UnderstandingSource.ACOUSTIC else UnderstandingSource.FALLBACK,
            durationMs = durationMs
        )
    }
}
