package com.babytalk.tv.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VadDetectorTest {

    @Test
    fun testMuteGateDiscardsAudioWhenMuted() {
        var detectedEvent: VoiceEvent? = null
        val detector = VadDetector(sampleRate = 16000) { event ->
            detectedEvent = event
        }

        // ミュート状態に設定
        detector.isMuted = true

        // 400Hz の大音量正弦波（強い音声）を作成
        val buffer = createSineWaveBuffer(freq = 400.0, amplitude = 20000.0, size = 1024)

        // 複数フレーム投入
        repeat(30) {
            detector.processFrame(buffer, buffer.size)
        }

        // ミュート中なので検知されないこと
        assertEquals(null, detectedEvent)
        assertTrue(detector.isMuted)
    }

    @Test
    fun testDetectionWhenUnmuted() {
        var detected = false
        val detector = VadDetector(sampleRate = 16000) {
            detected = true
        }

        detector.isMuted = false

        // 450Hz の正弦波バッファ
        val speechBuffer = createSineWaveBuffer(freq = 450.0, amplitude = 15000.0, size = 1024)
        val silenceBuffer = ShortArray(1024) { 0 }

        // 発声をシミュレート (約300ms = 約5フレーム)
        repeat(6) {
            detector.processFrame(speechBuffer, speechBuffer.size)
        }

        // 無音フレームを投入して発声終了をトリガー
        repeat(3) {
            detector.processFrame(silenceBuffer, silenceBuffer.size)
        }

        assertTrue("ミュート解除時は有効な発声が検知されること", detected)
    }

    private fun createSineWaveBuffer(freq: Double, amplitude: Double, size: Int): ShortArray {
        val buffer = ShortArray(size)
        for (i in 0 until size) {
            val angle = 2.0 * Math.PI * freq * i / 16000.0
            buffer[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return buffer
    }
}
