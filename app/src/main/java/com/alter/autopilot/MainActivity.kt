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


class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun speak(message: String) {
        if (ttsReady) {
            tts?.speak(
                message.take(700),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "alter_voice"
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
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
        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 24)
        }

        val title = TextView(this).apply {
            text = "ALTER Autopilot 0.9 Local AI Vision"
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

        setContentView(ScrollView(this).apply {
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
                                val name = it.displayName().orElse("").ifBlank {
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
