package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the type line region.
 *
 * Expects: "Legendary Creature — Demon", "Instant", "Artifact — Equipment", etc.
 * Region: Middle divider between art and rules text.
 *
 * Parsing:
 * - Splits on "—" or "-" to separate types from subtypes
 * - Recognizes supertypes: Legendary, Basic, Snow, World
 * - Recognizes card types: Creature, Instant, Sorcery, Artifact, Enchantment, Land, Planeswalker, Battle
 * - Everything after the dash is subtypes
 */
class TypeLineOcr {

    companion object {
        private const val TAG = "TypeLineOcr"

        private val SUPERTYPES = setOf(
            "LEGENDARY", "BASIC", "SNOW", "WORLD", "ONGOING"
        )

        private val CARD_TYPES = setOf(
            "CREATURE", "INSTANT", "SORCERY", "ARTIFACT", "ENCHANTMENT",
            "LAND", "PLANESWALKER", "TRIBAL", "BATTLE", "KINDRED"
        )
    }

    /**
     * Read the type line from a cropped type-line bitmap.
     *
     * @param regionBitmap Cropped bitmap containing only the type line area.
     * @return [TypeLineOcrResult] with parsed type components and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): TypeLineOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)

        if (rawText.isEmpty()) {
            Log.d(TAG, "No text in type line region")
            return TypeLineOcrResult("", emptyList(), emptyList(), emptyList(), 0f, "")
        }

        // Take the first meaningful line (type line is a single line)
        val lines = MlKitRecognizer.linesOf(recognized)
        val typeLine = lines.firstOrNull { it.any { c -> c.isLetter() } } ?: rawText

        // Parse into components
        val (typesPart, subtypesPart) = splitOnDash(typeLine)
        val words = typesPart.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }

        val supertypes = words.filter { it.uppercase() in SUPERTYPES }
        val types = words.filter { it.uppercase() in CARD_TYPES }
        val subtypes = if (subtypesPart.isNotBlank()) {
            subtypesPart.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        } else emptyList()

        val confidence = computeConfidence(types, typeLine)

        Log.d(TAG, "TypeLine: '$typeLine' → super=$supertypes, types=$types, sub=$subtypes (conf=${"%.2f".format(confidence)})")
        return TypeLineOcrResult(
            fullTypeLine = typeLine,
            supertypes = supertypes,
            types = types,
            subtypes = subtypes,
            confidence = confidence,
            rawText = rawText
        )
    }

    private fun splitOnDash(text: String): Pair<String, String> {
        // MTG uses em-dash "—" but OCR might return "-" or "–"
        val dashIdx = text.indexOfFirst { it == '—' || it == '–' }
        return if (dashIdx >= 0) {
            text.substring(0, dashIdx).trim() to text.substring(dashIdx + 1).trim()
        } else if (text.contains(" - ")) {
            val idx = text.indexOf(" - ")
            text.substring(0, idx).trim() to text.substring(idx + 3).trim()
        } else {
            text.trim() to ""
        }
    }

    private fun computeConfidence(types: List<String>, fullLine: String): Float {
        if (fullLine.isEmpty()) return 0f
        // If we recognized a valid MTG card type, confidence is high
        if (types.isNotEmpty()) return 0.85f
        // If the line contains letters but no recognized type, moderate confidence
        if (fullLine.any { it.isLetter() }) return 0.4f
        return 0.1f
    }
}
