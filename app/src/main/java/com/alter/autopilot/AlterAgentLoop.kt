package com.alter.autopilot

import android.content.Context
import org.json.JSONObject

object AlterAgentLoop {

    @Volatile
    var isRunning = false
        private set

    fun executeGoal(context: Context, goal: String, onStatusUpdate: ((String) -> Unit)? = null) {
        if (isRunning) {
            val msg = "Агент уже выполняет другую задачу."
            AlterSpeaker.speak(context, msg)
            onStatusUpdate?.invoke(msg)
            return
        }

        val rawGoal = goal.trim()
        if (rawGoal.isBlank()) return

        Thread {
            isRunning = true
            try {
                // 1. Проверяем будильник напрямую
                val alarmMatch = Regex("""(?:будильник|разбуди).*?\b([01]?\d|2[0-3])[:. ]([0-5]\d)\b""", RegexOption.IGNORE_CASE).find(rawGoal)
                if (alarmMatch != null) {
                    val hour = alarmMatch.groupValues[1].toInt()
                    val minute = alarmMatch.groupValues[2].toInt()
                    val ok = AlterActions.setAlarm(context, hour, minute)
                    val msg = if (ok) "Будильник установлен на %02d:%02d".format(hour, minute) else "Не удалось завести будильник"
                    AlterSpeaker.speak(context, msg)
                    onStatusUpdate?.invoke(msg)
                    return@Thread
                }

                // 2. Проверяем заметку напрямую
                val noteMatch = Regex("""^(?:запиши|сохрани|создай заметку|запомни)\s+(.+)$""", RegexOption.IGNORE_CASE).find(rawGoal)
                if (noteMatch != null) {
                    val noteText = noteMatch.groupValues[1].trim()
                    AlterActions.saveNote(context, noteText)
                    val msg = "Заметка сохранена: $noteText"
                    AlterSpeaker.speak(context, msg)
                    onStatusUpdate?.invoke(msg)
                    return@Thread
                }

                // 3. Автономный цикл через Accessibility и Gemini
                val screen = ScreenObserverService.instance
                if (screen == null) {
                    val msg = "Для автономного управления приложениями включи службу ALTER в Специальных возможностях."
                    AlterSpeaker.speak(context, msg)
                    onStatusUpdate?.invoke(msg)
                    return@Thread
                }

                val startMsg = "Принято: $rawGoal. Начинаю выполнение."
                AlterSpeaker.speak(context, startMsg)
                onStatusUpdate?.invoke(startMsg)

                val history = mutableListOf<String>()
                val maxSteps = 8

                for (step in 1..maxSteps) {
                    if (!isRunning) break

                    // Читаем текущее состояние экрана
                    val screenState = screen.readCurrentScreen()

                    val prompt = buildString {
                        append("Ты — автономный ИИ-агент ALTER, управляющий смартфоном Android.\n")
                        append("Цель пользователя: \"$rawGoal\"\n")
                        append("Текущий экран (шаг $step из $maxSteps):\n$screenState\n\n")
                        if (history.isNotEmpty()) {
                            append("История предыдущих действий:\n")
                            history.forEach { append("- $it\n") }
                            append("\n")
                        }
                        append("Выбери РОВНО ОДНО следующее логическое действие для приближения к цели.\n")
                        append("Ответ дай СТРОГО в формате JSON (без лишнего текста вокруг):\n")
                        append("{\"action\": \"open_app\", \"target\": \"название приложения\", \"comment\": \"Открываю Озон\"}\n")
                        append("{\"action\": \"tap\", \"target\": \"текст кнопки или элемента\", \"comment\": \"Нажимаю поиск\"}\n")
                        append("{\"action\": \"type\", \"text\": \"текст для ввода\", \"comment\": \"Ввожу запрос\"}\n")
                        append("{\"action\": \"scroll\", \"direction\": \"down\", \"comment\": \"Листаю вниз\"}\n")
                        append("{\"action\": \"back\", \"comment\": \"Возвращаюсь назад\"}\n")
                        append("{\"action\": \"home\", \"comment\": \"Перехожу на главный экран\"}\n")
                        append("{\"action\": \"done\", \"comment\": \"Цель успешно достигнута!\"}\n")
                    }

                    val rawResponse = AlterAiEngine.ask(context, prompt)
                    val jsonAction = parseActionJson(rawResponse)

                    if (jsonAction == null) {
                        // Если нейросеть ответила простым текстом
                        AlterSpeaker.speak(context, rawResponse)
                        onStatusUpdate?.invoke(rawResponse)
                        break
                    }

                    val action = jsonAction.optString("action", "done")
                    val comment = jsonAction.optString("comment", "")
                    val target = jsonAction.optString("target", "")
                    val textToType = jsonAction.optString("text", "")

                    if (comment.isNotBlank()) {
                        AlterSpeaker.speak(context, comment)
                        onStatusUpdate?.invoke("Шаг $step: $comment")
                    }

                    if (action == "done") {
                        break
                    }

                    // Выполняем действие
                    when (action) {
                        "open_app" -> {
                            AlterActions.openApp(context, target)
                            history.add("Открыл приложение: $target")
                        }
                        "tap" -> {
                            screen.tapByText(target)
                            history.add("Нажал на: $target")
                        }
                        "type" -> {
                            screen.typeText(textToType)
                            history.add("Ввёл текст: $textToType")
                        }
                        "scroll" -> {
                            screen.scrollDown()
                            history.add("Прокрутил экран вниз")
                        }
                        "back" -> {
                            screen.goBack()
                            history.add("Нажал назад")
                        }
                        "home" -> {
                            screen.goHome()
                            history.add("Нажал домой")
                        }
                    }

                    // Пауза между шагами для обновления интерфейса
                    Thread.sleep(1400)
                }

            } catch (e: Exception) {
                val errMsg = "Ошибка агента: ${e.localizedMessage}"
                onStatusUpdate?.invoke(errMsg)
            } finally {
                isRunning = false
            }
        }.start()
    }

    fun stop() {
        isRunning = false
    }

    private fun parseActionJson(raw: String): JSONObject? {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            val jsonString = trimmed.substring(start, end + 1)
            return try {
                JSONObject(jsonString)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}
