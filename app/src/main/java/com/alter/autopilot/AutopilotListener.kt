package com.alter.autopilot

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

class AutopilotListener : NotificationListenerService() {
    private val recent = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("alter", MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return
        val allowed = prefs.getString("packages", "")!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (sbn.packageName !in allowed || sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val action = n.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput && it.resultKey.isNotBlank() } == true
        } ?: return
        val text = prefs.getString("reply", "")?.trim().orEmpty()
        if (!isSafe(body, sender, text)) { record("Пропущено: проверка содержания (${sbn.packageName})"); return }
        val key = digest("${sbn.key}|$sender|$body")
        val now = System.currentTimeMillis()
        synchronized(recent) {
            recent.entries.removeAll { now - it.value > 24 * 60 * 60 * 1000L }
            if (recent.containsKey(key)) return
            // Mark before sending to avoid duplicate replies on notification updates.
            recent[key] = now
            if (recent.size > 300) recent.remove(recent.keys.first())
        }
        try {
            val inputs = action.remoteInputs ?: return
            val results = Bundle()
            inputs.filter { it.allowFreeFormInput }.forEach { results.putCharSequence(it.resultKey, text) }
            val intent = Intent()
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(this, 0, intent)
            record("Запрос отправки выполнен: ${sbn.packageName} (${sender.take(30)})")
        } catch (e: PendingIntent.CanceledException) {
            record("Ошибка: действие ответа недоступно (${sbn.packageName})")
        } catch (e: Exception) {
            record("Ошибка отправки: ${e.javaClass.simpleName}")
        }
    }

    private fun isSafe(body: String, sender: String, reply: String): Boolean {
        if (body.isBlank() || body.length > 1500 || reply.isBlank() || reply.length > 300) return false
        if (sender.isBlank() || sender.equals("Android System", true)) return false
        val risky = Regex("(?i)(\\botp\\b|\\b2fa\\b|\\bpin\\b|парол|код подтвержден|verification code|one.time password|\\b(?:\\d[ -]?){6}\\b|банк|оплат|перевод|payment|bank|password|срочно переведи)")
        if (risky.containsMatchIn(body) || risky.containsMatchIn(reply)) return false
        if (body.trim() == reply.trim()) return false
        return true
    }

    private fun digest(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun record(message: String) {
        val prefs = getSharedPreferences("alter", MODE_PRIVATE)
        val old = prefs.getString("log", "").orEmpty()
        val line = "${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}: $message"
        prefs.edit().putString("log", (line + "\n" + old).take(4000)).apply()
    }
}
