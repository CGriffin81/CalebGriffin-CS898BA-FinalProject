# MTG Scanner — Architecture & Runtime Behavior
**Updated:** 2026-07-21 (post-runtime investigation)

---

## Pipeline Execution Diagram (Current Implementation)

```
┌─────────────────────────────────────────────────────────────────┐
│  CameraX (device-dependent resolution, KEEP_ONLY_LATEST)        │
│  Delivers ImageProxy on single-threaded executor                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  CardFrameAnalyzer.analyze(image)                                │
│  Thread: CameraPreviewManager executor                           │
│  Action: image.toBitmap() → onFrameReady(bitmap)                 │
│  Timing: ~15–30ms at 2992×2992                                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  DetectionPipeline.processFrame(bitmap)                           │
│  Thread: Same executor (synchronous)                             │
│                                                                   │
│  ┌── CardDetector.detectCards(bitmap) ─────────────────────┐     │
│  │  • Adaptive downscale (4× if >2000px)                   │     │
│  │  • Grayscale → Sobel edges → Dilate → Flood-fill       │     │
│  │  • Filter: area 1.5–60%, aspect 0.50–0.90, fill >25%   │     │
│  │  • Timing: ~30–80ms                                     │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                   │
│  ┌── CardTracker.updateTracks(detections) ─────────────────┐     │
│  │  • Center-distance matching (frame-calibrated threshold) │     │
│  │  • STABILITY_FRAMES = 2 before "ready"                  │     │
│  │  • Timing: <1ms                                          │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                   │
│  IF stable AND not in processedCards:                             │
│    → extractCardImage(bitmap, region)                             │
│    → onCardReady(cardBitmap, trackingId)  ←── OWNED BY MAIN     │
│    → processedCards.add(trackingId)                               │
│                                                                   │
│  onFrameAnalysis(detectionCount)  ←── OWNED BY CameraScreen     │
└──────────────────────────────┬──────────────────────────────────┘
                               │ onCardReady fires
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  MainActivity.setupDetectionPipelineCallback()                    │
│  Thread: lifecycleScope.launch { ... } → Dispatchers.Main        │
│                                                                   │
│  Step 1: ocrPipeline.recognizeCard(cardBitmap, trackingId)       │
│    → OcrPreprocessor (pass-through)                              │
│    → CardOcrProcessor.processCardImage() [Dispatchers.Default]   │
│      → ML Kit textRecognizer.process(image).await()              │
│      → parseCardTextWithBounds() (spatial + regex)               │
│    → If confidence < 0.6: recognizeByRegions() fallback          │
│    Timing: ~80–200ms (single pass), ~240–600ms (with fallback)   │
│                                                                   │
│  Step 2: scryfallRepositoryResilience.findCardCandidatesResilient│
│    → Check Room collection first (~1ms)                          │
│    → Check SharedPreferences cache (~5ms)                        │
│    → Strategy 1: /cards/{set}/{cn} [Dispatchers.IO]              │
│    → Strategy 2: /cards/named?fuzzy= [Dispatchers.IO]            │
│    → Strategy 3: /cards/search?q= [Dispatchers.IO]               │
│    Timing: 0ms (cache hit) to 500ms (network)                    │
│                                                                   │
│  Step 3: fuzzyCardMatcher.matchCard(detectedText, candidates)    │
│    → Levenshtein scoring (60% name, 20% set, 20% collector)     │
│    → ≤3 candidates: all preserved. >3: filter score > 0.5       │
│    Timing: <1ms                                                   │
│                                                                   │
│  Step 4: navigator.navigateToVerification(cardVerification)      │
│    → Compose state update → VerificationScreen renders           │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  VerificationScreen                                               │
│  • Shows OCR results, Scryfall card image, match candidates      │
│  • User confirms → saveCardToCollection() → Room                 │
│  • User rejects/skips → clearProcessedCards() → back to camera   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Callback Ownership (Critical Design Decision)

The pipeline has exactly two callback exit points from `DetectionPipeline`:

| Callback | Owner | Purpose | Thread |
|---|---|---|---|
| `onCardReady` | **MainActivity** (exclusive) | OCR → Scryfall → Matcher → Navigate | Executor → lifecycleScope |
| `onFrameAnalysis` | **CameraScreen** | UI detection count display | Executor (unsafe but tolerated) |

**Rule:** `CameraScreen` MUST NOT set `onCardReady`. This was the root cause of the runtime failure discovered on 2026-07-21.

---

## Observed Runtime Behavior (Device Testing)

### Frame Resolution
The Samsung Galaxy S23 delivers **2992×2992** square frames despite `setTargetResolution(1280, 720)`. CameraX's resolution hint is advisory — the HAL selects the closest supported output which on this device is a square crop.

### Detection Performance
| Metric | Measured |
|---|---|
| Frame resolution | 2992×2992 |
| Internal processing resolution | 748×748 (4× downscale) |
| Detection time per frame | ~30–80ms |
| Effective frame rate into pipeline | ~7–12fps |
| Stability threshold | 2 frames (~150–300ms) |
| Total time to "Card Ready" | ~300–600ms from card entering frame |

### Detection Algorithm
Edge-based detection (not threshold-based). Finds card borders regardless of background brightness. Works on white desks, dark mats, and binder pages.

Key parameters:
- Edge threshold: 50 (rejects surface texture, catches card borders)
- Aspect ratio: 0.50–0.90 (accommodates perspective distortion)
- Minimum area: 1.5% of frame
- Fill ratio: >25% of bounding box
- Dilation: 2 passes for gap closure

---

## Runtime Defects Found (2026-07-21)

### Defect 1: Callback Overwrite (FIXED)
- **Symptom:** "Card Ready" fires but OCR never activates
- **Cause:** `CameraScreen.LaunchedEffect` set `detectionPipeline.onCardReady` to a local callback that passed a `Map` instead of `CardVerification`. AppRoot checked `if (cardData is CardVerification)` — always false.
- **Fix:** Removed CameraScreen's `LaunchedEffect`. `onCardReady` owned exclusively by MainActivity.

### Defect 2: Detection on light backgrounds (FIXED)
- **Symptom:** No detections on white desks/tables
- **Cause:** Original threshold-based detector only found bright-on-dark. 
- **Fix:** Replaced with edge-based detection using Sobel gradient magnitude.

### Defect 3: Tracker threshold too small for high-res frames (FIXED)
- **Symptom:** Tracking IDs changed every frame, stability never reached
- **Cause:** Fixed 50px threshold at 2992×2992 is too tight — normal bounding box jitter > 50px.
- **Fix:** Frame-relative calibration. At 2992×2992, threshold is ~230px.

### Defect 4: Stability too strict at low frame rates (FIXED)
- **Symptom:** Cards must be held extremely still for seconds
- **Cause:** 3-frame requirement at ~7fps = ~430ms minimum. Combined with jitter, often took 2–3 seconds.
- **Fix:** Reduced to 2-frame stability (~200–300ms).

---

## Performance Observations

### Bottlenecks (in order of impact)

1. **Flood-fill on background** (~20–80ms): The largest connected non-edge region is always the background. It processes 300K+ pixels before being filtered by area.
   - **Proposed fix:** Early termination when pixel count exceeds MAX_AREA threshold.

2. **Bitmap allocation at full resolution** (~15–30ms): 36MB ARGB bitmap created from `toBitmap()` before downscale.
   - **Cannot fix without camera changes** (HAL delivers 2992×2992 regardless of hint).

3. **OCR fallback doubles ML Kit calls** (~160–400ms extra): When confidence < 0.6, two additional ML Kit calls run sequentially.
   - **Proposed fix:** Run region OCR concurrently with `async/await`.

### What Does NOT Need Optimization

- Tracking: <1ms per frame
- Fuzzy matching: <1ms per card
- Navigation state updates: <1ms
- Room queries: <5ms
- Retrofit setup: one-time cost

---

## Architecture Principles (Lessons Learned)

1. **Single callback owner.** Any lambda field on a shared object (`DetectionPipeline`) must have exactly one setter. If two components set the same field, the last writer wins silently.

2. **Frame processing is the bottleneck budget.** With `KEEP_ONLY_LATEST`, the pipeline processes at 1/frame-time fps. All per-frame work must complete in <100ms to maintain responsive detection.

3. **CameraX resolution is advisory, not mandatory.** The target device ignored `setTargetResolution(1280, 720)` and delivered 2992×2992. Detection code must handle arbitrary resolutions gracefully.

4. **Stability threshold must scale with frame rate.** Higher resolution → slower processing → lower fps → longer wall-clock time per stability frame. The threshold must account for this.

5. **OCR is a one-shot operation, not per-frame.** After `onCardReady` fires, the pipeline should ideally stop processing frames until OCR completes. Currently it continues detecting (which wastes CPU but doesn't cause bugs since `processedCards` prevents re-fire).

---

## Current Test Coverage

| Test File | Tests | Coverage |
|---|---|---|
| `PipelineHandoffTest.kt` | 10 | Callback ownership, stability, tracking, deduplication, navigator chain |
| `DetectionPipelineIntegrationTest.kt` | 15 | Tracker logic, multi-card, center-distance, stability timing |
| `OcrPipelineIntegrationTest.kt` | 36 | Text parsing, confidence scoring, regex patterns, set codes |
| `FuzzyMatchingIntegrationTest.kt` | 13 | Levenshtein, scoring, filtering, single-candidate preservation |
| **Total** | **74** | **0 failures, 10 skipped (require Android runtime)** |
