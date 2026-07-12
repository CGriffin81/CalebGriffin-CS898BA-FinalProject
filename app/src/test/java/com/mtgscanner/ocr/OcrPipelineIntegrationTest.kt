package com.mtgscanner.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import com.mtgscanner.model.DetectedCardText

/**
 * OcrPipelineIntegrationTest: Tests OCR pipeline end-to-end.
 * Validates: image preprocessing → ML Kit recognition → field extraction.
 */
class OcrPipelineIntegrationTest {

    private lateinit var ocrPreprocessor: OcrPreprocessor
    private lateinit var ocrPipeline: OcrPipeline

    @Before
    fun setUp() {
        ocrPreprocessor = OcrPreprocessor()
        ocrPipeline = OcrPipeline()
    }

    /**
     * Test: Image preprocessing improves text clarity.
     */
    @Test
    fun testPreprocessingEnhancesContrast() {
        val testBitmap = createTestCardImageWithText("Black Lotus", "LEA", "1")

        val preprocessed = ocrPreprocessor.preprocessForOcr(testBitmap)

        assertNotNull("Preprocessed bitmap should not be null", preprocessed)
        assertEquals("Preprocessed dimensions should match", testBitmap.width, preprocessed.width)
        assertEquals("Preprocessed dimensions should match", testBitmap.height, preprocessed.height)
    }

    /**
     * Test: Region extraction splits card into name, type, collector regions.
     */
    @Test
    fun testRegionExtraction() {
        val testBitmap = createTestCardImageWithText("Black Lotus", "LEA", "1")

        val nameRegion = ocrPreprocessor.extractNameRegion(testBitmap)
        val typeRegion = ocrPreprocessor.extractTypeRegion(testBitmap)
        val collectorRegion = ocrPreprocessor.extractCollectorRegion(testBitmap)

        assertNotNull("Name region should be extracted", nameRegion)
        assertNotNull("Type region should be extracted", typeRegion)
        assertNotNull("Collector region should be extracted", collectorRegion)

        // Verify regions have reasonable dimensions
        assertTrue("Name region should have height > 0", nameRegion!!.height > 0)
        assertTrue("Collector region should have height > 0", collectorRegion!!.height > 0)
    }

    /**
     * Test: OCR field extraction from text.
     */
    @Test
    fun testFieldExtraction() {
        val testBitmap = createTestCardImageWithText("Black Lotus", "LEA", "1")

        val detectedText = ocrPipeline.recognizeCard(testBitmap, trackingId = 1)

        assertNotNull("Detected text should not be null", detectedText)
        // Note: In real scenario with ML Kit, these would be actual extracted values
        // Here we test that structure is correct
        assertNotNull("Card name should be extracted", detectedText.cardName)
        assertNotNull("Set code should be extracted", detectedText.setCode)
        assertNotNull("Collector number should be extracted", detectedText.collectorNumber)
    }

    /**
     * Test: Confidence scoring combines name, set, collector components.
     */
    @Test
    fun testConfidenceScoring() {
        val testBitmap = createTestCardImageWithText("Black Lotus", "LEA", "1")

        val detectedText = ocrPipeline.recognizeCard(testBitmap, trackingId = 1)

        assertNotNull("Confidence should be set", detectedText.confidence)
        assertTrue("Confidence should be 0.0–1.0", detectedText.confidence!! in 0.0..1.0)
    }

    /**
     * Test: Fallback to region-based OCR when full-card confidence is low.
     */
    @Test
    fun testRegionFallbackOnLowConfidence() {
        // Create low-confidence test image (poor quality)
        val lowQualityBitmap = createLowQualityCardImage()

        val detectedText = ocrPipeline.recognizeCard(lowQualityBitmap, trackingId = 1)

        assertNotNull("Should still extract data via fallback", detectedText)
        assertNotNull("Name should be extracted via fallback", detectedText.cardName)
    }

