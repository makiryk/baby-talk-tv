package com.babytalk.tv.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioCacheManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newManager(maxFiles: Int = 200, maxBytes: Long = 50L * 1024 * 1024) =
        AudioCacheManager(File(tmp.root, "audio/cache"), maxFiles, maxBytes)

    @Test
    fun saveAudio_writesFileAndLeavesNoTmp() {
        val manager = newManager()
        val bytes = byteArrayOf(1, 2, 3)

        val path = manager.saveAudio("abc", bytes)

        assertEquals(manager.fileFor("abc").absolutePath, path)
        assertEquals("phrase_abc.mp3", File(path!!).name)
        assertArrayEquals(bytes, File(path).readBytes())
        assertTrue(File(path).parentFile!!.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun saveAudio_overwritesExisting() {
        val manager = newManager()
        manager.saveAudio("abc", byteArrayOf(1))
        val path = manager.saveAudio("abc", byteArrayOf(9, 9))

        assertArrayEquals(byteArrayOf(9, 9), File(path!!).readBytes())
    }

    @Test
    fun prune_removesOldestWhenOverFileLimit() {
        val manager = newManager(maxFiles = 2)
        val now = System.currentTimeMillis()
        for ((i, h) in listOf("a", "b", "c").withIndex()) {
            val f = File(manager.saveAudio(h, byteArrayOf(1))!!)
            f.setLastModified(now - (10 - i) * 1000L)
        }
        // 保存時にも prune されるため、a は削除済み
        manager.pruneCacheIfNeeded()

        assertFalse(manager.fileFor("a").exists())
        assertTrue(manager.fileFor("b").exists())
        assertTrue(manager.fileFor("c").exists())
    }

    @Test
    fun prune_removesOldestWhenOverByteLimit() {
        val manager = newManager(maxBytes = 25)
        val now = System.currentTimeMillis()
        val old = File(manager.saveAudio("old", ByteArray(10))!!)
        old.setLastModified(now - 10_000)
        val mid = File(manager.saveAudio("mid", ByteArray(10))!!)
        mid.setLastModified(now - 5_000)
        manager.saveAudio("new", ByteArray(10)) // 合計 30 > 25 → old を削除

        assertFalse(manager.fileFor("old").exists())
        assertTrue(manager.fileFor("mid").exists())
        assertTrue(manager.fileFor("new").exists())
    }

    @Test
    fun prune_doesNothingWithinLimits() {
        val manager = newManager()
        manager.saveAudio("a", byteArrayOf(1))
        assertEquals(0, manager.pruneCacheIfNeeded())
    }
}
