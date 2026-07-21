package com.mtgscanner.analysis

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Processes camera frames from CameraX and delivers RGB bitmaps to the detection pipeline.
 *
 * Implements [ImageAnalysis.Analyzer] to receive frames on the analysis executor thread.
 * Uses [ImageProxy.toBitmap] (CameraX 1.3+) for correct and efficient YUV→RGB conversion
 * that handles rowStride and pixelStride correctly on all devices.
 *
 * Frame Processing (P3-01):
 * 1. Receive [ImageProxy] from CameraX on the analysis executor thread
 * 2. Convert to RGB Bitmap via [ImageProxy.toBitmap] (native, ~5–15ms)
 * 3. Deliver bitmap to [onFrameReady] callback
 * 4. Close [ImageProxy] in `finally` block
 *
 * Rotation handling (P3-02):
 * When [CameraPreviewManager] sets [ImageAnalysis.setTargetRotation], CameraX accounts
 * for the rotation internally and [ImageProxy.imageInfo.rotationDegrees] reports 0.
 * No per-frame bitmap rotation is needed. If for any reason rotationDegrees is non-zero,
 * the bitmap is still delivered as-is — the detection pipeline handles upright images.
 *
 * Threading: Runs on [CameraPreviewManager]'s single-threaded executor. The callback
 * [onFrameReady] is invoked on that same thread — the consumer must be thread-safe.
 *
 * @param onFrameReady Callback delivering each frame as an RGB [Bitmap].
 */
class CardFrameAnalyzer(
    private val onFrameReady: (frameBitmap: Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "CardFrameAnalyzer"
        private const val LOG_INTERVAL_FRAMES = 100  // Log timing every N frames
    }

    private var frameCount = 0L
    private var totalConversionTimeNs = 0L

    /**
     * Analyze a camera frame.
     *
     * Converts [ImageProxy] to [Bitmap] using the CameraX built-in [ImageProxy.toBitmap]
     * which correctly handles YUV_420_888 row stride padding on all devices (P3-01).
     * No manual YUV→JPEG→Bitmap round-trip — approximately 5× faster and artifact-free.
     *
     * Always closes [image] in the `finally` block to release native camera memory.
     *
     * @param image [ImageProxy] containing the camera frame.
     */
    override fun analyze(image: ImageProxy) {
        try {
            val startNs = SystemClock.elapsedRealtimeNanos()

            // P3-01: Use CameraX's built-in toBitmap() — correct rowStride handling,
            // ~5–15ms vs. 60–100ms for the old JPEG round-trip, no compression artifacts.
            val bitmap = image.toBitmap()

            val elapsedNs = SystemClock.elapsedRealtimeNanos() - startNs
            totalConversionTimeNs += elapsedNs
            frameCount++

            // Log average conversion time periodically for performance monitoring
            if (frameCount % LOG_INTERVAL_FRAMES == 0L) {
                val avgMs = (totalConversionTimeNs / frameCount) / 1_000_000.0
                Log.d(TAG, "Frame ${frameCount}: avg conversion=${String.format("%.1f", avgMs)}ms " +
                    "${bitmap.width}x${bitmap.height}")
            }

            onFrameReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Frame conversion failed, skipping: ${e.message}")
        } finally {
            image.close()
        }
    }
}
