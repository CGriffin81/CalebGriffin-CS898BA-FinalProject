package com.mtgscanner.anatomy.model

import android.graphics.Rect

/**
 * Semantic region types found on a Magic: The Gathering card.
 * Each represents a distinct functional area with specific content expectations.
 */
enum class RegionType {
    NAME_BAR,
    MANA_COST,
    ARTWORK,
    TYPE_LINE,
    RULES_TEXT,
    SET_SYMBOL,
    COLLECTOR_INFO,
    ARTIST_CREDIT,
    POWER_TOUGHNESS
}

/**
 * A single detected region on a card with its bounding rectangle,
 * confidence level, and semantic type.
 *
 * @param regionType What this region represents semantically
 * @param bounds Pixel-coordinate bounding rectangle within the card image
 * @param confidence 0.0–1.0 indicating how certain the detection is
 */
data class CardRegion(
    val regionType: RegionType,
    val bounds: Rect,
    val confidence: Float
)

/**
 * Complete anatomy of a detected card — all semantic regions with their locations.
 * Not all regions are required to be present (e.g., lands have no P/T).
 *
 * @param regions List of all detected regions
 * @param frameType The classified frame layout used for detection
 * @param imageWidth Width of the source image these regions are relative to
 * @param imageHeight Height of the source image these regions are relative to
 */
data class CardLayout(
    val regions: List<CardRegion>,
    val frameType: FrameType,
    val imageWidth: Int,
    val imageHeight: Int
) {
    /** Find a specific region by type, or null if not detected. */
    fun findRegion(type: RegionType): CardRegion? = regions.find { it.regionType == type }

    /** Check if a specific region was detected. */
    fun hasRegion(type: RegionType): Boolean = regions.any { it.regionType == type }
}
