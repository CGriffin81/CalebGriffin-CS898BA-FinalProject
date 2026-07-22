package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the collector information region.
 *
 * Expects: Small text at the bottom of the card containing:
 * - Collector number (e.g., "069/259", "107", "42a")
 * - Set code (e.g., "GRN", "M21", "LEA")
 * - Rarity indicator (C, U, R, M)
 * - Language code (EN, FR, etc. — ignored)
 * - Copyright line (©20XX Wizards — ignored)
 *
 * Supported formats:
 * - Standard fraction: "069/259 R • GRN"
 * - Modern compact: "069/259 R GRN EN"
 * - Legacy parenthesis: "(LEA) 1/302"
 * - Standalone: "107 M21"
 *
 * Does NOT interpret power/toughness fractions (those come from a different region).
 */
class CollectorOcr {

    companion object {
        private const val TAG = "CollectorOcr"

        private val LANGUAGE_CODES = setOf(
            "EN", "FR", "DE", "ES", "IT", "PT", "JP", "JA",
            "KR", "KO", "CS", "CT", "RU", "PH", "ZH"
        )

        private val RARITY_CODES = setOf("C", "U", "R", "M", "S", "P", "T")

        /** Fraction pattern: 069/259 or 42a/280 */
        private val FRACTION_PATTERN = Regex("(\\d{1,4}[a-zA-Z]?)/(\\d{1,4})")

        /** Set code pattern: 2-5 uppercase alphanumeric starting with letter */
        private val SET_CODE_PATTERN = Regex("\\b([A-Z][A-Z0-9]{1,4})\\b")

        /** Legacy parenthesis: (LEA), (M21) */
        private val LEGACY_SET_PATTERN = Regex("\\(([A-Z0-9]{2,5})\\)")

        /** Standalone number: 107, 42a */
        private val STANDALONE_NUM_PATTERN = Regex("\\b(\\d{1,4}[a-zA-Z]?)\\b")
    }

    /**
     * Read collector information from a cropped collector-region bitmap.
     *
     * @param regionBitmap Cropped bitmap containing only the collector info area.
     * @return [CollectorOcrResult] with parsed collector number, set code, rarity, and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): CollectorOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)

        if (rawText.isEmpty()) {
            Log.d(TAG, "No text in collector region")
            return CollectorOcrResult("", "", "", 0f, "")
        }

        val upperText = rawText.uppercase()

        // Extract collector number
        val collectorNumber = extractCollectorNumber(upperText)

        // Extract set code
        val setCode = extractSetCode(upperText)

        // Extract rarity
        val rarity = extractRarity(upperText, setCode)

        val confidence = computeConfidence(collectorNumber, setCode)

        Log.d(TAG, "Collector: cn='$collectorNumber' set='$setCode' rarity='$rarity' " +
            "(conf=${"%.2f".format(confidence)}, raw='${rawText.take(60)}')")

        return CollectorOcrResult(
            collectorNumber = collectorNumber,
            setCode = setCode,
            rarity = rarity,
            confidence = confidence,
            rawText = rawText
        )
    }

    private fun extractCollectorNumber(text: String): String {
        // Prefer fraction format: 069/259
        FRACTION_PATTERN.find(text)?.let { match ->
            val numerator = match.groupValues[1]
            return numerator.trimStart('0').ifEmpty { "0" }
        }

        // Fallback: standalone number (must not be a year like 2018, 2020)
        for (match in STANDALONE_NUM_PATTERN.findAll(text)) {
            val candidate = match.groupValues[1]
            val numericPart = candidate.filter { it.isDigit() }.toIntOrNull() ?: continue
            // Reject years and very large numbers
            if (numericPart in 1..999 && numericPart !in 2000..2099) {
                return candidate.trimStart('0').ifEmpty { "0" }
            }
        }

        return ""
    }

    private fun extractSetCode(text: String): String {
        // Strategy 1: Legacy parenthesis format
        LEGACY_SET_PATTERN.find(text)?.let { match ->
            val code = match.groupValues[1]
            if (code !in LANGUAGE_CODES) return code
        }

        // Strategy 2: Standalone token (2-5 chars, starts with letter, not a language code)
        for (match in SET_CODE_PATTERN.findAll(text)) {
            val candidate = match.groupValues[1]
            if (candidate.length in 2..5
                && candidate !in LANGUAGE_CODES
                && candidate !in RARITY_CODES
                && !candidate.all { it.isDigit() }
            ) {
                return candidate
            }
        }

        return ""
    }

    private fun extractRarity(text: String, setCode: String): String {
        // Look for single-letter rarity indicator, but not part of the set code
        val tokens = text.split(Regex("[\\s•·/()]+")).filter { it.isNotEmpty() }
        for (token in tokens) {
            if (token.length == 1 && token in RARITY_CODES && token != setCode) {
                return token
            }
        }
        return ""
    }

    private fun computeConfidence(collectorNumber: String, setCode: String): Float {
        var confidence = 0f
        if (collectorNumber.isNotEmpty()) confidence += 0.5f
        if (setCode.length in 2..5) confidence += 0.35f
        if (collectorNumber.isNotEmpty() && setCode.isNotEmpty()) confidence += 0.1f
        return confidence.coerceIn(0f, 1f)
    }
}
