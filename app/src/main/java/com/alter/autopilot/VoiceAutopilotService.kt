package com.alter.autopilot

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
    private var wasMuted = false

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
            .setContentTitle("ALTER — голосовой автопилот")
            .setContentText("Микрофон активен. Нажми «Остановить» для отключения.")
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

    private fun setSystemSoundMute(mute: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (mute) {
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                wasMuted = true
            } else if (wasMuted) {
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                wasMuted = false
            }
        } catch (_: Exception) {}
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

            setSystemSoundMute(true)
            recognizer?.startListening(request)
        } catch (_: Exception) {
            setSystemSoundMute(false)
            handler.postDelayed({ restartListening() }, 3000)
        }
    }

    private fun restartListening() {
        if (!active) return
        try { recognizer?.cancel() } catch (_: Exception) {}
        handler.postDelayed({ startListening() }, 1000)
    }

    override fun onResults(results: Bundle?) {
        setSystemSoundMute(false)
        val words = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )?.firstOrNull().orEmpty()

        if (words.isNotBlank()) {
            Thread {
                try {
                    val reply = AlterVoiceCommands.execute(this, words)
                    if (reply.isNotBlank()) {
                        AlterSpeaker.speak(this, reply)
                    }
                } catch (_: Exception) {}
            }.start()
        }
        handler.postDelayed({ restartListening() }, 2000)
    }

    override fun onError(error: Int) {
        setSystemSoundMute(false)
        // Если тишина или отсутствие совпадений — не перезапускаем судорожно каждую секунду
        val delay = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 3500L
            else -> 2000L
        }
        handler.postDelayed({ restartListening() }, delay)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        setSystemSoundMute(false)
    }

    override fun onBeginningOfSpeech() {
        setSystemSoundMute(false)
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        active = false
        isRunning = false
        setSystemSoundMute(false)
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
