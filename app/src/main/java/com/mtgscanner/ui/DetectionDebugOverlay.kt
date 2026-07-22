package com.mtgscanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mtgscanner.detection.CardNormalizer
import com.mtgscanner.detection.CardRegion
import com.mtgscanner.detection.NormalizationResult

/**
 * Debug overlay showing detection pipeline state on the camera preview.
 *
 * Visualizes:
 * - Detection boxes (cyan outline) — raw CardDetector output
 * - Expanded crop region (yellow outline) — what gets normalized
 * - Tracking ID label on each detection
 * - Normalization confidence percentage
 * - Rejected detections (red, crossed out)
 *
 * @param detections Current frame's detected card regions
 * @param lastNormalization Most recent normalization result (shows expanded box + metadata)
 * @param frameWidth Width of the camera frame (for coordinate scaling)
 * @param frameHeight Height of the camera frame (for coordinate scaling)
 * @param showOverlay Whether to render (developer toggle)
 */
@Composable
fun DetectionDebugOverlay(
    detections: List<CardRegion>,
    lastNormalization: NormalizationResult?,
    frameWidth: Int,
    frameHeight: Int,
    showOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    if (!showOverlay) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height
        val scaleX = canvasW / frameWidth.toFloat().coerceAtLeast(1f)
        val scaleY = canvasH / frameHeight.toFloat().coerceAtLeast(1f)

        // Draw all detection boxes (cyan)
        for (region in detections) {
            val left = region.x * scaleX
            val top = region.y * scaleY
            val width = region.width * scaleX
            val height = region.height * scaleY

            drawRect(
                color = Color.Cyan,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 2f)
            )

            // Aspect ratio label
            val aspect = region.width.toFloat() / region.height.toFloat()
            drawText(
                textMeasurer = textMeasurer,
                text = "AR:${"%.2f".format(aspect)}",
                topLeft = Offset(left + 4f, top + height - 14f),
                style = TextStyle(color = Color.Cyan, fontSize = 8.sp)
            )
        }

        // Draw last normalization result
        lastNormalization?.let { norm ->
            // Detected region (green = accepted, red = rejected)
            val detColor = if (norm.rejected) Color.Red else Color.Green
            val det = norm.detectedRegion
            drawRect(
                color = detColor,
                topLeft = Offset(det.x * scaleX, det.y * scaleY),
                size = Size(det.width * scaleX, det.height * scaleY),
                style = Stroke(width = 3f)
            )

            // Expanded region (yellow dashed effect — solid for now)
            if (!norm.rejected) {
                val exp = norm.expandedRegion
                drawRect(
                    color = Color.Yellow.copy(alpha = 0.7f),
                    topLeft = Offset(exp.x * scaleX, exp.y * scaleY),
                    size = Size(exp.width * scaleX, exp.height * scaleY),
                    style = Stroke(width = 1.5f)
                )
            }

            // Label: tracking ID + confidence
            val label = buildString {
                append("ID:${norm.trackingId}")
                append(" ${"%.0f".format(norm.confidence * 100)}%")
                if (norm.rejected) append(" REJECTED")
            }
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(det.x * scaleX + 4f, det.y * scaleY - 16f),
                style = TextStyle(
                    color = detColor,
                    fontSize = 10.sp
                )
            )

            // Canonical size indicator
            if (!norm.rejected) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${CardNormalizer.CANONICAL_WIDTH}×${CardNormalizer.CANONICAL_HEIGHT}",
                    topLeft = Offset(det.x * scaleX + 4f, det.y * scaleY + 2f),
                    style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp)
                )
            }
        }
    }
}
