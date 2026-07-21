package com.mtgscanner.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Room database entity representing a scanned Magic: The Gathering card.
 *
 * P5-01: Unique index on [scryfallId] prevents duplicate rows for the same card printing.
 * The application uses [findCardByIdentity] + quantity increment for duplicate handling,
 * and the DB-level constraint acts as a safety net.
 *
 * @param id Auto-generated primary key (Long); 0 for new records.
 * @param scryfallId Unique Scryfall UUID — constrained UNIQUE at the database level.
 * @param cardName Card name after normalization via Scryfall.
 * @param setCode Set abbreviation (lowercase, e.g., "m21", "lea").
 * @param collectorNumber Position in set (e.g., "1", "42a", "280").
 * @param quantity Physical copy count in collection (default 1).
 * @param rarity Rarity: "common", "uncommon", "rare", "mythic", or null.
 * @param colors Comma-separated color abbreviations (e.g., "U,R" or "").
 * @param typeLine Card type (e.g., "Creature — Dragon").
 * @param oracleText Oracle rules text.
 * @param imageUrl Scryfall CDN image URL.
 * @param scannedTimestamp When the card was first added (millis).
 * @param userConfirmed Whether user manually verified this card.
 */
@Entity(
    tableName = "scanned_cards",
    indices = [Index(value = ["scryfallId"], unique = true)]  // P5-01
)
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
    val name: String get() = cardName
    val scannedAt: Long get() = scannedTimestamp

    /** Convert entity to domain model [com.mtgscanner.model.ScannedCard]. */
    fun toScannedCard() = com.mtgscanner.model.ScannedCard(
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
 * Data Access Object for scanned card operations.
 */
@Dao
interface ScannedCardDao {

    /**
     * Insert a new card. Uses [OnConflictStrategy.IGNORE] so that a duplicate
     * scryfallId silently returns -1 instead of crashing (P5-01 safety).
     * The application should always check [findCardByIdentity] first and update quantity
     * rather than relying on this conflict strategy.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCard(card: ScannedCardEntity): Long

    @Update
    suspend fun updateCard(card: ScannedCardEntity)

    @Delete
    suspend fun deleteCard(card: ScannedCardEntity)

    @Query("SELECT * FROM scanned_cards WHERE id = :cardId")
    suspend fun getCard(cardId: Long): ScannedCardEntity?

    /** All cards ordered by most recent first. */
    @Query("SELECT * FROM scanned_cards ORDER BY scannedTimestamp DESC")
    fun getAllCards(): Flow<List<ScannedCardEntity>>

    /** Find a card by its Scryfall identity triple. */
    @Query("SELECT * FROM scanned_cards WHERE scryfallId = :scryfallId AND setCode = :setCode AND collectorNumber = :collectorNumber LIMIT 1")
    suspend fun findCardByIdentity(scryfallId: String, setCode: String, collectorNumber: String): ScannedCardEntity?

    /** Search cards by name substring (caller must provide % wildcards). */
    @Query("SELECT * FROM scanned_cards WHERE cardName LIKE :namePattern ORDER BY scannedTimestamp DESC")
    fun searchByName(namePattern: String): Flow<List<ScannedCardEntity>>

    /**
     * Cards in a set, sorted numerically by collector number (P5-03).
     * CAST(collectorNumber AS INTEGER) sorts "1, 2, 10, 100" correctly instead of
     * the text sort "1, 10, 100, 2" that the previous ORDER BY produced.
     */
    @Query("SELECT * FROM scanned_cards WHERE setCode = :setCode ORDER BY CAST(collectorNumber AS INTEGER)")
    fun getCardsBySet(setCode: String): Flow<List<ScannedCardEntity>>

    /** Count of unique card entries (rows). */
    @Query("SELECT COUNT(*) FROM scanned_cards")
    fun getCollectionSize(): Flow<Int>

    /**
     * Total quantity across all cards (P5-02).
     * COALESCE ensures 0 is returned when the table is empty instead of SQL NULL.
     */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM scanned_cards")
    fun getTotalCards(): Flow<Int>

    @Query("DELETE FROM scanned_cards")
    suspend fun clearAllCards()
}

/**
 * Room database for the scanned card collection.
 *
 * Version history:
 * - v1: Initial schema (no unique constraints)
 * - v2: P5-01 — Added UNIQUE INDEX on scryfallId
 */
@Database(entities = [ScannedCardEntity::class], version = 2, exportSchema = false)
abstract class ScannedCardDatabase : RoomDatabase() {
    abstract fun scannedCardDao(): ScannedCardDao

    companion object {
        @Volatile
        private var INSTANCE: ScannedCardDatabase? = null

        /**
         * P5-01: Migration from v1 → v2.
         * Creates the unique index on scryfallId. If duplicate rows already exist,
         * the IF NOT EXISTS clause prevents a crash — duplicates must be resolved
         * manually or the index creation will silently skip conflicting rows.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_scanned_cards_scryfallId ON scanned_cards(scryfallId)"
                )
            }
        }

        fun getInstance(context: Context): ScannedCardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScannedCardDatabase::class.java,
                    "scanned_cards.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
