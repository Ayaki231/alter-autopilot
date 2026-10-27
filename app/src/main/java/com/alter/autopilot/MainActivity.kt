package com.alter.autopilot

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val voiceRequest = 1001
    private lateinit var enable: Switch
    private lateinit var status: TextView

    private val prefs by lazy {
        getSharedPreferences("alter", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 24)
        }

        val title = TextView(this).apply {
            text = "ALTER Autopilot 0.3 Voice"
            textSize = 24f
        }

        val info = TextView(this).apply {
            text = "Голосовой помощник и локальный автоответчик."
        }

        val mic = Button(this).apply {
            text = "🎤 ГОВОРИТЬ С ALTER"
            setOnClickListener { listen() }
        }

        status = TextView(this).apply {
            text = "Нажми микрофон и произнеси команду."
            textSize = 16f
        }

        enable = Switch(this).apply {
            text = "Разрешить автоответы"
            isChecked = prefs.getBoolean("enabled", false)
        }

        val packages = EditText(this).apply {
            hint = "Пакеты через запятую"
            minLines = 2
            setText(prefs.getString("packages", ""))
        }

        val reply = EditText(this).apply {
            hint = "Текст автоответа"
            setText(prefs.getString(
                "reply",
                "Сейчас не могу ответить. Напишу позже."
            ))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        val log = TextView(this).apply {
            textSize = 12f
        }

        fun refresh() {
            log.text = "Последние события:\n" +
                prefs.getString("log", "Пока нет событий")
        }

        val save = Button(this).apply {
            text = "Сохранить правила"
            setOnClickListener {
                val allowed = packages.text.toString()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (enable.isChecked && allowed.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Укажите приложение",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (enable.isChecked &&
                    reply.text.isBlank()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Укажите текст ответа",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    prefs.edit()
                        .putBoolean("enabled", enable.isChecked)
                        .putString("packages", allowed.joinToString(","))
                        .putString("reply", reply.text.toString())
                        .apply()

                    Toast.makeText(
                        this@MainActivity,
                        "Сохранено",
                        Toast.LENGTH_SHORT
                    ).show()
                    refresh()
                }
            }
        }

        val access = Button(this).apply {
            text = "Открыть доступ к уведомлениям"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }

        val refreshButton = Button(this).apply {
            text = "Обновить журнал"
            setOnClickListener { refresh() }
        }

        listOf(
            title, info, mic, status, enable,
            packages, reply, save, access,
            refreshButton, log
        ).forEach { root.addView(it) }


        val screenButton = Button(this).apply {
            text = "ЧТО НА ЭКРАНЕ?"
            setOnClickListener {
                val service = ScreenObserverService.instance
                if (service == null) {
                    status.text = "Включи ALTER в специальных возможностях."
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                } else {
                    status.text = service.readCurrentScreen()
                }
            }
        }
        root.addView(screenButton)

        setContentView(ScrollView(this).apply {
            addView(root)
        })

        refresh()
    }

    private fun listen() {
        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "ru-RU"
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Скажи команду для ALTER"
            )
        }

        try {
            startActivityForResult(intent, voiceRequest)
        } catch (e: ActivityNotFoundException) {
            status.text = "На телефоне нет сервиса распознавания речи."
        }
    }

    @Deprecated("Legacy speech result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != voiceRequest ||
            resultCode != RESULT_OK) return

        val words = data?.getStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS
        )

        val command = words?.firstOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()

        if (command.isEmpty()) {
            status.text = "Не удалось распознать команду."
            return
        }

        status.text = "Услышал: $command"
        executeCommand(command)
    }

    private fun executeCommand(command: String) {
        when {
            "открой телеграм" in command ||
            "открыть телеграм" in command ||
            "открой telegram" in command -> {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage("org.telegram.messenger")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    status.text = "Не удалось открыть Telegram."
                }
            }

            "открой настройки" in command ||
            "открыть настройки" in command -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }

            "выключи автоответ" in command ||
            "отключи автоответ" in command -> {
                prefs.edit().putBoolean("enabled", false).apply()
                enable.isChecked = false
                status.text = "Автоответы выключены."
            }

            "включи автоответ" in command -> {
                val packages = prefs.getString("packages", "").orEmpty()
                val reply = prefs.getString("reply", "").orEmpty()
                if (packages.isBlank() || reply.isBlank()) {
                    status.text = "Сначала сохрани правила автоответов."
                } else {
                    prefs.edit().putBoolean("enabled", true).apply()
                    enable.isChecked = true
                    status.text = "Автоответы включены."
                }
            }

            else -> {
                status.text = "Команда пока не поддерживается: $command"
            }
        }
    }
}
