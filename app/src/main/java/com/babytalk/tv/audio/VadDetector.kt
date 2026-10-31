package com.babytalk.tv.audio

import kotlin.math.sqrt

/** Sample-count based VAD: timing is independent of capture thread scheduling. */
class VadDetector(
    private val sampleRate: Int = 16000,
    private val onVoiceDetected: (VoiceEvent) -> Unit
) {
    init { require(sampleRate >= 1000) }

    @Volatile
    var isMuted: Boolean = false
        @Synchronized set(value) {
            field = value
            if (value) resetVoiceState()
        }

    private var noiseFloorRms = 150.0
    private val preRoll = ShortArray(sampleRate * 140 / 1000)
    private var prePosition = 0
    private var preSize = 0
    private val pcm = ShortArray(sampleRate * 3500 / 1000)
    private var pcmSize = 0
    private var activeSamples = 0
    private var silenceSamples = 0
    private var isVoicing = false
    private val pitches = mutableListOf<Double>()

    @Synchronized
    fun processFrame(buffer: ShortArray, readSize: Int) {
        if (isMuted) { resetVoiceState(); return }
        require(readSize in 0..buffer.size)
        // Bound processing chunks so large caller buffers obey the same timing limits.
        val chunkSize = (sampleRate / 100).coerceAtLeast(1)
        var offset = 0
        while (offset < readSize) {
            val size = minOf(chunkSize, readSize - offset,
                if (isVoicing) pcm.size - pcmSize else chunkSize)
            processChunk(buffer.copyOfRange(offset, offset + size))
            offset += size
        }
    }

    private fun processChunk(frame: ShortArray) {
        val rms = sqrt(frame.sumOf { it.toDouble() * it } / frame.size)
        if (!isVoicing && rms < noiseFloorRms * 1.5) {
            noiseFloorRms = (noiseFloorRms * 0.95 + rms * 0.05).coerceAtLeast(50.0)
        }
        val speech = rms > noiseFloorRms * 2.2
        if (!isVoicing && !speech) {
            for (sample in frame) {
                preRoll[prePosition] = sample
                prePosition = (prePosition + 1) % preRoll.size
                preSize = minOf(preSize + 1, preRoll.size)
            }
            return
        }
        if (!isVoicing) {
            isVoicing = true
            for (i in 0 until preSize) {
                pcm[pcmSize++] = preRoll[(prePosition - preSize + i + preRoll.size) % preRoll.size]
            }
            preSize = 0
        }
        val count = minOf(frame.size, pcm.size - pcmSize)
        frame.copyInto(pcm, pcmSize, 0, count)
        pcmSize += count
        if (speech) {
            activeSamples += count
            silenceSamples = 0
            estimatePitch(frame, sampleRate)?.let { pitches.add(it) }
        } else {
            silenceSamples += count
        }
        if (pcmSize == pcm.size || silenceSamples >= sampleRate * 240 / 1000) finishVoicing()
    }

    private fun finishVoicing() {
        val duration = pcmSize * 1000L / sampleRate
        val event = if (activeSamples * 1000L / sampleRate >= 250) {
            VoiceEvent(classifyTone(pitches.takeIf { it.isNotEmpty() }?.average() ?: 450.0, duration),
                duration, audioPcm = pcm.copyOf(pcmSize))
        } else null
        resetVoiceState()
        event?.let(onVoiceDetected)
    }

    private fun resetVoiceState() {
        isVoicing = false
        pcmSize = 0
        activeSamples = 0
        silenceSamples = 0
        prePosition = 0
        preSize = 0
        pitches.clear()
    }
}

internal fun classifyTone(avgPitch: Double, durationMs: Long): VoiceTone = when {
    avgPitch > 550 && durationMs < 1200 -> VoiceTone.COOING_HAPPY
    durationMs > 1800 || avgPitch < 320 -> VoiceTone.FUSS_WHINE
    else -> VoiceTone.BABBLING_NEUTRAL
}

/** Normalized autocorrelation; choose the first strong local maximum to avoid octave errors. */
internal fun estimatePitch(pcm: ShortArray, sampleRate: Int): Double? {
    val minLag = (sampleRate / 1000).coerceAtLeast(1)
    val maxLag = sampleRate / 250
    if (pcm.size <= maxLag + 1) return null
    val mean = pcm.average()
    val correlations = DoubleArray(maxLag + 2)
    for (lag in minLag..maxLag + 1) {
        var cross = 0.0
        var left = 0.0
        var right = 0.0
        for (i in 0 until pcm.size - lag) {
            val a = pcm[i] - mean
            val b = pcm[i + lag] - mean
            cross += a * b
            left += a * a
            right += b * b
        }
        correlations[lag] = if (left * right > 0) cross / sqrt(left * right) else 0.0
    }
    for (lag in minLag..maxLag) {
        if (correlations[lag] >= 0.75 &&
            (lag == minLag || correlations[lag] >= correlations[lag - 1]) &&
            correlations[lag] >= correlations[lag + 1]) return sampleRate.toDouble() / lag
    }
    return null
}
