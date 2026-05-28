package com.toss.clicker

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.IOException

class OpenCVMatcher(private val context: Context) {

    private val templates = ArrayList<Template>()

    // Reusable Mat instances to avoid GC pressure in the real-time frame loop
    private val frameMat = Mat()
    private val frameGray = Mat()
    private val frameCanny = Mat()
    private val matchResult = Mat()

    // Configurable parameters
    var threshold = 0.8f
    var useCanny = false
    var lowCannyThreshold = 50.0
    var highCannyThreshold = 150.0

    // Debugging properties
    @Volatile var lastMatchScore = 0.0
    @Volatile var lastMatchTemplateName = "None"

    // Region of Interest relative values (0.0f - 1.0f)
    // Default: full screen
    private var roiRelativeRect: RectRelative = RectRelative(0.0f, 0.0f, 1.0f, 1.0f)

    data class Template(
        val name: String,
        val mat: Mat,
        val width: Int,
        val height: Int
    )

    data class RectRelative(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    )

    init {
        loadTemplatesFromAssets()
    }

    /**
     * Loads all templates from the assets folder and caches them in memory.
     */
    private fun loadTemplatesFromAssets() {
        try {
            val assetList = context.assets.list("") ?: return
            for (filename in assetList) {
                if (filename.endsWith(".png") || filename.endsWith(".jpg")) {
                    loadTemplate(filename)
                }
            }
            Log.i(TAG, "Successfully loaded ${templates.size} templates from assets.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list assets: ${e.message}", e)
        }
    }

    private fun loadTemplate(filename: String) {
        try {
            context.assets.open(filename).use { inputStream ->
                val bytes = inputStream.readBytes()
                val matOfByte = MatOfByte(*bytes)
                val colorMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
                matOfByte.release()
                
                if (colorMat.empty()) {
                    Log.e(TAG, "Failed to decode template image: $filename")
                    return
                }

                // Convert template to grayscale immediately
                val grayMat = Mat()
                Imgproc.cvtColor(colorMat, grayMat, Imgproc.COLOR_BGR2GRAY)
                colorMat.release()

                // If using Canny pre-processing, we preprocess templates here
                val processedMat = if (useCanny) {
                    val cannyMat = Mat()
                    Imgproc.Canny(grayMat, cannyMat, lowCannyThreshold, highCannyThreshold)
                    grayMat.release()
                    cannyMat
                } else {
                    grayMat
                }

                templates.add(Template(filename, processedMat, processedMat.cols(), processedMat.rows()))
                Log.d(TAG, "Loaded template '$filename' [${processedMat.cols()}x${processedMat.rows()}]")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading template $filename: ${e.message}", e)
        }
    }

    /**
     * Updates the Region of Interest using relative float coordinates (0.0 to 1.0)
     */
    fun setRoi(x: Float, y: Float, w: Float, h: Float) {
        roiRelativeRect = RectRelative(
            x.coerceIn(0.0f, 1.0f),
            y.coerceIn(0.0f, 1.0f),
            w.coerceIn(0.0f, 1.0f),
            h.coerceIn(0.0f, 1.0f)
        )
    }

    /**
     * Matches the templates against the screen capture.
     * Returns the absolute screen Point of the match center, or null if no match is found.
     */
    fun findMatch(screenBitmap: Bitmap): Point? {
        if (templates.isEmpty()) {
            return null
        }

        // Convert android Bitmap to OpenCV Mat (RGBA format)
        Utils.bitmapToMat(screenBitmap, frameMat)

        val screenW = frameMat.cols()
        val screenH = frameMat.rows()

        // Calculate absolute ROI coordinates
        val roiX = (roiRelativeRect.x * screenW).toInt()
        val roiY = (roiRelativeRect.y * screenH).toInt()
        val roiW = (roiRelativeRect.w * screenW).toInt().coerceAtMost(screenW - roiX)
        val roiH = (roiRelativeRect.h * screenH).toInt().coerceAtMost(screenH - roiY)

        if (roiW <= 0 || roiH <= 0) {
            return null
        }

        val roi = Rect(roiX, roiY, roiW, roiH)
        val croppedMat = Mat(frameMat, roi)

        // Preprocess: RGBA to Grayscale
        Imgproc.cvtColor(croppedMat, frameGray, Imgproc.COLOR_RGBA2GRAY)

        val processedFrame = if (useCanny) {
            Imgproc.Canny(frameGray, frameCanny, lowCannyThreshold, highCannyThreshold)
            frameCanny
        } else {
            frameGray
        }

        var matchedPoint: Point? = null
        var bestScore = 0.0
        var bestTemplateName = "None"

        // Loop through all loaded templates
        for (template in templates) {
            // Frame must be larger than or equal to the template
            if (processedFrame.cols() < template.width || processedFrame.rows() < template.height) {
                continue
            }

            // Perform template matching
            Imgproc.matchTemplate(processedFrame, template.mat, matchResult, Imgproc.TM_CCOEFF_NORMED)
            
            val minMax = Core.minMaxLoc(matchResult)
            val score = minMax.maxVal
            if (score > bestScore) {
                bestScore = score
                bestTemplateName = template.name
            }
            
            if (score >= threshold) {
                // Found match! Calculate absolute center coordinate
                val matchLoc = minMax.maxLoc
                val absoluteCenterX = roi.x + matchLoc.x + template.width / 2.0
                val absoluteCenterY = roi.y + matchLoc.y + template.height / 2.0
                
                matchedPoint = Point(absoluteCenterX, absoluteCenterY)
                Log.i(TAG, "MATCHED template '${template.name}' with score $score at absolute ($absoluteCenterX, $absoluteCenterY)")
                break // Break loop on first match to prioritize speed
            }
        }

        lastMatchScore = bestScore
        lastMatchTemplateName = bestTemplateName
        Log.d(TAG, "findMatch - bestScore: $bestScore for template: $bestTemplateName (threshold: $threshold)")

        // Clean up temporary submat header
        croppedMat.release()

        return matchedPoint
    }

    /**
     * Release all allocated memory resources.
     */
    fun release() {
        frameMat.release()
        frameGray.release()
        frameCanny.release()
        matchResult.release()
        for (template in templates) {
            template.mat.release()
        }
        templates.clear()
        Log.i(TAG, "OpenCVMatcher resources released.")
    }

    companion object {
        private const val TAG = "OpenCVMatcher"
    }
}
