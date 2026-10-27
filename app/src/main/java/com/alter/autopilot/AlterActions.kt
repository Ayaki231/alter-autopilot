package com.alter.autopilot

import android.app.Activity
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

object AlterActions {

    // UNIVERSAL_LAUNCHER_012
    fun openApp(activity: Activity, appName: String): Boolean {
        val pm = activity.packageManager
        val name = appName.trim().lowercase()

        if (name.isBlank()) return false

        val aliases = mapOf(
            "телеграм" to "telegram",
            "телеграмм" to "telegram",
            "телега" to "telegram",
            "ютуб" to "youtube",
            "вацап" to "whatsapp",
            "ватсап" to "whatsapp",
            "вотсап" to "whatsapp",
            "хром" to "chrome"
        )

        val query = aliases[name] ?: name

        val launcherIntent = Intent(
            Intent.ACTION_MAIN
        ).addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(launcherIntent, 0)

        val matched = apps.firstOrNull { info ->
            val label = info.loadLabel(pm)
                .toString()
                .lowercase()

            label == query
        } ?: apps.firstOrNull { info ->
            val label = info.loadLabel(pm)
                .toString()
                .lowercase()

            label.contains(query)
        }

        if (matched != null) {
            return try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(
                        matched.activityInfo.packageName,
                        matched.activityInfo.name
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }

        return false
    }


    // INSTALL_FROM_PLAY_012
    fun findAppInStore(
        activity: Activity,
        appName: String
    ): Boolean {
        val name = appName.trim()
        if (name.isBlank()) return false

        val encoded = android.net.Uri.encode(name)

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse(
                "market://search?q=$encoded&c=apps"
            )
        )

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse(
                "https://play.google.com/store/search" +
                "?q=$encoded&c=apps"
            )
        )

        return try {
            activity.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                activity.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun setAlarm(
        activity: Activity,
        hour: Int,
        minute: Int
    ): Boolean {
        if (hour !in 0..23 || minute !in 0..59) return false

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "ALTER")
        }

        return try {
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveNote(activity: Activity, text: String): Boolean {
        if (text.isBlank()) return false

        return try {
            val prefs = activity.getSharedPreferences(
                "alter_notes",
                Activity.MODE_PRIVATE
            )

            val notes = JSONArray(
                prefs.getString("notes", "[]") ?: "[]"
            )

            val note = JSONObject().apply {
                put("text", text.trim())
                put("created", System.currentTimeMillis())
            }

            notes.put(note)

            prefs.edit()
                .putString("notes", notes.toString())
                .apply()

            Toast.makeText(
                activity,
                "ALTER: заметка сохранена",
                Toast.LENGTH_SHORT
            ).show()

            true
        } catch (_: Exception) {
            false
        }
    }
}
