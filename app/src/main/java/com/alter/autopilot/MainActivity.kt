package com.alter.autopilot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("alter", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 24) }
        val title = TextView(this).apply { text = "ALTER Autopilot 0.1"; textSize = 24f }
        val info = TextView(this).apply { text = "Локальный автоответчик. Включайте только для выбранных приложений. Не использует облачный ИИ." }
        val enable = Switch(this).apply { text = "Разрешить автоответы"; isChecked = prefs.getBoolean("enabled", false) }
        val packages = EditText(this).apply {
            hint = "Пакеты через запятую: org.telegram.messenger"
            setSingleLine(false); minLines = 2
            setText(prefs.getString("packages", ""))
        }
        val reply = EditText(this).apply {
            hint = "Текст автоответа"
            setText(prefs.getString("reply", "Сейчас не могу ответить. Напишу позже."))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val save = Button(this).apply { text = "Сохранить правила" }
        val access = Button(this).apply { text = "Открыть доступ к уведомлениям" }
        val log = TextView(this).apply { textSize = 12f }
        fun refresh() { log.text = "Последние события:\n" + prefs.getString("log", "Пока нет событий") }
        save.setOnClickListener {
            val allowed = packages.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }
            if (enable.isChecked && allowed.isEmpty()) {
                Toast.makeText(this, "Укажите хотя бы один пакет приложения", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            if (enable.isChecked && reply.text.isBlank()) {
                Toast.makeText(this, "Укажите текст ответа", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            prefs.edit().putBoolean("enabled", enable.isChecked).putString("packages", allowed.joinToString(","))
                .putString("reply", reply.text.toString()).apply()
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show(); refresh()
        }
        access.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        val refreshButton = Button(this).apply { text = "Обновить журнал"; setOnClickListener { refresh() } }
        listOf(title, info, enable, packages, reply, save, access, refreshButton, log).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) }); refresh()
    }
}
