package com.alter.autopilot

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingAlterService : Service() {
    companion object {
        var active = false
            private set
    }

    private var wm: WindowManager? = null
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            val manager = getSystemService(WINDOW_SERVICE) as WindowManager
            val density = resources.displayMetrics.density
            val size = (56 * density).toInt()

            val params = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 20
                y = 250
            }

            val backgroundShape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(48, 60, 220))
            }

            val view = TextView(this).apply {
                text = "A"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = backgroundShape
            }

            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY

                        if (kotlin.math.abs(dx) > 12 ||
                            kotlin.math.abs(dy) > 12) moved = true

                        if (moved) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            manager.updateViewLayout(view, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            startActivity(
                                Intent(this, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                        true
                    }
                    else -> false
                }
            }

            manager.addView(view, params)
            wm = manager
            bubble = view
            active = true
        } catch (e: Exception) {
            android.util.Log.e("ALTER", "Overlay error", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        bubble?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {}
        }
        active = false
        bubble = null
        super.onDestroy()
    }
}
