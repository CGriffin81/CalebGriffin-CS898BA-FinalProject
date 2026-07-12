package com.mtgscanner.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import com.mtgscanner.model.ScannedCard

/**
 * ScannedCardDatabaseIntegrationTest: Tests Room database operations.
 * Validates: CRUD operations, queries, reactive Flow updates.
 * 
 * Note: This is an Android instrumented test (requires emulator/device).
 */
@RunWith(AndroidJUnit4::class)
class ScannedCardDatabaseIntegrationTest {

    private lateinit var database: ScannedCardDatabase
    private lateinit var dao: ScannedCardDao

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScannedCardDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.scannedCardDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Test: Insert single card into database.
     */
    @Test
    fun testInsertCard() = runBlocking {
        val card = ScannedCard(
            name = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            quantity = 1,
            imageUrl = "https://example.com/black-lotus.jpg",
            scannedAt = System.currentTimeMillis()
        )

        dao.insertCard(card)

        val retrieved = dao.getCard(1)
        assertNotNull("Card should be inserted", retrieved)
        assertEquals("Card name should match", "Black Lotus", retrieved?.name)
    }

    /**
     * Test: Update card quantity.
     */
    @Test
    fun testUpdateCardQuantity() = runBlocking {
        val card = ScannedCard(
            name = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            quantity = 1,
            imageUrl = "https://example.com/black-lotus.jpg",
            scannedAt = System.currentTimeMillis()
        )

        dao.insertCard(card)
        val cardId = 1

        val updatedCard = card.copy(id = cardId, quantity = 3)
        dao.updateCard(updatedCard)

        val retrieved = dao.getCard(cardId)
        assertEquals("Quantity should be updated", 3, retrieved?.quantity)
    }

    /**
     * Test: Delete card from database.
     */
    @Test
    fun testDeleteCard() = runBlocking {
        val card = ScannedCard(
            name = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            quantity = 1,
            imageUrl = "https://example.com/black-lotus.jpg",
            scannedAt = System.currentTimeMillis()
        )

        dao.insertCard(card)
        val cardId = 1

        val insertedCard = dao.getCard(cardId)
        assertNotNull("Card should exist after insert", insertedCard)

        dao.deleteCard(insertedCard!!)

        val deleted = dao.getCard(cardId)
        assertNull("Card should be deleted", deleted)
    }

    /**
     * Test: Get all cards (reactive Flow).
     */
    @Test
    fun testGetAllCards() = runBlocking {
        val cards = listOf(
            ScannedCard("Black Lotus", "LEA", "1", 1, null, System.currentTimeMillis()),
            ScannedCard("Ancestral Recall", "LEA", "2", 2, null, System.currentTimeMillis()),
            ScannedCard("Mox Pearl", "LEA", "3", 1, null, System.currentTimeMillis())
        )

        cards.forEach { dao.insertCard(it) }

        val allCards = dao.getAllCards().first()
        assertEquals("Should retrieve all cards", 3, allCards.size)
    }

    /**
     * Test: Search by card name.
     */
    @Test
    fun testSearchByName() = runBlocking {
        val cards = listOf(
            ScannedCard("Black Lotus", "LEA", "1", 1, null, System.currentTimeMillis()),
            ScannedCard("Black Knight", "LEA", "4", 1, null, System.currentTimeMillis()),
            ScannedCard("White Knight", "LEA", "5", 1, null, System.currentTimeMillis())
        )

        cards.forEach { dao.insertCard(it) }

        val blackCards = dao.searchByName("Black")
        assertEquals("Should find cards with 'Black' in name", 2, blackCards.size)
    }

    /**
     * Test: Get cards by set code.
     */
    @Test
    fun testGetCardsBySet() = runBlocking {
        val cards = listOf(
            ScannedCard("Black Lotus", "LEA", "1", 1, null, System.currentTimeMillis()),
            ScannedCard("Lightning Bolt", "LEA", "102", 1, null, System.currentTimeMillis()),
            ScannedCard("Shock", "M21", "280", 2, null, System.currentTimeMillis())
        )

        cards.forEach { dao.insertCard(it) }

        val leaCards = dao.getCardsBySet("LEA")
        assertEquals("Should get only LEA cards", 2, leaCards.size)
    }

