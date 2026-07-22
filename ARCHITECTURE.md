# MTG Scanner — Architecture & Runtime Behavior
**Updated:** 2026-07-21 (Card Anatomy Engine architecture)

---

## Architecture Overview

MTG Scanner uses a **Card Anatomy Pipeline** — a layout-first approach to card recognition.
Instead of running OCR on the entire card image and guessing which text belongs to which field,
the system first identifies the card's semantic regions using computer vision, then runs
specialized OCR readers on each region independently.

### Why the Architecture Changed

The original OCR-first pipeline had fundamental problems:

| Problem | Root Cause | Anatomy Solution |
|---|---|---|
| Type lines confused with card names | OCR sees all text, parser guesses semantics | Name reader only receives name bar bitmap |
| P/T fractions confused with collector numbers | Single-pass regex doesn't know spatial context | P/T reader and Collector reader are separate |
| Collector line often missing | Crop too small, region extraction wrong | Anatomy detector locates exact collector position |
| Confidence doesn't reflect correctness | Binary "populated = confident" | Per-field confidence from specialized readers |
| One-size-fits-all parsing | All frames treated identically | Layout templates per frame type |

---

## Pipeline Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  CameraX (KEEP_ONLY_LATEST, device-dependent resolution)        │
│  ImageProxy → toBitmap() → 2992×2992 on Galaxy S23              │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  DetectionPipeline.processFrame(bitmap)                           │
│  • CardDetector: edge-based detection, flood-fill, aspect filter │
│  • CardTracker: center-distance matching, 2-frame stability      │
│  • Expand bounding box 20%                                       │
│  → onCardReady(cardBitmap, trackingId)                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  CardAnatomyEngine.analyze(cardBitmap, trackingId)               │
│                                                                   │
│  ┌─ Stage 1: CardAnatomyDetector ─────────────────────────────┐ │
│  │  • Select FrameLayoutTemplate from FrameLayoutRegistry      │ │
│  │  • Convert proportional coords → pixel Rects                │ │
│  │  • Refine bounds using horizontal edge detection            │ │
│  │  • Validate with luminance variance per region              │ │
│  │  → CardLayout (9 regions with bounds + confidence)          │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─ Stage 2: RegionOcrPipeline ───────────────────────────────┐ │
│  │  • Crop each region from card bitmap                        │ │
│  │  • Dispatch to specialized readers (concurrent):            │ │
│  │    ├── NameOcr        → NameOcrResult                       │ │
│  │    ├── TypeLineOcr    → TypeLineOcrResult                   │ │
│  │    ├── CollectorOcr   → CollectorOcrResult                  │ │
│  │    ├── RulesOcr       → RulesOcrResult                      │ │
│  │    ├── ArtistOcr      → ArtistOcrResult                     │ │
│  │    └── PowerToughnessOcr → PowerToughnessOcrResult          │ │
│  │  → CardOcrResults (all regions aggregated)                  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─ Stage 3: Assembly + Fallback ─────────────────────────────┐ │
│  │  • Assemble DetectedCardText from region results            │ │
│  │  • If no name found → fallback to legacy OcrPipeline        │ │
│  │  → DetectedCardText (backward-compatible)                   │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  Evidence Scoring & Candidate Generation                          │
│  • CardEvidence: per-field value + confidence                    │
│  • EvidenceLookupStrategy: plans Scryfall API call sequence      │
│  • EvidenceCandidateScorer: weighted scoring (name 55%, type     │
│    15%, set 12%, P/T 10%, collector 8%)                          │
│  • ScryfallRepositoryResilience: executes planned lookups        │
│  → List<CardMatchCandidate> ranked by score                      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  VerificationScreen → User Confirm → Room Database               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Diagram (UML-style)

