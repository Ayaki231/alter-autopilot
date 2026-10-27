package com.alter.autopilot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

object AlterVoiceCommands {
    fun execute(context: Context, spoken: String): String {
        val command = spoken.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("^(альтер|alter)[,\\s:!]*"), "")
            .trim()

        val screen = ScreenObserverService.instance

        if (command in listOf("назад", "вернись назад", "шаг назад")) {
            return if (screen?.goBack() == true) "Назад выполнено"
            else "Включи специальные возможности ALTER"
        }

        if (command in listOf("домой", "главный экран", "на главный экран")) {
            return if (screen?.goHome() == true) "Главный экран открыт"
            else "Включи специальные возможности ALTER"
        }

        if (command in listOf("прокрути вниз", "листай вниз", "вниз")) {
            return if (screen?.scrollDown() == true) "Экран прокручен"
            else "Не удалось прокрутить экран"
        }

        val tap = Regex("^(нажми|кликни)\\s+(на\\s+)?(.+)$")
            .find(command)
        if (tap != null) {
            val target = tap.groupValues[3].trim()
            return if (screen?.tapByText(target) == true) "Нажато: $target"
            else "Не найдена кнопка: $target"
        }

        val type = Regex("^(напиши|введи|впиши)\\s+(.+)$")
            .find(command)
        if (type != null) {
            val value = type.groupValues[2]
            return if (screen?.typeText(value) == true) "Текст введён"
            else "Сначала выбери поле ввода"
        }

        val open = Regex("^открой\\s+(.+)$").find(command)
        if (open != null) {
            val app = open.groupValues[1].trim()
            val packages = mapOf(
                "telegram" to "org.telegram.messenger",
                "телеграм" to "org.telegram.messenger",
                "whatsapp" to "com.whatsapp",
                "ватсап" to "com.whatsapp",
                "youtube" to "com.google.android.youtube",
                "ютуб" to "com.google.android.youtube",
                "chrome" to "com.android.chrome",
                "хром" to "com.android.chrome",
                "настройки" to "com.android.settings"
            )
            val pkg = packages[app] ?: return "Приложение пока неизвестно: $app"
            val intent = context.packageManager
                .getLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(pkg)
                }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            return try {
                context.startActivity(intent)
                "Открываю $app"
            } catch (e: Exception) {
                "Не удалось открыть $app: ${e.javaClass.simpleName}"
            }
        }

        return "Команда пока не поддерживается: $command"
    }
}
