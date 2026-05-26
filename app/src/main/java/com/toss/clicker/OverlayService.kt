package com.toss.clicker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import androidx.core.content.ContextCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var btnToggleMacro: Button
    private lateinit var btnCloseOverlay: ImageButton
    private lateinit var vDragHandle: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null)

        btnToggleMacro = overlayView.findViewById(R.id.btnToggleMacro)
        btnCloseOverlay = overlayView.findViewById(R.id.btnCloseOverlay)
        vDragHandle = overlayView.findViewById(R.id.vDragHandle)

        setupOverlay()
        setupListeners()
    }

    private fun setupOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager.addView(overlayView, params)

        // Dragging mechanism
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        vDragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupListeners() {
        btnToggleMacro.setOnClickListener {
            val nextState = !ScreenCaptureService.isMacroActive
            ScreenCaptureService.isMacroActive = nextState
            updateButtonState(nextState)
        }

        btnCloseOverlay.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopSelf()
        }
    }

    private fun updateButtonState(isActive: Boolean) {
        val colorRes = if (isActive) android.R.color.holo_green_light else android.R.color.holo_red_light
        val text = if (isActive) "MACRO ON" else "MACRO OFF"
        
        btnToggleMacro.text = text
        btnToggleMacro.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, colorRes)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        ScreenCaptureService.isMacroActive = false
    }
}
