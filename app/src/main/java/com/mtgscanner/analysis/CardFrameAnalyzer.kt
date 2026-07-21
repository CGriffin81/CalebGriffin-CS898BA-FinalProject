package com.mtgscanner.analysis

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Processes camera frames from CameraX and delivers RGB bitmaps to the detection pipeline.
 *
 * Implements [ImageAnalysis.Analyzer] to receive frames via [com.mtgscanner.camera.CameraPreviewManager].
 * Converts YUV image data from the camera sensor to an RGB [Bitmap], applies device rotation
 * correction, and passes the result to the downstream consumer via [onFrameReady].
 *
 * Frame Processing:
 * 1. Receive [ImageProxy] (YUV_420_888 format) from CameraX on the analysis executor thread
 * 2. Convert to RGB Bitmap via [imageToBitmap]
 * 3. Apply device rotation correction if sensor reports non-zero rotation
 * 4. Invoke [onFrameReady] callback with the final Bitmap
 * 5. Close [ImageProxy] in `finally` block to release native memory
 *
 * Threading: All processing runs on [CameraPreviewManager]'s single-threaded executor.
 * The callback [onFrameReady] is invoked on that same background thread — the consumer
 * (typically [com.mtgscanner.detection.DetectionPipeline.processFrame]) must be thread-safe.
 *
 * @param onFrameReady Callback delivering each processed frame as an RGB [Bitmap].
 *   Called on the analysis executor thread. Must return quickly to avoid frame drops.
 */
class CardFrameAnalyzer(
    private val onFrameReady: (frameBitmap: Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "CardFrameAnalyzer"
    }

    /**
     * Analyze a camera frame (required by [ImageAnalysis.Analyzer]).
     *
     * Called for each frame delivered by CameraX on the analysis executor thread.
     * Converts the YUV image to an RGB Bitmap, applies rotation correction, and
     * delivers it to [onFrameReady]. Always closes the [ImageProxy] in the `finally` block.
     *
     * Exception Safety: Any exception during conversion is caught and logged.
     * The frame is silently dropped — this prevents a single bad frame from crashing
     * the entire camera pipeline.
     *
     * @param image [ImageProxy] containing the camera frame in YUV_420_888 format.
     */
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = imageToBitmap(image)
            val rotation = image.imageInfo.rotationDegrees

            val outputBitmap = if (rotation != 0) {
                rotateBitmap(bitmap, rotation.toFloat())
            } else {
                bitmap
            }

            onFrameReady(outputBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Frame conversion failed, skipping frame: ${e.message}")
        } finally {
            image.close()
        }
    }

    /**
     * Convert CameraX [ImageProxy] (YUV_420_888) to an RGB [Bitmap].
     *
     * Current implementation uses the YUV→JPEG→Bitmap round-trip approach.
     * This is functional but slower than optimal — P3-01 will replace this with
     * [ImageProxy.toBitmap()] from CameraX 1.3+ for correct rowStride handling
     * and ~5x faster conversion.
     *
     * @param imageProxy Camera frame from CameraX.
     * @return RGB Bitmap (not yet rotation-corrected).
     */
    private fun imageToBitmap(imageProxy: ImageProxy): Bitmap {
        val planes = imageProxy.planes
        val ySize = planes[0].buffer.remaining()
        val u = ByteArray(planes[1].buffer.remaining())
        val v = ByteArray(planes[2].buffer.remaining())

        planes[1].buffer.get(u)
        planes[2].buffer.get(v)

        val nv21 = ByteArray(ySize + u.size + v.size)
        planes[0].buffer.get(nv21, 0, ySize)

        // Interleave U and V into NV21 layout
        for (i in u.indices) {
            nv21[ySize + 2 * i + 1] = u[i]
            nv21[ySize + 2 * i] = v[i]
        }

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Rotate a bitmap by [degrees] clockwise.
     *
     * Corrects for the device sensor orientation so the detection pipeline always
     * receives an upright image regardless of how the phone is held.
     *
     * @param bitmap Input bitmap to rotate.
     * @param degrees Clockwise rotation angle (typically 0, 90, 180, or 270).
     * @return New rotated bitmap. The original bitmap is not recycled.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
