package com.mtgscanner.network

import android.util.Log
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ScryfallRepositoryResilience: Enhanced repository with network resilience, caching, and offline support.
 * Strategy: Try network with retries → fallback to cache → return null with error state.
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

    sealed class Result<T> {
        data class Success<T>(val data: T) : Result<T>()
        data class CacheHit<T>(val data: T, val message: String = "From cache") : Result<T>()
        data class Error<T>(val message: String, val fallbackData: T? = null) : Result<T>()
    }

    /**
     * Find card candidates with full resilience strategy:
     * 1. Check if online
     * 2. Try identity lookup with retry
     * 3. Try fuzzy name with retry
     * 4. Try search with retry
     * 5. Fallback to cache on all failures
     */
    suspend fun findCardCandidatesResilient(detectedText: DetectedCardText): Result<List<ScryfallCard>> =
        withContext(Dispatchers.Default) {
            Log.d(TAG, "Finding candidates for: ${detectedText.cardName}")

            // Check network status
            val isOnline = networkStateManager.isNetworkAvailable.value
            if (!isOnline) {
                Log.w(TAG, "Offline mode: checking cache only")
                val cachedCards = cacheManager.searchCardsByName(detectedText.cardName)
                return@withContext if (cachedCards.isNotEmpty()) {
                    Result.CacheHit(cachedCards, "Using cached cards (offline)")
                } else {
                    Result.Error("No internet and no cached cards", null)
                }
            }

            // Strategy 1: Identity lookup (set + collector)
            if (!detectedText.setCode.isNullOrEmpty() && !detectedText.collectorNumber.isNullOrEmpty()) {
                val byIdentity = retryableCall(retryPolicy) {
                    apiClient.getCardByIdentity(
                        detectedText.setCode!!,
                        detectedText.collectorNumber!!
                    )
                }
                if (byIdentity != null) {
                    cacheManager.saveCard(byIdentity)
                    Log.d(TAG, "Found by identity: ${byIdentity.name}")
                    return@withContext Result.Success(listOf(byIdentity))
                }
            }

            // Strategy 2: Fuzzy name match
            val fuzzyMatches = retryableCall(retryPolicy) {
                apiClient.getCardByFuzzyName(
                    detectedText.cardName,
                    detectedText.setCode
                )
            }
            if (fuzzyMatches != null) {
                cacheManager.saveCard(fuzzyMatches)
                Log.d(TAG, "Found by fuzzy name: ${fuzzyMatches.name}")
                return@withContext Result.Success(listOf(fuzzyMatches))
            }

            // Strategy 3: General search
            val searchResults = retryableCall(retryPolicy) {
                apiClient.searchCards(detectedText.cardName)
            }
            if (searchResults != null && searchResults.isNotEmpty()) {
                cacheManager.saveCards(searchResults.take(10))
                Log.d(TAG, "Found ${searchResults.size} by search")
                return@withContext Result.Success(searchResults.take(10))
            }

            // Strategy 4: Fallback to cache
            val cachedCards = cacheManager.searchCardsByName(detectedText.cardName)
            if (cachedCards.isNotEmpty()) {
                Log.d(TAG, "No network results; falling back to ${cachedCards.size} cached cards")
                return@withContext Result.CacheHit(cachedCards, "No network; using cached results")
            }

            Log.w(TAG, "No candidates found (network and cache)")
            return@withContext Result.Error("No candidates found", null)
        }

    /**
     * Get card by identity with resilience.
     */
    suspend fun getCardByIdentityResilient(setCode: String, collectorNumber: String): Result<ScryfallCard> =
        withContext(Dispatchers.IO) {
            if (!networkStateManager.isNetworkAvailable.value) {
                // Try cache first
                val cached = cacheManager.getAllCachedCards()
                    .find { it.setCode == setCode && it.collectorNumber == collectorNumber }
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

            // Fallback to cache
            val cached = cacheManager.getAllCachedCards()
                .find { it.setCode == setCode && it.collectorNumber == collectorNumber }

            return@withContext if (cached != null) {
                Result.CacheHit(cached, "Network failed; using cache")
            } else {
                Result.Error("Card not found: $setCode #$collectorNumber")
            }
        }

    /**
     * Preload entire set for offline use.
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
     * Get cache statistics for debugging.
     */
    fun getCacheStats(): NetworkCacheManager.CacheStats = cacheManager.getCacheStats()
}
