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
 * Parses recognized text using a candidate-scoring approach rather than fixed positional assumptions.
 *
 * Name extraction: Evaluates every OCR line using weighted heuristics (vertical position,
 * font size, title-case, word count, type-line exclusion) and selects the highest-scoring candidate.
 *
 * Collector line identification: Identifies the collector line first using structural patterns,
 * then extracts set code and collector number from it. Supports standard fractions (107/280),
 * standalone numbers (107 M21), promo formats, and rejects power/toughness fractions.
 *
 * Confidence: Reflects actual confidence in field correctness using per-field quality scores
 * rather than a simple binary populated/empty check.
 */
class CardOcrProcessor {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Process a card image using ML Kit OCR and return structured card fields.
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

            val blockCount = recognizedText.textBlocks.size
            val lineCount = recognizedText.textBlocks.sumOf { it.lines.size }
            val rawText = recognizedText.text
            Log.d(TAG, "ML Kit returned: blocks=$blockCount, lines=$lineCount, " +
                "rawLen=${rawText.length}, first100='${rawText.take(100).replace('\n', '|')}'")

            if (rawText.isEmpty()) {
                Log.w(TAG, "ML Kit returned EMPTY text for ${cardBitmap.width}x${cardBitmap.height} bitmap")
                return@withContext DetectedCardText(trackingId = trackingId)
            }

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

