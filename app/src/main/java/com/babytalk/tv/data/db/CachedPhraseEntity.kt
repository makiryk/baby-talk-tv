package com.babytalk.tv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_phrases",
    indices = [Index(value = ["textHash"], unique = true)]
)
data class CachedPhraseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val textHash: String,            // 重複防止用ハッシュ
    val category: String,            // REACTIVE / IDLE_AUTONOMOUS / CLOSING
    val tone: String,                // COOING_HAPPY / BABBLING_NEUTRAL / FUSS_WHINE / GENTLE / SLEEPY
    val timeOfDay: String,           // MORNING / DAY / EVENING / NIGHT / ANY
    val text: String,
    val audioFilePath: String? = null,
    val usedCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
