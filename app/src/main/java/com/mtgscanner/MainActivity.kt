package com.mtgscanner

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.ui.AppNavigator
import com.mtgscanner.ui.AppRoot
import kotlinx.coroutines.launch

/**
 * MainActivity: Entry point for the MTG Scanner application.
 * Initializes all core components (camera, detection, OCR, database) and sets up Compose UI.
 * Handles runtime camera permissions and lifecycle management.
 */
class MainActivity : ComponentActivity() {

    private lateinit var cameraPreviewManager: CameraPreviewManager
    private lateinit var detectionPipeline: DetectionPipeline
    private lateinit var database: ScannedCardDatabase

    /**
     * Runtime camera permission request launcher.
     */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Camera permission granted; initialize camera
            initializeCamera()
        } else {
            // Permission denied; show error or exit
            android.util.Log.e("MainActivity", "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Room database
        database = ScannedCardDatabase.getInstance(this)

        // Initialize core components (CameraPreviewManager, DetectionPipeline)
        cameraPreviewManager = CameraPreviewManager(this)
        detectionPipeline = DetectionPipeline()

        // Request camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            initializeCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Set up Compose UI with Material3 theme
        setContent {
            MaterialTheme {
                Surface {
                    // Create navigator and app root
                    val navigator = remember { AppNavigator() }

                    AppRoot(
                        navigator = navigator,
                        cameraPreviewManager = cameraPreviewManager,
                        detectionPipeline = detectionPipeline,
                        database = database
                    )
                }
            }
        }
    }

    /**
     * Initialize the camera preview and detection pipeline.
     * Called after camera permissions are granted.
     */
    private fun initializeCamera() {
        lifecycleScope.launch {
            // Camera is ready; detection pipeline will receive frames from CameraPreviewManager
            android.util.Log.d("MainActivity", "Camera initialized")
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume camera preview if needed
    }

    override fun onPause() {
        super.onPause()
        // Pause camera preview
        cameraPreviewManager.stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        cameraPreviewManager.stopCamera()
    }
}
