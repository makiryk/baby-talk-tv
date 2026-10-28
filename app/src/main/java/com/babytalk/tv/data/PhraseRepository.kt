package com.babytalk.tv.data

import android.content.Context
import com.babytalk.tv.audio.VoiceTone
import org.json.JSONArray
import java.io.InputStreamReader

data class Phrase(
    val category: String, // REACTIVE, IDLE_AUTONOMOUS, CLOSING
    val tone: String,     // COOING_HAPPY, BABBLING_NEUTRAL, FUSS_WHINE, GENTLE
    val timeOfDay: String,
    val text: String,
    val audioPath: String? = null
)

class PhraseRepository(private val context: Context) {

    private val phrases = mutableListOf<Phrase>()

    init {
        loadPreloadedPhrases()
    }

    private fun loadPreloadedPhrases() {
        try {
            val inputStream = context.assets.open("preloaded_phrases.json")
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                phrases.add(
                    Phrase(
                        category = obj.getString("category"),
                        tone = obj.getString("tone"),
                        timeOfDay = obj.optString("timeOfDay", "ANY"),
                        text = obj.getString("text")
                    )
                )
            }
        } catch (e: Exception) {
            // 最低限のフォールバック
            phrases.add(Phrase("REACTIVE", "COOING_HAPPY", "ANY", "たのしいねぇ！"))
            phrases.add(Phrase("REACTIVE", "BABBLING_NEUTRAL", "ANY", "なあに？きいてるよ。"))
            phrases.add(Phrase("REACTIVE", "FUSS_WHINE", "ANY", "どうしたの？だいじょうぶだよ。"))
            phrases.add(Phrase("IDLE_AUTONOMOUS", "GENTLE", "ANY", "いま何してるのかな？"))
            phrases.add(Phrase("CLOSING", "GENTLE", "ANY", "きょうはいっぱいお話ししたね。おやすみなさい。"))
        }
    }

    /**
     * M6対応: 段階的フォールバック付きフレーズ選定
     */
    fun getPhrase(category: String, tone: VoiceTone? = null): Phrase {
        val toneString = tone?.name ?: "GENTLE"

        // Step 1: カテゴリとトーン完全一致
        val step1 = phrases.filter { it.category == category && it.tone == toneString }
        if (step1.isNotEmpty()) return step1.random()

        // Step 2: カテゴリ一致
        val step2 = phrases.filter { it.category == category }
        if (step2.isNotEmpty()) return step2.random()

        // Step 3: 全体フォールバック
        return phrases.firstOrNull() ?: Phrase("REACTIVE", "GENTLE", "ANY", "うんうん、たのしいね。")
    }
}
