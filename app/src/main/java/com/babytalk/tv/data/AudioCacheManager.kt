package com.babytalk.tv.data

import android.content.Context
import java.io.File

/**
 * クラウドTTSで事前合成した音声の内部ストレージキャッシュ。
 * - 書き込みは一時ファイル → rename のアトミック方式
 * - 件数・総容量を超えたら lastModified が古い順に削除（LRU）
 */
class AudioCacheManager(
    private val cacheDir: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {
    constructor(context: Context) : this(File(context.filesDir, "audio/cache"))

    companion object {
        const val DEFAULT_MAX_FILES = 200
        const val DEFAULT_MAX_BYTES = 50L * 1024 * 1024
    }

    init {
        cacheDir.mkdirs()
    }

    fun fileFor(hash: String): File = File(cacheDir, "phrase_$hash.mp3")

    /** 保存して絶対パスを返す。失敗時は null。 */
    fun saveAudio(hash: String, bytes: ByteArray): String? {
        return try {
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "cache_$hash.tmp")
            tmp.writeBytes(bytes)
            val target = fileFor(hash)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return null
            }
            pruneCacheIfNeeded()
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** 古いファイルから削除して件数・容量の上限内に収める。削除した件数を返す。 */
    fun pruneCacheIfNeeded(): Int {
        val files = cacheDir.listFiles { f -> f.isFile && f.name.startsWith("phrase_") && f.name.endsWith(".mp3") }
            ?.sortedBy { it.lastModified() }?.toMutableList() ?: return 0
        var total = files.sumOf { it.length() }
        var removed = 0
        while (files.isNotEmpty() && (files.size > maxFiles || total > maxBytes)) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            if (oldest.delete()) removed++
        }
        return removed
    }
}
