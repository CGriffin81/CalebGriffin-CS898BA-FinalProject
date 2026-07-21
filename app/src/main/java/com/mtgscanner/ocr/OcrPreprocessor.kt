package com.mtgscanner.ocr

import android.graphics.Bitmap

/**
 * Image preprocessing pipeline for OCR optimization.
 *
 * Provides:
 * - [preprocessForOcr]: Currently a pass-through (CLAHE enhancement is future work).
 * - [extractCardRegions]: Crops the card into three sub-regions (name, type, collector)
 *   for targeted OCR when full-card recognition has low confidence.
 *
 * Region proportions (P2-06) are based on measured standard MTG card layout (2.5" × 3.5"):
 * - Name bar:       3%–10% from top (bold card title)
 * - Type line:      52%–61% from top (creature type / spell type)
 * - Collector info:  90%–98% from top (set code, collector number, rarity, artist)
 */
class OcrPreprocessor {

    companion object {
        // P2-06: Correct card region proportions (measured from standard MTG card layout)
        private const val NAME_START = 0.03f
        private const val NAME_END = 0.10f
        private const val TYPE_START = 0.52f
        private const val TYPE_END = 0.61f
        private const val COLLECTOR_START = 0.90f
        private const val COLLECTOR_END = 0.98f
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
     * Extract and crop specific regions of the card for targeted OCR (P2-06).
     *
     * Used when full-card OCR confidence is low (< 0.6). Each region is recognized
     * independently by ML Kit, then results are combined by [OcrPipeline.recognizeByRegions].
     *
     * Region proportions are tuned to standard MTG card anatomy:
     * - Name bar (3%–10%): Large, bold card title at the very top.
     * - Type line (52%–61%): "Creature — Dragon" or "Instant" text in the middle divider.
     * - Collector line (90%–98%): Small text with set code, collector number, rarity, artist.
     *
     * @param cardBitmap Full card bitmap to crop.
     * @return [CardRegions] containing three cropped bitmaps.
     */
    fun extractCardRegions(cardBitmap: Bitmap): CardRegions {
        val h = cardBitmap.height
        val w = cardBitmap.width

        val nameY = (h * NAME_START).toInt()
        val nameH = ((h * NAME_END) - nameY).toInt().coerceAtLeast(1)

        val typeY = (h * TYPE_START).toInt()
        val typeH = ((h * TYPE_END) - typeY).toInt().coerceAtLeast(1)

        val collectorY = (h * COLLECTOR_START).toInt()
        val collectorH = ((h * COLLECTOR_END) - collectorY).toInt().coerceAtLeast(1)
            .coerceAtMost(h - collectorY) // prevent overflow past bitmap height

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
     * @param nameRegion Top 3%–10%: card name (large bold text).
     * @param typeRegion Middle 52%–61%: type line and creature type.
     * @param collectorRegion Bottom 90%–98%: set code, collector number, rarity, artist.
     */
    data class CardRegions(
        val nameRegion: Bitmap,
        val typeRegion: Bitmap,
        val collectorRegion: Bitmap
    )
}
