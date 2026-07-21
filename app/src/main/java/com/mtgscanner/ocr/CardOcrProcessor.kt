package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgscanner.model.DetectedCardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Optical Character Recognition processor for Magic card images.
 * Uses Google ML Kit Text Recognition (Latin script optimized) to extract text from card images.
 * Parses raw OCR output using regex patterns to extract card fields (name, set code, collector number).
 *
 * Async bridge: Uses kotlinx-coroutines-play-services `Task.await()` to suspend the coroutine
 * until ML Kit completes recognition. This replaces the previous fire-and-forget
 * addOnSuccessListener pattern which returned an empty placeholder before OCR completed.
 *
 * Confidence Scoring:
 * - Card name plausible (non-empty, contains letter, not all-digits): +0.5
 * - Set code found (2–5 alphanumeric chars): +0.25
 * - Collector number found (1–3 digits + optional letter suffix): +0.25
 * - Total: 0.0–1.0; values < 0.6 trigger region-specific fallback in OcrPipeline
 *
 * @property textRecognizer ML Kit TextRecognizer singleton (on-device, no network required).
 *   Shared across calls — TextRecognizer is thread-safe and designed for reuse.
 */
class CardOcrProcessor {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Process a card image using ML Kit OCR and return structured card fields.
     *
     * Uses [kotlinx.coroutines.tasks.await] to suspend the calling coroutine until ML Kit
     * completes text recognition. The coroutine resumes with the recognized [com.google.mlkit.vision.text.Text]
     * object, which is then parsed for card name, set code, and collector number.
     *
     * Runs on [Dispatchers.Default] — ML Kit schedules its own work internally
     * (typically to a background thread pool), so this dispatcher just ensures we
     * are not blocking the main thread while awaiting the result.
     *
     * @param cardBitmap Cropped card image bitmap from the detection pipeline (RGB, variable size).
     * @param trackingId Card tracking ID from the detection pipeline (for correlation in logs).
     * @return [DetectedCardText] with extracted name, set code, collector number, confidence, and raw text.
     *   On any exception, returns an empty [DetectedCardText] with `ocrConfidence = 0f`.
     */
    suspend fun processCardImage(
        cardBitmap: Bitmap,
        trackingId: Int
    ): DetectedCardText = withContext(Dispatchers.Default) {
        return@withContext try {
            val image = InputImage.fromBitmap(cardBitmap, 0)

            // Suspend until ML Kit completes — replaces the broken addOnSuccessListener pattern.
            // Task.await() is provided by kotlinx-coroutines-play-services.
            val recognizedText = textRecognizer.process(image).await()

            val result = parseCardText(recognizedText.text).copy(trackingId = trackingId)

            Log.d(
                TAG,
                "OCR trackingId=$trackingId: " +
                    "name='${result.cardName}' " +
                    "set='${result.setCode}' " +
                    "collector='${result.collectorNumber}' " +
                    "confidence=${result.ocrConfidence}"
            )

            result
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed for trackingId=$trackingId: ${e.message}", e)
            DetectedCardText(trackingId = trackingId)
        }
    }

    /**
     * Parse raw OCR text into structured card fields.
     *
     * Splits on newlines, trims each line, and applies regex extraction for each field.
     * The card name is assumed to be the first non-trivial line of text. The set code
     * is searched using the legacy parenthesis pattern `(XXX)`. The collector number is
     * searched from the bottom of the text upward, preferring the fraction format
     * `NNN/NNN` which is the most reliable indicator of the collector line on modern cards.
     *
     * @param rawText Raw string from [com.google.mlkit.vision.text.Text.getText].
     * @return [DetectedCardText] with `trackingId = -1` (caller sets the real ID via `.copy()`).
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
     * Extract the card name from OCR lines.
     *
     * Takes the first non-trivial line — a line that is at least 2 characters long and
     * contains at least one letter. This avoids returning a lone digit (e.g., a mana cost
     * fragment) as the card name when OCR splits text oddly.
     *
     * Limitation: Does not yet use ML Kit's spatial [com.google.mlkit.vision.text.TextBlock.getBoundingBox]
     * to anchor extraction to the top 10% of the card image. That improvement is tracked
     * as P2-02 in the implementation plan.
     *
     * @param lines Non-empty, trimmed lines from the raw OCR text.
     * @return Card name candidate, or empty string if no suitable line found.
     */
    private fun extractCardName(lines: List<String>): String {
        return lines.firstOrNull { line ->
            line.length >= 2 && line.any { it.isLetter() }
        } ?: ""
    }

