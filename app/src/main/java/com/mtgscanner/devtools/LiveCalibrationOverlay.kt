package com.mtgscanner.devtools

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mtgscanner.anatomy.model.ProportionalRect
import com.mtgscanner.detection.CardRegion
import com.mtgscanner.detection.NormalizationResult

/**
 * Live calibration overlay with FULL touch interaction.
 *
 * Touch pipeline:
 * 1. This composable uses a transparent Box with pointerInput that CONSUMES all
 *    touch events when enabled — preventing PreviewView from receiving them.
 * 2. On touch down: hit-test all region rectangles in screen space.
 *    - If a region is hit: select it, determine if corner (resize) or center (drag).
 *    - If no region hit: deselect.
 * 3. On drag: update the selected region's normalized coordinates in real-time.
 * 4. On release: save the updated coordinates to the calibration store.
 *
 * Resize handles are rendered at all four corners of the selected region.
 * The handle hit radius is 24dp for easy touch targeting.
 */
@Composable
fun LiveCalibrationOverlay(
    detections: List<CardRegion>,
    lastNormalization: NormalizationResult?,
    frameWidth: Int,
    frameHeight: Int,
    regions: List<LiveRegion>,
    onRegionUpdated: (Int, ProportionalRect) -> Unit,
    ocrTexts: Map<String, String>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled || detections.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current

    // Interaction state
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    // Use the largest detection as the active card
    val activeCard = detections.maxByOrNull { it.area } ?: return
    val expandedCard = lastNormalization?.expandedRegion ?: activeCard

    // Snapshot the card position — only update when a new stable detection arrives,
    // NOT on every frame (which would cancel in-progress gestures).
    var stableCard by remember { mutableStateOf(expandedCard) }
    LaunchedEffect(expandedCard.x, expandedCard.y, expandedCard.width, expandedCard.height) {
        stableCard = expandedCard
    }

    // This Box CONSUMES all touch events when the overlay is active.
    // KEY: pointerInput key is ONLY `enabled` — NOT regions or card position.
    // This prevents the gesture handler from being cancelled on every frame update.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(enabled) {
                val handleRadius = 48f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val currentSize = Size(size.width.toFloat(), size.height.toFloat())
                    val scaleX = currentSize.width / frameWidth.toFloat()
                    val scaleY = currentSize.height / frameHeight.toFloat()
                    val card = stableCard

                    // Hit test
                    val touchResult = hitTest(
                        down.position, regions, card, scaleX, scaleY, handleRadius
                    )
                    selectedIdx = touchResult.regionIdx
                    dragMode = touchResult.mode

                    if (selectedIdx >= 0) {
                        Log.d(TAG, "Touch: region=${regions[selectedIdx].name}, mode=$dragMode " +
                            "at (${down.position.x.toInt()},${down.position.y.toInt()})")
                    } else {
                        Log.d(TAG, "Touch: no region hit at (${down.position.x.toInt()},${down.position.y.toInt()})")
                    }

                    // Drag loop
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) break

                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed && selectedIdx >= 0) {
                            change.consume()
                            val delta = change.positionChange()

                            // Convert screen delta to normalized delta using expansion-aware math
                            val expansionFraction = 0.20f
                            val cardRange = (1f + expansionFraction) / (1f + 2f * expansionFraction) -
                                expansionFraction / (1f + 2f * expansionFraction)
                            val cardScreenW = cardRange * card.width * scaleX
                            val cardScreenH = cardRange * card.height * scaleY
                            val dnX = delta.x / cardScreenW
                            val dnY = delta.y / cardScreenH

                            val r = regions[selectedIdx].rect
                            val newRect = when (dragMode) {
                                DragMode.MOVE -> ProportionalRect(
                                    top = (r.top + dnY).coerceIn(0f, 1f - r.height),
                                    bottom = (r.bottom + dnY).coerceIn(r.height, 1f),
                                    left = (r.left + dnX).coerceIn(0f, 1f - r.width),
                                    right = (r.right + dnX).coerceIn(r.width, 1f)
                                )
                                DragMode.RESIZE_TL -> ProportionalRect(
                                    top = (r.top + dnY).coerceIn(0f, r.bottom - 0.02f),
                                    bottom = r.bottom,
                                    left = (r.left + dnX).coerceIn(0f, r.right - 0.02f),
                                    right = r.right
                                )
                                DragMode.RESIZE_TR -> ProportionalRect(
                                    top = (r.top + dnY).coerceIn(0f, r.bottom - 0.02f),
                                    bottom = r.bottom,
                                    left = r.left,
                                    right = (r.right + dnX).coerceIn(r.left + 0.02f, 1f)
                                )
                                DragMode.RESIZE_BL -> ProportionalRect(
                                    top = r.top,
                                    bottom = (r.bottom + dnY).coerceIn(r.top + 0.02f, 1f),
                                    left = (r.left + dnX).coerceIn(0f, r.right - 0.02f),
                                    right = r.right
                                )
                                DragMode.RESIZE_BR -> ProportionalRect(
                                    top = r.top,
                                    bottom = (r.bottom + dnY).coerceIn(r.top + 0.02f, 1f),
                                    left = r.left,
                                    right = (r.right + dnX).coerceIn(r.left + 0.02f, 1f)
                                )
                                DragMode.NONE -> r
                            }
                            onRegionUpdated(selectedIdx, newRect)
                        }
                    }

                    // Drag ended
                    if (selectedIdx >= 0) {
                        val r = regions[selectedIdx]
                        Log.d(TAG, "Drag end: ${r.name} → L=${"%.3f".format(r.rect.left)} " +
                            "T=${"%.3f".format(r.rect.top)} R=${"%.3f".format(r.rect.right)} " +
                            "B=${"%.3f".format(r.rect.bottom)}")
                    }
                }
            }
            .drawWithContent {
                drawContent()

                val scaleX = size.width / frameWidth.toFloat()
                val scaleY = size.height / frameHeight.toFloat()
                val card = stableCard

                // Draw detected card outline
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(activeCard.x * scaleX, activeCard.y * scaleY),
                    size = Size(activeCard.width * scaleX, activeCard.height * scaleY),
                    style = Stroke(width = 1.5f)
                )

                // Draw expanded card region
                drawRect(
                    color = Color.Yellow.copy(alpha = 0.3f),
                    topLeft = Offset(card.x * scaleX, card.y * scaleY),
                    size = Size(card.width * scaleX, card.height * scaleY),
                    style = Stroke(width = 1f)
                )

                // Draw each OCR region
                regions.forEachIndexed { idx, region ->
                    val color = REGION_COLORS[idx % REGION_COLORS.size]
                    val isSelected = idx == selectedIdx
                    drawRegion(region, card, scaleX, scaleY, color, isSelected, textMeasurer, ocrTexts)
                }
            }
    )

    // Periodic coordinate logging
    LaunchedEffect(regions, expandedCard) {
        logAllCoordinates(expandedCard, regions)
    }
}

