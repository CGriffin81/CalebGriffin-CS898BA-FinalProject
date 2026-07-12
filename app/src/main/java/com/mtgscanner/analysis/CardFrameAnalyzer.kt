package com.mtgscanner.analysis

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * Processes camera frames for card detection and analysis.
 * Receives frames from CameraX ImageAnalysis and prepares them for downstream detection pipeline.
 */
class CardFrameAnalyzer(
    private val onFrameAnalyzed: (analysisResult: String) -> Unit
) : ImageAnalysis.Analyzer {

    /**
     * Called for each camera frame. Converts image to bitmap, extracts metadata, and queues analysis.
     * @param image Current camera frame.
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
     * Convert ImageProxy (YUV) to Bitmap.
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
     * Rotate bitmap by specified degrees.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
