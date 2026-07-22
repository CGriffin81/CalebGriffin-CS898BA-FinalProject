package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the rules text region.
 *
 * Expects: Multi-line ability text in smaller font.
 * Region: The text box between type line and P/T (or collector info for non-creatures).
 *
 * This reader does minimal parsing — it returns the raw oracle text as recognized.
 * The primary value is providing the text for Scryfall cross-reference or display.
 */
class RulesOcr {

    companion object {
        private const val TAG = "RulesOcr"
    }

    /**
     * Read rules text from a cropped rules-text bitmap.
     *
     * @param regionBitmap Cropped bitmap containing only the rules text area.
     * @return [RulesOcrResult] with extracted oracle text and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): RulesOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)

        if (rawText.isEmpty()) {
            Log.d(TAG, "No text in rules region")
            return RulesOcrResult(oracleText = "", confidence = 0f, rawText = "")
        }

        // Clean up OCR artifacts — join lines that were split mid-sentence
        val cleaned = cleanRulesText(rawText)
        val confidence = computeConfidence(cleaned)

        Log.d(TAG, "Rules: ${cleaned.length} chars, ${cleaned.count { it == '\n' } + 1} lines " +
            "(conf=${"%.2f".format(confidence)})")

        return RulesOcrResult(oracleText = cleaned, confidence = confidence, rawText = rawText)
    }

    private fun cleanRulesText(text: String): String {
        return text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun computeConfidence(text: String): Float {
        if (text.isEmpty()) return 0f
        if (!text.any { it.isLetter() }) return 0.1f

        var confidence = 0.5f

        // Longer text = more likely correctly recognized rules
        if (text.length > 20) confidence += 0.15f
        if (text.length > 50) confidence += 0.1f

        // Contains MTG keywords → good sign
        val keywords = listOf("target", "damage", "creature", "player", "life",
            "draw", "discard", "exile", "graveyard", "battlefield")
        val hasKeywords = keywords.any { text.lowercase().contains(it) }
        if (hasKeywords) confidence += 0.15f

        return confidence.coerceIn(0f, 1f)
    }
}
