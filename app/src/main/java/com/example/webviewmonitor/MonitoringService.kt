package com.example.webviewmonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MonitoringService : Service() {

    companion object {
        const val ACTION_NOTIFY = "com.example.webviewmonitor.ACTION_NOTIFY"
        const val ACTION_STOP   = "com.example.webviewmonitor.ACTION_STOP"
        const val CHANNEL_ID    = "monitor_channel_v2"
        const val NOTIF_ID      = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("監視中..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NOTIFY -> {
                val message = intent.getStringExtra("message") ?: "空きあり！"
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(message))
            }
            ACTION_STOP -> {
                val stopIntent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_STOP_FROM_NOTIFICATION
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(stopIntent)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String) = run {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MonitoringService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WebViewMonitor")
            .setContentText(text)
            .setContentIntent(tapIntent)
            .addAction(0, "監視停止", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "監視通知", NotificationManager.IMPORTANCE_HIGH
        )
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 500, 200, 500)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
}
