# Card Anatomy Engine — Architecture Design

**Author:** Lead Systems Engineer  
**Date:** 2026-07-21  
**Status:** Implemented (Phases A, D, E, F complete; B, C pending)

---

## 1. Problem Statement

The current OCR-first pipeline treats the card as a flat image and relies on ML Kit to
recognize all text, then post-hoc regex parsing to assign semantic meaning to recognized
lines. This fails because:

- The crop often excludes the collector line (bottom 5% of the card)
- ML Kit returns text in an unpredictable order
- Power/toughness fractions are confused with collector numbers
- Type lines are confused with card names
- Confidence scoring cannot distinguish "correct extraction" from "populated fields"
- Region-based fallback crops empty padding instead of card content

The fundamental issue: **OCR does not understand card layout.** It sees text; it does
not know where the name bar, type line, or collector info should be.

---

## 2. Proposed Architecture: Card Anatomy Engine

The Card Anatomy Engine replaces the "OCR everything then parse" approach with
**"understand layout first, then OCR each region with semantic context."**

### New Pipeline

```
Camera (CameraX)
  ↓ ImageProxy.toBitmap()
CardFrameAnalyzer
  ↓ Bitmap
DetectionPipeline (PRESERVED — no changes)
  ↓ onCardReady(cardBitmap, trackingId)
┌─────────────────────────────────────────────────────────┐
│  CARD ANATOMY ENGINE (new)                               │
│                                                          │
│  Stage 1: PerspectiveCorrector                           │
│    → Find 4 corners → Homography → Rectified card       │
│                                                          │
│  Stage 2: FrameClassifier                                │
│    → Classify card frame type (Modern, Classic, Full-    │
│      Art, Borderless, Split, DFC, Saga, Planeswalker)    │
│    → Returns FrameLayout with region proportions         │
│                                                          │
│  Stage 3: AnatomyDetector                                │
│    → Given FrameLayout, locate actual region boundaries  │
│    → Uses edge detection within expected zones           │
│    → Returns CardAnatomy (precise region Rects)          │
│                                                          │
│  Stage 4: RegionExtractor                                │
│    → Crop each region from rectified image               │
│    → Apply region-specific preprocessing                 │
│    → Returns CardRegionImages                            │
│                                                          │
│  Stage 5: SpecializedOcr                                 │
│    → Name region: large font, title-case expectation     │
│    → Type region: known vocabulary constraint            │
│    → Collector region: small font, alphanumeric          │
│    → Mana cost: symbol detection (not text OCR)          │
│    → Returns RawFieldTexts                               │
│                                                          │
│  Stage 6: FieldValidator                                 │
│    → Validate each field against known MTG constraints   │
│    → Name: length, character set, title-case             │
│    → Set: 2-5 chars, in known set list or plausible      │
│    → Collector: 1-4 digits, optional letter suffix       │
│    → Type: matches MTG type vocabulary                   │
│    → Returns ValidatedFields + per-field confidence      │
│                                                          │
│  Stage 7: EvidenceScorer                                 │
│    → Combine all field confidences into overall score    │
│    → Weight by field importance for Scryfall lookup      │
│    → Determine lookup strategy based on available fields │
│    → Returns ScoredEvidence                              │
│                                                          │
└─────────────────────────────────────────────────────────┘
  ↓ ScoredEvidence (replaces DetectedCardText)
ScryfallRepositoryResilience (PRESERVED)
  ↓ List<ScryfallCard>
FuzzyCardMatcher (PRESERVED)
  ↓ Ranked CardMatchCandidate list
VerificationScreen (PRESERVED)
  ↓ User confirms
Room Database (PRESERVED)
```

---

## 3. New Classes & Interfaces

### 3.1 Package: `com.mtgscanner.anatomy`

