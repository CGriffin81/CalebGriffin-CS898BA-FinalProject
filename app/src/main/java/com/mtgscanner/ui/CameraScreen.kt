package com.mtgscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.detection.DetectionPipeline

/**
 * CameraScreen: Live camera preview with real-time card detection feedback.
 *
 * This composable is ONLY responsible for:
 * 1. Binding the CameraX preview to the UI
 * 2. Connecting frame delivery to [DetectionPipeline.processFrame]
 * 3. Displaying detection count feedback to the user
 *
 * It does NOT own the [DetectionPipeline.onCardReady] callback.
 * That callback is owned exclusively by [MainActivity.setupDetectionPipelineCallback],
 * which handles: OCR → Scryfall → FuzzyMatch → Navigate to Verification.
 *
 * Previous bug: This composable's LaunchedEffect overwrote onCardReady after
 * MainActivity set it, causing OCR to never activate.
 *
 * @param cameraPreviewManager Manages CameraX lifecycle and frame delivery.
 * @param detectionPipeline Orchestrates detection, tracking, and OCR preparation.
 * @param modifier Compose modifier for the root layout.
 */
@Composable
fun CameraScreen(
    cameraPreviewManager: CameraPreviewManager,
    detectionPipeline: DetectionPipeline,
    modifier: Modifier = Modifier
) {
    // Detection count updated from onFrameAnalysis — NOT from onCardReady
    var detectionCount by remember { mutableStateOf(0) }

    // Wire the frame analysis callback to update the UI detection counter.
    // This does NOT touch onCardReady — that is owned by MainActivity.
    LaunchedEffect(Unit) {
        detectionPipeline.onFrameAnalysis = { count ->
            detectionCount = count
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Preview — binds preview + frame analysis via AndroidView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    cameraPreviewManager.setupCamera(
                        previewView = this,
                        onFrameReady = detectionPipeline::processFrame
                    )
                }
            }
        )

        // Top status bar with detection count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MTG Scanner",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (detectionCount > 0) "Cards in view: $detectionCount" else "Scanning...",
                color = if (detectionCount > 0) Color.Cyan else Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom instruction panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Hold card steady for recognition",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            if (detectionCount > 0) {
                Text(
                    text = "Detecting $detectionCount card region(s)...",
                    color = Color.Cyan,
                    fontSize = 11.sp
                )
            }
        }
    }
}
