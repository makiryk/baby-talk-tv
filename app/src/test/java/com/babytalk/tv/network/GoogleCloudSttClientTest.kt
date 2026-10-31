package com.babytalk.tv.network

import com.babytalk.tv.audio.UtteranceIntent
import com.babytalk.tv.audio.VoiceEvent
import com.babytalk.tv.audio.VoiceTone
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class GoogleCloudSttClientTest {

    @Test
    fun buildWav_hasValidHeader() {
        val wav = GoogleCloudSttClient.buildWav(shortArrayOf(1, -2, 3))
        assertEquals(44 + 6, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 6, b.getInt(4))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(28))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4))
        assertEquals(6, b.getInt(40))
        assertEquals(-2, b.getShort(46).toInt())
    }

    @Test
    fun buildRequestJson_hasExpectedStructure() {
        val wav = byteArrayOf(1, 2, 3)
        val json = JSONObject(GoogleCloudSttClient.buildRequestJson(wav))
        val config = json.getJSONObject("config")
        assertEquals("LINEAR16", config.getString("encoding"))
        assertEquals(16000, config.getInt("sampleRateHertz"))
        assertEquals("ja-JP", config.getString("languageCode"))
        assertEquals(
            Base64.getEncoder().encodeToString(wav),
            json.getJSONObject("audio").getString("content")
        )
    }

    @Test
    fun extractTranscripts_readsAlternatives() {
        val body = """{"results":[{"alternatives":[{"transcript":"わんわん"},{"transcript":"ワンワン"}]}]}"""
        assertEquals(listOf("わんわん", "ワンワン"), GoogleCloudSttClient.extractTranscripts(body))
    }

    @Test
    fun extractTranscripts_emptyOrInvalidGivesEmpty() {
        assertTrue(GoogleCloudSttClient.extractTranscripts("{}").isEmpty())
        assertTrue(GoogleCloudSttClient.extractTranscripts("not json").isEmpty())
    }

    @Test
    fun matchKeyword_mapsAllIntents() {
        val cases = mapOf(
            "まんま" to UtteranceIntent.WORD_MAMMA,
            "ごはん" to UtteranceIntent.WORD_MAMMA,
            "もぐもぐ" to UtteranceIntent.WORD_MAMMA,
            "わんわん" to UtteranceIntent.WORD_WANWAN,
            "いぬ" to UtteranceIntent.WORD_WANWAN,
            "ばあ" to UtteranceIntent.WORD_BAA,
            "いないいない" to UtteranceIntent.WORD_BAA,
            "ぶーぶー" to UtteranceIntent.WORD_BUUBUU,
            "くるま" to UtteranceIntent.WORD_BUUBUU,
            "パパ" to UtteranceIntent.WORD_PAPA,
            "ママ" to UtteranceIntent.WORD_MAMA,
            "おはよう" to UtteranceIntent.WORD_GREETING,
            "こんにちは" to UtteranceIntent.WORD_GREETING,
            "ばいばい" to UtteranceIntent.WORD_BYE,
            "またね" to UtteranceIntent.WORD_BYE,
            "ねんね" to UtteranceIntent.WORD_NENNE,
            "ねむい" to UtteranceIntent.WORD_NENNE
        )
        for ((text, intent) in cases) {
            assertEquals(text, intent, GoogleCloudSttClient.matchKeyword(listOf(text))?.first)
        }
    }

    @Test
    fun matchKeyword_normalizesKatakanaAndSpaces() {
        assertEquals(UtteranceIntent.WORD_WANWAN, GoogleCloudSttClient.matchKeyword(listOf("ワン ワン"))?.first)
        assertEquals(UtteranceIntent.WORD_BUUBUU, GoogleCloudSttClient.matchKeyword(listOf("ブーブー"))?.first)
    }

    @Test
    fun matchKeyword_noMatchReturnsNull() {
        assertNull(GoogleCloudSttClient.matchKeyword(listOf("あうあう")))
        assertNull(GoogleCloudSttClient.matchKeyword(emptyList()))
    }

    @Test
    fun recognize_returnsNullWhenApiKeyMissing() = runTest {
        val event = VoiceEvent(VoiceTone.BABBLING_NEUTRAL, 500, audioPcm = ShortArray(8000))
        assertNull(GoogleCloudSttClient({ null }).recognize(event))
        assertNull(GoogleCloudSttClient({ "  " }).recognize(event))
    }

    @Test
    fun recognize_returnsNullWhenNoAudio() = runTest {
        assertNull(GoogleCloudSttClient({ "key" }).recognize(VoiceEvent(VoiceTone.BABBLING_NEUTRAL, 500)))
    }
}
