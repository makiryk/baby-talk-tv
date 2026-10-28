package com.babytalk.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class BabyTalkApplication : Application() {

    companion object {
        const val CHANNEL_ID_SERVICE = "channel_baby_talk_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
}
