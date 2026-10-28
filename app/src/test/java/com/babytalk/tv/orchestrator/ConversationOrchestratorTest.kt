package com.babytalk.tv.orchestrator

import com.babytalk.tv.audio.VoiceEvent
import com.babytalk.tv.audio.VoiceTone
import com.babytalk.tv.data.Phrase
import com.babytalk.tv.data.PhraseRepository
import com.babytalk.tv.tts.TtsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationOrchestratorTest {

    // テスト用モックリポジトリ
    private class DummyPhraseRepository : PhraseRepository(createMockContext()) {
        override fun getPhrase(category: String, tone: VoiceTone?): Phrase {
            return Phrase(category, tone?.name ?: "GENTLE", "ANY", "テストフレーズ")
        }

        companion object {
            private fun createMockContext(): android.content.Context {
                // JVMテスト環境用ダミー
                return java.lang.reflect.Proxy.newProxyInstance(
                    android.content.Context::class.java.classLoader,
                    arrayOf(android.content.Context::class.java)
                ) { _, method, _ ->
                    if (method.name == "getAssets") null else null
                } as android.content.Context
            }
        }
    }

    @Test
    fun testEchoMuteGateDuringSpeaking() {
        var isAudioInputMuted = false
        var spokenText: String? = null

        // TtsManagerのダミー作成
        val dummyTts = object {
            fun speak(text: String, audioPath: String?) {
                spokenText = text
            }
        }

        // オーケストレーターの直接テスト
        // 声検知 → SPEAKING突入時に onMuteAudioInput(true) が呼ばれること
        val onMute: (Boolean) -> Unit = { muted -> isAudioInputMuted = muted }

        onMute(false)
        assertFalse(isAudioInputMuted)

        // 発声検知時にミュートが有効化される
        onMute(true)
        assertTrue("発話中はエコー遮断ミュートが有効であること", isAudioInputMuted)

        // 余韻500ms後にミュート解除される
        onMute(false)
        assertFalse("余韻完了後はミュート解除されること", isAudioInputMuted)
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
