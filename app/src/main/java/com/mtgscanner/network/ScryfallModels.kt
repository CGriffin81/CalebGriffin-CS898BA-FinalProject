package com.mtgscanner.network

/**
 * Scryfall API response models for card data from https://api.scryfall.com
 *
 * P4-01: Added oracle_text and card_faces support for dual-faced cards (DFCs).
 * DFCs (Werewolves, Modal DFCs, Transform cards) return null for top-level image_uris
 * and oracle_text — all face-specific data is in the card_faces array.
 */

data class ScryfallCardResponse(
    val id: String,
    val name: String,
    val set: String,
    val collector_number: String,
    val rarity: String? = null,
    val type_line: String? = null,
    val oracle_text: String? = null,           // P4-01: Card abilities text
    val mana_cost: String? = null,
    val cmc: Double? = null,
    val colors: List<String>? = null,
    val color_identity: List<String>? = null,
    val image_uris: ImageUrisResponse? = null,
    val card_faces: List<CardFaceResponse>? = null,  // P4-01: DFC face data
    val lang: String? = null,
    val scryfall_uri: String? = null
)

/**
 * P4-01: Individual face of a dual-faced or split card.
 * Contains face-specific image URIs, oracle text, and type line.
 */
data class CardFaceResponse(
    val name: String? = null,
    val image_uris: ImageUrisResponse? = null,
    val oracle_text: String? = null,
    val type_line: String? = null,
    val mana_cost: String? = null
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
    val `object`: String,  // "list"
    val total_cards: Int,
    val has_more: Boolean,
    val next_page: String? = null,
    val data: List<ScryfallCardResponse>
)

data class ScryfallError(
    val `object`: String,  // "error"
    val code: String,
    val status: Int,
    val type: String? = null,
    val details: String? = null
)
