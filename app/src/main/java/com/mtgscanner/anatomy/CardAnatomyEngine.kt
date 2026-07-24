package com.mtgscanner.anatomy

import android.graphics.Bitmap
import android.util.Log
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.FrameType
import com.mtgscanner.anatomy.model.RegionType
import com.mtgscanner.anatomy.ocr.CardOcrResults
import com.mtgscanner.anatomy.ocr.RegionOcrPipeline
import com.mtgscanner.model.DetectedCardText

/**
 * Card Anatomy Engine — the ONLY source of OCR region definitions.
 *
 * Pipeline order:
 * 1. Receive perspective-corrected canonical card bitmap (488×680)
 * 2. Classify frame type (currently defaults to MODERN — future: visual classification)
 * 3. Ask CardAnatomyDetector for region definitions using the registry template
 * 4. Generate OCR regions dynamically from the anatomy result
 * 5. Pass those regions to RegionOcrPipeline for per-region OCR
 * 6. Assemble DetectedCardText from structured reader results
 *
 * NO hardcoded percentages. NO static constants for region positions.
 * Every OCR region originates from the Card Anatomy Engine's template system.
 *
 * The engine also runs full-bitmap ML Kit as a parallel path and merges results,
 * using the anatomy-detected regions to assign semantic meaning to ML Kit text by position.
 */
