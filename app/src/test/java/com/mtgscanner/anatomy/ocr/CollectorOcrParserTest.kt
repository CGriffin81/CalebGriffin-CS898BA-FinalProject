package com.mtgscanner.anatomy.ocr

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for CollectorOcr parsing logic using real MTG collector line formats.
 *
 * Tests use CollectorOcr.parseText() which contains the core parsing logic
 * separated from ML Kit bitmap recognition. This validates every supported
 * collector number format used by Wizards of the Coast.
 *
 * Test data sourced from real Scryfall collector_number values.
 */
class CollectorOcrParserTest {

    private lateinit var ocr: CollectorOcr

    @Before
    fun setUp() {
        ocr = CollectorOcr()
    }

    // ══════════════════════════════════════════════════════════════
    // STANDARD FRACTION FORMAT
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `fraction format - 069 of 259 with set and rarity`() {
        val result = ocr.parseText("069/259 R • GRN • EN")
        assertEquals("69", result.collectorNumber)
        assertEquals("GRN", result.setCode)
        assertEquals("R", result.rarity)
    }

    @Test
    fun `fraction format - 1 of 302`() {
        val result = ocr.parseText("1/302 R LEA")
        assertEquals("1", result.collectorNumber)
        assertEquals("LEA", result.setCode)
    }

    @Test
    fun `fraction format - showcase numbering exceeds total`() {
        val result = ocr.parseText("280/274 R M21")
        assertEquals("280", result.collectorNumber)
        assertEquals("M21", result.setCode)
    }

    @Test
    fun `fraction format - bonus sheet numbering`() {
        val result = ocr.parseText("301/280 M ONE")
        assertEquals("301", result.collectorNumber)
        assertEquals("ONE", result.setCode)
    }

    @Test
    fun `fraction format - zero-padded three digits`() {
        val result = ocr.parseText("001/287 M MH2")
        assertEquals("1", result.collectorNumber)
        assertEquals("MH2", result.setCode)
        assertEquals("M", result.rarity)
    }

    // ══════════════════════════════════════════════════════════════
    // ALPHA SUFFIX VARIANTS
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `alpha suffix - 107a alternate art`() {
        val result = ocr.parseText("107a/280 U DMU")
        assertEquals("107a", result.collectorNumber)
        assertEquals("DMU", result.setCode)
    }

    @Test
    fun `alpha suffix - 42b variant`() {
        val result = ocr.parseText("42b M21")
        assertEquals("42b", result.collectorNumber)
        assertEquals("M21", result.setCode)
    }

    // ══════════════════════════════════════════════════════════════
    // NON-FRACTION FORMATS
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `plain number without fraction`() {
        val result = ocr.parseText("107 R GRN EN")
        assertEquals("107", result.collectorNumber)
        assertEquals("GRN", result.setCode)
        assertEquals("R", result.rarity)
    }

    @Test
    fun `plain number - single digit`() {
        val result = ocr.parseText("1 LEA")
        assertEquals("1", result.collectorNumber)
        assertEquals("LEA", result.setCode)
    }

    // ══════════════════════════════════════════════════════════════
    // PREFIXED FORMATS
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `prefix S - special card`() {
        val result = ocr.parseText("S107 MH2")
        assertEquals("S107", result.collectorNumber)
        assertEquals("MH2", result.setCode)
    }

    @Test
    fun `prefix P - promo card`() {
        val result = ocr.parseText("P042 R GRN")
        assertEquals("P42", result.collectorNumber)
        assertEquals("GRN", result.setCode)
    }

    // ══════════════════════════════════════════════════════════════
    // RARITY SEPARATION
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `rarity C is common`() {
        val result = ocr.parseText("042/259 C GRN EN")
        assertEquals("42", result.collectorNumber)
        assertEquals("C", result.rarity)
    }

    @Test
    fun `rarity M is mythic`() {
        val result = ocr.parseText("069/259 M GRN")
        assertEquals("69", result.collectorNumber)
        assertEquals("M", result.rarity)
    }

    @Test
    fun `rarity letter not confused with set code M21`() {
        val result = ocr.parseText("107/280 R M21")
        assertEquals("107", result.collectorNumber)
        assertEquals("M21", result.setCode)
        assertEquals("R", result.rarity)
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `copyright year not confused with collector number`() {
        val result = ocr.parseText("069/259 R GRN ©2018 Wizards")
        assertEquals("69", result.collectorNumber)
    }

    @Test
    fun `empty text produces empty result`() {
        val result = ocr.parseText("")
        assertEquals("", result.collectorNumber)
        assertEquals("", result.setCode)
        assertEquals("", result.rarity)
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `language code EN not mistaken for set code`() {
        val result = ocr.parseText("069/259 R GRN EN")
        assertEquals("GRN", result.setCode)
    }

    @Test
    fun `language code FR not mistaken for set code`() {
        val result = ocr.parseText("107/280 U DMU FR")
        assertEquals("DMU", result.setCode)
    }

    // ══════════════════════════════════════════════════════════════
    // CONFIDENCE
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `high confidence with both number and set`() {
        val result = ocr.parseText("069/259 R GRN EN")
        assertTrue("Expected > 0.7, got ${result.confidence}", result.confidence > 0.7f)
    }

    @Test
    fun `moderate confidence with number only`() {
        val result = ocr.parseText("069/259")
        assertTrue("Expected > 0.4", result.confidence > 0.4f)
    }

    @Test
    fun `low confidence for non-collector text`() {
        val result = ocr.parseText("Wizards of the Coast")
        assertTrue("Expected < 0.5", result.confidence < 0.5f)
    }
}
