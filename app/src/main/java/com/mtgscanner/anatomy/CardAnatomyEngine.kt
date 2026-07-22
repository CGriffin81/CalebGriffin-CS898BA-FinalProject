package com.mtgscanner.anatomy

import android.graphics.Bitmap
import android.util.Log
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.FrameType
import com.mtgscanner.anatomy.model.RegionType
import com.mtgscanner.anatomy.ocr.CardOcrResults
import com.mtgscanner.anatomy.ocr.RegionOcrPipeline
import com.mtgscanner.model.DetectedCardText
import com.mtgscanner.ocr.OcrPipeline

/**
 * Card Anatomy Engine — orchestrates the anatomy-aware recognition pipeline.
 *
 * Pipeline: CardBitmap → Anatomy Detection → Region-Specific OCR → DetectedCardText
 *
 * Instead of passing the full card bitmap directly to OCR and hoping the parser
 * can figure out which text is which, this engine first identifies card regions
 * (name bar, type line, collector info, etc.) then runs specialized OCR readers
 * on each region independently.
 *
 * The engine preserves backward compatibility by producing [DetectedCardText],
 * which downstream components (Scryfall lookup, FuzzyMatcher, UI) already consume.
 *
 * @param anatomyDetector Rule-based card region detector
 * @param regionOcrPipeline Region-specific OCR readers
 * @param legacyOcrPipeline Legacy full-card OCR (used as fallback when anatomy fails)
 */
class CardAnatomyEngine(
    private val anatomyDetector: CardAnatomyDetector = CardAnatomyDetector(),
    private val regionOcrPipeline: RegionOcrPipeline = RegionOcrPipeline(),
    private val legacyOcrPipeline: OcrPipeline = OcrPipeline(),
    // Kept for backward compat constructor
    @Suppress("UNUSED_PARAMETER")
    ocrPipeline: OcrPipeline = OcrPipeline()
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
     * Analyze a card bitmap through the anatomy-aware pipeline.
     *
     * Steps:
     * 1. Run CardAnatomyDetector to get CardLayout (region positions + confidence)
     * 2. Run RegionOcrPipeline on individual regions (specialized per-region readers)
     * 3. Assemble DetectedCardText from region results
     * 4. Fallback to legacy OcrPipeline if anatomy produces nothing useful
     *
     * @param cardBitmap Expanded card crop from DetectionPipeline
     * @param trackingId Card tracking ID for correlation
     * @return DetectedCardText (backward-compatible output for downstream consumers)
     */
    suspend fun analyze(cardBitmap: Bitmap, trackingId: Int): DetectedCardText {
        Log.d(TAG, "analyze: trackingId=$trackingId, bitmap=${cardBitmap.width}x${cardBitmap.height}")

        // Step 1: Detect card anatomy (fast, ~5-15ms)
        val layout = anatomyDetector.detect(cardBitmap, FrameType.MODERN)
        lastCardLayout = layout
        lastBitmapWidth = cardBitmap.width
        lastBitmapHeight = cardBitmap.height

        Log.d(TAG, "Anatomy detected: ${layout.regions.size} regions, " +
            "name=${layout.findRegion(RegionType.NAME_BAR)?.confidence ?: 0f}, " +
            "collector=${layout.findRegion(RegionType.COLLECTOR_INFO)?.confidence ?: 0f}")

        // Step 2: Run region-specific OCR readers
        val ocrResults = regionOcrPipeline.recognizeAllRegions(cardBitmap, layout)
        lastOcrResults = ocrResults

        // Step 3: Assemble DetectedCardText from region results
        val cardName = ocrResults.name?.name ?: ""
        val setCode = ocrResults.collector?.setCode ?: ""
        val collectorNumber = ocrResults.collector?.collectorNumber ?: ""
        val confidence = ocrResults.overallConfidence

        Log.d(TAG, "Region OCR: name='$cardName' set='$setCode' cn='$collectorNumber' conf=$confidence")

        // Step 4: Fallback to legacy pipeline if anatomy produced no usable name
        if (cardName.isBlank()) {
            Log.w(TAG, "Anatomy produced no name — falling back to legacy OCR")
            val legacyResult = legacyOcrPipeline.recognizeCard(cardBitmap, trackingId)
            if (legacyResult.cardName.isNotBlank()) {
                Log.d(TAG, "Legacy fallback found: '${legacyResult.cardName}'")
                return legacyResult
            }
        }

        val rawText = buildString {
            ocrResults.name?.rawText?.let { if (it.isNotEmpty()) appendLine(it) }
            ocrResults.typeLine?.rawText?.let { if (it.isNotEmpty()) appendLine(it) }
            ocrResults.rules?.rawText?.let { if (it.isNotEmpty()) appendLine(it) }
            ocrResults.collector?.rawText?.let { if (it.isNotEmpty()) appendLine(it) }
        }

        Log.d(TAG, "analyze complete: name='$cardName' set='$setCode' cn='$collectorNumber' conf=$confidence")

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
