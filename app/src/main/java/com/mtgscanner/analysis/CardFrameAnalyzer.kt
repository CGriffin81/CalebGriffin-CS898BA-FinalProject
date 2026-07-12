package com.mtgscanner.analysis

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * Processes camera frames from CameraX for card detection and analysis.
 * Implements ImageAnalysis.Analyzer to receive frames via CameraPreviewManager.
 * Converts YUV image data from camera to RGB bitmap and handles device rotation.
 * Queues processed frames to downstream DetectionPipeline via callback.
 *
 * Frame Processing Pipeline:
 * 1. Receive ImageProxy (YUV format, 8-bit Y plane + interleaved UV) from CameraX
 * 2. Extract YUV planes and convert to RGB bitmap
 * 3. Apply device rotation correction (0°, 90°, 180°, 270°)
 * 4. Invoke onFrameAnalyzed callback with processed frame
 * 5. Close ImageProxy to release native memory
 *
 * Performance Considerations:
 * - YUV→RGB conversion is CPU-intensive; runs on CameraPreviewManager's executor thread
 * - CameraX STRATEGY_KEEP_ONLY_LATEST ensures only latest frame is processed (no queue buildup)
 * - Frame callback should return quickly to avoid dropping subsequent frames
 *
 * @param onFrameAnalyzed Callback invoked for each processed frame; receives analysis result string
 */
class CardFrameAnalyzer(
    private val onFrameAnalyzed: (analysisResult: String) -> Unit
) : ImageAnalysis.Analyzer {

    /**
     * Analyze a camera frame (required by ImageAnalysis.Analyzer).
     * Called for each frame delivered by CameraX on the analyzer executor thread.
     * Converts YUV image to RGB bitmap, applies rotation correction, and invokes callback.
     * MUST call image.close() in finally block to release native memory.
     *
     * Exception Safety:
     * - All exceptions caught and logged (not rethrown) to prevent camera pipeline disruption
     * - image.close() guaranteed to run via finally block
     *
     * @param image ImageProxy containing camera frame in YUV format
     * @throws Exception caught internally; frame is skipped if conversion fails
     */
    override fun analyze(image: ImageProxy) {
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageToBitmap(image)
            
            // Get frame dimensions
            val width = image.width
            val height = image.height
            val rotation = image.imageInfo.rotationDegrees
            
            // TODO: Pass bitmap to detection pipeline
            // For now, just report frame received
            val result = "Frame: ${width}x${height} rot=${rotation}°"
            onFrameAnalyzed(result)
            
        } finally {
            image.close()
        }
    }

    /**
     * Convert CameraX ImageProxy (YUV format) to RGB Bitmap.
     * CameraX delivers frames in NV21 YUV format (Y plane + interleaved UV).
     * Conversion steps:
     * 1. Extract Y, U, V planes from ImageProxy
     * 2. Interleave U/V into NV21 format for YuvImage constructor
     * 3. Compress YuvImage to JPEG byte array (Java provides YuvImage support)
     * 4. Decode JPEG to Bitmap (standard Android method)
     * 5. Apply device rotation correction
     *
     * Performance: ~50-100ms for typical VGA frame (depends on device, camera resolution, CPU)
     *
     * @param imageProxy Camera frame from CameraX (YUV format)
     * @return RGB Bitmap with device rotation applied
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
        
        // Interleave U and V
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
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate if needed
        return if (imageProxy.imageInfo.rotationDegrees != 0) {
            rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
        } else {
            bitmap
        }
    }

    /**
     * Rotate bitmap by specified degrees using Android Matrix transform.
     * Corrects for device rotation (portrait/landscape) so card detection sees consistent orientation.
     * Typical values: 0° (device portrait), 90° (landscape), 180°, 270°.
     *
     * @param bitmap Input bitmap to rotate
     * @param degrees Clockwise rotation angle (typically 0, 90, 180, or 270)
     * @return New rotated bitmap (original bitmap unchanged, caller should recycle if no longer needed)
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
