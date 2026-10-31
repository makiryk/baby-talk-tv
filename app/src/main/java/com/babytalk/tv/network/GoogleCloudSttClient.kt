package com.babytalk.tv.network

import com.babytalk.tv.audio.SpeechRecognizer
import com.babytalk.tv.audio.UnderstandingConfidence
import com.babytalk.tv.audio.UnderstandingSource
import com.babytalk.tv.audio.UtteranceIntent
import com.babytalk.tv.audio.UtteranceUnderstanding
import com.babytalk.tv.audio.VoiceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Google Cloud Speech-to-Text v1 REST クライアント。
 * 失敗・未設定・タイムアウト・認識結果なしはすべて null を返し、呼び出し側が音響判定へフォールバックする。
 */
class GoogleCloudSttClient(
    private val apiKeyProvider: () -> String?,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val sampleRate: Int = SAMPLE_RATE
) : SpeechRecognizer {
    companion object {
        private const val ENDPOINT = "https://speech.googleapis.com/v1/speech:recognize"
        const val SAMPLE_RATE = 16000
        const val DEFAULT_TIMEOUT_MS = 700L

        /** 認識テキストのキーワード表（ひらがな正規化後に部分一致。長いものを先に判定する） */
        private val KEYWORDS: List<Pair<UtteranceIntent, List<String>>> = listOf(
            UtteranceIntent.WORD_BYE to listOf("ばいばい", "またね"),
            UtteranceIntent.WORD_BAA to listOf("いないいない", "ばあ"),
            UtteranceIntent.WORD_GREETING to listOf("おはよう", "こんにちは"),
            UtteranceIntent.WORD_NENNE to listOf("ねんね", "ねむい", "眠い"),
            UtteranceIntent.WORD_MAMMA to listOf("まんま", "ごはん", "ご飯", "もぐもぐ"),
            UtteranceIntent.WORD_WANWAN to listOf("わんわん", "いぬ", "犬"),
            UtteranceIntent.WORD_BUUBUU to listOf("ぶーぶー", "ぶうぶう", "くるま", "車"),
            UtteranceIntent.WORD_PAPA to listOf("ぱぱ"),
            UtteranceIntent.WORD_MAMA to listOf("まま")
        )

        /** 16bit モノラル LINEAR16 PCM に 44 バイトの WAV ヘッダーを付ける */
        fun buildWav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
            val dataSize = pcm.size * 2
            val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            buf.putInt(36 + dataSize)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(16)
            buf.putShort(1) // PCM
            buf.putShort(1) // mono
            buf.putInt(sampleRate)
            buf.putInt(sampleRate * 2) // byte rate
            buf.putShort(2) // block align
            buf.putShort(16) // bits per sample
            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(dataSize)
            pcm.forEach { buf.putShort(it) }
            return buf.array()
        }

        fun buildRequestJson(wav: ByteArray, sampleRate: Int = SAMPLE_RATE): String = JSONObject().apply {
            put("config", JSONObject()
                .put("encoding", "LINEAR16")
                .put("sampleRateHertz", sampleRate)
                .put("languageCode", "ja-JP")
                .put("maxAlternatives", 3))
            put("audio", JSONObject().put("content", Base64.getEncoder().encodeToString(wav)))
        }.toString()

        /** レスポンス JSON から候補テキスト（信頼度順）を取り出す。結果なし・不正な JSON は空リスト */
        fun extractTranscripts(responseJson: String): List<String> = try {
            val results = JSONObject(responseJson).optJSONArray("results") ?: JSONArray()
            val out = mutableListOf<String>()
            for (i in 0 until results.length()) {
                val alts = results.getJSONObject(i).optJSONArray("alternatives") ?: continue
                for (j in 0 until alts.length()) {
                    alts.getJSONObject(j).optString("transcript", "").takeIf { it.isNotBlank() }?.let { out.add(it) }
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }

        /** カタカナ→ひらがな、空白除去。 */
        fun normalize(text: String): String = buildString(text.length) {
            for (c in text) {
                when {
                    c.isWhitespace() || c == '　' -> {}
                    c in 'ァ'..'ヶ' -> append((c.code - 0x60).toChar())
                    else -> append(c)
                }
            }
        }

        /** 認識テキスト群から意図キーワードを探す。見つからなければ null（hit = 一致したキーワード） */
        fun matchKeyword(transcripts: List<String>): Pair<UtteranceIntent, String>? {
            for (text in transcripts) {
                val norm = normalize(text)
                for ((intent, words) in KEYWORDS) {
                    words.firstOrNull { norm.contains(it) }?.let { return intent to it }
                }
            }
            return null
        }
    }

    override suspend fun recognize(event: VoiceEvent): UtteranceUnderstanding? {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return null
        if (event.audioPcm.isEmpty()) return null
        val transcripts = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) { requestTranscripts(key, event.audioPcm) }
        } ?: return null
        val (intent, keyword) = matchKeyword(transcripts) ?: return null
        return UtteranceUnderstanding(
            intent = intent,
            recognizedText = transcripts.first(),
            detectedKeyword = keyword,
            tone = event.tone,
            confidence = UnderstandingConfidence.HIGH,
            source = UnderstandingSource.STT,
            durationMs = event.durationMs
        )
    }

    private fun requestTranscripts(apiKey: String, pcm: ShortArray): List<String>? = try {
        val conn = (URL("$ENDPOINT?key=$apiKey").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            setRequestProperty("content-type", "application/json; charset=utf-8")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(buildRequestJson(buildWav(pcm, sampleRate), sampleRate))
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else extractTranscripts(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
