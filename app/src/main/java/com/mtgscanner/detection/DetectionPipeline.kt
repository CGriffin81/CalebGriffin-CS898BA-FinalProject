package com.mtgscanner.detection

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrates detection → tracking → OCR-readiness for each camera frame.
 *
 * Flow per frame:
 * 1. Detect card regions via [CardDetector]
 * 2. Track detections via [CardTracker] (frame-to-frame ID persistence)
 * 3. When a track reaches 3-frame stability, extract the card image and fire [onCardReady]
 * 4. Prune stale tracks and notify the UI via [onFrameAnalysis]
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
    private val processedCards = mutableSetOf<Int>()
    private var calibrated = false
    private var frameCount = 0

    /**
     * Process a single camera frame through detection + tracking.
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
                Log.d(TAG, "Frame #$frameCount: ${frameBitmap.width}x${frameBitmap.height}, processedCards=${processedCards.size}")
            }

            // Step 1: Detect
            val detections = cardDetector.detectCards(frameBitmap)

            // Step 2: Track
            val matchMap = cardTracker.updateTracks(detections)

            // Step 3: Fire callback for stable, unprocessed cards
            for ((detIdx, trackingId) in matchMap) {
                if (trackingId !in processedCards && cardTracker.isStableDetection(trackingId)) {
                    val cardRegion = detections[detIdx]

                    // Expand bounding box by 20% to include card borders and text regions.
                    // The edge-based detection finds the interior (art area) bounded by card
                    // text edges — expanding ensures the name bar and collector line are included.
                    // 20% is needed because the collector line sits at the very bottom edge.
                    val expandFraction = 0.20f
                    val expandX = (cardRegion.width * expandFraction).toInt()
                    val expandY = (cardRegion.height * expandFraction).toInt()
                    val expandedRegion = CardRegion(
                        x = (cardRegion.x - expandX).coerceAtLeast(0),
                        y = (cardRegion.y - expandY).coerceAtLeast(0),
                        width = (cardRegion.width + 2 * expandX).coerceAtMost(frameBitmap.width - (cardRegion.x - expandX).coerceAtLeast(0)),
                        height = (cardRegion.height + 2 * expandY).coerceAtMost(frameBitmap.height - (cardRegion.y - expandY).coerceAtLeast(0)),
                        area = cardRegion.area
                    )

                    val cardBitmap = cardDetector.extractCardImage(frameBitmap, expandedRegion)

                    Log.d(TAG, "Card ready: trackingId=$trackingId, " +
                        "detected=${cardRegion.width}x${cardRegion.height}, " +
                        "expanded=${expandedRegion.width}x${expandedRegion.height}, " +
                        "cropBitmap=${cardBitmap.width}x${cardBitmap.height}")

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
