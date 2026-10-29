package com.babytalk.tv.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleCloudTtsClientTest {

    @Test
    fun escapeSsml_escapesXmlSpecialCharacters() {
        assertEquals(
            "a&amp;b&lt;c&gt;d&quot;e&apos;f",
            GoogleCloudTtsClient.escapeSsml("a&b<c>d\"e'f")
        )
    }

    @Test
    fun escapeSsml_keepsJapaneseText() {
        assertEquals("ぱたぱた　ぽん", GoogleCloudTtsClient.escapeSsml("ぱたぱた　ぽん"))
    }

    @Test
    fun buildSsml_wrapsWithProsodyAndEscapes() {
        assertEquals(
            "<speak><prosody pitch=\"+3st\" rate=\"88%\">&lt;ばあ&gt;</prosody></speak>",
            GoogleCloudTtsClient.buildSsml("<ばあ>")
        )
    }

    @Test
    fun buildRequestJson_hasExpectedStructure() {
        val json = JSONObject(GoogleCloudTtsClient.buildRequestJson("こんにちは"))
        assertEquals(
            "<speak><prosody pitch=\"+3st\" rate=\"88%\">こんにちは</prosody></speak>",
            json.getJSONObject("input").getString("ssml")
        )
        val voice = json.getJSONObject("voice")
        assertEquals("ja-JP", voice.getString("languageCode"))
        assertEquals("ja-JP-Neural2-B", voice.getString("name"))
        assertEquals("MP3", json.getJSONObject("audioConfig").getString("audioEncoding"))
    }

    @Test
    fun extractAudioContent_returnsBase64String() {
        assertEquals("QUJD", GoogleCloudTtsClient.extractAudioContent("{\"audioContent\":\"QUJD\"}"))
    }

    @Test
    fun extractAudioContent_returnsNullOnMissingOrInvalid() {
        assertNull(GoogleCloudTtsClient.extractAudioContent("{}"))
        assertNull(GoogleCloudTtsClient.extractAudioContent("not json"))
    }
}
