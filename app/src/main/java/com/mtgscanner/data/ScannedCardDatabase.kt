package com.mtgscanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.mtgscanner.model.ScannedCard
import kotlinx.coroutines.flow.Flow

/**
 * Room entity for ScannedCard persistence.
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
 */
@Dao
interface ScannedCardDao {
    
    @Insert
    suspend fun insertCard(card: ScannedCardEntity): Long
    
    @Update
    suspend fun updateCard(card: ScannedCardEntity)
    
    @Delete
    suspend fun deleteCard(card: ScannedCardEntity)
    
    @Query("SELECT * FROM scanned_cards WHERE id = :cardId")
    suspend fun getCard(cardId: Long): ScannedCardEntity?
    
    @Query("SELECT * FROM scanned_cards ORDER BY scannedTimestamp DESC")
    fun getAllCards(): Flow<List<ScannedCardEntity>>
    
    @Query("SELECT * FROM scanned_cards WHERE scryfallId = :scryfallId AND setCode = :setCode AND collectorNumber = :collectorNumber LIMIT 1")
    suspend fun findCardByIdentity(scryfallId: String, setCode: String, collectorNumber: String): ScannedCardEntity?
    
    @Query("SELECT * FROM scanned_cards WHERE cardName LIKE :namePattern ORDER BY scannedTimestamp DESC")
    fun searchByName(namePattern: String): Flow<List<ScannedCardEntity>>
    
    @Query("SELECT * FROM scanned_cards WHERE setCode = :setCode ORDER BY collectorNumber")
    fun getCardsBySet(setCode: String): Flow<List<ScannedCardEntity>>
    
    @Query("SELECT COUNT(*) FROM scanned_cards")
    fun getCollectionSize(): Flow<Int>
    
    @Query("SELECT SUM(quantity) FROM scanned_cards")
    fun getTotalCards(): Flow<Int>
    
    @Query("DELETE FROM scanned_cards")
    suspend fun clearAllCards()
}
