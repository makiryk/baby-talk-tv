package com.babytalk.tv.orchestrator

import com.babytalk.tv.audio.UnderstandingConfidence
import com.babytalk.tv.audio.UnderstandingSource
import com.babytalk.tv.audio.UtteranceIntent
import com.babytalk.tv.audio.UtteranceUnderstander
import com.babytalk.tv.audio.UtteranceUnderstanding
import com.babytalk.tv.audio.VoiceEvent
import com.babytalk.tv.audio.VoiceTone
import com.babytalk.tv.data.Phrase
import com.babytalk.tv.data.PhraseSource
import com.babytalk.tv.tts.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationOrchestratorTest {

    private class FakePhrases : PhraseSource {
        override fun getPhrase(category: String, tone: VoiceTone?) =
            Phrase(category, tone?.name ?: "GENTLE", "ANY", "$category:${tone?.name}")

        override fun getUnderstoodPhrase(understanding: UtteranceUnderstanding) =
            Phrase("UNDERSTOOD", understanding.intent.name, "ANY", "U:${understanding.intent.name}")
    }

    private class FakeSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        override fun speak(text: String, audioFilePath: String?) { spoken.add(text) }
        override fun stop() { stopCount++ }
    }

    private class FakeEngine(private val delayMs: Long = 0, private val gate: CompletableDeferred<Unit>? = null) :
        UtteranceUnderstander {
        var calls = 0
        override suspend fun understand(event: VoiceEvent): UtteranceUnderstanding {
            calls++
            gate?.await()
            if (delayMs > 0) delay(delayMs)
            return UtteranceUnderstanding(
                UtteranceIntent.WORD_MAMMA, "まんま", "まんま", event.tone,
                UnderstandingConfidence.HIGH, UnderstandingSource.STT, event.durationMs
            )
        }
    }

    private class Env(testScope: TestScope, engine: UtteranceUnderstander) {
        val speaker = FakeSpeaker()
        val mutes = mutableListOf<Boolean>()
        val orchestrator = ConversationOrchestrator(
            phraseRepository = FakePhrases(),
            ttsManager = speaker,
            speechUnderstandingEngine = engine,
            onMuteAudioInput = { mutes.add(it) },
            onSessionFinished = {},
            scope = CoroutineScope(StandardTestDispatcher(testScope.testScheduler) + Job())
        )
    }

    private val event = VoiceEvent(VoiceTone.BABBLING_NEUTRAL, 800)

    @Test
    fun voiceIsUnderstoodThenSpokenWithIntentPhrase() = runTest {
        val env = Env(this, FakeEngine(delayMs = 300))
        env.orchestrator.startSession(5)
        env.orchestrator.onVoiceDetected(event)
        assertEquals(ConversationState.LISTENING, env.orchestrator.state.value)
        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf("U:WORD_MAMMA"), env.speaker.spoken)
        assertEquals(ConversationState.SPEAKING, env.orchestrator.state.value)
        assertTrue("発話中はミュート", env.mutes.last())
        env.orchestrator.stopSession()
    }

    @Test
    fun stopSessionDuringUnderstandingDiscardsResult() = runTest {
        val env = Env(this, FakeEngine(delayMs = 300))
        env.orchestrator.startSession(5)
        env.orchestrator.onVoiceDetected(event)
        runCurrent()
        env.orchestrator.stopSession()
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(env.speaker.spoken.isEmpty())
        assertEquals(ConversationState.STANDBY, env.orchestrator.state.value)
    }

    @Test
    fun stopSessionCancelsPostSpeechWaitSoStateStaysStandby() = runTest {
        val env = Env(this, FakeEngine())
        env.orchestrator.startSession(5)
        env.orchestrator.onVoiceDetected(event)
        runCurrent()
        env.orchestrator.onUtteranceCompleted() // 500ms 余韻待機を開始
        env.orchestrator.stopSession()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(ConversationState.STANDBY, env.orchestrator.state.value)
        assertEquals(true, env.mutes.last())
    }

    @Test
    fun voiceWhileNotIdleIsIgnored() = runTest {
        val engine = FakeEngine(delayMs = 300)
        val env = Env(this, engine)
        env.orchestrator.startSession(5)
        env.orchestrator.onVoiceDetected(event)
        env.orchestrator.onVoiceDetected(event) // LISTENING 中
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, engine.calls)
        assertEquals(1, env.speaker.spoken.size)
        env.orchestrator.stopSession()
    }

    @Test
    fun staleResultFromPreviousSessionIsNotSpoken() = runTest {
        val gate = CompletableDeferred<Unit>()
        val env = Env(this, FakeEngine(gate = gate))
        env.orchestrator.startSession(5)
        env.orchestrator.onVoiceDetected(event)
        runCurrent()
        env.orchestrator.stopSession()
        env.orchestrator.startSession(5) // 新セッション（状態は IDLE）
        gate.complete(Unit)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(env.speaker.spoken.isEmpty())
        assertEquals(ConversationState.IDLE, env.orchestrator.state.value)
        env.orchestrator.stopSession()
    }

    @Test
    fun testConversationStates() {
        val states = ConversationState.values()
        assertTrue(states.contains(ConversationState.IDLE))
        assertTrue(states.contains(ConversationState.LISTENING))
        assertTrue(states.contains(ConversationState.SPEAKING))
        assertTrue(states.contains(ConversationState.CLOSING))
        assertTrue(states.contains(ConversationState.STANDBY))
    }
}
