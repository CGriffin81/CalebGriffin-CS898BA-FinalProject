package com.mtgscanner.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.data.ScannedCardEntity
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.model.*
import kotlinx.coroutines.launch

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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            AppScreen.VERIFICATION -> {
                navigator.cardVerification?.let { cardVerification ->
                    val scope = rememberCoroutineScope()

                    Box(modifier = Modifier.fillMaxSize()) {
                        VerificationScreen(
                            cardVerification = cardVerification,
                            onConfirm = { scannedCard, _ ->
                                scope.launch {
                                    saveCardToCollection(scannedCard, database)
                                }
                                detectionPipeline.clearProcessedCards()
                                navigator.navigateToCamera()
                            },
                            onReject = {
                                detectionPipeline.clearProcessedCards()
                                navigator.navigateToCamera()
                            },
                            onSkip = {
                                detectionPipeline.clearProcessedCards()
                                navigator.navigateToCamera()
                            }
                        )

                        // Back button overlay
                        IconButton(
                            onClick = {
                                detectionPipeline.clearProcessedCards()
                                navigator.navigateToCamera()
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
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
                        onEditCard = { _ ->
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
                            Icons.AutoMirrored.Filled.ArrowBack,
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
 * Persist a confirmed card to the Room collection database.
 *
 * Logic:
 * 1. Validate that [ScannedCard.scryfallId] is non-blank (guards against bad Scryfall data).
 * 2. Check if the card already exists via [ScannedCardDao.findCardByIdentity].
 * 3. If it exists: increment the stored quantity by the new card's quantity.
 * 4. If it doesn't: insert a new [ScannedCardEntity] row.
 *
 * This function is suspend-safe and should be called from a coroutine scope.
 *
 * @param scannedCard The confirmed card from the VerificationScreen.
 * @param database The Room database instance.
 */
private suspend fun saveCardToCollection(scannedCard: ScannedCard, database: ScannedCardDatabase) {
    val dao = database.scannedCardDao()

    // Guard: reject cards with empty scryfallId (prevents corrupt data)
    if (scannedCard.scryfallId.isBlank()) {
        Log.e("AppRoot", "Refusing to store card with blank scryfallId: '${scannedCard.cardName}'")
        return
    }

    // Check for duplicate by identity triple (scryfallId + setCode + collectorNumber)
    val existing = dao.findCardByIdentity(
        scannedCard.scryfallId,
        scannedCard.setCode,
        scannedCard.collectorNumber
    )

    if (existing != null) {
        // Card already in collection — increment quantity
        val updatedQuantity = existing.quantity + scannedCard.quantity
        dao.updateCard(existing.copy(quantity = updatedQuantity))
        Log.d("AppRoot", "Updated quantity for '${existing.cardName}': ${existing.quantity} → $updatedQuantity")
    } else {
        // New card — insert fresh row
        val entity = ScannedCardEntity(
            scryfallId = scannedCard.scryfallId,
            cardName = scannedCard.cardName,
            setCode = scannedCard.setCode,
            collectorNumber = scannedCard.collectorNumber,
            quantity = scannedCard.quantity,
            rarity = scannedCard.rarity,
            colors = scannedCard.colors,
            typeLine = scannedCard.typeLine,
            oracleText = scannedCard.oracleText,
            imageUrl = scannedCard.imageUrl,
            scannedTimestamp = scannedCard.scannedTimestamp,
            userConfirmed = true
        )
        val insertedId = dao.insertCard(entity)
        Log.d("AppRoot", "Inserted '${scannedCard.cardName}' with id=$insertedId")
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
                    Icons.Default.CameraAlt,
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
                    Icons.Default.GridView,
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
