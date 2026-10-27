package com.alter.autopilot

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

object AlterActions {

    fun openApp(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val raw = appName.trim().lowercase()
        if (raw.isBlank()) return false

        val aliases = mapOf(
            "телеграм" to "telegram",
            "телеграмм" to "telegram",
            "телега" to "telegram",
            "тг" to "telegram",
            "ютуб" to "youtube",
            "ютюб" to "youtube",
            "вацап" to "whatsapp",
            "ватсап" to "whatsapp",
            "вотсап" to "whatsapp",
            "хром" to "chrome",
            "браузер" to "chrome",
            "плей маркет" to "play store",
            "плеймаркет" to "play store",
            "вк" to "vk",
            "вконтакте" to "vk"
        )

        val query = aliases[raw] ?: raw

        // 1. Прямой запуск по известным пакетам (100% надёжность)
        val directPackages = mapOf(
            "telegram" to listOf(
                "org.telegram.messenger",
                "org.telegram.messenger.web",
                "org.thunderdog.challegram",
                "org.telegram.plus"
            ),
            "whatsapp" to listOf(
                "com.whatsapp",
                "com.whatsapp.w4b"
            ),
            "youtube" to listOf(
                "com.google.android.youtube"
            ),
            "chrome" to listOf(
                "com.android.chrome"
            ),
            "vk" to listOf(
                "com.vkontakte.android"
            ),
            "play store" to listOf(
                "com.android.vending"
            )
        )

        val packagesToTry = directPackages[query]
        if (packagesToTry != null) {
            for (pkg in packagesToTry) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        // 2. Поиск по всем установленным приложениям
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcherIntent, 0)

            val matched = apps.firstOrNull { info ->
                val label = info.loadLabel(pm).toString().lowercase()
                label == query || label == raw
            } ?: apps.firstOrNull { info ->
                val label = info.loadLabel(pm).toString().lowercase()
                label.contains(query) || label.contains(raw)
            }

            if (matched != null) {
                val intent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(
                            matched.activityInfo.packageName,
                            matched.activityInfo.name
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    fun findAppInStore(context: Context, appName: String): Boolean {
        val name = appName.trim()
        if (name.isBlank()) return false
        val encoded = android.net.Uri.encode(name)

        val marketIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=$encoded&c=apps")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun setAlarm(context: Context, hour: Int, minute: Int): Boolean {
        if (hour !in 0..23 || minute !in 0..59) return false
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "ALTER")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveNote(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val prefs = context.getSharedPreferences("alter_notes", Context.MODE_PRIVATE)
            val notes = JSONArray(prefs.getString("notes", "[]") ?: "[]")
            val note = JSONObject().apply {
                put("text", text.trim())
                put("created", System.currentTimeMillis())
            }
            notes.put(note)
            prefs.edit().putString("notes", notes.toString()).apply()
            Toast.makeText(context, "ALTER: заметка сохранена", Toast.LENGTH_SHORT).show()
            true
        } catch (_: Exception) {
            false
        }
    }
}
