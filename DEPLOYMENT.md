# MTG Scanner - Real-World Testing Deployment Guide

## Prerequisites

### Required Software
- **Android Studio** (latest version) or **Android SDK Command Line Tools**
- **Java Development Kit (JDK)** 8 or higher (recommend JDK 11+)
- **Android SDK** (API 34, API 24+)
- **Android NDK** (for OpenCV native libraries)
- **Gradle** (7.0+, or use embedded gradle via Android Studio)
- **adb (Android Debug Bridge)** for deployment

### Target Device
- **Samsung Galaxy S23** with Android 11+ (API 30+)
- USB debugging enabled in Developer Options
- USB connection to development machine

---

## Build Instructions

### Option 1: Using Android Studio (Recommended)

1. Open the project in Android Studio
   ```bash
   open -a "Android Studio" /path/to/CalebGriffin-CS898BA-FinalProject
   ```

2. Wait for Gradle sync to complete (Android Studio will download dependencies automatically)

3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**

4. Monitor the Build console for completion

5. APK will be generated at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option 2: Using Gradle Command Line

1. Ensure JAVA_HOME and ANDROID_HOME are set:
   ```bash
   # Windows
   set JAVA_HOME=C:\Program Files\Java\jdk-11
   set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
   
   # macOS/Linux
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
   export ANDROID_HOME=$HOME/Library/Android/Sdk
   ```

2. From project root, run:
   ```bash
   # Windows
   gradlew.bat assembleDebug
   
   # macOS/Linux
   ./gradlew assembleDebug
   ```

3. If gradlew is not available, install the wrapper:
   ```bash
   gradle wrapper --gradle-version 7.6
   ```

4. APK will be at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Deployment to Samsung Galaxy S23

### Step 1: Connect Device

1. Connect Samsung Galaxy S23 via USB cable
2. Verify connection with adb:
   ```bash
   adb devices
   ```
   
   Output should show:
   ```
   List of attached devices
   emulator-5554          device
   R38N70ABCDE           device
   ```

### Step 2: Install APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `-r` flag replaces existing installation
- Wait for "Success" message

### Step 3: Launch Application

```bash
adb shell am start -n com.mtgscanner/.MainActivity
```

Or tap the MTG Scanner icon on the device home screen.

---

## Real-World Testing Scenarios

### Scenario 1: Basic Card Detection (Single Card)
1. Place one MTG card on desk in front of camera
2. Tap "Start Scanning" on MainMenuScreen
3. Wait for card detection (should highlight with blue contour)
4. Review OCR results on VerificationScreen
5. Confirm card and enter quantity (1)
6. Verify card appears in CollectionScreen

**Expected Results:**
- ✓ Card detected within 3-5 seconds
- ✓ Card name, set code, collector number extracted
- ✓ Scryfall match found (green > 0.8 confidence)
- ✓ Card image displays in VerificationScreen
- ✓ Card stored in collection with correct quantity

**Failure Indicators:**
- ✗ No detection after 10 seconds → check lighting, try different angle
- ✗ OCR confidence < 0.6 → adjust focus, reduce glare
- ✗ No Scryfall match → check internet connection

---

### Scenario 2: Multiple Cards (Binder Page)
1. Display 9-12 Magic cards on a binder page or table
2. Tap "Start Scanning"
3. Slowly flip through cards (1-2 seconds per card)
4. All cards should be detected individually and tracked
5. Navigate to VerificationScreen for each
6. Confirm quantity for each card

**Expected Results:**
- ✓ All 9-12 cards detected
- ✓ Each card tracked separately (no duplicates while visible)
- ✓ Detection count increments correctly
- ✓ OCR processes each card independently
- ✓ All cards appear in CollectionScreen with correct quantities

**Failure Indicators:**
- ✗ Fewer than 50% of cards detected → reduce scanning speed
- ✗ Duplicate detections (same card counted multiple times while visible) → check CardTracker 3-frame stability
- ✗ OCR timeouts → reduce frame size or increase device CPU usage

