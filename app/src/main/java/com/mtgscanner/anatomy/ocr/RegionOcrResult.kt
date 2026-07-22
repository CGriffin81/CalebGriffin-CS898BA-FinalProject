package com.mtgscanner.anatomy.ocr

/**
 * Structured result from a region-specific OCR reader.
 * Each reader produces domain-specific fields relevant to its region.
 */

/** Result from NameOcr — the card's title. */
data class NameOcrResult(
    val name: String,
    val confidence: Float,
    val rawText: String
)

/** Result from TypeLineOcr — supertypes, types, and subtypes. */
data class TypeLineOcrResult(
    val fullTypeLine: String,
    val supertypes: List<String>,
    val types: List<String>,
    val subtypes: List<String>,
    val confidence: Float,
    val rawText: String
)

/** Result from CollectorOcr — collector number, set code, rarity. */
data class CollectorOcrResult(
    val collectorNumber: String,
    val setCode: String,
    val rarity: String,
    val confidence: Float,
    val rawText: String
)

/** Result from RulesOcr — oracle text. */
data class RulesOcrResult(
    val oracleText: String,
    val confidence: Float,
    val rawText: String
)

/** Result from ArtistOcr — artist name. */
data class ArtistOcrResult(
    val artistName: String,
    val confidence: Float,
    val rawText: String
)

/** Result from PowerToughnessOcr — power and toughness values. */
data class PowerToughnessOcrResult(
    val power: String,
    val toughness: String,
    val confidence: Float,
    val rawText: String
)
