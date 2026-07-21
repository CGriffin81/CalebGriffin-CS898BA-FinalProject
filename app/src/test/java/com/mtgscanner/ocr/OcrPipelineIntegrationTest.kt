package com.mtgscanner.ocr

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
 * OcrPipelineIntegrationTest
 *
 * Tests the parsing and confidence-scoring logic inside [CardOcrProcessor] without
 * requiring a real device or ML Kit runtime. The [CardOcrProcessor.parseCardText] and
 * [CardOcrProcessor.calculateConfidence] methods are `internal`, so they are directly
 * callable from this same-module test.
 *
 * Tests that require the live ML Kit recognizer (which needs GMS on an Android device)
 * are in the androidTest source set as [CardOcrProcessorInstrumentedTest].
 *
 * Why these tests exist:
 * - Previously, processCardImage() used addOnSuccessListener (fire-and-forget) and
 *   always returned empty DetectedCardText before OCR completed. That bug meant
 *   OcrPipeline always received ocrConfidence=0f and cardName="".
 * - After the P1-01 fix, processCardImage() uses Task.await() and returns the real
 *   OCR result. These unit tests verify that the downstream parsing logic correctly
 *   extracts fields from realistic ML Kit output and scores confidence accurately.
 */
class OcrPipelineIntegrationTest {

    private lateinit var processor: CardOcrProcessor
    private lateinit var preprocessor: OcrPreprocessor

