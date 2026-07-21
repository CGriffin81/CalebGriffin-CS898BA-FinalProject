package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * Image preprocessing pipeline for OCR optimization.
 *
 * Provides:
 * - [preprocessForOcr]: Currently a pass-through (CLAHE enhancement is future work).
 * - [extractCardRegions]: Crops the card into three sub-regions (name, type, collector)
 *   for targeted OCR when full-card recognition has low confidence.
 *
 * Region proportions account for the DetectionPipeline's 20% bounding box expansion.
 * The detection algorithm finds the card's interior (art area), then expands 20% on
 * each side. This means the actual card content is inset within the crop:
 *
 * Expanded bitmap layout (approximate with 20% expansion):
 *   0%–14.3%   = expansion padding (background/border)
 *   14.3%–85.7% = actual card content (71.4% of bitmap)
 *   85.7%–100% = expansion padding (background/border)
 *
 * Within the card content region (normalized 0–100%):
 * - Name bar:       3%–12% from card top
 * - Type line:      52%–62% from card top
 * - Collector info:  88%–99% from card top
 *
 * These are translated to bitmap coordinates accounting for the expansion offset.
 */
class OcrPreprocessor {

    companion object {
        private const val TAG = "OcrPreprocessor"

        // The expansion fraction applied by DetectionPipeline
        // (must match DetectionPipeline.expandFraction = 0.20f)
        private const val EXPANSION_FRACTION = 0.20f

        // Region proportions relative to the ACTUAL CARD content (not the expanded bitmap).
        // These are wider than before to ensure text is captured even with slight crop variance.
        private const val NAME_START = 0.0f    // from very top of card content
        private const val NAME_END = 0.14f     // captures name bar + mana cost region
        private const val TYPE_START = 0.50f
        private const val TYPE_END = 0.64f
        private const val COLLECTOR_START = 0.86f
        private const val COLLECTOR_END = 1.0f  // to very bottom of card content
    }

    /**
     * Preprocess card image to improve OCR accuracy.
     *
     * Currently a pass-through — returns the input unchanged.
     * Future work: CLAHE contrast enhancement, sharpening, Gaussian blur.
     *
     * @param cardBitmap Input RGB bitmap from DetectionPipeline.
     * @return The bitmap (currently unchanged).
     */
    fun preprocessForOcr(cardBitmap: Bitmap): Bitmap {
        return cardBitmap
    }

    /**
     * Extract and crop specific regions of the card for targeted OCR.
     *
     * Accounts for the 20% expansion padding added by DetectionPipeline.
     * Translates card-relative proportions to absolute bitmap coordinates.
     *
     * The crops are intentionally generous (wider than the exact text region)
     * to give ML Kit enough context for text recognition. Narrow slivers (< 10%
     * of image height) produce zero text blocks because the ML Kit model needs
     * vertical context around text lines.
     *
     * @param cardBitmap Expanded card bitmap from DetectionPipeline.
     * @return [CardRegions] containing three cropped bitmaps.
     */
    fun extractCardRegions(cardBitmap: Bitmap): CardRegions {
        val h = cardBitmap.height
        val w = cardBitmap.width

        // Calculate where the actual card content starts/ends within the expanded bitmap.
        // With 20% expansion on each side of a region of size S:
        //   expanded_size = S + 2 * (S * 0.20) = S * 1.40
        //   card_start = (0.20 * S) / (1.40 * S) = 0.1429 of expanded bitmap
        //   card_end = (S + 0.20 * S) / (1.40 * S) = 0.8571 of expanded bitmap
        val cardStartFraction = EXPANSION_FRACTION / (1f + 2f * EXPANSION_FRACTION)
        val cardEndFraction = (1f + EXPANSION_FRACTION) / (1f + 2f * EXPANSION_FRACTION)

        // Translate card-relative positions to bitmap-absolute positions
        fun cardToAbsoluteY(cardFraction: Float): Int {
            return ((cardStartFraction + cardFraction * (cardEndFraction - cardStartFraction)) * h).toInt()
        }

        val nameY = cardToAbsoluteY(NAME_START).coerceIn(0, h - 1)
        val nameEndY = cardToAbsoluteY(NAME_END).coerceIn(nameY + 1, h)
        val nameH = (nameEndY - nameY).coerceAtLeast(1)

        val typeY = cardToAbsoluteY(TYPE_START).coerceIn(0, h - 1)
        val typeEndY = cardToAbsoluteY(TYPE_END).coerceIn(typeY + 1, h)
        val typeH = (typeEndY - typeY).coerceAtLeast(1)

        val collectorY = cardToAbsoluteY(COLLECTOR_START).coerceIn(0, h - 1)
        val collectorEndY = cardToAbsoluteY(COLLECTOR_END).coerceIn(collectorY + 1, h)
        val collectorH = (collectorEndY - collectorY).coerceAtLeast(1)

        Log.d(TAG, "extractCardRegions: bitmap=${w}x${h}, " +
            "cardStart=${(cardStartFraction * h).toInt()}, cardEnd=${(cardEndFraction * h).toInt()}, " +
            "nameRegion=y:$nameY h:$nameH, " +
            "typeRegion=y:$typeY h:$typeH, " +
            "collectorRegion=y:$collectorY h:$collectorH")

        val nameRegion = Bitmap.createBitmap(cardBitmap, 0, nameY, w, nameH)
        val typeRegion = Bitmap.createBitmap(cardBitmap, 0, typeY, w, typeH)
        val collectorRegion = Bitmap.createBitmap(cardBitmap, 0, collectorY, w, collectorH)

        return CardRegions(
            nameRegion = nameRegion,
            typeRegion = typeRegion,
            collectorRegion = collectorRegion
        )
    }

    /**
     * Extracted card regions for targeted OCR processing.
     *
     * @param nameRegion Top region: card name (large bold text).
     * @param typeRegion Middle region: type line and creature type.
     * @param collectorRegion Bottom region: set code, collector number, rarity, artist.
     */
    data class CardRegions(
        val nameRegion: Bitmap,
        val typeRegion: Bitmap,
        val collectorRegion: Bitmap
    )
}
