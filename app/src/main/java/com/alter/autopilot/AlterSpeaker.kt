package com.alter.autopilot

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

object AlterSpeaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingMessages = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (tts == null) {
            mainHandler.post {
                try {
                    tts = TextToSpeech(context.applicationContext, this)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(1.05f)

            if (isReady) {
                synchronized(pendingMessages) {
                    for (msg in pendingMessages) {
                        speakInternal(msg)
                    }
                    pendingMessages.clear()
                }
            }
        }
    }

    fun speak(context: Context, message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return

        val prefs = context.getSharedPreferences("alter", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tts_enabled", true)) return

        init(context)

        mainHandler.post {
            if (isReady && tts != null) {
                speakInternal(trimmed)
            } else {
                synchronized(pendingMessages) {
                    if (pendingMessages.size < 5) {
                        pendingMessages.add(trimmed)
                    }
                }
            }
        }
    }

    private fun speakInternal(text: String) {
        try {
            tts?.speak(text.take(800), TextToSpeech.QUEUE_FLUSH, null, "alter_speech_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    fun stop() {
        mainHandler.post {
            try {
                tts?.stop()
            } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
            tts = null
            isReady = false
        }
    }
}
