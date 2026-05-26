package com.toss.clicker

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class TossClickerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV successfully loaded from Maven Dependency!")
        } else {
            Log.e(TAG, "Failed to load OpenCV library.")
        }
    }

    companion object {
        private const val TAG = "TossClickerApp"
    }
}
