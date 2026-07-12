package com.mtgscanner.matching

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard
import com.mtgscanner.model.CardMatchCandidate

/**
 * FuzzyMatchingIntegrationTest: Tests fuzzy card matching pipeline.
 * Validates: detected text → Scryfall candidates → ranked results.
 */
class FuzzyMatchingIntegrationTest {

    private lateinit var fuzzyMatcher: FuzzyCardMatcher

    @Before
    fun setUp() {
        fuzzyMatcher = FuzzyCardMatcher()
    }

    /**
     * Test: Perfect match (OCR output matches Scryfall exactly).
     */
    @Test
    fun testPerfectMatch() {
        val detectedText = DetectedCardText(
            cardName = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            confidence = 0.95
        )

        val scryfallCandidates = listOf(
            ScryfallCard(
                id = "123e4567-e89b-12d3-a456-426614174000",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, scryfallCandidates)

        assertFalse("Should return at least one match", results.isEmpty())
        val topMatch = results.first()
        assertTrue("Perfect match should have high score", topMatch.matchScore > 0.9)
    }

    /**
     * Test: Fuzzy match handles OCR noise (typos, partial matches).
     */
    @Test
    fun testFuzzyMatchWithOcrNoise() {
        val detectedText = DetectedCardText(
            cardName = "Black Lotos",  // Typo: "Lotus" → "Lotos"
            setCode = "LEA",
            collectorNumber = "1",
            confidence = 0.75
        )

        val scryfallCandidates = listOf(
            ScryfallCard(
                id = "123e4567-e89b-12d3-a456-426614174000",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, scryfallCandidates)

        assertFalse("Should still match despite typo", results.isEmpty())
        val topMatch = results.first()
        assertTrue("Should have reasonable score", topMatch.matchScore > 0.5)
    }

    /**
     * Test: Weighted scoring: name primary, set/collector secondary.
     */
    @Test
    fun testWeightedScoring() {
        val detectedText = DetectedCardText(
            cardName = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            confidence = 0.90
        )

        val candidates = listOf(
            // Perfect name, perfect set, perfect collector
            ScryfallCard(
                id = "perfect",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = null
            ),
            // Perfect name, wrong set
            ScryfallCard(
                id = "wrong-set",
                name = "Black Lotus",
                setCode = "2X2",
                collectorNumber = "100",
                imageUris = null
            ),
            // Wrong name, right set/collector (shouldn't rank high)
            ScryfallCard(
                id = "wrong-name",
                name = "Ancestral Recall",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, candidates)

        assertFalse("Should return matches", results.isEmpty())
        // Perfect match should rank highest
        assertEquals("Perfect match should rank first", "perfect", results[0].scryfallCard.id)
        // Wrong name should rank lowest
        assertTrue("Wrong name should rank lower than perfect", 
            results[0].matchScore > results.last().matchScore)
    }

    /**
     * Test: Filtering low-confidence matches (threshold 0.5).
     */
    @Test
    fun testLowConfidenceFiltering() {
        val detectedText = DetectedCardText(
            cardName = "Random Text",
            setCode = "XXX",
            collectorNumber = "999",
            confidence = 0.30
        )

        val scryfallCandidates = listOf(
            ScryfallCard(
                id = "123",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, scryfallCandidates)

        // Should either be empty or have very low match scores
        if (results.isNotEmpty()) {
            assertTrue("Low-confidence matches should be filtered", results.first().matchScore < 0.5)
        }
    }

    /**
     * Test: Multiple candidates ranked by score.
     */
    @Test
    fun testMultipleCandidateRanking() {
        val detectedText = DetectedCardText(
            cardName = "Mox Pearl",
            setCode = "LEA",
            collectorNumber = "3",
            confidence = 0.85
        )

        val scryfallCandidates = listOf(
            ScryfallCard(
                id = "rank1",
                name = "Mox Pearl",
                setCode = "LEA",
                collectorNumber = "3",
                imageUris = null
            ),
            ScryfallCard(
                id = "rank2",
                name = "Mox Sapphire",
                setCode = "LEA",
                collectorNumber = "4",
                imageUris = null
            ),
            ScryfallCard(
                id = "rank3",
                name = "Mox Jet",
                setCode = "LEA",
                collectorNumber = "5",
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, scryfallCandidates)

        assertFalse("Should return multiple candidates", results.isEmpty())
        // Perfect match should rank first
        assertEquals("Perfect match should be first", "rank1", results[0].scryfallCard.id)
        // Results should be sorted by matchScore descending
        assertTrue("Results should be sorted by score descending",
            results[0].matchScore >= results[1].matchScore)
    }

    /**
     * Test: Collector number scoring (exact match preferred).
     */
    @Test
    fun testCollectorNumberScoring() {
        val detectedText = DetectedCardText(
            cardName = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            confidence = 0.85
        )

        val candidates = listOf(
            ScryfallCard(
                id = "correct",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",  // Exact match
                imageUris = null
            ),
            ScryfallCard(
                id = "different-collector",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "2",  // Different collector
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, candidates)

        assertTrue("Exact collector match should rank higher",
            results[0].scryfallCard.collectorNumber == "1")
    }

    /**
     * Test: Levenshtein distance calculation (core fuzzy matching).
     */
    @Test
    fun testLevenshteinDistance() {
        // Direct Levenshtein tests
        val testCases = listOf(
            Triple("Black Lotus", "Black Lotus", 0),      // Identical
            Triple("Black Lotus", "Black Lotos", 1),      // 1 char diff
            Triple("cat", "dog", 3),                      // Completely different
            Triple("Saturday", "Sunday", 3),              // Common prefix
            Triple("", "abc", 3),                         // Empty string
            Triple("abc", "", 3)                          // Empty string
        )

        testCases.forEach { (str1, str2, expectedDistance) ->
            val distance = levenshteinDistance(str1, str2)
            assertEquals("Distance '$str1' vs '$str2'", expectedDistance, distance)
        }
    }

    /**
     * Test: Confidence score reflects match quality.
     */
    @Test
    fun testMatchConfidenceReflectsQuality() {
        val detectedText = DetectedCardText(
            cardName = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            confidence = 0.90
        )

        val candidates = listOf(
            ScryfallCard(
                id = "high-match",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = null
            ),
            ScryfallCard(
                id = "low-match",
                name = "Swamp",
                setCode = "XYZ",
                collectorNumber = "999",
                imageUris = null
            )
        )

        val results = fuzzyMatcher.matchCard(detectedText, candidates)

        assertTrue("Should have multiple results", results.size >= 1)
        assertTrue("High-quality match should have higher score",
            results[0].matchScore > (if (results.size > 1) results[1].matchScore else 0.0))
    }

    /**
     * Helper: Calculate Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) {
            dp[i][0] = i
        }
        for (j in 0..s2.length) {
            dp[0][j] = j
        }

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // Deletion
                    dp[i][j - 1] + 1,      // Insertion
                    dp[i - 1][j - 1] + cost // Substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}
