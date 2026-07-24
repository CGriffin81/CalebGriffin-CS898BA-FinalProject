package com.mtgscanner.detection

/**
 * A detection candidate — either accepted or rejected with an explanation.
 * Every region the detector evaluates produces one of these, ensuring
 * no decision is made silently.
 */
data class DetectionCandidate(
    val region: CardRegion,
    val accepted: Boolean,
    val reason: String,
    val aspectRatio: Float,
    val fillRatio: Float,
    val areaFraction: Float
)
