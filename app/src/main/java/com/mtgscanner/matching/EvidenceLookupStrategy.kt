package com.mtgscanner.matching

import android.util.Log

/**
 * Determines the optimal Scryfall lookup strategy based on available evidence.
 *
 * Rather than always executing the same lookup sequence regardless of what OCR found,
 * this planner selects the most efficient strategy for the evidence quality:
 *
 * - Strong name + set + collector → Identity lookup first (fastest, most precise)
 * - Strong name only → Fuzzy name lookup (reliable, single result)
 * - Weak name → General search (broad, multiple candidates)
 * - Name + type → Filtered search (narrows results)
 *
 * The strategy NEVER rejects a lookup just because collector info is missing.
 * Missing info simply removes one strategy option; it doesn't prevent identification.
 */
class EvidenceLookupStrategy {

    companion object {
        private const val TAG = "EvidenceLookupStrategy"
    }

    /**
     * Plan the ordered sequence of lookup strategies to attempt.
     *
     * Returns a prioritized list of [LookupStep]s. The caller should attempt
     * them in order, stopping at the first successful result.
     *
     * @param evidence Available OCR evidence with confidences
     * @return Ordered list of lookup steps to attempt
     */
    fun planLookup(evidence: CardEvidence): List<LookupStep> {
        val steps = mutableListOf<LookupStep>()

        Log.d(TAG, "Planning lookup for: $evidence")

        // Strategy 1: Identity lookup (most precise — single card result)
        // Only if both set code and collector number are present with reasonable confidence
        if (evidence.canDoIdentityLookup) {
            steps.add(LookupStep.Identity(
                setCode = evidence.setCode.value.lowercase(),
                collectorNumber = evidence.collectorNumber.value
            ))
        }

        // Strategy 2: Fuzzy name match (very reliable for clean names)
        // Scryfall's fuzzy matcher is tolerant of OCR noise
        if (evidence.canDoNameLookup) {
            val setHint = if (evidence.setCode.isPresent && evidence.setCode.confidence > 0.5f) {
                evidence.setCode.value.lowercase()
            } else null

            steps.add(LookupStep.FuzzyName(
                name = evidence.name.value,
                setCodeHint = setHint
            ))
        }

        // Strategy 3: General search (broadest, returns multiple candidates)
        // Used when name confidence is lower or as a fallback
        if (evidence.name.isPresent && evidence.name.value.length >= 2) {
            val query = buildSearchQuery(evidence)
            steps.add(LookupStep.Search(query = query))
        }

        // Strategy 4: Type-filtered search (when we know the type but name is weak)
        if (evidence.typeLine.isPresent && evidence.typeLine.confidence > 0.6f
            && evidence.name.isPresent) {
            val typeQuery = "${evidence.name.value} t:${extractPrimaryType(evidence.typeLine.value)}"
            steps.add(LookupStep.Search(query = typeQuery))
        }

        Log.d(TAG, "Planned ${steps.size} lookup steps: ${steps.map { it.javaClass.simpleName }}")
        return steps
    }

    /**
     * Build a search query string from available evidence.
     * Combines name with optional set filter for more targeted results.
     */
    private fun buildSearchQuery(evidence: CardEvidence): String {
        val parts = mutableListOf<String>()

        // Always include the name (primary search term)
        parts.add(evidence.name.value)

        // Add set filter if confident
        if (evidence.setCode.isPresent && evidence.setCode.confidence > 0.6f) {
            parts.add("s:${evidence.setCode.value.lowercase()}")
        }

        return parts.joinToString(" ")
    }

    /**
     * Extract the primary card type from a type line for search filtering.
     * "Legendary Creature — Demon" → "creature"
     */
    private fun extractPrimaryType(typeLine: String): String {
        val types = listOf("creature", "instant", "sorcery", "artifact", "enchantment",
            "land", "planeswalker", "battle")
        val lower = typeLine.lowercase()
        return types.firstOrNull { lower.contains(it) } ?: ""
    }
}

/**
 * A single lookup step in the planned strategy.
 * Each variant contains the parameters needed for that specific Scryfall API call.
 */
sealed class LookupStep {
    /** Exact identity lookup: /cards/{set}/{cn} */
    data class Identity(val setCode: String, val collectorNumber: String) : LookupStep()

    /** Fuzzy name lookup: /cards/named?fuzzy={name}&set={setHint} */
    data class FuzzyName(val name: String, val setCodeHint: String?) : LookupStep()

    /** General search: /cards/search?q={query} */
    data class Search(val query: String) : LookupStep()
}
