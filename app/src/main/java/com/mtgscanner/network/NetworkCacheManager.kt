package com.mtgscanner.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.mtgscanner.model.ScryfallCard
import java.util.concurrent.TimeUnit

/**
 * NetworkCacheManager: Offline-first caching strategy for Scryfall cards.
 * Stores fetched card data locally to enable offline browsing and reduce API calls.
 * Implements cache expiration (optional TTL) and preload support.
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
     * Save card to cache.
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
     * Save multiple cards in bulk.
     */
    fun saveCards(cards: List<ScryfallCard>) {
        cards.forEach { saveCard(it) }
    }

    /**
     * Retrieve card from cache.
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
     * Search cache by name (case-insensitive substring match).
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
     * Get all cached cards (useful for offline browsing).
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
     * Clear all cached cards.
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
     * Get cache statistics (useful for debugging).
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
