package com.mtgscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ErrorSnackbar: Material3 snackbar for network/error messages.
 * Dismissible with optional action button.
 */
@Composable
fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    duration: Long = 4000L,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(duration)
        onDismiss()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Color(0xFFE53935), shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE53935))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Error icon + message
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )

                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 2
                )
            }

            // Action button or dismiss
            if (actionLabel != null && onActionClick != null) {
                TextButton(
                    onClick = {
                        onActionClick()
                        onDismiss()
                    },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * OfflineNotice: Sticky banner indicating offline mode.
 */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFA500), shape = RoundedCornerShape(0.dp)),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFA500))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = "Offline",
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )

            Text(
                text = "No internet connection. Using offline database.",
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * ErrorDialog: Full-screen error dialog with retry option.
 */
@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFFE53935)
                )
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color.Gray
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onRetry()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Retry", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        modifier = modifier,
        containerColor = Color.White,
        titleContentColor = Color.Black,
        textContentColor = Color.Gray
    )
}

/**
 * LowConfidenceWarning: Warning dialog when OCR confidence is below threshold.
 */
@Composable
fun LowConfidenceWarning(
    detectedName: String,
    confidence: Double,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onRetry,
        title = {
            Text(
                text = "Low Confidence Detection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Card Name: $detectedName",
                    fontSize = 14.sp
                )

                LinearProgressIndicator(
                    progress = confidence.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (confidence > 0.7) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    trackColor = Color.LightGray
                )

                Text(
                    text = "Confidence: ${(confidence * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = if (confidence > 0.7) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "This detection has low confidence. Try with better lighting or different angle.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Continue Anyway", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        modifier = modifier
    )
}

/**
 * LoadingOverlay: Semi-transparent overlay with loading spinner.
 */
@Composable
fun LoadingOverlay(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .background(Color.White, shape = RoundedCornerShape(12.dp))
                .padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFF1976D2),
                strokeWidth = 3.dp
            )

            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * PermissionDeniedScreen: Full screen error for missing permissions.
 */
@Composable
fun PermissionDeniedScreen(
    permission: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF44336)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .padding(32.dp)
        ) {
            Icon(
                Icons.Default.NoPhotography,
                contentDescription = "Permission Denied",
                modifier = Modifier.size(64.dp),
                tint = Color.White
            )

            Text(
                text = "Permission Denied",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "MTG Scanner requires $permission permission to work.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Exit App",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}
