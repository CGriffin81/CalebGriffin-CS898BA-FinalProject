package com.mtgscanner.detection

import android.graphics.Bitmap
import android.util.Log

/**
 * Normalizes detected card crops to a canonical size with validated aspect ratio.
 *
 * Responsibilities:
 * 1. Validate aspect ratio is within MTG card tolerance (rejects non-card rectangles)
 * 2. Expand the detected region consistently (20% padding for text capture)
 * 3. Scale the crop to a canonical resolution for consistent OCR performance
 * 4. Expose detection metadata for debug overlay visualization
 *
 * MTG card aspect ratio: 63mm × 88mm = 0.716 (width/height)
 * With tolerance for perspective distortion: 0.55–0.85
 *
 * Canonical output: 488×680 pixels (matches Scryfall "normal" image size)
 * This ensures OCR operates on a consistent resolution regardless of camera frame size.
 */
class CardNormalizer {

    companion object {
        private const val TAG = "CardNormalizer"

        /** Standard MTG card aspect ratio (width / height) */
        private const val CARD_ASPECT_RATIO = 0.716f  // 63/88

        /** Acceptable range for detected aspect ratio (accounts for perspective) */
        private const val MIN_ASPECT_RATIO = 0.50f
        private const val MAX_ASPECT_RATIO = 0.85f

        /** Expansion fraction applied to detected region (captures name + collector) */
        private const val EXPAND_FRACTION = 0.20f

        /** Canonical output dimensions matching Scryfall "normal" images */
        const val CANONICAL_WIDTH = 488
        const val CANONICAL_HEIGHT = 680
    }

    /** Last normalization result for debug overlay. */
    @Volatile
    var lastResult: NormalizationResult? = null
        private set

    /**
     * Normalize a detected card region into a canonical bitmap.
     *
     * Steps:
     * 1. Validate aspect ratio — reject if outside tolerance
     * 2. Expand the bounding box by [EXPAND_FRACTION] on all sides
     * 3. Extract the expanded region from the frame
     * 4. Scale to canonical resolution [CANONICAL_WIDTH] × [CANONICAL_HEIGHT]
     *
     * @param frameBitmap The full camera frame
     * @param region Detected card region from CardDetector
     * @param trackingId Tracking ID for correlation
     * @return [NormalizationResult] with the canonical bitmap, or rejected=true if invalid
     */
    fun normalize(frameBitmap: Bitmap, region: CardRegion, trackingId: Int): NormalizationResult {
        val aspectRatio = region.width.toFloat() / region.height.toFloat()

        // Step 1: Validate aspect ratio
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            Log.d(TAG, "REJECTED trackingId=$trackingId: aspect=${"%.3f".format(aspectRatio)} " +
                "outside [$MIN_ASPECT_RATIO, $MAX_ASPECT_RATIO]")
            val result = NormalizationResult(
                bitmap = null,
                rejected = true,
                rejectReason = "aspect ratio ${"%.2f".format(aspectRatio)} outside tolerance",
                trackingId = trackingId,
                detectedRegion = region,
                expandedRegion = region,
                aspectRatio = aspectRatio,
                confidence = 0f
            )
            lastResult = result
            return result
        }

        // Step 2: Expand bounding box with margin for rotation correction
        // Add extra 5% beyond the standard expansion to ensure rotated card
        // content isn't clipped after deskewing.
        val expandFrac = EXPAND_FRACTION + 0.05f
        val expandX = (region.width * expandFrac).toInt()
        val expandY = (region.height * expandFrac).toInt()

        // Force the expanded region to standard card aspect ratio (0.716 = w/h).
        var expWidth = region.width + 2 * expandX
        var expHeight = region.height + 2 * expandY
        val currentAspect = expWidth.toFloat() / expHeight.toFloat()

