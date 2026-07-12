# Project File Inventory

## Documentation Files

| File | Purpose | Status |
|------|---------|--------|
| [README.md](README.md) | Project overview, architecture, deployment status | ✓ Updated |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Build & deployment guide, testing scenarios, troubleshooting | ✓ Created |
| [PRELAUNCH_CHECKLIST.md](PRELAUNCH_CHECKLIST.md) | Pre-deployment verification checklist | ✓ Created |
| [AI_Log.md](AI_Log.md) | Complete development history with timestamps | ✓ Updated |
| [PROJECT_FILE_INVENTORY.md](PROJECT_FILE_INVENTORY.md) | This file — complete project structure | ✓ This file |

---

## Source Code Files (26 Kotlin files)

### Core Application (1 file)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| MainActivity.kt | `com.mtgscanner` | 370 | App entry point, component initialization, permission handling, detection pipeline orchestration |

### Camera Layer (1 file)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| CameraPreviewManager.kt | `camera` | 180 | CameraX lifecycle management, preview binding, ImageAnalysis setup |
| CardFrameAnalyzer.kt | `analysis` | 100 | YUV→Bitmap conversion, frame rotation handling |

### Detection Pipeline (3 files)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| CardDetector.kt | `detection` | 150 | Otsu thresholding, morphological operations, contour detection |
| CardTracker.kt | `detection` | 120 | Frame-to-frame ID assignment, 3-frame stability, stale track cleanup |
| DetectionPipeline.kt | `detection` | 100 | Orchestration, duplicate prevention, OCR callback |

### OCR Layer (3 files)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| CardOcrProcessor.kt | `ocr` | 140 | ML Kit text recognition, regex field extraction, confidence scoring |
| OcrPreprocessor.kt | `ocr` | 130 | CLAHE enhancement, Gaussian blur, sharpening, region extraction |
| OcrPipeline.kt | `ocr` | 110 | Preprocessing orchestration, fallback on low confidence |

### Fuzzy Matching (1 file)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| FuzzyCardMatcher.kt | `matching` | 80 | Levenshtein distance, weighted scoring (60/20/20) |

### Data & Persistence (2 files)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| CardModels.kt | `model` | 120 | Data classes for OCR, Scryfall, local storage, verification |
| ScannedCardDatabase.kt | `data` | 160 | Room entity, DAO, CRUD, search, statistics |

### Network Layer (6 files)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| ScryfallModels.kt | `network` | 100 | API response DTOs (ScryfallCard, ScryfallSearchResponse, etc.) |
| ScryfallApiService.kt | `network` | 80 | Retrofit interface (7 endpoints) |
| ScryfallApiClient.kt | `network` | 150 | Async API client with suspendable coroutines |
| ScryfallRepository.kt | `network` | 140 | Repository pattern, cache + network |
| NetworkResilience.kt | `network` | 120 | NetworkStateManager, RetryPolicy, retryableCall() |
| NetworkCacheManager.kt | `network` | 130 | SharedPreferences cache, search, preload, stats |
| ScryfallRepositoryResilience.kt | `network` | 150 | Resilient wrapper with Result<T>, fallback chain |

### UI Layer (7 files)
| File | Package | Lines | Purpose |
|------|---------|-------|---------|
| CameraScreen.kt | `ui` | 180 | Live preview, detection overlay, frame counter |
| VerificationScreen.kt | `ui` | 300 | OCR results, candidate ranking, quantity input, error UI |
| CollectionScreen.kt | `ui` | 200 | Grid layout, search, filter by set, sort, statistics |
| AppNavigator.kt | `ui` | 80 | State machine (MAIN ↔ CAMERA ↔ VERIFICATION ↔ COLLECTION) |
| AppRoot.kt | `ui` | 60 | Navigation routing, MainMenuScreen |
| ErrorHandling.kt | `ui` | 180 | 6 Material3 error composables (ErrorSnackbar, OfflineNotice, etc.) |
| Theme.kt | `ui/theme` | 100 | Material3 color scheme and styling |

**Total Source Code: ~3,500 lines of production Kotlin**

---

## Configuration Files (4 files)

| File | Purpose | Status |
|------|---------|--------|
| build.gradle.kts | Gradle build config, 40+ dependencies, compileSdk 34, minSdk 24 | ✓ Complete |
| settings.gradle.kts | Gradle settings, repository config, project structure | ✓ Complete |
| AndroidManifest.xml | Permissions, hardware features, activity config, orientation lock | ✓ Complete |
| proguard-rules.pro | ProGuard obfuscation rules (if needed for release build) | ✓ Available |

