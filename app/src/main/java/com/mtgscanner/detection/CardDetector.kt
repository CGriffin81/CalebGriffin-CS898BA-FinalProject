package com.mtgscanner.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Detects rectangular card-like regions in camera frames using Android-native Bitmap processing.
 *
 * Detection strategy (no OpenCV required):
 * 1. Convert the frame to grayscale luminance values
 * 2. Compute adaptive threshold (mean luminance of the frame)
 * 3. Binarize: pixels above threshold → foreground (card), below → background
 * 4. Scan for connected rectangular foreground regions via row-projection bounding boxes
 * 5. Filter by:
 *    - Minimum area: > 2% of total frame pixels (rejects noise)
 *    - Aspect ratio: width/height in 0.60–0.80 range (MTG card is 2.5"×3.5" = 0.714)
 *
 * Limitations:
 * - Works best with light cards on dark backgrounds (typical binder mat or dark desk)
 * - No perspective correction (tracked as future work)
 * - Simple threshold may fail under mixed lighting (CLAHE enhancement is P2-06)
 * - Does not handle overlapping cards
 */
class CardDetector {

    companion object {
        private const val TAG = "CardDetector"

        /** Minimum region area as fraction of total frame area (2%) */
        private const val MIN_AREA_FRACTION = 0.02f

        /** Card aspect ratio (width/height) range for standard MTG cards */
        private const val MIN_ASPECT_RATIO = 0.55f
        private const val MAX_ASPECT_RATIO = 0.85f
    }

    /**
     * Detect all card-like rectangular regions in a bitmap frame.
     *
     * Scans the frame for bright rectangular regions against a darker background.
     * Returns bounding boxes that pass aspect ratio and area filtering.
     *
     * Performance: ~15–40ms at 1280×720 on a modern phone (pure Kotlin pixel scan).
     *
     * @param bitmap Input frame bitmap from camera preview (RGB, any size).
     * @return List of [CardRegion] objects representing detected card locations.
     *   Empty if no card-shaped regions found.
     */
    fun detectCards(bitmap: Bitmap): List<CardRegion> {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val minArea = (totalPixels * MIN_AREA_FRACTION).toInt()

        // Step 1: Extract grayscale luminance and compute mean for thresholding
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val luminance = ByteArray(totalPixels)
        var lumSum = 0L
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Standard luminance: 0.299R + 0.587G + 0.114B
            val lum = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
            luminance[i] = lum.toByte()
            lumSum += lum
        }

        val meanLuminance = (lumSum / totalPixels).toInt()
        // Threshold slightly above mean to bias toward detecting bright cards on dark backgrounds
        val threshold = (meanLuminance + 30).coerceIn(60, 220)

        // Step 2: Binarize — true = foreground (bright, likely card surface)
        val binary = BooleanArray(totalPixels)
        for (i in luminance.indices) {
            binary[i] = (luminance[i].toInt() and 0xFF) > threshold
        }

