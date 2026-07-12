package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.mtgscanner.model.DetectedCardText

/**
 * Orchestrates OCR pipeline: preprocessing, text recognition, and field extraction.
 * Coordinates between image preparation and text parsing.
 */
class OcrPipeline(
    private val ocrProcessor: CardOcrProcessor = CardOcrProcessor(),
    private val preprocessor: OcrPreprocessor = OcrPreprocessor()
) {

    /**
     * Run full OCR pipeline on a card image.
     * @param cardBitmap Card image to recognize.
     * @param trackingId Tracking ID from detection.
     * @return Detected card text with extracted fields.
     */
    suspend fun recognizeCard(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText {
        return try {
            // Preprocess image for better OCR
            val preprocessed = preprocessor.preprocessForOcr(cardBitmap)
            
            // Run OCR on preprocessed image
            val result = ocrProcessor.processCardImage(preprocessed, trackingId)
            
            // If single-pass OCR is weak, optionally try region-focused approach
            if (result.ocrConfidence < 0.6f) {
                val regionResult = recognizeByRegions(cardBitmap, trackingId)
                if (regionResult.ocrConfidence > result.ocrConfidence) {
                    Log.d("OcrPipeline", "Region-based OCR improved confidence from ${result.ocrConfidence} to ${regionResult.ocrConfidence}")
                    return regionResult
                }
            }
            
            result.copy(trackingId = trackingId)
        } catch (e: Exception) {
            Log.e("OcrPipeline", "OCR pipeline failed", e)
            DetectedCardText(
                trackingId = trackingId,
                cardName = "",
                setCode = "",
                collectorNumber = "",
                ocrConfidence = 0f,
                rawOcrText = ""
            )
        }
    }

    /**
     * Region-focused OCR: extract card name, set, and collector from specific card regions.
     * Improves accuracy when full-card OCR is noisy.
     */
    private suspend fun recognizeByRegions(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText {
        return try {
            val regions = preprocessor.extractCardRegions(cardBitmap)
            
            // Recognize each region independently
            val nameResult = ocrProcessor.processCardImage(regions.nameRegion, trackingId)
            val collectorResult = ocrProcessor.processCardImage(regions.collectorRegion, trackingId)
            
            // Combine results
            return DetectedCardText(
                trackingId = trackingId,
                cardName = nameResult.cardName,
                setCode = nameResult.setCode + collectorResult.setCode,  // Could appear in either region
                collectorNumber = collectorResult.collectorNumber,
                ocrConfidence = (nameResult.ocrConfidence + collectorResult.ocrConfidence) / 2f,
                rawOcrText = nameResult.rawOcrText + "\n" + collectorResult.rawOcrText
            )
        } catch (e: Exception) {
            Log.e("OcrPipeline", "Region-based OCR failed", e)
            DetectedCardText(
                trackingId = trackingId,
                cardName = "",
                setCode = "",
                collectorNumber = "",
                ocrConfidence = 0f,
                rawOcrText = ""
            )
        }
    }
}
