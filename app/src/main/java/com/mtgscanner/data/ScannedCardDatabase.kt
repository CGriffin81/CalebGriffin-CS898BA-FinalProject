package com.mtgscanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Database
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.mtgscanner.model.ScannedCard
import kotlinx.coroutines.flow.Flow
import android.content.Context

/**
 * Room database entity representing a scanned Magic: The Gathering card in the local collection.
 * Maps to "scanned_cards" table with auto-incrementing primary key.
 *
 * @param id Auto-generated primary key (Long); 0 for new records to trigger autoincrement
 * @param scryfallId Unique UUID from Scryfall database for card identity
 * @param cardName Common card name (may differ from OCR output before normalization)
 * @param setCode Magic set abbreviation (e.g., "m21", "lea", "khm")
 * @param collectorNumber Position in set (typically 1-300+, used for uniqueness)
 * @param quantity Count of this physical card in the collection (default 1)
 * @param rarity Card rarity: "C" (common), "U" (uncommon), "R" (rare), "M" (mythic), or null
 * @param colors Comma-separated color abbreviations (e.g., "U,R" for blue+red; empty string for colorless)
 * @param typeLine Card type line (e.g., "Creature — Dragon", "Sorcery", "Land")
 * @param oracleText Oracle rules text of the card (full abilities description)
 * @param imageUrl URL to card image on Scryfall CDN (nullable; for loading card artwork)
 * @param scannedTimestamp Millisecond timestamp when card was added to collection
 * @param userConfirmed Boolean: whether user manually verified this card (vs. auto-detected)
 */
@Entity(tableName = "scanned_cards")
data class ScannedCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scryfallId: String,
    val cardName: String,
    val setCode: String,
    val collectorNumber: String,
    val quantity: Int = 1,
    val rarity: String? = null,
    val colors: String = "",
    val typeLine: String? = null,
    val oracleText: String? = null,
    val imageUrl: String? = null,
    val scannedTimestamp: Long = System.currentTimeMillis(),
    val userConfirmed: Boolean = false
) {
    val name: String
        get() = cardName

    val scannedAt: Long
        get() = scannedTimestamp

    /**
     * Convert this database entity to a domain model ScannedCard.
     * Bridges Room persistence layer with business logic model.
     *
     * @return ScannedCard domain object with identical fields
     */
    fun toScannedCard() = ScannedCard(
        id = id,
        scryfallId = scryfallId,
        cardName = cardName,
        setCode = setCode,
        collectorNumber = collectorNumber,
        quantity = quantity,
        rarity = rarity,
        colors = colors,
        typeLine = typeLine,
        oracleText = oracleText,
        imageUrl = imageUrl,
        scannedTimestamp = scannedTimestamp,
        userConfirmed = userConfirmed
    )
}

/**
 * Data Access Object for ScannedCard operations.
 * Provides CRUD operations, search, filtering, and collection statistics.
 * All suspend functions are coroutine-safe and run on Dispatcher.IO.
 * Flow-based functions provide reactive, real-time updates to the database.
 */
@Dao
interface ScannedCardDao {
    
    /**
     * Insert a new scanned card into the database.
     * Suspending function for insert safety and conflict handling.
     *
     * @param card ScannedCardEntity to insert
     * @return Long: Auto-generated primary key (ID) of the inserted card, or -1 if insertion failed
     */
    @Insert
    suspend fun insertCard(card: ScannedCardEntity): Long
    
    /**
     * Update an existing scanned card by ID.
     * Replaces all fields of the card with matching ID.
     *
     * @param card ScannedCardEntity with updated fields (ID must match existing record)
     */
    @Update
    suspend fun updateCard(card: ScannedCardEntity)
    
    /**
     * Delete a card from the collection by its entity.
     * Card must be an existing record (ID must match).
     *
     * @param card ScannedCardEntity to delete
     */
    @Delete
    suspend fun deleteCard(card: ScannedCardEntity)
    
