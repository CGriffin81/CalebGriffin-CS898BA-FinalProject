package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgscanner.model.DetectedCardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Optical Character Recognition processor for Magic card images.
 *
 * Uses Google ML Kit Text Recognition (Latin script, on-device) to extract text from card images.
 * Parses the recognized [Text] object using both spatial bounding boxes (P2-02) and regex
 * patterns to extract card name, set code, and collector number.
 *
 * Name extraction (P2-02): Uses [Text.TextBlock.boundingBox] to find the text line whose
 * top edge is within the upper 12% of the card image — the Magic card name region.
 *
 * Set code extraction (P2-04): Supports both legacy parenthesis format `(LEA)` and
 * modern collector line format `042/274 R • M21`.
 *
 * Collector number extraction (P2-03): Searches from the bottom of the text upward,
 * preferring the fraction format `NNN/NNN`.
 *
 * Confidence scoring (P2-05): Awards points only for plausible extractions —
 * rejects numeric-only names, validates set code length, and collector number format.
 */
class CardOcrProcessor {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Process a card image using ML Kit OCR and return structured card fields.
     *
     * Uses spatial bounding boxes from the [Text] result to identify the card name
     * by its position in the top region of the image (P2-02), rather than relying
     * solely on text order.
     *
     * @param cardBitmap Cropped card image from the detection pipeline (RGB, variable size).
     * @param trackingId Card tracking ID for correlation in logs.
     * @return [DetectedCardText] with extracted fields and confidence score.
     */
    suspend fun processCardImage(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText = withContext(Dispatchers.Default) {
        return@withContext try {
            Log.d(TAG, "processCardImage: trackingId=$trackingId, " +
                "bitmap=${cardBitmap.width}x${cardBitmap.height}")

            val image = InputImage.fromBitmap(cardBitmap, 0)
            val recognizedText = textRecognizer.process(image).await()

            // Diagnostic: log what ML Kit returned
            val blockCount = recognizedText.textBlocks.size
            val lineCount = recognizedText.textBlocks.sumOf { it.lines.size }
            val rawText = recognizedText.text
            Log.d(TAG, "ML Kit returned: blocks=$blockCount, lines=$lineCount, " +
                "rawLen=${rawText.length}, first100='${rawText.take(100).replace('\n', '|')}'")

            if (rawText.isEmpty()) {
                Log.w(TAG, "ML Kit returned EMPTY text for ${cardBitmap.width}x${cardBitmap.height} bitmap")
                return@withContext DetectedCardText(trackingId = trackingId)
            }

            // Use spatial parsing (P2-02) with image height for bounding box anchoring
            val result = parseCardTextWithBounds(recognizedText, cardBitmap.height)
                .copy(trackingId = trackingId)

            Log.d(TAG, "OCR trackingId=$trackingId: name='${result.cardName}' " +
                "set='${result.setCode}' collector='${result.collectorNumber}' " +
                "confidence=${result.ocrConfidence}")

            result
        } catch (e: Exception) {
            Log.e(TAG, "OCR EXCEPTION for trackingId=$trackingId: ${e.javaClass.simpleName}: ${e.message}", e)
            DetectedCardText(trackingId = trackingId)
        }
    }

    /**
     * Parse recognized text using ML Kit's spatial bounding boxes (P2-02).
     *
     * The card name is identified by finding the text line whose bounding box top edge
     * is within the upper 12% of the image. This is far more reliable than using
     * the first line of raw text, which may be a mana cost or art noise.
     *
     * Falls back to the raw-text `parseCardText()` logic for set code and collector number
     * since those use regex patterns that work well on the flat text representation.
     *
     * @param recognizedText The [Text] object from ML Kit with spatial data.
     * @param imageHeight The height of the source image in pixels (for Y threshold calculation).
     * @return [DetectedCardText] with `trackingId = -1` (caller sets the real ID).
     */
    internal fun parseCardTextWithBounds(recognizedText: Text, imageHeight: Int): DetectedCardText {
        val rawText = recognizedText.text
        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        // ─── DIAGNOSTIC: Dump all ML Kit lines with bounding boxes ───
        Log.d(TAG, "╔══ FULL OCR DUMP (imageHeight=$imageHeight, ${lines.size} lines) ══")
        Log.d(TAG, "║ Raw text: '${rawText.replace('\n', '|')}'")
        var lineIdx = 0
        for (block in recognizedText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                val boxStr = if (box != null) "L=${box.left} T=${box.top} R=${box.right} B=${box.bottom}" else "null"
                Log.d(TAG, "║ [$lineIdx] '${line.text}' → bbox($boxStr)")
                lineIdx++
            }
        }
        Log.d(TAG, "╚══ END OCR DUMP ══")

        // P2-02: Extract name using spatial bounding boxes
        val cardName = extractCardNameSpatial(recognizedText, imageHeight)
            .ifEmpty { extractCardName(lines) } // fallback to text-order if spatial fails

        val setCode = extractSetCode(lines)
        val collectorNumber = extractCollectorNumber(lines)
        val confidence = calculateConfidence(cardName, setCode, collectorNumber)

        Log.d(TAG, "parseCardTextWithBounds RESULT: name='$cardName' set='$setCode' " +
            "collector='$collectorNumber' confidence=$confidence")

        return DetectedCardText(
            trackingId = -1,
            cardName = cardName,
            setCode = setCode,
            collectorNumber = collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }

    /**
     * Extract the card name using ML Kit bounding boxes (P2-02).
     *
     * Finds the text line whose bounding box top edge is within the upper 20% of
     * the card image. This threshold accounts for the 15% expansion padding added by
     * DetectionPipeline — the actual name bar (at ~5% of the real card) appears at
     * approximately 15-16% of the expanded bitmap.
     *
     * If multiple lines qualify, takes the one with the largest bounding box width
     * (the name is typically the widest text in that region).
     *
     * @param recognizedText ML Kit [Text] result with [Text.TextBlock] and [Text.Line] spatial data.
     * @param imageHeight Image height in pixels for computing the 20% threshold.
     * @return Card name string, or empty string if no text found in the name region.
     */
    private fun extractCardNameSpatial(recognizedText: Text, imageHeight: Int): String {
        val nameThresholdY = (imageHeight * 0.20f).toInt()
        Log.d(TAG, "extractCardNameSpatial: imageHeight=$imageHeight, threshold=$nameThresholdY (20%)")

        // Collect all lines with their bounding boxes
        val allLines = recognizedText.textBlocks.flatMap { block -> block.lines }
        val candidateLines = allLines
            .filter { line ->
                val top = line.boundingBox?.top ?: Int.MAX_VALUE
                top < nameThresholdY
            }
            .filter { line ->
                // Must be a plausible name: ≥2 chars, contains a letter
                line.text.trim().length >= 2 && line.text.any { it.isLetter() }
            }

        Log.d(TAG, "  ${allLines.size} total lines, ${candidateLines.size} in name region (<$nameThresholdY)")
        candidateLines.forEach { line ->
            Log.d(TAG, "  candidate: '${line.text}' bbox.top=${line.boundingBox?.top} width=${line.boundingBox?.width()}")
        }

        if (candidateLines.isEmpty()) {
            Log.d(TAG, "  NO spatial candidates — falling back to text-order")
            return ""
        }

        // If multiple lines in the name region, prefer the widest (most likely the card name)
        val bestLine = candidateLines.maxByOrNull { line ->
            line.boundingBox?.width() ?: 0
        }

        val result = bestLine?.text?.trim() ?: ""
        Log.d(TAG, "  SELECTED: '$result'")
        return result
    }

    /**
     * Parse raw OCR text into structured card fields (text-order based).
     *
     * Used by unit tests and as a fallback when spatial parsing is not available.
     * Splits on newlines and applies regex extraction for set code and collector number.
     *
     * @param rawText Raw string from ML Kit.
     * @return [DetectedCardText] with `trackingId = -1`.
     */
    internal fun parseCardText(rawText: String): DetectedCardText {
        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        val cardName = extractCardName(lines)
        val setCode = extractSetCode(lines)
        val collectorNumber = extractCollectorNumber(lines)
        val confidence = calculateConfidence(cardName, setCode, collectorNumber)

        return DetectedCardText(
            trackingId = -1,
            cardName = cardName,
            setCode = setCode,
            collectorNumber = collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }

    /**
     * Extract the card name from text lines (fallback when spatial data unavailable).
     *
     * Takes the first non-trivial line — ≥2 characters and contains at least one letter.
     * Skips lone digits (e.g., mana cost fragments).
     */
    private fun extractCardName(lines: List<String>): String {
        return lines.firstOrNull { line ->
            line.length >= 2 && line.any { it.isLetter() }
        } ?: ""
    }

    /**
     * Extract the Magic set code from OCR lines (P2-04: legacy + modern formats).
     *
     * Strategy:
     * 1. Legacy format: `(LEA)`, `(M21)`, `(2X2)` — parenthesized, searched in all lines.
     * 2. Modern format: `042/274 R • M21` — set code as a 2–5 char alphanumeric token
     *    at the end of the collector line (searched bottom-up). Excludes known language
     *    codes (EN, FR, DE, etc.) that appear in the same region.
     * 3. Standalone token: Any 2-5 char token matching [A-Z][A-Z0-9]{1,4} in the bottom
     *    3 lines, even without a fraction. This handles cases where the crop doesn't
     *    capture the full collector line but still has the set code visible.
     *
     * @param lines Non-empty, trimmed lines from raw OCR text.
     * @return Set code string, or empty string if not found.
     */
    internal fun extractSetCode(lines: List<String>): String {
        Log.d(TAG, "extractSetCode: examining ${lines.size} lines")

        // Strategy 1: Legacy parenthesis format — (LEA), (M21), (2X2)
        val legacyPattern = Regex("\\(([A-Z0-9]{2,5})\\)", RegexOption.IGNORE_CASE)
        for (line in lines) {
            legacyPattern.find(line.uppercase())?.let { match ->
                val candidate = match.groupValues[1]
                if (candidate !in LANGUAGE_CODES) {
                    Log.d(TAG, "  setCode FOUND (legacy): '$candidate' in line '$line'")
                    return candidate
                }
                Log.d(TAG, "  setCode REJECTED (language code): '$candidate' in line '$line'")
            }
        }
        Log.d(TAG, "  Strategy 1 (legacy): no match")

        // Strategy 2: Modern format — search bottom lines for isolated 2-5 char token
        // Modern collector lines look like: "042/274 R • M21" or "EN 042/274 M21 ©2020"
        val modernPattern = Regex("\\b([A-Z][A-Z0-9]{1,4})\\b")
        val bottom3 = lines.reversed().take(3)
        Log.d(TAG, "  Strategy 2 (modern): checking bottom ${bottom3.size} lines for fraction+token")
        for (line in bottom3) {
            val upperLine = line.uppercase()
            val hasFraction = upperLine.contains(Regex("\\d{1,3}/\\d{1,3}"))
            if (!hasFraction) {
                Log.d(TAG, "    SKIP (no fraction): '$line'")
                continue
            }
            Log.d(TAG, "    HAS FRACTION: '$line'")

            // Find all token candidates on this line
            modernPattern.findAll(upperLine).forEach { match ->
                val candidate = match.groupValues[1]
                if (candidate.length in 2..5
                    && candidate !in LANGUAGE_CODES
                    && !candidate.all { it.isDigit() }
                ) {
                    Log.d(TAG, "  setCode FOUND (modern): '$candidate' in line '$line'")
                    return candidate
                }
                Log.d(TAG, "    token REJECTED: '$candidate' (len=${candidate.length}, " +
                    "isLang=${candidate in LANGUAGE_CODES}, allDigit=${candidate.all { it.isDigit() }})")
            }
        }
        Log.d(TAG, "  Strategy 2 (modern): no match")

        // Strategy 3: Standalone set code token in bottom lines (no fraction required).
        // Handles cases where the crop captures set code text but not the full collector line.
        // Requires 3+ chars to reduce false positives from creature type abbreviations.
        val standaloneSetPattern = Regex("\\b([A-Z][A-Z0-9]{2,4})\\b")
        Log.d(TAG, "  Strategy 3 (standalone): checking bottom ${bottom3.size} lines")
        for (line in bottom3) {
            val upperLine = line.uppercase()
            standaloneSetPattern.findAll(upperLine).forEach { match ->
                val candidate = match.groupValues[1]
                if (candidate.length in 3..5
                    && candidate !in LANGUAGE_CODES
                    && candidate !in COMMON_WORDS
                    && !candidate.all { it.isDigit() }
                ) {
                    Log.d(TAG, "  setCode FOUND (standalone): '$candidate' in line '$line'")
                    return candidate
                }
                Log.d(TAG, "    standalone REJECTED: '$candidate' (isLang=${candidate in LANGUAGE_CODES}, " +
                    "isCommon=${candidate in COMMON_WORDS})")
            }
        }
        Log.d(TAG, "  Strategy 3 (standalone): no match")

        Log.w(TAG, "  extractSetCode: ALL STRATEGIES FAILED for ${lines.size} lines")
        return ""
    }

    /**
     * Extract the collector number from OCR lines (P2-03: bottom-up + fraction preference).
     *
     * Searches from the last line upward. Prefers the fraction format `NNN/NNN`
     * (e.g., `042/274`). Also supports the letter-suffixed variant `42a/280`.
     * Returns the numerator with leading zeros stripped.
     *
     * Falls back to a standalone 1–3 digit number in the bottom half of the text.
     */
    internal fun extractCollectorNumber(lines: List<String>): String {
        Log.d(TAG, "extractCollectorNumber: examining ${lines.size} lines (bottom-up)")

        // Prefer fraction format with optional letter suffix: "042/274" or "42a/280"
        val fractionPattern = Regex("\\b(\\d{1,3}[a-zA-Z]?)/(\\d{1,3})\\b")
        for (line in lines.reversed()) {
            fractionPattern.find(line)?.let { match ->
                val numerator = match.groupValues[1]
                val denominator = match.groupValues[2]
                // Strip leading zeros from numeric-only part, keep letter suffix
                val stripped = numerator.trimStart('0').ifEmpty { "0" }
                Log.d(TAG, "  collector FOUND (fraction): '$stripped' from '$numerator/$denominator' in line '$line'")
                return stripped
            }
        }
        Log.d(TAG, "  No fraction pattern found in any line")

        // Fallback: standalone 1–3 digit number with optional letter, bottom half only
        val standalonePattern = Regex("\\b(\\d{1,3}[a-zA-Z]?)\\b")
        val bottomHalf = if (lines.size > 2) lines.drop(lines.size / 2) else lines
        Log.d(TAG, "  Fallback: checking bottom half (${bottomHalf.size} lines)")
        for (line in bottomHalf.reversed()) {
            standalonePattern.find(line)?.let { match ->
                val candidate = match.groupValues[1]
                if (candidate.any { it.isDigit() }) {
                    Log.d(TAG, "  collector FOUND (standalone): '$candidate' in line '$line'")
                    return candidate
                }
            }
        }

        Log.w(TAG, "  extractCollectorNumber: NOTHING FOUND in ${lines.size} lines")
        return ""
    }

    /**
     * Calculate OCR confidence score (0.0–1.0) with plausibility validation (P2-05).
     *
     * Awards:
     * - Card name (+0.5): ≥2 chars, contains letter, not all-digits, not all-uppercase-single-word
     *   shorter than 2 chars.
     * - Set code (+0.25): 2–5 alphanumeric characters, not a known language code.
     * - Collector number (+0.25): matches `\d{1,3}[a-zA-Z]?` exactly.
     *
     * Score < 0.6 triggers region-based fallback in [OcrPipeline].
     */
    internal fun calculateConfidence(
        cardName: String,
        setCode: String,
        collectorNumber: String
    ): Float {
        var score = 0f

        val nameIsPlausible = cardName.length >= 2
            && cardName.any { it.isLetter() }
            && !cardName.all { it.isDigit() }
            && !cardName.matches(Regex("^[\\d\\s]+$"))  // reject strings like "3 2" (mana costs)
        if (nameIsPlausible) score += 0.5f

        val setIsPlausible = setCode.length in 2..5
            && setCode.all { it.isLetterOrDigit() }
            && setCode.uppercase() !in LANGUAGE_CODES
        if (setIsPlausible) score += 0.25f

        val collectorIsPlausible = collectorNumber.matches(Regex("\\d{1,3}[a-zA-Z]?"))
        if (collectorIsPlausible) score += 0.25f

        return score
    }

    companion object {
        private const val TAG = "CardOcrProcessor"

        /** Language codes that appear on cards but are NOT set codes. */
        private val LANGUAGE_CODES = setOf(
            "EN", "FR", "DE", "ES", "IT", "PT", "JP", "JA",
            "KR", "KO", "CS", "CT", "RU", "PH", "ZH"
        )

        /** Common English words/abbreviations that could match set code patterns
         *  but are NOT set codes. Prevents false positives from creature type text. */
        private val COMMON_WORDS = setOf(
            "THE", "AND", "FOR", "NOT", "YOU", "ALL", "CAN", "HAD",
            "HER", "WAS", "ONE", "OUR", "OUT", "ARE", "HAS", "HIS",
            "HOW", "ITS", "MAY", "NEW", "NOW", "OLD", "SEE", "WAY",
            "WHO", "DID", "GET", "HIM", "LET", "SAY", "SHE", "TOO",
            "USE", "PAY", "PUT", "END", "TAP", "ADD", "ETB", "CMC",
            "EACH", "THAT", "WITH", "HAVE", "THIS", "WILL", "YOUR",
            "FROM", "THEY", "BEEN", "WHEN", "INTO", "THAN", "THEM",
            "CARD", "DRAW", "GAIN", "LOSE", "LIFE", "TURN"
        )
    }
}