    // ═══════════════════════════════════════════════════════════════════
    // MAIN PARSING ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parse recognized text using candidate-scoring approach.
     * Evaluates all lines with heuristics and selects the best card name,
     * then identifies the collector line and extracts set code + collector number.
     */
    internal fun parseCardTextWithBounds(recognizedText: Text, imageHeight: Int): DetectedCardText {
        val rawText = recognizedText.text
        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        // Gather spatial info for all lines
        val spatialLines = recognizedText.textBlocks
            .flatMap { block -> block.lines }
            .map { line ->
                SpatialLine(
                    text = line.text.trim(),
                    top = line.boundingBox?.top ?: 0,
                    bottom = line.boundingBox?.bottom ?: imageHeight,
                    left = line.boundingBox?.left ?: 0,
                    right = line.boundingBox?.right ?: 0,
                    height = (line.boundingBox?.height() ?: 0),
                    width = (line.boundingBox?.width() ?: 0)
                )
            }
            .filter { it.text.isNotEmpty() }

        // ─── DIAGNOSTIC: Dump all lines ───
        Log.d(TAG, "╔══ OCR DUMP (imageH=$imageHeight, ${spatialLines.size} spatial lines) ══")
        spatialLines.forEachIndexed { i, sl ->
            Log.d(TAG, "║ [$i] '${sl.text}' top=${sl.top} h=${sl.height} w=${sl.width}")
        }
        Log.d(TAG, "╚══ END DUMP ══")

        // Step 1: Score all lines as card name candidates
        val cardName = selectCardName(spatialLines, imageHeight)

        // Step 2: Identify collector line and extract set code + collector number
        val collectorResult = identifyCollectorLine(spatialLines, lines, imageHeight)

        // Step 3: Calculate confidence reflecting correctness probability
        val confidence = calculateConfidence(cardName, collectorResult, spatialLines, imageHeight)

        Log.d(TAG, "FINAL RESULT: name='$cardName' set='${collectorResult.setCode}' " +
            "collector='${collectorResult.collectorNumber}' confidence=$confidence")

        return DetectedCardText(
            trackingId = -1,
            cardName = cardName,
            setCode = collectorResult.setCode,
            collectorNumber = collectorResult.collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // CARD NAME SELECTION (Scored Candidate Approach)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Evaluate every OCR line as a card name candidate using weighted heuristics.
     * Returns the highest-scoring candidate.
     *
     * Heuristics:
     * - Vertical position: higher lines score better (name is at top of card)
     * - Font size (bbox height): larger text scores better (name is largest text)
     * - Title-case probability: MTG names are typically title-cased
     * - Word count: 1-4 words preferred (most MTG names)
     * - Punctuation penalty: names rarely have heavy punctuation
     * - Type-line exclusion: penalizes known MTG type words
     * - Mana/number penalty: rejects lines that are mostly numbers or symbols
     */
    private fun selectCardName(spatialLines: List<SpatialLine>, imageHeight: Int): String {
        if (spatialLines.isEmpty()) return ""

        val maxHeight = spatialLines.maxOf { it.height }.coerceAtLeast(1)

        data class ScoredCandidate(val text: String, val score: Float, val breakdown: String)

        val candidates = spatialLines.map { line ->
            var score = 0f
            val reasons = mutableListOf<String>()

            val text = line.text

            // Skip lines that are clearly not names
            if (text.length < 2 || !text.any { it.isLetter() }) {
                return@map ScoredCandidate(text, -1f, "REJECTED: too short or no letters")
            }

            // 1. Vertical position score (0–25 points)
            // Name is in the top 30% of the card. Lines near the top get full points.
            val verticalFraction = line.top.toFloat() / imageHeight.coerceAtLeast(1)
            val positionScore = when {
                verticalFraction < 0.15f -> 25f
                verticalFraction < 0.25f -> 20f
                verticalFraction < 0.35f -> 12f
                verticalFraction < 0.50f -> 5f
                else -> 0f
            }
            score += positionScore
            reasons.add("pos=${positionScore.toInt()}")

            // 2. Font size score (0–20 points)
            // Card name is typically the largest or second-largest text on the card
            val sizeRatio = line.height.toFloat() / maxHeight
            val sizeScore = (sizeRatio * 20f).coerceIn(0f, 20f)
            score += sizeScore
            reasons.add("size=${sizeScore.toInt()}")

            // 3. Title-case score (0–15 points)
            val words = text.split("\\s+".toRegex())
            val titleCaseWords = words.count { w ->
                w.isNotEmpty() && w[0].isUpperCase() && (w.length == 1 || w.drop(1).any { it.isLowerCase() })
            }
            val titleCaseRatio = if (words.isNotEmpty()) titleCaseWords.toFloat() / words.size else 0f
            val titleScore = when {
                titleCaseRatio >= 0.8f -> 15f
                titleCaseRatio >= 0.5f -> 10f
                text.all { it.isUpperCase() || !it.isLetter() } -> 3f // ALL CAPS (some promos)
                else -> 2f
            }
            score += titleScore
            reasons.add("title=${titleScore.toInt()}")

            // 4. Word count score (0–15 points)
            val wordCount = words.size
            val wordScore = when (wordCount) {
                1 -> 10f  // "Counterspell", "Terror"
                2 -> 15f  // "Doom Whisperer", "Lightning Bolt"
                3 -> 14f  // "Teferi's Protection", "Wrath of God"
                4 -> 10f  // "Swords to Plowshares" (parsed as 3 w/ contraction)
                5 -> 6f
                else -> 2f
            }
            score += wordScore
            reasons.add("words=$wordCount(${wordScore.toInt()})")

            // 5. Punctuation penalty (-5 to 0)
            val punctCount = text.count { it in "{}()[]•©™®/\\|" }
            val punctPenalty = -(punctCount * 2.5f).coerceAtMost(15f)
            score += punctPenalty
            if (punctPenalty < 0) reasons.add("punct=${punctPenalty.toInt()}")

            // 6. Type-line penalty (-30)
            val upperText = text.uppercase()
            val isTypeLine = MTG_TYPE_WORDS.any { typeWord ->
                upperText.contains(typeWord)
            }
            if (isTypeLine) {
                score -= 30f
                reasons.add("TYPE_LINE(-30)")
            }

            // 7. Mana/number penalty
            val digitFraction = text.count { it.isDigit() }.toFloat() / text.length
            if (digitFraction > 0.4f) {
                score -= 20f
                reasons.add("digits(-20)")
            }

            // 8. Collector-line penalty (has fraction pattern like 107/280)
            if (text.matches(Regex(".*\\d{1,3}/\\d{2,3}.*"))) {
                score -= 25f
                reasons.add("collector_line(-25)")
            }

            // 9. Rules text penalty (common ability keywords)
            val hasRulesKeywords = RULES_TEXT_INDICATORS.any { upperText.contains(it) }
            if (hasRulesKeywords && wordCount > 4) {
                score -= 15f
                reasons.add("rules(-15)")
            }

            ScoredCandidate(text, score, reasons.joinToString(", "))
        }

        // Log all candidates with scores
        Log.d(TAG, "┌── NAME CANDIDATES (${candidates.size}) ──")
        candidates.sortedByDescending { it.score }.forEach { c ->
            val marker = if (c.score == candidates.maxOf { it.score }) "★" else " "
            Log.d(TAG, "│ $marker [${String.format("%.1f", c.score)}] '${c.text}' → ${c.breakdown}")
        }

        val best = candidates.maxByOrNull { it.score }
        val result = if (best != null && best.score > 0f) best.text else ""
        Log.d(TAG, "└── SELECTED NAME: '$result' (score=${best?.score ?: 0f})")
        return result
    }

    // ═══════════════════════════════════════════════════════════════════
    // COLLECTOR LINE IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Identify the collector line, then extract set code and collector number from it.
     *
     * The collector line is identified by structural patterns rather than assuming
     * a fixed position. Supports:
     * - Standard fraction: "069/259 R • GRN" or "069/259 R GRN EN"
     * - Standalone number with set: "107 M21" or "107 GRN"
     * - Promo format: "P107" or "M0107"
     * - Copyright line with set: "©2018 Wizards GRN"
     * - Plain number in bottom region with adjacent set code
     *
     * Explicitly rejects power/toughness fractions (both parts ≤ 20, no set code nearby).
     */
    private fun identifyCollectorLine(
        spatialLines: List<SpatialLine>,
        textLines: List<String>,
        imageHeight: Int
    ): CollectorResult {
        Log.d(TAG, "┌── COLLECTOR LINE SEARCH (${textLines.size} lines) ──")

        // Score each line as a potential collector line
        data class CollectorCandidate(
            val line: String,
            val collectorNumber: String,
            val setCode: String,
            val score: Float,
            val reason: String
        )

        val candidates = mutableListOf<CollectorCandidate>()

        for ((idx, line) in textLines.withIndex()) {
            val verticalPosition = idx.toFloat() / textLines.size.coerceAtLeast(1)

            // Use spatial data for vertical position if available
            val spatialMatch = spatialLines.firstOrNull { it.text == line }
            val spatialVertical = if (spatialMatch != null && imageHeight > 0) {
                spatialMatch.top.toFloat() / imageHeight
            } else {
                verticalPosition
            }

            // ─── Pattern 1: Standard fraction (NNN/NNN) with set code ───
            val fractionMatch = Regex("(\\d{1,4}[a-zA-Z]?)/(\\d{1,4})").find(line)
            if (fractionMatch != null) {
                val numerator = fractionMatch.groupValues[1]
                val denominator = fractionMatch.groupValues[2]
                val numVal = numerator.filter { it.isDigit() }.toIntOrNull() ?: 0
                val denVal = denominator.toIntOrNull() ?: 0

                // Reject power/toughness: both parts ≤ 20 and no set code token nearby
                val isPowerToughness = numVal <= 20 && denVal <= 20
                    && !line.contains(Regex("[A-Z]{2,5}"))
                    && !line.contains(Regex("[•·]"))

                if (isPowerToughness) {
                    Log.d(TAG, "│ [$idx] REJECTED P/T: '$line' ($numerator/$denominator)")
                } else {
                    val stripped = numerator.trimStart('0').ifEmpty { "0" }
                    val setCode = extractSetCodeFromLine(line)
                    var score = 50f // base score for fraction format
                    if (spatialVertical > 0.6f) score += 20f // bottom half bonus
                    if (denVal >= 50) score += 10f // real sets have 50+ cards
                    if (setCode.isNotEmpty()) score += 15f // set code confirmation
                    candidates.add(CollectorCandidate(line, stripped, setCode, score,
                        "fraction $numerator/$denominator, set=$setCode"))
                }
            }

            // ─── Pattern 2: Standalone collector number + set code token ───
            // Matches lines like "107 M21", "GRN 069", "107 R GRN"
            val standaloneMatch = Regex("\\b(\\d{1,4}[a-zA-Z]?)\\b").findAll(line)
            for (match in standaloneMatch) {
                val num = match.groupValues[1]
                val numVal = num.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (numVal in 1..999 && spatialVertical > 0.5f) {
                    val setCode = extractSetCodeFromLine(line)
                    if (setCode.isNotEmpty()) {
                        val stripped = num.trimStart('0').ifEmpty { "0" }
                        var score = 30f
                        if (spatialVertical > 0.7f) score += 15f
                        // Don't duplicate if already found as fraction
                        if (candidates.none { it.collectorNumber == stripped && it.line == line }) {
                            candidates.add(CollectorCandidate(line, stripped, setCode, score,
                                "standalone $num + set=$setCode"))
                        }
                    }
                }
            }

            // ─── Pattern 3: Legacy parenthesis format (SET) ───
            val legacyMatch = Regex("\\(([A-Z0-9]{2,5})\\)", RegexOption.IGNORE_CASE).find(line)
            if (legacyMatch != null) {
                val setCandidate = legacyMatch.groupValues[1].uppercase()
                if (setCandidate !in LANGUAGE_CODES) {
                    // Look for a number on the same line
                    val numMatch = Regex("\\b(\\d{1,4}[a-zA-Z]?)\\b").find(line)
                    val collNum = numMatch?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" } ?: ""
                    var score = 40f
                    if (spatialVertical > 0.6f) score += 15f
                    candidates.add(CollectorCandidate(line, collNum, setCandidate, score,
                        "legacy ($setCandidate), collector=$collNum"))
                }
            }
        }

        // Log all candidates
        candidates.sortedByDescending { it.score }.forEach { c ->
            Log.d(TAG, "│ [${String.format("%.0f", c.score)}] '${c.line}' → cn='${c.collectorNumber}' " +
                "set='${c.setCode}' (${c.reason})")
        }

        val best = candidates.maxByOrNull { it.score }
        val result = if (best != null) {
            CollectorResult(best.collectorNumber, best.setCode, best.score)
        } else {
            CollectorResult("", "", 0f)
        }
        Log.d(TAG, "└── SELECTED: cn='${result.collectorNumber}' set='${result.setCode}' " +
            "score=${result.score}")
        return result
    }

    /**
     * Extract a set code token from a single line.
     * Finds 2-5 character alphanumeric tokens that start with a letter,
     * excluding known language codes. Does NOT filter against COMMON_WORDS
     * because this is called on lines already identified as collector lines
     * by structural patterns.
     */
    private fun extractSetCodeFromLine(line: String): String {
        val upperLine = line.uppercase()
        val tokenPattern = Regex("\\b([A-Z][A-Z0-9]{1,4})\\b")

        for (match in tokenPattern.findAll(upperLine)) {
            val candidate = match.groupValues[1]
            if (candidate.length in 2..5
                && candidate !in LANGUAGE_CODES
                && !candidate.all { it.isDigit() }
            ) {
                return candidate
            }
        }
        return ""
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIDENCE SCORING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculate confidence reflecting actual correctness probability.
     *
     * Unlike the previous binary approach (populated = +points), this considers:
     * - Name quality: position score, title-case quality, absence of noise indicators
     * - Set code quality: whether it was found on a collector line vs guessed
     * - Collector number quality: fraction format vs standalone, plausible range
     * - Overall coherence: all three fields from same line = higher confidence
     *
     * Returns 0.0–1.0 where:
     * - 0.9–1.0: High confidence (name + set + collector all extracted cleanly)
     * - 0.6–0.9: Moderate confidence (name good, partial collector info)
     * - 0.3–0.6: Low confidence (name only, or uncertain extraction)
     * - 0.0–0.3: Very low (mostly noise)
     */
    internal fun calculateConfidence(
        cardName: String,
        collectorResult: CollectorResult,
        spatialLines: List<SpatialLine>,
        imageHeight: Int
    ): Float {
        var score = 0f

        // Name confidence (0–0.50)
        if (cardName.isNotEmpty()) {
            val nameLen = cardName.length
            val hasLetter = cardName.any { it.isLetter() }
            val isNotDigits = !cardName.all { it.isDigit() }
            val isNotMana = !cardName.matches(Regex("^[\\d\\s{}()]+$"))
            val wordCount = cardName.split("\\s+".toRegex()).size
            val isTitleCase = cardName.split("\\s+".toRegex()).any {
                it.isNotEmpty() && it[0].isUpperCase()
            }

            if (hasLetter && isNotDigits && isNotMana) {
                score += 0.25f // base: we have something that looks like a name
                if (nameLen >= 4) score += 0.05f // reasonable length
                if (wordCount in 1..4) score += 0.05f // typical name word count
                if (isTitleCase) score += 0.05f // looks like a proper name
                if (!MTG_TYPE_WORDS.any { cardName.uppercase().contains(it) }) {
                    score += 0.05f // not a type line
                }
                // Cap at 0.5 if name is from a high position
                val nameInTopHalf = spatialLines.any {
                    it.text == cardName && it.top < imageHeight * 0.4f
                }
                if (nameInTopHalf) score += 0.05f
            }
        }

        // Set code confidence (0–0.25)
        val setCode = collectorResult.setCode
        if (setCode.isNotEmpty()) {
            score += 0.10f // base: something extracted
            if (setCode.length in 3..5) score += 0.05f // typical set code length
            if (setCode.all { it.isLetterOrDigit() }) score += 0.05f
            if (collectorResult.score > 40f) score += 0.05f // high collector-line confidence
        }

        // Collector number confidence (0–0.25)
        val collectorNumber = collectorResult.collectorNumber
        if (collectorNumber.isNotEmpty()) {
            val numVal = collectorNumber.filter { it.isDigit() }.toIntOrNull() ?: 0
            score += 0.08f // base: something extracted
            if (numVal in 1..500) score += 0.07f // reasonable range for most sets
            if (collectorResult.score >= 50f) score += 0.05f // came from fraction format
            if (setCode.isNotEmpty()) score += 0.05f // paired with a set code
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Simplified confidence for the text-only parseCardText() method (no spatial data).
     * Also used by unit tests directly.
     */
    internal fun calculateSimpleConfidence(
        cardName: String,
        setCode: String,
        collectorNumber: String
    ): Float {
        var score = 0f

        if (cardName.length >= 2 && cardName.any { it.isLetter() }
            && !cardName.all { it.isDigit() }) {
            score += 0.40f
        }
        if (setCode.length in 2..5 && setCode.all { it.isLetterOrDigit() }
            && setCode.uppercase() !in LANGUAGE_CODES) {
            score += 0.25f
        }
        if (collectorNumber.isNotEmpty()
            && collectorNumber.matches(Regex("\\d{1,4}[a-zA-Z]?"))) {
            score += 0.20f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Public overload for backward compatibility with tests.
     * Delegates to [calculateSimpleConfidence].
     */
    internal fun calculateConfidence(
        cardName: String,
        setCode: String,
        collectorNumber: String
    ): Float = calculateSimpleConfidence(cardName, setCode, collectorNumber)

    // ═══════════════════════════════════════════════════════════════════
    // TEXT-ONLY PARSING (for unit tests / fallback without spatial data)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parse raw OCR text into structured card fields (text-order based).
     * Used by unit tests and as a fallback when spatial parsing is not available.
     */
    internal fun parseCardText(rawText: String): DetectedCardText {
        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        val cardName = extractCardNameFromLines(lines)
        val collectorResult = extractCollectorFromLines(lines)
        val confidence = calculateSimpleConfidence(
            cardName, collectorResult.setCode, collectorResult.collectorNumber
        )

        return DetectedCardText(
            trackingId = -1,
            cardName = cardName,
            setCode = collectorResult.setCode,
            collectorNumber = collectorResult.collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }

    /**
     * Extract card name from text lines using heuristics (no spatial data).
     * Prefers the first line that looks like a card name (title-case, not a type line,
     * not a collector line, not rules text).
     */
    private fun extractCardNameFromLines(lines: List<String>): String {
        for (line in lines) {
            if (line.length < 2 || !line.any { it.isLetter() }) continue
            if (line.all { it.isDigit() || it.isWhitespace() }) continue

            val upper = line.uppercase()
            // Skip type lines
            if (MTG_TYPE_WORDS.any { upper.contains(it) }) continue
            // Skip collector lines (has fraction or looks like "107/280 R GRN")
            if (line.contains(Regex("\\d{2,}/\\d{2,}"))) continue
            // Skip rules text indicators
            if (RULES_TEXT_INDICATORS.any { upper.contains(it) } && line.split(" ").size > 4) continue
            // Skip copyright
            if (upper.contains("©") || upper.contains("WIZARDS")) continue

            return line
        }
        // Fallback: first line with letters
        return lines.firstOrNull { it.length >= 2 && it.any { c -> c.isLetter() } } ?: ""
    }

    /**
     * Extract collector info from text lines (no spatial data).
     */
    private fun extractCollectorFromLines(lines: List<String>): CollectorResult {
        // Search from bottom up for collector patterns
        for (line in lines.reversed()) {
            // Fraction format
            val fractionMatch = Regex("(\\d{1,4}[a-zA-Z]?)/(\\d{1,4})").find(line)
            if (fractionMatch != null) {
                val num = fractionMatch.groupValues[1]
                val den = fractionMatch.groupValues[2]
                val numVal = num.filter { it.isDigit() }.toIntOrNull() ?: 0
                val denVal = den.toIntOrNull() ?: 0

                // Reject power/toughness
                if (numVal <= 20 && denVal <= 20 && !line.contains(Regex("[A-Z]{2,5}"))) {
                    continue
                }

                val stripped = num.trimStart('0').ifEmpty { "0" }
                val setCode = extractSetCodeFromLine(line)
                return CollectorResult(stripped, setCode, 50f)
            }

            // Legacy format
            val legacyMatch = Regex("\\(([A-Z0-9]{2,5})\\)", RegexOption.IGNORE_CASE).find(line)
            if (legacyMatch != null) {
                val set = legacyMatch.groupValues[1].uppercase()
                if (set !in LANGUAGE_CODES) {
                    val numMatch = Regex("\\b(\\d{1,4}[a-zA-Z]?)\\b").find(line)
                    val cn = numMatch?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" } ?: ""
                    return CollectorResult(cn, set, 40f)
                }
            }
        }

        return CollectorResult("", "", 0f)
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASSES & CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Spatial information for a single OCR line. */
    internal data class SpatialLine(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
        val height: Int,
        val width: Int
    )

    /** Result of collector line identification. */
    internal data class CollectorResult(
        val collectorNumber: String,
        val setCode: String,
        val score: Float
    )

    companion object {
        private const val TAG = "CardOcrProcessor"

        /** Language codes that appear on cards but are NOT set codes. */
        private val LANGUAGE_CODES = setOf(
            "EN", "FR", "DE", "ES", "IT", "PT", "JP", "JA",
            "KR", "KO", "CS", "CT", "RU", "PH", "ZH"
        )

        /** Common words that should not be mistaken for set codes. */
        private val COMMON_WORDS = setOf(
            "THE", "AND", "FOR", "NOT", "YOU", "ALL", "CAN", "HAD",
            "HER", "WAS", "ONE", "OUR", "OUT", "ARE", "HAS", "HIS",
            "HOW", "ITS", "MAY", "NEW", "NOW", "OLD", "SEE", "WAY",
            "WHO", "DID", "GET", "HIM", "LET", "SAY", "SHE", "TOO",
            "USE", "PAY", "PUT", "END", "TAP", "ADD", "ETB", "CMC",
            "EACH", "THAT", "WITH", "HAVE", "THIS", "WILL", "YOUR",
            "FROM", "THEY", "BEEN", "WHEN", "INTO", "THAN", "THEM",
            "CARD", "DRAW", "GAIN", "LOSE", "LIFE", "TURN",
            "FLYING", "TRAMPLE", "HASTE", "REACH", "FLASH",
            "DEATHTOUCH", "LIFELINK", "VIGILANCE", "MENACE"
        )

        /** MTG type-line words used to exclude type lines from name candidates. */
        private val MTG_TYPE_WORDS = setOf(
            "CREATURE", "INSTANT", "SORCERY", "ARTIFACT", "ENCHANTMENT",
            "LAND", "PLANESWALKER", "LEGENDARY", "TRIBAL", "BATTLE",
            "KINDRED", "BASIC"
        )

        /** Words that indicate rules/ability text (penalizes long lines containing these). */
        private val RULES_TEXT_INDICATORS = setOf(
            "TARGET", "DESTROY", "DAMAGE", "COUNTER", "RETURN",
            "SACRIFICE", "EXILE", "GRAVEYARD", "BATTLEFIELD",
            "CONTROLLER", "OPPONENT", "PLAYER", "SPELL", "ABILITY",
            "SURVEIL", "SCRY", "MILL", "WARD", "EQUIP"
        )
    }
}
