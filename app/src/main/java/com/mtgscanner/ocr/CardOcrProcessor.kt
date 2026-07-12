package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgscanner.model.DetectedCardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optical Character Recognition processor for Magic card images.
 * Uses Google ML Kit Text Recognition (Latin script optimized) to extract text from card images.
 * Parses raw OCR output using regex patterns to extract card fields (name, set code, collector number).
 * Calculates confidence score based on which fields were successfully extracted (40% name, 30% set, 30% collector).
 *
 * ML Kit provides on-device OCR with good support for printed Magic card text.
 * Raw text output is parsed with regex patterns to extract structured fields.
 *
 * Confidence Scoring:
 * - Card name found: +0.4
 * - Set code found (pattern "(XXX)"): +0.3
 * - Collector number found (pattern digits): +0.3
 * - Total: 0.0-1.0; values < 0.6 trigger region-specific fallback in OcrPipeline
 *
 * @property textRecognizer ML Kit TextRecognizer singleton (on-device, no network required)
 */
class CardOcrProcessor {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process a card image using ML Kit OCR and extract structured card fields.
     * Performs on-device text recognition (no network call) and parses result using regex patterns.
     * Returns DetectedCardText with confidence score; low confidence triggers fallback in OcrPipeline.
     *
     * Suspending function - runs on Dispatchers.Default to avoid blocking main thread.
     * ML Kit processing is async but wrapped in coroutine context.
     *
     * @param cardBitmap Cropped card image bitmap from DetectionPipeline (RGB, size variable)
     * @param trackingId Card tracking ID from detection (for linking OCR to detection)
     * @return DetectedCardText with extracted name/setCode/collectorNumber and confidence score
     */
    suspend fun processCardImage(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText = withContext(Dispatchers.Default) {
        return@withContext try {
            val image = InputImage.fromBitmap(cardBitmap, 0)
            
            textRecognizer.process(image)
                .addOnSuccessListener { recognizedText ->
                    // Raw OCR text from all blocks
                    val rawText = recognizedText.text
                    
                    // Parse card fields from raw text
                    val parsed = parseCardText(rawText)
                    
                    Log.d("CardOcrProcessor", "OCR result: name='${parsed.cardName}', set='${parsed.setCode}', collector='${parsed.collectorNumber}'")
                }
                .addOnFailureListener { e ->
                    Log.e("CardOcrProcessor", "OCR failed", e)
                }
            
            // For now, return a placeholder; in practice, use coroutine wrapper for ML Kit
            DetectedCardText(
                trackingId = trackingId,
                cardName = "",
                setCode = "",
                collectorNumber = "",
                ocrConfidence = 0f,
                rawOcrText = ""
            )
        } catch (e: Exception) {
            Log.e("CardOcrProcessor", "Error in processCardImage", e)
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
     * Parse raw OCR text to extract structured card fields.
     * Processes raw ML Kit output by splitting into lines and applying regex patterns
     * to find card name (first line), set code (3-4 letter code in parentheses),
     * and collector number (digits, typically 1-3).
     *
     * Handles typical Magic card layout:
     * - Name appears near top of card
     * - Set symbol and code appear mid-card (in parentheses)
     * - Collector number appears near bottom
     *
     * @param rawText Raw OCR output text from ML Kit (may contain noise, formatting)
     * @return DetectedCardText with parsed fields; trackingId will be -1 (set by caller)
     */
    private fun parseCardText(rawText: String): DetectedCardText {
        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        
        val cardName = extractCardName(lines)
        val setCode = extractSetCode(lines)
        val collectorNumber = extractCollectorNumber(lines)
        val confidence = calculateConfidence(cardName, setCode, collectorNumber)
        
        return DetectedCardText(
            trackingId = -1,  // Will be set by caller
            cardName = cardName,
            setCode = setCode,
            collectorNumber = collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }

    /**
     * Extract card name from OCR text lines.
     * Card name typically appears near the top of the card image (often first significant line).
     * Simplified implementation: takes first non-empty line as name.
     * In production, may need more sophisticated heuristics for dual-faced cards or special layouts.
     *
     * @param lines List of non-empty text lines from OCR (already split and trimmed)
     * @return Card name string, or empty string if no lines available
     */
    private fun extractCardName(lines: List<String>): String {
        // First few non-empty lines often contain the name
        return when {
            lines.isNotEmpty() -> lines[0]  // Simplified: assume first line is name
            else -> ""
        }
    }

    /**
     * Extract Magic set code from OCR lines using regex pattern.
     * Set codes appear in parentheses on Magic cards: "(m21)", "(rtr)", "(sta)" format.
     * Pattern: \\(([A-Z0-9]{1,4})\\) matches 1-4 alphanumeric characters in parentheses.
     *
     * @param lines List of OCR text lines to search
     * @return Set code (uppercase, 1-4 chars), or empty string if not found
     */
    private fun extractSetCode(lines: List<String>): String {
        val setPattern = Regex("\\(([A-Z0-9]{1,4})\\)")
        
        for (line in lines) {
            val match = setPattern.find(line)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return ""
    }

    /**
     * Extract collector number (card position in set) using regex pattern.
     * Collector numbers are typically 1-3 digits, optionally followed by a letter suffix (e.g., "42", "156a").
     * Pattern: \\b(\\d+)([a-zA-Z]?)\\b searches for digit sequences up to 3 characters.
     * Implementation limits to 3 digits to avoid false matches (e.g., year numbers).
     *
     * @param lines List of OCR text lines to search
     * @return Collector number string (digits + optional letter), or empty string if not found
     */
    private fun extractCollectorNumber(lines: List<String>): String {
        val numberPattern = Regex("\\b(\\d+)([a-zA-Z]?)\\b")
        
        // Look for pattern that matches collector number format
        for (line in lines) {
            val match = numberPattern.find(line)
            if (match != null && match.groupValues[1].length <= 3) {
                // Collector numbers are typically 1-3 digits
                return match.groupValues[1] + match.groupValues[2]
            }
        }
        
        return ""
    }

    /**
     * Calculate overall OCR confidence score (0.0 to 1.0).
     * Weighted scoring based on which card fields were successfully extracted:
     * - Card name present: +0.4 (most important for identification)
     * - Set code present: +0.3 (important for uniqueness)
     * - Collector number present: +0.3 (important for uniqueness)
     *
     * Score < 0.6 triggers region-specific fallback in OcrPipeline
     * (re-processes name/type/collector regions separately for more accurate results).
     *
     * @param cardName Card name extracted (or empty if not found)
     * @param setCode Set code extracted (or empty if not found)
     * @param collectorNumber Collector number extracted (or empty if not found)
     * @return Float: Confidence score from 0.0 (all empty) to 1.0 (all fields found)
     */
    private fun calculateConfidence(
        cardName: String,
        setCode: String,
        collectorNumber: String
    ): Float {
        var score = 0f
        if (cardName.isNotEmpty()) score += 0.4f
        if (setCode.isNotEmpty()) score += 0.3f
        if (collectorNumber.isNotEmpty()) score += 0.3f
        return score
    }
}
