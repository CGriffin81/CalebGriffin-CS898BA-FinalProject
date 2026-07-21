# Real-Time MTG Scanner
## Caleb Griffin

### Overview
A real-time Android scanner for Magic: The Gathering cards using CameraX. Identifies and catalogs cards from binder pages showing 9–12 cards at a time. The app supports slow page-by-page scanning, verifies each identified card with the user, and stores confirmed cards with quantity in a local Room collection.

### Implementation Status — Updated 2026-07-21

**Pipeline: Functional end-to-end.** All stages produce real data. No placeholder stubs remain. OCR diagnostic instrumentation active for set code/collector number investigation.

```
CameraX (device-dependent resolution, KEEP_ONLY_LATEST)
  ↓ ImageProxy.toBitmap() — ~15–30ms/frame
CardFrameAnalyzer
  ↓ Bitmap delivered to detection
CardDetector (edge-based, adaptive 4× downscale, aspect 0.50–0.90, area 1.5–60%)
  ↓ CardRegion bounding boxes
CardTracker (center-distance, frame-calibrated threshold, 2-frame stability)
  ↓ Expand 20% → Stable card bitmap (includes name + collector regions)
OcrPipeline → CardOcrProcessor (ML Kit await(), spatial 20% name extraction)
  ↓ extractSetCode (3 strategies) + extractCollectorNumber (fraction + standalone)
  ↓ DetectedCardText {name, setCode, collectorNumber, confidence}
  ↓ If confidence < 0.6: region fallback (expansion-aware crop coordinates)
ScryfallRepositoryResilience (local-first: Room → cache → identity → fuzzy → search)
  ↓ List<ScryfallCard>
FuzzyCardMatcher (Levenshtein, 60/20/20 weighting, single-candidate preservation)
  ↓ Ranked CardMatchCandidate list
VerificationScreen (card image, OCR results, quantity entry)
  ↓ User confirms
saveCardToCollection() (validates scryfallId, deduplicates, increments quantity)
  ↓
Room Database (UNIQUE index on scryfallId, numeric collector sort)
  ↓
CollectionScreen (grid, search, filter by set, statistics)
```

**Build:** `BUILD SUCCESSFUL` — 0 errors
**Tests:** 74 unit tests, 0 failures, 10 skipped (require Android device)
**APK:** `app/build/outputs/apk/debug/app-debug.apk` generates cleanly

### Technology Stack
- **Language:** Kotlin 1.9.24
- **UI:** Jetpack Compose + Material3 (Compose BOM 2024.06.00)
- **Camera:** CameraX 1.4.2
- **OCR:** Google ML Kit Text Recognition (Play Services delivery)
- **Database:** Room 2.6.1 with Flow-based reactive queries
- **Network:** Retrofit 2.9.0 + OkHttp 4.11.0 → Scryfall API
- **Image Loading:** Coil 2.5.0
- **Coroutines:** kotlinx-coroutines 1.7.3 + play-services bridge
- **Build:** AGP 8.2.2, compileSdk 34, minSdk 24, targetSdk 34

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
   - `camera/CameraPreviewManager.kt`: CameraX lifecycle management, preview binding, ImageAnalysis with STRATEGY_KEEP_ONLY_LATEST backpressure, single-threaded executor with lifecycle cleanup
   - `analysis/CardFrameAnalyzer.kt`: `imageProxy.toBitmap()` conversion with target rotation

2. **Detection Pipeline** (✓ Complete)
   - `detection/CardDetector.kt`: Edge-based detection (Sobel gradient), adaptive 4× downscale, flood-fill connected components, aspect-ratio (0.50–0.90) & area (1.5–60%) filtering
   - `detection/CardTracker.kt`: Frame-to-frame ID assignment, frame-calibrated center-distance threshold, 2-frame stability enforcement, 30-second stale track cleanup
   - `detection/DetectionPipeline.kt`: Orchestration layer, 20% bounding box expansion, onCardReady callback (owned by MainActivity), duplicate prevention via processedCards Set

