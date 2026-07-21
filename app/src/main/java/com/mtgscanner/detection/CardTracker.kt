package com.mtgscanner.detection

import kotlin.math.abs

/**
 * Tracks card objects across consecutive frames to prevent duplicate scanning.
 *
 * Uses center-distance matching with a **frame-relative** position threshold:
 * the threshold scales with the frame diagonal so that tracking works correctly
 * regardless of whether the camera delivers 720p, 1080p, or 3000×3000 frames.
 *
 * A tracked card must be detected in at least [STABILITY_FRAMES] consecutive
 * frames before it is considered "stable" and eligible for OCR.
 */
class CardTracker(
    private val frameHistorySize: Int = 10,
    /** Base threshold in pixels at 720p. Scales linearly with frame size. */
    private val baseThreshold: Int = 80
) {
    companion object {
        private const val STABILITY_FRAMES = 3
        private const val MAX_MISSED_FRAMES = 8
        private const val STALE_TIMEOUT_MS = 30_000L
        /** Reference diagonal for threshold scaling (720p = 1280×720). */
        private const val REFERENCE_DIAGONAL = 1468f  // sqrt(1280² + 720²)
    }

    private val trackedCards = mutableMapOf<Int, TrackedCard>()
    private var nextTrackingId = 0
    private var currentThreshold = baseThreshold

    /**
     * Update the position threshold based on the current frame dimensions.
     * Called automatically from [updateTracks] using the first detection's parent frame size.
     * At 2992×2992, the threshold is ~2.9× larger than at 720p.
     */
    fun calibrateThreshold(frameWidth: Int, frameHeight: Int) {
        val diagonal = kotlin.math.sqrt((frameWidth * frameWidth + frameHeight * frameHeight).toFloat())
        currentThreshold = ((baseThreshold * diagonal) / REFERENCE_DIAGONAL).toInt().coerceAtLeast(50)
    }

    /**
     * Update tracker with current frame detections.
     *
     * Matches new detections to existing tracks by center-point distance.
     * Creates new tracks for unmatched detections, increments framesSeen for matches,
     * and removes tracks that have been missed for [MAX_MISSED_FRAMES] consecutive frames.
     *
     * @param currentDetections Card regions detected in the current frame.
     * @return Map from detection index → assigned tracking ID.
     */
    fun updateTracks(currentDetections: List<CardRegion>): Map<Int, Int> {
        val matchMap = mutableMapOf<Int, Int>()
        val unmatched = currentDetections.indices.toMutableSet()

        // Match current detections to existing tracks
        for ((trackId, trackedCard) in trackedCards.toList()) {
            var bestMatch = -1
            var bestDistance = Int.MAX_VALUE

            for (detIdx in unmatched) {
                val det = currentDetections[detIdx]
                val dist = positionDistance(trackedCard.lastRegion, det)

                if (dist < currentThreshold && dist < bestDistance) {
                    bestMatch = detIdx
                    bestDistance = dist
                }
            }

            if (bestMatch >= 0) {
                trackedCard.framesSeen++
                trackedCard.framesMissed = 0  // Reset miss counter on successful match
                trackedCard.lastRegion = currentDetections[bestMatch]
                matchMap[bestMatch] = trackId
                unmatched.remove(bestMatch)
            } else {
                trackedCard.framesMissed++
                if (trackedCard.framesMissed > MAX_MISSED_FRAMES) {
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
     * Check if a tracked card has been detected consistently across [STABILITY_FRAMES] frames.
     */
    fun isStableDetection(trackingId: Int): Boolean {
        val card = trackedCards[trackingId] ?: return false
        return card.framesSeen >= STABILITY_FRAMES
    }

    fun timeSinceFirstSeen(trackingId: Int): Long {
        val card = trackedCards[trackingId] ?: return -1
        return System.currentTimeMillis() - card.firstSeen
    }

    /** Remove tracks older than [STALE_TIMEOUT_MS]. */
    fun pruneStaleTracks() {
        val now = System.currentTimeMillis()
        val removed = trackedCards.entries.removeAll { (_, card) ->
            now - card.firstSeen > STALE_TIMEOUT_MS
        }
    }

    /** Euclidean distance between centers of two card regions. */
    private fun positionDistance(region1: CardRegion, region2: CardRegion): Int {
        val dx = abs(region1.centerX - region2.centerX)
        val dy = abs(region1.centerY - region2.centerY)
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
    }
}

data class TrackedCard(
    val id: Int,
    val firstSeen: Long,
    var lastRegion: CardRegion,
    var framesSeen: Int = 0,
    var framesMissed: Int = 0
)
