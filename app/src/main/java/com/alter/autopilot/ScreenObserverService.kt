package com.alter.autopilot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenObserverService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun readCurrentScreen(): String {
        val root = rootInActiveWindow
            ?: return "Не удалось получить содержимое экрана."
        return try {
            val lines = collectText(root)
            if (lines.isEmpty()) "Текст на экране не найден."
            else lines.joinToString("\n")
        } finally {
            root.recycle()
        }
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Никакого постоянного сбора содержимого экрана.
        // Чтение выполняется только по запросу пользователя.
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: ScreenObserverService? = null
            private set

        fun collectText(node: AccessibilityNodeInfo?, limit: Int = 30): List<String> {
            if (node == null) return emptyList()
            val result = mutableListOf<String>()

            fun visit(current: AccessibilityNodeInfo?) {
                if (current == null || result.size >= limit) return

                val text = current.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    result.add(text.take(150))
                }

                for (i in 0 until current.childCount) {
                    visit(current.getChild(i))
                }
            }

            visit(node)
            return result
        }
    }
}
