package com.toss.clicker

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var matcher: OpenCVMatcher
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private var lastProcessedTime = 0L
    private val frameIntervalMs = 100L // Throttling: 10 FPS to save CPU and reduce heat
    private var frameCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        matcher = OpenCVMatcher(this)
        
        // Define ROI to focus on screen area where templates are expected (e.g. bottom-middle area)
        // Adjust relative values (x, y, w, h) according to target application
        matcher.setRoi(0.0f, 0.4f, 1.0f, 0.6f) 
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                startCapture(resultCode, resultData)
            } else {
                Log.e(TAG, "Invalid result code or data, stopping service.")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        try {
            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val screenDensity = metrics.densityDpi

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to obtain MediaProjection.")
                stopSelf()
                return
            }

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "TossClickerDisplay",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
            backgroundHandler = Handler(handlerThread!!.looper)

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    if (isMacroActive) {
                        val now = System.currentTimeMillis()
                        if ((now - lastProcessedTime) >= frameIntervalMs) {
                            lastProcessedTime = now
                            processImage(image)
                        } else {
                            image.close()
                        }
                    } else {
                        image.close()
                    }
                }
            }, backgroundHandler)

            Log.i(TAG, "Screen capture loop successfully started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}", e)
            stopSelf()
        }
    }

    private fun processImage(image: Image) {
        try {
            val width = image.width
            val height = image.height
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            // Copy frame data into Bitmap
            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close() // Close Image as quickly as possible

            // Crop out stride padding if present
            val cleanBitmap = if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } else {
                bitmap
            }

            frameCount++
            // Execute template matching
            val matchedPoint = matcher.findMatch(cleanBitmap)

            val scoreText = String.format(java.util.Locale.US, "%.3f", matcher.lastMatchScore)
            val debugText = "Frames: $frameCount\nScore: $scoreText (${matcher.lastMatchTemplateName})"
            OverlayService.instance?.updateDebugInfo(debugText)

            if (matchedPoint != null) {
                // Dispatch accessibility touch action
                AutoClickService.instance?.clickAt(
                    matchedPoint.x.toFloat(),
                    matchedPoint.y.toFloat()
                )
            }

            // Cleanup Bitmap memory
            if (cleanBitmap != bitmap) {
                cleanBitmap.recycle()
            }
            bitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing screen frame: ${e.message}", e)
            image.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Toss Clicker Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground Service for Screen Capture Monitoring"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Toss Clicker Running")
            .setContentText("Screen capture overlay active.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isMacroActive = false
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        handlerThread?.quitSafely()
        handlerThread = null
        
        matcher.release()
        Log.i(TAG, "Screen capture service destroyed.")
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "toss_clicker_service_channel"
        private const val NOTIFICATION_ID = 8888
        
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isMacroActive = false
    }
}