    /**
     * Extract the Magic set code from OCR lines.
     *
     * Supports the legacy parenthesis format used on cards through approximately 2003:
     * `(LEA)`, `(M21)`, `(2X2)`.
     *
     * Note: Modern cards (post-2003) print the collector line as `042/274 R • M21`
     * without parentheses. Extraction for that format is tracked as P2-04.
     *
     * @param lines Non-empty, trimmed lines from raw OCR text.
     * @return Set code string (as OCR captured it), or empty string if not found.
     */
    private fun extractSetCode(lines: List<String>): String {
        // Legacy format: (LEA), (M21), (2X2) — cards through ~2003
        val legacyPattern = Regex("\\(([A-Z0-9]{2,4})\\)")
        for (line in lines) {
            legacyPattern.find(line)?.let { return it.groupValues[1] }
        }
        return ""
    }

    /**
     * Extract the collector number from OCR lines.
     *
     * Searches from the last line upward (collector info is at the bottom of every MTG card).
     * Prefers the fraction format `NNN/NNN` (e.g., `042/274`) which is the most reliable
     * indicator of the collector line on modern cards. Returns the numerator as the
     * collector number (e.g., `"42"` from `"042/274"`).
     *
     * Falls back to a standalone 1–3 digit number with optional letter suffix
     * (e.g., `"42a"` for variant prints) if no fraction is found in the bottom half.
     *
     * @param lines Non-empty, trimmed lines from raw OCR text.
     * @return Collector number string, or empty string if not found.
     */
    private fun extractCollectorNumber(lines: List<String>): String {
        // Prefer fraction format: "042/274" → returns "42"
        val fractionPattern = Regex("\\b(\\d{1,3})/(\\d{1,3})\\b")
        for (line in lines.reversed()) {
            fractionPattern.find(line)?.let { return it.groupValues[1].trimStart('0').ifEmpty { "0" } }
        }

        // Fallback: standalone 1–3 digit number with optional letter, bottom half only
        val standalonePattern = Regex("\\b(\\d{1,3}[a-zA-Z]?)\\b")
        val bottomHalf = if (lines.size > 2) lines.drop(lines.size / 2) else lines
        for (line in bottomHalf.reversed()) {
            standalonePattern.find(line)?.let { match ->
                val candidate = match.groupValues[1]
                // Require at least one digit; exclude pure letter matches
                if (candidate.any { it.isDigit() }) return candidate
            }
        }

        return ""
    }

    /**
     * Calculate the overall OCR confidence score (0.0–1.0).
     *
     * Awards points only when extracted fields pass a plausibility check:
     * - Card name (+0.5): non-empty, ≥ 2 chars, contains at least one letter, not all-digits.
     * - Set code (+0.25): 2–5 alphanumeric characters.
     * - Collector number (+0.25): matches `\d{1,3}[a-zA-Z]?` exactly.
     *
     * A score < 0.6 triggers the region-based fallback in [OcrPipeline].
     * The name carries the most weight because it is the primary identification signal.
     *
     * @param cardName Extracted card name (may be empty).
     * @param setCode Extracted set code (may be empty).
     * @param collectorNumber Extracted collector number (may be empty).
     * @return Confidence score from 0.0 (nothing found) to 1.0 (all fields found and plausible).
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
        if (nameIsPlausible) score += 0.5f

        val setIsPlausible = setCode.length in 2..5
            && setCode.all { it.isLetterOrDigit() }
        if (setIsPlausible) score += 0.25f

        val collectorIsPlausible = collectorNumber.matches(Regex("\\d{1,3}[a-zA-Z]?"))
        if (collectorIsPlausible) score += 0.25f

        return score
    }

    companion object {
        private const val TAG = "CardOcrProcessor"
    }
}
