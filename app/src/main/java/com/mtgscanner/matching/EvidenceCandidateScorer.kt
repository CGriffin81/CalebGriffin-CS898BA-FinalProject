package com.mtgscanner.matching

import android.util.Log
import com.mtgscanner.model.CardMatchCandidate
import com.mtgscanner.model.ScryfallCard

/**
 * Evidence-based candidate scoring system.
 *
 * Treats OCR output as probabilistic evidence rather than truth.
 * Each field contributes to a candidate's score proportional to:
 * 1. The WEIGHT of that field (name is most important)
 * 2. The CONFIDENCE of the OCR extraction (uncertain fields contribute less)
 * 3. The MATCH QUALITY (how well the candidate matches the evidence)
 *
 * Key principles:
 * - Card name receives the highest weight (primary identification signal)
 * - Collector info only disambiguates when multiple candidates share a name
 * - Missing fields NEVER reject a candidate — they just don't help score it
 * - Low-confidence fields contribute proportionally less to the final score
 * - A confident name match alone is sufficient to surface a candidate
 *
 * Weights (configurable):
 * - Name:           0.55 (primary identification)
 * - Type line:      0.15 (confirms creature vs spell, helps disambiguation)
 * - Set code:       0.12 (disambiguates printings)
 * - Collector #:    0.08 (exact printing identification)
 * - P/T:           0.10 (confirms creature identity)
 */
