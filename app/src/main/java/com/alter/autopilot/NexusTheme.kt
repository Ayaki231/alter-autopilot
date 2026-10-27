package com.alter.autopilot

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object NexusTheme {
    val background = Color.rgb(9, 14, 28)       // Глубокий космический киберпанк
    val surface = Color.rgb(16, 26, 48)         // Карточка с легким свечением
    val surfaceLight = Color.rgb(24, 38, 66)    // Поля ввода
    val cyan = Color.rgb(0, 229, 255)           // Неоновый акцент
    val purple = Color.rgb(168, 85, 247)        // Фиолетовый градиент
    val white = Color.WHITE
    val muted = Color.rgb(148, 163, 184)
    val success = Color.rgb(34, 197, 94)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(
        context: Context,
        color: Int,
        radius: Int = 18,
        strokeColor: Int? = null,
        strokeWidth: Int = 1
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            if (strokeColor != null) {
                setStroke(dp(context, strokeWidth), strokeColor)
            }
        }

    fun styleCard(view: View) {
        val ctx = view.context
        view.background = rounded(ctx, surface, 20, Color.rgb(35, 55, 95), 1)
        if (view is LinearLayout) {
            view.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 18))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 16)
            }
            view.layoutParams = lp
        }
    }

    fun styleButton(button: Button, isPrimary: Boolean = false, isDanger: Boolean = false) {
        val ctx = button.context
        button.isAllCaps = false
        button.textSize = 14f
        button.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

        val bgColor = when {
            isDanger -> Color.rgb(225, 29, 72)
            isPrimary -> cyan
            else -> surfaceLight
        }
        val textColor = when {
            isPrimary -> Color.rgb(9, 14, 28)
            else -> white
        }
        button.setTextColor(textColor)
        button.background = rounded(ctx, bgColor, 14, if (isPrimary || isDanger) null else Color.rgb(45, 75, 120))
        button.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        button.minHeight = dp(ctx, 50)

        val lp = (button.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(ctx, 10)
        button.layoutParams = lp
    }

    fun styleInput(editText: EditText) {
        val ctx = editText.context
        editText.setTextColor(white)
        editText.setHintTextColor(muted)
        editText.textSize = 14f
        editText.background = rounded(ctx, surfaceLight, 14, Color.rgb(40, 65, 105))
        editText.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(ctx, 12)
        }
        editText.layoutParams = lp
    }

    fun createHeader(context: Context, text: String, icon: String = ""): TextView {
        return TextView(context).apply {
            val fullText = if (icon.isNotBlank()) "$icon  $text" else text
            this.text = fullText
            setTextColor(cyan)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 12)
            }
            layoutParams = lp
        }
    }
}
