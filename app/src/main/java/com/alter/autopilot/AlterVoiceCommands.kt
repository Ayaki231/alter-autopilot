package com.alter.autopilot

import android.content.Context
import java.util.Locale

object AlterVoiceCommands {
    fun execute(context: Context, spoken: String): String {
        val command = spoken.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("^(?:эй\\s+)?(?:альтер|alter)[,\\s:!]*"), "")
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
        val tap = Regex("^(?:нажми|кликни)\\s+(?:на\\s+)?(.+)$").find(command)
        if (tap != null) {
            val target = tap.groupValues[1].trim()
            return if (screen?.tapByText(target) == true) "Нажато: $target"
            else "Не найдена кнопка: $target"
        }

        val type = Regex("^(?:напиши|введи|впиши)\\s+(.+)$").find(command)
        if (type != null) {
            val value = type.groupValues[1]
            return if (screen?.typeText(value) == true) "Текст введён"
            else "Сначала выбери поле ввода"
        }

        // 3. Открытие приложений (открой телеграм, запусти ютуб, включи ватсап)
        val open = Regex("^(?:открой|запусти|включи)\\s+(?:мне\\s+)?(?:приложение\\s+)?(.+)$").find(command)
        if (open != null) {
            val app = open.groupValues[1].trim().removeSuffix(" пожалуйста").trim()
            val ok = AlterActions.openApp(context, app)
            return if (ok) "Открываю $app"
            else "Не удалось найти приложение $app. Убедись, что оно установлено."
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
