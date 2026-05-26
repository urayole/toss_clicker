package com.toss.clicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.toss.clicker.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            
            // Start the overlay control interface
            startService(Intent(this, OverlayService::class.java))
            
            Toast.makeText(this, "Clicker Service Started", Toast.LENGTH_SHORT).show()
            updateUIState()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun setupListeners() {
        binding.btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.btnStartCapture.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AutoClickService.instance == null) {
                Toast.makeText(this, "Please enable accessibility service first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Launch screen capture permission request
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        }

        binding.btnStopCapture.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, OverlayService::class.java))
            updateUIState()
            Toast.makeText(this, "Clicker Service Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIState() {
        // Check OpenCV Loader
        val isOpenCVLoaded = OpenCVLoader.initDebug()
        binding.tvOpenCVStatus.text = "OpenCV: " + if (isOpenCVLoaded) "Loaded" else "Not Loaded"
        binding.tvOpenCVStatus.setTextColor(ContextCompat.getColor(this, if (isOpenCVLoaded) android.R.color.holo_green_light else android.R.color.holo_red_light))

        // Check Accessibility
        val isAccessibilityEnabled = AutoClickService.instance != null
        binding.tvAccessibilityStatus.text = "Accessibility: " + if (isAccessibilityEnabled) "Active" else "Disabled"
        binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, if (isAccessibilityEnabled) android.R.color.holo_green_light else android.R.color.holo_red_light))
        binding.btnAccessibility.visibility = if (isAccessibilityEnabled) View.GONE else View.VISIBLE

        // Check Overlay
        val isOverlayEnabled = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = "Overlay: " + if (isOverlayEnabled) "Granted" else "Denied"
        binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, if (isOverlayEnabled) android.R.color.holo_green_light else android.R.color.holo_red_light))
        binding.btnOverlay.visibility = if (isOverlayEnabled) View.GONE else View.VISIBLE

        // Control buttons state
        val isServiceRunning = ScreenCaptureService.isRunning
        if (isServiceRunning) {
            binding.btnStartCapture.visibility = View.GONE
            binding.btnStopCapture.visibility = View.VISIBLE
        } else {
            binding.btnStartCapture.visibility = View.VISIBLE
            binding.btnStopCapture.visibility = View.GONE
        }
    }
}
