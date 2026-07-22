package com.mtgscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mtgscanner.anatomy.CardAnatomyEngine
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.detection.DetectionPipeline

/**
 * CameraScreen: Live camera preview with real-time card detection feedback
 * and optional anatomy debug overlay.
 *
 * This composable is responsible for:
 * 1. Binding the CameraX preview to the UI
 * 2. Connecting frame delivery to [DetectionPipeline.processFrame]
 * 3. Displaying detection count feedback to the user
 * 4. Rendering CardLayout debug overlay when enabled (developer option)
 *
 * It does NOT own the [DetectionPipeline.onCardReady] callback.
 * That callback is owned exclusively by [MainActivity.setupDetectionPipelineCallback].
 *
 * @param cameraPreviewManager Manages CameraX lifecycle and frame delivery.
 * @param detectionPipeline Orchestrates detection, tracking, and OCR preparation.
 * @param cardAnatomyEngine Optional anatomy engine for debug overlay data.
 * @param showAnatomyOverlay Whether to show the debug overlay (developer toggle).
 * @param onToggleOverlay Callback to toggle the overlay state.
 * @param modifier Compose modifier for the root layout.
 */
@Composable
fun CameraScreen(
    cameraPreviewManager: CameraPreviewManager,
    detectionPipeline: DetectionPipeline,
    cardAnatomyEngine: CardAnatomyEngine? = null,
    showAnatomyOverlay: Boolean = false,
    onToggleOverlay: () -> Unit = {},
    pipelineStatus: PipelineStatus = PipelineStatus(),
    modifier: Modifier = Modifier
) {
    // Detection count updated from onFrameAnalysis — NOT from onCardReady
    var detectionCount by remember { mutableStateOf(0) }

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

        // ─── Detection Debug Overlay ───
        if (showAnatomyOverlay) {
            DetectionDebugOverlay(
                detections = detectionPipeline.lastDetections,
                lastNormalization = detectionPipeline.normalizer.lastResult,
                frameWidth = 2992,  // Will be overwritten on first frame; default for S23
                frameHeight = 2992,
                showOverlay = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ─── Anatomy Debug Overlay ───
        if (showAnatomyOverlay && cardAnatomyEngine != null) {
            val layout = cardAnatomyEngine.lastCardLayout
            AnatomyDebugOverlay(
                cardLayout = layout,
                showOverlay = true,
                cardBitmapWidth = cardAnatomyEngine.lastBitmapWidth,
                cardBitmapHeight = cardAnatomyEngine.lastBitmapHeight,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ─── Pipeline Status Panel ───
        PipelineStatusOverlay(
            state = pipelineStatus,
            showOverlay = showAnatomyOverlay,
            modifier = Modifier.align(Alignment.TopEnd)
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

            // Developer toggle for anatomy overlay
            Text(
                text = if (showAnatomyOverlay) "🔍 Anatomy Overlay: ON" else "🔍 Anatomy Overlay: OFF",
                color = if (showAnatomyOverlay) Color.Green else Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { onToggleOverlay() }
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
