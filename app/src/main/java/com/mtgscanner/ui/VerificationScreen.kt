package com.mtgscanner.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgscanner.model.*

/**
 * VerificationScreen: User confirmation interface for detected Magic cards.
 * Displays OCR results, Scryfall matching candidates, and Scryfall card image preview.
 * Allows user to confirm identity, enter quantity, or reject/skip the card.
 * Integrates comprehensive error handling UI components:
 * - OfflineNotice: Shows when cache was used (no internet)
 * - ErrorSnackbar: Displays network or API errors
 * - LowConfidenceWarning: Alerts when OCR confidence < 60% (encourages verification)
 *
 * Card Flow:
 * 1. CardDetection → OCR → FuzzyMatching → VerificationScreen (this screen)
 * 2. User selects from match candidates (top-ranked by default)
 * 3. User enters quantity of copies to add
 * 4. User confirms → card saved to database OR rejects/skips
 *
 * Features:
 * - Vertical scrolling for long card lists (e.g., 10+ match candidates)
 * - LaunchedEffect to auto-select top candidate and show warnings
 * - Real-time quantity input validation
 * - Scryfall image preview (loads from CDN via Coil)
 * - Error state UI components for resilient offline handling
 *
 * @param cardVerification CardVerification state with OCR/matches/errors
 * @param onConfirm Callback(card: ScannedCard, quantity: Int) when user confirms
 * @param onReject Callback() when user rejects this card
 * @param onSkip Callback() when user skips for later review
 * @param modifier Compose modifier for styling
 */
@Composable
fun VerificationScreen(
    cardVerification: CardVerification,
    onConfirm: (card: ScannedCard, quantity: Int) -> Unit,
    onReject: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quantity by remember { mutableStateOf("1") }
    var selectedCandidate by remember { mutableStateOf<CardMatchCandidate?>(null) }
    var showErrorSnackbar by remember { mutableStateOf(false) }
    var showLowConfidenceDialog by remember { mutableStateOf(false) }

    val detectedText = cardVerification.detectedCardText
    val candidates = cardVerification.matchCandidates
    val errorMessage = cardVerification.errorMessage
    val isOffline = cardVerification.isOffline
    val ocrConfidence = cardVerification.ocrConfidence

    // Auto-select top candidate if available
    LaunchedEffect(candidates) {
        if (candidates.isNotEmpty() && selectedCandidate == null) {
            selectedCandidate = candidates.first()
        }
    }

    // Show error snackbar if error message exists
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            showErrorSnackbar = true
        }
    }

    // Show low confidence warning if OCR < 60%
    LaunchedEffect(ocrConfidence) {
        if (ocrConfidence < 0.6 && ocrConfidence > 0.0) {
            showLowConfidenceDialog = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Offline Notice (if using cache)
            if (isOffline) {
                OfflineNotice(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Header
            Text(
                text = "Verify Card",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )

            // OCR Results Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OCR Detected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                    Text(
                        text = "Card Name: ${detectedText?.cardName ?: "Unknown"}",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Set Code: ${detectedText?.setCode ?: "Unknown"}",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Collector #: ${detectedText?.collectorNumber ?: "Unknown"}",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    
                    // OCR Confidence with color coding
                    val confColor = when {
                        ocrConfidence >= 0.8 -> Color(0xFF4CAF50)  // Green
                        ocrConfidence >= 0.6 -> Color(0xFFFFA726)  // Orange
                        else -> Color(0xFFE53935)                   // Red
                    }
                    Text(
                        text = "Confidence: ${String.format("%.1f%%", ocrConfidence * 100)}",
                        fontSize = 12.sp,
                        color = confColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Divider()

            // Match Candidates Section
            if (candidates.isNotEmpty()) {
                Text(
                    text = "Matched Candidates",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                candidates.forEach { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        isSelected = selectedCandidate?.scryfallCard?.id == candidate.scryfallCard.id,
                        onSelect = { selectedCandidate = candidate }
                    )
                }
            } else {
                Text(
                    text = if (errorMessage != null)
                        "No candidates matched: $errorMessage"
                    else
                        "No candidates matched. Review OCR results.",
                    fontSize = 14.sp,
                    color = Color.Red,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color(0xFFFFE5E5), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }

            Divider()

            // Selected Card Display
            selectedCandidate?.let { candidate ->
                SelectedCardPreview(candidate = candidate)
            }

            Divider()

            // Quantity Input
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                label = { Text("Quantity") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                suffix = { Text("cards") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Skip")
                }

                Button(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Reject", Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reject")
                }

                Button(
                    onClick = {
                        val qty = quantity.toIntOrNull() ?: 1
                        val scannedCard = selectedCandidate?.scryfallCard?.let { card ->
                            ScannedCard(
                                scryfallId = card.id,
                                cardName = card.name,
                                setCode = card.setCode,
                                collectorNumber = card.collectorNumber,
                                quantity = qty,
                                rarity = card.rarity,
                                colors = card.colors.joinToString(","),
                                typeLine = card.typeLine,
                                oracleText = card.oracleText,
                                imageUrl = card.imageUris?.normal,
                                scannedTimestamp = System.currentTimeMillis()
                            )
                        } ?: return@Button
                        onConfirm(scannedCard, qty)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Confirm", Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Error Snackbar overlay (dismissible)
        if (showErrorSnackbar && errorMessage != null) {
            ErrorSnackbar(
                message = errorMessage,
                onDismiss = { showErrorSnackbar = false },
                actionLabel = "Dismiss",
                onActionClick = { showErrorSnackbar = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        // Low Confidence Warning dialog
        if (showLowConfidenceDialog) {
            LowConfidenceWarning(
                confidence = ocrConfidence,
                onDismiss = { showLowConfidenceDialog = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * CandidateCard: Displays a single fuzzy-matched candidate with score.
 */
@Composable
fun CandidateCard(
    candidate: CardMatchCandidate,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.Cyan else Color.Gray,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE0F7FA) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = candidate.scryfallCard.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = candidate.matchReason,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = String.format("%.0f%%", candidate.matchScore * 100),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (candidate.matchScore >= 0.8) Color(0xFF4CAF50) else if (candidate.matchScore >= 0.6) Color(0xFFFFA726) else Color(0xFFE53935)
            )
        }
    }
}

/**
 * SelectedCardPreview: Displays detailed preview of the selected card.
 */
@Composable
fun SelectedCardPreview(candidate: CardMatchCandidate) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Card Preview",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )

        candidate.scryfallCard.imageUris?.normal?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = candidate.scryfallCard.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                contentScale = ContentScale.Fit
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA), shape = RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = candidate.scryfallCard.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "Set: ${candidate.scryfallCard.setCode}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "Collector #: ${candidate.scryfallCard.collectorNumber}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
