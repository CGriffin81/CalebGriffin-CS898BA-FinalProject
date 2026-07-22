package com.mtgscanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.RegionType

/**
 * Debug overlay that renders CardLayout region rectangles over the camera preview.
 *
 * Each region is drawn with a unique color and a text label showing:
 * - Region type name
 * - Confidence percentage
 *
 * The overlay scales CardLayout coordinates (relative to the card bitmap)
 * to the current composable size. This assumes the overlay fills the same
 * area as the camera preview.
 *
 * Toggle this overlay via [showOverlay] parameter — controlled by a developer option.
 *
 * @param cardLayout The detected card anatomy to visualize (null = no overlay)
 * @param showOverlay Whether to render the overlay (developer toggle)
 * @param cardBitmapWidth Width of the card bitmap the layout was detected on
 * @param cardBitmapHeight Height of the card bitmap the layout was detected on
 * @param modifier Compose modifier
 */
@Composable
fun AnatomyDebugOverlay(
    cardLayout: CardLayout?,
    showOverlay: Boolean,
    cardBitmapWidth: Int,
    cardBitmapHeight: Int,
    modifier: Modifier = Modifier
) {
    if (!showOverlay || cardLayout == null) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        // Scale factors from card bitmap coordinates to canvas coordinates
        val scaleX = canvasW / cardBitmapWidth.toFloat().coerceAtLeast(1f)
        val scaleY = canvasH / cardBitmapHeight.toFloat().coerceAtLeast(1f)

        for (region in cardLayout.regions) {
            val color = regionColor(region.regionType)
            val bounds = region.bounds

            // Scale to canvas coordinates
            val left = bounds.left * scaleX
            val top = bounds.top * scaleY
            val width = bounds.width() * scaleX
            val height = bounds.height() * scaleY

            // Draw rectangle outline
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 2f)
            )

            // Draw semi-transparent fill
            drawRect(
                color = color.copy(alpha = 0.1f),
                topLeft = Offset(left, top),
                size = Size(width, height)
            )

            // Draw label
            val label = "${regionLabel(region.regionType)} ${(region.confidence * 100).toInt()}%"
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(left + 4f, top + 2f),
                style = TextStyle(
                    color = color,
                    fontSize = 9.sp
                )
            )
        }
    }
}

/** Map each region type to a distinct color for visual differentiation. */
private fun regionColor(type: RegionType): Color = when (type) {
    RegionType.NAME_BAR -> Color(0xFF4CAF50)         // Green
    RegionType.MANA_COST -> Color(0xFFFF9800)        // Orange
    RegionType.ARTWORK -> Color(0xFF9C27B0)          // Purple
    RegionType.TYPE_LINE -> Color(0xFF2196F3)        // Blue
    RegionType.RULES_TEXT -> Color(0xFF607D8B)       // Blue Grey
    RegionType.SET_SYMBOL -> Color(0xFFE91E63)       // Pink
    RegionType.COLLECTOR_INFO -> Color(0xFFFFEB3B)   // Yellow
    RegionType.ARTIST_CREDIT -> Color(0xFF795548)    // Brown
    RegionType.POWER_TOUGHNESS -> Color(0xFFF44336)  // Red
}

/** Short display label for each region type. */
private fun regionLabel(type: RegionType): String = when (type) {
    RegionType.NAME_BAR -> "NAME"
    RegionType.MANA_COST -> "MANA"
    RegionType.ARTWORK -> "ART"
    RegionType.TYPE_LINE -> "TYPE"
    RegionType.RULES_TEXT -> "RULES"
    RegionType.SET_SYMBOL -> "SET"
    RegionType.COLLECTOR_INFO -> "COLL"
    RegionType.ARTIST_CREDIT -> "ARTIST"
    RegionType.POWER_TOUGHNESS -> "P/T"
}