        if (currentAspect < CARD_ASPECT_RATIO - 0.05f) {
            expWidth = (expHeight * CARD_ASPECT_RATIO).toInt()
        } else if (currentAspect > CARD_ASPECT_RATIO + 0.05f) {
            expHeight = (expWidth / CARD_ASPECT_RATIO).toInt()
        }

        // Center on detected region
        val centerX = region.x + region.width / 2
        val centerY = region.y + region.height / 2
        val expanded = CardRegion(
            x = (centerX - expWidth / 2).coerceAtLeast(0),
            y = (centerY - expHeight / 2).coerceAtLeast(0),
            width = expWidth.coerceAtMost(frameBitmap.width - (centerX - expWidth / 2).coerceAtLeast(0)),
            height = expHeight.coerceAtMost(frameBitmap.height - (centerY - expHeight / 2).coerceAtLeast(0)),
            area = region.area
        )

        // Step 3: Extract from frame
        val safeX = expanded.x.coerceIn(0, frameBitmap.width - 1)
        val safeY = expanded.y.coerceIn(0, frameBitmap.height - 1)
        val safeW = expanded.width.coerceAtMost(frameBitmap.width - safeX).coerceAtLeast(1)
        val safeH = expanded.height.coerceAtMost(frameBitmap.height - safeY).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(frameBitmap, safeX, safeY, safeW, safeH)

        // Step 4: Estimate rotation and deskew
        val deskewed = deskewCard(cropped)

        // Step 5: Scale to canonical size
        val canonical = Bitmap.createScaledBitmap(deskewed, CANONICAL_WIDTH, CANONICAL_HEIGHT, true)
        if (deskewed != canonical && deskewed != cropped) deskewed.recycle()
        if (cropped != canonical && cropped != deskewed) cropped.recycle()

        // Compute confidence: closer to ideal aspect ratio = higher confidence
        val aspectDeviation = kotlin.math.abs(aspectRatio - CARD_ASPECT_RATIO)
        val confidence = (1f - aspectDeviation / 0.2f).coerceIn(0.3f, 1.0f)

        Log.d(TAG, "Normalized trackingId=$trackingId: " +
            "detected=${region.width}x${region.height} aspect=${"%.3f".format(aspectRatio)}, " +
            "expanded=${expanded.width}x${expanded.height}, " +
            "canonical=${CANONICAL_WIDTH}x$CANONICAL_HEIGHT, conf=${"%.2f".format(confidence)}")

