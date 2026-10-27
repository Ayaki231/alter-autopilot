package com.alter.autopilot

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

object NexusTheme {
    val background = Color.rgb(9, 14, 28)
    val surface = Color.rgb(18, 31, 57)
    val surfaceLight = Color.rgb(25, 40, 68)
    val cyan = Color.rgb(100, 221, 255)
    val purple = Color.rgb(145, 100, 255)
    val white = Color.WHITE
    val muted = Color.rgb(150, 170, 205)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(
        context: Context,
        color: Int,
        radius: Int = 18,
        stroke: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            if (stroke != null) {
                setStroke(dp(context, 1), stroke)
            }
        }

    fun styleButton(button: Button, primary: Boolean = false) {
        val context = button.context
        button.isAllCaps = false
        button.textSize = 14f
        button.setTextColor(if (primary) background else white)
        button.background = rounded(
            context,
            if (primary) cyan else surfaceLight,
            16,
            if (primary) null else Color.rgb(45, 67, 105)
        )
        button.setPadding(
            dp(context, 16),
            dp(context, 14),
            dp(context, 16),
            dp(context, 14)
        )
        button.minHeight = dp(context, 52)
    }

    fun styleText(view: TextView, heading: Boolean = false) {
        view.setTextColor(if (heading) white else muted)
        if (heading) view.textSize = 22f
    }

    fun styleCard(view: View) {
        view.background = rounded(
            view.context,
            surface,
            22,
            Color.rgb(40, 64, 104)
        )
        view.elevation = dp(view.context, 3).toFloat()
    }
}
