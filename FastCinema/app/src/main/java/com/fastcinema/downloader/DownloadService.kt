package com.fastcinema.downloader

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fastcinema.FastCinemaApp
import com.fastcinema.R
import com.fastcinema.browser.BrowserActivity

/**
 * DownloadService — fon rejimida yuklashni davom ettiruvchi xizmat
 */
class DownloadService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.fastcinema.STOP_DOWNLOAD"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Yuklanmoqda...", 0))
        return START_STICKY
    }

    fun updateNotification(title: String, progress: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, progress))
    }

    private fun buildNotification(title: String, progress: Int): Notification {
        val intent = Intent(this, BrowserActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FastCinemaApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("FastCinema — Yuklab olish")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress == 0)
            .addAction(R.drawable.ic_close, "To'xtatish", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
