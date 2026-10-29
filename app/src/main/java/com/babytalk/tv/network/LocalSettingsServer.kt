package com.babytalk.tv.network

import android.content.Context
import android.content.SharedPreferences
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 同一Wi-Fi内のスマホのブラウザから API キーを設定するための簡易ローカルサーバー (ポート 8080)。
 * 標準の ServerSocket のみで実装している（com.sun.net.httpserver は Android に無い）。
 */
class LocalSettingsServer(
    context: Context,
    private val port: Int = PORT
) {
    companion object {
        const val PORT = 8080
        const val PREFS_NAME = "baby_talk_prefs"
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_GCP_TTS = "gcp_tts_api_key"

        private const val MAX_BODY = 64 * 1024
        private const val MAX_LINE = 8192

        class Request(val method: String, val path: String, val body: String)

        fun maskKey(key: String?): String = when {
            key.isNullOrEmpty() -> "(未設定)"
            key.length <= 8 -> "****"
            else -> key.take(4) + "****" + key.takeLast(4)
        }

        fun escapeHtml(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        fun parseForm(body: String): Map<String, String> =
            body.split("&").filter { it.contains("=") }.associate {
                val (k, v) = it.split("=", limit = 2)
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }

        private fun readLine(input: InputStream): String? {
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
                if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
                buf.write(b)
                if (buf.size() > MAX_LINE) return null
            }
        }

        /** HTTP リクエストを読む。Content-Length は byte 数なので、body は生のストリームから読む。不正なら null。 */
        fun readRequest(input: InputStream): Request? {
            val parts = (readLine(input) ?: return null).split(" ")
            if (parts.size < 2) return null
            var length = 0
            while (true) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0 && line.substring(0, i).trim().equals("Content-Length", ignoreCase = true)) {
                    length = line.substring(i + 1).trim().toIntOrNull() ?: return null
                }
            }
            if (length < 0 || length > MAX_BODY) return null
            val body = ByteArray(length)
            var off = 0
            while (off < length) {
                val n = input.read(body, off, length - off)
                if (n < 0) return null
                off += n
            }
            return Request(parts[0].uppercase(), parts[1].substringBefore('?'), body.toString(Charsets.UTF_8))
        }

        /** 端末のローカル IPv4 アドレス（取得できなければ null） */
        fun getLocalIpAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    @Synchronized
    fun start(): Boolean {
        if (serverSocket != null) return true
        return try {
            val ss = ServerSocket(port)
            val ex = Executors.newSingleThreadExecutor()
            serverSocket = ss
            executor = ex
            ex.execute {
                while (!ss.isClosed) {
                    try {
                        ss.accept().use { handle(it) }
                    } catch (e: Exception) {
                        // stop() による close、または個別クライアントのエラー。ループ条件で終了を判定する
                    }
                }
            }
            true
        } catch (e: Exception) {
            serverSocket = null
            executor = null
            false
        }
    }

    @Synchronized
    fun stop() {
        serverSocket?.close()
        executor?.shutdownNow()
        serverSocket = null
        executor = null
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 5000
        val req = readRequest(socket.getInputStream())
        val (code, body) = when {
            req == null -> 400 to "Bad Request"
            req.path == "/" && req.method == "GET" -> 200 to renderForm(null)
            req.path == "/save" ->
                if (req.method != "POST") {
                    405 to "Method Not Allowed"
                } else {
                    val form = parseForm(req.body)
                    val editor = prefs.edit()
                    // 空欄は「変更しない」
                    form[KEY_ANTHROPIC]?.trim()?.takeIf { it.isNotEmpty() }?.let { editor.putString(KEY_ANTHROPIC, it) }
                    form[KEY_GCP_TTS]?.trim()?.takeIf { it.isNotEmpty() }?.let { editor.putString(KEY_GCP_TTS, it) }
                    editor.apply()
                    200 to renderForm("保存しました")
                }
            else -> 404 to "Not Found"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code ${reason(code)}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        else -> "Error"
    }

    private fun renderForm(message: String?): String {
        val a = escapeHtml(maskKey(prefs.getString(KEY_ANTHROPIC, null)))
        val g = escapeHtml(maskKey(prefs.getString(KEY_GCP_TTS, null)))
        val msg = message?.let { "<p style=\"color:green\">${escapeHtml(it)}</p>" } ?: ""
        return """<!DOCTYPE html><html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Baby Talk TV 設定</title></head>
<body style="font-family:sans-serif;margin:16px"><h2>Baby Talk TV 設定</h2>$msg
<form method="POST" action="/save">
<p>Anthropic API Key<br>現在: $a<br><input type="password" name="$KEY_ANTHROPIC" style="width:100%" autocomplete="off"></p>
<p>Google Cloud API Key<br>現在: $g<br><input type="password" name="$KEY_GCP_TTS" style="width:100%" autocomplete="off"></p>
<p><small>空欄の項目は変更されません。</small></p>
<button type="submit">保存</button></form></body></html>"""
    }
}
