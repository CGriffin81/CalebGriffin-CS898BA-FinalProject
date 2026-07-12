# Real-Time MTG Scanner
## Caleb Griffin

### Overview
This project will build a real-time Android scanner for Magic: The Gathering cards using CameraX. The goal is to identify and catalog several cards at once, even when a binder page shows 9 or 12 cards at a time. The app should support slow page-by-page scanning, verify each identified card with the user, and let the user enter quantity before storing the card in a local collection.

### Brief Literature Review
The literature review points toward a pipeline built around mobile computer vision, OCR, and database-backed validation instead of a single end-to-end recognition model.

- CameraX and ImageAnalysis support continuous camera frames, which is the right base for live scanning.
- OCR, preprocessing, and perspective correction help clean up card text before identification.
- Real-time tracking and duplicate detection help prevent the same card from being added multiple times while it stays visible in the camera stream.
- Fuzzy matching against Scryfall helps turn imperfect OCR output into a valid card identity.
- Room supports local catalog storage for scanned cards, quantities, and metadata.
- Existing MTG scanners show the expected workflow: quick scanning, minimal friction, and collection management after recognition.
- Performance optimization matters because the app must keep preview, detection, OCR, and cataloging responsive on a phone.

### Target Workflow
1. CameraX opens a live preview and analyzes frames.
2. The app detects cards in the frame, including binder pages with multiple cards.
3. Each detected card is cropped, perspective-corrected, and sent through OCR or matching logic.
4. The app compares results against Scryfall data and picks the most likely card.
5. A verification prompt appears for the user to confirm the card and enter quantity.
6. The confirmed card is stored locally in Room and added to the catalog.

### Proposed Directory Structure
The codebase should stay organized by pipeline stage and Android layer.

```text
literature_review/
	# Research notes and annotated reading summaries

app/
	src/main/java/<package>/
		camera/
		analysis/
		detection/
		ocr/
		matching/
		data/
		ui/
		model/
		util/
	src/main/res/
	src/test/
	src/androidTest/
```

### Current State

**Completed Components:**

1. **Camera & Frame Analysis** (✓ Complete)
   - `camera/CameraPreviewManager.kt`: CameraX lifecycle management, preview binding, ImageAnalysis with STRATEGY_KEEP_ONLY_LATEST backpressure
   - `analysis/CardFrameAnalyzer.kt`: YUV→JPEG→Bitmap conversion, rotation handling

2. **Detection Pipeline** (✓ Complete)
   - `detection/CardDetector.kt`: Otsu thresholding, morphological operations, contour detection, aspect-ratio & area filtering
   - `detection/CardTracker.kt`: Frame-to-frame ID assignment, 3-frame stability enforcement, 30-second stale track cleanup
   - `detection/DetectionPipeline.kt`: Orchestration layer, onCardReady callback, duplicate prevention via processedCards Set

3. **Data Models** (✓ Complete)
   - `model/CardModels.kt`: DetectedCardText (OCR output), ScryfallCard (API model), ScannedCard (Room entity), CardMatchCandidate (fuzzy result), CardVerification (user action state)

4. **Optical Character Recognition** (✓ Complete)
   - `ocr/CardOcrProcessor.kt`: ML Kit Text Recognition, regex-based field extraction (name, set code, collector number), confidence scoring
   - `ocr/OcrPreprocessor.kt`: CLAHE contrast enhancement, Gaussian blur, sharpening, region extraction (name/type/collector)
   - `ocr/OcrPipeline.kt`: Full pipeline orchestration, fallback to region-based OCR on low confidence (<0.6)

5. **Fuzzy Matching** (✓ Complete)
   - `matching/FuzzyCardMatcher.kt`: Levenshtein distance-based matching, weighted scoring (60% name, 20% set, 20% collector), filtering >0.5 threshold

6. **Persistence & Database** (✓ Complete)
   - `data/ScannedCardDatabase.kt`: Room entity & DAO with CRUD, search by name, filter by set, collection stats (total, unique, sets)
9. **Network & Scryfall API** (✓ Complete)
   - `network/ScryfallModels.kt`: API response DTOs (ScryfallCardResponse, ImageUrisResponse, ScryfallSearchResponse, ScryfallError)
   - `network/ScryfallApiService.kt`: Retrofit interface with 7 endpoints (search, exact name, fuzzy name, collector number lookup, advanced query, random, set list)
   - `network/ScryfallApiClient.kt`: Async Retrofit-based client with suspendable coroutines, error handling, rate-limit awareness, response→domain conversion
   - `network/ScryfallRepository.kt`: Repository pattern combining network + local cache (offline-first: identity → fuzzy → search; preload support)
