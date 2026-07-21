package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.mtgscanner.model.DetectedCardText

/**
 * Orchestrates the complete OCR pipeline for card text recognition.
 * Coordinates preprocessing (image enhancement, noise reduction) and text recognition,
 * with fallback to region-focused recognition if single-pass confidence is below 0.6.
 *
 * @param ocrProcessor CardOcrProcessor instance for running OCR on images; default creates new instance
 * @param preprocessor OcrPreprocessor instance for image preparation; default creates new instance
 */
class OcrPipeline(
    private val ocrProcessor: CardOcrProcessor = CardOcrProcessor(),
    private val preprocessor: OcrPreprocessor = OcrPreprocessor()
) {

    /**
     * Execute the full OCR pipeline on a card bitmap.
     * Preprocesses the image for better text recognition, runs OCR, and extracts card fields (name, set, collector number).
     * If initial OCR confidence is below 0.6, attempts region-focused OCR as fallback for improvement.
     * All errors are caught and logged; returns empty DetectedCardText on failure.
     *
     * @param cardBitmap Card image bitmap to recognize, expected RGB format
     * @param trackingId Tracking identifier from the detection pipeline (used for logging/correlation)
     * @return DetectedCardText object containing recognized card name, set code, collector number, confidence score, and raw OCR text
     */
    suspend fun recognizeCard(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText {
        Log.d("OcrPipeline", "recognizeCard called: trackingId=$trackingId, " +
            "bitmap=${cardBitmap.width}x${cardBitmap.height}, " +
            "config=${cardBitmap.config}, recycled=${cardBitmap.isRecycled}")

        return try {
            // Preprocess image for better OCR
            val preprocessed = preprocessor.preprocessForOcr(cardBitmap)
            
            // Run OCR on preprocessed image
            val result = ocrProcessor.processCardImage(preprocessed, trackingId)

            Log.d("OcrPipeline", "Full-card OCR result: name='${result.cardName}' " +
                "set='${result.setCode}' collector='${result.collectorNumber}' " +
                "confidence=${result.ocrConfidence} rawLen=${result.rawOcrText.length}")
            
            // If single-pass OCR is weak, optionally try region-focused approach
            if (result.ocrConfidence < 0.6f) {
                Log.d("OcrPipeline", "Confidence < 0.6, trying region-based fallback...")
                val regionResult = recognizeByRegions(cardBitmap, trackingId)
                Log.d("OcrPipeline", "Region OCR result: name='${regionResult.cardName}' " +
                    "confidence=${regionResult.ocrConfidence}")
                if (regionResult.ocrConfidence > result.ocrConfidence) {
                    Log.d("OcrPipeline", "Region-based OCR improved confidence " +
                        "${result.ocrConfidence} → ${regionResult.ocrConfidence}")
                    return regionResult
                }
            }
            
            result.copy(trackingId = trackingId)
        } catch (e: Exception) {
            Log.e("OcrPipeline", "OCR pipeline EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
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
     * Perform region-focused OCR as a fallback when full-card OCR has low confidence.
     * Divides the card into distinct regions (name region, collector region) and processes each independently.
     * Combines results by preferring collector-region data for set/collector fields.
     * Averaged confidence score across regions provides more robust recognition.
     *
     * @param cardBitmap Card image to process by regions
     * @param trackingId Tracking identifier for logging correlation
     * @return DetectedCardText combining results from individual region recognition
     */
    private suspend fun recognizeByRegions(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText {
        return try {
            val regions = preprocessor.extractCardRegions(cardBitmap)

            Log.d("OcrPipeline", "Region crops: name=${regions.nameRegion.width}x${regions.nameRegion.height}, " +
                "collector=${regions.collectorRegion.width}x${regions.collectorRegion.height}")

            // Recognize each region independently
            val nameResult = ocrProcessor.processCardImage(regions.nameRegion, trackingId)
            val collectorResult = ocrProcessor.processCardImage(regions.collectorRegion, trackingId)

            Log.d("OcrPipeline", "Region name OCR: name='${nameResult.cardName}' " +
                "set='${nameResult.setCode}' rawLen=${nameResult.rawOcrText.length}")
            Log.d("OcrPipeline", "Region collector OCR: collector='${collectorResult.collectorNumber}' " +
                "set='${collectorResult.setCode}' rawLen=${collectorResult.rawOcrText.length}")

            // Combine results — prefer collector region for set/collector fields
            return DetectedCardText(
                trackingId = trackingId,
                cardName = nameResult.cardName.ifEmpty { collectorResult.cardName },
                setCode = collectorResult.setCode.ifEmpty { nameResult.setCode },
                collectorNumber = collectorResult.collectorNumber.ifEmpty { nameResult.collectorNumber },
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
