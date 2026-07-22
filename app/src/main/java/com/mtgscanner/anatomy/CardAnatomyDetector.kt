package com.mtgscanner.anatomy

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.CardRegion
import com.mtgscanner.anatomy.model.FrameLayoutTemplate
import com.mtgscanner.anatomy.model.FrameType
import com.mtgscanner.anatomy.model.ProportionalRect
import com.mtgscanner.anatomy.model.RegionType

/**
 * Rule-based Card Anatomy Detector.
 *
 * Accepts a (perspective-corrected) card bitmap and detects semantic regions
 * using deterministic computer vision — no machine learning.
 *
 * Detection strategy:
 * 1. Start with proportional geometry from a [FrameLayoutTemplate]
 * 2. Refine region boundaries using horizontal edge detection (finds actual divider lines)
 * 3. Validate each region with contrast/content checks
 * 4. Assign per-region confidence based on detection quality
 *
 * The detector is modular: new frame types can be added to [FrameLayoutRegistry]
 * without modifying this class.
 */
class CardAnatomyDetector(
    private val registry: FrameLayoutRegistry = FrameLayoutRegistry()
) {
    companion object {
        private const val TAG = "CardAnatomyDetector"

        /** How far (in fraction of region height) to search for refined edges */
        private const val REFINEMENT_SEARCH_FRACTION = 0.3f

        /** Minimum edge magnitude to consider a horizontal divider */
        private const val DIVIDER_EDGE_THRESHOLD = 30
    }

    /**
     * Detect all semantic regions on a card bitmap.
     *
     * @param cardBitmap Perspective-corrected card image (or best-available crop)
     * @param frameType The classified frame type. Use [FrameType.UNKNOWN] for auto/default.
     * @return [CardLayout] containing all detected regions with confidence scores.
     */
    fun detect(cardBitmap: Bitmap, frameType: FrameType = FrameType.UNKNOWN): CardLayout {
        val w = cardBitmap.width
        val h = cardBitmap.height
        val template = registry.getTemplate(frameType)

        Log.d(TAG, "Detecting anatomy: ${w}x${h}, frame=$frameType")

        val regions = mutableListOf<CardRegion>()

        // Detect each region using template proportions + edge refinement
        regions.add(detectRegion(cardBitmap, template.nameBar, RegionType.NAME_BAR, w, h))
        regions.add(detectRegion(cardBitmap, template.manaCost, RegionType.MANA_COST, w, h))
        regions.add(detectRegion(cardBitmap, template.artwork, RegionType.ARTWORK, w, h))
        regions.add(detectRegion(cardBitmap, template.typeLine, RegionType.TYPE_LINE, w, h))
        regions.add(detectRegion(cardBitmap, template.rulesText, RegionType.RULES_TEXT, w, h))
        regions.add(detectRegion(cardBitmap, template.setSymbol, RegionType.SET_SYMBOL, w, h))
        regions.add(detectRegion(cardBitmap, template.collectorInfo, RegionType.COLLECTOR_INFO, w, h))
        regions.add(detectRegion(cardBitmap, template.artistCredit, RegionType.ARTIST_CREDIT, w, h))

        // P/T is optional (only creatures have it)
        if (template.powerToughness != null) {
            val ptRegion = detectPowerToughness(cardBitmap, template.powerToughness, w, h)
            if (ptRegion != null) {
                regions.add(ptRegion)
            }
        }

        Log.d(TAG, "Detected ${regions.size} regions (frame=$frameType)")
        regions.forEach { r ->
            Log.d(TAG, "  ${r.regionType}: ${r.bounds} conf=${String.format("%.2f", r.confidence)}")
        }

        return CardLayout(
            regions = regions,
            frameType = frameType,
            imageWidth = w,
            imageHeight = h
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // REGION DETECTION (proportional + edge refinement)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect a single region using proportional template + edge-based refinement.
     *
     * Strategy:
     * 1. Convert proportional coordinates to pixel bounds
     * 2. Search for strong horizontal edges near expected top/bottom boundaries
     * 3. If edges found, snap boundaries to actual divider lines
     * 4. Compute confidence based on edge strength and region validity
     */
    private fun detectRegion(
        bitmap: Bitmap,
        proportional: ProportionalRect,
        regionType: RegionType,
        width: Int,
        height: Int
    ): CardRegion {
        // Step 1: Convert proportional to pixel coordinates
        val baseRect = proportionalToPixel(proportional, width, height)

        // Step 2: Refine boundaries by searching for horizontal dividers
        val refinedRect = refineVerticalBounds(bitmap, baseRect, width, height)

        // Step 3: Compute confidence
        val confidence = computeRegionConfidence(bitmap, refinedRect, regionType)

        return CardRegion(
            regionType = regionType,
            bounds = refinedRect,
            confidence = confidence
        )
    }

    /**
     * Detect Power/Toughness region with additional validation.
     * P/T box has a distinctive visual signature: small rectangular region in bottom-right
     * with a number/number pattern. Returns null if no P/T box is detected.
     */
    private fun detectPowerToughness(
        bitmap: Bitmap,
        proportional: ProportionalRect,
        width: Int,
        height: Int
    ): CardRegion? {
        val baseRect = proportionalToPixel(proportional, width, height)

        // Validate: P/T box should have different luminance than surrounding rules text
        val ptLuminance = averageLuminance(bitmap, baseRect)
        val aboveRect = Rect(
            baseRect.left, (baseRect.top - baseRect.height()).coerceAtLeast(0),
            baseRect.right, baseRect.top
        )
        val aboveLuminance = averageLuminance(bitmap, aboveRect)

        // P/T box is typically lighter (white/cream background with black numbers)
        // compared to the rules text area above it. Require noticeable contrast difference.
        val contrastDiff = kotlin.math.abs(ptLuminance - aboveLuminance)
        val hasPtBox = contrastDiff > 15

        return if (hasPtBox) {
            CardRegion(
                regionType = RegionType.POWER_TOUGHNESS,
                bounds = baseRect,
                confidence = (contrastDiff / 60f).coerceIn(0.3f, 0.95f)
            )
        } else {
            null  // No P/T box detected — likely not a creature
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE REFINEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Refine the vertical (top/bottom) bounds of a region by searching for
     * strong horizontal edges near the expected boundaries.
     *
     * Cards have clear horizontal dividers between regions (name bar bottom edge,
     * type line top/bottom edges, text box bottom). This method looks for the
     * strongest horizontal gradient within a search window around the expected boundary.
     */
    private fun refineVerticalBounds(
        bitmap: Bitmap,
        baseRect: Rect,
        width: Int,
        height: Int
    ): Rect {
        val searchRange = ((baseRect.bottom - baseRect.top) * REFINEMENT_SEARCH_FRACTION).toInt()
            .coerceAtLeast(3)

        // Refine top boundary
        val refinedTop = findHorizontalEdge(
            bitmap,
            searchCenterY = baseRect.top,
            searchRange = searchRange,
            xStart = baseRect.left,
            xEnd = baseRect.right,
            width = width,
            height = height
        ) ?: baseRect.top

        // Refine bottom boundary
        val refinedBottom = findHorizontalEdge(
            bitmap,
            searchCenterY = baseRect.bottom,
            searchRange = searchRange,
            xStart = baseRect.left,
            xEnd = baseRect.right,
            width = width,
            height = height
        ) ?: baseRect.bottom

        return Rect(
            baseRect.left,
            refinedTop.coerceIn(0, height - 1),
            baseRect.right,
            refinedBottom.coerceIn(refinedTop + 1, height)
        )
    }

    /**
     * Search for the strongest horizontal edge within a vertical range.
     * Uses horizontal gradient summation — a strong horizontal divider has
     * consistent vertical gradient across the full width of the region.
     *
     * @return Y coordinate of the strongest edge, or null if no significant edge found.
     */
    private fun findHorizontalEdge(
        bitmap: Bitmap,
        searchCenterY: Int,
        searchRange: Int,
        xStart: Int,
        xEnd: Int,
        width: Int,
        height: Int
    ): Int? {
        val yMin = (searchCenterY - searchRange).coerceIn(1, height - 2)
        val yMax = (searchCenterY + searchRange).coerceIn(1, height - 2)
        val xS = xStart.coerceIn(0, width - 1)
        val xE = xEnd.coerceIn(xS + 1, width)

        var bestY = -1
        var bestStrength = 0

        // Sample pixels along horizontal lines and compute vertical gradient
        val sampleStep = ((xE - xS) / 20).coerceAtLeast(1)  // Sample ~20 points across

        for (y in yMin..yMax) {
            var edgeSum = 0
            var samples = 0

            var x = xS
            while (x < xE) {
                val above = luminanceAt(bitmap, x, y - 1)
                val below = luminanceAt(bitmap, x, y + 1)
                edgeSum += kotlin.math.abs(below - above)
                samples++
                x += sampleStep
            }

            val avgEdge = if (samples > 0) edgeSum / samples else 0
            if (avgEdge > bestStrength) {
                bestStrength = avgEdge
                bestY = y
            }
        }

        return if (bestStrength >= DIVIDER_EDGE_THRESHOLD) bestY else null
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIDENCE COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute confidence for a detected region based on visual characteristics.
     *
     * Factors:
     * - Region size validity (not degenerate)
     * - Content presence (non-uniform luminance indicates text/graphics)
     * - Region-specific heuristics (e.g., name bar should have high contrast text)
     */
    private fun computeRegionConfidence(
        bitmap: Bitmap,
        rect: Rect,
        regionType: RegionType
    ): Float {
        // Base confidence from region validity
        val regionWidth = rect.width()
        val regionHeight = rect.height()
        if (regionWidth <= 0 || regionHeight <= 0) return 0f

        val imageArea = bitmap.width * bitmap.height
        val regionArea = regionWidth * regionHeight
        val areaFraction = regionArea.toFloat() / imageArea

        // Degenerate regions get low confidence
        if (areaFraction < 0.001f) return 0.1f

        // Check content variance (uniform = probably wrong, varied = has content)
        val variance = luminanceVariance(bitmap, rect)

        // Region-specific confidence adjustments
        val baseConfidence = when (regionType) {
            RegionType.NAME_BAR -> {
                // Name bar should have moderate variance (text on colored background)
                if (variance > 500) 0.85f else if (variance > 100) 0.70f else 0.40f
            }
            RegionType.ARTWORK -> {
                // Art should have high variance (complex image)
                if (variance > 2000) 0.90f else if (variance > 500) 0.75f else 0.50f
            }
            RegionType.TYPE_LINE -> {
                // Type line: text on background, moderate variance
                if (variance > 300) 0.80f else if (variance > 50) 0.65f else 0.35f
            }
            RegionType.COLLECTOR_INFO -> {
                // Collector: small text, lower variance is acceptable
                if (variance > 100) 0.75f else if (variance > 20) 0.60f else 0.30f
            }
            RegionType.RULES_TEXT -> {
                // Rules text: moderate variance from text content
                if (variance > 200) 0.80f else if (variance > 50) 0.65f else 0.45f
            }
            RegionType.MANA_COST -> {
                // Mana symbols: colored circles on background
                if (variance > 300) 0.80f else if (variance > 50) 0.60f else 0.40f
            }
            RegionType.SET_SYMBOL -> {
                // Set symbol: small colored icon
                if (variance > 200) 0.75f else 0.50f
            }
            RegionType.ARTIST_CREDIT -> {
                // Artist text: small, low contrast often
                if (variance > 50) 0.70f else 0.45f
            }
            RegionType.POWER_TOUGHNESS -> {
                // P/T: high contrast numbers on light box
                if (variance > 500) 0.85f else if (variance > 100) 0.70f else 0.40f
            }
        }

        return baseConfidence
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════

    /** Convert proportional (0–1) coordinates to pixel Rect. */
    private fun proportionalToPixel(prop: ProportionalRect, width: Int, height: Int): Rect {
        return Rect(
            (prop.left * width).toInt().coerceIn(0, width - 1),
            (prop.top * height).toInt().coerceIn(0, height - 1),
            (prop.right * width).toInt().coerceIn(1, width),
            (prop.bottom * height).toInt().coerceIn(1, height)
        )
    }

    /** Get luminance (0–255) at a specific pixel. */
    private fun luminanceAt(bitmap: Bitmap, x: Int, y: Int): Int {
        val px = bitmap.getPixel(
            x.coerceIn(0, bitmap.width - 1),
            y.coerceIn(0, bitmap.height - 1)
        )
        return (Color.red(px) * 77 + Color.green(px) * 150 + Color.blue(px) * 29) shr 8
    }

    /** Compute average luminance within a rect (sampled for performance). */
    private fun averageLuminance(bitmap: Bitmap, rect: Rect): Int {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height)
        )
        val w = safeRect.width()
        val h = safeRect.height()
        if (w <= 0 || h <= 0) return 128

        val stepX = (w / 10).coerceAtLeast(1)
        val stepY = (h / 10).coerceAtLeast(1)
        var sum = 0L
        var count = 0

        var y = safeRect.top
        while (y < safeRect.bottom) {
            var x = safeRect.left
            while (x < safeRect.right) {
                sum += luminanceAt(bitmap, x, y)
                count++
                x += stepX
            }
            y += stepY
        }

        return if (count > 0) (sum / count).toInt() else 128
    }

    /** Compute luminance variance within a rect (sampled for performance). */
    private fun luminanceVariance(bitmap: Bitmap, rect: Rect): Float {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height)
        )
        val w = safeRect.width()
        val h = safeRect.height()
        if (w <= 0 || h <= 0) return 0f

        val stepX = (w / 12).coerceAtLeast(1)
        val stepY = (h / 12).coerceAtLeast(1)
        val samples = mutableListOf<Int>()

        var y = safeRect.top
        while (y < safeRect.bottom) {
            var x = safeRect.left
            while (x < safeRect.right) {
                samples.add(luminanceAt(bitmap, x, y))
                x += stepX
            }
            y += stepY
        }

        if (samples.size < 2) return 0f

        val mean = samples.average().toFloat()
        return samples.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}
