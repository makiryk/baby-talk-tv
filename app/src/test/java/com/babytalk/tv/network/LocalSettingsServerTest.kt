package com.babytalk.tv.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSettingsServerTest {
    private fun read(s: String) =
        LocalSettingsServer.readRequest(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)))

    @Test
    fun parsesGet() {
        val r = read("GET /?x=1 HTTP/1.1\r\nHost: a\r\n\r\n")
        assertNotNull(r)
        assertEquals("GET", r!!.method)
        assertEquals("/", r.path)
        assertEquals("", r.body)
    }

    @Test
    fun parsesPostBody() {
        val body = "gcp_tts_api_key=%E3%81%82&anthropic_api_key=k"
        val r = read("POST /save HTTP/1.1\r\ncontent-length: ${body.length}\r\n\r\n$body")
        assertEquals("/save", r!!.path)
        val form = LocalSettingsServer.parseForm(r.body)
        assertEquals("あ", form["gcp_tts_api_key"])
        assertEquals("k", form["anthropic_api_key"])
    }

    @Test
    fun rejectsMalformed() {
        assertNull(read(""))
        assertNull(read("GARBAGE\r\n\r\n"))
        assertNull(read("POST /save HTTP/1.1\r\nContent-Length: 999999\r\n\r\n"))
    }

    @Test
    fun maskKey() {
        assertEquals("(未設定)", LocalSettingsServer.maskKey(null))
        assertEquals("****", LocalSettingsServer.maskKey("abcd"))
        assertEquals("abcd****wxyz", LocalSettingsServer.maskKey("abcdefghwxyz"))
    }
}
