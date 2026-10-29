package com.babytalk.tv.network

import android.util.Base64
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Cloud Text-to-Speech REST API クライアント (ja-JP-Neural2-B)。
 * 事前合成してローカルキャッシュするために使う。失敗時は null を返し、呼び出し側は組み込みTTSへフォールバックする。
 */
class GoogleCloudTtsClient(
    private val apiKey: String
) {
    companion object {
        private const val ENDPOINT = "https://texttospeech.googleapis.com/v1/text:synthesize"
        private const val VOICE_NAME = "ja-JP-Neural2-B"
        private const val LANGUAGE_CODE = "ja-JP"

        /** XML 特殊文字を SSML 用にエスケープする */
        fun escapeSsml(text: String): String = buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }

        fun buildSsml(text: String): String =
            "<speak><prosody pitch=\"+3st\" rate=\"88%\">${escapeSsml(text)}</prosody></speak>"

        fun buildRequestJson(text: String): String = JSONObject().apply {
            put("input", JSONObject().put("ssml", buildSsml(text)))
            put("voice", JSONObject().put("languageCode", LANGUAGE_CODE).put("name", VOICE_NAME))
            put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
        }.toString()

        /** レスポンス JSON から audioContent(Base64) を取り出す。無ければ null */
        fun extractAudioContent(responseJson: String): String? = try {
            JSONObject(responseJson).optString("audioContent", "").ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /** テキストを合成して MP3 バイト列を返す。失敗時は null。 */
    fun synthesize(text: String): ByteArray? {
        return try {
            val conn = (URL("$ENDPOINT?key=$apiKey").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("content-type", "application/json; charset=utf-8")
            }
            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(buildRequestJson(text)) }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val audio = extractAudioContent(body) ?: return null
                Base64.decode(audio, Base64.DEFAULT)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