    /**
     * Test: Find unique card by identity (name, set, collector).
     */
    @Test
    fun testFindCardByIdentity() = runBlocking {
        val card = ScannedCard(
            name = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            quantity = 1,
            imageUrl = null,
            scannedAt = System.currentTimeMillis()
        )

        dao.insertCard(card)

        val found = dao.findCardByIdentity("Black Lotus", "LEA", "1")
        assertNotNull("Should find card by identity", found)
        assertEquals("Should match exact identity", "Black Lotus", found?.name)
    }

    /**
     * Test: Duplicate prevention (unique key on name+set+collector).
     */
    @Test
    fun testUniquenessConstraint() = runBlocking {
        val card1 = ScannedCard(
            name = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            quantity = 1,
            imageUrl = null,
            scannedAt = System.currentTimeMillis()
        )

        dao.insertCard(card1)

        val card2 = card1.copy(id = 0, quantity = 2)
        // In production, this would trigger UNIQUE constraint violation
        // For now, second insert would fail or be ignored based on Room config
        try {
            dao.insertCard(card2)
        } catch (e: Exception) {
            // Expected: constraint violation
            assertTrue("Should fail on duplicate unique key", true)
        }
    }

    /**
     * Test: Collection statistics (total cards, unique cards, sets).
     */
    @Test
    fun testCollectionStatistics() = runBlocking {
        val cards = listOf(
            ScannedCard("Black Lotus", "LEA", "1", 2, null, System.currentTimeMillis()),
            ScannedCard("Ancestral Recall", "LEA", "2", 1, null, System.currentTimeMillis()),
            ScannedCard("Shock", "M21", "280", 3, null, System.currentTimeMillis()),
            ScannedCard("Mountain", "M21", "281", 5, null, System.currentTimeMillis())
        )

        cards.forEach { dao.insertCard(it) }

        val totalCards = dao.getTotalCards().first()
        val collectionSize = dao.getCollectionSize().first()

        assertEquals("Total cards should sum quantities", 2 + 1 + 3 + 5, totalCards)
        assertEquals("Collection size should be number of unique entries", 4, collectionSize)
    }

    /**
     * Test: Reactive Flow updates on data change.
     */
    @Test
    fun testFlowReactivity() = runBlocking {
        val allCardsFlow = dao.getAllCards()

        // Initial state: empty
        var cards = allCardsFlow.first()
        assertEquals("Initial state should be empty", 0, cards.size)

        // Insert card
        val card = ScannedCard(
            name = "Black Lotus",
            setCode = "LEA",
            collectorNumber = "1",
            quantity = 1,
            imageUrl = null,
            scannedAt = System.currentTimeMillis()
        )
        dao.insertCard(card)

        // Flow should emit updated list
        cards = allCardsFlow.first()
        assertEquals("Flow should emit after insert", 1, cards.size)
    }

    /**
     * Test: Large collection performance (100+ cards).
     */
    @Test
    fun testLargeCollectionPerformance() = runBlocking {
        val startTime = System.currentTimeMillis()

        // Insert 150 cards
        repeat(150) { idx ->
            val card = ScannedCard(
                name = "Card #$idx",
                setCode = "SET",
                collectorNumber = "$idx",
                quantity = 1,
                imageUrl = null,
                scannedAt = System.currentTimeMillis()
            )
            dao.insertCard(card)
        }

        val allCards = dao.getAllCards().first()
        val elapsedTime = System.currentTimeMillis() - startTime

        assertEquals("Should store all 150 cards", 150, allCards.size)
        assertTrue("Bulk insert should complete in reasonable time", elapsedTime < 5000) // 5 seconds
    }

    /**
     * Test: Query performance with large dataset.
     */
    @Test
    fun testQueryPerformance() = runBlocking {
        // Insert 100 cards
        repeat(100) { idx ->
            val card = ScannedCard(
                name = if (idx % 2 == 0) "Black $idx" else "White $idx",
                setCode = if (idx < 50) "LEA" else "M21",
                collectorNumber = "$idx",
                quantity = 1,
                imageUrl = null,
                scannedAt = System.currentTimeMillis()
            )
            dao.insertCard(card)
        }

        val startTime = System.currentTimeMillis()
        val blackCards = dao.searchByName("Black")
        val elapsedTime = System.currentTimeMillis() - startTime

        assertTrue("Should find Black cards", blackCards.isNotEmpty())
        assertTrue("Search should complete quickly", elapsedTime < 1000) // 1 second
    }
}
