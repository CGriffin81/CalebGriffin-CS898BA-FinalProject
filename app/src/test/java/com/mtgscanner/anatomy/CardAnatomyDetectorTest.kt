package com.mtgscanner.anatomy

import com.mtgscanner.anatomy.model.BoundingRect
import com.mtgscanner.anatomy.model.FrameLayoutTemplate
import com.mtgscanner.anatomy.model.FrameType
import com.mtgscanner.anatomy.model.ProportionalRect
import com.mtgscanner.anatomy.model.RegionType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for the Card Anatomy Engine.
 *
 * Tests validate GEOMETRY before OCR begins:
 * - Region detection produces valid bounding boxes
 * - Proportional coordinates convert correctly at various resolutions
 * - Frame layout templates have non-overlapping, valid regions
 * - Different card layouts (Modern, Classic, Full-Art, Borderless) produce correct geometry
 * - Edge cases (tiny images, extreme aspect ratios) are handled gracefully
 * - BoundingRect serialization round-trips correctly
 *
 * These tests do NOT require Android runtime (no Bitmap operations).
 * Tests that need actual Bitmap rendering are marked @Ignore and belong in androidTest.
 */
class CardAnatomyDetectorTest {

    private lateinit var registry: FrameLayoutRegistry

    @Before
    fun setUp() {
        registry = FrameLayoutRegistry()
    }

