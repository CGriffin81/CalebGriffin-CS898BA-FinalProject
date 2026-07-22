package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the power/toughness region.
 *
 * Expects: Two numbers separated by "/" (e.g., "6/6", "2/3").
 * Region: Small box in the bottom-right corner of creature cards.
 *
 * This region does NOT contain collector numbers — those come from [CollectorOcr].
 * Any fraction found here is explicitly power/toughness.
 *
 * Also handles variable P/T (star values, X), and planeswalker loyalty (single number).
 */
class PowerToughnessOcr {

    companion object {
        private const val TAG = "PowerToughnessOcr"

        /** P/T fraction pattern: number/number, star/star, etc. */
        private val PT_PATTERN = Regex("([\\d*X+]+)\\s*/\\s*([\\d*X+]+)")
    }

    /**
     * Read power/toughness from a cropped P/T box bitmap.
     *
     * @param regionBitmap Cropped bitmap containing only the P/T box area.
     * @return [PowerToughnessOcrResult] with extracted power, toughness, and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): PowerToughnessOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)

        if (rawText.isEmpty()) {
            Log.d(TAG, "No text in P/T region")
            return PowerToughnessOcrResult(power = "", toughness = "", confidence = 0f, rawText = "")
        }

        // Look for P/T fraction pattern
        val ptMatch = PT_PATTERN.find(rawText)
        if (ptMatch != null) {
            val power = ptMatch.groupValues[1].trim()
            val toughness = ptMatch.groupValues[2].trim()
            val confidence = computeConfidence(power, toughness)
            Log.d(TAG, "P/T: $power/$toughness (conf=${"%.2f".format(confidence)}, raw='$rawText')")
            return PowerToughnessOcrResult(
                power = power,
                toughness = toughness,
                confidence = confidence,
                rawText = rawText
            )
        }

        // Fallback: might be a planeswalker loyalty (single number)
        val singleNum = rawText.trim().filter { it.isDigit() || it == '*' }
        if (singleNum.isNotEmpty()) {
            Log.d(TAG, "P/T (loyalty?): '$singleNum' (raw='$rawText')")
            return PowerToughnessOcrResult(
                power = singleNum,
                toughness = "",
                confidence = 0.4f,
                rawText = rawText
            )
        }

        Log.d(TAG, "P/T: could not parse from '$rawText'")
        return PowerToughnessOcrResult(power = "", toughness = "", confidence = 0.1f, rawText = rawText)
    }

    private fun computeConfidence(power: String, toughness: String): Float {
        if (power.isEmpty() || toughness.isEmpty()) return 0.2f

        var confidence = 0.7f

        // Standard numeric P/T (most common)
        val pNum = power.toIntOrNull()
        val tNum = toughness.toIntOrNull()
        if (pNum != null && tNum != null) {
            confidence += 0.2f
            // Reasonable P/T range (0–15 for most cards)
            if (pNum in 0..15 && tNum in 0..15) confidence += 0.05f
        }

        // Star values are valid
        if (power == "*" && toughness == "*") confidence += 0.1f

        return confidence.coerceIn(0f, 1f)
    }
}
