package com.babytalk.tv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PhraseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhrases(phrases: List<CachedPhraseEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM cached_phrases")
    suspend fun getCount(): Int

    // Step 1: カテゴリ・トーン・時間帯の完全一致
    @Query("""
        SELECT * FROM cached_phrases
        WHERE category = :category 
          AND tone = :tone 
          AND (timeOfDay = :timeOfDay OR timeOfDay = 'ANY')
        ORDER BY usedCount ASC, lastUsedAt ASC, RANDOM()
        LIMIT 1
    """)
    suspend fun getExactPhrase(category: String, tone: String, timeOfDay: String): CachedPhraseEntity?

    // Step 2: トーン緩和 (カテゴリと時間帯のみ)
    @Query("""
        SELECT * FROM cached_phrases
        WHERE category = :category 
          AND (timeOfDay = :timeOfDay OR timeOfDay = 'ANY')
        ORDER BY usedCount ASC, lastUsedAt ASC, RANDOM()
        LIMIT 1
    """)
    suspend fun getPhraseRelaxTone(category: String, timeOfDay: String): CachedPhraseEntity?

    // Step 3: 時間帯緩和 (カテゴリ内任意)
    @Query("""
        SELECT * FROM cached_phrases
        WHERE category = :category
        ORDER BY usedCount ASC, lastUsedAt ASC, RANDOM()
        LIMIT 1
    """)
    suspend fun getPhraseAnyTime(category: String): CachedPhraseEntity?

    // 使用履歴カウントの更新
    @Query("UPDATE cached_phrases SET usedCount = usedCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUsage(id: Long, timestamp: Long = System.currentTimeMillis())

    // --- バッチジョブ管理 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: BatchSyncJobEntity)

    @Update
    suspend fun updateJob(job: BatchSyncJobEntity)

    @Query("SELECT * FROM batch_sync_jobs WHERE status IN ('SUBMITTED', 'IN_PROGRESS') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveJob(): BatchSyncJobEntity?
}
