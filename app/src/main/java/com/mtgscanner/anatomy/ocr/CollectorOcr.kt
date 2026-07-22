package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * OCR reader specialized for the collector information strip.
 *
 * Receives ONLY the cropped collector region bitmap. Never searches the full card.
 *
 * Supports all collector number formats used by Wizards of the Coast:
 *
 * NUMERIC FORMATS:
 * - Plain number: "107", "1", "280"
 * - Zero-padded: "001", "069", "0107"
 * - Fraction: "069/259", "107/280"
 *
 * SUFFIXED FORMATS:
 * - Alpha variant: "107a", "107b" (alternate art printings)
 * - Star/promo: "107★", "107*", "★107"
 * - Special prefix: "S107" (special), "P107" (promo), "C107" (Commander)
 * - Double-digit prefix: "D107" (draft), "M107" (Mythic Edition)
 *
 * SHOWCASE / SERIALIZED:
 * - Showcase numbering: numbers exceeding set total (e.g., "301/280")
 * - Serialized: "042/500" with unique per-card numbers
 *
 * RARITY is treated separately — never part of the collector number.
 * Language codes are ignored.
 * Copyright text is ignored.
 *
 * The collector number is returned EXACTLY as Scryfall expects it
 * (leading zeros stripped, suffixes preserved, no rarity, no set code).
 */
class CollectorOcr {

    companion object {
        private const val TAG = "CollectorOcr"

        /** Language codes that appear on collector lines but are NOT set codes. */
        private val LANGUAGE_CODES = setOf(
            "EN", "FR", "DE", "ES", "IT", "PT", "JP", "JA",
            "KR", "KO", "CS", "CT", "RU", "PH", "ZH"
        )

        /** Single-letter rarity indicators — separated from collector number. */
        private val RARITY_CODES = setOf("C", "U", "R", "M", "S", "P", "T")

        /**
         * All recognized collector number patterns, ordered by specificity.
         *
         * Scryfall collector_number field is a STRING that may contain:
         * digits, letters (a-z), star (★ or *), and slash (/).
         * Leading zeros are stripped. Suffixes are preserved.
         */
        private val FRACTION_PATTERN = Regex("(\\d{1,4}[a-zA-Z]?)\\s*/\\s*(\\d{1,4})")
        // Prefix pattern: "S107", "P042" — single letter prefix followed by 2+ digits.
        // Excludes "M" prefix to avoid confusion with set codes like "M21".
        private val PREFIX_PATTERN = Regex("\\b([SPCD])(\\d{2,4}[a-zA-Z]?)\\b")
        private val STAR_PREFIX_PATTERN = Regex("[★*](\\d{1,4}[a-zA-Z]?)")
        private val STAR_SUFFIX_PATTERN = Regex("(\\d{1,4}[a-zA-Z]?)[★*]")
        private val PLAIN_NUM_PATTERN = Regex("\\b(\\d{1,4})([a-zA-Z])?\\b")

        /** Set code pattern: 2–5 uppercase alphanumeric starting with a letter. */
        private val SET_CODE_PATTERN = Regex("\\b([A-Z][A-Z0-9]{1,4})\\b")

        /** Legacy parenthesized set code: (LEA), (M21), (2X2) */
        private val LEGACY_SET_PATTERN = Regex("\\(([A-Z0-9]{2,5})\\)", RegexOption.IGNORE_CASE)
    }

    /**
     * Read collector information from a cropped collector-strip bitmap.
     *
     * @param regionBitmap Cropped bitmap containing ONLY the collector info area.
     * @return [CollectorOcrResult] with collector number, set code, rarity, and confidence.
     */
    suspend fun read(regionBitmap: Bitmap): CollectorOcrResult {
        val recognized = MlKitRecognizer.recognize(regionBitmap)
        val rawText = MlKitRecognizer.textOf(recognized)
        return parseText(rawText)
    }