        val result = NormalizationResult(
            bitmap = canonical,
            rejected = false,
            rejectReason = null,
            trackingId = trackingId,
            detectedRegion = region,
            expandedRegion = expanded,
            aspectRatio = aspectRatio,
            confidence = confidence
        )
        lastResult = result
        return result
    }

    /**
     * Estimate and correct card rotation (deskew).
     *
     * Strategy: detect the dominant horizontal edge in the top 30% of the image
     * (the name bar / art border is a strong horizontal line). Measure its angle
     * from true horizontal and rotate the bitmap to correct it.
     *
     * If the angle is < 1°, skip rotation (not worth the quality loss).
     * If the angle is > 15°, skip rotation (probably not a card or too distorted).
     *
     * Uses sampling-based edge angle estimation — no OpenCV dependency.
     */
    private fun deskewCard(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // Sample the top 30% for horizontal edge detection
        val searchHeight = (h * 0.3f).toInt()
        if (searchHeight < 10 || w < 20) return bitmap

        // Find the strongest horizontal edge row in the top region
        // by computing the average vertical gradient across each row
        var bestRow = -1
        var bestStrength = 0
        val stepX = (w / 30).coerceAtLeast(1) // Sample ~30 points per row

        for (y in 5 until searchHeight - 5) {
            var rowStrength = 0
            var x = 5
            while (x < w - 5) {
                val above = luminanceAt(bitmap, x, y - 2)
                val below = luminanceAt(bitmap, x, y + 2)
                rowStrength += kotlin.math.abs(below - above)
                x += stepX
            }
            if (rowStrength > bestStrength) {
                bestStrength = rowStrength
                bestRow = y
            }
        }

        if (bestRow < 0) return bitmap

        // Estimate angle: compare edge Y position at left vs right
        val leftY = findEdgeY(bitmap, bestRow, w / 6, stepX = 1)
        val rightY = findEdgeY(bitmap, bestRow, w * 5 / 6, stepX = 1)

        if (leftY < 0 || rightY < 0) return bitmap

        val dx = (w * 4 / 6).toFloat()
        val dy = (rightY - leftY).toFloat()
        val angleRad = kotlin.math.atan2(dy, dx)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

        // Skip if angle is negligible or too extreme
        if (kotlin.math.abs(angleDeg) < 1f || kotlin.math.abs(angleDeg) > 15f) {
            return bitmap
        }

        Log.d(TAG, "Deskew: angle=${"%.1f".format(angleDeg)}° (leftY=$leftY, rightY=$rightY)")

        // Rotate the bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(-angleDeg, w / 2f, h / 2f)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)

        // Crop the rotated bitmap to remove black corners
        // After rotation, valid content is inset by ~sin(angle) * dimension
        val inset = (kotlin.math.abs(kotlin.math.sin(angleRad)) * kotlin.math.min(w, h) * 0.5f).toInt()
        val cropX = inset.coerceAtMost(w / 10)
        val cropY = inset.coerceAtMost(h / 10)
        val cropW = (w - 2 * cropX).coerceAtLeast(w / 2)
        val cropH = (h - 2 * cropY).coerceAtLeast(h / 2)

        val result = Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
        if (rotated != result) rotated.recycle()

        return result
    }

    /** Find the exact Y of the strongest horizontal edge at a given X position, near expectedY. */
    private fun findEdgeY(bitmap: Bitmap, expectedY: Int, x: Int, stepX: Int): Int {
        val searchRange = 15
        val yMin = (expectedY - searchRange).coerceAtLeast(2)
        val yMax = (expectedY + searchRange).coerceAtMost(bitmap.height - 3)

        var bestY = -1
        var bestStr = 0

        for (y in yMin..yMax) {
            val above = luminanceAt(bitmap, x, y - 1)
            val below = luminanceAt(bitmap, x, y + 1)
            val str = kotlin.math.abs(below - above)
            if (str > bestStr) {
                bestStr = str
                bestY = y
            }
        }
        return if (bestStr > 20) bestY else -1
    }

    /** Get luminance (0–255) at a pixel. */
    private fun luminanceAt(bitmap: Bitmap, x: Int, y: Int): Int {
        val px = bitmap.getPixel(
            x.coerceIn(0, bitmap.width - 1),
            y.coerceIn(0, bitmap.height - 1)
        )
        return (android.graphics.Color.red(px) * 77 +
                android.graphics.Color.green(px) * 150 +
                android.graphics.Color.blue(px) * 29) shr 8
    }
}

/**
 * Result of card normalization — contains the canonical bitmap plus metadata
 * for debug overlay visualization.
 *
 * @param bitmap Normalized bitmap at canonical resolution (null if rejected)
 * @param rejected Whether the detection was rejected (bad aspect ratio)
 * @param rejectReason Human-readable rejection reason (null if accepted)
 * @param trackingId Card tracking ID
 * @param detectedRegion Original detection bounding box (before expansion)
 * @param expandedRegion Expanded bounding box (after 20% expansion)
 * @param aspectRatio Measured aspect ratio of the detected region
 * @param confidence Normalization confidence (higher = closer to ideal card shape)
 */
data class NormalizationResult(
    val bitmap: Bitmap?,
    val rejected: Boolean,
    val rejectReason: String?,
    val trackingId: Int,
    val detectedRegion: CardRegion,
    val expandedRegion: CardRegion,
    val aspectRatio: Float,
    val confidence: Float
)
