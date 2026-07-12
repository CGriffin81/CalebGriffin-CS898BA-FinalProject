package com.mtgscanner.detection

import android.graphics.Bitmap
import android.graphics.Point
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Detects card regions in camera frames using OpenCV-based segmentation and contour analysis.
 * Uses Gaussian blur, Otsu thresholding, and morphological operations to isolate card shapes.
 * Filters detected contours by area and aspect ratio (Magic cards are ~0.7:1) to eliminate noise.
 */
class CardDetector {

    /**
     * Detect all card regions in a bitmap frame.
     * Processes frame through: grayscale conversion → Gaussian blur → Otsu binary thresholding →
     * morphological close operation → contour extraction → filtering by area and aspect ratio.
     * Minimum card area is 5000 pixels; aspect ratio must be between 0.6 and 0.85 (Magic card proportions).
     *
     * @param bitmap Input frame bitmap from camera preview, expected RGB or BGR format
     * @return List of CardRegion objects representing detected card locations; empty if no cards found
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
     * Extract a card image region from the source frame bitmap.
     * Creates a cropped bitmap using the coordinates and dimensions specified in the CardRegion.
     * The extracted region is ready for OCR processing.
     *
     * @param bitmap Source frame bitmap to crop from
     * @param region CardRegion object containing x, y, width, and height of the card
     * @return New cropped Bitmap containing only the card region
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
     * Perform perspective correction on a card region to normalize the viewing angle.
     * Extracts corner points from the contour and applies homography transformation.
     * Currently returns the original bitmap; full implementation via perspective matrix transformation pending.
     *
     * @param cardBitmap Card image to apply perspective correction to
     * @param contour MatOfPoint object containing the card's corner points
     * @return Perspective-corrected card bitmap, or original if correction not yet implemented
     */
    fun perspectiveCorrect(cardBitmap: Bitmap, contour: MatOfPoint): Bitmap {
        // TODO: Implement perspective correction using homography
        // For now, return original bitmap
        return cardBitmap
    }
}

/**
 * Represents a detected card region within a camera frame.
 * Stores position, dimensions, OpenCV contour, and area information for the detected card.
 *
 * @param x Horizontal position (pixels) of the top-left corner of the bounding box
 * @param y Vertical position (pixels) of the top-left corner of the bounding box
 * @param width Width (pixels) of the card region's bounding rectangle
 * @param height Height (pixels) of the card region's bounding rectangle
 * @param contour MatOfPoint object containing the card's contour points for perspective correction
 * @param area Total pixel area of the contour (used for filtering during detection)
 * @param trackingId Unique tracking identifier assigned by CardTracker; default -1 if untracked
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
