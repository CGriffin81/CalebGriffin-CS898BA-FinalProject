package com.mtgscanner.detection

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrates detection → tracking → normalization → callback for each camera frame.
 *
 * Flow per frame:
 * 1. Detect card regions via [CardDetector]
 * 2. Track detections via [CardTracker] (frame-to-frame ID persistence)
 * 3. When a track reaches stability, normalize via [CardNormalizer]
 *    - Validates aspect ratio (rejects non-card shapes)
 *    - Expands bounding box consistently (20%)
 *    - Scales to canonical 488×680 resolution
 * 4. Fire [onCardReady] with normalized bitmap
 * 5. Prune stale tracks and notify UI via [onFrameAnalysis]
 *
 * The normalized output ensures OCR always operates on a consistent resolution
 * regardless of camera frame size or card distance from camera.
 */
class DetectionPipeline(
    var onCardReady: (cardBitmap: Bitmap, trackingId: Int) -> Unit = { _, _ -> },
    var onFrameAnalysis: (detectionCount: Int) -> Unit = { _ -> }
) {
    companion object {
        private const val TAG = "DetectionPipeline"
    }

    private val cardDetector = CardDetector()
    private val cardTracker = CardTracker()
    private val cardNormalizer = CardNormalizer()
    private val processedCards = mutableSetOf<Int>()
    private var calibrated = false
    private var frameCount = 0

    /** Exposed for debug overlay — last normalization results per frame. */
    val normalizer: CardNormalizer get() = cardNormalizer

    /** Detection metadata for the most recent frame (for overlay). */
    @Volatile
    var lastDetections: List<CardRegion> = emptyList()
        private set

    /**
     * Process a single camera frame through detection + tracking + normalization.
     */
    fun processFrame(frameBitmap: Bitmap) {
        try {
            frameCount++

            // Calibrate tracker threshold on first frame (adapts to actual resolution)
            if (!calibrated) {
                cardTracker.calibrateThreshold(frameBitmap.width, frameBitmap.height)
                calibrated = true
                Log.d(TAG, "Calibrated for ${frameBitmap.width}x${frameBitmap.height}")
            }

            // Log every 30th frame to avoid flooding logcat
            if (frameCount % 30 == 1) {
                Log.d(TAG, "Frame #$frameCount: ${frameBitmap.width}x${frameBitmap.height}, " +
                    "processedCards=${processedCards.size}")
            }

            // Step 1: Detect card-shaped regions
            val detections = cardDetector.detectCards(frameBitmap)
            lastDetections = detections

            // Step 2: Track detections across frames
            val matchMap = cardTracker.updateTracks(detections)

            // Step 3: Normalize stable, unprocessed cards
            for ((detIdx, trackingId) in matchMap) {
                if (trackingId !in processedCards && cardTracker.isStableDetection(trackingId)) {
                    val cardRegion = detections[detIdx]

                    // Normalize: validate aspect ratio, expand, scale to canonical size
                    val normResult = cardNormalizer.normalize(frameBitmap, cardRegion, trackingId)

                    if (normResult.rejected) {
                        // Bad aspect ratio — skip this detection, allow re-evaluation
                        Log.d(TAG, "Skipped trackingId=$trackingId: ${normResult.rejectReason}")
                        continue
                    }

                    val cardBitmap = normResult.bitmap ?: continue

                    Log.d(TAG, "Card ready: trackingId=$trackingId, " +
                        "detected=${cardRegion.width}x${cardRegion.height}, " +
                        "aspect=${"%.3f".format(normResult.aspectRatio)}, " +
                        "canonical=${cardBitmap.width}x${cardBitmap.height}, " +
                        "conf=${"%.2f".format(normResult.confidence)}")

                    onCardReady(cardBitmap, trackingId)
                    processedCards.add(trackingId)
                }
            }

            // Step 4: Cleanup
            cardTracker.pruneStaleTracks()
            onFrameAnalysis(detections.size)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame #$frameCount: ${e.message}", e)
        }
    }

    /**
     * Clear processed card IDs — allows re-scanning of the same cards.
     * Called on confirm, reject, skip, and back navigation.
     */
    fun clearProcessedCards() {
        Log.d(TAG, "Cleared ${processedCards.size} processed card IDs")
        processedCards.clear()
    }
}