| Class | Responsibility |
|---|---|
| `CardAnatomyEngine` | Orchestrator — coordinates all stages, replaces OcrPipeline |
| `PerspectiveCorrector` | Detects card corners, computes homography, produces rectified bitmap |
| `FrameClassifier` | Classifies card frame type from visual features |
| `AnatomyDetector` | Locates precise region boundaries within classified frame |
| `RegionExtractor` | Crops and preprocesses individual regions |
| `SpecializedOcr` | Runs ML Kit with region-specific configuration |
| `FieldValidator` | Validates extracted text against MTG domain knowledge |
| `EvidenceScorer` | Produces final confidence scores and lookup strategy |

### 3.2 Package: `com.mtgscanner.anatomy.model`

| Data Class | Purpose |
|---|---|
| `FrameType` | Enum: MODERN, CLASSIC, FULL_ART, BORDERLESS, SPLIT, DFC_FRONT, DFC_BACK, SAGA, PLANESWALKER |
| `FrameLayout` | Maps FrameType → expected region proportions (normalized 0–1) |
| `CardAnatomy` | Detected region boundaries: nameRect, manaCostRect, artRect, typeRect, textRect, ptRect, collectorRect |
| `CardRegionImages` | Cropped bitmaps for each region |
| `RawFieldTexts` | Raw OCR output per region (name, type, collector, pt, manaCost) |
| `ValidatedField` | Single field: text, confidence, validationNotes |
| `ValidatedFields` | All validated fields together |
| `ScoredEvidence` | Final output: cardName, setCode, collectorNumber, confidence, lookupStrategy, rawEvidence |
| `LookupStrategy` | Enum: IDENTITY (set+cn), FUZZY_NAME, SEARCH, NAME_ONLY |

### 3.3 Modifications to Existing Classes

| Existing Class | Change |
|---|---|
| `DetectionPipeline` | **No change.** onCardReady still fires with (Bitmap, Int). |
| `MainActivity` | Replace `ocrPipeline.recognizeCard()` with `cardAnatomyEngine.analyze()` |
| `CardModels.kt` | Add `ScoredEvidence` as alternative to `DetectedCardText`, or extend `DetectedCardText` with anatomy fields |
| `OcrPipeline` | **Deprecated.** Replaced by `CardAnatomyEngine`. Keep for fallback during transition. |
| `OcrPreprocessor` | **Absorbed** into `RegionExtractor` with better preprocessing per region |
| `CardOcrProcessor` | **Absorbed** into `SpecializedOcr`. ML Kit usage moves here. |
| `CardDetector` | Add `findCorners()` method for perspective correction input |
| `ScryfallRepositoryResilience` | Accept `ScoredEvidence` as input (adapter method) |
| `FuzzyCardMatcher` | Accept `ScoredEvidence` or adapt via `DetectedCardText` conversion |

---

## 4. Interface Contracts

```kotlin
// ── CardAnatomyEngine ──
interface CardAnatomyEngine {
    suspend fun analyze(cardBitmap: Bitmap, trackingId: Int): ScoredEvidence
}

// ── PerspectiveCorrector ──
interface PerspectiveCorrector {
    fun rectify(cardBitmap: Bitmap): RectificationResult
}
data class RectificationResult(
    val rectifiedBitmap: Bitmap,
    val confidence: Float,  // 0-1, how confident are we in the corners
    val corners: List<Point>?  // detected corners, null if rectification skipped
)

// ── FrameClassifier ──
interface FrameClassifier {
    fun classify(rectifiedBitmap: Bitmap): FrameClassification
}
data class FrameClassification(
    val frameType: FrameType,
    val confidence: Float,
    val layout: FrameLayout
)

// ── AnatomyDetector ──
interface AnatomyDetector {
    fun detect(rectifiedBitmap: Bitmap, layout: FrameLayout): CardAnatomy
}

// ── RegionExtractor ──
interface RegionExtractor {
    fun extract(rectifiedBitmap: Bitmap, anatomy: CardAnatomy): CardRegionImages
}

// ── SpecializedOcr ──
interface SpecializedOcr {
    suspend fun recognizeRegions(regions: CardRegionImages): RawFieldTexts
}

// ── FieldValidator ──
interface FieldValidator {
    fun validate(rawTexts: RawFieldTexts, frameType: FrameType): ValidatedFields
}

// ── EvidenceScorer ──
interface EvidenceScorer {
    fun score(validated: ValidatedFields): ScoredEvidence
}
```

