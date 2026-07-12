package com.mtgscanner.network

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import com.mtgscanner.model.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retrofit client for Scryfall API card database queries.
 * Provides suspending functions for card lookups with automatic response parsing and error handling.
 * All API calls run on Dispatchers.IO for non-blocking network operations.
 *
 * API Details:
 * - Base URL: https://api.scryfall.com
 * - Rate limit: 100 requests per second (enforced server-side; honors Retry-After header)
 * - Response format: JSON with automatic Gson deserialization
 * - No authentication required (free, public API)
 *
 * Error Handling: All functions catch exceptions and return empty list or null, logging errors.
 * Responses can be cached via NetworkCacheManager to reduce API calls.
 * 
 * Methods support multiple lookup strategies:
 * - Exact name matching (precise but fails on typos)
 * - Fuzzy name matching (forgiving, handles variations)
 * - Set + collector number identity (most reliable when known)
 * - Advanced query syntax (powerful but complex)
 * - Bulk set loading (useful for offline preloading)
 *
 * @property retrofit Retrofit instance with Gson converter
 * @property apiService Retrofit-generated ScryfallApiService proxy
 */
class ScryfallApiClient {

    companion object {
        private const val BASE_URL = "https://api.scryfall.com"
        private const val TAG = "ScryfallApiClient"
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService: ScryfallApiService = retrofit.create(ScryfallApiService::class.java)
    private val gson = Gson()

    /**
     * Search for cards by query string.
     * Supports simple name search, set:code filters, collector numbers, and advanced syntax.
     * 
     * @param query Search query (e.g. "Black Lotus", "set:lea cn:1", "is:rare")
     * @return List of ScryfallCard domain models, or empty list on error
     */
    suspend fun searchCards(query: String): List<ScryfallCard> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiService.searchCards(query).execute()

            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext emptyList()
                Log.d(TAG, "Search '$query' returned ${body.data.size} cards (${body.total_cards} total)")
                body.data.mapNotNull { it.toDomainModel() }
            } else {
                val errorBody = response.errorBody()?.string()
                val error = parseError(errorBody)
                Log.e(TAG, "Search failed: ${error?.details ?: response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Find card by exact name.
     * Returns single card or null if not found.
     * 
     * @param cardName Exact card name (e.g. "Black Lotus")
     * @param setCode Optional set code to narrow results (e.g. "LEA")
     * @return ScryfallCard domain model or null
     */
    suspend fun getCardByExactName(cardName: String, setCode: String? = null): ScryfallCard? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val response = apiService.getCardByName(cardName, setCode).execute()

                if (response.isSuccessful) {
                    val card = response.body()?.toDomainModel()
                    Log.d(TAG, "Found exact: $cardName")
                    card
                } else {
                    Log.d(TAG, "No exact match: $cardName")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exact name lookup error: ${e.message}", e)
                null
            }
        }

    /**
     * Find card by fuzzy name (handles typos and variations).
     * Scryfall's fuzzy matching is robust and forgiving.
     * 
     * @param fuzzyName Card name with possible typos (e.g. "black lotos" → "Black Lotus")
     * @param setCode Optional set code filter
     * @return ScryfallCard domain model or null
     */
    suspend fun getCardByFuzzyName(fuzzyName: String, setCode: String? = null): ScryfallCard? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val response = apiService.getCardByFuzzyName(fuzzyName, setCode).execute()

                if (response.isSuccessful) {
                    val card = response.body()?.toDomainModel()
                    Log.d(TAG, "Found fuzzy: $fuzzyName → ${card?.name}")
                    card
                } else {
                    Log.d(TAG, "No fuzzy match: $fuzzyName")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fuzzy name lookup error: ${e.message}", e)
                null
            }
        }

    /**
     * Find card by set code and collector number.
     * Most reliable lookup when both are known (e.g. set:LEA cn:1 → "Black Lotus").
     * 
     * @param setCode Set code (e.g. "LEA")
     * @param collectorNumber Collector number (e.g. "1", "102", "42a")
     * @return ScryfallCard domain model or null
     */
    suspend fun getCardByIdentity(setCode: String, collectorNumber: String): ScryfallCard? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val response = apiService.getCardByCollectorNumber(setCode, collectorNumber).execute()