```
┌──────────────────────────────────────────────────────────────┐
│                        MainActivity                            │
│  owns: CardAnatomyEngine, DetectionPipeline, all network      │
│  callback: onCardReady → analyze → lookup → navigate          │
└──────┬───────────────────────────────┬───────────────────────┘
       │                               │
       ▼                               ▼
┌──────────────┐              ┌─────────────────────┐
│ Detection    │              │ CardAnatomyEngine    │
│ Pipeline     │              │                     │
│ ┌──────────┐ │   bitmap     │ ┌─────────────────┐ │
│ │CardDetect│─┼──────────────▶│CardAnatomyDetect│ │
│ │CardTrack │ │              │ │  + Registry     │ │
│ └──────────┘ │              │ └────────┬────────┘ │
└──────────────┘              │          │CardLayout │
                              │          ▼          │
                              │ ┌─────────────────┐ │
                              │ │RegionOcrPipeline│ │
                              │ │ ┌─────────────┐ │ │
                              │ │ │NameOcr      │ │ │
                              │ │ │TypeLineOcr  │ │ │
                              │ │ │CollectorOcr │ │ │
                              │ │ │RulesOcr     │ │ │
                              │ │ │ArtistOcr    │ │ │
                              │ │ │P/T Ocr      │ │ │
                              │ │ └─────────────┘ │ │
                              │ └────────┬────────┘ │
                              │          │Results   │
                              │          ▼          │
                              │  DetectedCardText   │
                              └─────────┬───────────┘
                                        │
                              ┌─────────▼───────────┐
                              │ Evidence Scoring     │
                              │ ┌─────────────────┐ │
                              │ │CardEvidence     │ │
                              │ │LookupStrategy   │ │
                              │ │CandidateScorer  │ │
                              │ └─────────────────┘ │
                              └─────────┬───────────┘
                                        │
                              ┌─────────▼───────────┐
                              │ Scryfall + Room      │
                              └─────────────────────┘
```

---

## Card Anatomy Detector

The `CardAnatomyDetector` is the central geometry engine. It uses **rule-based deterministic
computer vision** (no machine learning) to locate 9 semantic regions on a card.

### Detection Strategy
1. Select a `FrameLayoutTemplate` from the `FrameLayoutRegistry` based on frame type
2. Convert proportional (0.0–1.0) coordinates to pixel Rects using image dimensions
3. Refine region boundaries by scanning for strong horizontal edges near expected dividers
4. Validate each region using luminance variance (text regions have higher variance)
5. Assign per-region confidence based on content analysis

### Detected Regions

| Region | Content | Confidence Heuristic |
|---|---|---|
| NAME_BAR | Card title text | Moderate variance = text on colored background |
| MANA_COST | Mana symbols | High variance = colored circles |
| ARTWORK | Card illustration | Very high variance = complex image |
| TYPE_LINE | "Creature — Demon" | Moderate variance = text |
| RULES_TEXT | Ability text | Moderate variance from text content |
| SET_SYMBOL | Rarity/set icon | High variance = colored icon |
| COLLECTOR_INFO | Set code + number | Lower variance acceptable (small text) |
| ARTIST_CREDIT | Artist name | Low contrast small text |
| POWER_TOUGHNESS | P/T box (optional) | Contrast difference from surrounding area |

---

## Layout Templates

Templates define where each region should be located on a specific frame type,
using normalized 0.0–1.0 coordinates. This makes them resolution-independent.

### Registered Frame Types

| Frame Type | Era | P/T | Notes |
|---|---|---|---|
| MODERN | 2015–present (M15+) | Yes | Most common in circulation |
| CLASSIC | 2003–2014 (8th–M14) | Yes | Wider borders |
| FULL_ART | Zendikar lands, Unstable | No | Art covers entire card |
| BORDERLESS | Showcase, Extended Art | Yes | Art full-bleed behind text |
| UNKNOWN | Fallback | Yes | Uses MODERN proportions |

### Adding a New Layout

