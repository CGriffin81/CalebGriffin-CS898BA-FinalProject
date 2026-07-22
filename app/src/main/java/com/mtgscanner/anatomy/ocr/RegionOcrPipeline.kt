package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.RegionType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates all region-specific OCR readers using a [CardLayout].
 *
 * Instead of running OCR on the entire card and parsing semantics from raw text,
 * this pipeline:
 * 1. Receives a [CardLayout] describing where each region is
 * 2. Crops the card bitmap into individual region bitmaps
 * 3. Dispatches each crop to its specialized reader
 * 4. Collects structured results from each reader
 *
 * Readers run concurrently for performance.
 * Each reader only receives the bitmap for its assigned region.
 * No reader performs Scryfall lookup or card identification.
 */
class RegionOcrPipeline(
    private val nameOcr: NameOcr = NameOcr(),
    private val typeLineOcr: TypeLineOcr = TypeLineOcr(),
    private val collectorOcr: CollectorOcr = CollectorOcr(),
    private val rulesOcr: RulesOcr = RulesOcr(),
    private val artistOcr: ArtistOcr = ArtistOcr(),
    private val powerToughnessOcr: PowerToughnessOcr = PowerToughnessOcr()
) {
    companion object {
        private const val TAG = "RegionOcrPipeline"
    }

    /**
     * Run all region OCR readers on their respective card regions.
     *
     * Crops each region from the source bitmap based on [CardLayout] bounds,
     * then dispatches to specialized readers concurrently.
     *
     * @param cardBitmap The full card bitmap (same one used for anatomy detection)
     * @param layout The detected CardLayout with region positions
     * @return [CardOcrResults] containing all region reader outputs
     */
    suspend fun recognizeAllRegions(
        cardBitmap: Bitmap,
        layout: CardLayout
    ): CardOcrResults = coroutineScope {
        Log.d(TAG, "recognizeAllRegions: ${layout.regions.size} regions from ${cardBitmap.width}x${cardBitmap.height}")

        // Crop each region bitmap
        val nameBitmap = cropRegion(cardBitmap, layout, RegionType.NAME_BAR)
        val typeBitmap = cropRegion(cardBitmap, layout, RegionType.TYPE_LINE)
        val collectorBitmap = cropRegion(cardBitmap, layout, RegionType.COLLECTOR_INFO)
        val rulesBitmap = cropRegion(cardBitmap, layout, RegionType.RULES_TEXT)
        val artistBitmap = cropRegion(cardBitmap, layout, RegionType.ARTIST_CREDIT)
        val ptBitmap = cropRegion(cardBitmap, layout, RegionType.POWER_TOUGHNESS)

        // Run readers concurrently
        val nameDeferred = async { nameBitmap?.let { nameOcr.read(it) } }
        val typeDeferred = async { typeBitmap?.let { typeLineOcr.read(it) } }
        val collectorDeferred = async { collectorBitmap?.let { collectorOcr.read(it) } }
        val rulesDeferred = async { rulesBitmap?.let { rulesOcr.read(it) } }
        val artistDeferred = async { artistBitmap?.let { artistOcr.read(it) } }
        val ptDeferred = async { ptBitmap?.let { powerToughnessOcr.read(it) } }

        val results = CardOcrResults(
            name = nameDeferred.await(),
            typeLine = typeDeferred.await(),
            collector = collectorDeferred.await(),
            rules = rulesDeferred.await(),
            artist = artistDeferred.await(),
            powerToughness = ptDeferred.await()
        )

        Log.d(TAG, "All regions complete: name='${results.name?.name}' " +
            "type='${results.typeLine?.fullTypeLine}' " +
            "collector='${results.collector?.collectorNumber}/${results.collector?.setCode}' " +
            "pt='${results.powerToughness?.power}/${results.powerToughness?.toughness}'")

        results
    }

    /**
     * Crop a single region from the card bitmap using CardLayout bounds.
     * Returns null if the region doesn't exist in the layout or has degenerate bounds.
     */
    private fun cropRegion(bitmap: Bitmap, layout: CardLayout, type: RegionType): Bitmap? {
        val region = layout.findRegion(type) ?: return null
        val bounds = region.bounds

        // Validate bounds
        val safeLeft = bounds.left.coerceIn(0, bitmap.width - 1)
        val safeTop = bounds.top.coerceIn(0, bitmap.height - 1)
        val safeWidth = bounds.width().coerceAtMost(bitmap.width - safeLeft).coerceAtLeast(1)
        val safeHeight = bounds.height().coerceAtMost(bitmap.height - safeTop).coerceAtLeast(1)

        if (safeWidth < 5 || safeHeight < 5) {
            Log.w(TAG, "Region $type too small: ${safeWidth}x${safeHeight}, skipping")
            return null
        }

        return Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
    }
}

/**
 * Aggregated results from all region OCR readers.
 * Each field is nullable — a region may not have been detected or OCR may have failed.
 */
data class CardOcrResults(
    val name: NameOcrResult?,
    val typeLine: TypeLineOcrResult?,
    val collector: CollectorOcrResult?,
    val rules: RulesOcrResult?,
    val artist: ArtistOcrResult?,
    val powerToughness: PowerToughnessOcrResult?
) {
    /** Overall confidence: average of all non-null region confidences. */
    val overallConfidence: Float
        get() {
            val scores = listOfNotNull(
                name?.confidence,
                typeLine?.confidence,
                collector?.confidence
            )
            return if (scores.isEmpty()) 0f else scores.average().toFloat()
        }
}
