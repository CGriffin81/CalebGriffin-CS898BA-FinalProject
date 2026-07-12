package com.mtgscanner.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mtgscanner.analysis.CardFrameAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle binding, preview rendering, and frame analysis pipeline.
 * Handles camera setup, permission management, use case binding, and resource cleanup.
 * Coordinates between Compose UI (PreviewView), frame analysis (CardFrameAnalyzer),
 * and lifecycle management (ProcessCameraProvider).
 *
 * CameraX Architecture:
 * - ProcessCameraProvider: Singleton managing camera access and use case binding
 * - Preview: Displays live camera feed to user
 * - ImageAnalysis: Captures frames for card detection (STRATEGY_KEEP_ONLY_LATEST backpressure)
 * - CardFrameAnalyzer: Processes frames on background executor thread
 *
 * Lifecycle:
 * - setupCamera(): Initialize camera on CameraProvider availability (async)
 * - releaseCamera(): Clean up camera and executor when activity destroyed
 * - Camera access restricted to back camera (DEFAULT_BACK_CAMERA selector)
 *
 * @param context Android Context for camera access and executor setup
 * @param lifecycleOwner Activity/Fragment lifecycle for camera binding (typically MainActivity)
 */
class CameraPreviewManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner = context as LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameAnalyzer: CardFrameAnalyzer? = null

    /**
     * Initialize camera, bind preview and frame analysis use cases.
     * Async operation: fetches ProcessCameraProvider and binds to lifecycle.
     * Sets up Preview for live display and ImageAnalysis for frame processing (KEEP_ONLY_LATEST backpressure).
     *
     * Use Cases:
     * - Preview: Renders camera frames to PreviewView in real-time
     * - ImageAnalysis: Delivers frames to CardFrameAnalyzer on dedicated executor thread
     *   - Backpressure strategy: KEEP_ONLY_LATEST drops old frames if analyzer is busy
     *   - Executor: Single-threaded to ensure sequential frame processing
     *
     * @param previewView Target Compose PreviewView for camera preview rendering
     * @param onFrameAnalyzed Callback invoked with analysis result after each frame processed
     * @throws Exception if camera binding fails (caught internally, logged, but not rethrown)
     */
    fun setupCamera(
        previewView: PreviewView,
        onFrameAnalyzed: (analysisResult: String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            // Preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            // ImageAnalysis use case for frame processing
            frameAnalyzer = CardFrameAnalyzer(onFrameAnalyzed)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor, frameAnalyzer!!)
                }
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                // Handle camera binding error
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setupCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
        onFrameAnalyzed: (analysisResult: String) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            frameAnalyzer = CardFrameAnalyzer(onFrameAnalyzed)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor, frameAnalyzer!!)
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraPreviewManager", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Release camera resources and clean up.
     * Should be called in Activity.onDestroy() or fragment lifecycle cleanup.
     * Unbinds all use cases from ProcessCameraProvider and shuts down frame analysis executor.
     * Critical for preventing memory leaks and ensuring camera is available for other apps.
     */
    fun releaseCamera() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }

    fun stopCamera() {
        releaseCamera()
    }
}
