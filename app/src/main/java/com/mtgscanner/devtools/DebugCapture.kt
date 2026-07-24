package com.mtgscanner.devtools

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.util.Log
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.RegionType
import com.mtgscanner.detection.CardRegion
import com.mtgscanner.detection.DetectionCandidate
import com.mtgscanner.detection.NormalizationResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug Image Capture Pipeline.
 *
 * When enabled, saves every intermediate image from the CV pipeline for one
 * tracking ID into a timestamped directory. Disabled in production.
 *
 * Saved images:
 * 1. Original camera frame
 * 2. Detector output with bounding boxes
 * 3. Expanded card crop
 * 4. Normalized canonical card (488×680)
 * 5. Card with anatomy overlay
 * 6. Individual OCR region crops (Name, Mana, Type, Rules, Set, Collector, Artist, P/T)
 * 7. ML Kit annotated image (all text lines + bounding boxes)
 */
class DebugCapture(private val context: Context) {

    companion object {
        private const val TAG = "DebugCapture"
    }

    var enabled = false
    private var captureDir: File? = null
    private var frameNumber = 0

    /** Start a capture session for one card detection cycle. */
    fun beginSession(trackingId: Int) {
        if (!enabled) return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "debug/${timestamp}_track${trackingId}"
        )
        dir.mkdirs()
        captureDir = dir
        frameNumber = 0
        Log.d(TAG, "Session started: ${dir.absolutePath}")
    }

    /** Save the raw camera frame with detection bounding boxes drawn. */
    fun saveFrameWithDetections(
        frame: Bitmap,
        candidates: List<DetectionCandidate>,
        trackingId: Int
    ) {
        if (!enabled || captureDir == null) return

        val annotated = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val paintAccept = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f }
        val paintReject = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
        val paintText = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true }

        candidates.forEach { c ->
            val r = c.region
            val paint = if (c.accepted) paintAccept else paintReject
            canvas.drawRect(r.x.toFloat(), r.y.toFloat(),
                (r.x + r.width).toFloat(), (r.y + r.height).toFloat(), paint)
            canvas.drawText(c.reason, r.x.toFloat() + 4, r.y.toFloat() + 30, paintText)
        }

        saveBitmap(annotated, "01_frame_detections", trackingId, "frame=$frameNumber")
        annotated.recycle()
        frameNumber++
    }

    /** Save the expanded crop before normalization. */
    fun saveExpandedCrop(crop: Bitmap, normResult: NormalizationResult) {
        if (!enabled || captureDir == null) return
        saveBitmap(crop, "02_expanded_crop", normResult.trackingId,
            "aspect=${"%.3f".format(normResult.aspectRatio)} conf=${"%.2f".format(normResult.confidence)}")
    }

    /** Save the canonical normalized bitmap (488×680). */
    fun saveCanonical(bitmap: Bitmap, trackingId: Int, rotationAngle: Float) {
        if (!enabled || captureDir == null) return
        saveBitmap(bitmap, "03_canonical", trackingId,
            "rotation=${"%.1f".format(rotationAngle)}deg")
    }

    /** Save the card with anatomy region overlay drawn. */
    fun saveAnatomyOverlay(bitmap: Bitmap, layout: CardLayout, trackingId: Int) {
        if (!enabled || captureDir == null) return

        val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 14f; isAntiAlias = true }

        val colors = mapOf(
            RegionType.NAME_BAR to Color.GREEN,
            RegionType.MANA_COST to 0xFFFF9800.toInt(),
            RegionType.TYPE_LINE to Color.BLUE,
            RegionType.RULES_TEXT to Color.GRAY,
            RegionType.COLLECTOR_INFO to Color.YELLOW,
            RegionType.SET_SYMBOL to 0xFFE91E63.toInt(),
            RegionType.ARTIST_CREDIT to 0xFF795548.toInt(),
            RegionType.POWER_TOUGHNESS to Color.RED,
            RegionType.ARTWORK to 0xFF9C27B0.toInt()
        )

        layout.regions.forEach { region ->
            paint.color = colors[region.regionType] ?: Color.WHITE
            val b = region.bounds
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(),
                b.right.toFloat(), b.bottom.toFloat(), paint)
            canvas.drawText(region.regionType.name, b.left.toFloat() + 2,
                b.top.toFloat() + 14, textPaint)
        }

        saveBitmap(annotated, "04_anatomy_overlay", trackingId, "frame=${layout.frameType}")
        annotated.recycle()
    }

    /** Save an individual OCR region crop. */
    fun saveRegionCrop(crop: Bitmap, regionName: String, trackingId: Int) {
        if (!enabled || captureDir == null) return
        saveBitmap(crop, "05_region_$regionName", trackingId,
            "${crop.width}x${crop.height}")
    }

    /** Save the ML Kit annotated image with all text bounding boxes. */
    fun saveMlKitAnnotated(
        bitmap: Bitmap,
        textBlocks: List<MlKitBlock>,
        trackingId: Int
    ) {
        if (!enabled || captureDir == null) return

        val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val boxPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 1f }
        val textPaint = Paint().apply { color = Color.CYAN; textSize = 10f; isAntiAlias = true }

        textBlocks.forEach { block ->
            block.lines.forEach { line ->
                if (line.bounds != null) {
                    canvas.drawRect(line.bounds, boxPaint)
                    canvas.drawText(line.text.take(30),
                        line.bounds.left.toFloat(), line.bounds.top.toFloat() - 2, textPaint)
                }
            }
        }

        saveBitmap(annotated, "06_mlkit_annotated", trackingId,
            "blocks=${textBlocks.size} lines=${textBlocks.sumOf { it.lines.size }}")
        annotated.recycle()
    }

    /** End the current capture session. */
    fun endSession() {
        if (!enabled) return
        Log.d(TAG, "Session ended: ${captureDir?.absolutePath}")
        captureDir = null
    }

    private fun saveBitmap(bitmap: Bitmap, prefix: String, trackingId: Int, metadata: String) {
        val dir = captureDir ?: return
        val filename = "${prefix}_id${trackingId}_${metadata.replace(' ', '_').replace('=', '-')}.png"
        val file = File(dir, filename.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Saved: ${file.name} (${bitmap.width}×${bitmap.height})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ${file.name}: ${e.message}")
        }
    }
}

/** Simplified ML Kit block/line for annotation. */
data class MlKitBlock(val lines: List<MlKitLine>)
data class MlKitLine(val text: String, val bounds: Rect?)
