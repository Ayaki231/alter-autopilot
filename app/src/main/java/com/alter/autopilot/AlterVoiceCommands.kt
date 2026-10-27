package com.alter.autopilot

import android.content.Context
import android.content.Intent
import java.util.Locale

object AlterVoiceCommands {
    fun execute(context: Context, spoken: String): String {
        val command = spoken.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("^(альтер|alter)[,\\s:!]*"), "")
            .trim()

        if (command.isBlank()) return "Слушаю тебя"

        val screen = ScreenObserverService.instance

        // 1. Системные действия с экраном
        if (command in listOf("назад", "вернись назад", "шаг назад")) {
            return if (screen?.goBack() == true) "Назад выполнено"
            else "Включи специальные возможности ALTER"
        }

        if (command in listOf("домой", "главный экран", "на главный экран")) {
            return if (screen?.goHome() == true) "Главный экран открыт"
            else "Включи специальные возможности ALTER"
        }

        if (command in listOf("прокрути вниз", "листай вниз", "вниз", "прокрути")) {
            return if (screen?.scrollDown() == true) "Экран прокручен"
            else "Не удалось прокрутить экран"
        }

        // 2. Клики и ввод текста
        val tap = Regex("^(нажми|кликни)\\s+(на\\s+)?(.+)$").find(command)
        if (tap != null) {
            val target = tap.groupValues[3].trim()
            return if (screen?.tapByText(target) == true) "Нажато: $target"
            else "Не найдена кнопка: $target"
        }

        val type = Regex("^(напиши|введи|впиши)\\s+(.+)$").find(command)
        if (type != null) {
            val value = type.groupValues[2]
            return if (screen?.typeText(value) == true) "Текст введён"
            else "Сначала выбери поле ввода"
        }

        // 3. Открытие приложений
        val open = Regex("^открой\\s+(.+)$").find(command)
        if (open != null) {
            val app = open.groupValues[1].trim()
            val aliases = mapOf(
                "телеграм" to "telegram",
                "телеграмм" to "telegram",
                "телега" to "telegram",
                "ватсап" to "whatsapp",
                "вацап" to "whatsapp",
                "ютуб" to "youtube",
                "хром" to "chrome",
                "плей маркет" to "play store",
                "плеймаркет" to "play store"
            )

            val name = aliases[app] ?: app
            val pm = context.packageManager

            val query = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val matches = pm.queryIntentActivities(query, 0)
            val target = matches.firstOrNull {
                it.loadLabel(pm).toString().equals(name, ignoreCase = true)
            } ?: matches.firstOrNull {
                it.loadLabel(pm).toString().contains(name, ignoreCase = true)
            }

            if (target != null) {
                val label = target.loadLabel(pm).toString()
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(target.activityInfo.packageName, target.activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                return try {
                    context.startActivity(intent)
                    "Открываю $label"
                } catch (e: Exception) {
                    "Не удалось открыть $label"
                }
            }
        }

        // 4. Вопрос по экрану
        val screenPhrases = listOf("что на экране", "прочитай экран", "прочти экран", "посмотри на экран", "что здесь написано", "перескажи экран")
        if (screenPhrases.any { command.contains(it) }) {
            val screenText = screen?.readCurrentScreen().orEmpty()
            if (screenText.isBlank() || screenText.startsWith("Не удалось")) {
                return "Не удалось прочитать экран. Проверь доступ к специальным возможностям."
            }
            return AlterAiEngine.ask(context, "Кратко перескажи, что сейчас находится на экране пользователя", screenText)
        }

        // 5. Любой общий вопрос через AI
        return AlterAiEngine.ask(context, command)
    }
}
