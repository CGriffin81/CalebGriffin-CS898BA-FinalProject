package com.mtgscanner.ui

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.model.CardVerification
import com.mtgscanner.model.UserAction

/**
 * CameraScreen: Live camera preview with real-time card detection overlay.
 * Displays tracked card regions and transitions to verification when a card is ready.
 *
 * @param onCardDetected Callback when a card is stable and ready for verification
 * @param cameraPreviewManager Manages CameraX lifecycle and frame delivery
 * @param detectionPipeline Orchestrates detection, tracking, OCR, and matching
 */
@Composable
fun CameraScreen(
    onCardDetected: (cardData: Any) -> Unit,
    cameraPreviewManager: CameraPreviewManager,
    detectionPipeline: DetectionPipeline,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var detectedCardCount by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Set up detection pipeline callback to transition to verification screen
        detectionPipeline.onCardReady = { cardBitmap, trackingId ->
            isProcessing = true
            detectedCardCount += 1
            // Trigger verification flow (passed to parent navigator)
            onCardDetected(mapOf("bitmap" to cardBitmap, "trackingId" to trackingId))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Preview via AndroidView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                androidx.compose.ui.viewinterop.PreviewView(ctx).apply {
                    cameraPreviewManager.setupCamera(
                        lifecycleOwner = ctx as androidx.lifecycle.LifecycleOwner,
                        previewView = this,
                        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    )
                }
            }
        )

        // Detection overlay: Display tracked card regions (simplified; in production, draw rectangles)
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
 * DetectionOverlay: Displays detected and tracked card regions in real-time.
 * Simplified version; in production, use Canvas to draw rectangles/contours.
 */
@Composable
fun DetectionOverlay(
    modifier: Modifier = Modifier,
    detectionPipeline: DetectionPipeline
) {
    val trackedCardsCount by remember { mutableStateOf(0) }

    Box(modifier = modifier) {
        // Placeholder for contour drawing; would use Canvas in production
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
