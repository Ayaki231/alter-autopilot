package com.alter.autopilot

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.widget.*
import java.util.Locale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.framework.image.BitmapImageBuilder
import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File


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
            setPadding(32, 32, 32, 24)
        }

        val title = TextView(this).apply {
            text = "ALTER  |  NEXUS AI"
            textSize = 24f
        }

        val info = TextView(this).apply {
            text = "PERSONAL AI SYSTEM  •  VERSION ${BuildConfig.VERSION_NAME}"
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

        val aiApiKey = EditText(this).apply {
            hint = "Бесплатный ключ Gemini API"
            setText(prefs.getString("ai_api_key", ""))
        }

        val aiReplyEnable = Switch(this).apply {
            text = "Генерация автоответов через AI"
            isChecked = prefs.getBoolean("ai_reply_enabled", true)
        }

        val ttsEnable = Switch(this).apply {
            text = "Голосовые ответы вслух (TTS)"
            isChecked = prefs.getBoolean("tts_enabled", true)
        }

        val testAiButton = Button(this).apply {
            text = "⚡ ТЕСТ AI И ГОЛОСА"
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
                        .putBoolean("ai_reply_enabled", aiReplyEnable.isChecked)
                        .putBoolean("tts_enabled", ttsEnable.isChecked)
                        .putString("ai_api_key", aiApiKey.text.toString().trim())
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
            packages, reply, aiApiKey, aiReplyEnable, ttsEnable, testAiButton, save, access,
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
                    val content = service.readCurrentScreen()
                    status.text = "Экран прочитан. ALTER произносит результат."
                    speak(content)
                }
            }
        }
        root.addView(screenButton)


        val visionButton = Button(this).apply {
            text = "📷 ПРОЧИТАТЬ ФОТОГРАФИЮ"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, imageRequest)
            }
        }
        root.addView(visionButton)

        val aiVisionButton = Button(this).apply {
            text = "🧠 ЧТО НА ФОТО? (ЛОКАЛЬНЫЙ AI)"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, aiImageRequest)
            }
        }
        root.addView(aiVisionButton)


        val visionTitle = TextView(this).apply {
            text = "ALTER VISION — РЕЗУЛЬТАТ"
            textSize = 18f
            setPadding(0, 24, 0, 12)
        }
        root.addView(visionTitle)

        visionResult = TextView(this).apply {
            text = "Выбери фотографию для распознавания."
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(235, 240, 245))
            setTextColor(Color.BLACK)
        }
        root.addView(visionResult)

        val readResult = Button(this).apply {
            text = "🔊 ПРОЧИТАТЬ РЕЗУЛЬТАТ"
            setOnClickListener {
                if (lastVisionText.isNotBlank()) {
                    speak(lastVisionText)
                } else {
                    Toast.makeText(this@MainActivity, "Сначала распознай фотографию", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(readResult)

        val copyResult = Button(this).apply {
            text = "📋 КОПИРОВАТЬ ТЕКСТ"
            setOnClickListener {
                if (lastVisionText.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ALTER Vision", lastVisionText))
                    Toast.makeText(this@MainActivity, "Текст скопирован", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(copyResult)


        val floatingButton = Button(this).apply {
            text = "🟣 ПЛАВАЮЩИЙ ALTER: ВКЛ / ВЫКЛ"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Разреши отображение поверх приложений."
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                } else if (FloatingAlterService.active) {
                    stopService(
                        Intent(
                            this@MainActivity,
                            FloatingAlterService::class.java
                        )
                    )
                    status.text = "Плавающий ALTER выключен."
                } else {
                    startService(
                        Intent(
                            this@MainActivity,
                            FloatingAlterService::class.java
                        )
                    )
                    status.text = "Запускаю плавающий ALTER."
                }
            }
        }
        root.addView(floatingButton)



        // NEXUS_CORE_011
        val nexusCore = NexusCoreView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                NexusTheme.dp(this@MainActivity, 220)
            )
            setOnClickListener {
                mic.performClick()
            }
        }

        root.addView(nexusCore, 2)

        // NEXUS_UI_011
        title.apply {
            textSize = 28f
            setTextColor(NexusTheme.white)
            setPadding(0, NexusTheme.dp(this@MainActivity, 12), 0,
                NexusTheme.dp(this@MainActivity, 8))
        }

        info.apply {
            textSize = 12f
            setTextColor(NexusTheme.cyan)
            letterSpacing = 0.12f
            setPadding(0, 0, 0, NexusTheme.dp(this@MainActivity, 20))
        }

        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)

            if (child is Button) {
                NexusTheme.styleButton(child)
            } else if (child is EditText) {
                child.setTextColor(NexusTheme.white)
                child.setHintTextColor(NexusTheme.muted)
                child.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        NexusTheme.cyan
                    )
            } else if (child is TextView &&
                child !== title &&
                child !== info) {
                child.setTextColor(NexusTheme.muted)
            }

            val params = child.layoutParams
            if (params is LinearLayout.LayoutParams) {
                params.bottomMargin = NexusTheme.dp(this, 10)
                child.layoutParams = params
            }
        }

        NexusTheme.styleButton(mic, true)
        NexusTheme.styleButton(floatingButton, true)

        visionResult.apply {
            setBackgroundColor(NexusTheme.surface)
            setTextColor(NexusTheme.white)
            setPadding(
                NexusTheme.dp(this@MainActivity, 16),
                NexusTheme.dp(this@MainActivity, 16),
                NexusTheme.dp(this@MainActivity, 16),
                NexusTheme.dp(this@MainActivity, 16)
            )
        }


        // VOICE_UI_013
        val voiceControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val startVoiceButton = Button(this).apply {
            text = "START SERVICE"
            setOnClickListener {
                val permission = android.Manifest.permission.RECORD_AUDIO

                if (android.os.Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(permission) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(permission), 1313)
                } else {
                    try {
                        val serviceIntent = Intent(
                            this@MainActivity,
                            VoiceAutopilotService::class.java
                        )
                        startForegroundService(serviceIntent)
                        status.text = "Сервис запущен. Прослушивание пока недоступно."
                    } catch (e: Exception) {
                        status.text = "Ошибка запуска: ${e.message}"
                    }
                }
            }
        }

        val stopVoiceButton = Button(this).apply {
            text = "STOP SERVICE"
            setOnClickListener {
                stopService(
                    Intent(
                        this@MainActivity,
                        VoiceAutopilotService::class.java
                    )
                )
                status.text = "Сервис остановлен."
            }
        }

        NexusTheme.styleButton(startVoiceButton, true)
        NexusTheme.styleButton(stopVoiceButton)

        voiceControls.addView(
            startVoiceButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        voiceControls.addView(
            stopVoiceButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(voiceControls)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusTheme.background)
            isFillViewport = true
            addView(root)
        })

        refresh()
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
            val enhanced = ((gray - 128) * 1.35 + 128).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(enhanced, enhanced, enhanced)
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        if (resized !== source) resized.recycle()
        return output
    }


    private fun classifyPhoto(uri: Uri) {
        status.text = "AI анализирует фотографию..."

        Thread {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                var sample = 1
                while (maxOf(options.outWidth / sample,
                        options.outHeight / sample) > 1600) {
                    sample *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                } ?: throw Exception("Не удалось открыть фото")

                try {
                    val base = BaseOptions.builder()
                        .setModelAssetPath("efficientnet_lite0.tflite")
                        .build()

                    val config = ImageClassifier.ImageClassifierOptions.builder()
                        .setBaseOptions(base)
                        .setMaxResults(3)
                        .build()

                    ImageClassifier.createFromOptions(this, config).use { classifier ->
                        val image = BitmapImageBuilder(bitmap).build()
                        val result = classifier.classify(image)
                        val categories = result.classificationResult()
                            .classifications()
                            .flatMap { it.categories() }
                            .sortedByDescending { it.score() }
                            .take(3)

                        val description = if (categories.isEmpty()) {
                            "AI не смог определить объект."
                        } else {
                            categories.joinToString("\n") {
                                val name = it.displayName().ifBlank {
                                    it.categoryName()
                                }
                                val percent = (it.score() * 100).toInt()
                                "$name — $percent%"
                            }
                        }

                        runOnUiThread {
                            lastVisionText = description
                            visionResult.text =
                                "AI предполагает:\n$description"
                            status.text = "AI-анализ завершён."
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "AI Vision: ${e.message}"
                }
            }
        }.start()
    }

    private fun recognizeImage(uri: Uri) {
        status.text = "ALTER анализирует изображение..."

        Thread {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: throw Exception("Не удалось открыть изображение")

                val tessData = File(filesDir, "tessdata")
                if (!tessData.exists()) tessData.mkdirs()

                for (language in listOf("rus", "eng")) {
                    val target = File(tessData, "$language.traineddata")
                    if (!target.exists() || target.length() == 0L) {
                        assets.open("tessdata/$language.traineddata").use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                val api = TessBaseAPI()
                try {
                    if (!api.init(filesDir.absolutePath, "rus+eng")) {
                        throw Exception("Не удалось запустить OCR")
                    }
                    api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    val prepared = prepareVisionImage(bitmap)
                    try {
                        api.setImage(prepared)
                        val result = api.utF8Text.orEmpty().trim()
                        runOnUiThread {
                            lastVisionText = result
                            visionResult.text = if (result.isBlank())
                                "Текст на изображении не найден."
                            else result
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
                runOnUiThread {
                    status.text = "Vision: ${e.message}"
                }
            }
        }.start()
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

        if (requestCode == aiImageRequest && resultCode == RESULT_OK) {
            data?.data?.let { classifyPhoto(it) }
            return
        }

        if (requestCode == imageRequest && resultCode == RESULT_OK) {
            data?.data?.let { recognizeImage(it) }
            return
        }

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

        // VOICE_SCREEN_ROUTER_014
        val screenCommand = command
            .lowercase(Locale.ROOT)
            .trim()
            .replace(
                Regex("^(альтер|alter)[,\\s:!]+"),
                ""
            )
            .trim()

        val screenAction = when {
            screenCommand in listOf(
                "назад",
                "вернись назад",
                "шаг назад"
            ) -> "BACK"

            screenCommand in listOf(
                "домой",
                "на главный экран",
                "главный экран"
            ) -> "HOME"

            screenCommand.startsWith("нажми на ") ||
            screenCommand.startsWith("нажми ") ||
            screenCommand.startsWith("кликни на ") ||
            screenCommand.startsWith("кликни ") -> "TAP"

            else -> null
        }

        if (screenAction != null) {
            val service = ScreenObserverService.instance

            if (service == null) {
                status.text = "Включи службу ALTER в специальных возможностях."
                speak("Для управления экраном включи специальные возможности.")
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
                return
            }

            val success = when (screenAction) {
                "BACK" -> service.goBack()
                "HOME" -> service.goHome()
                "TAP" -> {
                    val target = screenCommand
                        .replace(
                            Regex("^(нажми|кликни)\\s+(на\\s+)?"),
                            ""
                        )
                        .trim()

                    target.isNotBlank() && service.tapByText(target)
                }
                else -> false
            }

            status.text = if (success) {
                "Команда выполнена: $screenCommand"
            } else {
                "Не удалось выполнить: $screenCommand"
            }

            // Не озвучиваем результат после нажатия:
            // действие могло открыть другое приложение.
            return
        }



        // SMART_INTENT_ROUTER_012
        val intentResult = if (
            command.contains("автоответ")
        ) {
            AlterIntentParser.Result(
                AlterIntentParser.Type.UNKNOWN
            )
        } else {
            AlterIntentParser.parse(command)
        }

        when (intentResult.type) {
            AlterIntentParser.Type.READ_SCREEN -> {
                val service = ScreenObserverService.instance
                if (service == null) {
                    status.text = "Включи доступ к специальным возможностям."
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                } else {
                    val content = service.readCurrentScreen()
                    status.text = "ALTER читает экран."
                    speak(content)
                }
                return
            }

            AlterIntentParser.Type.SET_ALARM -> {
                val ok = AlterActions.setAlarm(
                    this,
                    intentResult.hour,
                    intentResult.minute
                )
                status.text = if (ok) {
                    "Открываю установку будильника."
                } else {
                    "Не удалось открыть будильник."
                }
                if (ok) speak("Открываю будильник.")
                return
            }

            AlterIntentParser.Type.SAVE_NOTE -> {
                val ok = AlterActions.saveNote(
                    this,
                    intentResult.argument
                )
                status.text = if (ok) {
                    "Заметка сохранена."
                } else {
                    "Не удалось сохранить заметку."
                }
                if (ok) speak("Заметка сохранена.")
                return
            }

            AlterIntentParser.Type.INSTALL_APP -> {
                val ok = AlterActions.findAppInStore(
                    this,
                    intentResult.argument
                )
                status.text = if (ok) {
                    "Ищу приложение в Google Play."
                } else {
                    "Не удалось открыть магазин приложений."
                }
                if (ok) speak("Открываю магазин приложений.")
                return
            }

            AlterIntentParser.Type.OPEN_APP -> {
                val target = intentResult.argument
                if (target == "настройки") {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    return
                }

                val ok = AlterActions.openApp(this, target)
                if (ok) {
                    status.text = "Открываю $target"
                    speak("Открываю $target")
                    return
                }

                // Неизвестное приложение: старый обработчик
                // получает возможность выполнить команду.
            }

            AlterIntentParser.Type.UNKNOWN -> {
                // Оставляем существующие команды без изменений.
            }
        }


        when {
            "что на экране" in command ||
            "прочитай экран" in command ||
            "прочти экран" in command -> {
                val service = ScreenObserverService.instance
                if (service == null) {
                    val message = "Включи доступ к специальным возможностям."
                    status.text = message
                    speak(message)
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    val content = service.readCurrentScreen()
                    status.text = "ALTER читает экран."
                    speak(content)
                }
            }
            "открой телеграм" in command ||
            "открыть телеграм" in command ||
            "открой telegram" in command -> {
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("tg://")
                    ).apply {
                        setPackage("org.telegram.messenger")
                    }
                    startActivity(intent)
                    speak("Открываю Телеграм.")
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
                speak("Автоответы выключены.")
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
                    speak("Автоответы включены.")
                }
            }

            else -> {
                status.text = "Команда пока не поддерживается: $command"
            }
        }
    }
}
