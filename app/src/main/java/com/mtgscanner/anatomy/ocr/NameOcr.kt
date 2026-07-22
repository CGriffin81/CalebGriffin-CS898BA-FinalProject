package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the card name region.
 *
 * Expects: Large, bold text in title-case. Typically 1–5 words.
 * Region: Top portion of the card (name bar, left of mana cost).
 *
 * Validation:
 * - Must contain at least one letter
 * - Rejects all-digit results (mana cost fragments)
 * - Prefers the widest text line if multiple detected (name is largest text)
 * - Title-case detection boosts confidence
 */
class NameOcr {

    companion object {
        private const val TAG = "NameOcr"
    }

    /**
     * Read the card name from a cropped name-bar bitmap.
     *
     * @param regionBitmap Cropped bitmap containing only the name bar area.
     * @return [NameOcrResult] with extracted name and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): NameOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)

        if (rawText.isEmpty()) {
            Log.d(TAG, "No text recognized in name region")
            return NameOcrResult(name = "", confidence = 0f, rawText = "")
        }

        val lines = MlKitRecognizer.linesOf(recognized)

        // Select best name candidate:
        // If multiple lines, pick the one most likely to be a card name
        val name = selectBestName(lines, recognized)
        val confidence = computeNameConfidence(name)

        Log.d(TAG, "Name: '$name' (confidence=${"%.2f".format(confidence)}, raw='$rawText')")
        return NameOcrResult(name = name, confidence = confidence, rawText = rawText)
    }

    private fun selectBestName(lines: List<String>, recognized: com.google.mlkit.vision.text.Text?): String {
        if (lines.isEmpty()) return ""
        if (lines.size == 1) return lines[0]

        // Use bounding box width: card name is typically the widest text in the name bar
        if (recognized != null) {
            val allLines = recognized.textBlocks.flatMap { it.lines }
            val widest = allLines.maxByOrNull { it.boundingBox?.width() ?: 0 }
            if (widest != null && widest.text.any { it.isLetter() }) {
                return widest.text.trim()
            }
        }

        // Fallback: first line with letters
        return lines.firstOrNull { it.any { c -> c.isLetter() } } ?: lines[0]
    }

    private fun computeNameConfidence(name: String): Float {
        if (name.isEmpty()) return 0f
        if (!name.any { it.isLetter() }) return 0f
        if (name.all { it.isDigit() || it.isWhitespace() }) return 0.1f

        var confidence = 0.5f

        // Title-case bonus
        val words = name.split("\\s+".toRegex())
        val titleCaseCount = words.count { it.isNotEmpty() && it[0].isUpperCase() }
        if (titleCaseCount.toFloat() / words.size.coerceAtLeast(1) >= 0.7f) {
            confidence += 0.2f
        }

        // Length bonus (typical names are 5–25 chars)
        if (name.length in 3..30) confidence += 0.1f

        // Word count bonus (1–5 words is normal)
        if (words.size in 1..5) confidence += 0.1f

        // Penalty for special characters
        val specialCount = name.count { it in "{}[]()©™®" }
        if (specialCount > 0) confidence -= 0.15f

        return confidence.coerceIn(0f, 1f)
    }
}
