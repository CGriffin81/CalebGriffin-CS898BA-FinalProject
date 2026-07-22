package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the artist credit region.
 *
 * Expects: Small text at the bottom of the card, typically in the format:
 * - "Artist Name" (modern cards — no prefix)
 * - "Illus. Artist Name" (older cards)
 * - May include a paintbrush/pencil icon that OCR misreads
 *
 * Region: Bottom area, usually right side of the collector info line.
 */
class ArtistOcr {

    companion object {
        private const val TAG = "ArtistOcr"

        /** Prefixes that precede the artist name on older cards. */
        private val ARTIST_PREFIXES = listOf("illus.", "illus", "art:", "art")
    }

    /**
     * Read artist name from a cropped artist-credit bitmap.
     *
     * @param regionBitmap Cropped bitmap containing only the artist credit area.
     * @return [ArtistOcrResult] with extracted artist name and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): ArtistOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)

        if (rawText.isEmpty()) {
            Log.d(TAG, "No text in artist region")
            return ArtistOcrResult(artistName = "", confidence = 0f, rawText = "")
        }

        val artistName = extractArtistName(rawText)
        val confidence = computeConfidence(artistName)

        Log.d(TAG, "Artist: '$artistName' (conf=${"%.2f".format(confidence)}, raw='$rawText')")
        return ArtistOcrResult(artistName = artistName, confidence = confidence, rawText = rawText)
    }

    private fun extractArtistName(text: String): String {
        var cleaned = text.trim()

        // Remove known prefixes
        for (prefix in ARTIST_PREFIXES) {
            if (cleaned.lowercase().startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }

        // Remove copyright symbols and years
        cleaned = cleaned.replace(Regex("©\\d{4}.*"), "").trim()
        cleaned = cleaned.replace(Regex("\\d{4}\\s*Wizards.*", RegexOption.IGNORE_CASE), "").trim()

        // Take the first line only (artist name is single line)
        cleaned = cleaned.split('\n').firstOrNull()?.trim() ?: cleaned

        return cleaned
    }

    private fun computeConfidence(name: String): Float {
        if (name.isEmpty()) return 0f
        if (!name.any { it.isLetter() }) return 0.1f

        var confidence = 0.5f

        // Names are typically 2-3 words, title-case
        val words = name.split("\\s+".toRegex())
        if (words.size in 1..4) confidence += 0.2f
        val titleCase = words.count { it.isNotEmpty() && it[0].isUpperCase() }
        if (titleCase.toFloat() / words.size.coerceAtLeast(1) > 0.5f) confidence += 0.15f

        // Penalty for digits (artists don't have numbers in names, usually)
        if (name.any { it.isDigit() }) confidence -= 0.2f

        return confidence.coerceIn(0f, 1f)
    }
}
