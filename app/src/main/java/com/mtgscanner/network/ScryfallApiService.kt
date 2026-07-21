package com.mtgscanner.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Call

/**
 * ScryfallApiService: Retrofit interface for Scryfall API endpoints.
 * API Base: https://api.scryfall.com
 */
interface ScryfallApiService {

    /**
     * Search for cards by fuzzy query (name, set code, collector number).
     * 
     * @param q Query string (e.g. "Black Lotus", "exact:\"Black Lotus\"", "set:lea cn:1")
     * @return List of matching cards with pagination
     * 
     * Docs: https://scryfall.com/docs/api/cards/search
     */
    @GET("/cards/search")
    fun searchCards(
        @Query("q") q: String,
        @Query("unique") unique: String = "cards",  // Return one print per card
        @Query("order") order: String = "released",  // Sort by release date
        @Query("dir") dir: String = "desc",  // Most recent first
        @Query("page") page: Int = 1
    ): Call<ScryfallSearchResponse>

    /**
     * Get a card by exact name match.
     * 
     * @param exactName Exact card name (e.g. "Black Lotus")
     * @param setCode Optional set code filter (e.g. "LEA", "M21")
     * @return Single card or 404 if not found
     * 
     * Docs: https://scryfall.com/docs/api/cards/named
     */
    @GET("/cards/named")
    fun getCardByName(
        @Query("exact") exactName: String,
        @Query("set") setCode: String? = null
    ): Call<ScryfallCardResponse>

    /**
     * Get a card by fuzzy name match (handles typos).
     * 
     * @param fuzzyName Fuzzy card name (e.g. "black lotus" → "Black Lotus")
     * @param setCode Optional set code filter
     * @return Single best match or 404
     * 
     * Docs: https://scryfall.com/docs/api/cards/named
     */
    @GET("/cards/named")
    fun getCardByFuzzyName(
        @Query("fuzzy") fuzzyName: String,
        @Query("set") setCode: String? = null
    ): Call<ScryfallCardResponse>

    /**
     * Get a card by set code and collector number.
     *
     * Uses Scryfall's collector endpoint: GET /cards/{setCode}/{collectorNumber}
     * This is the most precise lookup when both identifiers are known from OCR.
     *
     * @param setCode Set code (e.g. "lea", "m21") — path parameter, case-insensitive on Scryfall.
     * @param collectorNumber Collector number (e.g. "1", "102", "280a") — path parameter.
     * @return Single card or 404 if not found.
     *
     * Docs: https://scryfall.com/docs/api/cards/collector
     */
    @GET("/cards/{setCode}/{collectorNumber}")
    fun getCardByCollectorNumber(
        @Path("setCode") setCode: String,
        @Path("collectorNumber") collectorNumber: String
    ): Call<ScryfallCardResponse>

    /**
     * Search with advanced query syntax.
     * Supports filters like: set:lea is:unique rarity:rare
     * 
     * @param q Advanced query (e.g. "set:lea cn:1..50")
     * @param page Pagination page (default 1)
     * @return List of cards matching filters
     * 
     * Docs: https://scryfall.com/docs/api/cards/search
     */
    @GET("/cards/search")
    fun advancedSearch(
        @Query("q") q: String,
        @Query("page") page: Int = 1
    ): Call<ScryfallSearchResponse>

    /**
     * Get a random card (for testing or exploration).
     * 
     * @return Random card
     * 
     * Docs: https://scryfall.com/docs/api/cards/random
     */
    @GET("/cards/random")
    fun getRandomCard(): Call<ScryfallCardResponse>

    /**
     * Get all sets (for set code validation and metadata).
     * 
     * @return List of all Magic sets
     * 
     * Docs: https://scryfall.com/docs/api/sets
     */
    @GET("/sets")
    fun getAllSets(): Call<ScryfallSetsResponse>
}

/**
 * ScryfallSetsResponse: Response model for /sets endpoint.
 */
data class ScryfallSetsResponse(
    val `object`: String,  // "list"
    val data: List<ScryfallSet>
)

/**
 * ScryfallSet: Metadata for a Magic set.
 */
data class ScryfallSet(
    val id: String,
    val code: String,
    val name: String,
    val released_at: String? = null,
    val type: String? = null,
    val icon_svg_uri: String? = null
)
