package com.mtgscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Developer debug overlay showing pipeline state in real time.
 *
 * Displays a semi-transparent panel in the top-right corner with:
 * - Current pipeline stage (Detection, Anatomy, OCR, Scryfall, Verification)
 * - Detection count and stability status
 * - Recognized card name from OCR
 * - OCR confidence score
 * - Scryfall lookup state (idle, searching, found, failed)
 * - Tracking ID of the active card
 *
 * Only rendered when [showOverlay] is true (developer toggle).
 *
 * @param state Current pipeline status data
 * @param showOverlay Whether to render
 */
@Composable
fun PipelineStatusOverlay(
    state: PipelineStatus,
    showOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    if (!showOverlay) return

    Column(
        modifier = modifier
            .padding(top = 56.dp, end = 8.dp)
            .width(180.dp)
            .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Header
        Text(
            text = "PIPELINE STATUS",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Pipeline stage
        StatusRow("Stage", state.stage.displayName, state.stage.color)

        // Detection info
        StatusRow("Detect", "${state.detectionCount} cards", Color.Cyan)
        StatusRow("Stable", if (state.isStable) "YES" else "no", if (state.isStable) Color.Green else Color.Gray)
        StatusRow("TrackID", if (state.trackingId >= 0) "#${state.trackingId}" else "—", Color.White)

        // OCR results
        if (state.stage.ordinal >= PipelineStage.OCR.ordinal) {
            Spacer(modifier = Modifier.height(2.dp))
            StatusRow("Name", state.recognizedName.take(15).ifEmpty { "—" },
                if (state.recognizedName.isNotEmpty()) Color.Green else Color.Gray)
            StatusRow("Conf", if (state.ocrConfidence > 0f) "${"%.0f".format(state.ocrConfidence * 100)}%" else "—",
                confidenceColor(state.ocrConfidence))
        }

        // Scryfall state
        if (state.stage.ordinal >= PipelineStage.SCRYFALL.ordinal) {
            Spacer(modifier = Modifier.height(2.dp))
            StatusRow("Scryfall", state.scryfallState, scryfallColor(state.scryfallState))
            if (state.candidateCount > 0) {
                StatusRow("Matches", "${state.candidateCount}", Color.Cyan)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun confidenceColor(confidence: Float): Color = when {
    confidence >= 0.7f -> Color.Green
    confidence >= 0.4f -> Color.Yellow
    confidence > 0f -> Color(0xFFFF6600)
    else -> Color.Gray
}

private fun scryfallColor(state: String): Color = when (state) {
    "found" -> Color.Green
    "searching" -> Color.Yellow
    "cache" -> Color.Cyan
    "failed" -> Color.Red
    "offline" -> Color(0xFFFF6600)
    else -> Color.Gray
}

/**
 * Current pipeline processing state — updated by MainActivity as the pipeline progresses.
 */
data class PipelineStatus(
    val stage: PipelineStage = PipelineStage.IDLE,
    val detectionCount: Int = 0,
    val isStable: Boolean = false,
    val trackingId: Int = -1,
    val recognizedName: String = "",
    val ocrConfidence: Float = 0f,
    val scryfallState: String = "idle",
    val candidateCount: Int = 0
)

/**
 * Pipeline processing stages in order.
 */
enum class PipelineStage(val displayName: String, val color: Color) {
    IDLE("Idle", Color.Gray),
    DETECTING("Detecting", Color.Cyan),
    STABLE("Stable", Color.Blue),
    ANATOMY("Anatomy", Color(0xFF9C27B0)),
    OCR("OCR", Color(0xFFFF9800)),
    SCRYFALL("Scryfall", Color(0xFF2196F3)),
    MATCHING("Matching", Color(0xFF4CAF50)),
    VERIFICATION("Verify", Color.Green),
    ERROR("Error", Color.Red)
}
