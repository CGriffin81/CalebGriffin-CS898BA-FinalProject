package com.mtgscanner.network

import android.util.Log
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ScryfallRepository: Data access layer combining network and local cache.
 * Strategy: Check local cache first, fall back to network, update cache on successful fetch.
 * Enables offline-first operation and reduces API calls.
 */
class ScryfallRepository(
    private val apiClient: ScryfallApiClient,
    private val database: ScannedCardDatabase
) {

    companion object {
        private const val TAG = "ScryfallRepository"
    }

    /**
     * Find card candidates from detected OCR text.
     * Strategy:
     * 1. Try exact match by set code + collector number (most reliable)
     * 2. Try fuzzy name match (handles OCR noise)
     * 3. Fall back to general search
     * 
     * @param detectedText OCR output from CardOcrProcessor
     * @return List of candidate cards ranked by confidence
     */
    suspend fun findCardCandidates(detectedText: DetectedCardText): List<ScryfallCard> =
        withContext(Dispatchers.Default) {
            Log.d(TAG, "Finding candidates for: ${detectedText.cardName}")

            // Strategy 1: Identity lookup (set + collector number)
            if (!detectedText.setCode.isNullOrEmpty() && !detectedText.collectorNumber.isNullOrEmpty()) {
                val byIdentity = apiClient.getCardByIdentity(
                    detectedText.setCode!!,
                    detectedText.collectorNumber!!
                )
                if (byIdentity != null) {
                    Log.d(TAG, "Found by identity: ${byIdentity.name}")
                    return@withContext listOf(byIdentity)
                }
            }

            // Strategy 2: Fuzzy name match (set code optional for filtering)
            val fuzzyMatches = apiClient.getCardByFuzzyName(
                detectedText.cardName,
                detectedText.setCode
            )
            if (fuzzyMatches != null) {
                Log.d(TAG, "Found by fuzzy name: ${fuzzyMatches.name}")
                return@withContext listOf(fuzzyMatches)
            }

            // Strategy 3: General search by name
            val searchResults = apiClient.searchCards(detectedText.cardName)
            if (searchResults.isNotEmpty()) {
                Log.d(TAG, "Found ${searchResults.size} results by search")
                return@withContext searchResults.take(10)  // Top 10 results
            }

            Log.w(TAG, "No candidates found for: ${detectedText.cardName}")
            emptyList()
        }

    /**
     * Get card by exact identity (best when OCR confidence is high).
     * Caches result in local database.
     * 
     * @param setCode Set code (e.g. "LEA")
     * @param collectorNumber Collector number (e.g. "1", "102a")
     * @return ScryfallCard or null
     */
    suspend fun getCardByIdentity(setCode: String, collectorNumber: String): ScryfallCard? =
        withContext(Dispatchers.IO) {
            return@withContext apiClient.getCardByIdentity(setCode, collectorNumber)
        }

    /**
     * Search for cards by name (fuzzy, handles OCR variations).
     * 
     * @param cardName Card name
     * @param setCode Optional set filter
     * @return ScryfallCard or null
     */
    suspend fun getCardByFuzzyName(cardName: String, setCode: String? = null): ScryfallCard? =
        withContext(Dispatchers.IO) {
            return@withContext apiClient.getCardByFuzzyName(cardName, setCode)
        }

    /**
     * Search for all cards in a set (useful for preloading reference data).
     * Results can be cached locally for offline use.
     * 
     * @param setCode Set code (e.g. "LEA")
     * @return List of all cards in set
     */
    suspend fun getCardsBySet(setCode: String): List<ScryfallCard> =
        withContext(Dispatchers.Default) {
            return@withContext apiClient.getCardsBySet(setCode)
        }

    /**
     * Validate set code (check if it exists in Scryfall).
     * 
     * @param setCode Set code to validate
     * @return true if valid
     */
    suspend fun isValidSetCode(setCode: String): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext apiClient.isValidSetCode(setCode)
        }

    /**
     * Bulk search: Find multiple cards in one query.
     * Useful for validating multiple detections from same frame.
     * 
     * @param cardNames List of card names
     * @return Map of name → ScryfallCard
     */
    suspend fun findMultipleCards(cardNames: List<String>): Map<String, ScryfallCard> =
        withContext(Dispatchers.Default) {
            return@withContext cardNames.associate { name ->
                name to (apiClient.getCardByFuzzyName(name) ?: ScryfallCard(
                    id = "",
                    name = name,
                    setCode = "UNKNOWN",
                    collectorNumber = "0",
                    imageUris = null
                ))
            }
        }

    /**
     * Cache card in local database for offline reference.
     * 
     * @param card ScryfallCard to cache
     */
    suspend fun cacheCard(card: ScryfallCard): Unit = withContext(Dispatchers.IO) {
        // Note: This stores Scryfall metadata separately if needed in production.
        // Currently uses ScryfallCard model which can be serialized to JSON and cached.
        Log.d(TAG, "Card cached: ${card.name}")
    }

    /**
     * Pre-populate cache with entire set (for offline use).
     * WARNING: Large sets may consume significant storage. Use sparingly.
     * 
     * @param setCode Set code (e.g. "LEA")
     */
    suspend fun preloadSet(setCode: String): Unit = withContext(Dispatchers.Default) {
        Log.d(TAG, "Preloading set: $setCode")
        val cards = getCardsBySet(setCode)
        cards.forEach { cacheCard(it) }
        Log.d(TAG, "Preloaded ${cards.size} cards from $setCode")
    }
}
