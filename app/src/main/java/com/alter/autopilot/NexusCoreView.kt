package com.alter.autopilot

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

class NexusCoreView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        contentDescription = "ALTER AI: нажми для голосовой команды"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val unit = resources.displayMetrics.density

        val base = minOf(width, height) * 0.25f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * unit

        for (i in 0..2) {
            val phase = (pulse + i / 3f) % 1f
            val radius = base + phase * base * 0.9f
            paint.color = if (i == 1) {
                Color.argb(
                    ((1f - phase) * 190).toInt(),
                    145, 100, 255
                )
            } else {
                Color.argb(
                    ((1f - phase) * 190).toInt(),
                    100, 221, 255
                )
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 43, 78)
        canvas.drawCircle(cx, cy, base * 0.78f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * unit
        paint.color = NexusTheme.cyan
        canvas.drawCircle(cx, cy, base * 0.78f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.BOLD
        )
        paint.textSize = base * 0.85f
        canvas.drawText(
            "A",
            cx,
            cy - (paint.ascent() + paint.descent()) / 2f,
            paint
        )
    }
}
