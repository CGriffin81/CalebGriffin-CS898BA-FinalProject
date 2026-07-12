package com.mtgscanner.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.mtgscanner.model.ScryfallCard
import java.util.concurrent.TimeUnit

/**
 * NetworkCacheManager: Offline-first caching strategy for Scryfall cards.
 * Stores fetched card data in SharedPreferences as JSON with 7-day TTL expiration.
 * Enables offline browsing, reduces API call load, and provides fallback during connectivity issues.
 * 
 * Cache Implementation:
 * - Uses SharedPreferences with Gson serialization
 * - Each card cached with metadata timestamp for TTL tracking
 * - 7-day cache expiration (608400000ms) for all cached cards
 * - Supports bulk operations and search across cached data
 * - All operations wrapped in try/catch with logging for resilience
 *
 * @param context Android Context for SharedPreferences access
 */
class NetworkCacheManager(context: Context) {

    companion object {
        private const val TAG = "NetworkCacheManager"
        private const val PREFS_NAME = "mtg_scanner_cache"
        private const val CACHE_EXPIRATION_MS = TimeUnit.DAYS.toMillis(7)  // 7 day TTL
        private const val KEY_CARD_PREFIX = "card_"
        private const val KEY_CARD_METADATA = "card_metadata_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Save a single card to the cache.
     * Serializes card to JSON and stores with timestamp for TTL tracking.
     * Overwrites previous entry if card ID already cached.
     *
     * @param card ScryfallCard to cache
     * @throws Exception caught internally and logged
     */
    fun saveCard(card: ScryfallCard) {
        try {
            val json = gson.toJson(card)
            val cacheKey = "${KEY_CARD_PREFIX}${card.id}"
            val metadataKey = "${KEY_CARD_METADATA}${card.id}"
            
            prefs.edit().apply {
                putString(cacheKey, json)
                putLong(metadataKey, System.currentTimeMillis())  // Store cache timestamp
                apply()
            }
            Log.d(TAG, "Card cached: ${card.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching card: ${e.message}", e)
        }
    }

    /**
     * Save multiple cards to cache in bulk.
     * Iterates through list calling saveCard() for each entry.
     *
     * @param cards List of ScryfallCards to cache
     */
    fun saveCards(cards: List<ScryfallCard>) {
        cards.forEach { saveCard(it) }
    }

    /**
     * Retrieve a card from cache by Scryfall UUID.
     * Checks cache expiration (7-day TTL) and removes expired entries.
     * Returns null if card not found or cache has expired.
     *
     * @param cardId Scryfall UUID (unique card identifier)
     * @return ScryfallCard?: The cached card if found and not expired, null otherwise
     */
    fun getCard(cardId: String): ScryfallCard? {
        return try {
            val cacheKey = "${KEY_CARD_PREFIX}${cardId}"
            val metadataKey = "${KEY_CARD_METADATA}${cardId}"
            val json = prefs.getString(cacheKey, null) ?: return null
            
            // Check if cache has expired
            val timestamp = prefs.getLong(metadataKey, 0)
            val ageMs = System.currentTimeMillis() - timestamp
            if (ageMs > CACHE_EXPIRATION_MS) {
                Log.d(TAG, "Cache expired for card $cardId (${ageMs}ms old)")
                prefs.edit().remove(cacheKey).remove(metadataKey).apply()
                return null
            }
            
            gson.fromJson(json, ScryfallCard::class.java).also {
                Log.d(TAG, "Retrieved from cache: ${it.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving card from cache: ${e.message}", e)
            null
        }
    }

    /**
     * Search cache for cards by name using case-insensitive substring matching.
     * Iterates through all cached cards and filters by query in card name.
     * Does NOT check TTL; only for UI-level search (not critical for expired entries).
     *
     * @param query Substring to search for (case-insensitive)
     * @return List<ScryfallCard>: All matching cards, or empty list if none found or cache is empty
     */
    fun searchCardsByName(query: String): List<ScryfallCard> {
        return try {
            prefs.all
                .filter { (key, _) -> key.startsWith(KEY_CARD_PREFIX) }
                .mapNotNull { (_, json) -> 
                    try {
                        gson.fromJson(json as String, ScryfallCard::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { it.name.contains(query, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching cache: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get all cached cards without TTL validation.
     * Useful for offline browsing, collection display, and batch operations.
     * Note: Returns all cards regardless of expiration (check getCacheStats for age info).
     *
     * @return List<ScryfallCard>: All cached cards, or empty list if cache is empty
     */
    fun getAllCachedCards(): List<ScryfallCard> {
        return try {
            prefs.all
                .filter { (key, _) -> key.startsWith(KEY_CARD_PREFIX) }
                .mapNotNull { (_, json) ->
                    try {
                        gson.fromJson(json as String, ScryfallCard::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached cards: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Clear all cached cards from SharedPreferences.
     * WARNING: This is destructive and cannot be undone. Useful for cache refresh or settings reset.
     *
     * @throws Exception caught internally and logged
     */
    fun clearCache() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}", e)
        }
    }

    /**
     * Get cache usage statistics for debugging and UI display.
     * Returns total cached cards, approximate memory usage, and oldest cached entry age.
     *
     * @return CacheStats data object with totalCards, totalSizeMb, oldestCardMs
     */
    fun getCacheStats(): CacheStats {
        val cards = getAllCachedCards()
        return CacheStats(
            totalCards = cards.size,
            totalSizeMb = prefs.all.values.sumOf { (it as? String)?.length ?: 0 } / (1024 * 1024),
            oldestCardMs = if (cards.isEmpty()) 0 else {
                prefs.all
                    .filter { (key, _) -> key.startsWith(KEY_CARD_METADATA) }
                    .values
                    .minOfOrNull { (it as? Long) ?: Long.MAX_VALUE } ?: 0
            }
        )
    }

    data class CacheStats(
        val totalCards: Int,
        val totalSizeMb: Long,
        val oldestCardMs: Long
    )
}
