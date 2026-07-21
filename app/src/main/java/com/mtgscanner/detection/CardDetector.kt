package com.mtgscanner.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Detects rectangular card-like regions in camera frames using edge-based processing.
 *
 * Strategy:
 * 1. Downscale the frame (adaptive: 4× for large frames, 2× for small)
 * 2. Compute gradient magnitude (Sobel-like edge detection)
 * 3. Threshold edges at a level that catches card borders but ignores surface texture
 * 4. Dilate edge map to close small gaps
 * 5. Flood-fill interior (non-edge) regions
 * 6. Filter by aspect ratio (0.50–0.90), area (1.5%–60%), and fill ratio (>25%)
 *
 * Tuning rationale:
 * - EDGE_THRESHOLD=50: Requires strong luminance change at card border, ignores paper/wood grain
 * - MIN_AREA_FRACTION=0.015: Catches cards even when photographed from further away
 * - Aspect ratio 0.50–0.90: Accommodates slight tilt and perspective distortion
 * - Fill ratio 0.25: Cards with rules text have internal edges that split the interior
 */
class CardDetector {

    companion object {
        private const val TAG = "CardDetector"

        /** Minimum region area as fraction of total frame area */
        private const val MIN_AREA_FRACTION = 0.015f

        /** Maximum region area as fraction (reject regions covering most of frame) */
        private const val MAX_AREA_FRACTION = 0.60f

        /** Card aspect ratio (width/height) — widened for perspective tolerance */
        private const val MIN_ASPECT_RATIO = 0.50f
        private const val MAX_ASPECT_RATIO = 0.90f

        /** Edge threshold — higher = only strong borders detected, ignores texture */
        private const val EDGE_THRESHOLD = 50

        /** Minimum fill ratio — how much of bounding box must be interior pixels */
        private const val MIN_FILL_RATIO = 0.25f
    }

    /**
     * Detect all card-like rectangular regions in a bitmap frame.
     */
    fun detectCards(bitmap: Bitmap): List<CardRegion> {
        val fullW = bitmap.width
        val fullH = bitmap.height
        val totalPixels = fullW * fullH
        val minArea = (totalPixels * MIN_AREA_FRACTION).toInt()
        val maxArea = (totalPixels * MAX_AREA_FRACTION).toInt()

        // Adaptive downscale: 4× for large frames (>2000px), 2× otherwise
        val scale = if (fullW > 2000 || fullH > 2000) 4 else 2
        val w = fullW / scale
        val h = fullH / scale
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)

        // Step 1: Grayscale luminance
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            lum[i] = (Color.red(p) * 77 + Color.green(p) * 150 + Color.blue(p) * 29) shr 8
        }

        // Step 2: Edge magnitude (Sobel-like)
        val edges = BooleanArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx = lum[idx + 1] - lum[idx - 1]
                val gy = lum[idx + w] - lum[idx - w]
                val magnitude = kotlin.math.abs(gx) + kotlin.math.abs(gy)
                edges[idx] = magnitude > EDGE_THRESHOLD
            }
        }

        // Step 3: Dilate edges (2 iterations for better gap closure)
        var current = edges
        repeat(2) {
            val dilated = BooleanArray(w * h)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    dilated[idx] = current[idx]
                        || current[idx - 1] || current[idx + 1]
                        || current[idx - w] || current[idx + w]
                }
            }
            current = dilated
        }

        // Step 4: Interior = non-edge regions
        val interior = BooleanArray(w * h) { !current[it] }

        // Step 5: Connected component labeling
        val labels = IntArray(w * h)
        var nextLabel = 1
        val regionBounds = mutableMapOf<Int, IntArray>()

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

        // Step 6: Filter by area, aspect ratio, and fill ratio
        val detectedCards = mutableListOf<CardRegion>()

        for ((_, bounds) in regionBounds) {
            val minX = bounds[0] * scale
            val minY = bounds[1] * scale
            val maxX = bounds[2] * scale
            val maxY = bounds[3] * scale
            val pixelCount = bounds[4] * scale * scale

            val regionWidth = maxX - minX + scale
            val regionHeight = maxY - minY + scale
            val regionArea = regionWidth * regionHeight

            if (regionArea < minArea || regionArea > maxArea) continue

            val aspectRatio = regionWidth.toFloat() / regionHeight.toFloat()
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) continue

            val fillRatio = pixelCount.toFloat() / regionArea
            if (fillRatio < MIN_FILL_RATIO) continue

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
            Log.d(TAG, "Detected ${detectedCards.size} region(s) in ${fullW}x${fullH} (scale=$scale)")
        }

        if (scaled != bitmap) scaled.recycle()
        return detectedCards
    }

    private fun floodFillWithCount(
        binary: BooleanArray,
        labels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        label: Int
    ): IntArray {
        var minX = startX; var minY = startY; var maxX = startX; var maxY = startY
        var count = 0

        val stack = ArrayDeque<Int>(512)
        val startIdx = startY * width + startX
        stack.addLast(startIdx)
        labels[startIdx] = label

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            val x = idx % width
            val y = idx / width
            count++
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y

            if (x > 0 && binary[idx - 1] && labels[idx - 1] == 0) { labels[idx - 1] = label; stack.addLast(idx - 1) }
            if (x < width - 1 && binary[idx + 1] && labels[idx + 1] == 0) { labels[idx + 1] = label; stack.addLast(idx + 1) }
            if (y > 0 && binary[idx - width] && labels[idx - width] == 0) { labels[idx - width] = label; stack.addLast(idx - width) }
            if (y < height - 1 && binary[idx + width] && labels[idx + width] == 0) { labels[idx + width] = label; stack.addLast(idx + width) }
        }

        return intArrayOf(minX, minY, maxX, maxY, count)
    }

    fun extractCardImage(bitmap: Bitmap, region: CardRegion): Bitmap {
        val safeX = region.x.coerceIn(0, bitmap.width - 1)
        val safeY = region.y.coerceIn(0, bitmap.height - 1)
        val safeW = region.width.coerceAtMost(bitmap.width - safeX).coerceAtLeast(1)
        val safeH = region.height.coerceAtMost(bitmap.height - safeY).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
    }

    fun perspectiveCorrect(cardBitmap: Bitmap): Bitmap = cardBitmap
}

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
