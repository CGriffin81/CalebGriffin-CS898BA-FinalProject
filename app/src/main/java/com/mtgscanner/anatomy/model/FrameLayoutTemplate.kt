package com.mtgscanner.anatomy.model

/**
 * Proportional region definition using normalized coordinates (0.0–1.0).
 * All values are fractions of card width/height, making templates resolution-independent.
 *
 * @param top Top edge as fraction of card height (0.0 = top)
 * @param bottom Bottom edge as fraction of card height (1.0 = bottom)
 * @param left Left edge as fraction of card width (0.0 = left)
 * @param right Right edge as fraction of card width (1.0 = right)
 */
data class ProportionalRect(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float
) {
    val height: Float get() = bottom - top
    val width: Float get() = right - left
}

/**
 * A frame layout template defines the expected proportional positions of all
 * card regions for a specific [FrameType].
 *
 * Templates use normalized 0.0–1.0 coordinates so they are independent of
 * actual image resolution. The [CardAnatomyDetector] converts these to pixel
 * coordinates based on the actual bitmap dimensions.
 *
 * @param frameType Which frame this template describes
 * @param nameBar Expected name bar region (top-left area)
 * @param manaCost Expected mana cost region (top-right area)
 * @param artwork Expected art region (large central area)
 * @param typeLine Expected type line region (middle divider)
 * @param rulesText Expected rules text box region
 * @param setSymbol Expected set symbol region (right side of type line)
 * @param collectorInfo Expected collector information region (bottom)
 * @param artistCredit Expected artist credit region (bottom)
 * @param powerToughness Expected P/T box (bottom-right, null for non-creatures)
 */
data class FrameLayoutTemplate(
    val frameType: FrameType,
    val nameBar: ProportionalRect,
    val manaCost: ProportionalRect,
    val artwork: ProportionalRect,
    val typeLine: ProportionalRect,
    val rulesText: ProportionalRect,
    val setSymbol: ProportionalRect,
    val collectorInfo: ProportionalRect,
    val artistCredit: ProportionalRect,
    val powerToughness: ProportionalRect?  // null = this frame type has no P/T box
)
