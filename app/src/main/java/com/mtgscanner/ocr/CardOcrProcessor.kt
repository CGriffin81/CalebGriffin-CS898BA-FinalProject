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
 * Optical Character Recognition using Google ML Kit.
 * Extracts text from card images and parses card metadata.
 */
class CardOcrProcessor {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process a card image and extract text.
     * @param cardBitmap Cropped card image.
     * @param trackingId Tracking ID from detection.
     * @return Detected card text with parsed fields.
     */
    suspend fun processCardImage(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText = withContext(Dispatchers.Default) {
        return@withContext try {
            val image = InputImage.fromBitmap(cardBitmap)
            
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
     * Parse raw OCR text to extract card fields.
     * Handles typical Magic card layout: name at top, set/collector at bottom.
     * @param rawText Raw OCR output.
     * @return Parsed DetectedCardText.
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
     * Extract card name from OCR lines.
     * Card name typically appears near the top of the card.
     */
    private fun extractCardName(lines: List<String>): String {
        // First few non-empty lines often contain the name
        return when {
            lines.isNotEmpty() -> lines[0]  // Simplified: assume first line is name
            else -> ""
        }
    }

    /**
     * Extract set code (3-4 letter code in parentheses).
     * Example: "(2X2)", "(RTR)", "(STA)"
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
     * Extract collector number.
     * Typically a number optionally followed by letter (e.g., "42", "156a").
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
     * Calculate overall OCR confidence (0.0 to 1.0).
     * Higher if all three fields were extracted.
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