---

## 5. Compatibility Strategy

### What stays the same:
- `DetectionPipeline` — unchanged, still delivers (Bitmap, Int)
- `CardTracker` — unchanged
- `CardDetector` — extended (new method), not rewritten
- `ScryfallRepositoryResilience` — unchanged (adapter converts ScoredEvidence → DetectedCardText)
- `FuzzyCardMatcher` — unchanged (same adapter)
- All UI code — unchanged
- Room database — unchanged
- `MainActivity` callback structure — unchanged (only the body of the callback changes)

### What gets replaced:
- `OcrPipeline.recognizeCard()` → `CardAnatomyEngine.analyze()`
- `CardOcrProcessor` → split into `SpecializedOcr` + `FieldValidator`
- `OcrPreprocessor` → absorbed into `RegionExtractor`

### Adapter for backward compatibility:
```kotlin
// In MainActivity callback, during transition period:
val evidence: ScoredEvidence = cardAnatomyEngine.analyze(cardBitmap, trackingId)

// Convert to DetectedCardText for existing downstream code
val detectedText = DetectedCardText(
    trackingId = trackingId,
    cardName = evidence.cardName,
    setCode = evidence.setCode,
    collectorNumber = evidence.collectorNumber,
    ocrConfidence = evidence.overallConfidence,
    rawOcrText = evidence.rawEvidence
)
```

---

## 6. Frame Type Layouts (Reference Data)

```
MODERN (2015+ frames — M15 onwards):
  Name:       y=3.5%–7.5%, x=5%–82%
  ManaCost:   y=3.5%–7.5%, x=82%–95%
  Art:        y=8%–50%,    x=5%–95%
  Type:       y=50%–55%,   x=5%–95%
  Text:       y=55%–88%,   x=5%–95%
  P/T:        y=88%–93%,   x=72%–93%
  Collector:  y=93%–97%,   x=5%–50%

CLASSIC (pre-2015 — 8th edition to M14):
  Name:       y=4%–8%,     x=6%–80%
  ManaCost:   y=4%–8%,     x=80%–94%
  Art:        y=10%–51%,   x=6%–94%
  Type:       y=52%–56%,   x=6%–94%
  Text:       y=57%–89%,   x=6%–94%
  P/T:        y=89%–94%,   x=70%–93%
  Collector:  y=95%–98%,   x=6%–50%

FULL_ART (full-art lands, special frames):
  Name:       y=4%–8%,     x=5%–82%
  Art:        y=0%–100%    (background)
  Type:       y=85%–89%,   x=5%–95%
  Collector:  y=95%–98%,   x=5%–50%

BORDERLESS (modern showcase, extended art):
  Name:       y=2%–6%,     x=4%–80%
  Art:        y=0%–100%    (full bleed)
  Type:       y=50%–54%,   x=4%–95%
  Collector:  y=94%–98%,   x=4%–50%
```

---

## 7. Implementation Roadmap

### Phase A: Foundation (No behavior change)

| Task | Agent | Effort | Description |
|---|---|---|---|
| A-01 | Data Engineer | S | Create `anatomy/model/` package with all data classes (FrameType, FrameLayout, CardAnatomy, CardRegionImages, RawFieldTexts, ValidatedField, ValidatedFields, ScoredEvidence, LookupStrategy) |
| A-02 | Lead Systems | S | Define interfaces (CardAnatomyEngine, PerspectiveCorrector, FrameClassifier, AnatomyDetector, RegionExtractor, SpecializedOcr, FieldValidator, EvidenceScorer) |
| A-03 | Platform Engineer | S | Create `CardAnatomyEngineImpl` skeleton that delegates to existing OcrPipeline (adapter pattern — new interface, old behavior) |
| A-04 | Platform Engineer | S | Wire `CardAnatomyEngineImpl.analyze()` into MainActivity callback in place of `ocrPipeline.recognizeCard()`. Verify no behavior change. |
| A-05 | QA Engineer | M | Write interface contract tests for each anatomy stage (input/output shape validation) |