```kotlin
// 1. Add enum value to FrameType.kt
enum class FrameType {
    MODERN, CLASSIC, FULL_ART, BORDERLESS,
    PLANESWALKER,  // ← add here
    SAGA, SPLIT, UNKNOWN
}

// 2. Create template with proportional coordinates
val planeswalkerTemplate = FrameLayoutTemplate(
    frameType = FrameType.PLANESWALKER,
    nameBar = ProportionalRect(top = 0.03f, bottom = 0.07f, left = 0.05f, right = 0.82f),
    manaCost = ProportionalRect(top = 0.03f, bottom = 0.07f, left = 0.82f, right = 0.95f),
    // ... define all regions
    powerToughness = null  // Planeswalkers have loyalty, not P/T
)

// 3. Register in FrameLayoutRegistry
registry.register(planeswalkerTemplate)
```

No code changes to `CardAnatomyDetector` are needed — it automatically uses
the template from the registry for any frame type.

---

## Specialized OCR Readers

Each reader receives ONLY the bitmap for its assigned region. No reader sees
the full card. No reader performs card identification or Scryfall lookup.

| Reader | Input | Output | Validation |
|---|---|---|---|
| `NameOcr` | Name bar crop | `NameOcrResult(name, confidence)` | Title-case, 1-5 words, widest line selection |
| `TypeLineOcr` | Type line crop | `TypeLineOcrResult(types, subtypes)` | MTG vocabulary matching |
| `CollectorOcr` | Collector crop | `CollectorOcrResult(cn, set, rarity)` | Fraction format, set code patterns, year rejection |
| `RulesOcr` | Rules text crop | `RulesOcrResult(oracleText)` | Keyword presence |
| `ArtistOcr` | Artist crop | `ArtistOcrResult(artistName)` | Prefix removal, title-case |
| `PowerToughnessOcr` | P/T box crop | `PowerToughnessOcrResult(power, toughness)` | Fraction pattern, star values |

All readers share a single `MlKitRecognizer` singleton for efficiency.
Readers run **concurrently** via `coroutineScope { async { ... } }`.

---

## Evidence Scoring

OCR output is treated as **probabilistic evidence**, not truth.

### CardEvidence Model
```kotlin
data class CardEvidence(
    val name: EvidenceField,           // "Doom Whisperer" @ 0.85 confidence
    val collectorNumber: EvidenceField, // "69" @ 0.70 confidence (or absent)
    val setCode: EvidenceField,        // "GRN" @ 0.60 confidence (or absent)
    val typeLine: EvidenceField,       // "Creature — Demon" @ 0.80
    val powerToughness: EvidenceField, // "6/6" @ 0.75 (or absent)
    val frameType: FrameType
)
```

### Scoring Weights

| Field | Weight | Role |
|---|---|---|
| Name | 55% | Primary identification signal |
| Type line | 15% | Creature vs spell disambiguation |
| Set code | 12% | Printing disambiguation |
| P/T | 10% | Creature confirmation |
| Collector # | 8% | Exact printing (disambiguation only) |

### Key Principles
- Missing fields **never reject** candidates — they provide zero contribution
- Low-confidence fields contribute proportionally less
- A confident name match alone is sufficient for identification
- Collector info only disambiguates between multiple candidates with the same name
- Score is normalized: `Σ(match × weight × confidence) / Σ(weight × confidence)`

---

## Lookup Strategy Planning

`EvidenceLookupStrategy` determines which Scryfall API calls to make:

| Condition | Strategy | API Call |
|---|---|---|
| Set + collector confident (>0.4) | Identity | `/cards/{set}/{cn}` |
| Name confident (>0.3), length ≥3 | Fuzzy name | `/cards/named?fuzzy=` |
| Name present, any confidence | General search | `/cards/search?q=` |
| Name + type, type confident (>0.6) | Filtered search | `/cards/search?q=name t:type` |

Strategies are returned as an ordered list. Caller attempts each in order,
stopping at the first successful result.

---

## Debug Overlay

The `AnatomyDebugOverlay` composable renders CardLayout regions on the camera preview:

