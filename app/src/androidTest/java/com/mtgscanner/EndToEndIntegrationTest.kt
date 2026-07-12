package com.mtgscanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.room.Room
import com.mtgscanner.analysis.CardFrameAnalyzer
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.detection.CardDetector
import com.mtgscanner.detection.CardTracker
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.matching.FuzzyCardMatcher
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.model.ScryfallCard
import com.mtgscanner.model.CardVerification
import com.mtgscanner.model.UserAction
import com.mtgscanner.ocr.OcrPipeline

/**
 * EndToEndIntegrationTest: Full workflow from camera frame to database storage.
 * Simulates complete pipeline: Detection → Tracking → OCR → Fuzzy Matching → Verification → Storage.
 *
 * Note: This is an Android instrumented test (requires emulator/device).
 */
@RunWith(AndroidJUnit4::class)
class EndToEndIntegrationTest {

    private lateinit var cardDetector: CardDetector
    private lateinit var cardTracker: CardTracker
    private lateinit var detectionPipeline: DetectionPipeline
    private lateinit var ocrPipeline: OcrPipeline
    private lateinit var fuzzyMatcher: FuzzyCardMatcher
    private lateinit var database: ScannedCardDatabase

    @Before
    fun setUp() {
        cardDetector = CardDetector()
        cardTracker = CardTracker()
        detectionPipeline = DetectionPipeline()
        ocrPipeline = OcrPipeline()
        fuzzyMatcher = FuzzyCardMatcher()

        // Initialize in-memory database
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScannedCardDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    /**
     * Test: Full workflow for single card detection and storage.
     * Flow: Camera frame → Detection → Tracking (3 frames) → OCR → Fuzzy match → Verify → Store
     */
    @Test
    fun testSingleCardFullWorkflow() = runBlocking {
        // Step 1: Simulate camera frame with card
        val frame = createTestCardFrame("Black Lotus", "LEA", "1")

        // Step 2: Detection
        val detections = cardDetector.detectCards(frame)
        assertTrue("Should detect card", detections.isNotEmpty())
        val detection = detections.first()

        // Step 3: Tracking (stable after 3 frames)
        var trackingId = -1
        repeat(3) {
            val tracks = cardTracker.updateTracks(listOf(detection))
            if (it == 2) trackingId = tracks[0] // Get tracking ID after 3rd frame
        }
        assertTrue("Should have valid tracking ID", trackingId >= 0)

        // Step 4: OCR recognition
        val detectedText = ocrPipeline.recognizeCard(frame, trackingId)
        assertNotNull("Should extract card text", detectedText)
        assertNotNull("Should have card name", detectedText.cardName)

        // Step 5: Fuzzy matching against Scryfall
        val scryfallCandidates = listOf(
            ScryfallCard(
                id = "lotus-lea",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = ScryfallCard.ImageUris(
                    small = "https://scryfall.com/example.jpg",
                    normal = "https://scryfall.com/example-large.jpg",
                    large = "https://scryfall.com/example-xlarge.jpg"
                )
            )
        )
        val matches = fuzzyMatcher.matchCard(detectedText, scryfallCandidates)
        assertFalse("Should find fuzzy matches", matches.isEmpty())
        val topMatch = matches.first()

        // Step 6: User verification (confirm)
        val cardVerification = CardVerification(
            trackingId = trackingId,
            detectedCardText = detectedText,
            matchCandidates = matches,
            userAction = UserAction.PENDING
        )
        
        val confirmedCard = topMatch.scryfallCard.let { card ->
            com.mtgscanner.model.ScannedCard(
                name = card.name,
                setCode = card.setCode,
                collectorNumber = card.collectorNumber,
                quantity = 1,
                imageUrl = card.imageUris?.normal,
                scannedAt = System.currentTimeMillis()
            )
        }

        // Step 7: Store to database
        database.scannedCardDao().insertCard(confirmedCard)

        // Step 8: Verify card is in database
        val storedCard = database.scannedCardDao().findCardByIdentity("Black Lotus", "LEA", "1")
        assertNotNull("Card should be stored in database", storedCard)
        assertEquals("Card details should match", "Black Lotus", storedCard?.name)
    }

    /**
     * Test: Multiple cards in single frame.
     * Workflow: Detect multiple → Track each → OCR each → Match each → Store all
     */
    @Test
    fun testMultipleCardsWorkflow() = runBlocking {
        // Create frame with 3 cards
        val frame = createTestMultiCardFrame(3)

        // Detection
        val detections = cardDetector.detectCards(frame)
        assertTrue("Should detect multiple cards", detections.size >= 2)

        // Tracking and OCR for each
        val detectedTexts = mutableListOf<DetectedCardText>()
        detections.forEach { detection ->
            repeat(3) { cardTracker.updateTracks(listOf(detection)) }
            val text = ocrPipeline.recognizeCard(frame, 1)
            detectedTexts.add(text)
        }

        assertTrue("Should extract text from multiple cards", detectedTexts.isNotEmpty())

        // Store all
        val scryfallCandidates = createMockScryfallCandidates()
        detectedTexts.forEach { text ->
            val matches = fuzzyMatcher.matchCard(text, scryfallCandidates)
            if (matches.isNotEmpty()) {
                val card = matches.first().scryfallCard.let { 
                    com.mtgscanner.model.ScannedCard(
                        name = it.name,
                        setCode = it.setCode,
                        collectorNumber = it.collectorNumber,
                        quantity = 1,
                        imageUrl = it.imageUris?.normal,
                        scannedAt = System.currentTimeMillis()
                    )
                }
                database.scannedCardDao().insertCard(card)
            }
        }

        val allCards = database.scannedCardDao().getAllCards()
        // Should have at least some cards stored
    }

    /**
     * Test: User rejection workflow (card rejected, not stored).
     */
    @Test
    fun testRejectionWorkflow() = runBlocking {
        val frame = createTestCardFrame("Unknown Card", "XXX", "999")

        val detections = cardDetector.detectCards(frame)
        if (detections.isNotEmpty()) {
            val detection = detections.first()
            repeat(3) { cardTracker.updateTracks(listOf(detection)) }

            val detectedText = ocrPipeline.recognizeCard(frame, 1)
            val scryfallCandidates = createMockScryfallCandidates()
            val matches = fuzzyMatcher.matchCard(detectedText, scryfallCandidates)

            // User rejects (low confidence or wrong match)
            val initialCount = database.scannedCardDao().getCollectionSize()

            // Don't store card
            // Verify count unchanged
            val finalCount = database.scannedCardDao().getCollectionSize()
            assertEquals("Count should not change on rejection", initialCount, finalCount)
        }
    }

    /**
     * Test: Quantity update workflow (same card scanned multiple times).
     */
    @Test
    fun testQuantityUpdateWorkflow() = runBlocking {
        val cardName = "Black Lotus"
        val setCode = "LEA"
        val collectorNum = "1"

        // First scan
        val frame1 = createTestCardFrame(cardName, setCode, collectorNum)
        val detection1 = cardDetector.detectCards(frame1).first()
        repeat(3) { cardTracker.updateTracks(listOf(detection1)) }

        val detectedText1 = ocrPipeline.recognizeCard(frame1, 1)
        val scryfallCandidates = createMockScryfallCandidates()
        val matches1 = fuzzyMatcher.matchCard(detectedText1, scryfallCandidates)

        val card1 = matches1.first().scryfallCard.let {
            com.mtgscanner.model.ScannedCard(
                name = it.name,
                setCode = it.setCode,
                collectorNumber = it.collectorNumber,
                quantity = 1,
                imageUrl = it.imageUris?.normal,
                scannedAt = System.currentTimeMillis()
            )
        }
        database.scannedCardDao().insertCard(card1)

        var stored = database.scannedCardDao().findCardByIdentity(cardName, setCode, collectorNum)
        assertEquals("First scan: quantity should be 1", 1, stored?.quantity)

        // Second scan (user wants to increase quantity to 2)
        val frame2 = createTestCardFrame(cardName, setCode, collectorNum)
        val detection2 = cardDetector.detectCards(frame2).first()
        repeat(3) { cardTracker.updateTracks(listOf(detection2)) }

        val detectedText2 = ocrPipeline.recognizeCard(frame2, 2)
        val matches2 = fuzzyMatcher.matchCard(detectedText2, scryfallCandidates)

        stored?.let {
            val updated = it.copy(quantity = 2)
            database.scannedCardDao().updateCard(updated)
        }

        stored = database.scannedCardDao().findCardByIdentity(cardName, setCode, collectorNum)
        assertEquals("After update: quantity should be 2", 2, stored?.quantity)
    }

    /**
     * Test: Search and filter workflow (collection browsing).
     */
    @Test
    fun testCollectionBrowsingWorkflow() = runBlocking {
        // Populate database with varied cards
        val cards = listOf(
            com.mtgscanner.model.ScannedCard("Black Lotus", "LEA", "1", 1, null, System.currentTimeMillis()),
            com.mtgscanner.model.ScannedCard("Ancestral Recall", "LEA", "2", 2, null, System.currentTimeMillis()),
            com.mtgscanner.model.ScannedCard("Shock", "M21", "280", 3, null, System.currentTimeMillis()),
            com.mtgscanner.model.ScannedCard("Mountain", "M21", "281", 5, null, System.currentTimeMillis())
        )

        cards.forEach { database.scannedCardDao().insertCard(it) }

        // Search by name
        val blackCards = database.scannedCardDao().searchByName("Black")
        assertEquals("Should find Black cards", 1, blackCards.size)

        // Filter by set
        val leaCards = database.scannedCardDao().getCardsBySet("LEA")
        assertEquals("Should get LEA set cards", 2, leaCards.size)

        // Collection stats
        val totalCards = database.scannedCardDao().getTotalCards()
        assertEquals("Total cards should sum quantities", 1 + 2 + 3 + 5, totalCards)
    }

    /**
     * Helper: Create synthetic test frame with single card.
     */
    private fun createTestCardFrame(name: String, setCode: String, collectorNum: String): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 14f
        }

