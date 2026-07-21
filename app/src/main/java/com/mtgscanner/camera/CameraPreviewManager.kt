package com.mtgscanner.camera

import android.content.Context
import android.graphics.Bitmap
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
 *
 * Coordinates between the Compose UI ([PreviewView]), frame analysis ([CardFrameAnalyzer]),
 * and Android lifecycle management ([ProcessCameraProvider]).
 *
 * Architecture:
 * - [ProcessCameraProvider]: Singleton managing camera access and use-case binding
 * - [Preview]: Renders the live camera feed to the user
 * - [ImageAnalysis]: Captures frames for card detection (STRATEGY_KEEP_ONLY_LATEST)
 * - [CardFrameAnalyzer]: Converts YUV frames to RGB Bitmaps on a dedicated background thread
 *
 * Frame Delivery:
 * Each processed frame is delivered as a [Bitmap] to the [onFrameReady] callback.
 * The callback runs on the analysis executor thread — the consumer
 * (typically [com.mtgscanner.detection.DetectionPipeline.processFrame]) must be thread-safe.
 *
 * Lifecycle:
 * - [setupCamera]: Binds camera on [ProcessCameraProvider] availability (async)
 * - [releaseCamera] / [stopCamera]: Unbinds all use cases and shuts down the executor
 *
 * @param context Android Context for camera access and executor setup.
 * @param lifecycleOwner Activity/Fragment lifecycle for camera binding (typically MainActivity).
 */
class CameraPreviewManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner = context as LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraPreviewManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameAnalyzer: CardFrameAnalyzer? = null

    /**
     * Initialize camera and bind preview + frame analysis use cases.
     *
     * This is an async operation: it requests the [ProcessCameraProvider] singleton and
     * binds use cases once available. The preview is rendered to [previewView] and each
     * analyzed frame is delivered as a [Bitmap] to [onFrameReady].
     *
     * Use Cases:
     * - **Preview**: Renders camera frames to [PreviewView] in real-time
     * - **ImageAnalysis**: Delivers frames to [CardFrameAnalyzer] on a dedicated executor
     *   - Backpressure: KEEP_ONLY_LATEST — drops old frames if analyzer is busy
     *   - Executor: Single-threaded for sequential frame processing
     *
     * @param previewView Target Compose [PreviewView] for camera preview rendering.
     * @param onFrameReady Callback delivering each processed frame as an RGB [Bitmap].
     *   Called on the analysis executor thread. Connect this to
     *   [com.mtgscanner.detection.DetectionPipeline.processFrame] to activate the pipeline.
     */
    fun setupCamera(
        previewView: PreviewView,
        onFrameReady: (frameBitmap: Bitmap) -> Unit
    ) {
        // Shut down previous executor if camera is being re-initialized (prevents thread leak)
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdown()
        }
        analysisExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview use case — renders live camera feed
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageAnalysis use case — delivers frames to CardFrameAnalyzer
            frameAnalyzer = CardFrameAnalyzer(onFrameReady)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor, frameAnalyzer!!)
                }

            // Always use back camera for card scanning
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "Camera bound successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
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
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdown()
        }
        analysisExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            frameAnalyzer = CardFrameAnalyzer(onFrameReady)
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
                Log.d(TAG, "Camera bound successfully (explicit lifecycle)")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Release camera resources and clean up.
     *
     * Unbinds all use cases from [ProcessCameraProvider] and shuts down the frame
     * analysis executor. Must be called in Activity.onDestroy() or equivalent cleanup point
     * to prevent memory leaks and ensure the camera is available for other apps.
     */
    fun releaseCamera() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }

    /**
     * Alias for [releaseCamera]. Stops camera and releases resources.
     */
    fun stopCamera() {
        releaseCamera()
    }
}
