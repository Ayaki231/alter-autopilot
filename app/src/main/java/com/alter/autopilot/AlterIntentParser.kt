package com.alter.autopilot

import java.util.Locale

object AlterIntentParser {

    enum class Type {
        OPEN_APP,
        SET_ALARM,
        SAVE_NOTE,
        READ_SCREEN,
        INSTALL_APP,
        UNKNOWN
    }

    data class Result(
        val type: Type,
        val argument: String = "",
        val hour: Int = -1,
        val minute: Int = -1
    )

    fun parse(command: String): Result {
        val text = command
            .lowercase(Locale.ROOT)
            .replace(Regex("[,!?]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(
                Regex("^(?:эй\\s+)?(?:alter|альтер)\\s+"),
                ""
            )
            .trim()

        val screenPhrases = listOf(
            "что на экране",
            "прочитай экран",
            "прочти экран",
            "посмотри на экран",
            "что тут написано",
            "что здесь написано"
        )

        if (screenPhrases.any { text.contains(it) }) {
            return Result(Type.READ_SCREEN)
        }

        val alarmWords = listOf(
            "будильник",
            "разбуди меня",
            "разбуди"
        )

        if (alarmWords.any { text.contains(it) }) {
            val time = Regex(
                """\b([01]?\d|2[0-3])[:. ]([0-5]\d)\b"""
            ).find(text)

            if (time != null) {
                return Result(
                    Type.SET_ALARM,
                    hour = time.groupValues[1].toInt(),
                    minute = time.groupValues[2].toInt()
                )
            }
        }

        val notePrefixes = listOf(
            "создай заметку",
            "сделай заметку",
            "запиши заметку",
            "сохрани заметку",
            "добавь заметку",
            "запомни",
            "запиши",
            "сохрани мысль"
        )

        for (prefix in notePrefixes) {
            if (text.startsWith(prefix)) {
                val content = text.removePrefix(prefix)
                    .trim()
                    .trimStart(':', ' ')
                return Result(Type.SAVE_NOTE, content)
            }
        }

        val installPrefixes = listOf(
            "установи ",
            "скачай ",
            "загрузи приложение ",
            "поставь приложение ",
            "скачай приложение ",
            "найди в плей маркете ",
            "найди в play market ",
            "найди в google play "
        )

        for (prefix in installPrefixes) {
            if (text.startsWith(prefix)) {
                return Result(
                    Type.INSTALL_APP,
                    text.removePrefix(prefix).trim()
                )
            }
        }

        // SMART_INTENT_FIX_012
        // Системные команды обрабатываются старой логикой.
        if ("автоответ" in text || "автоответы" in text) {
            return Result(Type.UNKNOWN)
        }

        val openPrefixes = listOf(
            "открой ",
            "открыть ",
            "запусти ",
            "запустить ",
            "включи ",
            "зайди в ",
            "перейди в ",
            "открой мне ",
            "включи мне "
        )

        for (prefix in openPrefixes.sortedByDescending { it.length }) {
            if (text.startsWith(prefix)) {
                val target = text.removePrefix(prefix)
                    .trim()
                    .removePrefix("мне ")
                    .removePrefix("приложение ")
                    .trim()
                    .removeSuffix(" пожалуйста")
                    .trim()

                if (target.isNotEmpty()) {
                    return Result(Type.OPEN_APP, target)
                }
            }
        }

        return Result(Type.UNKNOWN)
    }
}
