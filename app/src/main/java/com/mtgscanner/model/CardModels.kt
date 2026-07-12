package com.mtgscanner.model

/**
 * OCR output from a detected card image.
 * Contains text extracted from the card by ML Kit OCR, with confidence scoring.
 *
 * @param trackingId Card tracking ID from DetectionPipeline (for linking to detection)
 * @param cardName Extracted card name from OCR (may contain errors/noise)
 * @param setCode Extracted Magic set code (3-4 chars, may be empty or incorrect)
 * @param collectorNumber Extracted collector number (card position in set, may be noise)
 * @param ocrConfidence Overall OCR confidence (0.0-1.0); < 0.6 triggers region-specific fallback
 * @param rawOcrText Complete raw OCR text from full card image (for fallback analysis)
 * @param timestamp Millisecond timestamp when OCR was performed
 */
data class DetectedCardText(
    val trackingId: Int,
    val cardName: String = "",
    val setCode: String = "",
    val collectorNumber: String = "",
    val ocrConfidence: Float = 0f,
    val rawOcrText: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val confidence: Float
        get() = ocrConfidence
}

/**
 * Scryfall API card data model.
 * Represents a Magic card from the authoritative Scryfall database.
 * Subset of Scryfall response fields most relevant to MTG Scanner (identification, image, text).
 *
 * @param id Scryfall unique card UUID (e.g., "a1cd8cbf-c4b3-41c7-9b78-2e2c...")
 * @param name Official card name
 * @param setCode Magic set abbreviation (e.g., "m21" for Magic 2021, "lea" for Limited Edition Alpha)
 * @param collectorNumber Card's position in the set (1, 2, 3, ..., typically up to 300+)
 * @param rarity Rarity grade: "C" (common), "U" (uncommon), "R" (rare), "M" (mythic), "S" (special), or null
 * @param typeLine Card's type line (e.g., "Creature — Dragon", "Sorcery", "Land — Mountain")
 * @param oracleText Card's Oracle rules text (complete ability descriptions)
 * @param colors List of mana color characters (e.g., ["U"] for blue, ["U", "R"] for blue+red)
 * @param cmc Converted mana cost (floating point for hybrid mana cards)
 * @param imageUris Nested ImageUris object with links to card artwork on Scryfall CDN
 * @param scryfallUri Direct link to card on Scryfall.com for reference
 */
data class ScryfallCard(
    val id: String,                        // Scryfall unique card ID (UUID)
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val rarity: String? = null,
    val typeLine: String? = null,
    val oracleText: String? = null,
    val manaCost: String? = null,
    val colorIdentity: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val cmc: Float = 0f,
    val imageUris: ImageUris? = null,
    val scryfallUri: String = ""
) {
    /**
     * Image URIs for card artwork from Scryfall CDN.
     * Provides multiple sizes and crops for different UI contexts (thumbnails, full display, etc).
     *
     * @param small Small thumbnail (146×204px)
     * @param normal Standard image (488×680px)
     * @param large High-res image (672×936px)
     * @param png PNG format (highest quality, often 1000×1400px)
     * @param artCrop Cropped art-only version (remove border, typically 1000×560px)
     * @param borderCrop Full card with border cropped
     */
    data class ImageUris(
        val small: String? = null,
        val normal: String? = null,
        val large: String? = null,
        val png: String? = null,
        val artCrop: String? = null,
        val borderCrop: String? = null
    )
}

typealias UserAction = CardVerification.UserAction

/**
 * Local collection card entity stored in Room database.
 * Mirrors ScryfallCard data plus user-specific fields for inventory management.
 *
 * @param id Auto-generated Room primary key (Long); 0 for new records
 * @param scryfallId Scryfall UUID linking to canonical card definition
 * @param cardName Card name (may differ from OCR output after normalization via Scryfall)
 * @param setCode Set abbreviation (3-4 chars; part of card identity key)
 * @param collectorNumber Card number in set (part of card identity key)
 * @param quantity Number of physical copies user owns (default 1)
 * @param rarity Rarity field from Scryfall (C/U/R/M/S or null)
 * @param colors Comma-separated color abbreviations (e.g., "U,R" for blue+red; "" for colorless)
 * @param typeLine Card type line (Creature, Sorcery, etc.)
 * @param oracleText Full Oracle abilities text
 * @param imageUrl URL to card image for display
 * @param scannedTimestamp When card was added to collection (for sorting, statistics)
 * @param userConfirmed Whether user manually verified card (vs. auto-detected)
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
     * Generate unique identifier for this physical card in the collection.
     * Combines Scryfall ID with collector number and set code to allow
     * distinguishing multiple copies of the same card from different sets or printings.
     *
     * @return String formatted as "scryfallId:collectorNumber:setCode"
     */
    fun uniqueKey(): String = "$scryfallId:$collectorNumber:$setCode"
}

/**
 * Fuzzy matching result for OCR output matched against Scryfall candidates.
 * Represents a potential match with confidence score and reason code.
 * Candidates are ranked and presented to user for verification.
 *
 * @param scryfallCard The ScryfallCard that was matched
 * @param matchScore Weighted confidence score from 0.0 (no match) to 1.0 (perfect match)
 *                   Scoring: 60% name similarity (Levenshtein), 20% set code match, 20% collector number match
 * @param matchReason Human-readable reason for the match (for UI/debugging): e.g., "name", "exact identity", "fuzzy name"
 */
data class CardMatchCandidate(
    val scryfallCard: ScryfallCard,
    val matchScore: Float,                 // 0.0 to 1.0, higher is better
    val matchReason: String = ""           // e.g., "name match", "set+collector match"
)

/**
 * User verification state during card collection process.
 * Represents a detected card pending user confirmation and storage.
 * Includes OCR results, Scryfall matches, error states, and offline status for resilient error handling.
 *
 * Complete data flow: DetectedCardText → FuzzyMatcher → List<CardMatchCandidate> → VerificationScreen → CardVerification (confirmed) → Database
 *
 * @param trackingId Card tracking ID from detection pipeline (links back to frame detection)
 * @param detectedCardText Raw OCR output from card detection
 * @param matchCandidates List of fuzzy-matched Scryfall cards (sorted by matchScore descending)
 * @param selectedCard User-selected ScryfallCard from candidates (null until user confirms)
 * @param selectedQuantity Number of copies user wants to add (default 1, customizable in UI)
 * @param verificationTimestamp When user started verification (for analytics)
 * @param userAction User's action on this card: PENDING/CONFIRMED/REJECTED/SKIPPED
 * @param errorMessage Human-readable error message if operation failed (null if no error)
 * @param isOffline Boolean: true if API was unavailable during detection (affects storage strategy)
 * @param ocrConfidence OCR confidence from DetectedCardText (< 0.6 shows warning to user)
 */
data class CardVerification(
    val trackingId: Int,
    val detectedCardText: DetectedCardText,
    val matchCandidates: List<CardMatchCandidate>,
    val selectedCard: ScryfallCard? = null,
    val selectedQuantity: Int = 1,
    val verificationTimestamp: Long = System.currentTimeMillis(),
    val userAction: UserAction = UserAction.PENDING,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val ocrConfidence: Float = detectedCardText.ocrConfidence
) {
    /**
     * User's action on the verification card.
     * Determines whether card gets stored, discarded, or awaits action.
     */
    enum class UserAction {
        PENDING,      // Awaiting user action
        CONFIRMED,    // User confirmed and card is stored to database
        REJECTED,     // User rejected card (not stored)
        SKIPPED       // User skipped for later review
    }
}
