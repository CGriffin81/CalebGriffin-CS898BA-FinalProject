package com.mtgscanner.network

import android.util.Log
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Enhanced card repository with built-in network resilience, intelligent caching, and offline support.
 * Implements multi-layer fallback strategy:
 * 1. Check network availability
 * 2. Try identity lookup (set + collector) with exponential backoff retries
 * 3. Try fuzzy name match with retries
 * 4. Try general search with retries
 * 5. Fallback to cache on all network failures
 * 6. Return error state if both network and cache fail
 * Uses Result<T> sealed class to communicate success/cache-hit/error states.
 *
 * @param apiClient ScryfallApiClient for making API requests
 * @param database ScannedCardDatabase for local persistence
 * @param cacheManager NetworkCacheManager for offline card caching
 * @param networkStateManager NetworkStateManager to monitor connectivity
 * @param retryPolicy RetryPolicy controlling retry behavior (max retries, delays, backoff)
 */
class ScryfallRepositoryResilience(
    private val apiClient: ScryfallApiClient,
    private val database: ScannedCardDatabase,
    private val cacheManager: NetworkCacheManager,
    private val networkStateManager: NetworkStateManager,
    private val retryPolicy: RetryPolicy = RetryPolicy()
) {

    companion object {
        private const val TAG = "ScryfallRepositoryResilience"
    }

    /**
     * Sealed result type for repository operations.
     * Distinguishes between successful network fetch, cache hit, and error states.
     *
     * @param T Type of data returned in success/cache/error states
     */
    sealed class Result<T> {
        /**
         * Successful network result with fresh data from Scryfall API.
         * @param data T The retrieved data
         */
        data class Success<T>(val data: T) : Result<T>()

        /**
         * Cache hit when network is unavailable or API returns no results.
         * Indicates data was retrieved from local cache rather than live API.
         * @param data T The cached data
         * @param message String: : Description of cache status (default "From cache")
         */
        data class CacheHit<T>(val data: T, val message: String = "From cache") : Result<T>()

        /**
         * Error state indicating both network and cache failed to provide data.
         * @param message String: Human-readable error description
         * @param fallbackData T? : Optional fallback data (e.g., partial results) or null if none available
         */
        data class Error<T>(val message: String, val fallbackData: T? = null) : Result<T>()
    }

    /**
     * Find card candidates with comprehensive resilience strategy.
     *
     * Lookup order (P4-03: local-first, then network):
     * 0. Normalize input identifiers (P4-02: lowercase set code, trim whitespace)
     * 1. Check local collection (Room) — previously scanned cards (~1ms)
     * 2. Check SharedPreferences cache — previously fetched Scryfall data
     * 3. If offline, return cache result or error
     * 4. Strategy 1 - Identity Lookup: set + collector number via /cards/{set}/{cn}
     * 5. Strategy 2 - Fuzzy Name: /cards/named?fuzzy=
     * 6. Strategy 3 - General Search: /cards/search?q=
     * 7. Fallback - Cache search by name
     * 8. Error if nothing found
     *
     * Results are cached for offline use. Runs on Dispatchers.Default.
     *
     * @param detectedText OCR-recognized card text containing name, set code, and collector number.
     * @return Result: Success (network), CacheHit (local), or Error.
     */
    suspend fun findCardCandidatesResilient(detectedText: DetectedCardText): Result<List<ScryfallCard>> =
        withContext(Dispatchers.Default) {
            // P4-02: Normalize identifiers at entry point
            val cardName = detectedText.cardName.trim()
            val setCode = detectedText.setCode.lowercase().trim()
            val collectorNumber = detectedText.collectorNumber.trim()

            Log.d(TAG, "Finding candidates for: '$cardName' set='$setCode' cn='$collectorNumber'")

            // ─── P4-03: Check local collection FIRST (zero network cost) ───
            if (cardName.length >= 3) {
                try {
                    val ownedCards = database.scannedCardDao()
                        .searchByName("%${cardName}%")
                        .first()
                        .take(3)

                    if (ownedCards.isNotEmpty()) {
                        val fromCollection = ownedCards.mapNotNull { entity ->
                            // Try to get full Scryfall data from cache, otherwise build from entity
                            cacheManager.getCard(entity.scryfallId) ?: ScryfallCard(
                                id = entity.scryfallId,
                                name = entity.cardName,
                                setCode = entity.setCode,
                                collectorNumber = entity.collectorNumber,
                                rarity = entity.rarity,
                                typeLine = entity.typeLine,
                                oracleText = entity.oracleText,
                                imageUris = entity.imageUrl?.let {
                                    ScryfallCard.ImageUris(normal = it)
                                }
                            )
                        }
                        if (fromCollection.isNotEmpty()) {
                            Log.d(TAG, "Found ${fromCollection.size} in local collection")
                            return@withContext Result.CacheHit(fromCollection, "From your collection")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Local collection lookup failed: ${e.message}")
                    // Non-fatal — continue to cache/network
                }
            }

            // ─── Check SharedPreferences cache ───
            val cachedByName = cacheManager.searchCardsByName(cardName)
            if (cachedByName.isNotEmpty()) {
                Log.d(TAG, "Found ${cachedByName.size} in cache for '$cardName'")
                return@withContext Result.CacheHit(cachedByName, "From cache")
            }

            // ─── Check network availability ───
            val isOnline = networkStateManager.isNetworkAvailable.value
            if (!isOnline) {
                Log.w(TAG, "Offline: no local or cache results for '$cardName'")
                return@withContext Result.Error("No internet and no cached cards", null)
            }

            // ─── Strategy 1: Identity lookup (set + collector) ───
            if (setCode.isNotEmpty() && collectorNumber.isNotEmpty()) {
                val byIdentity = retryableCall(retryPolicy) {
                    apiClient.getCardByIdentity(setCode, collectorNumber)
                }
                if (byIdentity != null) {
                    cacheManager.saveCard(byIdentity)
                    Log.d(TAG, "Found by identity: ${byIdentity.name}")
                    return@withContext Result.Success(listOf(byIdentity))
                }
            }

            // ─── Strategy 2: Fuzzy name match ───
            val fuzzySetCode = setCode.ifEmpty { null }
            val fuzzyMatch = retryableCall(retryPolicy) {
                apiClient.getCardByFuzzyName(cardName, fuzzySetCode)
            }
            if (fuzzyMatch != null) {
                cacheManager.saveCard(fuzzyMatch)
                Log.d(TAG, "Found by fuzzy name: ${fuzzyMatch.name}")
                return@withContext Result.Success(listOf(fuzzyMatch))
            }

            // ─── Strategy 3: General search ───
            val searchResults = retryableCall(retryPolicy) {
                apiClient.searchCards(cardName)
            }
            if (searchResults != null && searchResults.isNotEmpty()) {
                cacheManager.saveCards(searchResults.take(10))
                Log.d(TAG, "Found ${searchResults.size} by search")
                return@withContext Result.Success(searchResults.take(10))
            }

            Log.w(TAG, "No candidates found for '$cardName' (all strategies exhausted)")
            return@withContext Result.Error("No candidates found", null)
        }

    /**
     * Retrieve a single card by its exact identity (set code + collector number) with resilience.
     * If offline, searches local cache for matching card.
     * If online, attempts API lookup with retry policy; falls back to cache on network failure.
     * Runs on Dispatchers.IO to avoid blocking the main thread.
     *
     * @param setCode Scryfall set code (e.g., "MIR", "DOM") for the card
     * @param collectorNumber Collector number (e.g., "42", "123*") uniquely identifying the card within the set
     * @return Result object: Success if found via network, CacheHit if found in cache, Error if card not found
     * @throws Exception (implicitly caught and converted to Result.Error) if API calls fail after all retries
     */
    suspend fun getCardByIdentityResilient(setCode: String, collectorNumber: String): Result<ScryfallCard> =
        withContext(Dispatchers.IO) {
            if (!networkStateManager.isNetworkAvailable.value) {
                // Try cache first (P4-02: case-insensitive set code comparison)
                val cached = cacheManager.getAllCachedCards()
                    .find { it.setCode.equals(setCode, ignoreCase = true) && it.collectorNumber == collectorNumber }
                return@withContext if (cached != null) {
                    Result.CacheHit(cached)
                } else {
                    Result.Error("Offline: card not in cache")
                }
            }

            val card = retryableCall(retryPolicy) {
                apiClient.getCardByIdentity(setCode, collectorNumber)
            }

            if (card != null) {
                cacheManager.saveCard(card)
                return@withContext Result.Success(card)
            }

            // Fallback to cache (P4-02: case-insensitive)
            val cached = cacheManager.getAllCachedCards()
                .find { it.setCode.equals(setCode, ignoreCase = true) && it.collectorNumber == collectorNumber }

            return@withContext if (cached != null) {
                Result.CacheHit(cached, "Network failed; using cache")
            } else {
                Result.Error("Card not found: $setCode #$collectorNumber")
            }
        }

    /**
     * Preload all cards from a specific set into the local cache for offline use.
     * Requires active network connection; returns error if offline.
     * On success, saves all retrieved cards to cache (capped at available cards for the set).
     * Useful for preloading common sets (Standard, Limited formats) for offline collection management.
     * Runs on Dispatchers.Default.
     *
     * @param setCode Scryfall set code (e.g., "MIR", "DOM", "SLD") to preload
     * @return Result object: Success with list of preloaded cards, Error if preload fails or offline
     */
    suspend fun preloadSetResilient(setCode: String): Result<List<ScryfallCard>> =
        withContext(Dispatchers.Default) {
            Log.d(TAG, "Preloading set: $setCode")

            if (!networkStateManager.isNetworkAvailable.value) {
                return@withContext Result.Error("Cannot preload: no network")
            }

            val cards = retryableCall(retryPolicy) {
                apiClient.getCardsBySet(setCode)
            }

            if (cards != null && cards.isNotEmpty()) {
                cacheManager.saveCards(cards)
                Log.d(TAG, "Preloaded ${cards.size} cards from $setCode")
                return@withContext Result.Success(cards)
            }

            return@withContext Result.Error("Failed to preload $setCode")
        }

    /**
     * Retrieve cache statistics and metadata for debugging and monitoring.
     * Provides insight into cache size, hit rates, and offline capability.
     *
     * @return NetworkCacheManager.CacheStats object containing cache hit/miss counts, size, and other diagnostics
     */
    fun getCacheStats(): NetworkCacheManager.CacheStats = cacheManager.getCacheStats()
}
