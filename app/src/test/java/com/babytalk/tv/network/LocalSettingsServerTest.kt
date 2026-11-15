package com.babytalk.tv.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun newPinIsSixDigits() {
        repeat(50) {
            val pin = LocalSettingsServer.newPin()
            assertTrue(pin, Regex("[0-9]{6}").matches(pin))
        }
    }

    @Test
    fun pinMatchesOnlyExactPin() {
        assertTrue(LocalSettingsServer.pinMatches("012345", "012345"))
        assertTrue(LocalSettingsServer.pinMatches("012345", " 012345 "))
        assertFalse(LocalSettingsServer.pinMatches("012345", "012346"))
        assertFalse(LocalSettingsServer.pinMatches("012345", "12345"))
        assertFalse(LocalSettingsServer.pinMatches("012345", ""))
        assertFalse(LocalSettingsServer.pinMatches("012345", null))
    }

    @Test
    fun pinIsReadFromForm() {
        val form = LocalSettingsServer.parseForm("anthropic_api_key=k&pin=654321")
        assertEquals("654321", form[LocalSettingsServer.KEY_PIN])
    }

    @Test
    fun maskKey() {
        assertEquals("(未設定)", LocalSettingsServer.maskKey(null))
        assertEquals("****", LocalSettingsServer.maskKey("abcd"))
        assertEquals("abcd****wxyz", LocalSettingsServer.maskKey("abcdefghwxyz"))
    }
}
