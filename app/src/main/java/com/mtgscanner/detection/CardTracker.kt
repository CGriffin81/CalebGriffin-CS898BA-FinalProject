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
     * Update tracker with current frame detections.
     * Matches new detections to tracked cards; creates new tracks if no match.
     * @param currentDetections Cards detected in this frame.
     * @return Mapping of detection index to tracking ID.
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
     * Check if a card has been seen consistently (not just a single frame).
     * Helps prevent adding cards that are momentarily detected but unstable.
     * @param trackingId Card tracking ID.
     * @return True if card has been seen in >= 3 frames.
     */
    fun isStableDetection(trackingId: Int): Boolean {
        return trackedCards[trackingId]?.framesSeen ?: 0 >= 3
    }

    /**
     * Get time since card was first detected.
     * @param trackingId Card tracking ID.
     * @return Milliseconds since first detection, or -1 if not tracked.
     */
    fun timeSinceFirstSeen(trackingId: Int): Long {
        val card = trackedCards[trackingId] ?: return -1
        return System.currentTimeMillis() - card.firstSeen
    }

    /**
     * Clear old tracks that haven't been seen recently.
     */
    fun pruneStaleTracks() {
        val now = System.currentTimeMillis()
        trackedCards.entries.removeAll { (_, card) ->
            now - card.firstSeen > 30000  // 30 second timeout
        }
    }

    /**
     * Euclidean distance between two card regions.
     */
    private fun positionDistance(region1: CardRegion, region2: CardRegion): Int {
        val dx = abs(region1.x - region2.x)
        val dy = abs(region1.y - region2.y)
        return (kotlin.math.sqrt((dx * dx + dy * dy).toDouble())).toInt()
    }
}

/**
 * Represents a tracked card across multiple frames.
 */
data class TrackedCard(
    val id: Int,
    val firstSeen: Long,
    var lastRegion: CardRegion,
    var framesSeen: Int = 0,
    var framesMissed: Int = 0
)
