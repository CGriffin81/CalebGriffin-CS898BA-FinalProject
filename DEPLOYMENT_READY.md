# MTG Scanner — Deployment Status

**Updated:** 2026-07-21  
**Status:** ✓ Pipeline functional end-to-end  
**Target:** Any Android 7.0+ device with camera (tested on Samsung Galaxy S23)

---

## What Changed (2026-07-21 Reliability Implementation)

The 2026-07-12 prototype had 15+ critical defects preventing any real-world card identification. A 6-phase reliability implementation resolved all blocking issues:

| Phase | What was fixed |
|-------|----------------|
| **Phase 1** | OCR async bridge, frame routing, card detection, database write |
| **Phase 2** | Spatial name extraction, modern set codes, collector number accuracy, confidence scoring, matcher preservation |
| **Phase 3** | `toBitmap()` conversion, 720p target resolution, executor lifecycle |
| **Phase 4** | DFC card_faces support, set code normalization, local-first lookup |
| **Phase 5** | UNIQUE index + migration, COALESCE SUM, numeric sort |
| **Phase 6** | Test suite repair (64 tests, 0 failures) |

---

## Current State

### Pipeline (all stages produce real data)

```
CameraX 1280×720 → toBitmap() ~8ms
  → CardDetector (flood-fill, aspect 0.55–0.85, area >2%)
  → CardTracker (center-distance, 3-frame stability)
  → CardOcrProcessor (ML Kit await(), spatial bounding box)
  → ScryfallRepositoryResilience (Room → cache → identity → fuzzy → search)
  → FuzzyCardMatcher (Levenshtein, single-candidate preservation)
  → VerificationScreen (image, candidates, quantity)
  → saveCardToCollection() (validate, deduplicate, persist)
  → Room v2 (UNIQUE scryfallId, numeric collector sort)
```

### Build Artifacts

| Artifact | Status |
|----------|--------|
| `compileDebugKotlin` | ✓ 0 errors, 2 deprecation warnings |
| `testDebugUnitTest` | ✓ 64 tests, 0 failures, 10 skipped |
| `assembleDebug` | ✓ `app-debug.apk` generated |
| `compileDebugAndroidTestKotlin` | ✓ Compiles cleanly |

### Source Files: 25 production Kotlin

```
analysis/CardFrameAnalyzer.kt      — ImageProxy.toBitmap(), frame delivery
camera/CameraPreviewManager.kt     — CameraX 720p, target rotation, executor lifecycle
detection/CardDetector.kt           — Flood-fill detection, aspect ratio filtering
detection/CardTracker.kt            — Center-distance matching, 3-frame stability
detection/DetectionPipeline.kt      — Orchestration, duplicate prevention
ocr/CardOcrProcessor.kt             — ML Kit await(), spatial + text extraction
ocr/OcrPreprocessor.kt              — Region cropping (3–10%, 52–61%, 90–98%)
ocr/OcrPipeline.kt                  — Full → region fallback on low confidence
matching/FuzzyCardMatcher.kt        — Levenshtein, P2-07 preservation
model/CardModels.kt                 — DetectedCardText, ScryfallCard, ScannedCard, etc.
data/ScannedCardDatabase.kt         — Room v2, UNIQUE index, migrations, DAO
network/ScryfallModels.kt           — DTOs (card_faces, oracle_text)
network/ScryfallApiService.kt       — Retrofit (@Path fixed)
network/ScryfallApiClient.kt        — Async client, DFC resolution, lowercase set codes
network/ScryfallRepositoryResilience.kt — Local-first lookup, retry, fallback chain
network/NetworkResilience.kt        — NetworkStateManager, RetryPolicy, retryableCall
network/NetworkCacheManager.kt      — SharedPreferences cache, 7-day TTL
ui/CameraScreen.kt                  — Preview + pipeline wiring
ui/VerificationScreen.kt            — Candidate display, quantity, confirm/reject
ui/CollectionScreen.kt              — Grid, search, filter, stats
ui/AppNavigator.kt                  — State machine
ui/AppRoot.kt                       — Navigation + saveCardToCollection()
ui/ErrorHandling.kt                 — 6 Material3 error components
ui/theme/Theme.kt                   — Material3 color scheme
MainActivity.kt                     — Orchestration, permissions, lifecycle
```

---

## Quick Deploy

```powershell
Set-Location "D:\Workspace\CS898BA\CalebGriffin-CS898BA-FinalProject"
.\Build-Apk.ps1 -Install -Launch
```

Monitor scanning:
```bash
adb logcat -s CardDetector:D DetectionPipeline:D CardOcrProcessor:D ScryfallRepositoryResilience:D
```

---

## Known Limitations

1. **Detection requires contrast** — bright cards on dark backgrounds work best. Cards on white paper may not have sufficient luminance difference.
2. **No image preprocessing** — `OcrPreprocessor.preprocessForOcr()` is a pass-through. Contrast enhancement (CLAHE) would improve low-light OCR.
3. **No perspective correction** — cards at angles produce keystoned crops.
4. **SharedPreferences cache scales poorly** — at 500+ cached cards, search becomes slow. Migration to Room-based cache is tracked.
5. **No ViewModel** — all state lives on the Activity. Configuration changes re-initialize components.

---

## Next Steps After Device Testing

1. Tune detection threshold based on real binder backgrounds
2. Implement OcrPreprocessor contrast enhancement
3. Add perspective correction (4-point homography)
4. Migrate NetworkCacheManager to Room for O(log N) lookup
5. Add ViewModel layer for proper lifecycle management
6. Profile memory and optimize Bitmap recycling