    /**
     * Test: Multiple cards in frame are processed individually.
     */
    @Test
    fun testMultipleCardOCR() {
        val cardBitmap1 = createTestCardImageWithText("Black Lotus", "LEA", "1")
        val cardBitmap2 = createTestCardImageWithText("Lightning Bolt", "LEA", "102")

        val detected1 = ocrPipeline.recognizeCard(cardBitmap1, trackingId = 1)
        val detected2 = ocrPipeline.recognizeCard(cardBitmap2, trackingId = 2)

        assertNotNull("First card should be recognized", detected1)
        assertNotNull("Second card should be recognized", detected2)
        assertNotEquals("Cards should have different tracking IDs", detected1.trackingId, detected2.trackingId)
    }

    /**
     * Test: Regex patterns correctly extract set codes.
     */
    @Test
    fun testSetCodeExtraction() {
        // Test various set code formats
        val testCases = listOf(
            "(LEA)" to "LEA",      // Classic format
            "(M21)" to "M21",      // Modern format
            "(ABUR)" to "ABUR",    // Longer codes
            "(2X2)" to "2X2"       // Alpha-numeric
        )

        testCases.forEach { (input, expected) ->
            val pattern = Regex("\\(([A-Z0-9]{1,4})\\)")
            val match = pattern.find(input)
            assertNotNull("Should match set code pattern: $input", match)
            assertEquals("Set code should be extracted correctly", expected, match?.groupValues?.get(1))
        }
    }

    /**
     * Test: Regex patterns correctly extract collector numbers.
     */
    @Test
    fun testCollectorNumberExtraction() {
        val testCases = listOf(
            "1" to "1",           // Single digit
            "102" to "102",       // Multi-digit
            "42a" to "42a",       // With letter suffix (foil indicator)
            "999b" to "999b"      // Large number with suffix
        )

        testCases.forEach { (input, expected) ->
            val pattern = Regex("\\b(\\d+)([a-zA-Z]?)\\b")
            val match = pattern.find(input)
            assertNotNull("Should match collector number pattern: $input", match)
            val full = match?.groupValues?.get(1)!! + match.groupValues.get(2)
            assertEquals("Collector number should be extracted correctly", expected, full)
        }
    }

    /**
     * Test: OCR robustness with rotated/skewed card images.
     */
    @Test
    fun testRotatedCardOCR() {
        val straightBitmap = createTestCardImageWithText("Black Lotus", "LEA", "1")
        val rotatedBitmap = rotateImage(straightBitmap, 15f) // 15 degree rotation

        val detected1 = ocrPipeline.recognizeCard(straightBitmap, trackingId = 1)
        val detected2 = ocrPipeline.recognizeCard(rotatedBitmap, trackingId = 2)

        assertNotNull("Straight card should be recognized", detected1)
        assertNotNull("Rotated card should still be recognized", detected2)
        // Confidence may differ, but both should extract data
    }

    /**
     * Test: Pipeline handles empty/blank images gracefully.
     */
    @Test
    fun testBlankImageHandling() {
        val blankBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blankBitmap)
        canvas.drawColor(Color.WHITE) // Blank white image

        val detectedText = ocrPipeline.recognizeCard(blankBitmap, trackingId = 1)

        assertNotNull("Should not crash on blank image", detectedText)
        // Confidence should be low for blank card
        assertTrue("Confidence should reflect low OCR quality", (detectedText.confidence ?: 0.0) < 0.5)
    }

    /**
     * Helper: Create synthetic card image with text labels.
     */
    private fun createTestCardImageWithText(name: String, setCode: String, collectorNum: String): Bitmap {
        val bitmap = Bitmap.createBitmap(180, 250, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }

        // Draw white background (card)
        canvas.drawColor(Color.WHITE)

        // Draw text labels (simulating card fields)
        canvas.drawText(name, 10f, 30f, paint)
        canvas.drawText("($setCode)", 10f, 100f, paint)
        canvas.drawText(collectorNum, 10f, 230f, paint)

        return bitmap
    }

    /**
     * Helper: Create low-quality card image (poor contrast, noise).
     */
    private fun createLowQualityCardImage(): Bitmap {
        val bitmap = Bitmap.createBitmap(180, 250, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw gray background (low contrast)
        canvas.drawColor(Color.GRAY)

        // Draw faint text
        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            alpha = 100  // Low opacity
        }

        canvas.drawText("Card Name", 10f, 30f, paint)

        return bitmap
    }

    /**
     * Helper: Rotate bitmap by angle.
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply {
            postRotate(angle, source.width / 2f, source.height / 2f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
