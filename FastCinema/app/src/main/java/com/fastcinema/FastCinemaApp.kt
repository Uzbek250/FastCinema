package com.fastcinema

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class FastCinemaApp : Application() {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "fastcinema_downloads"
        const val DOWNLOAD_CHANNEL_NAME = "Yuklab olishlar"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            DOWNLOAD_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Kino yuklab olish holati"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
