package com.babytalk.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.babytalk.tv.worker.BatchPhraseSyncWorker
import java.util.concurrent.TimeUnit

class BabyTalkApplication : Application() {

    companion object {
        const val CHANNEL_ID_SERVICE = "channel_baby_talk_service"
        private const val SYNC_WORK_NAME = "baby_talk_batch_sync_work"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupBatchSyncWorker()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                getString(R.string.mic_notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.mic_notification_text)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * 1日1回の Claude Message Batches 定期同期タスクを設定
     */
    private fun setupBatchSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<BatchPhraseSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