    /**
     * Retrieve a single card by its primary key ID.
     *
     * @param cardId Primary key (Long) of the card to retrieve
     * @return ScannedCardEntity?: The card if found, null if not found
     */
    @Query("SELECT * FROM scanned_cards WHERE id = :cardId")
    suspend fun getCard(cardId: Long): ScannedCardEntity?
    
    /**
     * Retrieve all scanned cards ordered by most recent first.
     * Reactive Flow that emits updates whenever the database changes.
     *
     * @return Flow<List<ScannedCardEntity>>: Observable list of all cards, newest first
     */
    @Query("SELECT * FROM scanned_cards ORDER BY scannedTimestamp DESC")
    fun getAllCards(): Flow<List<ScannedCardEntity>>
    
    /**
     * Find a card by unique identity (Scryfall ID, set code, collector number).
     * Returns first matching card; enforces uniqueness via LIMIT 1.
     *
     * @param scryfallId Scryfall UUID
     * @param setCode Magic set code (3-4 characters, e.g., \"m21\", \"lea\")
     * @param collectorNumber Card number in set (typically 1-300+)
     * @return ScannedCardEntity?: The matching card if found, null if not in collection
     */
    @Query("SELECT * FROM scanned_cards WHERE scryfallId = :scryfallId AND setCode = :setCode AND collectorNumber = :collectorNumber LIMIT 1")
    suspend fun findCardByIdentity(scryfallId: String, setCode: String, collectorNumber: String): ScannedCardEntity?
    
    /**
     * Search for cards by name using substring matching (case-insensitive).
     * Reactive Flow for real-time search results.
     *
     * @param namePattern Substring to search for (% wildcards added automatically for LIKE)
     * @return Flow<List<ScannedCardEntity>>: Cards matching the name pattern, newest first\n     */
    @Query("SELECT * FROM scanned_cards WHERE cardName LIKE :namePattern ORDER BY scannedTimestamp DESC")
    fun searchByName(namePattern: String): Flow<List<ScannedCardEntity>>
    
    /**
     * Retrieve all cards from a specific Magic set.
     * Useful for set-based collection browsing and management.
     *
     * @param setCode Set code (e.g., \"m21\", \"lea\")
     * @return Flow<List<ScannedCardEntity>>: All cards in the set, sorted by collector number
     */
    @Query("SELECT * FROM scanned_cards WHERE setCode = :setCode ORDER BY collectorNumber")
    fun getCardsBySet(setCode: String): Flow<List<ScannedCardEntity>>
    
    /**
     * Get count of unique card entries in the collection.
     * Reactive Flow for real-time collection size updates.
     *
     * @return Flow<Int>: Number of unique card records in database
     */
    @Query("SELECT COUNT(*) FROM scanned_cards")
    fun getCollectionSize(): Flow<Int>
    
    /**
     * Get total card quantity across entire collection.
     * Sums up all quantity fields (useful for "you have X cards" stat).
     *
     * @return Flow<Int?>: Total card count (nullable; null if no cards in collection)
     */
    @Query("SELECT SUM(quantity) FROM scanned_cards")
    fun getTotalCards(): Flow<Int>
    
    /**
     * Delete all cards from the database.
     * WARNING: This is destructive and cannot be undone. Use with caution.
     *
     * @throws Exception if database operation fails
     */
    @Query("DELETE FROM scanned_cards")
    suspend fun clearAllCards()
}

@Database(entities = [ScannedCardEntity::class], version = 1, exportSchema = false)
abstract class ScannedCardDatabase : RoomDatabase() {
    abstract fun scannedCardDao(): ScannedCardDao

    companion object {
        @Volatile
        private var INSTANCE: ScannedCardDatabase? = null

        fun getInstance(context: Context): ScannedCardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScannedCardDatabase::class.java,
                    "scanned_cards.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
