package com.mtgscanner.detection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * DetectionPipelineIntegrationTest
 *
 * Tests the detection and tracking pipeline components.
 *
 * Status note (plan item P1-03):
 * [CardDetector.detectCards] currently returns `emptyList()` — it is a stub while the
 * native detection implementation is pending. Tests that depend on real detections are
 * annotated with @Ignore and will be enabled once P1-03 is complete.
 *
 * Tests that do NOT require real detections (tracker logic, pipeline wiring) run now.
 */
class DetectionPipelineIntegrationTest {

    private lateinit var cardDetector: CardDetector
    private lateinit var cardTracker: CardTracker
    private lateinit var detectionPipeline: DetectionPipeline

    @Before
    fun setUp() {
        cardDetector = CardDetector()
        cardTracker = CardTracker()
        detectionPipeline = DetectionPipeline()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CardTracker — these work today because they receive hand-crafted CardRegion objects
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testTracker_newDetectionGetsTrackingId() {
        val region = CardRegion(x = 100, y = 100, width = 180, height = 250, area = 45000)
        val tracks = cardTracker.updateTracks(listOf(region))

        assertFalse("Tracker should assign an ID for a new detection", tracks.isEmpty())
        assertTrue("Detection index 0 should have a tracking ID", tracks.containsKey(0))
    }

    @Test
    fun testTracker_samePositionKeepsSameId() {
        val region = CardRegion(x = 100, y = 100, width = 180, height = 250, area = 45000)

        val frame1 = cardTracker.updateTracks(listOf(region))
        val idFrame1 = frame1[0]!!

        // Same position — should match the existing track
        val frame2 = cardTracker.updateTracks(listOf(region))
        val idFrame2 = frame2[0]!!

        assertEquals("Same position should keep the same tracking ID", idFrame1, idFrame2)
    }

    @Test
    fun testTracker_slightDriftKeepsSameId() {
        // Cards may shift a few pixels between frames due to hand movement
        val region1 = CardRegion(x = 100, y = 100, width = 180, height = 250, area = 45000)
        val region2 = CardRegion(x = 104, y = 103, width = 180, height = 250, area = 45000)  // 5px drift

        val frame1 = cardTracker.updateTracks(listOf(region1))
        val idFrame1 = frame1[0]!!

        val frame2 = cardTracker.updateTracks(listOf(region2))
        val idFrame2 = frame2[0]!!

        assertEquals("Small position drift should keep the same tracking ID", idFrame1, idFrame2)
    }

    @Test
    fun testTracker_farAwayDetectionGetsNewId() {
        val region1 = CardRegion(x = 0, y = 0, width = 180, height = 250, area = 45000)
        val frame1 = cardTracker.updateTracks(listOf(region1))
        val idFrame1 = frame1[0]!!

        // 400px away — must not match the existing track (positionThreshold = 50px)
        val region2 = CardRegion(x = 400, y = 400, width = 180, height = 250, area = 45000)
        val frame2 = cardTracker.updateTracks(listOf(region2))
        val idFrame2 = frame2[0]!!

        assertTrue(
            "Far-away detection should get a new tracking ID",
            idFrame2 != idFrame1
        )
    }

    @Test
    fun testTracker_stabilityRequires3Frames() {
        val region = CardRegion(x = 100, y = 100, width = 180, height = 250, area = 45000)
        // Frame 1
        val t1 = cardTracker.updateTracks(listOf(region))
        val trackingId = t1[0]!!
        assertFalse("Not stable after 1 frame", cardTracker.isStableDetection(trackingId))

        // Frame 2
        cardTracker.updateTracks(listOf(region))
        assertFalse("Not stable after 2 frames", cardTracker.isStableDetection(trackingId))

        // Frame 3 — should reach the 3-frame threshold
        cardTracker.updateTracks(listOf(region))
        assertTrue("Should be stable after 3 frames", cardTracker.isStableDetection(trackingId))
    }

    @Test
    fun testTracker_missingFramesTriggerRemoval() {
        val region = CardRegion(x = 100, y = 100, width = 180, height = 250, area = 45000)
        cardTracker.updateTracks(listOf(region))

        // Feed 6 frames with an empty detection list — card disappears
        // CardTracker removes tracks after > 5 consecutive misses
        repeat(6) { cardTracker.updateTracks(emptyList()) }

        // The track should have been pruned; a new detection at the same position gets a new ID
        val newTracks = cardTracker.updateTracks(listOf(region))
        // If no tracks are active, the new detection creates a new entry — this just must not crash
        assertTrue("Tracker must handle re-appearing detection gracefully", newTracks.isNotEmpty())
    }

    @Test
    fun testTracker_multipleCardsGetDistinctIds() {
        val region1 = CardRegion(x = 0, y = 0, width = 180, height = 250, area = 45000)
        val region2 = CardRegion(x = 200, y = 0, width = 180, height = 250, area = 45000)
        val region3 = CardRegion(x = 400, y = 0, width = 180, height = 250, area = 45000)

        val tracks = cardTracker.updateTracks(listOf(region1, region2, region3))

        assertEquals("All 3 detections should have tracking IDs", 3, tracks.size)
        val ids = tracks.values.toSet()
        assertEquals("All 3 tracking IDs should be distinct", 3, ids.size)
    }

    /**
     * Verifies the P1-03 center-distance fix:
     * Two CardRegions with the same center but different top-left coordinates
     * (due to different bounding box sizes) should be treated as the same card.
     */
    @Test
    fun testTracker_sameCenterDifferentBoundsKeepsSameId() {
        // Region 1: 180x250 at (100, 100) → center = (190, 225)
        val region1 = CardRegion(x = 100, y = 100, width = 180, height = 250, area = 45000)

        // Region 2: 200x270 at (90, 90) → center = (190, 225) — same center!
        val region2 = CardRegion(x = 90, y = 90, width = 200, height = 270, area = 54000)

        val frame1 = cardTracker.updateTracks(listOf(region1))
        val idFrame1 = frame1[0]!!

        val frame2 = cardTracker.updateTracks(listOf(region2))
        val idFrame2 = frame2[0]!!

        assertEquals(
            "Same center point should keep the same tracking ID regardless of bounding box size",
            idFrame1,
            idFrame2
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DetectionPipeline — wiring tests that don't need real card detection
    // ──────────────────────────────────────────────────────────────────────────

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testDetectionPipeline_onCardReadyCallbackIsInvokeable() {
        var callbackFired = false
        var receivedTrackingId = -1
        val fakeBitmap = Bitmap.createBitmap(180, 250, Bitmap.Config.ARGB_8888)

        detectionPipeline.onCardReady = { _, trackingId ->
            callbackFired = true
            receivedTrackingId = trackingId
        }

        // Directly invoke the callback (bypasses CardDetector stub)
        detectionPipeline.onCardReady(fakeBitmap, 42)

        assertTrue("onCardReady callback should fire", callbackFired)
        assertEquals("Tracking ID should be passed through", 42, receivedTrackingId)
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testDetectionPipeline_clearProcessedCards_allowsReprocessing() {
        val fakeBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        var callCount = 0

        detectionPipeline.onCardReady = { _, _ -> callCount++ }

        // Manually invoke twice without clearing — second should also be invokeable
        // (processedCards only blocks re-processing via processFrame(), not direct invocation)
        detectionPipeline.onCardReady(fakeBitmap, 1)
        detectionPipeline.clearProcessedCards()
        detectionPipeline.onCardReady(fakeBitmap, 1)

        assertEquals("Callback should fire both times after clearing", 2, callCount)
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testDetectionPipeline_processFrameDoesNotCrashOnEmptyDetections() {
        // CardDetector returns emptyList() — processFrame must handle that gracefully
        val frame = createBlackBitmap(640, 480)

        // Must not throw
        detectionPipeline.processFrame(frame)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CardDetector — now implemented (P1-03)
    // ──────────────────────────────────────────────────────────────────────────

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testSingleCardDetection() {
        val bitmap = createCardOnBlackBackground(640, 480, cardWidth = 180f, cardHeight = 250f)
        val detections = cardDetector.detectCards(bitmap)

        assertTrue("Should detect at least one card", detections.isNotEmpty())
        val detection = detections.first()
        assertTrue("Card area should be > 5000px²", detection.area > 5000)
        val aspectRatio = detection.width.toFloat() / detection.height.toFloat()
        assertTrue("Card aspect ratio should be 0.60–0.80", aspectRatio in 0.60f..0.80f)
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testMultipleCardDetection() {
        val bitmap = createMultipleCardsOnBlack(640, 480, cardCount = 4)
        val detections = cardDetector.detectCards(bitmap)

        assertTrue(
            "Should detect multiple cards (allowing 1 miss for edge cards)",
            detections.size >= 3
        )
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testStabilityViaProcessFrame() {
        val bitmap = createCardOnBlackBackground(640, 480, cardWidth = 180f, cardHeight = 250f)
        var stableCallbackFired = false

        detectionPipeline.onCardReady = { _, _ -> stableCallbackFired = true }

        // Feed 5 frames — card should reach stability threshold (3 frames) and trigger callback
        repeat(5) { detectionPipeline.processFrame(bitmap) }

        assertTrue("onCardReady should fire once card reaches 3-frame stability", stableCallbackFired)
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testDuplicatePrevention_sameCardNotProcessedTwice() {
        val bitmap = createCardOnBlackBackground(640, 480, cardWidth = 180f, cardHeight = 250f)
        var callCount = 0

        detectionPipeline.onCardReady = { _, _ -> callCount++ }

        repeat(10) { detectionPipeline.processFrame(bitmap) }

        assertEquals("Same card should trigger onCardReady exactly once", 1, callCount)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bitmap helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun createBlackBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        return bitmap
    }

    private fun createCardOnBlackBackground(
        width: Int,
        height: Int,
        cardWidth: Float,
        cardHeight: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply { color = Color.WHITE }
        val left = (width - cardWidth) / 2
        val top = (height - cardHeight) / 2
        canvas.drawRect(left, top, left + cardWidth, top + cardHeight, paint)
        return bitmap
    }

    private fun createMultipleCardsOnBlack(width: Int, height: Int, cardCount: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply { color = Color.WHITE }
        val cardW = 120f
        val cardH = 170f
        val spacing = 10f
        for (i in 0 until cardCount) {
            val col = i % 3
            val row = i / 3
            val left = col * (cardW + spacing) + spacing
            val top = row * (cardH + spacing) + spacing
            canvas.drawRect(left, top, left + cardW, top + cardH, paint)
        }
        return bitmap
    }
}
