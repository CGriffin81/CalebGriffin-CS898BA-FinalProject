package com.mtgscanner.model

/**
 * OCR output from a detected card image.
 * Contains text extracted from the card by OCR.
 */
data class DetectedCardText(
    val trackingId: Int,
    val cardName: String = "",
    val setCode: String = "",
    val collectorNumber: String = "",
    val ocrConfidence: Float = 0f,
    val rawOcrText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Scryfall card data model.
 * Represents a Magic card from the Scryfall database.
 * Subset of Scryfall API response fields relevant to identification and storage.
 */
data class ScryfallCard(
    val id: String,                        // Scryfall unique card ID (UUID)
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val rarity: String? = null,
    val typeLine: String? = null,
    val oracleText: String? = null,
    val colors: List<String> = emptyList(),
    val cmc: Float = 0f,
    val imageUris: ImageUris? = null,
    val scryfallUri: String = ""
) {
    data class ImageUris(
        val small: String? = null,
        val normal: String? = null,
        val large: String? = null,
        val png: String? = null,
        val artCrop: String? = null,
        val borderCrop: String? = null
    )
}

/**
 * Local storage entity for a scanned and catalogued Magic card.
 * Stored in Room database for offline access and collection management.
 */
data class ScannedCard(
    val id: Long = 0,
    val scryfallId: String,
    val cardName: String,
    val setCode: String,
    val collectorNumber: String,
    val quantity: Int = 1,
    val rarity: String? = null,
    val colors: String = "",              // Comma-separated: e.g., "U,R"
    val typeLine: String? = null,
    val oracleText: String? = null,
    val imageUrl: String? = null,
    val scannedTimestamp: Long = System.currentTimeMillis(),
    val userConfirmed: Boolean = false
) {
    /**
     * Unique identifier for this physical card in the collection.
     * Combines Scryfall ID with quantity so user can distinguish multiple copies.
     */
    fun uniqueKey(): String = "$scryfallId:$collectorNumber:$setCode"
}

/**
 * Fuzzy matching result when comparing OCR output to Scryfall candidates.
 */
data class CardMatchCandidate(
    val scryfallCard: ScryfallCard,
    val matchScore: Float,                 // 0.0 to 1.0, higher is better
    val matchReason: String = ""           // e.g., "name match", "set+collector match"
)

/**
 * User verification state during collection process.
 * User confirms a detected card and enters quantity before storage.
 */
data class CardVerification(
    val trackingId: Int,
    val detectedCardText: DetectedCardText,
    val matchCandidates: List<CardMatchCandidate>,
    val selectedCard: ScryfallCard? = null,
    val selectedQuantity: Int = 1,
    val verificationTimestamp: Long = System.currentTimeMillis(),
    val userAction: UserAction = UserAction.PENDING
) {
    enum class UserAction {
        PENDING,
        CONFIRMED,
        REJECTED,
        SKIPPED
    }
}
