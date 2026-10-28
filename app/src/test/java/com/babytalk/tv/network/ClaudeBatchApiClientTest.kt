package com.babytalk.tv.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeBatchApiClientTest {

    @Test
    fun testBlockedWordsFilter() {
        val client = ClaudeBatchApiClient("test-key")

        // 反射を使って parseJsonlLine をテスト
        val method = ClaudeBatchApiClient::class.java.getDeclaredMethod("parseJsonlLine", String::class.java)
        method.isAccessible = true

        // 正常なJSONL行
        val validJsonl = """
            {"custom_id": "test_1", "result": {"type": "succeeded", "message": {"content": [{"text": "[{\"category\":\"REACTIVE\",\"tone\":\"COOING_HAPPY\",\"text\":\"たのしいねぇ！\"}]"}]}}}
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val validResult = method.invoke(client, validJsonl) as List<*>
        assertEquals(1, validResult.size)

        // ネガティブワード（"こわい"）を含むJSONL行
        val blockedJsonl = """
            {"custom_id": "test_2", "result": {"type": "succeeded", "message": {"content": [{"text": "[{\"category\":\"REACTIVE\",\"tone\":\"FUSS_WHINE\",\"text\":\"こわいねぇ\"}]"}]}}}
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val blockedResult = method.invoke(client, blockedJsonl) as List<*>
        assertEquals("ネガティブワードを含むフレーズは除外されること", 0, blockedResult.size)
    }

    @Test
    fun testLengthFilter() {
        val client = ClaudeBatchApiClient("test-key")

        val method = ClaudeBatchApiClient::class.java.getDeclaredMethod("parseJsonlLine", String::class.java)
        method.isAccessible = true

        // 40文字を超える長すぎる文（赤ちゃん向けには不適切）
        val tooLongText = "あ".repeat(50)
        val tooLongJsonl = """
            {"custom_id": "test_3", "result": {"type": "succeeded", "message": {"content": [{"text": "[{\"category\":\"IDLE_AUTONOMOUS\",\"tone\":\"GENTLE\",\"text\":\"$tooLongText\"}]"}]}}}
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(client, tooLongJsonl) as List<*>
        assertEquals("40文字を超える長文は除外されること", 0, result.size)
    }
}
