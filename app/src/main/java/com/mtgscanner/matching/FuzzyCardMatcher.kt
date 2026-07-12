package com.mtgscanner.matching

import com.mtgscanner.model.CardMatchCandidate
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard

/**
 * Fuzzy matching logic to convert OCR output to Scryfall card identities.
 * Uses string similarity and contextual matching to rank candidate cards.
 */
class FuzzyCardMatcher {

    /**
     * Match detected card text to Scryfall candidates.
     * @param detectedText OCR output from card.
     * @param scryfallCandidates Possible cards from Scryfall.
     * @return Ranked list of matching candidates.
     */
    fun matchCard(
        detectedText: DetectedCardText,
        scryfallCandidates: List<ScryfallCard>
    ): List<CardMatchCandidate> {
        return scryfallCandidates
            .map { card ->
                val nameScore = levenshteinSimilarity(
                    detectedText.cardName.lowercase(),
                    card.name.lowercase()
                )
                
                val setScore = if (detectedText.setCode.isNotEmpty()) {
                    if (detectedText.setCode.equals(card.setCode, ignoreCase = true)) 1.0f else 0.0f
                } else {
                    0.5f  // Set not provided, reduce confidence slightly
                }
                
                val collectorScore = if (detectedText.collectorNumber.isNotEmpty()) {
                    if (detectedText.collectorNumber == card.collectorNumber) 1.0f else 0.0f
                } else {
                    0.5f
                }
                
                // Weighted score: name is primary, set and collector are tiebreakers
                val finalScore = (nameScore * 0.6f) + (setScore * 0.2f) + (collectorScore * 0.2f)
                val reason = buildString {
                    append("name:${(nameScore * 100).toInt()}%")
                    if (detectedText.setCode.isNotEmpty()) append(", set:${detectedText.setCode}")
                    if (detectedText.collectorNumber.isNotEmpty()) append(", #${detectedText.collectorNumber}")
                }
                
                CardMatchCandidate(
                    scryfallCard = card,
                    matchScore = finalScore,
                    matchReason = reason
                )
            }
            .filter { it.matchScore > 0.5f }  // Only keep reasonable matches
            .sortedByDescending { it.matchScore }
    }

    /**
     * Levenshtein distance-based string similarity (0.0 to 1.0).
     */
    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost  // substitution
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
}
