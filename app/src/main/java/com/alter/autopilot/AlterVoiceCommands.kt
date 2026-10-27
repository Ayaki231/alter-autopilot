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
            val aliases = mapOf(
                "телеграм" to "telegram",
                "телеграмм" to "telegram",
                "ватсап" to "whatsapp",
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
                it.loadLabel(pm).toString().equals(
                    name, ignoreCase = true
                )
            } ?: matches.firstOrNull {
                it.loadLabel(pm).toString().contains(
                    name, ignoreCase = true
                )
            }

            if (target == null) {
                return "Не нашёл приложение: $app"
            }

            val label = target.loadLabel(pm).toString()
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(
                    target.activityInfo.packageName,
                    target.activityInfo.name
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                "Открываю $label"
            } catch (e: Exception) {
                val accessibility = ScreenObserverService.instance
                if (accessibility != null && accessibility.goHome()) {
                    android.os.Handler(
                        android.os.Looper.getMainLooper()
                    ).postDelayed({
                        accessibility.tapByText(label)
                    }, 900)
                    "Пробую открыть $label"
                } else {
                    "Не удалось открыть $label: ${e.javaClass.simpleName}"
                }
            }
        }

        return "Команда пока не поддерживается: $command"
    }
}
