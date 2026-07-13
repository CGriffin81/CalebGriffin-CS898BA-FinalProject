package com.mtgscanner.ocr

import android.graphics.Bitmap

/**
 * Image preprocessing pipeline for OCR optimization.
 * Current implementation keeps the APK installable on 16 KB devices by avoiding
 * native OpenCV prebuilts until a 16 KB compatible native path is available.
 */
class OcrPreprocessor {

    /**
     * Preprocess card image to improve OCR accuracy.
     * @param cardBitmap Input RGB bitmap from DetectionPipeline
     * @return Input bitmap unchanged.
     */
    fun preprocessForOcr(cardBitmap: Bitmap): Bitmap {
        return cardBitmap
    }

    /**
    * Extract and crop specific regions of the card image for targeted OCR.
    * Used when full-card OCR confidence is low.
     *
     * @param cardBitmap Full card bitmap to crop
     * @return CardRegions data class with three cropped bitmaps
     */
    fun extractCardRegions(cardBitmap: Bitmap): CardRegions {
        val height = cardBitmap.height
        val width = cardBitmap.width
        
        // Rough region estimates (adjust based on actual card proportions)
        val nameRegion = Bitmap.createBitmap(cardBitmap, 0, 0, width, (height * 0.15).toInt())
        val typeRegion = Bitmap.createBitmap(cardBitmap, 0, (height * 0.4).toInt(), width, (height * 0.25).toInt())
        val collectorRegion = Bitmap.createBitmap(cardBitmap, 0, (height * 0.85).toInt(), width, (height * 0.15).toInt())
        
        return CardRegions(
            nameRegion = nameRegion,
            typeRegion = typeRegion,
            collectorRegion = collectorRegion
        )
    }

    /**
     * Extracted card regions for targeted OCR processing.
     * Used when full-card confidence is low - each region is recognized independently.
     *
     * @param nameRegion Top ~15% of card with card name (large, bold text)
     * @param typeRegion Middle ~25% with type line and abilities (medium text)
     * @param collectorRegion Bottom ~15% with set code, collector number, rarity (small text)
     */
    data class CardRegions(
        val nameRegion: Bitmap,
        val typeRegion: Bitmap,
        val collectorRegion: Bitmap
    )
}