    // ══════════════════════════════════════════════════════════════════
    // REGISTRY & TEMPLATE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `registry contains all default frame types`() {
        val types = registry.registeredTypes()
        assertTrue("Should contain MODERN", types.contains(FrameType.MODERN))
        assertTrue("Should contain CLASSIC", types.contains(FrameType.CLASSIC))
        assertTrue("Should contain FULL_ART", types.contains(FrameType.FULL_ART))
        assertTrue("Should contain BORDERLESS", types.contains(FrameType.BORDERLESS))
        assertTrue("Should contain UNKNOWN", types.contains(FrameType.UNKNOWN))
    }

    @Test
    fun `registry fallback returns MODERN for unregistered types`() {
        val template = registry.getTemplate(FrameType.PLANESWALKER)
        // PLANESWALKER isn't registered yet — should fall back to MODERN
        assertEquals(FrameType.MODERN, template.frameType)
    }

    @Test
    fun `custom template can be registered`() {
        val custom = FrameLayoutTemplate(
            frameType = FrameType.SAGA,
            nameBar = ProportionalRect(0.02f, 0.06f, 0.05f, 0.90f),
            manaCost = ProportionalRect(0.02f, 0.06f, 0.90f, 0.98f),
            artwork = ProportionalRect(0.06f, 0.85f, 0.05f, 0.95f),
            typeLine = ProportionalRect(0.86f, 0.90f, 0.05f, 0.95f),
            rulesText = ProportionalRect(0.06f, 0.85f, 0.05f, 0.95f),
            setSymbol = ProportionalRect(0.86f, 0.90f, 0.85f, 0.95f),
            collectorInfo = ProportionalRect(0.94f, 0.98f, 0.05f, 0.50f),
            artistCredit = ProportionalRect(0.94f, 0.98f, 0.50f, 0.95f),
            powerToughness = null
        )
        registry.register(custom)
        val retrieved = registry.getTemplate(FrameType.SAGA)
        assertEquals(FrameType.SAGA, retrieved.frameType)
    }

    // ══════════════════════════════════════════════════════════════════
    // PROPORTIONAL GEOMETRY TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `BoundingRect fromProportional at 488x680 standard resolution`() {
        // Modern name bar: top=3.5%, bottom=7.5%, left=5%, right=82%
        val rect = BoundingRect.fromProportional(0.05f, 0.035f, 0.82f, 0.075f, 488, 680)
        assertEquals("left", 24, rect.left)
        assertEquals("top", 23, rect.top)
        // right = (0.82 * 488).toInt() = 400, width = 400 - 24 = 376
        assertEquals("width", 376, rect.width)
        // bottom = (0.075 * 680).toInt() = 51, height = 51 - 23 = 28
        assertEquals("height", 28, rect.height)
        assertTrue("valid", rect.isValid)
    }

    @Test
    fun `BoundingRect fromProportional at 2992x2992 device resolution`() {
        val rect = BoundingRect.fromProportional(0.05f, 0.035f, 0.82f, 0.075f, 2992, 2992)
        assertEquals("left", 149, rect.left)
        assertEquals("top", 104, rect.top)
        // width = (0.82 * 2992) - (0.05 * 2992) = 2453 - 149 = 2304
        assertTrue("width > 2000", rect.width > 2000)
        assertTrue("height > 100", rect.height > 100)
    }

    @Test
    fun `BoundingRect fromProportional clamps to image bounds`() {
        // Attempt coordinates slightly beyond 1.0 — should clamp
        val rect = BoundingRect.fromProportional(-0.01f, -0.01f, 1.01f, 1.01f, 100, 100)
        assertEquals("left clamped to 0", 0, rect.left)
        assertEquals("top clamped to 0", 0, rect.top)
        assertEquals("right clamped to 100", 100, rect.right)
        assertEquals("bottom clamped to 100", 100, rect.bottom)
    }

    @Test
    fun `BoundingRect area calculation`() {
        val rect = BoundingRect(left = 10, top = 20, width = 100, height = 50)
        assertEquals(5000, rect.area)
        assertEquals(60, rect.centerX)
        assertEquals(45, rect.centerY)
        assertEquals(110, rect.right)
        assertEquals(70, rect.bottom)
    }

    @org.junit.Ignore("android.graphics.Rect requires Android runtime — move to androidTest")
    @Test
    fun `BoundingRect toAndroidRect round trip`() {
        val original = BoundingRect(left = 15, top = 25, width = 200, height = 100)
        val androidRect = original.toAndroidRect()
        val roundTripped = BoundingRect.fromAndroidRect(androidRect)
        assertEquals(original, roundTripped)
    }

    // ══════════════════════════════════════════════════════════════════
    // MODERN FRAME LAYOUT VALIDATION
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `modern template name bar is in top 12 percent`() {
        val template = registry.getTemplate(FrameType.MODERN)
        assertTrue("name top == 0", template.nameBar.top == 0.0f)
        assertTrue("name bottom <= 12%", template.nameBar.bottom <= 0.12f)
    }

    @Test
    fun `modern template artwork occupies large central area`() {
        val template = registry.getTemplate(FrameType.MODERN)
        val artHeight = template.artwork.height
        assertTrue("Art height > 35%", artHeight > 0.35f)
        assertTrue("Art height < 50%", artHeight < 0.50f)
    }

    @Test
    fun `modern template type line is between art and rules`() {
        val template = registry.getTemplate(FrameType.MODERN)
        assertTrue("type top >= art bottom",
            template.typeLine.top >= template.artwork.bottom - 0.02f)
        assertTrue("type bottom <= rules top + margin",
            template.typeLine.bottom <= template.rulesText.top + 0.02f)
    }

    @Test
    fun `modern template collector info is at card bottom`() {
        val template = registry.getTemplate(FrameType.MODERN)
        assertTrue("collector top > 88%", template.collectorInfo.top > 0.88f)
        assertTrue("collector bottom <= 100%", template.collectorInfo.bottom <= 1.0f)
    }

    @Test
    fun `modern template PT is below rules text`() {
        val template = registry.getTemplate(FrameType.MODERN)
        assertNotNull("Modern should have P/T", template.powerToughness)
        val pt = template.powerToughness!!
        assertTrue("P/T top >= rules bottom - margin",
            pt.top >= template.rulesText.bottom - 0.02f)
    }

    @Test
    fun `modern template regions do not have negative dimensions`() {
        val template = registry.getTemplate(FrameType.MODERN)
        val allRects = listOf(
            template.nameBar, template.manaCost, template.artwork,
            template.typeLine, template.rulesText, template.setSymbol,
            template.collectorInfo, template.artistCredit
        ) + listOfNotNull(template.powerToughness)

        allRects.forEach { rect ->
            assertTrue("${rect} width > 0", rect.width > 0f)
            assertTrue("${rect} height > 0", rect.height > 0f)
            assertTrue("${rect} left >= 0", rect.left >= 0f)
            assertTrue("${rect} top >= 0", rect.top >= 0f)
            assertTrue("${rect} right <= 1", rect.right <= 1.0f)
            assertTrue("${rect} bottom <= 1", rect.bottom <= 1.0f)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // CLASSIC FRAME LAYOUT VALIDATION
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `classic template has wider borders than modern`() {
        val modern = registry.getTemplate(FrameType.MODERN)
        val classic = registry.getTemplate(FrameType.CLASSIC)
        // Classic frames have slightly wider borders (6% vs 5%)
        assertTrue("Classic left margin >= Modern",
            classic.nameBar.left >= modern.nameBar.left)
    }

    @Test
    fun `classic template collector info is further down than modern`() {
        val modern = registry.getTemplate(FrameType.MODERN)
        val classic = registry.getTemplate(FrameType.CLASSIC)
        assertTrue("Classic collector top > Modern",
            classic.collectorInfo.top > modern.collectorInfo.top)
    }

    // ══════════════════════════════════════════════════════════════════
    // FULL-ART FRAME LAYOUT VALIDATION
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `full art template artwork covers entire card`() {
        val template = registry.getTemplate(FrameType.FULL_ART)
        assertEquals("Art top = 0", 0.0f, template.artwork.top, 0.001f)
        assertEquals("Art bottom = 1", 1.0f, template.artwork.bottom, 0.001f)
        assertEquals("Art left = 0", 0.0f, template.artwork.left, 0.001f)
        assertEquals("Art right = 1", 1.0f, template.artwork.right, 0.001f)
    }

    @Test
    fun `full art template has no power toughness`() {
        val template = registry.getTemplate(FrameType.FULL_ART)
        assertNull("Full-art lands should not have P/T", template.powerToughness)
    }

    @Test
    fun `full art template type line is near bottom`() {
        val template = registry.getTemplate(FrameType.FULL_ART)
        assertTrue("Full-art type line > 80% from top", template.typeLine.top > 0.80f)
    }

    // ══════════════════════════════════════════════════════════════════
    // BORDERLESS FRAME LAYOUT VALIDATION
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `borderless template has reasonable name position`() {
        val borderless = registry.getTemplate(FrameType.BORDERLESS)
        assertTrue("Borderless name top < 5%", borderless.nameBar.top < 0.05f)
        assertTrue("Borderless name bottom < 10%", borderless.nameBar.bottom < 0.10f)
    }

    @Test
    fun `borderless template has power toughness`() {
        val template = registry.getTemplate(FrameType.BORDERLESS)
        assertNotNull("Borderless creatures can have P/T", template.powerToughness)
    }

    @Test
    fun `borderless template artwork is full bleed`() {
        val template = registry.getTemplate(FrameType.BORDERLESS)
        assertEquals("Art covers full width", 0.0f, template.artwork.left, 0.001f)
        assertEquals("Art covers full height", 0.0f, template.artwork.top, 0.001f)
    }

    // ══════════════════════════════════════════════════════════════════
    // REGION ORDERING INVARIANTS (applies to all templates)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `standard templates have name above artwork start`() {
        // Full-art and Borderless have art covering the entire card (art behind name)
        // so only check MODERN and CLASSIC where art starts below name
        for (type in listOf(FrameType.MODERN, FrameType.CLASSIC)) {
            val template = registry.getTemplate(type)
            assertTrue("$type: name bottom <= art top + overlap",
                template.nameBar.bottom <= template.artwork.top + 0.05f)
        }
    }

    @Test
    fun `all templates have collector below rules`() {
        for (type in listOf(FrameType.MODERN, FrameType.CLASSIC, FrameType.BORDERLESS)) {
            val template = registry.getTemplate(type)
            assertTrue("$type: collector top > rules bottom - margin",
                template.collectorInfo.top > template.rulesText.bottom - 0.10f)
        }
    }

    @Test
    fun `mana cost is to the right of name bar`() {
        for (type in listOf(FrameType.MODERN, FrameType.CLASSIC, FrameType.FULL_ART, FrameType.BORDERLESS)) {
            val template = registry.getTemplate(type)
            assertTrue("$type: mana left >= name right - overlap",
                template.manaCost.left >= template.nameBar.right - 0.05f)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // BOUNDING RECT SERIALIZATION TESTS
    // These require real Android JSON implementation — run in androidTest
    // ══════════════════════════════════════════════════════════════════

    @org.junit.Ignore("JSONObject requires Android runtime — move to androidTest")
    @Test
    fun `BoundingRect JSON round trip`() {
        val original = BoundingRect(left = 42, top = 17, width = 350, height = 28)
        val json = original.toJson()
        val deserialized = BoundingRect.fromJson(json)
        assertEquals(original, deserialized)
    }

    @org.junit.Ignore("JSONObject requires Android runtime — move to androidTest")
    @Test
    fun `BoundingRect JSON contains all fields`() {
        val rect = BoundingRect(left = 10, top = 20, width = 100, height = 50)
        val json = rect.toJson()
        assertEquals(10, json.getInt("left"))
        assertEquals(20, json.getInt("top"))
        assertEquals(100, json.getInt("width"))
        assertEquals(50, json.getInt("height"))
    }

    // ══════════════════════════════════════════════════════════════════
    // RESOLUTION INDEPENDENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `proportional regions scale linearly with resolution`() {
        val prop = ProportionalRect(top = 0.10f, bottom = 0.50f, left = 0.05f, right = 0.95f)

        val small = BoundingRect.fromProportional(prop.left, prop.top, prop.right, prop.bottom, 100, 140)
        val large = BoundingRect.fromProportional(prop.left, prop.top, prop.right, prop.bottom, 1000, 1400)

        // The proportional area should be the same regardless of resolution
        val smallFraction = small.area.toFloat() / (100 * 140)
        val largeFraction = large.area.toFloat() / (1000 * 1400)
        assertEquals("Area fractions should be equal", smallFraction, largeFraction, 0.02f)
    }

    @Test
    fun `regions at 488x680 standard card size have reasonable pixel dimensions`() {
        val template = registry.getTemplate(FrameType.MODERN)
        val w = 488
        val h = 680

        val nameRect = BoundingRect.fromProportional(
            template.nameBar.left, template.nameBar.top,
            template.nameBar.right, template.nameBar.bottom, w, h
        )
        assertTrue("Name width > 300px at standard res", nameRect.width > 300)
        assertTrue("Name height > 40px at standard res", nameRect.height > 40)
        assertTrue("Name height < 100px at standard res", nameRect.height < 100)
    }

    @Test
    fun `collector region at standard size is readable`() {
        val template = registry.getTemplate(FrameType.MODERN)
        val w = 488
        val h = 680

        val collRect = BoundingRect.fromProportional(
            template.collectorInfo.left, template.collectorInfo.top,
            template.collectorInfo.right, template.collectorInfo.bottom, w, h
        )
        assertTrue("Collector width > 150px", collRect.width > 150)
        assertTrue("Collector height > 15px", collRect.height > 15)
    }

    // ══════════════════════════════════════════════════════════════════
    // EDGE CASE & ROBUSTNESS TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `BoundingRect with zero dimensions is invalid`() {
        val zero = BoundingRect(left = 0, top = 0, width = 0, height = 0)
        assertFalse("Zero-area rect is invalid", zero.isValid)
    }

    @Test
    fun `BoundingRect fromProportional handles 1x1 image`() {
        val rect = BoundingRect.fromProportional(0.0f, 0.0f, 1.0f, 1.0f, 1, 1)
        assertTrue("Should produce valid rect for 1x1", rect.isValid)
    }

    @Test
    fun `proportional coordinates produce non-overlapping name and mana at any resolution`() {
        val template = registry.getTemplate(FrameType.MODERN)
        for (size in listOf(100, 488, 1000, 2992)) {
            val nameRight = (template.nameBar.right * size).toInt()
            val manaLeft = (template.manaCost.left * size).toInt()
            assertTrue("At ${size}px: name right ($nameRight) <= mana left ($manaLeft)",
                nameRight <= manaLeft)
        }
    }
}