10. **User Interface (Jetpack Compose)** (✓ Complete)
   - `ui/CameraScreen.kt`: Live preview with detection overlay, frame counter
   - `ui/VerificationScreen.kt`: OCR results display, candidate ranking, Scryfall card image preview, quantity input, confirm/reject/skip actions
   - `ui/CollectionScreen.kt`: Grid layout, search by name, filter by set, sort (name, date), collection statistics
   - `ui/AppNavigator.kt`: State machine for MAIN ↔ CAMERA ↔ VERIFICATION ↔ COLLECTION transitions
   - `ui/AppRoot.kt`: Navigation routing, MainMenuScreen landing page
   - `ui/theme/Theme.kt`: Material3 color scheme (primary: blue, secondary: cyan, tertiary: green, error: red)

11. **Android Configuration & Build** (✓ Complete)
   - `AndroidManifest.xml`: Permissions (CAMERA, INTERNET, READ_MEDIA_IMAGES), hardware features, MainActivity declaration, portrait lock
   - `build.gradle.kts`: 40+ dependencies (CameraX, ML Kit, OpenCV, Room, Retrofit, Coil, Compose, Coroutines, test frameworks)
   - `settings.gradle.kts`: Repository configuration (Google, Maven Central, JCenter)
   - `res/values/strings.xml`: App strings (errors, permissions, actions)
   - `res/values/colors.xml`: Material3 color palette
   - `res/values/themes.xml`: Material3 theme styling
   - `MainActivity.kt` (WIRED): Complete app bootstrap with component initialization, permission handling, detection pipeline callback orchestration, lifecycle management

12. **Network Resilience & Caching** (✓ Complete)
   - `network/NetworkResilience.kt`: NetworkStateManager (connectivity monitoring), RetryPolicy (exponential backoff 100ms→5s + ±10% jitter), retryableCall() wrapper
   - `network/NetworkCacheManager.kt`: SharedPreferences offline cache (7-day TTL, search, bulk preload, stats)
   - `network/ScryfallRepositoryResilience.kt`: Resilient repository with fallback chain (identity → fuzzy → search → cache), Result<T> ADT, network awareness

13. **Error Handling UI** (✓ Complete)
   - `ui/ErrorHandling.kt`: 6 Material3 error components (ErrorSnackbar, OfflineNotice, ErrorDialog, LowConfidenceWarning, LoadingOverlay, PermissionDeniedScreen)

14. **Integration Testing** (✓ Complete - 58 test cases)
   - `test/detection/DetectionPipelineIntegrationTest.kt`: Detection, tracking, stability, multiple cards, duplicates, stale cleanup (7 tests)
   - `test/ocr/OcrPipelineIntegrationTest.kt`: Preprocessing, region extraction, field parsing, confidence, rotation, blank images (10 tests)
   - `test/matching/FuzzyMatchingIntegrationTest.kt`: Perfect matches, OCR noise, weighted scoring, filtering, ranking, Levenshtein (9 tests)
   - `androidTest/data/ScannedCardDatabaseIntegrationTest.kt`: CRUD, search, filter, uniqueness, stats, Flow reactivity, performance (11 tests)
   - `androidTest/EndToEndIntegrationTest.kt`: Full workflows (single card, multiple cards, rejection, quantity update, collection browsing) (6 tests)
   - `androidTest/ui/NavigationIntegrationTest.kt`: State transitions, data passing, UI interactions (15 tests)

**Pipeline Flow (with resilience):**
```
Camera Frame (CameraX)
  ↓
CardFrameAnalyzer (YUV→Bitmap)
  ↓
CardDetector (Segmentation + Contour)
  ↓
CardTracker (3-frame stability + ID assignment)
  ↓
OcrPipeline (Preprocessing + ML Kit + Regex)
  ↓
NetworkStateManager (Check connectivity)
  ↓ (if online)
ScryfallRepositoryResilience (with retry + fallback)
  ├─ RetryPolicy: Exponential backoff (100ms→5s, ±10% jitter)
  ├─ Fallback chain: identity → fuzzy → search
  └─ CacheHit/Error: SharedPreferences offline cache (7-day TTL)
  ↓
FuzzyCardMatcher (Levenshtein scoring vs Scryfall)
  ↓
VerificationScreen (User confirm/reject/skip + quantity)
  ├─ Show error UI on network failure (ErrorSnackbar, ErrorDialog)
  ├─ Show OfflineNotice if using cache
  └─ Show LowConfidenceWarning if OCR < threshold
  ↓
ScannedCardDatabase (Room storage)
  ↓
CollectionScreen (Browse + Search + Filter)
```

**Next Steps (Before Real-World Testing):**
- Integrate ScryfallRepositoryResilience into MainActivity + VerificationScreen callbacks
- Integrate NetworkStateManager + error UI components (OfflineNotice, ErrorSnackbar) into CameraScreen
- Integrate LowConfidenceWarning into VerificationScreen when OCR confidence < 0.6
- Preload common sets (LEA, M21, SLD) into cache during app first run
- Test offline mode: disable network → verify cache fallback → verify error UI
- Test retry logic: mock network timeouts → verify exponential backoff
- Build & deploy: `./gradlew assembleDebug` → install APK on Samsung Galaxy S23
- Real-world testing: Scan binder pages with 9-12 cards, verify detection/OCR/network resilience