/**
 * Draw a single region with optional resize handles.
 *
 * CRITICAL: The coordinate transformation here MUST match CardAnatomyDetector.proportionalToPixel().
 * The OCR crop is computed as:
 *   absPos = cardStart + norm × (cardEnd - cardStart)   [expansion-aware]
 * where cardStart = 0.20/1.40 = 0.1429, cardEnd = 1.20/1.40 = 0.8571.
 *
 * The overlay converts this from canonical bitmap coords (488×680) to screen coords
 * via the expandedCard position in the camera frame.
 */
private fun DrawScope.drawRegion(
    region: LiveRegion,
    card: CardRegion,
    scaleX: Float,
    scaleY: Float,
    color: Color,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    ocrTexts: Map<String, String>
) {
    // Apply the SAME expansion-aware transformation as CardAnatomyDetector.proportionalToPixel()
    // Template proportions are card-relative (0–1 within the card content area).
    // The bitmap has expansion padding, so card content starts at 14.29% and ends at 85.71%.
    val expansionFraction = 0.20f
    val cardStart = expansionFraction / (1f + 2f * expansionFraction) // 0.1429
    val cardEnd = (1f + expansionFraction) / (1f + 2f * expansionFraction) // 0.8571
    val cardRange = cardEnd - cardStart // 0.7143

    // Convert normalized card-relative coords to absolute bitmap-fraction coords
    val absLeft = cardStart + region.rect.left * cardRange
    val absTop = cardStart + region.rect.top * cardRange
    val absRight = cardStart + region.rect.right * cardRange
    val absBottom = cardStart + region.rect.bottom * cardRange

    // These are fractions of the canonical bitmap (488×680).
    // The canonical bitmap corresponds to the expandedCard in frame space.
    // So: screen position = expandedCard position + (absFraction × expandedCard size), scaled to canvas.
    val left = (card.x + absLeft * card.width) * scaleX
    val top = (card.y + absTop * card.height) * scaleY
    val width = (absRight - absLeft) * card.width * scaleX
    val height = (absBottom - absTop) * card.height * scaleY

    // Fill
    drawRect(
        color = color.copy(alpha = if (isSelected) 0.2f else 0.05f),
        topLeft = Offset(left, top),
        size = Size(width, height)
    )

    // Border
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = if (isSelected) 3f else 1.5f)
    )

    // Resize handles (on selected region)
    if (isSelected) {
        val handleSize = 12f
        listOf(
            Offset(left, top),                              // TL
            Offset(left + width - handleSize, top),         // TR
            Offset(left, top + height - handleSize),        // BL
            Offset(left + width - handleSize, top + height - handleSize) // BR
        ).forEach { pos ->
            drawRect(
                color = Color.White,
                topLeft = pos,
                size = Size(handleSize, handleSize)
            )
            drawRect(
                color = color,
                topLeft = pos,
                size = Size(handleSize, handleSize),
                style = Stroke(width = 2f)
            )
        }
    }

    // Label
    val ocrText = ocrTexts[region.name]?.take(12) ?: ""
    val label = if (ocrText.isNotEmpty()) "${region.name}: $ocrText" else region.name
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(left + 3f, top + 2f),
        style = TextStyle(
            color = color,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    )
}

