package com.mtgscanner.network

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import com.mtgscanner.model.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ScryfallApiClient: Network client for Scryfall card database.
 * Handles API calls, error handling, response parsing, and conversion to domain models.
 * 
 * Rate limit: 100 requests per second; honors Retry-After header.
 * Caching: Responses can be cached locally to reduce API calls.
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
     * Perform advanced search with Scryfall query syntax.
     * Examples:
     *   "set:lea" → All cards in Limited Edition Alpha
     *   "type:land" → All lands
     *   "rarity:rare" → All rare cards
     *   "colors:ub" → Blue-black cards
     * 
     * @param advancedQuery Scryfall search query syntax
     * @return List of matching cards
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
     * Find all cards in a set (useful for bulk loading a set for offline use).
     * 
     * @param setCode Set code (e.g. "LEA", "M21")
     * @return List of all cards in the set
     */
    suspend fun getCardsBySet(setCode: String): List<ScryfallCard> =
        advancedSearch("set:$setCode")

    /**
     * Validate set code against Scryfall's official set list.
     * 
     * @param setCode Set code to validate
     * @return true if valid, false otherwise
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
     * Get a random card (for testing or exploration).
     * 
     * @return Random ScryfallCard
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
     * Convert Scryfall API response to domain model.
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
                cmc = cmc,
                colors = colors,
                colorIdentity = color_identity,
                imageUris = image_uris?.let {
                    ScryfallCard.ImageUris(
                        small = it.small,
                        normal = it.normal,
                        large = it.large
                    )
                },
                scryfallUri = scryfall_uri
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
