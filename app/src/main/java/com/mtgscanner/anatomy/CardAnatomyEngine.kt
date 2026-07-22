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
 * Card Anatomy Engine — layout-driven recognition pipeline.
 *
 * Pipeline: CardBitmap → Anatomy Detection → Region-Specific OCR → DetectedCardText
 *
 * This engine NEVER runs full-card OCR. It identifies card regions geometrically,
 * then dispatches each region to its specialized OCR reader. No reader searches
 * the entire card. No heuristic guesses where information might be.
 *
 * If the anatomy detector cannot locate a region, that field is simply absent
 * from the output — it is not "searched for" elsewhere on the card.
 *
 * @param anatomyDetector Rule-based card region detector
 * @param regionOcrPipeline Region-specific OCR readers (one per semantic field)
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

    /** Last card bitmap dimensions — for overlay coordinate scaling. */
    @Volatile
    var lastBitmapWidth: Int = 0
        private set

    @Volatile
    var lastBitmapHeight: Int = 0
        private set

    /** Last OCR results from region readers — for debug inspection. */
    @Volatile
    var lastOcrResults: CardOcrResults? = null
        private set

    /**
     * Analyze a card bitmap through the anatomy-driven pipeline.
     *
     * Steps:
     * 1. Detect card anatomy → CardLayout with region positions
     * 2. Run per-region OCR readers on their respective crops
     * 3. Assemble DetectedCardText from structured region results
     *
     * No fallback to full-card OCR. No line searching. No guessing.
     * If a region's OCR fails, that field is empty in the output.
     *
     * @param cardBitmap Expanded card crop from DetectionPipeline
     * @param trackingId Card tracking ID for correlation
     * @return DetectedCardText with fields populated only from their correct regions
     */
    suspend fun analyze(cardBitmap: Bitmap, trackingId: Int): DetectedCardText {
        val totalStart = System.currentTimeMillis()
        Log.d(TAG, "analyze: trackingId=$trackingId, bitmap=${cardBitmap.width}x${cardBitmap.height}")

        // Stage 1: Detect card anatomy (deterministic geometry)
        val anatomyStart = System.currentTimeMillis()
        val layout = anatomyDetector.detect(cardBitmap, FrameType.MODERN)
        val anatomyMs = System.currentTimeMillis() - anatomyStart
        lastCardLayout = layout
        lastBitmapWidth = cardBitmap.width
        lastBitmapHeight = cardBitmap.height

        Log.d(TAG, "Stage 1 ANATOMY [${anatomyMs}ms]: ${layout.regions.size} regions, " +
            "nameConf=${layout.findRegion(RegionType.NAME_BAR)?.confidence ?: 0f}, " +
            "collConf=${layout.findRegion(RegionType.COLLECTOR_INFO)?.confidence ?: 0f}")

        // Stage 2: Run field-specific OCR readers (concurrent, each on its own crop)
        val ocrStart = System.currentTimeMillis()
        val ocrResults = regionOcrPipeline.recognizeAllRegions(cardBitmap, layout)
        val ocrMs = System.currentTimeMillis() - ocrStart
        lastOcrResults = ocrResults

        // Stage 3: Assemble output directly from reader results — no parsing, no guessing
        val cardName = ocrResults.name?.name ?: ""
        val setCode = ocrResults.collector?.setCode ?: ""
        val collectorNumber = ocrResults.collector?.collectorNumber ?: ""
        val confidence = ocrResults.overallConfidence

        val rawText = buildString {
            ocrResults.name?.rawText?.let { if (it.isNotEmpty()) appendLine("NAME: $it") }
            ocrResults.typeLine?.rawText?.let { if (it.isNotEmpty()) appendLine("TYPE: $it") }
            ocrResults.collector?.rawText?.let { if (it.isNotEmpty()) appendLine("COLL: $it") }
            ocrResults.powerToughness?.rawText?.let { if (it.isNotEmpty()) appendLine("P/T: $it") }
        }

        val totalMs = System.currentTimeMillis() - totalStart
        Log.d(TAG, "Stage 2 OCR [${ocrMs}ms]: name='$cardName' set='$setCode' cn='$collectorNumber' " +
            "conf=${"%.2f".format(confidence)}")
        Log.d(TAG, "TOTAL [${totalMs}ms]: anatomy=${anatomyMs}ms + ocr=${ocrMs}ms")

        // Expose timing for debug overlay
        lastTimingMs = TimingMetrics(anatomyMs = anatomyMs, ocrMs = ocrMs, totalMs = totalMs)

        return DetectedCardText(
            trackingId = trackingId,
            cardName = cardName,
            setCode = setCode,
            collectorNumber = collectorNumber,
            ocrConfidence = confidence,
            rawOcrText = rawText
        )
    }

    /** Timing metrics from the last analyze() call. */
    @Volatile
    var lastTimingMs: TimingMetrics = TimingMetrics()
        private set
}

/**
 * Timing metrics for the anatomy pipeline stages.
 */
data class TimingMetrics(
    val anatomyMs: Long = 0,
    val ocrMs: Long = 0,
    val totalMs: Long = 0
)
