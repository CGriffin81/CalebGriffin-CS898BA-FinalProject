package com.mtgscanner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.mtgscanner.anatomy.CardAnatomyEngine
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.devtools.DevSettings
import com.mtgscanner.devtools.DevSettingsPanel

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
    devSettings: DevSettings? = null,
    modifier: Modifier = Modifier
) {
    // Detection count updated from onFrameAnalysis — NOT from onCardReady
    var detectionCount by remember { mutableStateOf(0) }

    // Developer settings panel visibility
    var showDevPanel by remember { mutableStateOf(false) }

    // Freeze frame state — pauses pipeline and shows the exact bitmap sent to OCR
    var isFrozen by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        detectionPipeline.onFrameAnalysis = { count ->
            if (!isFrozen) detectionCount = count
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Either live camera or frozen frame
        if (isFrozen && frozenBitmap != null) {
            // ─── FROZEN FRAME: Display the exact bitmap sent to OCR ───
            val bmp = frozenBitmap!!
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Frozen card frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // ─── LIVE CAMERA: CameraX Preview with frame analysis ───
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        cameraPreviewManager.setupCamera(
                            previewView = this,
                            onFrameReady = { bitmap ->
                                if (!isFrozen) {
                                    detectionPipeline.processFrame(bitmap)
                                }
                            }
                        )
                    }
                }
            )
        }

        // ─── Detection Debug Overlay (independent toggle) ───
        if (devSettings?.showCardDetection == true || (devSettings == null && showAnatomyOverlay)) {
            // Explanatory overlay: shows ALL candidates with accept/reject reasons
            ExplanatoryOverlay(
                candidates = detectionPipeline.lastCandidates,
                lastNormalization = detectionPipeline.normalizer.lastResult,
                frameWidth = 2992,
                frameHeight = 2992,
                enabled = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ─── Live Calibration / OCR Regions Overlay (independent toggle) ───
        if (devSettings?.showOcrRegions == true || (devSettings == null && showAnatomyOverlay)) {
            // Live Inspector: draggable regions with immediate OCR feedback
            com.mtgscanner.devtools.LiveInspector(
                cardBitmap = detectionPipeline.normalizer.lastResult?.bitmap,
                expandedCard = detectionPipeline.normalizer.lastResult?.expandedRegion,
                frameWidth = 2992,
                frameHeight = 2992,
                enabled = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ─── Anatomy Debug Overlay / Region Labels (independent toggle) ───
        if (devSettings?.showRegionLabels == true || (devSettings == null && showAnatomyOverlay)) {
            if (cardAnatomyEngine != null) {
                val layout = cardAnatomyEngine.lastCardLayout
                AnatomyDebugOverlay(
                    cardLayout = layout,
                    showOverlay = true,
                    cardBitmapWidth = cardAnatomyEngine.lastBitmapWidth,
                    cardBitmapHeight = cardAnatomyEngine.lastBitmapHeight,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ─── Compact Pipeline State Indicator (always visible, minimal) ───
        if (devSettings?.showPipelineStatus == true) {
            CompactStateIndicator(
                stage = pipelineStatus.stage,
                detectionCount = pipelineStatus.detectionCount,
                trackingId = pipelineStatus.trackingId,
                recognizedName = pipelineStatus.recognizedName,
                confidence = pipelineStatus.ocrConfidence,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 8.dp)
            )
        }

        // ─── OCR Status Overlay (shows every region's OCR result) ───
        if (devSettings?.showOcrText == true) {
            OcrStatusOverlay(
                cardBitmap = detectionPipeline.normalizer.lastResult?.bitmap,
                cardLayout = cardAnatomyEngine?.lastCardLayout,
                enabled = true,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

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
            // Freeze/Resume button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFrozen) "⏸ FROZEN" else if (detectionCount > 0) "Detecting $detectionCount card(s)" else "Scanning...",
                    color = if (isFrozen) Color.Yellow else if (detectionCount > 0) Color.Cyan else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )

                // Freeze / Resume button
                Text(
                    text = if (isFrozen) "▶ Resume" else "⏸ Freeze",
                    color = if (isFrozen) Color.Green else Color.Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            if (isFrozen) {
                                // Resume: unfreeze, clear the frozen bitmap
                                isFrozen = false
                                frozenBitmap = null
                                detectionPipeline.clearProcessedCards()
                            } else {
                                // Freeze: capture the last normalized card bitmap
                                val bmp = detectionPipeline.normalizer.lastResult?.bitmap
                                if (bmp != null && !bmp.isRecycled) {
                                    frozenBitmap = bmp
                                    isFrozen = true
                                }
                            }
                        }
                        .background(
                            if (isFrozen) Color.Green.copy(alpha = 0.2f) else Color.Cyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Developer toggle: tap to show dev settings panel
            Text(
                text = if (devSettings?.anyOverlayEnabled == true) "🔍 Dev: ON" else "🔍 Dev Tools",
                color = if (devSettings?.anyOverlayEnabled == true) Color.Green else Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { showDevPanel = !showDevPanel }
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // ─── Dev Settings Panel (shown when toggle is tapped) ───
        if (showDevPanel && devSettings != null) {
            DevSettingsPanel(
                settings = devSettings,
                onDismiss = { showDevPanel = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
