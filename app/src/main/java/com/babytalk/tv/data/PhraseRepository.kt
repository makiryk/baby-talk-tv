package com.babytalk.tv.data

import android.content.Context
import com.babytalk.tv.audio.VoiceTone
import com.babytalk.tv.data.db.AppDatabase
import com.babytalk.tv.data.db.CachedPhraseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Calendar

data class Phrase(
    val category: String, // REACTIVE, IDLE_AUTONOMOUS, CLOSING
    val tone: String,     // COOING_HAPPY, BABBLING_NEUTRAL, FUSS_WHINE, GENTLE
    val timeOfDay: String,
    val text: String,
    val audioPath: String? = null
)

/**
 * フレーズキャッシュリポジトリ。
 * - Room DBキャッシュからの段階的フォールバック抽出
 * - 初期シード（preloaded_phrases.json）の自動インポート
 * - 使用履歴カウント管理
 */
class PhraseRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.phraseDao()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // メモリ内フォールバック用
    private val fallbackPhrases = mutableListOf<Phrase>()

    init {
        loadPreloadedPhrasesToDatabase()
    }

    private fun loadPreloadedPhrasesToDatabase() {
        scope.launch {
            try {
                val inputStream = context.assets.open("preloaded_phrases.json")
                val reader = InputStreamReader(inputStream)
                val jsonString = reader.readText()
                reader.close()

                val jsonArray = JSONArray(jsonString)
                val entityList = mutableListOf<CachedPhraseEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val category = obj.getString("category")
                    val tone = obj.getString("tone")
                    val timeOfDay = obj.optString("timeOfDay", "ANY")
                    val text = obj.getString("text")

                    fallbackPhrases.add(Phrase(category, tone, timeOfDay, text))

                    val hash = sha256(text)
                    entityList.add(
                        CachedPhraseEntity(
                            textHash = hash,
                            category = category,
                            tone = tone,
                            timeOfDay = timeOfDay,
                            text = text
                        )
                    )
                }

                // DBへの初期シード投入
                dao.insertPhrases(entityList)
            } catch (e: Exception) {
                // 最低限のフォールバック
                fallbackPhrases.add(Phrase("REACTIVE", "COOING_HAPPY", "ANY", "たのしいねぇ！"))
                fallbackPhrases.add(Phrase("REACTIVE", "BABBLING_NEUTRAL", "ANY", "なあに？きいてるよ。"))
                fallbackPhrases.add(Phrase("REACTIVE", "FUSS_WHINE", "ANY", "どうしたの？だいじょうぶだよ。"))
                fallbackPhrases.add(Phrase("IDLE_AUTONOMOUS", "GENTLE", "ANY", "いま何してるのかな？"))
                fallbackPhrases.add(Phrase("CLOSING", "GENTLE", "ANY", "きょうはいっぱいお話ししたね。おやすみなさい。"))
            }
        }
    }

    /**
     * M6対応: 段階的フォールバック付きフレーズ選定
     */
    fun getPhrase(category: String, tone: VoiceTone? = null): Phrase {
        val toneString = tone?.name ?: "GENTLE"
        val currentTimeOfDay = getCurrentTimeOfDay()

        return runBlocking(Dispatchers.IO) {
            // Step 1: 完全一致
            var entity = dao.getExactPhrase(category, toneString, currentTimeOfDay)

            // Step 2: トーン緩和
            if (entity == null) {
                entity = dao.getPhraseRelaxTone(category, currentTimeOfDay)
            }

            // Step 3: 時間帯緩和
            if (entity == null) {
                entity = dao.getPhraseAnyTime(category)
            }

            if (entity != null) {
                dao.incrementUsage(entity.id)
                Phrase(
                    category = entity.category,
                    tone = entity.tone,
                    timeOfDay = entity.timeOfDay,
                    text = entity.text,
                    audioPath = entity.audioFilePath
                )
            } else {
                // Step 4: メモリ内フォールバック
                val filtered = fallbackPhrases.filter { it.category == category }
                if (filtered.isNotEmpty()) {
                    filtered.random()
                } else {
                    Phrase(category, "GENTLE", "ANY", "うんうん、たのしいね。")
                }
            }
        }
    }

    private fun getCurrentTimeOfDay(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "MORNING"
            in 11..16 -> "DAY"
            in 17..20 -> "EVENING"
            else -> "NIGHT"
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
