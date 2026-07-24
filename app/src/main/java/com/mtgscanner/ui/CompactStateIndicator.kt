package com.mtgscanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact state indicator — shows pipeline stage as a single colored word.
 *
 * Default state: a small pill showing the current stage name with color.
 * Tapping it expands a brief diagnostic panel. Tapping again collapses it.
 *
 * Stages: SEARCHING → TRACKING → STABLE → OCR → MATCHING → VERIFY
 *
 * Does NOT obstruct the camera view by default.
 */
@Composable
fun CompactStateIndicator(
    stage: PipelineStage,
    detectionCount: Int,
    trackingId: Int,
    recognizedName: String,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val stageColor by animateColorAsState(
        targetValue = stage.color,
        label = "stageColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // Compact pill — always visible, minimal footprint
        Text(
            text = stage.displayName.uppercase(),
            color = stageColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )

        // Expanded diagnostic panel (only when tapped)
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DiagRow("Cards", "$detectionCount")
                DiagRow("Track", if (trackingId >= 0) "#$trackingId" else "—")
                DiagRow("Name", recognizedName.take(16).ifEmpty { "—" })
                DiagRow("Conf", if (confidence > 0f) "${"%.0f".format(confidence * 100)}%" else "—")
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 8.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
