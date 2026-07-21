package com.mtgscanner.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import android.view.Surface
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
import java.util.concurrent.TimeUnit

/**
 * Manages CameraX lifecycle binding, preview rendering, and frame analysis pipeline.
 *
 * Coordinates between the Compose UI ([PreviewView]), frame analysis ([CardFrameAnalyzer]),
 * and Android lifecycle management ([ProcessCameraProvider]).
 *
 * Camera Configuration (P3-02):
 * - Target resolution: 1280×720 (sufficient for 9–12 card detection, 56% less data than 1080p)
 * - Target rotation: Set from display rotation — eliminates per-frame bitmap rotation
 * - Backpressure: KEEP_ONLY_LATEST — drops frames if the analyzer is busy
 *
 * Executor Lifecycle (P3-03):
 * - Old executor is shut down and awaited before creating a new one on re-entry
 * - Guards against [java.util.concurrent.RejectedExecutionException] on navigation cycles
 * - Camera binding failures are always logged
 *
 * @param context Android Context for camera access.
 * @param lifecycleOwner Activity/Fragment lifecycle for camera binding.
 */
class CameraPreviewManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner = context as LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraPreviewManager"

        /** P3-02: Target resolution for ImageAnalysis — 720p is sufficient for card detection. */
        private val TARGET_RESOLUTION = Size(1280, 720)

        /** P3-03: Max time to wait for old executor to drain before creating new one. */
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_MS = 500L
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var frameAnalyzer: CardFrameAnalyzer? = null

    /**
     * Initialize camera and bind preview + frame analysis use cases.
     *
     * Configuration (P3-02):
     * - [ImageAnalysis] target resolution set to 1280×720
     * - [ImageAnalysis] target rotation set from [PreviewView.display] rotation
     *   (eliminates the need for per-frame bitmap rotation in [CardFrameAnalyzer])
     *
     * Executor lifecycle (P3-03):
     * - Shuts down any existing executor before creating a new one
     * - Awaits termination to prevent thread leaks on rapid re-entry
     *
     * @param previewView Target Compose [PreviewView] for camera preview rendering.
     * @param onFrameReady Callback delivering each frame as an RGB [Bitmap].
     */
    fun setupCamera(
        previewView: PreviewView,
        onFrameReady: (frameBitmap: Bitmap) -> Unit
    ) {
        // P3-03: Safely shut down previous executor before creating new one
        recycleExecutor()
        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // P3-02: Determine display rotation for target rotation
                val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

                // ImageAnalysis use case with P3-02 configuration
                frameAnalyzer = CardFrameAnalyzer(onFrameReady)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(TARGET_RESOLUTION)       // P3-02: 1280×720
                    .setTargetRotation(displayRotation)            // P3-02: match display
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, frameAnalyzer!!)
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera bound: resolution=${TARGET_RESOLUTION}, " +
                    "rotation=$displayRotation, backpressure=KEEP_ONLY_LATEST")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera setup failed: ${exc.message}", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Overload accepting an explicit [LifecycleOwner] and optional [CameraSelector].
     *
     * @param lifecycleOwner Lifecycle to bind camera use cases to.
     * @param previewView Target view for preview rendering.
     * @param cameraSelector Which camera to use (default: back camera).
     * @param onFrameReady Callback for processed frames (default: no-op).
     */
    fun setupCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
        onFrameReady: (frameBitmap: Bitmap) -> Unit = {}
    ) {
        recycleExecutor()
        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

                frameAnalyzer = CardFrameAnalyzer(onFrameReady)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(TARGET_RESOLUTION)
                    .setTargetRotation(displayRotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, frameAnalyzer!!)
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera bound (explicit lifecycle): resolution=${TARGET_RESOLUTION}")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera setup failed: ${exc.message}", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Release camera resources and clean up.
     *
     * Unbinds all use cases from [ProcessCameraProvider] and shuts down the executor.
     * Safe to call multiple times — idempotent.
     */
    fun releaseCamera() {
        cameraProvider?.unbindAll()
        recycleExecutor()
        Log.d(TAG, "Camera released")
    }

    /** Alias for [releaseCamera]. */
    fun stopCamera() {
        releaseCamera()
    }

    /**
     * P3-03: Safely shut down the current executor.
     *
     * Initiates orderly shutdown and waits up to [EXECUTOR_SHUTDOWN_TIMEOUT_MS] for
     * any in-flight frame analysis to complete. If it doesn't drain in time, forces
     * shutdown. Sets [analysisExecutor] to null to indicate no executor is active.
     *
     * This prevents:
     * - Thread leaks when CameraScreen leaves/re-enters composition
     * - [java.util.concurrent.RejectedExecutionException] on rapid navigation
     */
    private fun recycleExecutor() {
        analysisExecutor?.let { executor ->
            if (!executor.isShutdown) {
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow()
                        Log.w(TAG, "Executor did not drain in ${EXECUTOR_SHUTDOWN_TIMEOUT_MS}ms, forced shutdown")
                    }
                } catch (e: InterruptedException) {
                    executor.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }
        }
        analysisExecutor = null
    }
}
