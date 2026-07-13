package com.mtgscanner.detection

import android.graphics.Bitmap

/**
 * Detects card regions in camera frames.
 * Current implementation is a safe placeholder while native preprocessing is removed
 * to keep the APK installable on 16 KB devices.
 */
class CardDetector {

    /**
     * Detect all card regions in a bitmap frame.
     * @param bitmap Input frame bitmap from camera preview, expected RGB or BGR format
     * @return List of CardRegion objects representing detected card locations; empty if no cards found
     */
    fun detectCards(bitmap: Bitmap): List<CardRegion> {
        return emptyList()
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
     * Performs perspective correction on a card region.
     * Currently returns the original bitmap.
     *
     * @param cardBitmap Card image to apply perspective correction to
     * @return Perspective-corrected card bitmap, or original if correction not yet implemented
     */
    fun perspectiveCorrect(cardBitmap: Bitmap): Bitmap {
        return cardBitmap
    }
}

/**
 * Represents a detected card region within a camera frame.
 * Stores position, dimensions, and area information for the detected card.
 *
 * @param x Horizontal position (pixels) of the top-left corner of the bounding box
 * @param y Vertical position (pixels) of the top-left corner of the bounding box
 * @param width Width (pixels) of the card region's bounding rectangle
 * @param height Height (pixels) of the card region's bounding rectangle
 * @param area Total pixel area of the contour (used for filtering during detection)
 * @param trackingId Unique tracking identifier assigned by CardTracker; default -1 if untracked
 */
data class CardRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int,
    var trackingId: Int = -1
)