---

### Scenario 3: Offline Mode Testing
1. Disconnect WiFi and disable mobile data on device
2. Scan a card (should already be in cache from previous scans)
3. Verify "Using offline cache" notice appears in VerificationScreen
4. Confirm card is still matched correctly

**Expected Results:**
- ✓ No network errors displayed
- ✓ Card found from local cache (SharedPreferences)
- ✓ "OfflineNotice" banner visible
- ✓ Card quantity entered and saved

**Failure Indicators:**
- ✗ Error message about network → check cache was pre-populated
- ✗ No candidates found offline → card wasn't cached during online session

---

### Scenario 4: Network Resilience & Retry
1. Enable WiFi but simulate poor network (throttle connection)
2. Scan multiple cards
3. Observe retry behavior (delays, exponential backoff)
4. Verify cards are still matched after retries

**Expected Results:**
- ✓ App retries Scryfall API calls (up to 3 attempts)
- ✓ Delays between retries (100ms → 200ms → 400ms)
- ✓ Eventually succeeds or falls back to cache
- ✓ No crash or hanging UI

**Failure Indicators:**
- ✗ Immediate error without retry → check RetryPolicy initialization
- ✗ Excessive delays (> 20 seconds) → network timeout too long
- ✗ UI freezes → check async/await Coroutine implementation

---

### Scenario 5: Low OCR Confidence Warning
1. Scan a card that's blurry, at an angle, or with poor lighting
2. OCR confidence should be < 0.6 (60%)
3. Verify LowConfidenceWarning dialog appears

**Expected Results:**
- ✓ Dialog shows OCR confidence percentage
- ✓ Visual indicator (red if < 60%)
- ✓ Still allows confirmation with low confidence
- ✓ User can manually select correct card from candidates

**Failure Indicators:**
- ✗ No warning despite low confidence → check ocrConfidence field in CardVerification
- ✗ Dialog blocks confirmation → allow user to proceed despite warning

---

## Performance Baseline Measurements

During testing, record these metrics:

| Metric | Target | Method |
|--------|--------|--------|
| Detection latency | < 500ms | Time from frame capture to CardTracker.isReady() |
| OCR latency | < 1s | Time from stable detection to OcrPipeline result |
| API response time | < 2s | Time from ScryfallRepositoryResilience call to result |
| Total pipeline | < 3s | Frame capture → detection → OCR → Scryfall → VerificationScreen |
| Frame rate (preview) | 30 fps | Camera preview should remain smooth |
| Memory usage | < 200MB | Peak RAM during active scanning |
| Battery drain | < 10% per hour | While actively scanning |

**Measurement Tools:**
```bash
# Monitor app memory
adb shell dumpsys meminfo com.mtgscanner

# Monitor frame rate
adb shell dumpsys SurfaceFlinger --latency com.mtgscanner

# Monitor battery
adb shell dumpsys battery
```

---

## Troubleshooting

### APK Build Fails

**Error:** `Could not find method compile() for arguments...`
- **Solution:** Update gradle in settings.gradle or use Android Studio's gradle upgrade

**Error:** `Failed to resolve: org.opencv:opencv-android`
- **Solution:** Ensure `jcenter()` repository is enabled in build.gradle (it is by default)

**Error:** `com.google.mlkit:vision-common not found`
- **Solution:** Ensure `google()` repository is first in repositories list

### APK Installation Fails

**Error:** `INSTALL_FAILED_NO_MATCHING_ABIS`
- **Solution:** Device architecture mismatch. Rebuild APK or use compatible device

**Error:** `INSTALL_FAILED_INVALID_APK`
- **Solution:** APK is corrupted. Rebuild with `gradlew clean assembleDebug`

### App Crashes at Launch

**Error:** `java.lang.UnsatisfiedLinkError: Cannot find OpenCV native library`
- **Solution:** OpenCV native libs not loaded. Check CameraPreviewManager initialization

**Error:** `android.view.InflateException: Binary XML file...`
- **Solution:** Layout or resource inflation failed. Check theme/colors XML files

