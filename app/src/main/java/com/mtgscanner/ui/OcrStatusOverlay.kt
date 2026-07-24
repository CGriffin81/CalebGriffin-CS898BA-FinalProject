package com.mtgscanner.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.anatomy.model.CardLayout
import com.mtgscanner.anatomy.model.RegionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR Status Overlay — inspectable OCR results for every region.
 *
 * For each OCR region displays:
 * - Cropped bitmap (the exact pixels ML Kit receives)
 * - Region name
 * - OCR text result (or EMPTY / NO TEXT status)
 * - Confidence
 *
 * Never hides failures. Every region's status is visible at all times.
 */
@Composable
fun OcrStatusOverlay(
    cardBitmap: Bitmap?,
    cardLayout: CardLayout?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled || cardBitmap == null || cardLayout == null) return

    val scope = rememberCoroutineScope()

    // OCR results per region (computed on first render and when bitmap changes)
    var regionResults by remember { mutableStateOf<List<RegionOcrStatus>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Run OCR on all regions when bitmap changes
    LaunchedEffect(cardBitmap, cardLayout) {
        isProcessing = true
        regionResults = inspectAllRegions(cardBitmap, cardLayout)
        isProcessing = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Text(
            "OCR STATUS (${regionResults.size} regions)",
            color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
        )

        if (isProcessing) {
            Text("Processing...", color = Color.Yellow, fontSize = 9.sp)
        }

        // Each region's status
        regionResults.forEach { status ->
            RegionStatusRow(status)
        }
    }
}

@Composable
private fun RegionStatusRow(status: RegionOcrStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Cropped bitmap thumbnail
        if (status.crop != null) {
            Image(
                bitmap = status.crop.asImageBitmap(),
                contentDescription = status.name,
                modifier = Modifier
                    .width(60.dp)
                    .height(24.dp)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(24.dp)
                    .background(Color.Red.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text("EMPTY", color = Color.Red, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Status text
        Column(modifier = Modifier.weight(1f)) {
            // Region name + status color
            val statusColor = when (status.status) {
                OcrRegionState.SUCCESS -> Color.Green
                OcrRegionState.NO_TEXT -> Color.Red
                OcrRegionState.EMPTY_CROP -> Color.Red
                OcrRegionState.TOO_SMALL -> Color(0xFFFF6600)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(status.name, color = Color.Cyan, fontSize = 8.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(status.status.label, color = statusColor, fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace)
            }

            // OCR text (or failure message)
            Text(
                text = status.displayText,
                color = statusColor,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2
            )
        }
    }
}

/**
 * Run ML Kit on every anatomy region and collect results.
 * Never returns silently — every region produces a status.
 */
private suspend fun inspectAllRegions(
    bitmap: Bitmap,
    layout: CardLayout
): List<RegionOcrStatus> = withContext(Dispatchers.Default) {
    val regions = listOf(
        RegionType.NAME_BAR to "Name",
        RegionType.MANA_COST to "Mana",
        RegionType.TYPE_LINE to "Type",
        RegionType.RULES_TEXT to "Rules",
        RegionType.COLLECTOR_INFO to "Collector",
        RegionType.SET_SYMBOL to "SetSym",
        RegionType.ARTIST_CREDIT to "Artist",
        RegionType.POWER_TOUGHNESS to "P/T"
    )

    regions.map { (type, name) ->
        val region = layout.findRegion(type)
        if (region == null) {
            RegionOcrStatus(name, null, OcrRegionState.EMPTY_CROP, "Region not detected", 0f)
        } else {
            val bounds = region.bounds
            val safeLeft = bounds.left.coerceIn(0, bitmap.width - 1)
            val safeTop = bounds.top.coerceIn(0, bitmap.height - 1)
            val safeW = bounds.width().coerceAtMost(bitmap.width - safeLeft).coerceAtLeast(1)
            val safeH = bounds.height().coerceAtMost(bitmap.height - safeTop).coerceAtLeast(1)

            if (safeW < 5 || safeH < 5) {
                RegionOcrStatus(name, null, OcrRegionState.TOO_SMALL,
                    "Crop ${safeW}×${safeH} too small", 0f)
            } else {
                val crop = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeW, safeH)
                val ocrResult = com.mtgscanner.anatomy.ocr.MlKitRecognizer.recognize(crop)
                val text = ocrResult?.text?.trim() ?: ""

                if (text.isEmpty()) {
                    RegionOcrStatus(name, crop, OcrRegionState.NO_TEXT,
                        "NO TEXT (${safeW}×${safeH}px)", 0f)
                } else {
                    RegionOcrStatus(name, crop, OcrRegionState.SUCCESS,
                        text.replace('\n', ' ').take(50), region.confidence)
                }
            }
        }
    }
}

private data class RegionOcrStatus(
    val name: String,
    val crop: Bitmap?,
    val status: OcrRegionState,
    val displayText: String,
    val confidence: Float
)

private enum class OcrRegionState(val label: String) {
    SUCCESS("OK"),
    NO_TEXT("NO TEXT"),
    EMPTY_CROP("EMPTY"),
    TOO_SMALL("SMALL")
}
