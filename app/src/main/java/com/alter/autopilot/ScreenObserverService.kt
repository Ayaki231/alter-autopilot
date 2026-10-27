package com.alter.autopilot

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
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
            ?: return "Не удалось прочитать экран. Проверь доступ к специальным возможностям."

        return try {
            val packageName = root.packageName?.toString().orEmpty()
            val appName = getAppName(packageName)
            val lines = collectText(root, 40)

            if (lines.isEmpty()) {
                "Открыто приложение: $appName. Доступного текста на экране нет. Если здесь фотография, для её анализа потребуется функция распознавания изображений."
            } else {
                "Открыто приложение: $appName.\n" +
                    lines.joinToString("\n")
            }
        } finally {
            root.recycle()
        }
    }


    // SCREEN_CONTROL_014

    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun tapByText(target: String): Boolean {
        val query = target.trim()
        if (query.isBlank()) return false

        val root = rootInActiveWindow ?: return false

        try {
            val matches = mutableListOf<AccessibilityNodeInfo>()
            var visited = 0

            fun search(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || depth > 20 || visited >= 300) return

                visited++

                val text = node.text?.toString().orEmpty()
                val description =
                    node.contentDescription?.toString().orEmpty()

                if (
                    text.equals(query, ignoreCase = true) ||
                    description.equals(query, ignoreCase = true)
                ) {
                    matches.add(node)
                }

                for (i in 0 until node.childCount) {
                    search(node.getChild(i), depth + 1)
                }
            }

            search(root, 0)

            for (match in matches) {
                var current: AccessibilityNodeInfo? = match

                while (current != null) {
                    if (current.isClickable) {
                        return current.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    }
                    current = current.parent
                }
            }

            return false
        } finally {
            root.recycle()
        }
    }

    private fun getAppName(packageName: String): String {
        if (packageName.isBlank()) return "Неизвестное приложение"

        return when (packageName) {
            "org.telegram.messenger" -> "Telegram"
            "org.thunderdog.challegram" -> "Telegram X"
            "com.whatsapp" -> "WhatsApp"
            "com.android.chrome" -> "Google Chrome"
            "com.google.android.youtube" -> "YouTube"
            "com.instagram.android" -> "Instagram"
            "com.alter.autopilot" -> "ALTER Autopilot"
            else -> {
                try {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    packageName
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Чтение только по запросу пользователя.
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: ScreenObserverService? = null
            private set

        fun collectText(
            node: AccessibilityNodeInfo?,
            limit: Int = 40
        ): List<String> {
            if (node == null) return emptyList()

            val result = LinkedHashSet<String>()
            var visited = 0

            fun addText(value: CharSequence?) {
                val text = value?.toString()
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()

                if (text.isNotBlank()) {
                    result.add(text.take(200))
                }
            }

            fun visit(current: AccessibilityNodeInfo?, depth: Int) {
                if (current == null ||
                    result.size >= limit ||
                    visited >= 250 ||
                    depth > 20
                ) return

                visited++

                addText(current.text)
                addText(current.contentDescription)

                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    addText(current.hintText)
                }

                for (i in 0 until current.childCount) {
                    if (result.size >= limit) break
                    visit(current.getChild(i), depth + 1)
                }
            }

            visit(node, 0)
            return result.toList()
        }
    }
}
