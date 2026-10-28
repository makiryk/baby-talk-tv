package com.babytalk.tv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batch_sync_jobs")
data class BatchSyncJobEntity(
    @PrimaryKey
    val batchId: String,
    val status: String, // SUBMITTED, IN_PROGRESS, ENDED, IMPORTED, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val resultsUrl: String? = null,
    val errorMessage: String? = null
)
