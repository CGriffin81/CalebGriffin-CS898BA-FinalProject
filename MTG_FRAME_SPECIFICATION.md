# MTG Card Frame Specification
## Normalized Coordinate Reference for Card Anatomy Engine

**Author:** Research & Technical Writer  
**Date:** 2026-07-21  
**Purpose:** Define proportional region coordinates for every major MTG frame type  
**Sources:** Wizards of the Coast official card anatomy, Scryfall API frame documentation, measured card scans

---

## 1. Card Physical Dimensions

All Magic: The Gathering cards share identical physical dimensions:
- **Width:** 63mm (2.48")
- **Height:** 88mm (3.46")
- **Aspect ratio:** 1:1.397 (width:height)
- **Corner radius:** 3mm

Standard digital representation at 300 DPI: 744 × 1039 pixels.
Scryfall "normal" images: 488 × 680 pixels.

All coordinates in this document use **normalized values (0.0–1.0)** where:
- `x = 0.0` = left edge, `x = 1.0` = right edge
- `y = 0.0` = top edge, `y = 1.0` = bottom edge

---

## 2. Card Anatomy (Universal Elements)

Every standard MTG card face contains these semantic regions:

| Region | Content | OCR Value |
|---|---|---|
| **Name Bar** | Card title text | Primary identification |
| **Mana Cost** | Colored mana symbols | Secondary (confirms color identity) |
| **Artwork** | Illustration | Not OCR'd (visual matching possible) |
| **Type Line** | Card types and subtypes | Confirms card category |
| **Rules Text** | Oracle ability text | Cross-reference with Scryfall |
| **Set Symbol** | Expansion symbol (colored by rarity) | Rarity identification |
| **P/T Box** | Power/Toughness (creatures only) | Confirms creature stats |
| **Collector Strip** | Number, set code, rarity, language, artist | Exact printing ID |

---

## 3. Frame Eras & Types

### 3.1 Scryfall Frame Classification

Scryfall defines these frame values (from [API documentation](https://scryfall.com/docs/api/frames)):

| Frame | Era | Description |
|---|---|---|
| `1993` | Alpha–Alliances (1993–1996) | Original hand-drawn frame |
| `1997` | Mirage–Scourge (1997–2003) | Revised classic frame |
| `2003` | 8th Edition–M14 (2003–2014) | "Modern" frame (separate boxes for name/type/P-T) |
| `2015` | M15–present (2015+) | Holofoil-stamp frame (current standard) |
| `future` | Future Sight (2007) | Futuristic alternate frame |

### 3.2 Frame Effects (Overlays on Base Frames)

| Effect | Description | Layout Impact |
|---|---|---|
| `legendary` | Crown/filigree on name bar | Name bar slightly taller |
| `showcase` | Set-specific alternate art treatment | Varies per set |
| `extendedart` | Art extends into text box margins | Narrower text borders |
| `borderless` | No black border around card | Regions extend to edge |
| `fullart` | Art covers entire card face | Text overlays art |
| `companion` | Companion card styling | Minor cosmetic only |
| `nyxtouched` | Starfield frame (Theros) | Cosmetic only |
| `inverted` | Inverted/upside-down frame | Regions rotated |
| `etched` | Etched foil treatment | Cosmetic only |

---

## 4. Normalized Coordinate Specifications

### 4.1 Standard 2015 Frame (M15+, Most Common)

The most common frame in current circulation. Used for ~80% of cards printed since 2015.

```
┌──────────────────────────────────────────────┐ 0.00
│ border                                        │
│  ┌────────────────────────┬─────────┐        │ 0.035
│  │    CARD NAME           │ MANA ⊕⊕ │        │
│  └────────────────────────┴─────────┘        │ 0.085
│  ┌──────────────────────────────────┐        │ 0.09
│  │                                  │        │
│  │         ARTWORK                  │        │
│  │                                  │        │
│  │                                  │        │
│  └──────────────────────────────────┘        │ 0.495
│  ┌──────────────────────────┬───────┐        │ 0.50
│  │    TYPE LINE             │ ⬡ SET │        │
│  └──────────────────────────┴───────┘        │ 0.55
│  ┌──────────────────────────────────┐        │ 0.56
│  │                                  │        │
│  │       RULES TEXT                 │        │
│  │                                  │        │
│  │                                  │        │
│  │                                  │        │
│  │                        ┌────────┐│        │ 0.87
│  └────────────────────────│  P / T ││        │
│                           └────────┘│        │ 0.925
│  ┌─────────────────┬────────────────┐        │ 0.93
│  │ 069/259 R • GRN │  ✎ Artist Name │        │
│  └─────────────────┴────────────────┘        │ 0.97
│ border                                        │
└──────────────────────────────────────────────┘ 1.00
```

| Region | Top | Bottom | Left | Right | Height |
|---|---|---|---|---|---|
| Name Bar | 0.035 | 0.085 | 0.05 | 0.80 | 5.0% |
| Mana Cost | 0.035 | 0.085 | 0.80 | 0.95 | 5.0% |
| Artwork | 0.09 | 0.495 | 0.05 | 0.95 | 40.5% |
| Type Line | 0.50 | 0.55 | 0.05 | 0.82 | 5.0% |
| Set Symbol | 0.50 | 0.55 | 0.82 | 0.95 | 5.0% |
| Rules Text | 0.56 | 0.87 | 0.05 | 0.95 | 31.0% |
| P/T Box | 0.87 | 0.925 | 0.72 | 0.93 | 5.5% |
| Collector Strip | 0.93 | 0.97 | 0.05 | 0.50 | 4.0% |
| Artist Credit | 0.93 | 0.97 | 0.50 | 0.95 | 4.0% |

**Collector strip format (2015+ cards):**
```
{collector_number}/{total} • {set_code} • {language}
Example: 069/259 R • GRN • EN
```

---

### 4.2 2003 Frame (8th Edition through M14)

Introduced the "box" frame with distinct colored bars for name and type.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.04 | 0.09 | 0.06 | 0.78 | Slightly wider borders |
| Mana Cost | 0.04 | 0.09 | 0.78 | 0.94 | |
| Artwork | 0.10 | 0.51 | 0.06 | 0.94 | Rounded art window |
| Type Line | 0.52 | 0.57 | 0.06 | 0.82 | |
| Set Symbol | 0.52 | 0.57 | 0.82 | 0.94 | |
| Rules Text | 0.58 | 0.89 | 0.06 | 0.94 | |
| P/T Box | 0.89 | 0.94 | 0.70 | 0.93 | Rounded corners |
| Collector Strip | 0.95 | 0.98 | 0.06 | 0.50 | Smaller text |
| Artist Credit | 0.95 | 0.98 | 0.50 | 0.94 | |

**Collector strip format (2003–2014):**
```
{collector_number}/{total}
Example: 42/350
(Set code shown via expansion symbol only, not printed as text)
```

---

### 4.3 Pre-2003 Classic Frame (Alpha through 7th Edition)

Original frame with no distinct boxes. Name/type printed directly on the frame.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.03 | 0.08 | 0.07 | 0.75 | No separate box |
| Mana Cost | 0.03 | 0.08 | 0.75 | 0.93 | Circles on frame |
| Artwork | 0.09 | 0.50 | 0.07 | 0.93 | Square-ish window |
| Type Line | 0.51 | 0.56 | 0.07 | 0.93 | Full width |
| Rules Text | 0.57 | 0.90 | 0.07 | 0.93 | |
| P/T Box | 0.90 | 0.96 | 0.68 | 0.93 | Rounded, larger |
| Artist Credit | 0.96 | 0.99 | 0.07 | 0.93 | Very small |

**Collector strip:** Not present on cards before Exodus (1998). After Exodus: simple `{number}/{total}` format.

---

### 4.4 Borderless / Showcase Frame

Art extends to the card edges with no border. Text regions overlay the artwork.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.02 | 0.07 | 0.04 | 0.78 | Semi-transparent overlay |
| Mana Cost | 0.02 | 0.07 | 0.78 | 0.96 | |
| Artwork | 0.00 | 1.00 | 0.00 | 1.00 | Full card |
| Type Line | 0.50 | 0.55 | 0.04 | 0.82 | |
| Set Symbol | 0.50 | 0.55 | 0.82 | 0.96 | |
| Rules Text | 0.55 | 0.87 | 0.04 | 0.96 | |
| P/T Box | 0.87 | 0.925 | 0.72 | 0.94 | |
| Collector Strip | 0.94 | 0.98 | 0.04 | 0.50 | |
| Artist Credit | 0.94 | 0.98 | 0.50 | 0.96 | |

---

### 4.5 Full-Art Frame (Zendikar Lands, Unstable, etc.)

Art fills the entire card. Only the name bar, type line, and collector info have text.
No rules text box (or minimal). No P/T (typically lands).

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.03 | 0.08 | 0.05 | 0.80 | Narrow translucent bar |
| Mana Cost | 0.03 | 0.08 | 0.80 | 0.95 | Land symbols |
| Artwork | 0.00 | 1.00 | 0.00 | 1.00 | Entire card |
| Type Line | 0.85 | 0.90 | 0.05 | 0.95 | Low, near bottom |
| Collector Strip | 0.95 | 0.98 | 0.05 | 0.50 | |
| Artist Credit | 0.95 | 0.98 | 0.50 | 0.95 | |

**No P/T box.** No rules text for basic lands.

---

### 4.6 Extended Art Frame

Art extends into the borders but maintains the standard text layout.
Borders are narrower but present.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.035 | 0.085 | 0.03 | 0.80 | Narrower left border |
| Mana Cost | 0.035 | 0.085 | 0.80 | 0.97 | Narrower right border |
| Artwork | 0.09 | 0.495 | 0.0 | 1.0 | Extends to edges |
| Type Line | 0.50 | 0.55 | 0.03 | 0.82 | |
| Set Symbol | 0.50 | 0.55 | 0.82 | 0.97 | |
| Rules Text | 0.56 | 0.87 | 0.03 | 0.97 | Wider than standard |
| P/T Box | 0.87 | 0.925 | 0.74 | 0.95 | |
| Collector Strip | 0.93 | 0.97 | 0.03 | 0.50 | |

---

### 4.7 Planeswalker Frame

Unique layout with loyalty abilities replacing rules text, and a loyalty counter
in the bottom-right instead of P/T.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.035 | 0.08 | 0.05 | 0.80 | Same as standard |
| Mana Cost | 0.035 | 0.08 | 0.80 | 0.95 | |
| Artwork | 0.08 | 0.52 | 0.05 | 0.95 | Slightly taller |
| Type Line | 0.52 | 0.57 | 0.05 | 0.82 | "Legendary Planeswalker — Name" |
| Loyalty Abilities | 0.57 | 0.90 | 0.05 | 0.95 | [+1], [-2], [-7] format |
| Loyalty Box | 0.90 | 0.95 | 0.78 | 0.93 | Shield shape with number |
| Collector Strip | 0.95 | 0.98 | 0.05 | 0.50 | |

**Key difference:** Loyalty box replaces P/T box. Located bottom-right with shield shape.

---

### 4.8 Saga Frame

Vertical chapter layout with abilities listed as numbered chapters.
Art is on the right side instead of the top.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.03 | 0.08 | 0.05 | 0.60 | Shorter width |
| Mana Cost | 0.03 | 0.08 | 0.60 | 0.68 | |
| Artwork | 0.08 | 0.92 | 0.55 | 0.95 | RIGHT side, tall |
| Type Line | 0.08 | 0.12 | 0.05 | 0.55 | Above chapters |
| Chapter Text | 0.12 | 0.92 | 0.05 | 0.55 | LEFT side, numbered I/II/III |
| Collector Strip | 0.93 | 0.97 | 0.05 | 0.50 | |

**Key difference:** Art on right, text on left. No P/T. Chapters instead of rules text.

---

### 4.9 Split / Aftermath Cards

Two half-cards on one physical card. Each half has its own name, cost, type, and text.

**Standard Split (side by side, rotated 90°):**
Each half occupies 50% of the card width when rotated.

| Region (Left Half) | Top | Bottom | Left | Right |
|---|---|---|---|---|
| Name | 0.02 | 0.07 | 0.02 | 0.40 |
| Mana Cost | 0.02 | 0.07 | 0.40 | 0.48 |
| Type Line | 0.07 | 0.11 | 0.02 | 0.48 |
| Rules Text | 0.11 | 0.95 | 0.02 | 0.48 |

| Region (Right Half) | Top | Bottom | Left | Right |
|---|---|---|---|---|
| Name | 0.02 | 0.07 | 0.52 | 0.90 |
| Mana Cost | 0.02 | 0.07 | 0.90 | 0.98 |
| Type Line | 0.07 | 0.11 | 0.52 | 0.98 |
| Rules Text | 0.11 | 0.95 | 0.52 | 0.98 |

**Note:** Split cards are rotated 90° — the scanner must detect orientation.

---

### 4.10 Adventure Cards

Card with a small "adventure" sub-card embedded in the rules text area.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.035 | 0.085 | 0.05 | 0.80 | Main creature name |
| Mana Cost | 0.035 | 0.085 | 0.80 | 0.95 | Creature cost |
| Artwork | 0.09 | 0.495 | 0.05 | 0.95 | |
| Type Line | 0.50 | 0.55 | 0.05 | 0.82 | Creature type |
| Adventure Box | 0.56 | 0.78 | 0.05 | 0.55 | Sub-card: name + type + text |
| Rules Text | 0.56 | 0.87 | 0.55 | 0.95 | Creature rules (right side) |
| P/T Box | 0.87 | 0.925 | 0.72 | 0.93 | |
| Collector Strip | 0.93 | 0.97 | 0.05 | 0.50 | |

**Key difference:** Rules text area is split between adventure (left) and creature text (right).

---

### 4.11 Double-Faced Cards (DFC)

Two complete card faces on front and back. Each face has standard layout.
The front face has a sun/moon indicator in the top-left corner.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| DFC Indicator | 0.02 | 0.05 | 0.02 | 0.05 | Small ☀/☽ icon |
| Name Bar | 0.035 | 0.085 | 0.05 | 0.80 | Standard position |
| (all others) | Standard 2015 positions | | | |

**Key difference:** Small indicator icon in top-left corner. Otherwise standard layout.

---

### 4.12 Battle Cards

Landscape-oriented cards that rotate 90° when played.
Name on the left side, art across the top.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.03 | 0.08 | 0.05 | 0.60 | |
| Mana Cost | 0.03 | 0.08 | 0.60 | 0.68 | |
| Artwork | 0.08 | 0.50 | 0.05 | 0.95 | |
| Type Line | 0.50 | 0.55 | 0.05 | 0.95 | "Battle — Siege" |
| Rules Text | 0.55 | 0.87 | 0.05 | 0.95 | |
| Defense Box | 0.87 | 0.925 | 0.72 | 0.93 | Shield with number (like P/T) |
| Collector Strip | 0.93 | 0.97 | 0.05 | 0.50 | |

---

### 4.13 Retro Frame (Time Spiral Remastered, Dominaria Remastered)

Modern cards printed with the pre-8th-Edition visual style. Layout proportions
match the classic frame but may have modern collector info format.

Uses **Pre-2003 Classic** proportions (Section 4.3) with modern collector strip format.

---

### 4.14 Future Frame (Future Sight, Time Spiral Remastered)

Unique "futuristic" frame with art extending into the left side.
Name bar is narrower and pushed right.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.03 | 0.08 | 0.25 | 0.80 | Starts further right |
| Mana Cost | 0.03 | 0.08 | 0.80 | 0.95 | |
| Artwork | 0.03 | 0.55 | 0.0 | 0.25 | Left strip + top area |
| Type Line | 0.82 | 0.87 | 0.05 | 0.95 | Near bottom |
| Rules Text | 0.09 | 0.82 | 0.25 | 0.95 | Right of art |
| P/T Box | 0.87 | 0.93 | 0.05 | 0.25 | Left side (unusual) |
| Collector Strip | 0.94 | 0.98 | 0.05 | 0.50 | |

---

### 4.15 Universes Beyond / Secret Lair

These use standard 2015 frame proportions but may have unique visual treatments.
From a geometry perspective, they match Section 4.1 (Standard 2015) with possible
showcase treatments (Section 4.4).

**Secret Lair** cards often use borderless or full-art treatments — use the
appropriate template based on visual classification.

---

### 4.16 Commander-Specific Cards

Commander cards use standard frames. The only layout difference:
- Some Commander products use **oversized cards** (but these aren't scanned in binders)
- Color identity dots may appear on the type line bar

Proportions match Section 4.1 (Standard 2015).

---

### 4.17 Token Cards

Tokens have a distinct layout with larger art and minimal text.

| Region | Top | Bottom | Left | Right | Notes |
|---|---|---|---|---|---|
| Name Bar | 0.03 | 0.07 | 0.05 | 0.95 | Full width |
| Artwork | 0.07 | 0.75 | 0.03 | 0.97 | Very large |
| Type Line | 0.75 | 0.80 | 0.05 | 0.95 | |
| Rules Text | 0.80 | 0.90 | 0.05 | 0.95 | Minimal (often empty) |
| P/T Box | 0.90 | 0.96 | 0.72 | 0.93 | |
| Collector Strip | 0.96 | 0.99 | 0.05 | 0.95 | Minimal info |

---

## 5. Collector Line Formats

The collector information strip has evolved significantly across eras.

### Format by Era

| Era | Format | Example |
|---|---|---|
| Alpha–Alliances (1993–1996) | None | No collector number |
| Exodus–7th Ed (1998–2001) | `{num}/{total} ★` | `42/143 ★` |
| 8th Ed–M14 (2003–2014) | `{num}/{total}` | `042/249` |
| M15–Ixalan (2015–2017) | `{num}/{total} {rarity} {set}` | `069/264 R • GRN` |
| Dominaria+ (2018+) | `{num}/{total} • {set} • {lang}` | `069/259 R • GRN • EN` |
| Secret Lair / Promo | `{num}` or `{num}★` | `001` or `P042` |

### Rarity Codes in Collector Line
- `C` = Common (black set symbol)
- `U` = Uncommon (silver set symbol)
- `R` = Rare (gold set symbol)
- `M` = Mythic Rare (red-orange set symbol)
- `S` = Special / Bonus
- `P` = Promo

---

## 6. Set Symbol Placement

The expansion/set symbol is consistently located at the right edge of the type line bar.

| Frame Type | Set Symbol Position |
|---|---|
| Standard 2015 | Right end of type line bar (y: 50–55%, x: 82–95%) |
| 2003 frame | Same position but within the type-line box |
| Classic frame | Right of type line text (no box) |
| Full-art | Same relative position but overlaying art |
| Borderless | Same position, semi-transparent background |

**Rarity indicated by set symbol color:**
- Black = Common
- Silver = Uncommon
- Gold = Rare
- Red-orange = Mythic Rare

---

## 7. Implementation Notes for Card Anatomy Engine

### Template Selection Strategy

```
1. Default to MODERN (2015 frame) — covers ~80% of scanned cards
2. If name bar is NOT detected at expected position → try CLASSIC
3. If art covers full card → try FULL_ART or BORDERLESS
4. If chapters detected on left side → SAGA
5. If DFC indicator in top-left → DFC (still uses standard proportions)
6. If two text areas side by side → SPLIT
```

### Expansion Offset

When the bitmap includes DetectionPipeline's 20% expansion padding:
```
card_start = 0.20 / 1.40 = 0.1429 of bitmap
card_end   = 1.20 / 1.40 = 0.8571 of bitmap

To convert card-relative (0.0–1.0) to bitmap-absolute:
  bitmap_y = card_start + card_fraction × (card_end - card_start)
```

### Minimum Region Size for ML Kit

ML Kit requires approximately 50+ pixels of height to reliably detect text.
At 680px card height:
- Name bar (5% height) = 34px — borderline, use 8% for safety = 54px
- Collector strip (4% height) = 27px — too small, use 6% = 41px

Recommendation: **Pad regions by 1-2% vertically** for ML Kit reliability.

### Priority Order for OCR

Not all regions need OCR for card identification. Priority:
1. **Name Bar** — sufficient alone for Scryfall fuzzy match
2. **Collector Strip** — enables exact printing identification
3. **Type Line** — disambiguates same-name cards
4. **P/T Box** — confirms creature stats
5. **Rules Text** — useful for verification but not identification
6. **Artist Credit** — low value for identification