---

## Resource Files (3 files)

| File | Purpose | Status |
|------|---------|--------|
| res/values/strings.xml | App strings (errors, permissions, actions, UI labels) | ✓ Complete |
| res/values/colors.xml | Material3 color palette (primary, secondary, tertiary, error) | ✓ Complete |
| res/values/themes.xml | MTGScanner theme (typography, button styles, toolbar) | ✓ Complete |

---

## Test Files (6 files, 58 test cases)

### Unit Tests (3 files)
| File | Tests | Purpose |
|------|-------|---------|
| DetectionPipelineIntegrationTest.kt | 7 | Single/multiple card detection, tracking, stability, deduplication |
| OcrPipelineIntegrationTest.kt | 10 | Preprocessing, field extraction, confidence, rotation robustness |
| FuzzyMatchingIntegrationTest.kt | 9 | Perfect matches, noise tolerance, weighted scoring, ranking |

### Instrumented Tests (3 files)
| File | Tests | Purpose |
|------|-------|---------|
| ScannedCardDatabaseIntegrationTest.kt | 11 | CRUD ops, search, filter, uniqueness, stats, performance (150+ cards) |
| EndToEndIntegrationTest.kt | 6 | Full workflows (single card, multiple, rejection, quantity, browsing) |
| NavigationIntegrationTest.kt | 15 | State transitions, data passing, button interactions, screen visibility |

**Total Tests: 58 integration test cases, comprehensive coverage of all major components**

---

## Project Structure

```
CalebGriffin-CS898BA-FinalProject/
├── app/
│   ├── src/main/java/com/mtgscanner/
│   │   ├── MainActivity.kt
│   │   ├── camera/
│   │   │   ├── CameraPreviewManager.kt
│   │   │   └── CardFrameAnalyzer.kt
│   │   ├── detection/
│   │   │   ├── CardDetector.kt
│   │   │   ├── CardTracker.kt
│   │   │   └── DetectionPipeline.kt
│   │   ├── ocr/
│   │   │   ├── CardOcrProcessor.kt
│   │   │   ├── OcrPreprocessor.kt
│   │   │   └── OcrPipeline.kt
│   │   ├── matching/
│   │   │   └── FuzzyCardMatcher.kt
│   │   ├── data/
│   │   │   └── ScannedCardDatabase.kt
│   │   ├── model/
│   │   │   └── CardModels.kt
│   │   ├── network/
│   │   │   ├── ScryfallModels.kt
│   │   │   ├── ScryfallApiService.kt
│   │   │   ├── ScryfallApiClient.kt
│   │   │   ├── ScryfallRepository.kt
│   │   │   ├── NetworkResilience.kt
│   │   │   ├── NetworkCacheManager.kt
│   │   │   └── ScryfallRepositoryResilience.kt
│   │   └── ui/
│   │       ├── CameraScreen.kt
│   │       ├── VerificationScreen.kt
│   │       ├── CollectionScreen.kt
│   │       ├── AppNavigator.kt
│   │       ├── AppRoot.kt
│   │       ├── ErrorHandling.kt
│   │       └── theme/
│   │           └── Theme.kt
│   ├── src/main/res/
│   │   ├── values/strings.xml
│   │   ├── values/colors.xml
│   │   └── values/themes.xml
│   ├── src/test/java/com/mtgscanner/
│   │   ├── detection/DetectionPipelineIntegrationTest.kt
│   │   ├── ocr/OcrPipelineIntegrationTest.kt
│   │   └── matching/FuzzyMatchingIntegrationTest.kt
│   ├── src/androidTest/java/com/mtgscanner/
│   │   ├── data/ScannedCardDatabaseIntegrationTest.kt
│   │   ├── EndToEndIntegrationTest.kt
│   │   └── ui/NavigationIntegrationTest.kt
│   ├── src/main/AndroidManifest.xml
│   └── build.gradle.kts
├── settings.gradle.kts
├── build.gradle.kts (root, if present)
├── README.md
├── DEPLOYMENT.md
├── PRELAUNCH_CHECKLIST.md
├── AI_Log.md
├── PROJECT_FILE_INVENTORY.md (this file)
├── literature_review/ (original reference materials)
└── .git/ (version control)
```

---

## Component Integration Map

