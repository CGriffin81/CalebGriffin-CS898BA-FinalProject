# Pre-Launch Checklist

## Build Environment Verification
- [ ] JDK 8+ installed (verify: `java -version`)
- [ ] Android SDK installed with API 34 (verify: `android list targets`)
- [ ] Android NDK installed (for OpenCV)
- [ ] Gradle 7.0+ available (verify: `gradle -version`)
- [ ] JAVA_HOME environment variable set
- [ ] ANDROID_HOME environment variable set

## Source Code Verification
- [ ] All 26 Kotlin source files present in `app/src/main/java/com/mtgscanner/`
- [ ] No compilation errors (verify: run syntax check)
- [ ] All imports resolved (no missing dependencies)
- [ ] MainActivity.kt fully wired with all components
- [ ] ScryfallRepositoryResilience initialized in MainActivity
- [ ] NetworkStateManager monitoring active
- [ ] Error handling composables integrated into VerificationScreen

## Dependency Verification
- [ ] CameraX (core, camera2, lifecycle) — v1.3.0
- [ ] ML Kit Text Recognition — v16.0.0
- [ ] OpenCV Android SDK — v4.8.1
- [ ] Room (runtime, ktx) — v2.6.1
- [ ] Retrofit + Gson — v2.9.0
- [ ] Coil (image loading) — v2.5.0
- [ ] Jetpack Compose — v1.6.0
- [ ] Material3 — v1.1.2
- [ ] Kotlin Coroutines — v1.7.3
- [ ] Test frameworks (JUnit, Espresso, Mockito) — latest

## Configuration Verification
- [ ] AndroidManifest.xml includes:
  - [ ] CAMERA permission (required)
  - [ ] INTERNET permission
  - [ ] ACCESS_NETWORK_STATE permission
  - [ ] READ_MEDIA_IMAGES permission
  - [ ] Camera hardware feature declared
  - [ ] MainActivity as launcher activity
  - [ ] Portrait orientation lock
- [ ] build.gradle.kts configured:
  - [ ] compileSdk = 34
  - [ ] minSdk = 24
  - [ ] targetSdk = 34
  - [ ] versionCode = 1
  - [ ] versionName = "0.1.0"
  - [ ] All 40+ dependencies listed
- [ ] settings.gradle.kts configured:
  - [ ] Google repository added
  - [ ] Maven Central repository added
  - [ ] Gradle plugins configured

## Component Initialization Verification
- [ ] Database initialized first (ScannedCardDatabase.getInstance)
- [ ] Network components initialized:
  - [ ] NetworkStateManager created
  - [ ] NetworkCacheManager created
  - [ ] RetryPolicy configured (3 retries, 100ms→5s exponential backoff)
- [ ] ScryfallRepositoryResilience created with all dependencies
- [ ] Detection pipeline created and callback wired
- [ ] OCR pipeline created and ready
- [ ] Fuzzy matcher created and ready
- [ ] Camera manager created and lifecycle bound

## Offline Mode Verification
- [ ] NetworkCacheManager configured with 7-day TTL
- [ ] SharedPreferences backend for cache storage
- [ ] Cache preload mechanism in place
- [ ] Common sets available for preload (LEA, M21, SLD)
- [ ] Cache fallback chain working (identity → fuzzy → search → cache)

## Error Handling Verification
- [ ] ErrorSnackbar composable available
- [ ] OfflineNotice composable available
- [ ] LowConfidenceWarning composable available
- [ ] ErrorDialog composable available
- [ ] PermissionDeniedScreen composable available
- [ ] LoadingOverlay composable available
- [ ] All error paths integrated into MainActivity and screens

## Testing Status
- [ ] Unit tests pass (58 integration test cases)
  - [ ] Detection pipeline tests (7 tests)
  - [ ] OCR pipeline tests (10 tests)
  - [ ] Fuzzy matching tests (9 tests)
  - [ ] Database tests (11 tests)
  - [ ] End-to-end tests (6 tests)
  - [ ] Navigation tests (15 tests)

## Device Preparation
- [ ] Samsung Galaxy S23 connected via USB
- [ ] USB debugging enabled in Developer Options
- [ ] Device screen unlocked during installation
- [ ] Sufficient storage available (> 500MB free)
- [ ] Network connectivity available (WiFi or mobile)

## Pre-Deployment APK Build
- [ ] Clean build directory: `gradle clean`
- [ ] Build debug APK: `gradle assembleDebug`
- [ ] Verify APK created at `app/build/outputs/apk/debug/app-debug.apk`
- [ ] APK file size reasonable (< 150MB for typical Android build)
- [ ] APK signed with debug key

## Deployment
- [ ] APK installed via adb: `adb install -r app-debug.apk`
- [ ] Installation successful (no errors)
- [ ] App launches successfully
- [ ] PermissionRequestScreen appears if permissions not granted
- [ ] Permission grant successful
- [ ] MainMenuScreen displays with "Start Scanning" and "View Collection" buttons

## Runtime Verification (First Launch)
- [ ] Database initialized (no crashes)
- [ ] Camera initialized (CameraScreen shows preview)
- [ ] Detection pipeline ready (frame count visible)
- [ ] Network monitoring active (online/offline aware)
- [ ] Cache manager initialized (can preload sets)
- [ ] No runtime errors in logcat

## Real-World Testing Readiness
- [ ] Binder or collection of 9-12 MTG cards available
- [ ] Good lighting conditions (> 100 lux)
- [ ] Device camera focused and clean
- [ ] Battery charged (> 80%)
- [ ] Network connection stable
- [ ] Logcat monitoring ready: `adb logcat com.mtgscanner:V`

---

## Build & Deploy Command Sequence

```bash
# 1. Navigate to project
cd ~/CalebGriffin-CS898BA-FinalProject

# 2. Verify environment
java -version
gradle -version

# 3. Clean and build
gradle clean
gradle assembleDebug

# 4. Verify APK
ls -lh app/build/outputs/apk/debug/app-debug.apk

# 5. Connect device
adb devices

# 6. Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 7. Launch app
adb shell am start -n com.mtgscanner/.MainActivity

# 8. Monitor logs
adb logcat com.mtgscanner:V
```

---

## Critical Success Criteria

✓ **All items checked** = Ready for real-world testing
✗ **Any item unchecked** = Resolve before deployment

Generated: 2026-07-12 11:47 CST
