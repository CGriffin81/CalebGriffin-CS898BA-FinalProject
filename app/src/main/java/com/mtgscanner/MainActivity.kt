package com.mtgscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.detection.CardDetector
import com.mtgscanner.detection.CardTracker
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.matching.FuzzyCardMatcher
import com.mtgscanner.model.CardVerification
import com.mtgscanner.model.ScannedCard
import com.mtgscanner.network.ScryfallApiClient
import com.mtgscanner.network.ScryfallRepository
import com.mtgscanner.ocr.OcrPipeline
import com.mtgscanner.ui.AppNavigator
import com.mtgscanner.ui.AppRoot
import com.mtgscanner.ui.theme.MTGScannerTheme
import kotlinx.coroutines.launch

/**
 * MainActivity: Entry point for the MTG Scanner application.
 * Orchestrates initialization of all components:
 * - CameraX (preview + frame analysis)
 * - Detection pipeline (contour detection + tracking)
 * - OCR pipeline (text recognition + field extraction)
 * - Fuzzy matching (Levenshtein distance vs Scryfall)
 * - Scryfall API client (network + cache)
 * - Room database (local persistence)
 * - Jetpack Compose UI (camera, verification, collection screens)
 *
 * Lifecycle: Handles runtime permissions, camera resource management, and error states.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Core components
    private lateinit var cameraPreviewManager: CameraPreviewManager
    private lateinit var cardDetector: CardDetector
    private lateinit var cardTracker: CardTracker
    private lateinit var detectionPipeline: DetectionPipeline
    private lateinit var ocrPipeline: OcrPipeline
    private lateinit var fuzzyCardMatcher: FuzzyCardMatcher
    private lateinit var scryfallApiClient: ScryfallApiClient
    private lateinit var scryfallRepository: ScryfallRepository
    private lateinit var database: ScannedCardDatabase

    // UI state
    private var cameraPermissionGranted by mutableStateOf(false)
    private var isInitializing by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "Initializing MTG Scanner...")

        // Initialize database first (needed by repository)
        database = ScannedCardDatabase.getInstance(this)
        Log.d(TAG, "Database initialized")

        // Initialize network layer
        scryfallApiClient = ScryfallApiClient()
        scryfallRepository = ScryfallRepository(scryfallApiClient, database)
        Log.d(TAG, "Scryfall API client initialized")

        // Initialize detection pipeline components
        cardDetector = CardDetector()
        cardTracker = CardTracker()
        detectionPipeline = DetectionPipeline()
        Log.d(TAG, "Detection pipeline initialized")

        // Initialize OCR pipeline
        ocrPipeline = OcrPipeline()
        Log.d(TAG, "OCR pipeline initialized")

        // Initialize fuzzy matching
        fuzzyCardMatcher = FuzzyCardMatcher()
        Log.d(TAG, "Fuzzy matcher initialized")

        // Initialize camera
        cameraPreviewManager = CameraPreviewManager(this)
        Log.d(TAG, "Camera preview manager initialized")

        // Check camera permission and set up UI
        setupPermissionsAndUI()
    }

    /**
     * Handle camera permissions and set up Compose UI.
     */
    private fun setupPermissionsAndUI() {
        // Check if camera permission is already granted
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            cameraPermissionGranted = true
            initializeCamera()
            setupUI()
        } else {
            // Permission not granted; show permission request in Compose
            setupUIWithPermissionRequest()
        }
    }

    /**
     * Set up Compose UI (after permissions are handled).
     */
    private fun setupUI() {
        setContent {
            MTGScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navigator = remember { AppNavigator() }

                    // Wire DetectionPipeline callback to navigation
                    setupDetectionPipelineCallback(navigator)

                    AppRoot(
                        navigator = navigator,
                        cameraPreviewManager = cameraPreviewManager,
                        detectionPipeline = detectionPipeline,
                        database = database
                    )
                }
            }
        }
    }

    /**
     * Set up Compose UI with permission request dialog.
     */
    private fun setupUIWithPermissionRequest() {
        setContent {
            MTGScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionRequestScreen(
                        onPermissionGranted = {
                            cameraPermissionGranted = true
                            initializeCamera()
                            setupUI()
                        },
                        onPermissionDenied = {
                            Log.e(TAG, "Camera permission denied")
                            // Show error and close app
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * Wire DetectionPipeline.onCardReady callback to navigation and OCR pipeline.
     * Orchestrates: Detection → OCR → Fuzzy matching → Scryfall lookup → Verification screen.
     */
    private fun setupDetectionPipelineCallback(navigator: AppNavigator) {
        detectionPipeline.onCardReady = { cardBitmap, trackingId ->
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "Card detected (trackingId=$trackingId), starting OCR...")

                    // Step 1: OCR recognition
                    val detectedText = ocrPipeline.recognizeCard(cardBitmap, trackingId)
                    Log.d(TAG, "OCR result: ${detectedText.cardName} (confidence=${detectedText.confidence})")

                    // Step 2: Fetch Scryfall candidates (network or cache)
                    val scryfallCandidates = scryfallRepository.findCardCandidates(detectedText)
                    Log.d(TAG, "Found ${scryfallCandidates.size} Scryfall candidates")

                    // Step 3: Fuzzy matching
                    val matchCandidates = fuzzyCardMatcher.matchCard(
                        detectedText,
                        scryfallCandidates
                    )
                    Log.d(TAG, "Fuzzy matching produced ${matchCandidates.size} ranked candidates")

                    // Step 4: Navigate to verification screen
                    val cardVerification = CardVerification(
                        trackingId = trackingId,
                        detectedCardText = detectedText,
                        matchCandidates = matchCandidates,
                        userAction = com.mtgscanner.model.UserAction.PENDING
                    )
                    navigator.navigateToVerification(cardVerification)

                    Log.d(TAG, "Navigated to verification screen")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in card detection pipeline: ${e.message}", e)
                    // Could show error message in UI here
                }
            }
        }
    }

    /**
     * Initialize camera after permissions are granted.
     */
    private fun initializeCamera() {
        lifecycleScope.launch {
            try {
                // CameraPreviewManager will be bound to lifecycle in CameraScreen
                Log.d(TAG, "Camera ready for binding")
                isInitializing = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera: ${e.message}", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")
        // Camera will be paused by CameraPreviewManager lifecycle handling
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed")
        try {
            cameraPreviewManager.stopCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}", e)
        }
    }
}

/**
 * PermissionRequestScreen: Shows permission request dialog when camera permission is not granted.
 */
@Composable
fun PermissionRequestScreen(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1976D2)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Camera Permission Required",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "MTG Scanner needs access to your camera to scan Magic: The Gathering cards.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.Cyan,
                strokeWidth = 3.dp
            )

            Text(
                text = "Requesting permission...",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
