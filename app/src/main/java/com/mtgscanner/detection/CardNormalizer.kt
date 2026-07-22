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
        private const val MIN_ASPECT_RATIO = 0.55f
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

        // Step 2: Expand bounding box
        val expandX = (region.width * EXPAND_FRACTION).toInt()
        val expandY = (region.height * EXPAND_FRACTION).toInt()
        val expanded = CardRegion(
            x = (region.x - expandX).coerceAtLeast(0),
            y = (region.y - expandY).coerceAtLeast(0),
            width = (region.width + 2 * expandX).coerceAtMost(frameBitmap.width - (region.x - expandX).coerceAtLeast(0)),
            height = (region.height + 2 * expandY).coerceAtMost(frameBitmap.height - (region.y - expandY).coerceAtLeast(0)),
            area = region.area
        )

        // Step 3: Extract from frame
        val safeX = expanded.x.coerceIn(0, frameBitmap.width - 1)
        val safeY = expanded.y.coerceIn(0, frameBitmap.height - 1)
        val safeW = expanded.width.coerceAtMost(frameBitmap.width - safeX).coerceAtLeast(1)
        val safeH = expanded.height.coerceAtMost(frameBitmap.height - safeY).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(frameBitmap, safeX, safeY, safeW, safeH)

        // Step 4: Scale to canonical size
        val canonical = Bitmap.createScaledBitmap(cropped, CANONICAL_WIDTH, CANONICAL_HEIGHT, true)
        if (cropped != canonical) cropped.recycle()

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
