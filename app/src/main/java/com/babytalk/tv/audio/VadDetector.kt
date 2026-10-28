package com.babytalk.tv.audio

import kotlin.math.sqrt

/**
 * 赤ちゃんの声（喃語、クーイング、泣き声）を検出するVAD＆音響分析エンジン。
 * - H3対応: 自己発話ループ（エコー）を遮断するミュートゲート
 * - H4対応: F0周波数帯域 250Hz〜1000Hz、適応型ノイズフロア
 */
class VadDetector(
    private val sampleRate: Int = 16000,
    private val onVoiceDetected: (VoiceEvent) -> Unit
) {
    // エコー遮断用ミュートフラグ (TTS発話中〜完了後500msは true になる)
    @Volatile
    var isMuted: Boolean = false

    // 適応型ノイズフロア管理
    private var noiseFloorRms = 150.0
    private val noiseAlpha = 0.05 // 移動平均の更新係数

    // 音声区間検出用状態
    private var isVoicing = false
    private var voiceStartTimestamp = 0L
    private val pitchSamples = mutableListOf<Float>()

    companion object {
        private const val MIN_VOICE_DURATION_MS = 250L  // 最低発声長 (0.25秒)
        private const val MAX_VOICE_DURATION_MS = 3500L // 最大発声長 (3.5秒)
        private const val SNR_THRESHOLD_RATIO = 2.2     // ノイズフロアに対するエネルギー比率
    }

    /**
     * PCM 16bit データを解析
     */
    fun processFrame(buffer: ShortArray, readSize: Int) {
        // H3対応: ミュート中はフレームを破棄し、状態をリセット
        if (isMuted) {
            resetVoiceState()
            return
        }

        val rms = calculateRms(buffer, readSize)

        // ノイズフロアの動的更新（無音時にゆっくり適応）
        if (rms < noiseFloorRms * 1.5) {
            noiseFloorRms = (1.0 - noiseAlpha) * noiseFloorRms + noiseAlpha * rms
        }

        val isSpeechFrame = rms > (noiseFloorRms * SNR_THRESHOLD_RATIO)

        val currentTime = System.currentTimeMillis()

        if (isSpeechFrame) {
            if (!isVoicing) {
                // 発声開始
                isVoicing = true
                voiceStartTimestamp = currentTime
                pitchSamples.clear()
            }

            // ピッチ推定 (250Hz〜1000Hz)
            val pitch = estimatePitch(buffer, readSize, sampleRate)
            if (pitch in 250.0..1000.0) {
                pitchSamples.add(pitch.toFloat())
            }

            // 長すぎる場合は強制区切り
            if (currentTime - voiceStartTimestamp > MAX_VOICE_DURATION_MS) {
                finishVoicing(currentTime)
            }
        } else {
            if (isVoicing) {
                // 発声終了
                finishVoicing(currentTime)
            }
        }
    }

    private fun finishVoicing(endTime: Long) {
        val duration = endTime - voiceStartTimestamp
        if (duration >= MIN_VOICE_DURATION_MS) {
            val avgPitch = if (pitchSamples.isNotEmpty()) pitchSamples.average().toFloat() else 450f
            val tone = classifyTone(avgPitch, duration)
            onVoiceDetected(VoiceEvent(tone = tone, durationMs = duration))
        }
        resetVoiceState()
    }

    private fun resetVoiceState() {
        isVoicing = false
        pitchSamples.clear()
        voiceStartTimestamp = 0L
    }

    /**
     * RMS (二乗平均平方根) 音量計算
     */
    private fun calculateRms(buffer: ShortArray, size: Int): Double {
        if (size == 0) return 0.0
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / size)
    }

    /**
     * 自己相関法 (Autocorrelation) によるピッチ推定 (250Hz〜1000Hz)
     */
    private fun estimatePitch(buffer: ShortArray, size: Int, sampleRate: Int): Double {
        val minLag = sampleRate / 1000 // 1000Hz
        val maxLag = sampleRate / 250  // 250Hz

        if (size <= maxLag) return -1.0

        var bestLag = -1
        var maxCorr = 0.0

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until (size - lag)) {
                corr += buffer[i].toDouble() * buffer[i + lag].toDouble()
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        return if (bestLag > 0) {
            sampleRate.toDouble() / bestLag
        } else {
            -1.0
        }
    }

    /**
     * 平均ピッチと継続時間によるトーン分類
     */
    private fun classifyTone(avgPitch: Float, durationMs: Long): VoiceTone {
        return when {
            avgPitch > 550f && durationMs < 1200L -> VoiceTone.COOING_HAPPY
            durationMs > 1800L || avgPitch < 320f -> VoiceTone.FUSS_WHINE
            else -> VoiceTone.BABBLING_NEUTRAL
        }
    }
}
