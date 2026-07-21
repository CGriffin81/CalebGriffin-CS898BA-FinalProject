# Pre-Launch Checklist
**Updated:** 2026-07-21 | **Source files:** 25 | **Tests:** 64 unit + 9 androidTest

---

## Build Environment

- [ ] Android Studio JBR available at `C:\Program Files\Android\Android Studio\jbr`
- [ ] Android SDK API 34 installed
- [ ] Gradle wrapper present: `.\gradlew.bat -v` shows Gradle 8.x + JVM 21
- [ ] `JAVA_HOME` set to Android Studio JBR for builds

## Source Code (25 Kotlin files)

- [ ] All files present in `app/src/main/java/com/mtgscanner/`
- [ ] No `ScryfallRepository.kt` (deleted — dead code)
- [ ] `compileDebugKotlin` passes with 0 errors
- [ ] `testDebugUnitTest` passes: 64 tests, 0 failures, 10 skipped

## Dependencies (build.gradle.kts)

- [ ] CameraX 1.4.2 (core, camera2, lifecycle, view)
- [ ] ML Kit Play Services OCR 19.0.1
- [ ] Room 2.6.1 (runtime, ktx, compiler via kapt)
- [ ] Retrofit 2.9.0 + Gson converter
- [ ] Coil 2.5.0
- [ ] Compose BOM 2024.06.00
- [ ] kotlinx-coroutines 1.7.3 (android, core, play-services)
- [ ] No OpenCV dependency (pure Kotlin detection)

## Android Configuration

- [ ] `AndroidManifest.xml`: CAMERA, INTERNET, ACCESS_NETWORK_STATE, READ_MEDIA_IMAGES
- [ ] Camera hardware feature required, autofocus optional
- [ ] Portrait orientation lock on MainActivity
- [ ] `com.google.mlkit.vision.DEPENDENCIES` meta-data = "ocr"
- [ ] compileSdk=34, minSdk=24, targetSdk=34

## Pipeline Connectivity (no stubs)

- [ ] `CameraScreen` passes `detectionPipeline::processFrame` as frame callback
- [ ] `CardDetector.detectCards()` returns real bounding boxes (not emptyList)
- [ ] `CardOcrProcessor.processCardImage()` uses `Task.await()` (not addOnSuccessListener)
- [ ] `AppRoot.onConfirm` calls `saveCardToCollection()` (not a TODO)
- [ ] `ScryfallApiService.getCardByCollectorNumber()` uses `@Path` (not @Query)
- [ ] `DetectionPipeline.clearProcessedCards()` called on reject/skip/back

## Database (Room v2)

- [ ] Schema version = 2
- [ ] UNIQUE INDEX on `scryfallId`
- [ ] Migration v1→v2 registered
- [ ] `OnConflictStrategy.IGNORE` on insert
- [ ] `COALESCE(SUM(quantity), 0)` for empty collection
- [ ] `CAST(collectorNumber AS INTEGER)` for numeric sort

## Device Preparation

- [ ] Samsung Galaxy S23 (or any Android 7.0+ device)
- [ ] USB debugging enabled
- [ ] `adb devices` shows connected device
- [ ] Storage > 500MB free
- [ ] Network available (WiFi or mobile)
- [ ] MTG cards available for testing

## Build & Deploy

```powershell
Set-Location "D:\Workspace\CS898BA\CalebGriffin-CS898BA-FinalProject"
.\Build-Apk.ps1 -Install -Launch
```

Or manually:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat --stop
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.mtgscanner/.MainActivity
```

## First Launch Verification

- [ ] App launches without crash
- [ ] Permission dialog appears
- [ ] MainMenuScreen shows "Scan Cards" and "View Collection"
- [ ] Camera preview loads on CameraScreen
- [ ] Logcat shows: `D/DetectionPipeline: processFrame received: 1280x720`
- [ ] Logcat shows: `D/CardFrameAnalyzer: Frame 100: avg conversion=Xms`

## Scan Verification

- [ ] Place a card on dark background → detection fires within 3 seconds
- [ ] Logcat shows: `D/CardDetector: Detected 1 card region(s)`
- [ ] After 3 stable frames: `D/DetectionPipeline: Card ready: trackingId=0`
- [ ] OCR fires: `D/CardOcrProcessor: OCR trackingId=0: name='...'`
- [ ] Verification screen appears with card name and Scryfall image
- [ ] Confirm → card appears in Collection screen

## Known Limitations (not defects)

- OcrPreprocessor is a pass-through (no contrast enhancement yet)
- Detection requires bright card on dark background (no CLAHE)
- Perspective correction not implemented (commented TODO)
- CollectionScreen "edit card" tap handler is a TODO
- `setTargetResolution()` generates deprecation warning (functional)

---

**All items checked = ready for real-world testing.**