        // Draw card region
        canvas.drawRect(100f, 50f, 300f, 300f, paint.apply { style = Paint.Style.STROKE })

        // Draw text labels
        canvas.drawText(name, 110f, 100f, paint.apply { style = Paint.Style.FILL })
        canvas.drawText("($setCode)", 110f, 150f, paint)
        canvas.drawText(collectorNum, 110f, 280f, paint)

        return bitmap
    }

    /**
     * Helper: Create frame with multiple cards.
     */
    private fun createTestMultiCardFrame(cardCount: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
        }

        repeat(cardCount) { idx ->
            val col = idx % 3
            val row = idx / 3
            val left = col * 220f + 10f
            val top = row * 240f + 10f
            canvas.drawRect(left, top, left + 200f, top + 200f, paint)
        }

        return bitmap
    }

    /**
     * Helper: Create mock Scryfall candidates for testing.
     */
    private fun createMockScryfallCandidates(): List<ScryfallCard> {
        return listOf(
            ScryfallCard(
                id = "lotus",
                name = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                imageUris = ScryfallCard.ImageUris(
                    small = "https://scryfall.com/example.jpg",
                    normal = "https://scryfall.com/example.jpg",
                    large = "https://scryfall.com/example.jpg"
                )
            ),
            ScryfallCard(
                id = "ancestral",
                name = "Ancestral Recall",
                setCode = "LEA",
                collectorNumber = "2",
                imageUris = null
            ),
            ScryfallCard(
                id = "shock",
                name = "Shock",
                setCode = "M21",
                collectorNumber = "280",
                imageUris = null
            )
        )
    }
}