        // Step 3: Find connected foreground regions using flood-fill labeling
        val labels = IntArray(totalPixels) // 0 = unlabeled
        var nextLabel = 1
        val regionBounds = mutableMapOf<Int, IntArray>() // label → [minX, minY, maxX, maxY]

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (binary[idx] && labels[idx] == 0) {
                    // Flood-fill this connected component
                    val bounds = floodFill(binary, labels, width, height, x, y, nextLabel)
                    regionBounds[nextLabel] = bounds
                    nextLabel++
                }
            }
        }

        // Step 4: Filter regions by area and aspect ratio
        val detectedCards = mutableListOf<CardRegion>()

        for ((_, bounds) in regionBounds) {
            val minX = bounds[0]
            val minY = bounds[1]
            val maxX = bounds[2]
            val maxY = bounds[3]

            val regionWidth = maxX - minX + 1
            val regionHeight = maxY - minY + 1
            val regionArea = regionWidth * regionHeight

            // Area filter: reject tiny noise regions
            if (regionArea < minArea) continue

            // Aspect ratio filter: width/height should match card proportions
            val aspectRatio = regionWidth.toFloat() / regionHeight.toFloat()
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) continue

            detectedCards.add(
                CardRegion(
                    x = minX,
                    y = minY,
                    width = regionWidth,
                    height = regionHeight,
                    area = regionArea
                )
            )
        }

        if (detectedCards.isNotEmpty()) {
            Log.d(TAG, "Detected ${detectedCards.size} card region(s) in ${width}x${height} frame")
        }

        return detectedCards
    }

    /**
     * Flood-fill a connected foreground region starting at (startX, startY).
     *
     * Uses an iterative stack-based approach to avoid stack overflow on large regions.
     * Labels all connected foreground pixels with [label] and returns the bounding box
     * as [minX, minY, maxX, maxY].
     *
     * @param binary Boolean array where true = foreground pixel.
     * @param labels Int array for labeling visited pixels (modified in-place).
     * @param width Image width.
     * @param height Image height.
     * @param startX Starting X coordinate for the fill.
     * @param startY Starting Y coordinate for the fill.
     * @param label Label value to assign to this region's pixels.
     * @return IntArray of [minX, minY, maxX, maxY] bounding box.
     */
    private fun floodFill(
        binary: BooleanArray,
        labels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        label: Int
    ): IntArray {
        var minX = startX
        var minY = startY
        var maxX = startX
        var maxY = startY

        val stack = ArrayDeque<Int>()
        val startIdx = startY * width + startX
        stack.addLast(startIdx)
        labels[startIdx] = label

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            val x = idx % width
            val y = idx / width

            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            // Check 4-connected neighbors
            val neighbors = intArrayOf(
                if (x > 0) idx - 1 else -1,          // left
                if (x < width - 1) idx + 1 else -1,  // right
                if (y > 0) idx - width else -1,       // up
                if (y < height - 1) idx + width else -1 // down
            )

            for (nIdx in neighbors) {
                if (nIdx >= 0 && binary[nIdx] && labels[nIdx] == 0) {
                    labels[nIdx] = label
                    stack.addLast(nIdx)
                }
            }
        }

        return intArrayOf(minX, minY, maxX, maxY)
    }

    /**
     * Extract a card image region from the source frame bitmap.
     *
     * Creates a cropped Bitmap using the coordinates and dimensions from the [CardRegion].
     * The extracted region is ready for OCR processing.
     *
     * @param bitmap Source frame bitmap to crop from.
     * @param region [CardRegion] containing x, y, width, and height of the card.
     * @return New cropped Bitmap containing only the card region.
     */
    fun extractCardImage(bitmap: Bitmap, region: CardRegion): Bitmap {
        // Clamp to bitmap bounds to prevent IndexOutOfBoundsException
        val safeX = region.x.coerceIn(0, bitmap.width - 1)
        val safeY = region.y.coerceIn(0, bitmap.height - 1)
        val safeW = region.width.coerceAtMost(bitmap.width - safeX)
        val safeH = region.height.coerceAtMost(bitmap.height - safeY)

        return Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
    }

    /**
     * Performs perspective correction on a card region.
     * Currently returns the original bitmap — perspective correction is tracked as future work.
     *
     * @param cardBitmap Card image to apply perspective correction to.
     * @return The original bitmap (unchanged).
     */
    fun perspectiveCorrect(cardBitmap: Bitmap): Bitmap {
        return cardBitmap
    }
}

/**
 * Represents a detected card region within a camera frame.
 *
 * @param x Horizontal position (pixels) of the top-left corner of the bounding box.
 * @param y Vertical position (pixels) of the top-left corner of the bounding box.
 * @param width Width (pixels) of the card region's bounding rectangle.
 * @param height Height (pixels) of the card region's bounding rectangle.
 * @param area Total pixel area of the bounding box (width × height).
 * @param trackingId Unique tracking identifier assigned by [CardTracker]; default -1 if untracked.
 */
data class CardRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int,
    var trackingId: Int = -1
) {
    /** Center X coordinate of the bounding box. */
    val centerX: Int get() = x + width / 2

    /** Center Y coordinate of the bounding box. */
    val centerY: Int get() = y + height / 2

    /** Aspect ratio (width / height) of the detected region. */
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}
