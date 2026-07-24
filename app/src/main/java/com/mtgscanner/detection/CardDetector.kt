package com.mtgscanner.detection

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Detects card-shaped rectangular objects using ML Kit Object Detection.
 *
 * Replaces the custom edge-based flood-fill algorithm that failed at various distances.
 * ML Kit handles:
 * - Any distance (close-up filling frame, arm's length, far away)
 * - Any background (wood, playmat, binder, other cards)
 * - Any lighting condition
 * - Perspective distortion
 *
 * Post-ML Kit filtering:
 * - Aspect ratio 0.45–0.95 (MTG card is 0.716, with perspective tolerance)
 * - Minimum area 0.3% of frame (rejects tiny noise detections)
 */
class CardDetector {

    companion object {
        private const val TAG = "CardDetector"

        /** Card aspect ratio bounds (width/height) — generous for perspective */
        private const val MIN_ASPECT_RATIO = 0.45f
        private const val MAX_ASPECT_RATIO = 0.95f

        /** Minimum detection area as fraction of frame */
        private const val MIN_AREA_FRACTION = 0.003f
    }

    private val detector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    /** All candidates from the last detection (accepted + rejected with reasons). */
    @Volatile
    var lastCandidates: List<DetectionCandidate> = emptyList()
        private set

    /**
     * Detect all card-like rectangular objects in a bitmap frame.
     *
     * Uses ML Kit Object Detection to find objects, then filters by
     * aspect ratio to identify card-shaped rectangles.
     */
    fun detectCards(bitmap: Bitmap): List<CardRegion> {
        // ML Kit Object Detection is async — we run it synchronously here
        // using a blocking approach since detectCards is called from a dedicated thread.
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val results = com.google.android.gms.tasks.Tasks.await(detector.process(image))

            val frameArea = bitmap.width * bitmap.height
            val minArea = (frameArea * MIN_AREA_FRACTION).toInt()
            val candidates = mutableListOf<DetectionCandidate>()
            val accepted = mutableListOf<CardRegion>()

            for (obj in results) {
                val box = obj.boundingBox
                val regionWidth = box.width()
                val regionHeight = box.height()
                val regionArea = regionWidth * regionHeight
                val areaFraction = regionArea.toFloat() / frameArea
                val aspectRatio = regionWidth.toFloat() / regionHeight.toFloat()

                val region = CardRegion(
                    x = box.left.coerceAtLeast(0),
                    y = box.top.coerceAtLeast(0),
                    width = regionWidth.coerceAtMost(bitmap.width - box.left),
                    height = regionHeight.coerceAtMost(bitmap.height - box.top),
                    area = regionArea
                )

                when {
                    regionArea < minArea -> {
                        candidates.add(DetectionCandidate(region, false,
                            "Area too small (${"%.1f".format(areaFraction * 100)}%)",
                            aspectRatio, 1f, areaFraction))
                    }
                    aspectRatio < MIN_ASPECT_RATIO -> {
                        candidates.add(DetectionCandidate(region, false,
                            "Aspect ${"%.2f".format(aspectRatio)} (too narrow)",
                            aspectRatio, 1f, areaFraction))
                    }
                    aspectRatio > MAX_ASPECT_RATIO -> {
                        candidates.add(DetectionCandidate(region, false,
                            "Aspect ${"%.2f".format(aspectRatio)} (too wide)",
                            aspectRatio, 1f, areaFraction))
                    }
                    else -> {
                        candidates.add(DetectionCandidate(region, true,
                            "Accepted (ML Kit)",
                            aspectRatio, 1f, areaFraction))
                        accepted.add(region)
                    }
                }
            }

            lastCandidates = candidates

            if (accepted.isNotEmpty()) {
                Log.d(TAG, "ML Kit: ${results.size} objects → ${accepted.size} cards " +
                    "(${candidates.count { !it.accepted }} rejected)")
            }

            accepted
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit Object Detection failed: ${e.message}", e)
            lastCandidates = emptyList()
            emptyList()
        }
    }

    fun extractCardImage(bitmap: Bitmap, region: CardRegion): Bitmap {
        val safeX = region.x.coerceIn(0, bitmap.width - 1)
        val safeY = region.y.coerceIn(0, bitmap.height - 1)
        val safeW = region.width.coerceAtMost(bitmap.width - safeX).coerceAtLeast(1)
        val safeH = region.height.coerceAtMost(bitmap.height - safeY).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
    }

    fun perspectiveCorrect(cardBitmap: Bitmap): Bitmap = cardBitmap
}

data class CardRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int,
    var trackingId: Int = -1
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}
