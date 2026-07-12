package com.mtgscanner.detection

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrates the detection pipeline: segmentation, tracking, and OCR preparation.
 * Manages frame-by-frame analysis and ensures only stable detections are passed to OCR.
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
     * Process a single camera frame.
     * @param frameBitmap Input frame from camera.
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
     * Clear processed cards when user confirms/rejects batch.
     */
    fun clearProcessedCards() {
        processedCards.clear()
    }
}