                if (response.isSuccessful) {
                    val card = response.body()?.toDomainModel()
                    Log.d(TAG, "Found by identity: $setCode #$collectorNumber → ${card?.name}")
                    card
                } else {
                    Log.d(TAG, "No card at $setCode #$collectorNumber")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Identity lookup error: ${e.message}", e)
                null
            }
        }

    /**
     * Perform advanced search using Scryfall query syntax.
     * Supports complex filters and boolean operators for powerful card discovery.
     *
     * Example queries:
     * - "set:lea" → All cards in Limited Edition Alpha
     * - "type:land" → All lands
     * - "rarity:rare" → All rare cards
     * - "colors:ub" → Blue-black cards
     * - "cmc:3 is:legendary" → 3-cost legendary creatures
     * - "text:flying" → Cards with flying ability
     *
     * Full query syntax reference: https://scryfall.com/docs/syntax
     *
     * @param advancedQuery Scryfall search query syntax string
     * @return List<ScryfallCard>: Matching cards, or empty list on error/no results
     */
    suspend fun advancedSearch(advancedQuery: String): List<ScryfallCard> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val response = apiService.advancedSearch(advancedQuery).execute()

                if (response.isSuccessful) {
                    val body = response.body() ?: return@withContext emptyList()
                    Log.d(TAG, "Advanced search returned ${body.data.size} cards")
                    body.data.mapNotNull { it.toDomainModel() }
                } else {
                    Log.e(TAG, "Advanced search failed: ${response.message()}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Advanced search error: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * Find all cards in a Magic set (useful for offline preloading or set browsing).
     * Internally uses advancedSearch("set:setCode") for efficient bulk retrieval.
     * Results not paginated; Scryfall returns all cards in the set.
     *
     * @param setCode Set code (e.g., "LEA", "M21", "KHM")
     * @return List<ScryfallCard>: All cards in set, or empty list if invalid set code
     */
    suspend fun getCardsBySet(setCode: String): List<ScryfallCard> =
        advancedSearch("set:$setCode")

    /**
     * Validate if a set code is recognized by Scryfall.
     * Useful before attempting set-based lookups or filtering.
     * Internally fetches all valid sets and checks membership (case-insensitive).
     *
     * @param setCode Set code to validate (case-insensitive comparison)
     * @return Boolean: true if set code is valid, false if invalid or error occurred
     */
    suspend fun isValidSetCode(setCode: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiService.getAllSets().execute()
            if (response.isSuccessful) {
                val sets = response.body()?.data ?: emptyList()
                sets.any { it.code.equals(setCode, ignoreCase = true) }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Set validation error: ${e.message}", e)
            false
        }
    }

    /**
     * Retrieve a random card from Scryfall database.
     * Useful for testing, UI exploration, or random card suggestions.
     * Each call returns a different random card (Scryfall ensures good distribution).
     *
     * @return ScryfallCard?: Random card object, or null if API call failed
     */
    suspend fun getRandomCard(): ScryfallCard? = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiService.getRandomCard().execute()
            if (response.isSuccessful) {
                response.body()?.toDomainModel()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Random card error: ${e.message}", e)
            null
        }
    }

    /**
     * Convert Scryfall API response data class to domain model ScryfallCard.
     * Bridges JSON deserialization layer with business logic.
     * Handles type conversion, field mapping, and nested ImageUris structure.
     *
     * @receiver ScryfallCardResponse parsed from Scryfall API JSON
     * @return ScryfallCard domain model, or null if conversion fails
     */
    private fun ScryfallCardResponse.toDomainModel(): ScryfallCard? {
        return try {
            ScryfallCard(
                id = id,
                name = name,
                setCode = set.uppercase(),
                collectorNumber = collector_number,
                rarity = rarity,
                typeLine = type_line,
                manaCost = mana_cost,
                cmc = cmc?.toFloat() ?: 0f,
                colors = colors ?: emptyList(),
                colorIdentity = color_identity ?: emptyList(),
                imageUris = image_uris?.let {
                    ScryfallCard.ImageUris(
                        small = it.small,
                        normal = it.normal,
                        large = it.large
                    )
                },
                scryfallUri = scryfall_uri ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert card response: ${e.message}", e)
            null
        }
    }

    /**
     * Parse Scryfall error response.
     */
    private fun parseError(errorBody: String?): ScryfallError? {
        return try {
            if (errorBody.isNullOrEmpty()) return null
            gson.fromJson(errorBody, ScryfallError::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing error body: ${e.message}", e)
            null
        }
    }
}