| Region | Color | Label |
|---|---|---|
| Name Bar | Green | NAME |
| Mana Cost | Orange | MANA |
| Artwork | Purple | ART |
| Type Line | Blue | TYPE |
| Rules Text | Blue Grey | RULES |
| Set Symbol | Pink | SET |
| Collector Info | Yellow | COLL |
| Artist Credit | Brown | ARTIST |
| P/T | Red | P/T |

Toggled via a tap on "🔍 Anatomy Overlay: ON/OFF" in the camera screen bottom panel.

---

## Test Coverage

| Test File | Tests | Coverage |
|---|---|---|
| `CardAnatomyDetectorTest.kt` | 33 | Registry, templates, proportional geometry, frame layouts, serialization, edge cases |
| `PipelineHandoffTest.kt` | 10 | Callback ownership, stability, tracking, deduplication |
| `DetectionPipelineIntegrationTest.kt` | 15 | Tracker logic, multi-card, center-distance |
| `OcrPipelineIntegrationTest.kt` | 36 | Text parsing, confidence scoring, patterns |
| `FuzzyMatchingIntegrationTest.kt` | 13 | Levenshtein, scoring, filtering |
| **Total** | **107** | **0 failures, 13 skipped (require Android runtime)** |

---

## Package Structure

```
com.mtgscanner/
├── anatomy/                    ← NEW: Card Anatomy Engine
│   ├── CardAnatomyDetector.kt    Region detection (CV, no ML)
│   ├── CardAnatomyEngine.kt      Pipeline orchestrator
│   ├── FrameLayoutRegistry.kt    Template storage
│   ├── model/
│   │   ├── CardAnatomyModels.kt  RegionType, CardRegion, CardLayout
│   │   ├── CardLayoutModel.kt    Geometry-only model with serialization
│   │   ├── FrameLayoutTemplate.kt ProportionalRect, template data class
│   │   └── FrameType.kt          Frame type enum
│   └── ocr/                    ← NEW: Specialized readers
│       ├── RegionOcrBase.kt       Shared ML Kit singleton
│       ├── RegionOcrResult.kt     Result data classes
│       ├── RegionOcrPipeline.kt   Concurrent reader orchestrator
│       ├── NameOcr.kt             Name bar reader
│       ├── TypeLineOcr.kt         Type line reader
│       ├── CollectorOcr.kt        Collector info reader
│       ├── RulesOcr.kt            Rules text reader
│       ├── ArtistOcr.kt           Artist credit reader
│       └── PowerToughnessOcr.kt   P/T reader
├── camera/                     Camera + frame delivery
├── detection/                  Card detection + tracking (unchanged)
├── matching/                   ← ENHANCED: Evidence-based scoring
│   ├── FuzzyCardMatcher.kt       Legacy scorer (retained)
│   ├── CardEvidence.kt           Evidence model with per-field confidence
│   ├── EvidenceCandidateScorer.kt Weighted candidate scoring
│   └── EvidenceLookupStrategy.kt  API call planning
├── model/                      Domain models (unchanged)
├── network/                    Scryfall + resilience (unchanged)
├── ocr/                        Legacy OCR (retained as fallback)
├── data/                       Room database (unchanged)
└── ui/                         ← ENHANCED: Debug overlay
    ├── AnatomyDebugOverlay.kt    Region visualization
    ├── CameraScreen.kt           With overlay toggle
    └── ...
```

---

## Architecture Principles

1. **Layout before OCR.** Understand WHERE regions are before attempting to read text.
2. **Single responsibility per reader.** Each OCR reader handles exactly one region type.
3. **Evidence, not truth.** OCR output is probabilistic; missing data doesn't reject candidates.
4. **Template-driven geometry.** New frame types require only data (a template), not new code.
5. **Concurrent execution.** Region readers run in parallel via coroutines.
6. **Graceful fallback.** If anatomy produces nothing, the legacy full-card OCR still runs.
7. **Debug observability.** Every intermediate result is logged and overlay-visualizable.
8. **Single callback owner.** DetectionPipeline.onCardReady owned exclusively by MainActivity.
