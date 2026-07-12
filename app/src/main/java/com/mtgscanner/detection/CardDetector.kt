package com.mtgscanner.detection

import android.graphics.Bitmap
import android.graphics.Point
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Detects card regions in a frame using segmentation and contour finding.
 * Applies thresholding, morphological operations, and contour analysis.
 */
class CardDetector {

    /**
     * Detect card contours in a bitmap.
     * @param bitmap Input frame bitmap.
     * @return List of detected card regions (contours).
     */
    fun detectCards(bitmap: Bitmap): List<CardRegion> {
        // Convert Bitmap to OpenCV Mat
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        
        // Apply Gaussian blur to reduce noise
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
        
        // Apply Otsu thresholding for binary image
        val binary = Mat()
        Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        
        // Apply morphological operations to clean up
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(5.0, 5.0))
        val cleaned = Mat()
        Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_CLOSE, kernel)
        
        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(cleaned, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // Filter contours by area and aspect ratio (cards are roughly rectangular)
        val detectedCards = mutableListOf<CardRegion>()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            // Minimum card area threshold (adjust based on camera distance)
            if (area > 5000) {
                val rect = Imgproc.boundingRect(contour)
                val aspectRatio = rect.width.toFloat() / rect.height.toFloat()
                // Magic cards are roughly 0.7:1 ratio, allow some tolerance
                if (aspectRatio in 0.6f..0.85f) {
                    detectedCards.add(
                        CardRegion(
                            x = rect.x,
                            y = rect.y,
                            width = rect.width,
                            height = rect.height,
                            contour = contour,
                            area = area.toInt()
                        )
                    )
                }
            }
        }
        
        // Clean up OpenCV resources
        mat.release()
        gray.release()
        blurred.release()
        binary.release()
        cleaned.release()
        kernel.release()
        hierarchy.release()
        
        return detectedCards
    }

    /**
     * Extract region of interest (card image) from bitmap.
     * @param bitmap Source frame.
     * @param region Card region to extract.
     * @return Cropped card bitmap.
     */
    fun extractCardImage(bitmap: Bitmap, region: CardRegion): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            region.x,
            region.y,
            region.width,
            region.height
        )
    }

    /**
     * Perform perspective correction on a card region.
     * @param cardBitmap Card image to correct.
     * @param contour Card contour with corner points.
     * @return Perspective-corrected card bitmap.
     */
    fun perspectiveCorrect(cardBitmap: Bitmap, contour: MatOfPoint): Bitmap {
        // TODO: Implement perspective correction using homography
        // For now, return original bitmap
        return cardBitmap
    }
}

/**
 * Represents a detected card region in a frame.
 */
data class CardRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val contour: MatOfPoint,
    val area: Int,
    var trackingId: Int = -1
)