### Phase B: Perspective Correction

| Task | Agent | Effort | Description |
|---|---|---|---|
| B-01 | CV Engineer | M | Implement `PerspectiveCorrector` using Hough line detection on edge image to find 4 dominant lines forming card boundary |
| B-02 | CV Engineer | S | Compute homography matrix from 4 corners → standard card aspect ratio (63mm × 88mm → 2.5:3.5) |
| B-03 | CV Engineer | S | Apply perspective warp to produce rectified bitmap at standard resolution (e.g., 488×680 or proportional) |
| B-04 | CV Engineer | S | Add confidence metric: corner detection quality, line straightness, aspect ratio deviation |
| B-05 | CV Engineer | S | Fallback: if corner detection fails (confidence < 0.3), pass through the expanded crop unchanged |
| B-06 | QA Engineer | S | Test with perspective-distorted card images (tilted, angled, in-binder) |

### Phase C: Frame Classification

| Task | Agent | Effort | Description |
|---|---|---|---|
| C-01 | CV Engineer | M | Implement `FrameClassifier` using color histogram analysis of border region (gold=rare, silver=uncommon, black/white=modern, etc.) |
| C-02 | CV Engineer | S | Add texture analysis: modern frames have specific gradient patterns in the name/type bars |
| C-03 | Data Engineer | S | Create `FrameLayout` lookup table with region proportions for each FrameType |
| C-04 | CV Engineer | S | Handle ambiguous classifications with top-2 candidates and confidence scores |
| C-05 | QA Engineer | S | Test against known cards from each frame era (Alpha, 4th Ed, M15, Showcase, etc.) |

### Phase D: Anatomy Detection

| Task | Agent | Effort | Description |
|---|---|---|---|
| D-01 | CV Engineer | M | Implement `AnatomyDetector`: given expected regions from FrameLayout, find actual horizontal dividers (name bar bottom, type bar top/bottom, text box bottom) using edge detection within expected zones |
| D-02 | CV Engineer | S | Detect P/T box location (bottom-right, distinctive shape) |
| D-03 | CV Engineer | S | Detect collector info region (bottom, typically has small text with specific spacing) |
| D-04 | CV Engineer | S | Output `CardAnatomy` with actual pixel-precise Rects for each region |
| D-05 | QA Engineer | S | Validate anatomy detection accuracy against manually-annotated card images |

### Phase E: Region Extraction & Specialized OCR

| Task | Agent | Effort | Description |
|---|---|---|---|
| E-01 | OCR Engineer | S | Implement `RegionExtractor`: crop regions from rectified image, apply per-region preprocessing (contrast for collector, sharpen for name) |
| E-02 | OCR Engineer | M | Implement `SpecializedOcr`: run ML Kit on each region independently with region-appropriate parameters |
| E-03 | OCR Engineer | S | Name region OCR: expect title-case, large font, 1-5 words |
| E-04 | OCR Engineer | S | Type region OCR: vocabulary-constrained (known type/subtype words) |
| E-05 | OCR Engineer | S | Collector region OCR: expect small font, digits, 2-5 char set code, fraction format |
| E-06 | OCR Engineer | S | Mana cost region: symbol detection (not text OCR) — future enhancement, low priority |
| E-07 | QA Engineer | M | Test specialized OCR accuracy per region vs full-card OCR baseline |

### Phase F: Field Validation & Evidence Scoring

