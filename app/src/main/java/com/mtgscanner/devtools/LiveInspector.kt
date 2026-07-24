package com.mtgscanner.devtools

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.anatomy.model.ProportionalRect
import com.mtgscanner.detection.CardRegion
import com.mtgscanner.detection.NormalizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live Inspector — draggable OCR regions directly on the live camera image.
 *
 * Purpose: "Does this box actually cover the thing I think it covers?"
 *
 * Behavior:
 * - Camera stays visible
 * - Detected card stays visible
 * - Every region is draggable
 * - When you drag: rectangle moves immediately, OCR reruns immediately, text updates immediately
 * - No save dialog, no separate screen, no template view
 *
 * This is purely a debugging tool.
 */
@Composable
fun LiveInspector(
    cardBitmap: Bitmap?,
    expandedCard: CardRegion?,
    frameWidth: Int,
    frameHeight: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled || expandedCard == null) return

    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    // Mutable region definitions — these move when you drag
    var regions by remember {
        mutableStateOf(listOf(
            InspectorRegion("Name",      ProportionalRect(0.0f, 0.12f, 0.03f, 0.82f), ""),
            InspectorRegion("Type",      ProportionalRect(0.49f, 0.57f, 0.03f, 0.83f), ""),
            InspectorRegion("Collector", ProportionalRect(0.90f, 1.0f, 0.03f, 0.55f), ""),
            InspectorRegion("P/T",       ProportionalRect(0.86f, 0.93f, 0.70f, 0.95f), "")
        ))
    }

    var selectedIdx by remember { mutableIntStateOf(-1) }

    // Stable reference for touch handler
    var stableCard by remember { mutableStateOf(expandedCard) }
    LaunchedEffect(expandedCard) { stableCard = expandedCard }

    // Expansion-aware constants (must match CardAnatomyDetector)
    val expFrac = 0.20f
    val cardStart = expFrac / (1f + 2f * expFrac) // 0.1429
    val cardRange = (1f + expFrac) / (1f + 2f * expFrac) - cardStart // 0.7143

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val scaleX = size.width.toFloat() / frameWidth
                    val scaleY = size.height.toFloat() / frameHeight
                    val card = stableCard

                    // Hit test
                    selectedIdx = regions.indexOfLast { r ->
                        val absL = cardStart + r.rect.left * cardRange
                        val absT = cardStart + r.rect.top * cardRange
                        val absR = cardStart + r.rect.right * cardRange
                        val absB = cardStart + r.rect.bottom * cardRange
                        val sL = (card.x + absL * card.width) * scaleX
                        val sT = (card.y + absT * card.height) * scaleY
                        val sR = (card.x + absR * card.width) * scaleX
                        val sB = (card.y + absB * card.height) * scaleY
                        down.position.x in sL..sR && down.position.y in sT..sB
                    }

                    // Drag loop
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) break
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed || selectedIdx < 0) continue
                        change.consume()

                        val delta = change.positionChange()
                        val scX = size.width.toFloat() / frameWidth
                        val scY = size.height.toFloat() / frameHeight
                        val cardW = cardRange * stableCard.width * scX
                        val cardH = cardRange * stableCard.height * scY
                        val dnX = delta.x / cardW
                        val dnY = delta.y / cardH

                        val r = regions[selectedIdx].rect
                        val newRect = ProportionalRect(
                            top = (r.top + dnY).coerceIn(0f, 1f - r.height),
                            bottom = (r.bottom + dnY).coerceIn(r.height, 1f),
                            left = (r.left + dnX).coerceIn(0f, 1f - r.width),
                            right = (r.right + dnX).coerceIn(r.width, 1f)
                        )
                        regions = regions.toMutableList().apply {
                            this[selectedIdx] = this[selectedIdx].copy(rect = newRect)
                        }
                    }

                    // Drag ended — rerun OCR on moved region
                    if (selectedIdx >= 0 && cardBitmap != null) {
                        val idx = selectedIdx
                        scope.launch {
                            val r = regions[idx]
                            val text = cropAndOcr(cardBitmap, r.rect, cardStart, cardRange)
                            regions = regions.toMutableList().apply {
                                this[idx] = this[idx].copy(ocrText = text)
                            }
                        }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val scaleX = size.width / frameWidth.toFloat()
                val scaleY = size.height / frameHeight.toFloat()
                val card = stableCard

                regions.forEachIndexed { idx, r ->
                    val absL = cardStart + r.rect.left * cardRange
                    val absT = cardStart + r.rect.top * cardRange
                    val absR = cardStart + r.rect.right * cardRange
                    val absB = cardStart + r.rect.bottom * cardRange

                    val sL = (card.x + absL * card.width) * scaleX
                    val sT = (card.y + absT * card.height) * scaleY
                    val sW = (absR - absL) * card.width * scaleX
                    val sH = (absB - absT) * card.height * scaleY

                    val color = COLORS[idx % COLORS.size]
                    val isSelected = idx == selectedIdx

                    // Rectangle
                    drawRect(color.copy(alpha = if (isSelected) 0.2f else 0.05f),
                        Offset(sL, sT), Size(sW, sH))
                    drawRect(color, Offset(sL, sT), Size(sW, sH),
                        style = Stroke(if (isSelected) 3f else 1.5f))

                    // Name + OCR text
                    val label = if (r.ocrText.isNotEmpty()) "${r.name}: ${r.ocrText.take(20)}"
                                else r.name
                    drawText(textMeasurer, label, Offset(sL + 3f, sT + 2f),
                        TextStyle(color = color, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                }
            }
    )

    // Run initial OCR for all regions on first render
    LaunchedEffect(cardBitmap) {
        if (cardBitmap != null) {
            regions = regions.map { r ->
                val text = cropAndOcr(cardBitmap, r.rect, cardStart, cardRange)
                r.copy(ocrText = text)
            }
        }
    }
}

/** Crop a region from the canonical bitmap and run ML Kit. */
private suspend fun cropAndOcr(
    bitmap: Bitmap,
    rect: ProportionalRect,
    cardStart: Float,
    cardRange: Float
): String = withContext(Dispatchers.Default) {
    val w = bitmap.width
    val h = bitmap.height

    val absL = cardStart + rect.left * cardRange
    val absT = cardStart + rect.top * cardRange
    val absR = cardStart + rect.right * cardRange
    val absB = cardStart + rect.bottom * cardRange

    val px = (absL * w).toInt().coerceIn(0, w - 1)
    val py = (absT * h).toInt().coerceIn(0, h - 1)
    val pw = ((absR - absL) * w).toInt().coerceAtMost(w - px).coerceAtLeast(1)
    val ph = ((absB - absT) * h).toInt().coerceAtMost(h - py).coerceAtLeast(1)

    if (pw < 5 || ph < 5) return@withContext "[TOO SMALL ${pw}×${ph}]"

    val crop = Bitmap.createBitmap(bitmap, px, py, pw, ph)
    val result = com.mtgscanner.anatomy.ocr.MlKitRecognizer.recognize(crop)
    val text = result?.text?.trim()?.replace('\n', ' ') ?: ""

    if (text.isEmpty()) "[NO TEXT ${pw}×${ph}]" else text
}

private data class InspectorRegion(
    val name: String,
    val rect: ProportionalRect,
    val ocrText: String
)

private val COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3),
    Color(0xFFFFEB3B), Color(0xFFF44336)
)
