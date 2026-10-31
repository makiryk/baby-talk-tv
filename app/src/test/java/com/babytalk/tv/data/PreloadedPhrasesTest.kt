package com.babytalk.tv.data

import com.babytalk.tv.audio.UtteranceIntent
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreloadedPhrasesTest {
    @Test
    fun everyIntentHasAtLeastOneUnderstoodPhrase() {
        val file = listOf("src/main/assets/preloaded_phrases.json", "app/src/main/assets/preloaded_phrases.json")
            .map(::File).first { it.exists() }
        val arr = JSONArray(file.readText(Charsets.UTF_8))
        val tones = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.getString("category") == PhraseRepository.UNDERSTOOD_CATEGORY }
            .map { it.getString("tone") }.toSet()
        for (intent in UtteranceIntent.values()) {
            assertTrue("${intent.name} のフレーズがない", intent.name in tones)
        }
    }
}
