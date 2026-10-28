package com.babytalk.tv.network

import com.babytalk.tv.BuildConfig
import com.babytalk.tv.data.db.CachedPhraseEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Anthropic Claude 公式 Message Batches API クライアント。
 * - 利用コスト50%オフで非同期一括生成を行う
 * - H5対応: BuildConfig からモデルIDを取得
 * - M2対応: 安全フィルタ（文字数・ネガティブ語・重複排除）
 */
class ClaudeBatchApiClient(
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/messages/batches"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val ANTHROPIC_BETA = "message-batches-2024-09-24"

        // ネガティブ・不適切語除外リスト
        private val BLOCKED_WORDS = listOf("だめ", "いけない", "こわい", "いたい", "おこる", "ばか", "しぬ", "きらい")
    }

    data class BatchCreateResponse(
        val batchId: String,
        val status: String
    )

    data class BatchStatusResponse(
        val batchId: String,
        val status: String, // in_progress, ended, canceling, expired
        val resultsUrl: String?
    )

    /**
     * 新規 Message Batch リクエストを投入 (POST /v1/messages/batches)
     */
    fun createBabyTalkBatch(timeOfDay: String = "ANY"): BatchCreateResponse? {
        val url = URL(BASE_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            setRequestProperty("anthropic-beta", ANTHROPIC_BETA)
            setRequestProperty("content-type", "application/json")
        }

        val modelId = BuildConfig.CLAUDE_MODEL_ID
        val prompt = "0〜2歳の赤ちゃんに優しく語りかける保育士AIとして、" +
                "赤ちゃんの声への相槌フレーズ(REACTIVE)と、自律語りかけフレーズ(IDLE_AUTONOMOUS)を合計15個生成してください。" +
                "出力は以下のJSON配列のみとし、説明文やマークダウンのコードフェンスは含めないでください:\n" +
                "[{\"category\": \"REACTIVE|IDLE_AUTONOMOUS\", \"tone\": \"COOING_HAPPY|BABBLING_NEUTRAL|FUSS_WHINE|GENTLE\", \"text\": \"ひらがな中心の短い文(20文字以内)\"}]"

        val systemPrompt = "あなたは赤ちゃんに安心感を与える優しい保育士AIです。短く、明るく、オノマトペを多用した安全な言葉のみを出力してください。"

        val requestJson = JSONObject().apply {
            val requestsArray = JSONArray()
            val singleRequest = JSONObject().apply {
                put("custom_id", "baby_talk_${System.currentTimeMillis()}")
                val params = JSONObject().apply {
                    put("model", modelId)
                    put("max_tokens", 1024)
                    put("system", systemPrompt)
                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                    put("messages", messages)
                }
                put("params", params)
            }
            requestsArray.put(singleRequest)
            put("requests", requestsArray)
        }

        return try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(requestJson.toString())
                it.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseString = conn.inputStream.bufferedReader().use { it.readText() }
                val respObj = JSONObject(responseString)
                BatchCreateResponse(
                    batchId = respObj.getString("id"),
                    status = respObj.getString("processing_status")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * バッチの処理ステータスを確認 (GET /v1/messages/batches/{id})
     */
    fun getBatchStatus(batchId: String): BatchStatusResponse? {
        val url = URL("$BASE_URL/$batchId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            setRequestProperty("anthropic-beta", ANTHROPIC_BETA)
        }

        return try {
            if (conn.responseCode in 200..299) {
                val responseString = conn.inputStream.bufferedReader().use { it.readText() }
                val respObj = JSONObject(responseString)
                BatchStatusResponse(
                    batchId = respObj.getString("id"),
                    status = respObj.getString("processing_status"),
                    resultsUrl = respObj.optString("results_url", null)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 完了したバッチの results_url から JSONL をダウンロードし、安全フィルタを通過したエンティティリストへ変換
     */
    fun fetchAndParseResults(resultsUrl: String): List<CachedPhraseEntity> {
        val url = URL(resultsUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30000
            readTimeout = 30000
        }

        val resultList = mutableListOf<CachedPhraseEntity>()

        try {
            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                var line: String? = reader.readLine()

                while (line != null) {
                    if (line.isNotBlank()) {
                        val parsed = parseJsonlLine(line)
                        resultList.addAll(parsed)
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            conn.disconnect()
        }

        return resultList
    }

    /**
     * JSONL 行からフレーズを取り出し、バリデーションと安全フィルタを適用 (M2対応)
     */
    private fun parseJsonlLine(line: String): List<CachedPhraseEntity> {
        val list = mutableListOf<CachedPhraseEntity>()
        try {
            val root = JSONObject(line)
            val result = root.getJSONObject("result")
            if (result.getString("type") != "succeeded") return emptyList()

            val message = result.getJSONObject("message")
            val contentArray = message.getJSONArray("content")
            if (contentArray.length() == 0) return emptyList()

            var textContent = contentArray.getJSONObject(0).getString("text").trim()

            // マークダウンコードフェンス (```json ... ```) の除去
            if (textContent.startsWith("```")) {
                textContent = textContent.substringAfter("\n").substringBeforeLast("```").trim()
            }

            val phrasesArray = JSONArray(textContent)
            for (i in 0 until phrasesArray.length()) {
                val item = phrasesArray.getJSONObject(i)
                val category = item.getString("category")
                val tone = item.getString("tone")
                val text = item.getString("text").trim()

                // 安全バリデーション (M2対応):
                // 1. 文字数制限 (40文字以内)
                if (text.length !in 3..40) continue

                // 2. ネガティブワードチェック
                if (BLOCKED_WORDS.any { text.contains(it) }) continue

                // 3. ハッシュ計算（重複防止）
                val hash = sha256(text)

                list.add(
                    CachedPhraseEntity(
                        textHash = hash,
                        category = category,
                        tone = tone,
                        timeOfDay = "ANY",
                        text = text
                    )
                )
            }
        } catch (e: Exception) {
            // JSONパースエラー時はスキップ
        }
        return list
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
