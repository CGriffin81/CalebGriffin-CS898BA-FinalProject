package com.mtgscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.model.*

/**
 * AppRoot: Root navigation composable routing all screens and managing transitions.
 * Implements state-driven navigation using AppNavigator state machine.
 * Conditionally renders current screen (MAIN, CAMERA, VERIFICATION, COLLECTION) based on navigation state.
 * Manages back button overlays, dependencies passing, and screen callbacks.
 *
 * Navigation Routes:
 * - MAIN: Menu → CameraClick → CAMERA or CollectionClick → COLLECTION
 * - CAMERA: Card detected → VERIFICATION with CardVerification data
 * - VERIFICATION: Confirm → save + return to CAMERA; Reject/Skip → CAMERA
 * - COLLECTION: Browse → can select card details (future enhancement)
 *
 * Dependencies:
 * - cameraPreviewManager: CameraX lifecycle, passed to CameraScreen
 * - detectionPipeline: Card detection/tracking, passed to CameraScreen
 * - database: Room database, passed for card storage on confirmation
 *
 * Back Button Behavior:
 * - Back buttons added as overlays on CAMERA and VERIFICATION screens (for landscape support)
 * - Buttons routed to appropriate screen (navigator.returnToMain() or navigator.navigateToCamera())
 * - Main and Collection screens have built-in navigation controls
 *
 * @param navigator AppNavigator managing current screen state
 * @param cameraPreviewManager CameraX lifecycle and preview binding
 * @param detectionPipeline Detection and tracking for frame processing
 * @param database Room database for scanned card persistence
 */
@Composable
fun AppRoot(
    navigator: AppNavigator,
    cameraPreviewManager: CameraPreviewManager,
    detectionPipeline: DetectionPipeline,
    database: ScannedCardDatabase
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        when (navigator.currentScreen) {
            AppScreen.MAIN -> MainMenuScreen(
                onCameraClick = { navigator.navigateToCamera() },
                onCollectionClick = { navigator.navigateToCollection() }
            )

            AppScreen.CAMERA -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(
                        onCardDetected = { cardData ->
                            if (cardData is CardVerification) {
                                navigator.navigateToVerification(cardData)
                            }
                        },
                        cameraPreviewManager = cameraPreviewManager,
                        detectionPipeline = detectionPipeline
                    )

                    // Back button overlay
                    IconButton(
                        onClick = { navigator.returnToMain() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            AppScreen.VERIFICATION -> {
                navigator.cardVerification?.let { cardVerification ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        VerificationScreen(
                            cardVerification = cardVerification,
                            onConfirm = { card, qty ->
                                // Save to database
                                // TODO: Insert into Room database
                                navigator.navigateToCamera() // Return to camera after confirm
                            },
                            onReject = {
                                navigator.navigateToCamera() // Return to camera
                            },
                            onSkip = {
                                navigator.navigateToCamera() // Return to camera
                            }
                        )

                        // Back button overlay
                        IconButton(
                            onClick = { navigator.navigateToCamera() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            AppScreen.COLLECTION -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CollectionScreen(
                        database = database,
                        onEditCard = { card ->
                            // TODO: Show edit dialog for quantity
                        }
                    )

                    // Back button overlay
                    IconButton(
                        onClick = { navigator.returnToMain() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * MainMenuScreen: Main landing screen with navigation options.
 */
@Composable
fun MainMenuScreen(
    onCameraClick: () -> Unit,
    onCollectionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1976D2),
                        Color(0xFF1565C0)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .padding(32.dp)
        ) {
            // App Title
            Text(
                text = "MTG Scanner",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Catalog your Magic: The Gathering collection",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Camera Button
            Button(
                onClick = onCameraClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    androidx.compose.material.icons.filled.Videocam,
                    contentDescription = "Scan Cards",
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF1976D2)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Scan Cards",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            // Collection Button
            Button(
                onClick = onCollectionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
            ) {
                Icon(
                    androidx.compose.material.icons.filled.Collections,
                    contentDescription = "View Collection",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "View Collection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tip: Position your camera facing a binder with 9–12 cards visible. The app will detect each card individually.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