    /**
     * Parse collector information from raw text (testable without ML Kit).
     * This is the core parsing logic separated from the bitmap/ML Kit layer.
     */
    internal fun parseText(rawText: String): CollectorOcrResult {
        if (rawText.isEmpty()) {
            Log.d(TAG, "No text in collector region")
            return CollectorOcrResult("", "", "", 0f, "")
        }

        Log.d(TAG, "Raw collector text: '$rawText'")

        val collectorNumber = parseCollectorNumber(rawText)
        val setCode = parseSetCode(rawText)
        val rarity = parseRarity(rawText, setCode)
        val confidence = computeConfidence(collectorNumber, setCode, rawText)

        Log.d(TAG, "Parsed: cn='$collectorNumber' set='$setCode' rarity='$rarity' " +
            "conf=${"%.2f".format(confidence)}")

        return CollectorOcrResult(
            collectorNumber = collectorNumber,
            setCode = setCode,
            rarity = rarity,
            confidence = confidence,
            rawText = rawText
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // COLLECTOR NUMBER PARSING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse the collector number from the raw text.
     * Tries patterns in order of specificity.
     */
    private fun parseCollectorNumber(rawText: String): String {
        val text = rawText.trim()

        // Pattern 1: Fraction format — "069/259", "42a/280", "301/280"
        FRACTION_PATTERN.find(text)?.let { match ->
            val number = match.groupValues[1]
            return normalizeCollectorNumber(number)
        }

        // Pattern 2: Prefix format — "S107", "P042", "C001"
        PREFIX_PATTERN.find(text)?.let { match ->
            val prefix = match.groupValues[1].uppercase()
            val number = match.groupValues[2]
            if (prefix in setOf("S", "P", "C", "D")) {
                return prefix + normalizeCollectorNumber(number)
            }
        }

        // Pattern 3: Star-prefixed — "★107"
        STAR_PREFIX_PATTERN.find(text)?.let { match ->
            val number = match.groupValues[1]
            return "★" + normalizeCollectorNumber(number)
        }

        // Pattern 4: Star-suffixed — "107★"
        STAR_SUFFIX_PATTERN.find(text)?.let { match ->
            val number = match.groupValues[1]
            return normalizeCollectorNumber(number) + "★"
        }

        // Pattern 5: Plain number with optional letter — "107", "107a"
        for (match in PLAIN_NUM_PATTERN.findAll(text)) {
            val digits = match.groupValues[1]
            val suffix = (match.groupValues.getOrNull(2) ?: "").lowercase()
            val numVal = digits.toIntOrNull() ?: continue

            // Reject years (1900–2099)
            if (numVal in 1900..2099) continue
            // Reject if preceded by copyright symbol
            if (match.range.first > 0 && text[match.range.first - 1] == '©') continue
            // Reject if preceded by a letter (it's part of a set code like "M21")
            if (match.range.first > 0 && text[match.range.first - 1].isLetter()) continue
            // Accept valid collector range
            if (numVal in 1..9999) {
                val normalized = normalizeCollectorNumber(digits)
                return if (suffix.isNotEmpty()) normalized + suffix else normalized
            }
        }

        return ""
    }

    /**
     * Normalize a collector number to Scryfall format.
     * Strips leading zeros but preserves letter suffixes.
     * "069" → "69", "001" → "1", "0" → "0", "042a" → "42a"
     */
    private fun normalizeCollectorNumber(raw: String): String {
        // Separate numeric prefix from any non-digit suffix
        val digits = raw.takeWhile { it.isDigit() }
        val suffix = raw.dropWhile { it.isDigit() }

        val stripped = digits.trimStart('0').ifEmpty { "0" }
        return stripped + suffix
    }

    // ═══════════════════════════════════════════════════════════════
    // SET CODE PARSING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse the set code from the raw text.
     * Looks for 2–5 character alphanumeric tokens that aren't language codes or rarity.
     */
    private fun parseSetCode(rawText: String): String {
        val upper = rawText.uppercase()

        // Strategy 1: Legacy parenthesized format — (LEA), (M21), (2X2)
        LEGACY_SET_PATTERN.find(upper)?.let { match ->
            val code = match.groupValues[1]
            if (code !in LANGUAGE_CODES && code.length >= 2) return code
        }

        // Strategy 2: Standalone token — 2-5 chars, starts with letter
        for (match in SET_CODE_PATTERN.findAll(upper)) {
            val candidate = match.groupValues[1]
            if (candidate.length in 2..5
                && candidate !in LANGUAGE_CODES
                && candidate !in RARITY_CODES
                && !candidate.all { it.isDigit() }
                // Don't match prefixed collector numbers (S107, P042, C001, D107)
                && !(candidate[0] in "SPCD" && candidate.drop(1).all { it.isDigit() })
            ) {
                return candidate
            }
        }

        return ""
    }

    // ═══════════════════════════════════════════════════════════════
    // RARITY PARSING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse the rarity indicator from the raw text.
     * Rarity is a single letter (C/U/R/M) that appears as its own token,
     * not as part of the collector number or set code.
     */
    private fun parseRarity(rawText: String, setCode: String): String {
        val upper = rawText.uppercase()
        // Split on whitespace and common separators
        val tokens = upper.split(Regex("[\\s•·/()©]+")).filter { it.isNotEmpty() }

        for (token in tokens) {
            // Must be exactly a single letter
            if (token.length != 1) continue
            // Must be a valid rarity code
            if (token !in RARITY_CODES) continue
            // Must not BE the set code itself (e.g., single-letter won't match 3-letter set)
            if (token == setCode) continue
            return token
        }
        return ""
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIDENCE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compute confidence based on what was successfully extracted.
     */
    private fun computeConfidence(
        collectorNumber: String,
        setCode: String,
        rawText: String
    ): Float {
        if (rawText.isEmpty()) return 0f

        var confidence = 0.2f // Base: we have some text in the region

        // Collector number found
        if (collectorNumber.isNotEmpty()) {
            confidence += 0.35f
            // Higher confidence if it looks like a standard number
            val numPart = collectorNumber.filter { it.isDigit() }.toIntOrNull()
            if (numPart != null && numPart in 1..500) confidence += 0.1f
        }

        // Set code found
        if (setCode.length in 2..5) {
            confidence += 0.25f
        }

        // Both found together = very high confidence
        if (collectorNumber.isNotEmpty() && setCode.isNotEmpty()) {
            confidence += 0.05f
        }

        return confidence.coerceIn(0f, 1f)
    }
}
