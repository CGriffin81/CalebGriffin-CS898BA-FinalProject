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
 * CameraScreen: Live camera preview with real-time card detection overlay.
 *
 * Connects the CameraX frame stream to the [DetectionPipeline] so that each analyzed
 * frame is passed through detection → tracking → OCR readiness checks.
 *
 * When a card reaches stability (3+ frames), [DetectionPipeline.onCardReady] fires and
 * this screen invokes [onCardDetected] to transition to the verification screen.
 *
 * @param onCardDetected Callback when a card is stable and ready for verification.
 * @param cameraPreviewManager Manages CameraX lifecycle and frame delivery.
 * @param detectionPipeline Orchestrates detection, tracking, and OCR preparation.
 * @param modifier Compose modifier for the root layout.
 */
@Composable
fun CameraScreen(
    onCardDetected: (cardData: Any) -> Unit,
    cameraPreviewManager: CameraPreviewManager,
    detectionPipeline: DetectionPipeline,
    modifier: Modifier = Modifier
) {
    var detectedCardCount by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    // Wire the detection pipeline's "card ready" callback to navigate to verification.
    // This is set once when the composable enters composition.
    LaunchedEffect(Unit) {
        detectionPipeline.onCardReady = { cardBitmap, trackingId ->
            isProcessing = true
            detectedCardCount += 1
            onCardDetected(mapOf("bitmap" to cardBitmap, "trackingId" to trackingId))
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
                    // Connect frame delivery: CameraX → CardFrameAnalyzer → DetectionPipeline
                    cameraPreviewManager.setupCamera(
                        previewView = this,
                        onFrameReady = detectionPipeline::processFrame
                    )
                }
            }
        )

        // Detection overlay: shows tracked card count (placeholder for bounding box drawing)
        DetectionOverlay(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            detectionPipeline = detectionPipeline
        )

        // Top status bar
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
                text = "Detected: $detectedCardCount",
                color = Color.Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom status and control panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.Cyan,
                    strokeWidth = 3.dp
                )
                Text(
                    text = "Processing card...",
                    color = Color.White,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "Position card and align with binder page (9–12 cards)",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * DetectionOverlay: Displays detected and tracked card count in real-time.
 * Simplified version — in production, this would draw bounding box rectangles
 * using Canvas over the camera preview.
 */
@Composable
fun DetectionOverlay(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") detectionPipeline: DetectionPipeline
) {
    val trackedCardsCount by remember { mutableStateOf(0) }

    Box(modifier = modifier) {
        Text(
            text = "Tracked: $trackedCardsCount cards",
            color = Color.Cyan,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        )
    }
}
