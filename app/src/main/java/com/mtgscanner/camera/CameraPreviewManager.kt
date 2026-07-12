package com.mtgscanner.camera

import android.content.Context
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
 * Manages CameraX lifecycle, preview setup, and frame analysis pipeline.
 * Handles camera permissions, binding use cases, and frame delivery to analyzer.
 */
class CameraPreviewManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameAnalyzer: CardFrameAnalyzer? = null

    /**
     * Initialize camera, bind preview and analyzer use cases.
     * @param previewView Target view for camera preview.
     * @param onFrameAnalyzed Callback when frame analysis completes.
     */
    fun setupCamera(
        previewView: PreviewView,
        onFrameAnalyzed: (analysisResult: String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.result
            
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

    /**
     * Release camera resources.
     */
    fun releaseCamera() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }
}