    @Before
    fun setUp() {
        processor = CardOcrProcessor()
        preprocessor = OcrPreprocessor()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // parseCardText — card name extraction
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * The first non-trivial line (≥2 chars, contains a letter) becomes the card name.
     * A single digit on line 0 must not be picked as the name.
     */
    @Test
    fun testParseCardText_nameIsFirstNonTrivialLine() {
        val rawText = "3\nBlack Lotus\n(LEA)\n1/274"
        val result = processor.parseCardText(rawText)
        assertEquals("Should skip lone digit and pick card name", "Black Lotus", result.cardName)
    }

    @Test
    fun testParseCardText_singleWordName() {
        val rawText = "Shock\n(M21)\n280/274"
        val result = processor.parseCardText(rawText)
        assertEquals("Should extract single-word name", "Shock", result.cardName)
    }

    @Test
    fun testParseCardText_multiWordName() {
        val rawText = "Lightning Bolt\n(LEA)\n102/302"
        val result = processor.parseCardText(rawText)
        assertEquals("Should extract multi-word name", "Lightning Bolt", result.cardName)
    }

    @Test
    fun testParseCardText_nameWithApostrophe() {
        val rawText = "Teferi's Protection\n(C17)\n16/309"
        val result = processor.parseCardText(rawText)
        assertEquals("Should extract name with apostrophe", "Teferi's Protection", result.cardName)
    }

    @Test
    fun testParseCardText_emptyInput_returnsEmptyName() {
        val result = processor.parseCardText("")
        assertEquals("Empty input should produce empty name", "", result.cardName)
    }

    @Test
    fun testParseCardText_whitespaceOnlyLines_returnsEmptyName() {
        val result = processor.parseCardText("   \n  \n  ")
        assertEquals("Whitespace-only input should produce empty name", "", result.cardName)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // parseCardText — set code extraction
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testSetCodeExtraction_legacyParenthesisFormat() {
        val testCases = listOf(
            "(LEA)" to "LEA",
            "(M21)" to "M21",
            "(2X2)" to "2X2",
            "(MH2)" to "MH2"
        )
        testCases.forEach { (line, expected) ->
            val result = processor.parseCardText("Card Name\n$line\n1")
            assertEquals("Set code from '$line'", expected, result.setCode)
        }
    }

    @Test
    fun testSetCodeExtraction_noParenthesis_returnsEmpty() {
        // Modern cards print "042/274 R • M21" — P2-04 now supports this format.
        val result = processor.parseCardText("Shock\nInstant\nDeal 2 damage.\n280/274 R M21")
        assertEquals("Modern format set code should be extracted", "M21", result.setCode)
    }

    @Test
    fun testSetCodeExtraction_modernFormat_variousSetCodes() {
        val testCases = listOf(
            "042/274 R MH2" to "MH2",
            "001/287 M ONE" to "ONE",
            "156/281 U DMU" to "DMU",
            "280/274 C M21" to "M21"
        )
        testCases.forEach { (collectorLine, expectedSet) ->
            val result = processor.parseCardText("Card Name\nType Line\n$collectorLine")
            assertEquals("Modern set code from '$collectorLine'", expectedSet, result.setCode)
        }
    }

    @Test
    fun testSetCodeExtraction_languageCodesExcluded() {
        // "EN" appears on collector lines but is a language code, not a set code
        val result = processor.parseCardText("Card Name\nEN 042/274 M21")
        assertEquals("Should extract M21 not EN", "M21", result.setCode)
    }

    @Test
    fun testSetCodeExtraction_legacyAndModernCoexist() {
        // Legacy format should be found first if present
        val result = processor.parseCardText("Card Name\n(LEA)\n042/274 M21")
        assertEquals("Legacy format should take priority when present", "LEA", result.setCode)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // parseCardText — collector number extraction
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * The fraction pattern NNN/NNN at the end of the text should be preferred.
     * The numerator is returned as the collector number.
     */
    @Test
    fun testCollectorNumberExtraction_fractionFormat_modern() {
        val result = processor.parseCardText("Shock\n(M21)\n280/274")
        assertEquals("Should extract numerator from fraction", "280", result.collectorNumber)
    }

    @Test
    fun testCollectorNumberExtraction_fractionFormat_leadingZero() {
        val result = processor.parseCardText("Lightning Bolt\n(LEA)\n042/274")
        assertEquals("Should strip leading zero from fraction", "42", result.collectorNumber)
    }

    @Test
    fun testCollectorNumberExtraction_fractionFormat_singleDigit() {
        val result = processor.parseCardText("Black Lotus\n(LEA)\n1/302")
        assertEquals("Single-digit collector from fraction", "1", result.collectorNumber)
    }

    @Test
    fun testCollectorNumberExtraction_letterSuffix() {
        // Some promo and variant cards use suffixed collector numbers like "42a"
        val result = processor.parseCardText("Card Name\n42a/280")
        assertEquals("Should extract collector with letter suffix", "42a", result.collectorNumber)
    }

    /**
     * A mana cost digit on line 0 must not be picked as the collector number
     * because the bottom-up search prioritises bottom lines.
     */
    @Test
    fun testCollectorNumberExtraction_manaCostNotConfused() {
        // "3" is the mana cost on the first line; "280/274" is the collector line at the bottom
        val result = processor.parseCardText("3\nShock\n(M21)\n280/274")
        assertEquals("Should not confuse mana cost with collector number", "280", result.collectorNumber)
    }

    @Test
    fun testCollectorNumberExtraction_noNumber_returnsEmpty() {
        val result = processor.parseCardText("Card Name\n(LEA)")
        assertEquals("No number in text → empty collector", "", result.collectorNumber)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // calculateConfidence — plausibility gating
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testConfidence_allFieldsPlausible_returnsOne() {
        val score = processor.calculateConfidence("Black Lotus", "LEA", "1")
        assertEquals("All plausible fields → 1.0", 1.0f, score, 0.001f)
    }

    @Test
    fun testConfidence_nameOnly_returnsHalf() {
        val score = processor.calculateConfidence("Black Lotus", "", "")
        assertEquals("Name only → 0.5", 0.5f, score, 0.001f)
    }

    @Test
    fun testConfidence_nameAndSet_returnsSeventyFive() {
        val score = processor.calculateConfidence("Shock", "M21", "")
        assertEquals("Name + set → 0.75", 0.75f, score, 0.001f)
    }

    @Test
    fun testConfidence_allEmpty_returnsZero() {
        val score = processor.calculateConfidence("", "", "")
        assertEquals("All empty → 0.0", 0.0f, score, 0.001f)
    }

    /**
     * A lone digit is not a plausible card name — it might be a mana cost OCR fragment.
     * Confidence must not receive the name bonus for implausible names.
     */
    @Test
    fun testConfidence_singleDigitName_notPlausible() {
        val score = processor.calculateConfidence("3", "M21", "42")
        assertTrue(
            "Single-digit name should not earn name bonus; score should be ≤ 0.5",
            score <= 0.5f
        )
    }

    @Test
    fun testConfidence_allDigitName_notPlausible() {
        val score = processor.calculateConfidence("123", "", "")
        assertEquals("All-digit name is not plausible → 0.0", 0.0f, score, 0.001f)
    }

    @Test
    fun testConfidence_setCodeTooShort_notPlausible() {
        val score = processor.calculateConfidence("Black Lotus", "L", "1")
        assertEquals("1-char set code is not plausible → name + collector only", 0.75f, score, 0.001f)
    }

    @Test
    fun testConfidence_collectorNumberTooLong_notPlausible() {
        // 4+ digit numbers are not valid collector numbers (e.g., a year like "2021")
        val score = processor.calculateConfidence("Black Lotus", "LEA", "2021")
        assertEquals("4-digit collector is not plausible → name + set only", 0.75f, score, 0.001f)
    }

    @Test
    fun testConfidence_manaCostLikeName_notPlausible() {
        // "3 2" looks like mana costs, not a card name
        val score = processor.calculateConfidence("3 2", "M21", "42")
        assertTrue("Mana cost pattern should not earn name bonus", score <= 0.5f)
    }

    @Test
    fun testConfidence_languageCodeAsSetCode_notPlausible() {
        // "EN" is a language code, not a valid set code
        val score = processor.calculateConfidence("Black Lotus", "EN", "1")
        assertEquals("Language code as set → name + collector only", 0.75f, score, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Full parseCardText round-trips — realistic OCR output
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Simulate realistic ML Kit output for a clean card scan.
     * ML Kit typically returns text blocks in top-to-bottom order, one block per line.
     */
    @Test
    fun testParseCardText_realisticCleanCard() {
        // Approximate what ML Kit returns for a well-lit modern card
        val mlKitOutput = "Lightning Bolt\nInstant\nDeal 3 damage to any target.\n(LEA)\n102/302"
        val result = processor.parseCardText(mlKitOutput)

        assertEquals("Name extracted correctly", "Lightning Bolt", result.cardName)
        assertEquals("Set code extracted correctly", "LEA", result.setCode)
        assertEquals("Collector number extracted correctly", "102", result.collectorNumber)
        assertTrue("Clean card should have confidence ≥ 0.75", result.ocrConfidence >= 0.75f)
    }

    @Test
    fun testParseCardText_realisticCardWithOcrNoise() {
        // OCR may misread characters — e.g., "Lightnin6 Bolt" or extra spaces
        val mlKitOutput = "Lightnin6 Bolt\nInstant\nDeal 3 damage.\n(LEA)\n102/302"
        val result = processor.parseCardText(mlKitOutput)

        // Name will be noisy but non-empty — that's expected; Scryfall fuzzy handles the rest
        assertFalse("Noisy name should still be non-empty", result.cardName.isEmpty())
        assertEquals("Set code should still be extracted despite name noise", "LEA", result.setCode)
        assertEquals("Collector number should still be extracted", "102", result.collectorNumber)
    }

    @Test
    fun testParseCardText_blankImage_allEmpty() {
        // A blank white image produces empty OCR output — confirm graceful handling
        val result = processor.parseCardText("")
        assertEquals("Blank image → empty name", "", result.cardName)
        assertEquals("Blank image → empty set", "", result.setCode)
        assertEquals("Blank image → empty collector", "", result.collectorNumber)
        assertEquals("Blank image → zero confidence", 0.0f, result.ocrConfidence, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // OcrPreprocessor — region crop sanity checks (no ML Kit required)
    // ──────────────────────────────────────────────────────────────────────────

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testRegionExtraction_producesThreeNonNullRegions() {
        val cardBitmap = createSyntheticCardBitmap(180, 250)
        val regions = preprocessor.extractCardRegions(cardBitmap)

        assertTrue("Name region height > 0", regions.nameRegion.height > 0)
        assertTrue("Type region height > 0", regions.typeRegion.height > 0)
        assertTrue("Collector region height > 0", regions.collectorRegion.height > 0)
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testRegionExtraction_regionWidthMatchesInput() {
        val cardBitmap = createSyntheticCardBitmap(180, 250)
        val regions = preprocessor.extractCardRegions(cardBitmap)

        assertEquals("Name region width = card width", 180, regions.nameRegion.width)
        assertEquals("Collector region width = card width", 180, regions.collectorRegion.width)
    }

    @Ignore("Bitmap.createBitmap() requires Android runtime — move to androidTest for device execution")
    @Test
    fun testPreprocessing_returnsInputUnchanged() {
        val input = createSyntheticCardBitmap(180, 250)
        val output = preprocessor.preprocessForOcr(input)

        // OcrPreprocessor is currently a no-op pass-through; verify dimensions preserved
        assertEquals("Width preserved", input.width, output.width)
        assertEquals("Height preserved", input.height, output.height)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regex pattern correctness — verified independently of ML Kit
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testSetCodePattern_legacyFormats() {
        val pattern = Regex("\\(([A-Z0-9]{2,5})\\)", RegexOption.IGNORE_CASE)
        val testCases = mapOf(
            "(LEA)" to "LEA",
            "(M21)" to "M21",
            "(2X2)" to "2X2",
            "(MH2)" to "MH2",
            "(ABUR)" to "ABUR"
        )
        testCases.forEach { (input, expected) ->
            val match = pattern.find(input.uppercase())
            assertEquals("Pattern match for '$input'", expected, match?.groupValues?.get(1))
        }
    }

    @Test
    fun testCollectorFractionPattern() {
        val pattern = Regex("\\b(\\d{1,3})/(\\d{1,3})\\b")
        val testCases = mapOf(
            "1/302" to "1",
            "042/274" to "042",
            "280/274" to "280",
            "99/99" to "99"
        )
        testCases.forEach { (input, expected) ->
            val match = pattern.find(input)
            assertEquals("Fraction numerator from '$input'", expected, match?.groupValues?.get(1))
        }
    }

    @Test
    fun testCollectorStandalonePattern_letterSuffix() {
        val pattern = Regex("\\b(\\d{1,3}[a-zA-Z]?)\\b")
        val validCases = listOf("42", "1", "280", "42a", "99b")
        validCases.forEach { input ->
            val match = pattern.find(input)
            assertEquals("Standalone collector '$input'", input, match?.groupValues?.get(1))
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun createSyntheticCardBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
        }
        canvas.drawText("Black Lotus", 10f, 20f, paint)
        canvas.drawText("(LEA)", 10f, 100f, paint)
        canvas.drawText("1/302", 10f, 240f, paint)
        return bitmap
    }
}