| Task | Agent | Effort | Description |
|---|---|---|---|
| F-01 | OCR Engineer | M | Implement `FieldValidator` with domain knowledge: MTG type vocabulary, known set codes, collector number formats, title-case rules |
| F-02 | OCR Engineer | S | Add cross-field validation: set code on collector line must match position of collector number |
| F-03 | OCR Engineer | S | Power/toughness rejection: if P/T region contains a fraction, exclude it from collector candidates |
| F-04 | Scryfall Engineer | S | Implement `EvidenceScorer` with lookup strategy selection based on which fields are confident |
| F-05 | Scryfall Engineer | S | Define lookup strategies: IDENTITY (set+cn, highest priority), FUZZY_NAME (name only), SEARCH (fallback), NAME_ONLY (lowest) |
| F-06 | QA Engineer | M | End-to-end accuracy testing: from card image to correct Scryfall match |

### Phase G: Integration & Cleanup

| Task | Agent | Effort | Description |
|---|---|---|---|
| G-01 | Platform Engineer | S | Remove OcrPipeline adapter, wire CardAnatomyEngine directly |
| G-02 | Platform Engineer | S | Remove old OcrPreprocessor (absorbed into RegionExtractor) |
| G-03 | Platform Engineer | S | Remove old CardOcrProcessor (absorbed into SpecializedOcr + FieldValidator) |
| G-04 | Performance Engineer | M | Profile full anatomy pipeline, ensure < 500ms total from card ready to Scryfall lookup |
| G-05 | QA Engineer | M | Full regression test: 10+ real card images across frame types, verify all produce correct identification |
| G-06 | Research Writer | S | Update ARCHITECTURE.md with new pipeline diagram and anatomy engine documentation |

---

## 8. Risk Analysis

| Risk | Severity | Mitigation |
|---|---|---|
| Perspective correction fails on binder pages (reflections, page curvature) | Medium | Fallback: skip rectification, use expanded crop as-is |
| Frame classification is inaccurate for damaged/worn cards | Low | Default to MODERN layout (most common), validate result |
| Anatomy detection misplaces dividers | Medium | Use ML Kit full-card OCR as validation — compare line positions to expected regions |
| Specialized OCR is slower than single-pass | Medium | Run regions concurrently with `async/await`. Budget: 3 × 80ms parallel = 80ms, not 240ms |
| Breaking change to DetectedCardText consumers | Low | ScoredEvidence → DetectedCardText adapter ensures backward compatibility |

---

## 9. Success Criteria

The Card Anatomy Engine is successful when:

1. **Name accuracy ≥ 95%** for cards with visible name bars (vs ~80% current)
2. **Set code accuracy ≥ 70%** when collector line is visible (vs ~10% current)
3. **Collector number accuracy ≥ 70%** when collector line is visible (vs ~5% current)
4. **No P/T false matches** as collector numbers (vs frequent current issue)
5. **No type line** selected as card name (vs occasional current issue)
6. **Total pipeline time < 600ms** from card ready to Scryfall lookup start
7. **Confidence score correlates with correctness** (high confidence = correct field)

---

## 10. Incremental Adoption Strategy

The design supports incremental rollout:

1. **Phase A** alone produces zero behavior change — it's a structural refactor
2. **Phase B** (perspective) can be tested independently and improves all downstream stages
3. **Phases C+D** (classification + anatomy) replace OcrPreprocessor.extractCardRegions()
4. **Phase E** replaces CardOcrProcessor with better per-region recognition
5. **Phase F** replaces the candidate-scoring approach in CardOcrProcessor with validation-based confidence
6. **Phase G** removes all old code

At each phase boundary, the system produces correct output. No phase leaves the app in a broken state.

---

## 11. Agent Assignments

| Agent | Primary Responsibility |
|---|---|
| **Lead Systems Engineer** | Architecture decisions, interface design, integration oversight |
| **Computer Vision Engineer** | Phases B, C, D (perspective, classification, anatomy detection) |
| **OCR Pipeline Engineer** | Phases E, F (specialized OCR, field validation) |
| **Android Platform Engineer** | Phases A, G (wiring, integration, lifecycle) |
| **Scryfall Integration Engineer** | Phase F (evidence scoring, lookup strategy) |
| **Performance Engineer** | Phase G (profiling, optimization) |
| **QA Test Engineer** | All phases (contract tests, integration tests, accuracy testing) |
| **Research/Technical Writer** | Phase G (documentation) |