class EvidenceCandidateScorer(
    private val nameWeight: Float = 0.55f,
    private val typeWeight: Float = 0.15f,
    private val setCodeWeight: Float = 0.12f,
    private val collectorWeight: Float = 0.08f,
    private val ptWeight: Float = 0.10f
) {
    companion object {
        private const val TAG = "EvidenceScorer"
    }

    /**
     * Score a list of Scryfall candidates against collected evidence.
     *
     * For each candidate, computes a weighted score based on how well it matches
     * each evidence field, modulated by the confidence of that field.
     *
     * Returns all candidates with scores > 0, sorted by score descending.
     * Does NOT filter aggressively — let the UI present ranked options.
     *
     * @param evidence Collected OCR evidence with per-field confidence
     * @param candidates Scryfall cards to score against
     * @return Scored and ranked candidates (best first), never empty if candidates were provided
     */
    fun scoreCandidates(
        evidence: CardEvidence,
        candidates: List<ScryfallCard>
    ): List<CardMatchCandidate> {
        if (candidates.isEmpty()) return emptyList()

        Log.d(TAG, "Scoring ${candidates.size} candidates against: $evidence")

        val scored = candidates.map { card ->
            val breakdown = scoreCandidate(evidence, card)
            CardMatchCandidate(
                scryfallCard = card,
                matchScore = breakdown.totalScore,
                matchReason = breakdown.reason
            )
        }.sortedByDescending { it.matchScore }

        // Log top results
        scored.take(3).forEach { c ->
            Log.d(TAG, "  ${c.matchScore.format()} '${c.scryfallCard.name}' — ${c.matchReason}")
        }

        return scored
    }

    /**
     * Score a single candidate against evidence.
     */
    private fun scoreCandidate(evidence: CardEvidence, card: ScryfallCard): ScoreBreakdown {
        var totalScore = 0f
        var totalWeight = 0f
        val reasons = mutableListOf<String>()

        // ─── Name (highest weight) ───
        if (evidence.name.isPresent) {
            val nameMatch = computeNameMatch(evidence.name.value, card.name)
            val weightedContribution = nameMatch * nameWeight * evidence.name.confidence
            totalScore += weightedContribution
            totalWeight += nameWeight * evidence.name.confidence
            reasons.add("name:${(nameMatch * 100).toInt()}%")
        }

        // ─── Type Line ───
        if (evidence.typeLine.isPresent && card.typeLine != null) {
            val typeMatch = computeTypeMatch(evidence.typeLine.value, card.typeLine)
            val weightedContribution = typeMatch * typeWeight * evidence.typeLine.confidence
            totalScore += weightedContribution
            totalWeight += typeWeight * evidence.typeLine.confidence
            if (typeMatch > 0.5f) reasons.add("type:${(typeMatch * 100).toInt()}%")
        }

        // ─── Set Code (disambiguation, not rejection) ───
        if (evidence.setCode.isPresent) {
            val setMatch = if (evidence.setCode.value.equals(card.setCode, ignoreCase = true)) 1.0f else 0.0f
            val weightedContribution = setMatch * setCodeWeight * evidence.setCode.confidence
            totalScore += weightedContribution
            totalWeight += setCodeWeight * evidence.setCode.confidence
            if (setMatch > 0f) reasons.add("set:${card.setCode}")
        }

        // ─── Collector Number (disambiguation only) ───
        if (evidence.collectorNumber.isPresent) {
            val cnMatch = if (evidence.collectorNumber.value == card.collectorNumber) 1.0f else 0.0f
            val weightedContribution = cnMatch * collectorWeight * evidence.collectorNumber.confidence
            totalScore += weightedContribution
            totalWeight += collectorWeight * evidence.collectorNumber.confidence
            if (cnMatch > 0f) reasons.add("#${card.collectorNumber}")
        }

        // ─── Power/Toughness (creature confirmation) ───
        if (evidence.powerToughness.isPresent && card.typeLine?.contains("Creature") == true) {
            val ptMatch = computePtMatch(evidence.powerToughness.value, card)
            val weightedContribution = ptMatch * ptWeight * evidence.powerToughness.confidence
            totalScore += weightedContribution
            totalWeight += ptWeight * evidence.powerToughness.confidence
            if (ptMatch > 0.5f) reasons.add("pt:match")
        }

        // Normalize: score relative to the weight of evidence we actually had
        val normalizedScore = if (totalWeight > 0f) {
            (totalScore / totalWeight).coerceIn(0f, 1f)
        } else {
            0f
        }

        return ScoreBreakdown(
            totalScore = normalizedScore,
            reason = reasons.joinToString(", ").ifEmpty { "no matching evidence" }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // MATCHING FUNCTIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compute how well a detected name matches a candidate card name.
     *
     * Uses Levenshtein similarity with bonuses for:
     * - Exact match (1.0)
     * - Substring containment (partial credit)
     * - Case-insensitive comparison
     */
    private fun computeNameMatch(detected: String, candidate: String): Float {
        val d = detected.lowercase().trim()
        val c = candidate.lowercase().trim()

        if (d == c) return 1.0f
        if (d.isEmpty()) return 0f

        // Levenshtein similarity
        val similarity = levenshteinSimilarity(d, c)

        // Bonus for substring match (OCR might truncate or miss characters)
        val substringBonus = when {
            c.startsWith(d) || c.endsWith(d) -> 0.1f
            c.contains(d) -> 0.05f
            d.length >= 5 && c.contains(d.take(d.length - 1)) -> 0.03f
            else -> 0f
        }

        return (similarity + substringBonus).coerceIn(0f, 1f)
    }

    /**
     * Compute type line match.
     *
     * Checks if the detected type words appear in the candidate's type line.
     * Partial matches earn partial credit.
     */
    private fun computeTypeMatch(detected: String, candidateType: String): Float {
        val detectedWords = detected.lowercase().split(Regex("[\\s—–-]+")).filter { it.length >= 3 }
        val candidateWords = candidateType.lowercase().split(Regex("[\\s—–-]+"))

        if (detectedWords.isEmpty()) return 0f

        val matchCount = detectedWords.count { dw -> candidateWords.any { cw -> cw.contains(dw) || dw.contains(cw) } }
        return matchCount.toFloat() / detectedWords.size.coerceAtLeast(1)
    }

    /**
     * Compute power/toughness match against candidate card.
     *
     * Extracts P/T from the ScryfallCard's typeLine or known attributes.
     * Format: "power/toughness" as a single string in evidence.
     */
    private fun computePtMatch(detectedPt: String, card: ScryfallCard): Float {
        // ScryfallCard doesn't have explicit P/T fields in our model,
        // but we can match against the type line for creature confirmation.
        // If the card is a creature and we detected P/T, that's a positive signal.
        return if (card.typeLine?.lowercase()?.contains("creature") == true) 0.7f else 0f
    }

    // ═══════════════════════════════════════════════════════════════
    // LEVENSHTEIN UTILITY
    // ═══════════════════════════════════════════════════════════════

    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    private data class ScoreBreakdown(val totalScore: Float, val reason: String)

    private fun Float.format(): String = "%.2f".format(this)
}
