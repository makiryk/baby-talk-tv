package com.babytalk.tv.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class VadDetectorTest {
    private fun speech(size: Int = 160) = ShortArray(size) {
        (sin(2 * Math.PI * 400 * it / 16000) * 15000).toInt().toShort()
    }
    private fun feed(detector: VadDetector, frame: ShortArray, count: Int) {
        repeat(count) { detector.processFrame(frame, frame.size) }
    }

    @Test fun preRollAndPcmArePreservedAndCopied() {
        val events = mutableListOf<VoiceEvent>()
        val detector = VadDetector { events.add(it) }
        val quiet = ShortArray(160) { 20 }
        val voice = speech()
        feed(detector, quiet, 20)
        feed(detector, voice, 30)
        feed(detector, ShortArray(160), 24)
        val event = events.single()
        assertEquals(680L, event.durationMs)
        assertEquals(10880, event.audioPcm.size)
        assertTrue(event.audioPcm.take(2240).all { it == 20.toShort() })
        assertArrayEquals(voice, event.audioPcm.copyOfRange(2240, 2400))
        val saved = event.audioPcm.copyOf()
        voice.fill(0)
        feed(detector, speech(), 30)
        feed(detector, ShortArray(160), 24)
        assertArrayEquals(saved, event.audioPcm)
    }

    @Test fun shortPauseJoinsAndLongPauseSeparates() {
        val events = mutableListOf<VoiceEvent>()
        val detector = VadDetector { events.add(it) }
        feed(detector, speech(), 30)
        feed(detector, ShortArray(160), 20)
        assertTrue(events.isEmpty())
        feed(detector, speech(), 30)
        feed(detector, ShortArray(160), 24)
        assertEquals(1, events.size)
        assertEquals(1040L, events.single().durationMs)
        feed(detector, speech(), 30)
        feed(detector, ShortArray(160), 24)
        assertEquals(2, events.size)
    }

    @Test fun muteResetsActiveAudioAndPreRollEvenWithoutMutedFrames() {
        val events = mutableListOf<VoiceEvent>()
        val detector = VadDetector { events.add(it) }
        feed(detector, speech(), 30)
        detector.isMuted = true
        feed(detector, speech(), 400)
        detector.isMuted = false
        feed(detector, speech(), 10)
        feed(detector, ShortArray(160), 24)
        assertTrue(events.isEmpty())
        feed(detector, ShortArray(160) { 20 }, 20)
        detector.isMuted = true
        detector.isMuted = false
        feed(detector, speech(), 25)
        feed(detector, ShortArray(160), 24)
        assertEquals(490L, events.single().durationMs)
    }

    @Test fun sampleTimingHonorsMinimumAndMaximumWithoutSleeping() {
        val events = mutableListOf<VoiceEvent>()
        val detector = VadDetector { events.add(it) }
        feed(detector, speech(), 24)
        feed(detector, ShortArray(160), 24)
        assertTrue(events.isEmpty())
        // A single large input is also split at the exact maximum length.
        detector.processFrame(speech(16000 * 4), 16000 * 4)
        assertEquals(3500L, events.single().durationMs)
        assertEquals(56000, events.single().audioPcm.size)
    }

    @Test fun readSizeExcludesUnusedBufferTail() {
        val events = mutableListOf<VoiceEvent>()
        val detector = VadDetector { events.add(it) }
        val frame = speech() + ShortArray(160) { Short.MAX_VALUE }
        repeat(25) { detector.processFrame(frame, 160) }
        feed(detector, ShortArray(160), 24)
        assertEquals(7840, events.single().audioPcm.size)
        assertFalse(events.single().audioPcm.contains(Short.MAX_VALUE))
    }
}
