package com.mtgscanner.network

/**
 * Scryfall API models for card data from https://api.scryfall.com
 */

data class ScryfallCardResponse(
    val id: String,
    val name: String,
    val set: String,
    val collector_number: String,
    val rarity: String? = null,
    val type_line: String? = null,
    val mana_cost: String? = null,
    val cmc: Double? = null,
    val colors: List<String>? = null,
    val color_identity: List<String>? = null,
    val image_uris: ImageUrisResponse? = null,
    val lang: String? = null,
    val scryfall_uri: String? = null
)

data class ImageUrisResponse(
    val small: String? = null,
    val normal: String? = null,
    val large: String? = null,
    val png: String? = null,
    val art_crop: String? = null,
    val border_crop: String? = null
)

data class ScryfallSearchResponse(
    val object: String,  // "list"
    val total_cards: Int,
    val has_more: Boolean,
    val next_page: String? = null,
    val data: List<ScryfallCardResponse>
)

data class ScryfallError(
    val object: String,  // "error"
    val code: String,
    val status: Int,
    val type: String? = null,
    val details: String? = null
)
