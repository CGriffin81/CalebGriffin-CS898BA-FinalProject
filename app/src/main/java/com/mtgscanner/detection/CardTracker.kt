package com.mtgscanner.detection

import kotlin.math.abs

/**
 * Tracks card objects across consecutive frames to prevent duplicate scanning.
 * Maintains a history of recently detected cards and assigns consistent IDs.
 */
class CardTracker(
    private val frameHistorySize: Int = 10,
    private val positionThreshold: Int = 50  // pixels
) {

    private val trackedCards = mutableMapOf<Int, TrackedCard>()
    private var nextTrackingId = 0

    /**
     * Update tracker with current frame detections and maintain tracking state.
     * Matches new detections against previously tracked cards using position-based distance.
     * Creates new tracks for unmatched detections and removes stale tracks (not seen for > 5 frames).
     *
     * @param currentDetections List of card regions detected in the current frame
     * @return Map from detection index to assigned tracking ID (Int to Int)
     */
    fun updateTracks(currentDetections: List<CardRegion>): Map<Int, Int> {
        val matchMap = mutableMapOf<Int, Int>()
        val unmatched = currentDetections.indices.toMutableSet()
        
        // Try to match current detections to existing tracks
        for ((trackId, trackedCard) in trackedCards.toList()) {
            var bestMatch = -1
            var bestDistance = Int.MAX_VALUE
            
            for (detIdx in unmatched) {
                val det = currentDetections[detIdx]
                val dist = positionDistance(trackedCard.lastRegion, det)
                
                if (dist < positionThreshold && dist < bestDistance) {
                    bestMatch = detIdx
                    bestDistance = dist
                }
            }
            
            if (bestMatch >= 0) {
                // Update track
                trackedCard.framesSeen++
                trackedCard.lastRegion = currentDetections[bestMatch]
                matchMap[bestMatch] = trackId
                unmatched.remove(bestMatch)
            } else {
                // Card not seen in this frame
                trackedCard.framesMissed++
                if (trackedCard.framesMissed > 5) {
                    trackedCards.remove(trackId)
                }
            }
        }
        
        // Create new tracks for unmatched detections
        for (detIdx in unmatched) {
            val newId = nextTrackingId++
            trackedCards[newId] = TrackedCard(
                id = newId,
                firstSeen = System.currentTimeMillis(),
                lastRegion = currentDetections[detIdx],
                framesSeen = 1
            )
            matchMap[detIdx] = newId
        }
        
        return matchMap
    }

    /**
     * Check if a tracked card has been detected consistently across multiple frames.
     * Enforces stability requirement to prevent false positives from momentary detections.
     * A card must be seen in at least 3 consecutive or near-consecutive frames to be considered stable.
     *
     * @param trackingId The tracking ID of the card to check (assigned by updateTracks)
     * @return Boolean: true if card has been detected >= 3 times, false otherwise
     */
    fun isStableDetection(trackingId: Int): Boolean {
        return trackedCards[trackingId]?.framesSeen ?: 0 >= 3
    }

    /**
     * Get time elapsed since card was first detected in frames.
     * Useful for timing out long-lost cards or analyzing detection persistence.
     *
     * @param trackingId Card tracking ID assigned by updateTracks()
     * @return Milliseconds since first detection of this card, or -1 if tracking ID not found
     */
    fun timeSinceFirstSeen(trackingId: Int): Long {
        val card = trackedCards[trackingId] ?: return -1
        return System.currentTimeMillis() - card.firstSeen
    }

    /**
     * Clear old tracks that haven't been seen recently.
     * Removes tracks older than 30 seconds to prevent memory leaks from stale card detections.
     * Should be called periodically (e.g., every 10 frames) to maintain performance.
     */
    fun pruneStaleTracks() {
        val now = System.currentTimeMillis()
        trackedCards.entries.removeAll { (_, card) ->
            now - card.firstSeen > 30000  // 30 second timeout
        }
    }

    /**
     * Calculate Euclidean distance between the centers of two card regions.
     * Used to match detections across frames — cards detected at similar center positions
     * are likely the same physical card.
     *
     * Uses [CardRegion.centerX] and [CardRegion.centerY] (center of bounding box)
     * instead of top-left corner, preventing spurious new tracks when a card's
     * detected bounding box changes size slightly between frames.
     *
     * @param region1 First card region (previous frame).
     * @param region2 Second card region (current frame).
     * @return Distance in pixels between region centers.
     */
    private fun positionDistance(region1: CardRegion, region2: CardRegion): Int {
        val dx = abs(region1.centerX - region2.centerX)
        val dy = abs(region1.centerY - region2.centerY)
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
    }
}

/**
 * Internal data class representing a card being tracked across multiple frames.
 * Maintains frame history and timing information for duplicate prevention.
 *
 * @param id Unique tracking ID assigned by CardTracker incrementing counter
 * @param firstSeen Timestamp in milliseconds when this card was first detected
 * @param lastRegion Most recent CardRegion bounding box for this tracked card
 * @param framesSeen Count of frames where this card was detected (cumulative, increments on match)
 * @param framesMissed Count of consecutive frames where this card was NOT detected (resets on match)
 */
data class TrackedCard(
    val id: Int,
    val firstSeen: Long,
    var lastRegion: CardRegion,
    var framesSeen: Int = 0,
    var framesMissed: Int = 0
)
