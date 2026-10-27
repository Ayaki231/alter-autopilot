package com.alter.autopilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.util.Locale

object AlterVoiceCommands {
    fun execute(context: Context, spoken: String): String {
        val command = spoken.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("^(?:эй\\s+)?(?:альтер|алтер|alter)[,\\s:!]*"), "")
            .trim()

        if (command.isBlank()) return "Слушаю тебя"

        val screen = ScreenObserverService.instance

        // 1. Системные действия с экраном
        if (command in listOf("назад", "вернись назад", "шаг назад")) {
            return if (screen?.goBack() == true) "Назад выполнено"
            else "Включи службу ALTER в специальных возможностях"
        }

        if (command in listOf("домой", "главный экран", "на главный экран")) {
            return if (screen?.goHome() == true) "Главный экран открыт"
            else "Включи службу ALTER в специальных возможностях"
        }

        if (command in listOf("прокрути вниз", "листай вниз", "вниз", "прокрути")) {
            return if (screen?.scrollDown() == true) "Экран прокручен"
            else "Не удалось прокрутить экран"
        }

        // 2. Отправка сообщений в Telegram (например: "отправь сообщение в телеграмме контакту макс привет")
        val sendPattern = Regex("^(?:отправь|напиши)(?:\\s+сообщение)?(?:\\s+в\\s+(?:телеграм(?:м)?(?:е)?|тг))?(?:\\s+(?:контакту|для|пользователю|кому))?\\s+([a-zA-Zа-яА-Я0-9_]+)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val sendMatch = sendPattern.find(command)
        if (sendMatch != null) {
            val contact = sendMatch.groupValues[1].trim()
            val messageText = sendMatch.groupValues[2].trim()

            // Запуск Telegram с предварительно заполненным текстом
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                `package` = "org.telegram.messenger"
                putExtra(Intent.EXTRA_TEXT, messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(sendIntent)
                if (screen != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        screen.tapByText(contact)
                    }, 1200)
                }
                "Открываю Telegram для отправки сообщения $contact: $messageText"
            } catch (e: Exception) {
                AlterActions.openApp(context, "telegram")
                "Открываю Telegram. Выбери $contact для отправки."
            }
        }

        // 3. Клики и ввод текста
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

        // 4. Открытие приложений (открой телеграм, запусти ютуб, включи ватсап)
        val open = Regex("^(?:открой|запусти|включи)\\s+(?:мне\\s+)?(?:приложение\\s+)?(.+)$").find(command)
        if (open != null) {
            val app = open.groupValues[1].trim().removeSuffix(" пожалуйста").trim()
            val ok = AlterActions.openApp(context, app)
            return if (ok) "Открываю $app"
            else "Не удалось найти приложение $app. Убедись, что оно установлено."
        }

        // 5. Вопрос по экрану (гибкое распознавание: "что на экране", "что у меня на экране", "перескажи экран")
        val isScreenQuery = command.contains("на экране") ||
                command.contains("прочитай экран") ||
                command.contains("перескажи экран") ||
                command.contains("посмотри на экран") ||
                command.contains("что тут написано") ||
                command.contains("что здесь написано")

        if (isScreenQuery) {
            if (screen == null) {
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {}
                return "Для чтения экрана включи службу ALTER в Специальных возможностях. Я открыл настройки."
            }

            val screenText = screen.readCurrentScreen().trim()
            if (screenText.isBlank() || screenText.startsWith("Не удалось")) {
                return "На экране сейчас нет текста для чтения."
            }
            return AlterAiEngine.ask(context, "Кратко перескажи, что сейчас находится на экране пользователя", screenText)
        }

        // 6. Любой общий вопрос через AI
        return AlterAiEngine.ask(context, command)
    }
}
