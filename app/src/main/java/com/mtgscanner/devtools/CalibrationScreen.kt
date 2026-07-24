package com.mtgscanner.devtools

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Card Anatomy Calibration Tool — Developer Mode only.
 *
 * Displays a frozen card bitmap with draggable/resizable region overlays.
 * Each region shows its recognized OCR text in real time as it's adjusted.
 * Coordinates are persisted as normalized 0–1 percentages via CalibrationStore.
 */
@Composable
fun CalibrationScreen(
    cardBitmap: Bitmap?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val scope = rememberCoroutineScope()

    // Current frame type selection
    var selectedFrameType by remember { mutableStateOf("MODERN") }
    val frameTypes = listOf("MODERN", "LEGACY", "DFC", "ADVENTURE", "SAGA", "BATTLE", "TOKEN")

    // Load profile for selected frame type
    var profile by remember { mutableStateOf(store.loadOrDefault(selectedFrameType)) }

    // Track which region is selected for drag/resize
    var selectedRegionIdx by remember { mutableIntStateOf(-1) }

    // OCR results per region (updated when region is adjusted)
    var ocrResults by remember { mutableStateOf(mapOf<String, String>()) }

    // Trigger counter to force recomposition after drag
    var updateTrigger by remember { mutableIntStateOf(0) }

    // Re-load when frame type changes
    LaunchedEffect(selectedFrameType) {
        profile = store.loadOrDefault(selectedFrameType)
        ocrResults = emptyMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CALIBRATION", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Save button
                Button(
                    onClick = { store.save(profile) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Save", fontSize = 10.sp) }

                // Reset button
                Button(
                    onClick = {
                        store.reset(selectedFrameType)
                        profile = CalibrationProfile.defaultForType(selectedFrameType)
                        ocrResults = emptyMap()
                        updateTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Reset", fontSize = 10.sp) }

                // Close button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Close", fontSize = 10.sp) }
            }
        }

        // Frame type selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            frameTypes.forEach { type ->
                val isSelected = type == selectedFrameType
                Text(
                    text = type,
                    color = if (isSelected) Color.Cyan else Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            if (isSelected) Color.Cyan.copy(alpha = 0.1f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .then(
                            Modifier.pointerInput(type) {
                                detectDragGestures(onDrag = { _, _ -> }) // Consume to use as tap
                            }
                        )
                )
            }
        }

        // Main calibration area — card bitmap with region overlays
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (cardBitmap != null) {
                CalibrationCanvas(
                    bitmap = cardBitmap,
                    profile = profile,
                    ocrResults = ocrResults,
                    selectedIdx = selectedRegionIdx,
                    updateTrigger = updateTrigger,
                    onRegionSelected = { idx -> selectedRegionIdx = idx },
                    onRegionMoved = { idx, dxNorm, dyNorm ->
                        val region = profile.regions[idx]
                        val newLeft = (region.left + dxNorm).coerceIn(0f, 1f - region.width)
                        val newTop = (region.top + dyNorm).coerceIn(0f, 1f - region.height)
                        region.left = newLeft
                        region.top = newTop
                        region.right = newLeft + region.width
                        region.bottom = newTop + region.height
                        updateTrigger++

                        // Run OCR on the moved region
                        scope.launch {
                            val text = runOcrOnRegion(cardBitmap, region)
                            ocrResults = ocrResults + (region.name to text)
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No card bitmap captured.\nScan a card first, then open calibration.",
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
            }
        }

        // Bottom region info panel
        if (selectedRegionIdx in profile.regions.indices) {
            val region = profile.regions[selectedRegionIdx]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(region.name, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "L=${"%.3f".format(region.left)} T=${"%.3f".format(region.top)} " +
                            "R=${"%.3f".format(region.right)} B=${"%.3f".format(region.bottom)}",
                        color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    ocrResults[region.name] ?: "—",
                    color = Color.Green, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Canvas rendering the card bitmap with draggable region overlays.
 */
@Composable
private fun CalibrationCanvas(
    bitmap: Bitmap,
    profile: CalibrationProfile,
    ocrResults: Map<String, String>,
    selectedIdx: Int,
    updateTrigger: Int,
    onRegionSelected: (Int) -> Unit,
    onRegionMoved: (Int, Float, Float) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var dragRegionIdx by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(updateTrigger) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Find which region was tapped
                        val normX = offset.x / size.width
                        val normY = offset.y / size.height
                        dragRegionIdx = profile.regions.indexOfFirst { r ->
                            normX in r.left..r.right && normY in r.top..r.bottom
                        }
                        if (dragRegionIdx >= 0) onRegionSelected(dragRegionIdx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragRegionIdx >= 0) {
                            val dxNorm = dragAmount.x / size.width
                            val dyNorm = dragAmount.y / size.height
                            onRegionMoved(dragRegionIdx, dxNorm, dyNorm)
                        }
                    },
                    onDragEnd = { dragRegionIdx = -1 }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // Draw each region
        profile.regions.forEachIndexed { idx, region ->
            val color = regionColor(idx)
            val isSelected = idx == selectedIdx
            val strokeWidth = if (isSelected) 3f else 1.5f

            val left = region.left * w
            val top = region.top * h
            val rWidth = region.width * w
            val rHeight = region.height * h

            // Filled background (very subtle)
            drawRect(
                color = color.copy(alpha = if (isSelected) 0.15f else 0.05f),
                topLeft = Offset(left, top),
                size = Size(rWidth, rHeight)
            )

            // Border
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(rWidth, rHeight),
                style = Stroke(width = strokeWidth)
            )

            // Label
            val label = region.name.take(6)
            val ocrText = ocrResults[region.name]?.take(20) ?: ""
            val displayText = if (ocrText.isNotEmpty()) "$label: $ocrText" else label
            drawText(
                textMeasurer = textMeasurer,
                text = displayText,
                topLeft = Offset(left + 2f, top + 1f),
                style = TextStyle(color = color, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            )
        }
    }
}

/** Run ML Kit on a single region crop from the card bitmap. */
private suspend fun runOcrOnRegion(bitmap: Bitmap, region: CalibrationRegion): String {
    val x = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val y = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val w = (region.width * bitmap.width).toInt().coerceAtMost(bitmap.width - x).coerceAtLeast(1)
    val h = (region.height * bitmap.height).toInt().coerceAtMost(bitmap.height - y).coerceAtLeast(1)

    if (w < 5 || h < 5) return "[too small]"

    val crop = Bitmap.createBitmap(bitmap, x, y, w, h)
    val result = com.mtgscanner.anatomy.ocr.MlKitRecognizer.recognize(crop)
    return result?.text?.replace('\n', ' ')?.trim() ?: "[empty]"
}

/** Distinct colors for each region index. */
private fun regionColor(idx: Int): Color = when (idx % 8) {
    0 -> Color(0xFF4CAF50)   // Green — Name
    1 -> Color(0xFFFF9800)   // Orange — ManaCost
    2 -> Color(0xFF2196F3)   // Blue — TypeLine
    3 -> Color(0xFF607D8B)   // BlueGrey — RulesText
    4 -> Color(0xFFFFEB3B)   // Yellow — CollectorInfo
    5 -> Color(0xFFE91E63)   // Pink — SetSymbol
    6 -> Color(0xFF795548)   // Brown — Artist
    7 -> Color(0xFFF44336)   // Red — PowerToughness
    else -> Color.White
}