/**
 * Hit test: determine which region and which interaction mode (move vs resize corner).
 * Uses the SAME expansion-aware coordinate math as the rendering.
 */
private fun hitTest(
    position: Offset,
    regions: List<LiveRegion>,
    card: CardRegion,
    scaleX: Float,
    scaleY: Float,
    handleRadius: Float
): HitResult {
    val expansionFraction = 0.20f
    val cardStart = expansionFraction / (1f + 2f * expansionFraction)
    val cardEnd = (1f + expansionFraction) / (1f + 2f * expansionFraction)
    val cardRange = cardEnd - cardStart

    for (idx in regions.indices.reversed()) {
        val r = regions[idx].rect
        val absLeft = cardStart + r.left * cardRange
        val absTop = cardStart + r.top * cardRange
        val absRight = cardStart + r.right * cardRange
        val absBottom = cardStart + r.bottom * cardRange

        val left = (card.x + absLeft * card.width) * scaleX
        val top = (card.y + absTop * card.height) * scaleY
        val right = (card.x + absRight * card.width) * scaleX
        val bottom = (card.y + absBottom * card.height) * scaleY

        // Check resize corners first
        if (position.distTo(Offset(left, top)) < handleRadius)
            return HitResult(idx, DragMode.RESIZE_TL)
        if (position.distTo(Offset(right, top)) < handleRadius)
            return HitResult(idx, DragMode.RESIZE_TR)
        if (position.distTo(Offset(left, bottom)) < handleRadius)
            return HitResult(idx, DragMode.RESIZE_BL)
        if (position.distTo(Offset(right, bottom)) < handleRadius)
            return HitResult(idx, DragMode.RESIZE_BR)

        // Check body (move)
        if (position.x in left..right && position.y in top..bottom)
            return HitResult(idx, DragMode.MOVE)
    }
    return HitResult(-1, DragMode.NONE)
}

private fun Offset.distTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun logAllCoordinates(card: CardRegion, regions: List<LiveRegion>) {
    Log.d(TAG, "╔══ LIVE REGIONS (card: ${card.x},${card.y} ${card.width}x${card.height}) ══")
    regions.forEach { r ->
        Log.d(TAG, "║ ${r.name}: [${r.rect.left},${r.rect.top}→${r.rect.right},${r.rect.bottom}]")
    }
    Log.d(TAG, "╚══════════════════════════════════════════════════════")
}

data class LiveRegion(
    val name: String,
    val rect: ProportionalRect
)

private data class HitResult(val regionIdx: Int, val mode: DragMode)

private enum class DragMode { NONE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }

private const val TAG = "LiveCalibration"

private val REGION_COLORS = listOf(
    Color(0xFF4CAF50),   // Green — Name
    Color(0xFFFF9800),   // Orange — ManaCost
    Color(0xFF2196F3),   // Blue — TypeLine
    Color(0xFF607D8B),   // BlueGrey — RulesText
    Color(0xFFFFEB3B),   // Yellow — CollectorInfo
    Color(0xFFE91E63),   // Pink — SetSymbol
    Color(0xFF795548),   // Brown — Artist
    Color(0xFFF44336)    // Red — PowerToughness
)
