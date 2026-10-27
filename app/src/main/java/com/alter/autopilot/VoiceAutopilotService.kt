package com.alter.autopilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class VoiceAutopilotService : Service() {

    // VOICE_STATE_0141
    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val ACTION_STOP =
            "com.alter.autopilot.STOP_VOICE"

        private const val CHANNEL_ID = "alter_voice"
        private const val NOTIFICATION_ID = 1301
    }

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(
            NotificationManager::class.java
        )

        val channel = NotificationChannel(
            CHANNEL_ID,
            "ALTER Voice Autopilot",
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val stopIntent = Intent(
            this,
            VoiceAutopilotService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1301,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("ALTER Voice Autopilot")
            .setContentText("Сервис активен. Распознавание речи ещё не включено.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Остановить",
                    stopPendingIntent
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        isRunning = true

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
