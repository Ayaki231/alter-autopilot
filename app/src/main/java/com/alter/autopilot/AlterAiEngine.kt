package com.alter.autopilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object AlterAiEngine {

    fun generateReply(context: Context, sender: String, incomingText: String): String {
        val prefs = context.getSharedPreferences("alter", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("ai_api_key", "")?.trim().orEmpty()
        val customPrompt = prefs.getString("ai_personality", "")?.trim().orEmpty()

        if (apiKey.isBlank()) {
            return prefs.getString("reply", "Здравствуйте! Сейчас не могу ответить, напишу позже.")
                ?.trim().orEmpty()
        }

        val systemInstruction = if (customPrompt.isNotBlank()) {
            "$customPrompt\nОтвечай кратко, от первого лица (1-2 коротких предложения)."
        } else {
            "Ты — личный помощник владельца телефона. Собеседник ($sender) прислал сообщение: \"$incomingText\". " +
            "Напиши короткий, естественный и вежливый ответ на русском языке от первого лица (максимум 1-2 предложения), как будто отвечает сам владелец. " +
            "Не используй шаблонных фраз вроде 'Как ИИ...', отвечай прямо по делу."
        }

        return try {
            val reply = callGemini(apiKey, systemInstruction)
            if (reply.isNotBlank()) reply else prefs.getString("reply", "").orEmpty()
        } catch (_: Exception) {
            prefs.getString("reply", "").orEmpty()
        }
    }

    fun ask(context: Context, userQuery: String, screenContext: String? = null): String {
        val prefs = context.getSharedPreferences("alter", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("ai_api_key", "")?.trim().orEmpty()

        if (apiKey.isBlank()) {
            return "Чтобы я мог отвечать на вопросы, введи бесплатный ключ Gemini в настройках ALTER."
        }

        val prompt = buildString {
            append("Ты — ALTER, футуристичный автономный голосовой ассистент на Android. ")
            append("Отвечай кратко, чётко и емко на русском языке (2-3 предложения для озвучивания вслух).\n")
            if (!screenContext.isNullOrBlank()) {
                append("Контекст экрана пользователя:\n$screenContext\n\n")
            }
            append("Запрос пользователя: $userQuery")
        }

        return try {
            val answer = callGemini(apiKey, prompt)
            if (answer.isNotBlank()) answer else "Не удалось получить ответ от нейросети."
        } catch (e: Exception) {
            "Ошибка связи с ИИ: ${e.localizedMessage ?: "проверь интернет"}"
        }
    }

    private fun callGemini(apiKey: String, prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
        }

        val bodyJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val item = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", parts)
                }
                put(item)
            }
            put("contents", contents)

            val genConfig = JSONObject().apply {
                put("maxOutputTokens", 250)
                put("temperature", 0.7)
            }
            put("generationConfig", genConfig)
        }

        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(bodyJson.toString())
            writer.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val response = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use {
                it.readText()
            }
            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            return parts.getJSONObject(0).optString("text", "").trim()
        } else {
            val errStream = conn.errorStream
            val errText = if (errStream != null) {
                BufferedReader(InputStreamReader(errStream, StandardCharsets.UTF_8)).use { it.readText() }
            } else ""
            throw RuntimeException("HTTP $code: $errText")
        }
    }
}
