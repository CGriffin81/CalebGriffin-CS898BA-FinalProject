package com.mtgscanner.detection

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrates the complete detection pipeline: segmentation, frame-to-frame tracking, and OCR preparation.
 * Manages frame-by-frame analysis with stability enforcement to prevent duplicate OCR processing.
 * Only passes cards that have been detected stably (3+ frames) to the OCR pipeline.
 *
 * Callbacks:
 * - onCardReady: Fired when a card reaches stability threshold, passed to OCR layer
 * - onFrameAnalysis: Fired after each frame with detection count for UI feedback
 *
 * @param onCardReady Callback invoked with (cardBitmap: Bitmap, trackingId: Int) when card is ready for OCR
 * @param onFrameAnalysis Callback invoked with detection count after each frame analysis
 */
class DetectionPipeline(
    private val onCardReady: (cardBitmap: Bitmap, trackingId: Int) -> Unit,
    private val onFrameAnalysis: (detectionCount: Int) -> Unit
) {

    private val cardDetector = CardDetector()
    private val cardTracker = CardTracker(
        frameHistorySize = 10,
        positionThreshold = 50
    )
    
    private val processedCards = mutableSetOf<Int>()  // Tracking IDs already sent to OCR

    /**
     * Process a single camera frame through the complete detection pipeline.
     * Sequence:
     * 1. Detect cards using OpenCV segmentation (Otsu + morphology + contours)
     * 2. Track detections across frames and assign stable IDs
     * 3. Extract card images and invoke onCardReady callback for stable detections
     * 4. Clean up stale tracking data
     * 5. Update UI with detection count
     *
     * @param frameBitmap Input RGB bitmap from camera CameraX analyzer
     * @throws Exception if OpenCV processing fails (caught and logged)
     */
    fun processFrame(frameBitmap: Bitmap) {
        try {
            // Detect cards in frame
            val detections = cardDetector.detectCards(frameBitmap)
            
            // Update tracking
            val matchMap = cardTracker.updateTracks(detections)
            
            // Process stable detections
            for ((detIdx, trackingId) in matchMap) {
                if (trackingId !in processedCards && cardTracker.isStableDetection(trackingId)) {
                    // Extract and prepare card image for OCR
                    val cardRegion = detections[detIdx]
                    val cardBitmap = cardDetector.extractCardImage(frameBitmap, cardRegion)
                    
                    // Perspective correction TODO
                    // val correctedBitmap = cardDetector.perspectiveCorrect(cardBitmap, cardRegion.contour)
                    
                    // Pass to OCR pipeline
                    onCardReady(cardBitmap, trackingId)
                    processedCards.add(trackingId)
                    
                    Log.d("DetectionPipeline", "Card ready: trackingId=$trackingId, area=${cardRegion.area}")
                }
            }
            
            // Clean up old tracks
            cardTracker.pruneStaleTracks()
            
            // Notify frame analysis result
            onFrameAnalysis(detections.size)
            
        } catch (e: Exception) {
            Log.e("DetectionPipeline", "Error processing frame", e)
        }
    }

    /**
     * Clear the set of processed card tracking IDs.
     * Called when user confirms/rejects a batch of detected cards to allow re-scanning.
     * Enables the same card to be processed again if it re-enters the camera frame.
     */
    fun clearProcessedCards() {
        processedCards.clear()
    }
}
