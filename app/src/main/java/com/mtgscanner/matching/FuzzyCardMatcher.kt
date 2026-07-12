package com.mtgscanner.matching

import com.mtgscanner.model.CardMatchCandidate
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard

/**
 * Performs fuzzy string matching to rank Scryfall candidate cards against OCR-detected card text.
 * Uses Levenshtein distance-based similarity scoring with weighted multi-factor ranking:
 * card name (60% weight), set code (20% weight), collector number (20% weight).
 * Filters results to only retain matches with score > 0.5.
 */
class FuzzyCardMatcher {

    /**
     * Match detected OCR card text against a list of Scryfall candidates and return ranked matches.
     * For each candidate, calculates a weighted composite score:
     * - nameScore (60%): Levenshtein similarity of card names (case-insensitive)
     * - setScore (20%): 1.0 if set code matches, 0.0 if provided and differs, 0.5 if not provided
     * - collectorScore (20%): 1.0 if collector number matches exactly, 0.0 if provided and differs, 0.5 if not provided
     * Filters to matches with score > 0.5 and sorts in descending order by final score.
     *
     * @param detectedText OCR output containing card name, set code, and collector number
     * @param scryfallCandidates List of possible cards from Scryfall API to match against
     * @return List of CardMatchCandidate objects sorted by match score (descending); empty if no candidates exceed threshold
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
     * Calculate Levenshtein distance-based string similarity between two strings.
     * Returns 1.0 for identical strings, 0.0 for completely different strings.
     * Similarity = 1 - (distance / max_length), ensuring result is always between 0.0 and 1.0.
     *
     * @param s1 First string to compare
     * @param s2 Second string to compare
     * @return Similarity score between 0.0 (completely different) and 1.0 (identical)
     */
    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    /**
     * Calculate the Levenshtein distance between two strings using dynamic programming.
     * Levenshtein distance is the minimum number of single-character edits (insertion, deletion, substitution)
     * required to transform one string into another.
     * Uses a 2D DP table where dp[i][j] represents the distance between s1[0..i-1] and s2[0..j-1].
     *
     * @param s1 First string to compare
     * @param s2 Second string to compare
     * @return Levenshtein distance as an integer; 0 if strings are identical
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
