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
import com.mtgscanner.anatomy.CardAnatomyEngine
import com.mtgscanner.camera.CameraPreviewManager
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.detection.CardDetector
import com.mtgscanner.detection.CardTracker
import com.mtgscanner.detection.DetectionPipeline
import com.mtgscanner.matching.FuzzyCardMatcher
import com.mtgscanner.model.CardVerification
import com.mtgscanner.model.ScannedCard
import com.mtgscanner.network.NetworkCacheManager
import com.mtgscanner.network.NetworkStateManager
import com.mtgscanner.network.RetryPolicy
import com.mtgscanner.network.ScryfallApiClient
import com.mtgscanner.network.ScryfallRepositoryResilience
import com.mtgscanner.ocr.OcrPipeline
import com.mtgscanner.ui.ErrorSnackbar
import com.mtgscanner.ui.LowConfidenceWarning
import com.mtgscanner.ui.OfflineNotice
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
    private lateinit var cardAnatomyEngine: CardAnatomyEngine
    private lateinit var fuzzyCardMatcher: FuzzyCardMatcher
    private lateinit var scryfallApiClient: ScryfallApiClient
    private lateinit var scryfallRepositoryResilience: ScryfallRepositoryResilience
    private lateinit var networkStateManager: NetworkStateManager
    private lateinit var networkCacheManager: NetworkCacheManager
    private lateinit var database: ScannedCardDatabase

    // UI state
    private var cameraPermissionGranted by mutableStateOf(false)
    private var isInitializing by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    private var isOffline by mutableStateOf(false)
    private var showLowConfidenceWarning by mutableStateOf(false)
    private var lowConfidenceValue by mutableStateOf(0f)

    /** Developer option: show anatomy debug overlay on camera preview. */
    internal var showAnatomyOverlay by mutableStateOf(false)

    /**
     * Initialize the MainActivity with all required components.
     * Sets up database, network layer, detection pipeline, OCR, fuzzy matching, and camera.
     * Manages permissions and orchestrates the Compose UI setup.
     *
     * @param savedInstanceState Bundle containing previously saved state, or null if creating fresh
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "Initializing MTG Scanner...")

        // Initialize database first (needed by repository)
        database = ScannedCardDatabase.getInstance(this)
        Log.d(TAG, "Database initialized")

        // Initialize network layer with resilience components
        scryfallApiClient = ScryfallApiClient()
        networkStateManager = NetworkStateManager(this)
        networkCacheManager = NetworkCacheManager(this)
        val retryPolicy = RetryPolicy(maxRetries = 3, initialDelayMs = 100, maxDelayMs = 5000)
        scryfallRepositoryResilience = ScryfallRepositoryResilience(
            scryfallApiClient,
            database,
            networkCacheManager,
            networkStateManager,
            retryPolicy
        )
        Log.d(TAG, "Scryfall API client and resilience layer initialized")

        // Initialize detection pipeline components
        cardDetector = CardDetector()
        cardTracker = CardTracker()
        detectionPipeline = DetectionPipeline()
        Log.d(TAG, "Detection pipeline initialized")

        // Initialize OCR pipeline
        ocrPipeline = OcrPipeline()
        Log.d(TAG, "OCR pipeline initialized")

        // Initialize Card Anatomy Engine (wraps OCR with anatomy-aware detection)
        cardAnatomyEngine = CardAnatomyEngine(ocrPipeline = ocrPipeline)
        Log.d(TAG, "Card Anatomy Engine initialized")

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
     * Check camera permissions and initialize UI accordingly.
     * If camera permission is already granted, initializes the camera and sets up the main UI.
     * If permission is not granted, displays a permission request screen.
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
     * Set up the main Compose UI after permissions have been granted.
     * Initializes the navigation system, sets up the detection pipeline callback,
     * and renders the AppRoot composable with all necessary dependencies.
     */
    private fun setupUI() {
        setContent {
            MTGScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navigator = remember { AppNavigator() }

                    // Wire DetectionPipeline callback to navigation — once only
                    LaunchedEffect(Unit) {
                        setupDetectionPipelineCallback(navigator)
                    }

                    AppRoot(
                        navigator = navigator,
                        cameraPreviewManager = cameraPreviewManager,
                        detectionPipeline = detectionPipeline,
                        database = database,
                        cardAnatomyEngine = cardAnatomyEngine,
                        showAnatomyOverlay = showAnatomyOverlay,
                        onToggleOverlay = { showAnatomyOverlay = !showAnatomyOverlay }
                    )
                }
            }
        }
    }

    /**
     * Set up Compose UI with an integrated permission request dialog.
     * Displays a permission request screen that prompts the user for camera access.
     * On grant: initializes camera and sets up main UI.
     * On deny: logs error and closes the application.
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
     * Configure the DetectionPipeline callback to orchestrate the full card scanning workflow.
     * Implements the pipeline: Detection → OCR → Fuzzy Matching → Scryfall Lookup (with Resilience) → Verification Screen.
     * Handles all Result<T> states (Success/CacheHit/Error) and displays appropriate error/offline UI states.
     * Updates low OCR confidence warnings when detected text confidence falls below 0.6.
     *
     * @param navigator AppNavigator instance to manage screen transitions after card processing is complete
     */
    private fun setupDetectionPipelineCallback(navigator: AppNavigator) {
        // Track OCR retry attempts per tracking ID to prevent infinite empty-result loops
        val ocrRetryCount = mutableMapOf<Int, Int>()
        val MAX_OCR_RETRIES = 2

        detectionPipeline.onCardReady = { cardBitmap, trackingId ->
            lifecycleScope.launch {
                try {
                    val retries = ocrRetryCount.getOrDefault(trackingId, 0)
                    Log.d(TAG, "Card detected (trackingId=$trackingId, retry=$retries), " +
                        "crop=${cardBitmap.width}x${cardBitmap.height}, starting OCR...")

                    // Step 1: Card Anatomy Engine (detection + OCR)
                    val detectedText = cardAnatomyEngine.analyze(cardBitmap, trackingId)
                    Log.d(TAG, "Anatomy+OCR result: '${detectedText.cardName}' (confidence=${detectedText.ocrConfidence})")

                    // Guard: If OCR produced no usable card name
                    if (detectedText.cardName.isBlank()) {
                        if (retries < MAX_OCR_RETRIES) {
                            // Allow retry — clear this specific card from processed set
                            ocrRetryCount[trackingId] = retries + 1
                            Log.w(TAG, "OCR empty for trackingId=$trackingId (retry ${retries + 1}/$MAX_OCR_RETRIES)")
                            detectionPipeline.clearProcessedCards()
                        } else {
                            // Max retries reached — give up on this card, don't retry again
                            Log.w(TAG, "OCR failed $MAX_OCR_RETRIES times for trackingId=$trackingId — giving up")
                            ocrRetryCount.remove(trackingId)
                            // Don't clear processedCards — this card stays suppressed
                        }
                        return@launch
                    }

                    // OCR succeeded — clear retry counter
                    ocrRetryCount.remove(trackingId)

                    // Step 1.5: Check OCR confidence and warn if low
                    if (detectedText.ocrConfidence < 0.6) {
                        Log.w(TAG, "Low OCR confidence: ${detectedText.ocrConfidence}")
                        lowConfidenceValue = detectedText.ocrConfidence
                        showLowConfidenceWarning = true
                    }

                    // Step 2: Fetch Scryfall candidates with resilience (network + retry + cache)
                    val resultCandidates = scryfallRepositoryResilience.findCardCandidatesResilient(detectedText)
                    
                    val scryfallCandidates = when (resultCandidates) {
                        is ScryfallRepositoryResilience.Result.Success -> {
                            Log.d(TAG, "Scryfall lookup successful: ${resultCandidates.data.size} candidates")
                            isOffline = false
                            errorMessage = null
                            resultCandidates.data
                        }
                        is ScryfallRepositoryResilience.Result.CacheHit -> {
                            Log.w(TAG, "Scryfall cache hit: ${resultCandidates.message}")
                            isOffline = true
                            errorMessage = "Using offline cache"
                            resultCandidates.data
                        }
                        is ScryfallRepositoryResilience.Result.Error -> {
                            Log.e(TAG, "Scryfall lookup failed: ${resultCandidates.message}")
                            errorMessage = resultCandidates.message
                            isOffline = true
                            resultCandidates.fallbackData ?: emptyList()
                        }
                    }

                    Log.d(TAG, "Found ${scryfallCandidates.size} Scryfall candidates")

                    // Step 3: Fuzzy matching
                    val matchCandidates = fuzzyCardMatcher.matchCard(
                        detectedText,
                        scryfallCandidates
                    )
                    Log.d(TAG, "Fuzzy matching produced ${matchCandidates.size} ranked candidates")

                    // Guard: If no candidates found at all, don't navigate to empty verification.
                    // Clear processed cards so the same card region can be re-detected.
                    if (matchCandidates.isEmpty() && scryfallCandidates.isEmpty()) {
                        Log.w(TAG, "No candidates found for '${detectedText.cardName}' — retrying detection")
                        detectionPipeline.clearProcessedCards()
                        return@launch
                    }

                    // Step 4: Navigate to verification screen with error/offline state
                    val cardVerification = CardVerification(
                        trackingId = trackingId,
                        detectedCardText = detectedText,
                        matchCandidates = matchCandidates,
                        userAction = com.mtgscanner.model.UserAction.PENDING
                    )
                    navigator.navigateToVerification(
                        cardVerification,
                        errorMessage = errorMessage,
                        isOffline = isOffline,
                        ocrConfidence = detectedText.ocrConfidence
                    )

                    Log.d(TAG, "Navigated to verification screen")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in card detection pipeline: ${e.message}", e)
                    errorMessage = "Pipeline error: ${e.message}"
                    // On exception, allow re-detection of the same card
                    detectionPipeline.clearProcessedCards()
                }
            }
        }
    }

    /**
     * Initialize the camera preview manager after permissions are confirmed.
     * Prepares the camera for binding to the lifecycle and ready for frame analysis.
     * Sets isInitializing to false upon completion, allowing the UI to proceed with camera rendering.
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

    /**
     * Handle activity resume lifecycle event.
     * Logs resume state; camera management is delegated to CameraPreviewManager via lifecycle binding.
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")
    }

    /**
     * Handle activity pause lifecycle event.
     * Logs pause state; camera is paused by CameraPreviewManager via lifecycle binding.
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")
        // Camera will be paused by CameraPreviewManager lifecycle handling
    }

    /**
     * Handle activity destruction lifecycle event.
     * Cleans up camera resources by stopping the CameraPreviewManager.
     * Logs any errors that occur during cleanup.
     */
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
 * Composable screen that displays a camera permission request dialog.
 * Automatically launches the system permission request on initial composition.
 * Provides user feedback with a loading indicator while awaiting permission decision.
 *
 * @param onPermissionGranted Callback invoked when user grants camera permission
 * @param onPermissionDenied Callback invoked when user denies camera permission
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
