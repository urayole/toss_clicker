package com.toss.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast

class AutoClickService : AccessibilityService() {

    private var pressStartTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected.")
        instance = this
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // We don't need to handle accessibility events - only gestures and key events
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // Trigger emergency stop on long-press of volume up or volume down
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    pressStartTime = System.currentTimeMillis()
                } else if (event.repeatCount > 0) {
                    val elapsed = System.currentTimeMillis() - pressStartTime
                    if (elapsed > 1000) { // 1 second long press
                        triggerEmergencyStop()
                        return true // Consume key event
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    fun clickAt(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        // Build simulated gesture (tap duration: 100 milliseconds for reliability)
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(stroke)
        }

        // 1. Minimize overlay window to bypass Android 12+ Untrusted Touch check
        OverlayService.instance?.minimizeOverlay()

        // 2. Wait 150ms for the layout update to apply before injecting touch event
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Simulated click succeeded at ($x, $y)")
                        // 3. Restore overlay to original size and interactive state
                        OverlayService.instance?.restoreOverlay()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Simulated click failed/cancelled")
                        // 3. Restore overlay even if gesture is cancelled
                        OverlayService.instance?.restoreOverlay()
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching gesture: ${e.message}", e)
                OverlayService.instance?.restoreOverlay()
            }
        }, 150)
    }

    private fun triggerEmergencyStop() {
        Log.w(TAG, "EMERGENCY STOP TRIGGERED!")
        
        // Turn off macro execution flag
        ScreenCaptureService.isMacroActive = false
        
        // Stop matching service and overlay service
        val stopCaptureIntent = Intent(this, ScreenCaptureService::class.java)
        stopService(stopCaptureIntent)

        val stopOverlayIntent = Intent(this, OverlayService::class.java)
        stopService(stopOverlayIntent)

        Toast.makeText(this, "Emergency Stop Activated!", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "AutoClickService"
        var instance: AutoClickService? = null
            private set
    }
}
