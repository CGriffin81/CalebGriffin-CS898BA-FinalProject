package com.mtgscanner.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Compact developer settings panel rendered at the bottom of the camera screen.
 * Shows independent toggles for each overlay layer.
 * Tapping a toggle enables/disables that specific overlay.
 */
@Composable
fun DevSettingsPanel(
    settings: DevSettings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DEV OVERLAYS", color = Color.White, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ALL", color = Color.Cyan, fontSize = 9.sp,
                    modifier = Modifier.clickable { settings.enableAll() })
                Text("NONE", color = Color.Gray, fontSize = 9.sp,
                    modifier = Modifier.clickable { settings.disableAll() })
                Text("✕", color = Color.Red, fontSize = 12.sp,
                    modifier = Modifier.clickable { onDismiss() })
            }
        }

        // Toggle grid (2 columns)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleRow("Detection", settings.showCardDetection) { settings.toggleCardDetection() }
                ToggleRow("OCR Regions", settings.showOcrRegions) { settings.toggleOcrRegions() }
                ToggleRow("Tracking IDs", settings.showTrackingIds) { settings.toggleTrackingIds() }
                ToggleRow("OCR Text", settings.showOcrText) { settings.toggleOcrText() }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleRow("Pipeline", settings.showPipelineStatus) { settings.togglePipelineStatus() }
                ToggleRow("Labels", settings.showRegionLabels) { settings.toggleRegionLabels() }
                ToggleRow("Timing", settings.showPerformance) { settings.togglePerformance() }
                ToggleRow("Scryfall", settings.showScryfallLookup) { settings.toggleScryfallLookup() }
                ToggleRow("📸 Capture", settings.debugCaptureEnabled) { settings.toggleDebugCapture() }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (enabled) Color.Green.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(
            if (enabled) "●" else "○",
            color = if (enabled) Color.Green else Color.Gray,
            fontSize = 10.sp
        )
    }
}
