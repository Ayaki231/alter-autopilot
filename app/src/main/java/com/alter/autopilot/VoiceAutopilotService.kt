package com.alter.autopilot

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import java.util.Locale

class VoiceAutopilotService : Service(), RecognitionListener {
    companion object {
        const val ACTION_STOP = "com.alter.autopilot.STOP_VOICE"
        private const val CHANNEL = "alter_voice"
        private const val ID = 1301
        @Volatile var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    override fun onCreate() {
        super.onCreate()
        AlterSpeaker.init(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL, "ALTER Background Voice",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
    }

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        val stop = PendingIntent.getService(
            this, ID,
            Intent(this, VoiceAutopilotService::class.java)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ALTER — фоновый голос")
            .setContentText("Микрофон активен. Нажми Остановить для выхода.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(ID, notification)
        }

        if (!active) {
            active = true
            isRunning = true
            handler.post { startListening() }
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (!active) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(this)
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            recognizer?.startListening(request)
        } catch (_: Exception) {
            handler.postDelayed({ restartListening() }, 1500)
        }
    }

    private fun restartListening() {
        if (!active) return
        try { recognizer?.cancel() } catch (_: Exception) {}
        handler.postDelayed({ startListening() }, 600)
    }

    override fun onResults(results: Bundle?) {
        val words = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )?.firstOrNull().orEmpty()

        if (words.isNotBlank()) {
            val normalized = words.lowercase(Locale.ROOT).trim()
            if (normalized.startsWith("альтер") || normalized.startsWith("alter")) {
                Thread {
                    try {
                        val reply = AlterVoiceCommands.execute(this, words)
                        if (reply.isNotBlank()) {
                            AlterSpeaker.speak(this, reply)
                        }
                    } catch (_: Exception) {}
                }.start()
            }
        }
        handler.postDelayed({ restartListening() }, 1500)
    }

    override fun onError(error: Int) {
        handler.postDelayed({ restartListening() }, 1500)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        active = false
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
