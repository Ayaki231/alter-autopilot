package com.alter.autopilot

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.Locale

class MainActivity : Activity() {

    private fun speak(message: String) {
        AlterSpeaker.speak(this, message)
    }

    override fun onDestroy() {
        AlterSpeaker.stop()
        super.onDestroy()
    }

    private val voiceRequest = 1001
    private val imageRequest = 1002
    private val aiImageRequest = 1003
    private lateinit var enable: Switch
    private lateinit var status: TextView
    private lateinit var visionResult: TextView
    private var lastVisionText = ""
    private val prefs by lazy {
        getSharedPreferences("alter", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlterSpeaker.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NexusTheme.background)
            val pad = NexusTheme.dp(this@MainActivity, 16)
            setPadding(pad, pad, pad, NexusTheme.dp(this@MainActivity, 32))
        }

        // ================= HERO СЕКЦИЯ =================
        val heroLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = NexusTheme.dp(this@MainActivity, 18)
            }
            layoutParams = lp
        }

        // Светящаяся сфера Nexus Core
        val coreView = NexusCoreView(this).apply {
            val size = NexusTheme.dp(this@MainActivity, 120)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = NexusTheme.dp(this@MainActivity, 8)
            }
            setOnClickListener { listen() }
        }
        heroLayout.addView(coreView)

        val title = TextView(this).apply {
            text = "ALTER  |  AUTONOMOUS OS"
            textSize = 20f
            setTextColor(NexusTheme.white)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        heroLayout.addView(title)

        val info = TextView(this).apply {
            text = "AGENTIC AI SYSTEM  •  v0.22"
            textSize = 12f
            setTextColor(NexusTheme.muted)
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = NexusTheme.dp(this@MainActivity, 12)
            }
            layoutParams = lp
        }
        heroLayout.addView(info)

        // Статус-бар в виде бейджа
        status = TextView(this).apply {
            text = "● СИСТЕМА ГОТОВА К РАБОТЕ"
            textSize = 13f
            setTextColor(NexusTheme.cyan)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            background = NexusTheme.rounded(this@MainActivity, NexusTheme.surfaceLight, 12, Color.rgb(30, 60, 100))
            val p = NexusTheme.dp(this@MainActivity, 12)
            setPadding(p, NexusTheme.dp(this@MainActivity, 8), p, NexusTheme.dp(this@MainActivity, 8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = NexusTheme.dp(this@MainActivity, 12)
            }
            layoutParams = lp
        }
        heroLayout.addView(status)

        val mic = Button(this).apply {
            text = "🎤 СКАЗАТЬ КОМАНДУ АЛЬТЕРУ"
            NexusTheme.styleButton(this, isPrimary = true)
            setOnClickListener { listen() }
        }
        heroLayout.addView(mic)
        root.addView(heroLayout)

        // ================= КАРТОЧКА 1: АВТОНОМНЫЙ АВТОПИЛОТ (AGENTIC OS) =================
        val agentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            NexusTheme.styleCard(this)
        }
        agentCard.addView(NexusTheme.createHeader(this, "АВТОНОМНЫЙ АВТОПИЛОТ", "🚀"))

        val agentDesc = TextView(this).apply {
            text = "Управляет телефоном как человек: покупка на маркетплейсах, добавление в корзину, почта, будильники, заметки."
            setTextColor(NexusTheme.muted)
            textSize = 12f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = NexusTheme.dp(this@MainActivity, 10)
            }
            layoutParams = lp
        }
        agentCard.addView(agentDesc)

        val goalInput = EditText(this).apply {
            hint = "Цель: например, найди кофе на Озоне и добавь в корзину"
            NexusTheme.styleInput(this)
        }
        agentCard.addView(goalInput)

        val agentButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        val runGoalBtn = Button(this).apply {
            text = "▶ ЗАПУСК"
            NexusTheme.styleButton(this, isPrimary = true)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = NexusTheme.dp(this@MainActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val goal = goalInput.text.toString().trim()
                if (goal.isBlank()) {
                    Toast.makeText(this@MainActivity, "Введите цель для автопилота", Toast.LENGTH_SHORT).show()
                } else {
                    status.text = "Автопилот запущен: $goal"
                    AlterAgentLoop.executeGoal(this@MainActivity, goal) { currentStatus ->
                        runOnUiThread { status.text = currentStatus }
                    }
                }
            }
        }

        val stopGoalBtn = Button(this).apply {
            text = "⏹ СТОП"
            NexusTheme.styleButton(this, isDanger = true)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = NexusTheme.dp(this@MainActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                AlterAgentLoop.stop()
                status.text = "Автопилот остановлен пользователем."
                speak("Автопилот остановлен.")
            }
        }
        agentButtonsLayout.addView(runGoalBtn)
        agentButtonsLayout.addView(stopGoalBtn)
        agentCard.addView(agentButtonsLayout)
        root.addView(agentCard)

        // ================= КАРТОЧКА 2: ИИ МОЗГ (GEMINI) =================
        val aiCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            NexusTheme.styleCard(this)
        }
        aiCard.addView(NexusTheme.createHeader(this, "ИИ МОЗГ (GEMINI)", "🧠"))

        val aiApiKey = EditText(this).apply {
            hint = "Бесплатный ключ Gemini API"
            setText(prefs.getString("ai_api_key", ""))
            NexusTheme.styleInput(this)
        }
        aiCard.addView(aiApiKey)

        val aiReplyEnable = Switch(this).apply {
            text = "Генерация автоответов через AI"
            setTextColor(NexusTheme.white)
            isChecked = prefs.getBoolean("ai_reply_enabled", true)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = NexusTheme.dp(this@MainActivity, 8) }
            layoutParams = lp
        }
        aiCard.addView(aiReplyEnable)

        val ttsEnable = Switch(this).apply {
            text = "Голосовые ответы вслух (TTS)"
            setTextColor(NexusTheme.white)
            isChecked = prefs.getBoolean("tts_enabled", true)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = NexusTheme.dp(this@MainActivity, 12) }
            layoutParams = lp
        }
        aiCard.addView(ttsEnable)

        val testAiButton = Button(this).apply {
            text = "⚡ ТЕСТ AI И ГОЛОСА"
            NexusTheme.styleButton(this)
            setOnClickListener {
                status.text = "Тестирую AI и голос..."
                speak("Привет! Я Альтер. Проверяю подключение к нейросети.")
                Thread {
                    val res = AlterAiEngine.ask(this@MainActivity, "Скажи одно короткое приветствие для владельца.")
                    runOnUiThread {
                        status.text = res
                        speak(res)
                    }
                }.start()
            }
        }
        aiCard.addView(testAiButton)
        root.addView(aiCard)

        // ================= КАРТОЧКА 3: АВТООТВЕТЧИК УВЕДОМЛЕНИЙ =================
        val autoReplyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            NexusTheme.styleCard(this)
        }
        autoReplyCard.addView(NexusTheme.createHeader(this, "АВТООТВЕТЧИК УВЕДОМЛЕНИЙ", "💬"))

        enable = Switch(this).apply {
            text = "Разрешить автоответы в мессенджерах"
            setTextColor(NexusTheme.white)
            isChecked = prefs.getBoolean("enabled", false)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = NexusTheme.dp(this@MainActivity, 8) }
            layoutParams = lp
        }
        autoReplyCard.addView(enable)

        val packages = EditText(this).apply {
            hint = "Пакеты через запятую (org.telegram.messenger)"
            setText(prefs.getString("packages", "org.telegram.messenger,com.whatsapp"))
            NexusTheme.styleInput(this)
        }
        autoReplyCard.addView(packages)

        val reply = EditText(this).apply {
            hint = "Запасной шаблон ответа (если AI выключен)"
            setText(prefs.getString("reply", "Сейчас не могу ответить. Напишу позже."))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            NexusTheme.styleInput(this)
        }
        autoReplyCard.addView(reply)

        val save = Button(this).apply {
            text = "💾 СОХРАНИТЬ ВСЕ НАСТРОЙКИ"
            NexusTheme.styleButton(this, isPrimary = true)
            setOnClickListener {
                val allowed = packages.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }
                prefs.edit()
                    .putBoolean("enabled", enable.isChecked)
                    .putBoolean("ai_reply_enabled", aiReplyEnable.isChecked)
                    .putBoolean("tts_enabled", ttsEnable.isChecked)
                    .putString("ai_api_key", aiApiKey.text.toString().trim())
                    .putString("packages", allowed.joinToString(","))
                    .putString("reply", reply.text.toString())
                    .apply()

                Toast.makeText(this@MainActivity, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
                status.text = "Настройки успешно сохранены."
            }
        }
        autoReplyCard.addView(save)

        val access = Button(this).apply {
            text = "🔔 РАЗРЕШИТЬ ДОСТУП К УВЕДОМЛЕНИЯМ"
            NexusTheme.styleButton(this)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        autoReplyCard.addView(access)

        val specAccess = Button(this).apply {
            text = "♿ СПЕЦ. ВОЗМОЖНОСТИ (ЧТЕНИЕ ЭКРАНА)"
            NexusTheme.styleButton(this)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        autoReplyCard.addView(specAccess)
        root.addView(autoReplyCard)

        // ================= КАРТОЧКА 4: ФОНОВАЯ СЛУЖБА И ВИДЖЕТ =================
        val serviceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            NexusTheme.styleCard(this)
        }
        serviceCard.addView(NexusTheme.createHeader(this, "ФОНОВЫЙ ГОЛОС И ПЛАВАЮЩИЙ АЛЬТЕР", "🎙️"))

        val voiceButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        val startVoiceButton = Button(this).apply {
            text = "▶ СТАРТ СЛУЖБЫ"
            NexusTheme.styleButton(this)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = NexusTheme.dp(this@MainActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val permission = android.Manifest.permission.RECORD_AUDIO
                if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(permission), 1313)
                } else {
                    try {
                        startForegroundService(Intent(this@MainActivity, VoiceAutopilotService::class.java))
                        status.text = "Фоновый микрофон активен."
                    } catch (e: Exception) {
                        status.text = "Ошибка запуска: ${e.message}"
                    }
                }
            }
        }

        val stopVoiceButton = Button(this).apply {
            text = "⏹ СТОП СЛУЖБЫ"
            NexusTheme.styleButton(this, isDanger = true)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = NexusTheme.dp(this@MainActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                stopService(Intent(this@MainActivity, VoiceAutopilotService::class.java))
                status.text = "Фоновый микрофон остановлен."
            }
        }
        voiceButtonsLayout.addView(startVoiceButton)
        voiceButtonsLayout.addView(stopVoiceButton)
        serviceCard.addView(voiceButtonsLayout)

        val floatingButton = Button(this).apply {
            text = "🟣 ПЛАВАЮЩИЙ АЛЬТЕР (ВИДЖЕТ): ВКЛ / ВЫКЛ"
            NexusTheme.styleButton(this)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else if (FloatingAlterService.active) {
                    stopService(Intent(this@MainActivity, FloatingAlterService::class.java))
                    status.text = "Плавающий виджет выключен."
                } else {
                    startService(Intent(this@MainActivity, FloatingAlterService::class.java))
                    status.text = "Плавающий виджет запущен поверх экрана."
                }
            }
        }
        serviceCard.addView(floatingButton)
        root.addView(serviceCard)

        // ================= КАРТОЧКА 5: ЗРЕНИЕ И ЭКРАН =================
        val visionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            NexusTheme.styleCard(this)
        }
        visionCard.addView(NexusTheme.createHeader(this, "ЗРЕНИЕ И АНАЛИЗ ЭКРАНА", "👁️"))

        val screenButton = Button(this).apply {
            text = "📱 ЧТО СЕЙЧАС НА ЭКРАНЕ?"
            NexusTheme.styleButton(this)
            setOnClickListener {
                val service = ScreenObserverService.instance
                if (service == null) {
                    status.text = "Включи ALTER в Специальных возможностях."
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    val content = service.readCurrentScreen()
                    status.text = "ALTER анализирует экран..."
                    Thread {
                        val reply = AlterAiEngine.ask(this@MainActivity, "Кратко перескажи, что сейчас на экране", content)
                        runOnUiThread {
                            status.text = reply
                            speak(reply)
                        }
                    }.start()
                }
            }
        }
        visionCard.addView(screenButton)

        val visionButton = Button(this).apply {
            text = "📷 РАСПОЗНАТЬ ТЕКСТ С ФОТО (OCR)"
            NexusTheme.styleButton(this)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, imageRequest)
            }
        }
        visionCard.addView(visionButton)

        val aiVisionButton = Button(this).apply {
            text = "🧠 КЛАССИФИКАЦИЯ ОБЪЕКТОВ НА ФОТО"
            NexusTheme.styleButton(this)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, aiImageRequest)
            }
        }
        visionCard.addView(aiVisionButton)

        visionResult = TextView(this).apply {
            text = "Выбери фото для распознавания."
            textSize = 13f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(NexusTheme.surfaceLight)
            setTextColor(NexusTheme.white)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = NexusTheme.dp(this@MainActivity, 8) }
            layoutParams = lp
        }
        visionCard.addView(visionResult)
        root.addView(visionCard)

        // ================= КАРТОЧКА 6: ЖУРНАЛ СОБЫТИЙ =================
        val logCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            NexusTheme.styleCard(this)
        }
        logCard.addView(NexusTheme.createHeader(this, "ЖУРНАЛ СОБЫТИЙ", "📋"))

        val log = TextView(this).apply {
            textSize = 11f
            setTextColor(NexusTheme.muted)
            text = prefs.getString("log", "Пока нет событий")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = NexusTheme.dp(this@MainActivity, 8) }
            layoutParams = lp
        }
        logCard.addView(log)

        val refreshButton = Button(this).apply {
            text = "🔄 ОБНОВИТЬ ЖУРНАЛ"
            NexusTheme.styleButton(this)
            setOnClickListener {
                log.text = prefs.getString("log", "Пока нет событий")
            }
        }
        logCard.addView(refreshButton)
        root.addView(logCard)

        // Оборачиваем весь экран в стильный ScrollView
        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusTheme.background)
            isFillViewport = true
            addView(root)
        })
    }

    private fun prepareVisionImage(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        val scale = if (maxSide < 1400) 1400f / maxSide else 1f
        val width = (source.width * scale).toInt()
        val height = (source.height * scale).toInt()
        val resized = if (scale != 1f)
            Bitmap.createScaledBitmap(source, width, height, true)
        else source
        val pixels = IntArray(width * height)
        resized.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = if (gray > 140) Color.WHITE else Color.BLACK
        }
        val binary = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        binary.setPixels(pixels, 0, width, 0, 0, width, height)
        if (resized !== source) resized.recycle()
        return binary
    }

    private fun classifyPhoto(uri: Uri) {
        status.text = "AI анализирует фотографию..."
        Thread {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while (maxOf(options.outWidth / sample, options.outHeight / sample) > 1600) { sample *= 2 }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                } ?: throw Exception("Не удалось открыть фото")

                try {
                    val base = BaseOptions.builder().setModelAssetPath("efficientnet_lite0.tflite").build()
                    val config = ImageClassifier.ImageClassifierOptions.builder().setBaseOptions(base).setMaxResults(3).build()
                    ImageClassifier.createFromOptions(this, config).use { classifier ->
                        val image = BitmapImageBuilder(bitmap).build()
                        val result = classifier.classify(image)
                        val categories = result.classificationResult().classifications().flatMap { it.categories() }.sortedByDescending { it.score() }.take(3)
                        val description = if (categories.isEmpty()) "AI не смог определить объект."
                        else categories.joinToString("\n") {
                            val name = it.displayName().ifBlank { it.categoryName() }
                            val percent = (it.score() * 100).toInt()
                            "$name — $percent%"
                        }
                        runOnUiThread {
                            lastVisionText = description
                            visionResult.text = "AI предполагает:\n$description"
                            status.text = "AI-анализ завершён."
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "AI Vision: ${e.message}" }
            }
        }.start()
    }

    private fun recognizeImage(uri: Uri) {
        status.text = "ALTER анализирует изображение..."
        Thread {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw Exception("Не удалось открыть изображение")
                val tessData = File(filesDir, "tessdata")
                if (!tessData.exists()) tessData.mkdirs()
                for (language in listOf("rus", "eng")) {
                    val target = File(tessData, "$language.traineddata")
                    if (!target.exists() || target.length() == 0L) {
                        assets.open("tessdata/$language.traineddata").use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                val api = TessBaseAPI()
                try {
                    if (!api.init(filesDir.absolutePath, "rus+eng")) throw Exception("Не удалось запустить OCR")
                    api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    val prepared = prepareVisionImage(bitmap)
                    try {
                        api.setImage(prepared)
                        val result = api.utF8Text.orEmpty().trim()
                        runOnUiThread {
                            lastVisionText = result
                            visionResult.text = if (result.isBlank()) "Текст на изображении не найден." else result
                            status.text = "Анализ изображения завершён."
                        }
                    } finally {
                        if (prepared !== bitmap) prepared.recycle()
                    }
                } finally {
                    api.recycle()
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Vision: ${e.message}" }
            }
        }.start()
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду или цель для ALTER")
        }
        try {
            startActivityForResult(intent, voiceRequest)
        } catch (e: ActivityNotFoundException) {
            status.text = "На телефоне нет сервиса распознавания речи."
        }
    }

    @Deprecated("Legacy speech result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == aiImageRequest && resultCode == RESULT_OK) {
            data?.data?.let { classifyPhoto(it) }
            return
        }
        if (requestCode == imageRequest && resultCode == RESULT_OK) {
            data?.data?.let { recognizeImage(it) }
            return
        }
        if (requestCode != voiceRequest || resultCode != RESULT_OK) return

        val words = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val command = words?.firstOrNull()?.trim().orEmpty()
        if (command.isEmpty()) {
            status.text = "Не удалось распознать команду."
            return
        }

        status.text = "Услышал: $command"
        Thread {
            val reply = AlterVoiceCommands.execute(this, command)
            runOnUiThread {
                status.text = reply
                speak(reply)
            }
        }.start()
    }
}