**Error:** `ClassNotFoundException: com.mtgscanner.network.NetworkStateManager`
- **Solution:** Class not compiled. Check imports in MainActivity.kt

### Detection Not Working

**Symptom:** No cards detected in CameraScreen
- **Check 1:** Camera permission granted? See PermissionRequestScreen
- **Check 2:** Lighting sufficient? Try different angle/distance
- **Check 3:** CardDetector threshold correct? May need tuning for device camera

**Symptom:** Cards detected but tracking unstable
- **Check 1:** Ensure CardTracker.STABILITY_FRAMES = 3 is set
- **Check 2:** Frame rate sufficient (30+ fps)? Check camera backpressure

### OCR Not Working

**Symptom:** OCR confidence always 0
- **Check 1:** Image preprocessing working? Check OcrPreprocessor CLAHE/blur
- **Check 2:** ML Kit Text Recognition initialized? Check dependencies in build.gradle

**Symptom:** Wrong card names extracted
- **Check 1:** Regex patterns matching OCR text? See CardOcrProcessor patterns
- **Check 2:** Region extraction parameters correct? Check OcrPreprocessor regions

### Scryfall API Not Responding

**Symptom:** "No internet and no cached cards" error
- **Check 1:** Internet connection active? Test with `adb shell ping google.com`
- **Check 2:** Scryfall API reachable? Test with `curl https://api.scryfall.com/cards/random`
- **Check 3:** RetryPolicy exhausted? Check logs for retry attempts

**Symptom:** Rate limiting (429 error)
- **Check 1:** RetryPolicy configured with backoff? Should be 100ms→5s
- **Check 2:** Too many concurrent requests? Detection pipeline should be single-threaded

---

## Logcat Debugging

Monitor app output during testing:

```bash
# View all app logs
adb logcat com.mtgscanner:V

# View only errors
adb logcat com.mtgscanner:E *:S

# Save logs to file
adb logcat > mtg_scanner_debug.log

# Real-time filtering
adb logcat | grep "MainActivity\|DetectionPipeline\|OcrPipeline\|ScryfallRepositoryResilience"
```

**Key Log Tags:**
- `MainActivity` — App initialization, permission handling
- `DetectionPipeline` — Card detection flow
- `CardTracker` — Tracking and duplicate prevention
- `OcrPipeline` — OCR recognition and fallback
- `ScryfallRepositoryResilience` — Network requests and cache fallback
- `NetworkStateManager` — Connectivity monitoring
- `VerificationScreen` — User verification flow

---

## Post-Testing Checklist

- [ ] All 9-12 cards detected in binder page test
- [ ] OCR accuracy > 80% (name extracted correctly)
- [ ] Scryfall matching > 90% (correct card identified)
- [ ] Offline mode functional (cache verified)
- [ ] Network retry logic working (no premature failures)
- [ ] Collection screen shows all scanned cards
- [ ] Search/filter working in collection
- [ ] Memory usage stable (< 200MB)
- [ ] Battery drain acceptable (< 10%/hour)
- [ ] No crashes during 5+ minute scanning session
- [ ] Low confidence warnings display for blurry cards
- [ ] Error messages clear and actionable

---

## Next Steps After Real-World Testing

1. **Performance Profiling** — Use Android Profiler to identify bottlenecks
2. **Perspective Correction** — Add homography transform for skewed cards
3. **Offline Preload** — Pre-populate cache with common sets (LEA, M21, SLD)
4. **UI Refinement** — Based on user feedback from testing
5. **Release Build** — Generate release APK with ProGuard obfuscation
6. **Play Store Deployment** — If project continues beyond CS898BA

---

## Support & Questions

For issues during testing, check:
1. `AI_Log.md` — Complete development history and decisions
2. `README.md` — Architecture and component documentation
3. Source code comments — Detailed docstrings in all classes
4. Test files in `app/src/test/` and `app/src/androidTest/` — Usage examples

Generated: 2026-07-12 11:47 CST