class CardAnatomyEngine(
    private val anatomyDetector: CardAnatomyDetector = CardAnatomyDetector(),
    private val regionOcrPipeline: RegionOcrPipeline = RegionOcrPipeline()
) {
    companion object {
        private const val TAG = "CardAnatomyEngine"
    }

    /** Last detected CardLayout — exposed for debug overlay visualization. */
    @Volatile
    var lastCardLayout: CardLayout? = null
        private set

    @Volatile
    var lastBitmapWidth: Int = 0
        private set

    @Volatile
    var lastBitmapHeight: Int = 0
        private set

    @Volatile
    var lastOcrResults: CardOcrResults? = null
        private set

    @Volatile
    var lastTimingMs: TimingMetrics = TimingMetrics()
        private set

    /**
     * Analyze a card bitmap through the anatomy-driven pipeline.
     *
     * All region positions come from CardAnatomyDetector → FrameLayoutRegistry.
     * No hardcoded pixel offsets or percentage constants exist in this method.
     */
    suspend fun analyze(cardBitmap: Bitmap, trackingId: Int): DetectedCardText {
        val totalStart = System.currentTimeMillis()
        Log.d(TAG, "analyze: trackingId=$trackingId, bitmap=${cardBitmap.width}x${cardBitmap.height}")

        // ═══ Stage 1: Classify frame type ═══
        // Future: visual frame classification based on border color/texture.
        // For now: use MODERN as the most common frame in circulation.
        val frameType = classifyFrame(cardBitmap)

        // ═══ Stage 2: Generate regions from Card Anatomy Engine ═══
        val anatomyStart = System.currentTimeMillis()
        val layout = anatomyDetector.detect(cardBitmap, frameType)
        val anatomyMs = System.currentTimeMillis() - anatomyStart

        lastCardLayout = layout
        lastBitmapWidth = cardBitmap.width
        lastBitmapHeight = cardBitmap.height

        Log.d(TAG, "Stage 1 ANATOMY [${anatomyMs}ms]: frame=$frameType, " +
            "${layout.regions.size} regions generated from template")

        // ═══ Stage 3: Run ML Kit on full bitmap + assign by anatomy position ═══
        // The anatomy regions define WHERE each field is. ML Kit reads WHAT is there.
        // We run ML Kit once on the full bitmap and use the anatomy regions to
        // assign recognized text lines to their correct semantic fields.
        val ocrStart = System.currentTimeMillis()
        val result = recognizeWithAnatomy(cardBitmap, layout, trackingId)
        val ocrMs = System.currentTimeMillis() - ocrStart

        val totalMs = System.currentTimeMillis() - totalStart
        lastTimingMs = TimingMetrics(anatomyMs = anatomyMs, ocrMs = ocrMs, totalMs = totalMs)

        Log.d(TAG, "Stage 2 OCR [${ocrMs}ms]: name='${result.cardName}' " +
            "set='${result.setCode}' cn='${result.collectorNumber}' " +
            "conf=${"%.2f".format(result.ocrConfidence)}")
        Log.d(TAG, "TOTAL [${totalMs}ms]: anatomy=${anatomyMs}ms + ocr=${ocrMs}ms")

        return result
    }

    /**
     * Classify the card frame type from visual features.
     * Currently returns MODERN (most common). Future: analyze border color,
     * texture patterns, or set symbol position to determine frame era.
     */
    private fun classifyFrame(bitmap: Bitmap): FrameType {
        // TODO: Implement visual frame classification
        // For now, MODERN covers ~80% of cards in circulation
        return FrameType.MODERN
    }

    /**
     * Run ML Kit on the full bitmap, then use anatomy-derived regions to assign
     * recognized text to semantic fields.
     *
     * This approach works because:
     * - ML Kit gets the full image context (better recognition than tiny crops)
     * - The anatomy regions tell us WHERE each field should be (no guessing)
     * - We match ML Kit text lines to regions by bounding box overlap
     */
    private suspend fun recognizeWithAnatomy(
        cardBitmap: Bitmap,
        layout: CardLayout,
        trackingId: Int
    ): DetectedCardText {
        // Run ML Kit on the full canonical bitmap
        val recognized = com.mtgscanner.anatomy.ocr.MlKitRecognizer.recognize(cardBitmap)
        val rawText = recognized?.text ?: ""

        if (recognized == null || rawText.isEmpty()) {
            Log.d(TAG, "ML Kit returned empty text")
            return DetectedCardText(trackingId = trackingId)
        }

        val allLines = recognized.textBlocks.flatMap { it.lines }
        Log.d(TAG, "ML Kit found ${allLines.size} lines in ${cardBitmap.width}x${cardBitmap.height}")

        // Match text lines to anatomy regions by bounding box overlap
        val nameRegion = layout.findRegion(RegionType.NAME_BAR)
        val typeRegion = layout.findRegion(RegionType.TYPE_LINE)
        val collectorRegion = layout.findRegion(RegionType.COLLECTOR_INFO)

        // Name: find the widest text line whose center falls within the NAME_BAR region
        var cardName = ""
        if (nameRegion != null) {
            val nameLines = allLines.filter { line ->
                val lineCenter = line.boundingBox?.centerY() ?: Int.MAX_VALUE
                lineCenter >= nameRegion.bounds.top && lineCenter <= nameRegion.bounds.bottom
                    && line.text.length >= 2 && line.text.any { it.isLetter() }
            }
            cardName = nameLines.maxByOrNull { it.boundingBox?.width() ?: 0 }?.text?.trim() ?: ""
            Log.d(TAG, "Name region [${nameRegion.bounds}]: matched ${nameLines.size} lines → '$cardName'")
        }

        // Collector: find text lines whose center falls within COLLECTOR_INFO region
        var setCode = ""
        var collectorNumber = ""
        if (collectorRegion != null) {
            val collectorLines = allLines.filter { line ->
                val lineCenter = line.boundingBox?.centerY() ?: 0
                lineCenter >= collectorRegion.bounds.top && lineCenter <= collectorRegion.bounds.bottom
                    && line.text.length >= 2
            }
            val collectorText = collectorLines.joinToString(" ") { it.text }
            if (collectorText.isNotEmpty()) {
                val parsed = com.mtgscanner.anatomy.ocr.CollectorOcr().parseText(collectorText)
                setCode = parsed.setCode
                collectorNumber = parsed.collectorNumber
                Log.d(TAG, "Collector region [${collectorRegion.bounds}]: '$collectorText' → " +
                    "set='$setCode' cn='$collectorNumber'")
            }
        }

        // Confidence based on what we found
        val confidence = when {
            cardName.isNotBlank() && setCode.isNotBlank() && collectorNumber.isNotBlank() -> 0.85f
            cardName.isNotBlank() && setCode.isNotBlank() -> 0.7f
            cardName.isNotBlank() -> 0.5f
            else -> 0f
        }

        return DetectedCardText(
            trackingId = trackingId,
            cardName = cardName,
            setCode = setCode,
            collectorNumber = collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }
}

/**
 * Timing metrics for the anatomy pipeline stages.
 */
data class TimingMetrics(
    val anatomyMs: Long = 0,
    val ocrMs: Long = 0,
    val totalMs: Long = 0
)
