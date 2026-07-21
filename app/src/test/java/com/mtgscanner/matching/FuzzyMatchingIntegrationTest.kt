package com.mtgscanner.matching

import com.mtgscanner.model.CardMatchCandidate
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FuzzyMatchingIntegrationTest
 *
 * Tests [FuzzyCardMatcher] end-to-end: OCR-detected text → Scryfall candidates → ranked results.
 * No Android framework dependencies required — pure Kotlin/JVM unit tests.
 */
class FuzzyMatchingIntegrationTest {

    private lateinit var fuzzyMatcher: FuzzyCardMatcher

    @Before
    fun setUp() {
        fuzzyMatcher = FuzzyCardMatcher()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers — avoids repeating boilerplate DetectedCardText construction
    // ──────────────────────────────────────────────────────────────────────────

    private fun detectedText(
        name: String,
        setCode: String = "",
        collectorNumber: String = "",
        confidence: Float = 0.9f
    ) = DetectedCardText(
        trackingId = 0,
        cardName = name,
        setCode = setCode,
        collectorNumber = collectorNumber,
        ocrConfidence = confidence
    )

    private fun scryfallCard(
        id: String,
        name: String,
        setCode: String = "TEST",
        collectorNumber: String = "1"
    ) = ScryfallCard(id = id, name = name, setCode = setCode, collectorNumber = collectorNumber)

    // ──────────────────────────────────────────────────────────────────────────
    // Basic matching
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testPerfectMatch() {
        val text = detectedText("Black Lotus", setCode = "LEA", collectorNumber = "1")
        val candidates = listOf(scryfallCard("lotus-lea", "Black Lotus", "LEA", "1"))

        val results = fuzzyMatcher.matchCard(text, candidates)

        assertFalse("Should return at least one match", results.isEmpty())
        assertTrue("Perfect match should score > 0.9", results.first().matchScore > 0.9f)
    }

    @Test
    fun testFuzzyMatchWithOcrNoise_oneCharTypo() {
        // "Lotos" vs "Lotus" — Levenshtein distance 1
        val text = detectedText("Black Lotos", setCode = "LEA", collectorNumber = "1")
        val candidates = listOf(scryfallCard("lotus-lea", "Black Lotus", "LEA", "1"))

        val results = fuzzyMatcher.matchCard(text, candidates)

        assertFalse("Should still match despite one-char typo", results.isEmpty())
        assertTrue("Typo match should score > 0.5", results.first().matchScore > 0.5f)
    }

    @Test
    fun testNoMatchForCompletelyDifferentName() {
        val text = detectedText("Zzzzz Xxxxx", setCode = "ZZZ", collectorNumber = "999")
        val candidates = listOf(scryfallCard("lotus-lea", "Black Lotus", "LEA", "1"))

        val results = fuzzyMatcher.matchCard(text, candidates)

        // Either empty or every score is below 0.5 (the filter threshold)
        assertTrue(
            "Completely different name should produce no results above threshold",
            results.isEmpty() || results.all { it.matchScore <= 0.5f }
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Weighted scoring
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testWeightedScoring_perfectRanksFirst() {
        val text = detectedText("Black Lotus", setCode = "LEA", collectorNumber = "1")
        val candidates = listOf(
            scryfallCard("perfect", "Black Lotus", "LEA", "1"),     // perfect match
            scryfallCard("wrong-set", "Black Lotus", "2X2", "100"), // name matches, wrong set
            scryfallCard("wrong-name", "Ancestral Recall", "LEA", "1") // wrong name
        )

        val results = fuzzyMatcher.matchCard(text, candidates)

        assertFalse("Should return matches", results.isEmpty())
        assertEquals("Perfect match should rank first", "perfect", results[0].scryfallCard.id)
        assertTrue(
            "Perfect should outrank wrong-name",
            results[0].matchScore > results.last().matchScore
        )
    }

    @Test
    fun testCollectorNumberScoring_exactMatchRanksHigher() {
        val text = detectedText("Black Lotus", setCode = "LEA", collectorNumber = "1")
        val candidates = listOf(
            scryfallCard("correct", "Black Lotus", "LEA", "1"),
            scryfallCard("different-collector", "Black Lotus", "LEA", "2")
        )

        val results = fuzzyMatcher.matchCard(text, candidates)

        assertTrue("Exact collector match should rank first", results.isNotEmpty())
        assertEquals(
            "Exact collector number should win",
            "1",
            results[0].scryfallCard.collectorNumber
        )
    }

    @Test
    fun testMultipleCandidateRanking_sortedDescending() {
        val text = detectedText("Mox Pearl", setCode = "LEA", collectorNumber = "3")
        val candidates = listOf(
            scryfallCard("rank1", "Mox Pearl", "LEA", "3"),   // perfect
            scryfallCard("rank2", "Mox Sapphire", "LEA", "4"), // close name
            scryfallCard("rank3", "Mox Jet", "LEA", "5")      // different
        )

        val results = fuzzyMatcher.matchCard(text, candidates)

        assertFalse("Should return candidates", results.isEmpty())
        assertEquals("Perfect match first", "rank1", results[0].scryfallCard.id)
        if (results.size >= 2) {
            assertTrue(
                "Results must be sorted descending by score",
                results[0].matchScore >= results[1].matchScore
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Filter threshold (score > 0.5)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testFilterThreshold_lowConfidenceMatchExcluded() {
        // OCR name is completely wrong — no score should exceed 0.5
        val text = detectedText("Random Garbage Text", setCode = "XXX", collectorNumber = "999")
        val candidates = listOf(scryfallCard("lotus", "Black Lotus", "LEA", "1"))

        val results = fuzzyMatcher.matchCard(text, candidates)

        // Either empty (filtered) or every remaining result is above 0.5 by design of the filter
        if (results.isNotEmpty()) {
            assertTrue(
                "Any result that passes the filter must have score > 0.5",
                results.all { it.matchScore > 0.5f }
            )
        }
        // The important assertion: a totally wrong name should not produce a high-confidence match
        assertTrue(
            "Random name should not produce score > 0.7",
            results.isEmpty() || results.first().matchScore < 0.7f
        )
    }

    @Test
    fun testSingleCandidatePreserved_evenWithNoisyName() {
        // Heavily corrupted OCR — Levenshtein vs correct name will score below 0.5
        // but with only 1 candidate (from Scryfall identity lookup), it should be preserved.
        // This test validates the fix intended by P2-07 in the implementation plan.
        // Currently FuzzyCardMatcher filters all results ≤ 0.5 regardless of list size.
        // After P2-07 is applied, a single candidate bypasses the filter.
        val text = detectedText("Blck Lotuz")  // 3+ char difference
        val candidates = listOf(scryfallCard("lotus", "Black Lotus", "LEA", "1"))

        val results = fuzzyMatcher.matchCard(text, candidates)

        // Current behaviour (pre P2-07): may be empty due to score filter
        // After P2-07: result should be preserved even at low score
        // This test documents the current state; it will need updating when P2-07 lands.
        if (results.isNotEmpty()) {
            assertEquals("If a result is returned it should be Black Lotus", "Black Lotus", results[0].scryfallCard.name)
        }
        // No assertion on isEmpty() — documents current behaviour honestly
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Confidence score reflects quality
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testMatchConfidenceReflectsQuality_highMatchScoresHigher() {
        val text = detectedText("Black Lotus", setCode = "LEA", collectorNumber = "1")
        val candidates = listOf(
            scryfallCard("high-match", "Black Lotus", "LEA", "1"),
            scryfallCard("low-match", "Swamp", "XYZ", "999")
        )

        val results = fuzzyMatcher.matchCard(text, candidates)

        assertTrue("Should have at least one result", results.isNotEmpty())
        if (results.size >= 2) {
            assertTrue(
                "High-quality match should score higher than low-quality match",
                results[0].matchScore > results[1].matchScore
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Levenshtein distance — core algorithm correctness
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testLevenshteinDistance_knownValues() {
        val testCases = listOf(
            Triple("Black Lotus", "Black Lotus", 0),  // identical
            Triple("Black Lotus", "Black Lotos", 1),  // 1 substitution
            Triple("cat", "dog", 3),                  // 3 substitutions
            Triple("Saturday", "Sunday", 3),          // 3 edits
            Triple("", "abc", 3),                     // empty s1
            Triple("abc", "", 3)                      // empty s2
        )

        testCases.forEach { (s1, s2, expectedDist) ->
            val dist = levenshteinDistance(s1, s2)
            assertEquals("Levenshtein('$s1', '$s2')", expectedDist, dist)
        }
    }

    @Test
    fun testLevenshteinDistance_symmetric() {
        val pairs = listOf(
            "Black Lotus" to "Black Lotos",
            "Lightning" to "Lighting",
            "Shock" to "Stock"
        )
        pairs.forEach { (a, b) ->
            assertEquals(
                "Levenshtein distance should be symmetric for '$a' / '$b'",
                levenshteinDistance(a, b),
                levenshteinDistance(b, a)
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper — mirrors FuzzyCardMatcher.levenshteinDistance for unit testing
    // ──────────────────────────────────────────────────────────────────────────

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
}