```
MainActivity (Main orchestrator)
│
├─ CameraPreviewManager
│  └─ CardFrameAnalyzer (YUV→Bitmap)
│     └─ DetectionPipeline
│        ├─ CardDetector (Segmentation)
│        ├─ CardTracker (Tracking + ID assignment)
│        └─ OcrPipeline (on cardReady callback)
│           ├─ OcrPreprocessor (Enhancement)
│           └─ CardOcrProcessor (ML Kit + Regex)
│              └─ ScryfallRepositoryResilience (findCardCandidatesResilient)
│                 ├─ NetworkStateManager (Check online/offline)
│                 ├─ RetryPolicy (Exponential backoff)
│                 └─ NetworkCacheManager (7-day TTL cache)
│                    └─ ScryfallApiClient (API calls)
│                       └─ FuzzyCardMatcher (Levenshtein scoring)
│                          └─ VerificationScreen (User confirmation)
│                             └─ ScannedCardDatabase (Room persistence)
│                                └─ CollectionScreen (Browse + Search)
│
├─ NetworkConnectivityReceiver (BroadcastReceiver) [TODO]
│  └─ NetworkStateManager (Connectivity changes)
│
└─ UI Layer (Jetpack Compose)
   ├─ AppNavigator (State machine)
   ├─ AppRoot (Navigation routing)
   ├─ MainMenuScreen (Landing page)
   ├─ CameraScreen (Live preview)
   ├─ VerificationScreen (User confirmation)
   │  ├─ ErrorSnackbar (on error)
   │  ├─ OfflineNotice (if cache hit)
   │  └─ LowConfidenceWarning (if OCR < 60%)
   └─ CollectionScreen (Browse collection)
```

---

## Dependencies Summary

### Build Tools
- Gradle: 7.0+
- Android Gradle Plugin: 8.0+
- Kotlin: 1.9.0+

### Core Android
- androidx.appcompat:appcompat
- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.activity:activity-compose

### Camera
- androidx.camera:camera-core: 1.3.0
- androidx.camera:camera-camera2: 1.3.0
- androidx.camera:camera-lifecycle: 1.3.0

### ML Kit
- com.google.mlkit:vision-common: 16.0.0
- com.google.mlkit:text-recognition: 16.0.0

### Image Processing
- opencv-android: 4.8.1

### Database
- androidx.room:room-runtime: 2.6.1
- androidx.room:room-ktx: 2.6.1
- androidx.room:room-compiler: 2.6.1

### Networking
- com.squareup.retrofit2:retrofit: 2.9.0
- com.squareup.retrofit2:converter-gson: 2.9.0
- com.google.code.gson:gson: 2.10.1

### Image Loading
- io.coil-kt:coil-compose: 2.5.0

### Jetpack Compose
- androidx.compose.ui:ui: 1.6.0
- androidx.compose.ui:ui-graphics: 1.6.0
- androidx.compose.ui:ui-tooling-preview: 1.6.0
- androidx.compose.material3:material3: 1.1.2
- androidx.activity:activity-compose: 1.7.2

### Coroutines
- org.jetbrains.kotlinx:kotlinx-coroutines-core: 1.7.3
- org.jetbrains.kotlinx:kotlinx-coroutines-android: 1.7.3

### Testing
- junit:junit: 4.13.2
- androidx.test.ext:junit: 1.1.5
- androidx.test.espresso:espresso-core: 3.5.1
- org.mockito:mockito-core: 5.2.0
- org.mockito.kotlin:mockito-kotlin: 5.1.0

**Total: 40+ direct dependencies, hundreds of transitive dependencies**

---

## Key Metrics

| Metric | Value |
|--------|-------|
| Source Files | 26 Kotlin files |
| Lines of Code | ~3,500 production code |
| Test Cases | 58 integration tests |
| Packages | 9 packages (camera, analysis, detection, ocr, matching, data, model, network, ui) |
| Dependencies | 40+ direct dependencies |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14.0) |
| Compile SDK | 34 |
| Kotlin Version | 1.9.0+ |
| Java Version | 1.8 |
| Compose Version | 1.6.0 |
| Material3 Version | 1.1.2 |

---

## Development History

Total development time: ~2 hours
- Phases: Scaffolding → Components → Testing → Configuration → Resilience → Integration → Deployment
- Status: **Ready for Real-World Testing**
- Last Updated: 2026-07-12 11:51 CST

---

## Next Steps Post-Testing

1. **Performance Profiling** — Android Profiler analysis
2. **Perspective Correction** — Homography transform for skewed cards
3. **Offline Preload** — Pre-populate cache with common sets
4. **UI Polish** — Based on real-world user feedback
5. **Release Build** — ProGuard obfuscation + signing
6. **Play Store** — If project continues beyond CS898BA

---

Generated: 2026-07-12 11:51 CST
