package com.toss.clicker

import android.util.Log
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
    private lateinit var tvDebugInfo: android.widget.TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null)

        btnToggleMacro = overlayView.findViewById(R.id.btnToggleMacro)
        btnCloseOverlay = overlayView.findViewById(R.id.btnCloseOverlay)
        vDragHandle = overlayView.findViewById(R.id.vDragHandle)
        tvDebugInfo = overlayView.findViewById(R.id.tvDebugInfo)

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
        if (!isActive && ::tvDebugInfo.isInitialized) {
            tvDebugInfo.text = "Macro Stopped"
        }
    }

    fun updateDebugInfo(info: String) {
        if (::tvDebugInfo.isInitialized) {
            tvDebugInfo.post {
                tvDebugInfo.text = info
            }
        }
    }

    private var originalWidth = WindowManager.LayoutParams.WRAP_CONTENT
    private var originalHeight = WindowManager.LayoutParams.WRAP_CONTENT
    private var originalFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    fun minimizeOverlay() {
        if (!::overlayView.isInitialized) return
        overlayView.post {
            try {
                val params = overlayView.layoutParams as WindowManager.LayoutParams
                originalWidth = params.width
                originalHeight = params.height
                originalFlags = params.flags

                // Change to 1x1 invisible dot and add FLAG_NOT_TOUCHABLE to bypass untrusted touch check
                params.width = 1
                params.height = 1
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(overlayView, params)
                Log.d("OverlayService", "Overlay minimized to bypass security touch filter.")
            } catch (e: Exception) {
                Log.e("OverlayService", "Error minimizing overlay: ${e.message}")
            }
        }
    }

    fun restoreOverlay() {
        if (!::overlayView.isInitialized) return
        overlayView.post {
            try {
                val params = overlayView.layoutParams as WindowManager.LayoutParams
                params.width = originalWidth
                params.height = originalHeight
                params.flags = originalFlags
                windowManager.updateViewLayout(overlayView, params)
                Log.d("OverlayService", "Overlay layout restored to original state.")
            } catch (e: Exception) {
                Log.e("OverlayService", "Error restoring overlay: ${e.message}")
            }
        }
    }

    private var targetGuideView: View? = null
    private var targetGuideParams: WindowManager.LayoutParams? = null
    private var isTargetGuideShowing = false

    fun showTargetGuide(x: Int, y: Int, w: Int, h: Int) {
        if (!::overlayView.isInitialized) return
        overlayView.post {
            try {
                if (targetGuideView == null) {
                    targetGuideView = LayoutInflater.from(this).inflate(R.layout.layout_target_guide, null)
                    targetGuideParams = WindowManager.LayoutParams(
                        w,
                        h,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        this.x = x
                        this.y = y
                    }
                    windowManager.addView(targetGuideView, targetGuideParams)
                    isTargetGuideShowing = true
                    Log.d("OverlayService", "Target guide overlay added at ($x, $y) size: ${w}x${h}")

                    // Start breathing glow pulsing animation
                    val vRing = targetGuideView?.findViewById<View>(R.id.vTargetRing)
                    vRing?.let { view ->
                        val animator = android.animation.ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.2f).apply {
                            duration = 300
                            repeatMode = android.animation.ValueAnimator.REVERSE
                            repeatCount = android.animation.ValueAnimator.INFINITE
                            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        }
                        animator.start()
                        view.tag = animator // Store animator to stop it on release
                    }
                } else {
                    // Update layout parameters smoothly on movement
                    targetGuideParams?.let { params ->
                        if (params.x != x || params.y != y || params.width != w || params.height != h) {
                            params.x = x
                            params.y = y
                            params.width = w
                            params.height = h
                            windowManager.updateViewLayout(targetGuideView, params)
                            Log.d("OverlayService", "Target guide moved to ($x, $y)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OverlayService", "Error showing target guide: ${e.message}", e)
            }
        }
    }

    fun hideTargetGuide() {
        if (!::overlayView.isInitialized) return
        overlayView.post {
            try {
                targetGuideView?.let { view ->
                    val vRing = view.findViewById<View>(R.id.vTargetRing)
                    val animator = vRing?.tag as? android.animation.ObjectAnimator
                    animator?.cancel()
                    windowManager.removeView(view)
                    targetGuideView = null
                    targetGuideParams = null
                    isTargetGuideShowing = false
                    Log.d("OverlayService", "Target guide overlay removed.")
                }
            } catch (e: Exception) {
                Log.e("OverlayService", "Error hiding target guide: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        targetGuideView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        ScreenCaptureService.isMacroActive = false
    }

    companion object {
        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
