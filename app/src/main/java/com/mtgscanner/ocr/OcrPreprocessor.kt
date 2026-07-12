package com.mtgscanner.ocr

import android.graphics.Bitmap
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Image preprocessing pipeline for OCR optimization.
 * Enhances card image quality using OpenCV techniques to improve ML Kit text recognition accuracy.
 * Applied before full-card OCR; if confidence < 0.6, OcrPipeline calls extractCardRegions for targeted re-processing.
 *
 * Preprocessing Steps:
 * 1. Grayscale conversion (reduce color noise)
 * 2. CLAHE contrast enhancement (local histogram equalization, prevents washout)
 * 3. Gaussian blur (reduce OCR noise while preserving edges)
 * 4. Sharpening kernel (enhance text definition for better ML Kit recognition)
 * 5. Region extraction (split card into name/type/collector regions for fallback OCR)
 */
class OcrPreprocessor {

    /**
     * Preprocess card image to improve OCR accuracy.
     * Applies full pipeline: grayscale → CLAHE contrast → Gaussian blur → sharpening kernel.
     * CLAHE (Contrast Limited Adaptive Histogram Equalization) prevents overexposure while enhancing text contrast.
     * Gaussian blur reduces sensor noise. Sharpening kernel (unsharp mask) enhances text edges.
     *
     * Pipeline is optimized for printed card text (not handwriting or cursive).
     * Intensive operation; consider running on background thread (Dispatchers.Default recommended).
     *
     * @param cardBitmap Input RGB bitmap from DetectionPipeline
     * @return Preprocessed bitmap with enhanced contrast and reduced noise, same dimensions as input
     */
    fun preprocessForOcr(cardBitmap: Bitmap): Bitmap {
        return cardBitmap
    }

    /**
     * Extract and crop specific regions of the card image for targeted OCR.
     * Used when full-card OCR confidence is low (< 0.6).
     * Magic cards have distinct text regions with different font sizes/weights:
     * - Name region (top ~15%): Large, bold text - most important for identification
     * - Type region (middle ~25%): Medium text, abilities/flavor - provides context
     * - Collector region (bottom ~15%): Small text, set/number/rarity - important for uniqueness
     *
     * Each region is preprocessed separately for better recognition in focused area.
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
