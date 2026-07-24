package com.mtgscanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mtgscanner.detection.DetectionCandidate
import com.mtgscanner.detection.NormalizationResult

/**
 * Explanatory Overlay — shows EVERY detection candidate with its accept/reject reason.
 *
 * Nothing silently disappears. Every decision the pipeline makes is visible:
 * - Green solid box + "Accepted: Tracking ID N" for accepted cards
 * - Red dashed box + reason text for rejected candidates
 * - Orange box + "Normalizer rejected: reason" for normalization failures
 *
 * Each candidate shows its metrics (aspect ratio, area%, fill%) so developers
 * can immediately see WHY a detection was accepted or rejected.
 */
@Composable
fun ExplanatoryOverlay(
    candidates: List<DetectionCandidate>,
    lastNormalization: NormalizationResult?,
    frameWidth: Int,
    frameHeight: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / frameWidth.toFloat()
        val scaleY = size.height / frameHeight.toFloat()

        // Draw ALL candidates — accepted and rejected
        for (candidate in candidates) {
            val region = candidate.region
            val left = region.x * scaleX
            val top = region.y * scaleY
            val width = region.width * scaleX
            val height = region.height * scaleY

            if (candidate.accepted) {
                // ─── ACCEPTED: solid green outline ───
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 3f)
                )
                drawRect(
                    color = Color.Green.copy(alpha = 0.08f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                // Label
                drawText(
                    textMeasurer = textMeasurer,
                    text = "✓ ${candidate.reason}",
                    topLeft = Offset(left + 4f, top + 2f),
                    style = TextStyle(
                        color = Color.Green,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
                // Metrics
                drawText(
                    textMeasurer = textMeasurer,
                    text = "AR:${"%.2f".format(candidate.aspectRatio)} " +
                        "Fill:${"%.0f".format(candidate.fillRatio * 100)}% " +
                        "Area:${"%.1f".format(candidate.areaFraction * 100)}%",
                    topLeft = Offset(left + 4f, top + height - 14f),
                    style = TextStyle(
                        color = Color.Green.copy(alpha = 0.7f),
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            } else {
                // ─── REJECTED: dashed red outline ───
                drawRect(
                    color = Color.Red.copy(alpha = 0.6f),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )
                // Rejection reason
                drawText(
                    textMeasurer = textMeasurer,
                    text = "✗ ${candidate.reason}",
                    topLeft = Offset(left + 4f, top + 2f),
                    style = TextStyle(
                        color = Color.Red,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }

        // Draw normalizer rejection if present
        lastNormalization?.let { norm ->
            if (norm.rejected) {
                val region = norm.detectedRegion
                val left = region.x * scaleX
                val top = region.y * scaleY
                val width = region.width * scaleX
                val height = region.height * scaleY

                drawRect(
                    color = Color(0xFFFF6600),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "⚠ Normalizer: ${norm.rejectReason}",
                    topLeft = Offset(left + 4f, top + height - 14f),
                    style = TextStyle(
                        color = Color(0xFFFF6600),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
