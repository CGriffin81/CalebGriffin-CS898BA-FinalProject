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
The literature review and project scope are in place. Next work should focus on Android project scaffolding, CameraX preview and analysis setup, then the first detection and identification pipeline.
