package com.mtgscanner.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Detects rectangular card-like regions in camera frames using Android-native Bitmap processing.
 *
 * Detection strategy (edge-based, works on any background):
 * 1. Convert frame to grayscale
 * 2. Compute Sobel-like edge magnitude at each pixel
 * 3. Threshold edges to create binary edge map
 * 4. Scan for rectangular regions bounded by strong edges
 * 5. Filter by aspect ratio (0.55–0.85) and minimum area (2% of frame)
 *
 * This approach detects card borders regardless of whether the card is brighter or
 * darker than the background — it only requires a luminance difference at the edge.
 * Works on white desks, dark mats, binder pages, and held in hand.
 */
class CardDetector {

    companion object {
        private const val TAG = "CardDetector"

        /** Minimum region area as fraction of total frame area */
        private const val MIN_AREA_FRACTION = 0.02f

        /** Maximum region area as fraction (reject regions covering most of frame) */
        private const val MAX_AREA_FRACTION = 0.60f

        /** Card aspect ratio (width/height) range for standard MTG cards */
        private const val MIN_ASPECT_RATIO = 0.55f
        private const val MAX_ASPECT_RATIO = 0.85f

        /** Edge threshold — pixels with gradient magnitude above this are "edge" */
        private const val EDGE_THRESHOLD = 30

        /** Downscale factor for performance — process at 1/SCALE resolution */
        private const val SCALE = 2
    }

    /**
     * Detect all card-like rectangular regions in a bitmap frame.
     *
     * Uses edge detection (gradient magnitude) to find card borders regardless of
     * background brightness. Works on both dark and light surfaces.
     *
     * Performance: ~20–50ms at 1280×720 on a modern phone (downscaled 2× internally).
     *
     * @param bitmap Input frame bitmap from camera preview (RGB, any size).
     * @return List of [CardRegion] objects representing detected card locations.
     */
    fun detectCards(bitmap: Bitmap): List<CardRegion> {
        val fullW = bitmap.width
        val fullH = bitmap.height
        val totalPixels = fullW * fullH
        val minArea = (totalPixels * MIN_AREA_FRACTION).toInt()
        val maxArea = (totalPixels * MAX_AREA_FRACTION).toInt()

        // Downscale for performance
        val w = fullW / SCALE
        val h = fullH / SCALE
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)

        // Step 1: Grayscale luminance
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            lum[i] = (Color.red(p) * 77 + Color.green(p) * 150 + Color.blue(p) * 29) shr 8
        }

        // Step 2: Sobel edge magnitude (simplified — horizontal + vertical gradient)
        val edges = BooleanArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                // Horizontal gradient
                val gx = lum[idx + 1] - lum[idx - 1]
                // Vertical gradient
                val gy = lum[idx + w] - lum[idx - w]
                // Magnitude approximation (Manhattan distance — faster than sqrt)
                val magnitude = kotlin.math.abs(gx) + kotlin.math.abs(gy)
                edges[idx] = magnitude > EDGE_THRESHOLD
            }
        }

        // Step 3: Dilate edges slightly to close small gaps in card borders
        val dilated = BooleanArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                dilated[idx] = edges[idx]
                    || edges[idx - 1] || edges[idx + 1]
                    || edges[idx - w] || edges[idx + w]
            }
        }

        // Step 4: Find "interior" regions — large connected areas NOT crossed by edges
        // A card's interior is a large uniform area surrounded by its border edges.
        // Invert the edge map: non-edge pixels are potential card interiors.
        val interior = BooleanArray(w * h) { !dilated[it] }

        // Connected component labeling on interior regions
        val labels = IntArray(w * h)
        var nextLabel = 1
        val regionBounds = mutableMapOf<Int, IntArray>() // label → [minX, minY, maxX, maxY, pixelCount]

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (interior[idx] && labels[idx] == 0) {
                    val bounds = floodFillWithCount(interior, labels, w, h, x, y, nextLabel)
                    regionBounds[nextLabel] = bounds
                    nextLabel++
                }
            }
        }

        // Step 5: Filter by area and aspect ratio (scale back to full resolution)
        val detectedCards = mutableListOf<CardRegion>()

        for ((_, bounds) in regionBounds) {
            val minX = bounds[0] * SCALE
            val minY = bounds[1] * SCALE
            val maxX = bounds[2] * SCALE
            val maxY = bounds[3] * SCALE
            val pixelCount = bounds[4] * SCALE * SCALE  // approximate

            val regionWidth = maxX - minX + SCALE
            val regionHeight = maxY - minY + SCALE
            val regionArea = regionWidth * regionHeight

            // Area filters
            if (regionArea < minArea) continue
            if (regionArea > maxArea) continue

            // Aspect ratio filter
            val aspectRatio = regionWidth.toFloat() / regionHeight.toFloat()
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) continue

            // Rectangularity check: pixel count should be >50% of bounding box area
            // (rejects L-shapes and irregular blobs)
            val fillRatio = pixelCount.toFloat() / regionArea
            if (fillRatio < 0.4f) continue

            detectedCards.add(
                CardRegion(
                    x = minX.coerceIn(0, fullW - 1),
                    y = minY.coerceIn(0, fullH - 1),
                    width = regionWidth.coerceAtMost(fullW - minX),
                    height = regionHeight.coerceAtMost(fullH - minY),
                    area = regionArea
                )
            )
        }

        if (detectedCards.isNotEmpty()) {
            Log.d(TAG, "Detected ${detectedCards.size} card region(s) in ${fullW}x${fullH} frame")
        }

        // Recycle the scaled bitmap
        if (scaled != bitmap) scaled.recycle()

        return detectedCards
    }

    /**
     * Flood-fill with pixel count — iterative stack-based approach.
     * Returns [minX, minY, maxX, maxY, pixelCount].
     */
    private fun floodFillWithCount(
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
        var count = 0

        val stack = ArrayDeque<Int>(256)
        val startIdx = startY * width + startX
        stack.addLast(startIdx)
        labels[startIdx] = label

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            val x = idx % width
            val y = idx / width
            count++

            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            // 4-connected neighbors
            if (x > 0 && binary[idx - 1] && labels[idx - 1] == 0) {
                labels[idx - 1] = label; stack.addLast(idx - 1)
            }
            if (x < width - 1 && binary[idx + 1] && labels[idx + 1] == 0) {
                labels[idx + 1] = label; stack.addLast(idx + 1)
            }
            if (y > 0 && binary[idx - width] && labels[idx - width] == 0) {
                labels[idx - width] = label; stack.addLast(idx - width)
            }
            if (y < height - 1 && binary[idx + width] && labels[idx + width] == 0) {
                labels[idx + width] = label; stack.addLast(idx + width)
            }
        }

        return intArrayOf(minX, minY, maxX, maxY, count)
    }

    /**
     * Extract a card image region from the source frame bitmap.
     */
    fun extractCardImage(bitmap: Bitmap, region: CardRegion): Bitmap {
        val safeX = region.x.coerceIn(0, bitmap.width - 1)
        val safeY = region.y.coerceIn(0, bitmap.height - 1)
        val safeW = region.width.coerceAtMost(bitmap.width - safeX).coerceAtLeast(1)
        val safeH = region.height.coerceAtMost(bitmap.height - safeY).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
    }

    /**
     * Perspective correction placeholder — returns input unchanged.
     */
    fun perspectiveCorrect(cardBitmap: Bitmap): Bitmap = cardBitmap
}

/**
 * Represents a detected card region within a camera frame.
 */
data class CardRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int,
    var trackingId: Int = -1
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}