3. **Data Models** (✓ Complete)
   - `model/CardModels.kt`: DetectedCardText (OCR output), ScryfallCard (API model), ScannedCard (Room entity), CardMatchCandidate (fuzzy result), CardVerification (user action state)

4. **Optical Character Recognition** (✓ Complete)
   - `ocr/CardOcrProcessor.kt`: ML Kit Text Recognition, spatial bounding box name extraction (20% threshold), 3-strategy set code extraction (legacy/modern/standalone), fraction-based collector number, confidence scoring with diagnostic instrumentation
   - `ocr/OcrPreprocessor.kt`: Pass-through preprocessing, expansion-aware region extraction (accounts for 20% bounding box padding from detection)
   - `ocr/OcrPipeline.kt`: Full pipeline orchestration, fallback to region-based OCR on low confidence (<0.6), ifEmpty preference for set code combination

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
   - `build.gradle.kts`: 40+ dependencies (CameraX 1.4.2, ML Kit Play Services OCR, Room, Retrofit, Coil, Compose BOM, Coroutines, test frameworks)
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

## Deployment & Testing

**Status:** ✓ **READY FOR REAL-WORLD TESTING**

The application is fully implemented with all 13 core components, complete error handling, network resilience, and offline-first caching. All 26 Kotlin source files are compiled without errors.

### Pre-Launch Resources
1. **[DEPLOYMENT.md](DEPLOYMENT.md)** — Complete build and deployment guide
   - Build instructions (Android Studio or Gradle CLI)
   - Step-by-step deployment to Samsung Galaxy S23
   - 5 comprehensive real-world testing scenarios
   - Performance baseline measurements
   - Troubleshooting guide for common issues
   - Logcat debugging commands

2. **[PRELAUNCH_CHECKLIST.md](PRELAUNCH_CHECKLIST.md)** — Pre-deployment verification
   - Build environment verification (JDK, SDK, NDK, Gradle)
   - Source code and dependency verification (26 files, 40+ deps)
   - Configuration verification (Manifest, Gradle, Settings)
   - Component initialization verification
   - Testing status (58 integration test cases)
   - Device preparation (Samsung Galaxy S23)
   - Runtime verification (first launch)
   - Critical success criteria

3. **[Build-Apk.ps1](Build-Apk.ps1)** — Windows build script
   - Stops old daemons
   - Sets `JAVA_HOME` to Android Studio JBR
   - Runs the project wrapper clean/build flow
   - Checks for the debug APK after build

### Quick Start Deployment

1. **Build APK on Windows:**
   ```powershell
   Set-Location "D:\Workspace\CS898BA\CalebGriffin-CS898BA-FinalProject"
   .\Build-Apk.ps1
   ```

   Or, run the wrapper directly:
   ```powershell
   .\gradlew.bat --stop
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   $env:Path="$env:JAVA_HOME\bin;" + $env:Path
   .\gradlew.bat clean
   .\gradlew.bat assembleDebug
   ```

2. **Install on Device:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Launch App:**
   ```bash
   adb shell am start -n com.mtgscanner/.MainActivity
   ```

4. **Monitor Logs:**
   ```bash
   adb logcat com.mtgscanner:V
   ```

### Testing Scenarios Included
- ✓ Single card detection and cataloging
- ✓ Multiple cards (9-12 card binder page)
- ✓ Offline mode with cache fallback
- ✓ Network resilience and retry logic
- ✓ Low OCR confidence warnings
- ✓ Performance baseline measurements
- ✓ Error state handling
- ✓ Collection browsing and search

### Build Notes
- Build the app with `Build-Apk.ps1` or `./gradlew.bat`, not the system `gradle` command.
- Use Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`) for builds.
- The APK contains native ML Kit libraries, so test on 16 KB page-size devices before release.
- For 16 KB page-size installs, the app uses compressed native library packaging (`useLegacyPackaging = true`) because current native prebuilts are not 16 KB aligned.
- Compose dependencies are aligned through the Compose BOM to avoid runtime method mismatches during startup.
- Unit tests use `testOptions { unitTests.isReturnDefaultValues = true }` to allow `android.util.Log` calls in production code without crashing JVM tests.

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed testing procedures.
