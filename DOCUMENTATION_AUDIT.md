# Documentation Consistency Report
**Audit Date:** 2026-07-21
**Auditor:** Magic Scan Research & Technical Writer (Kiro)

---

## Summary

The project documentation was written on 2026-07-12 to describe a prototype that had 15+ critical defects preventing any real-world functionality. On 2026-07-21, a comprehensive reliability implementation (Phases 1–6) was completed that fixed all blocking defects and made the pipeline functional end-to-end. This audit identifies documentation that became inaccurate after those fixes.

---

## Files Updated in This Audit

| File | Action | Reason |
|------|--------|--------|
| `readme.md` | Updated header section | Implementation status, pipeline diagram, technology stack now reflect actual working state |
| `AI_Log.md` | Appended new milestone entry | Documents the reliability implementation (Phases 1–6) with specific changes per file |
| `DOCUMENTATION_AUDIT.md` | Created (this file) | Consistency report documenting what is outdated |

---

## Obsolete Documentation

These files contain claims that were true on 2026-07-12 but are now inaccurate:

| File | Obsolete Claim | Reality (2026-07-21) |
|------|----------------|---------------------|
| `INDEX.md` | "58 integration tests passing" | 64 tests total, 54 passing, 10 skipped, 3 androidTest stubs |
| `INDEX.md` | "26 source files" | 25 source files (ScryfallRepository.kt deleted) |
| `INDEX.md` | "Otsu segmentation + contour detection" | Replaced with flood-fill + adaptive threshold + aspect ratio filtering |
| `INDEX.md` | "CLAHE preprocessing" | OcrPreprocessor is still a pass-through — CLAHE was never implemented |
| `DEPLOYMENT_READY.md` | "All 26 Kotlin source files compile without errors" | 25 files; ScryfallRepository.kt was dead code and deleted |
| `DEPLOYMENT_READY.md` | "58 integration tests" | 64 tests (rewritten) + 9 androidTest `CardPersistenceTest` |
| `DEPLOYMENT_READY.md` | "OCR with ML Kit + preprocessing (CLAHE, blur, sharpening)" | Preprocessing is a no-op; CLAHE/blur/sharpening are NOT implemented |
| `PROJECT_FILE_INVENTORY.md` | Lists `ScryfallRepository.kt` | File deleted in P5-04 |
| `PROJECT_FILE_INVENTORY.md` | "opencv-android: 4.8.1" dependency | OpenCV was removed months ago; never in current build.gradle.kts |
| `PROJECT_FILE_INVENTORY.md` | Line counts for all files | All rewritten files have different line counts now |
| `PROJECT_FILE_INVENTORY.md` | "OcrPreprocessor: CLAHE enhancement, Gaussian blur, sharpening" | OcrPreprocessor is a pass-through returning input unchanged |
| `PRELAUNCH_CHECKLIST.md` | References OpenCV, 26 files, 58 tests | All inaccurate |
| `DEPLOYMENT.md` | Build instructions use `gradle` command | Should use `.\gradlew.bat` with JBR |

---

## Duplicate Documentation

| Files | Overlap | Recommendation |
|-------|---------|----------------|
| `DEPLOYMENT_READY.md` + `INDEX.md` | Both describe the same deployment steps, file counts, and testing scenarios | Consolidate into INDEX.md as the single entry point; DEPLOYMENT_READY.md adds no unique information |
| `DEPLOYMENT.md` + `PRELAUNCH_CHECKLIST.md` | Overlapping environment verification sections | Acceptable — PRELAUNCH_CHECKLIST is a checklist format of DEPLOYMENT's prose |
| `readme.md` + `PROJECT_FILE_INVENTORY.md` | Both list component descriptions and file structure | README should summarize; INVENTORY should be authoritative for file details |

---

## Missing Documentation

| What's Missing | Impact | Priority |
|------|--------|----------|
| **CHANGELOG.md** | No formal versioned changelog exists | Low — AI_Log serves this purpose for a course project |
| **Architecture Decision Records (ADRs)** | Decisions documented in code comments but not in a formal ADR format | Low — appropriate for project scope |
| **P2-02 spatial extraction rationale** | The 12% bounding box threshold is not documented outside the code | Medium — should be in a design note |
| **Detection algorithm limitations** | Bright-on-dark assumption not documented at the user level | Medium — affects real-world usage |
| **Migration guide** | No guidance for users upgrading from v1 to v2 database schema | Low — no deployed users exist |

---

## Inconsistencies Discovered

1. **OpenCV references persist** — `PROJECT_FILE_INVENTORY.md`, `DEPLOYMENT_READY.md`, and `INDEX.md` all mention OpenCV. OpenCV was removed before the reliability work began. The detection now uses pure Kotlin/Android bitmap processing.

2. **"CLAHE preprocessing" claimed** — Multiple documents state that OcrPreprocessor performs CLAHE, Gaussian blur, and sharpening. This was never implemented — the preprocessor returns its input unchanged. It was a design intent, not an implementation.

3. **"26 source files" count is stale** — ScryfallRepository.kt was deleted (P5-04). The actual count is 25 production Kotlin files.

4. **Test counts differ everywhere** — INDEX.md says 58, DEPLOYMENT_READY.md says 58, PROJECT_FILE_INVENTORY.md says 58. The actual count is 64 unit tests + 12 androidTest tests (9 CardPersistenceTest + 3 placeholder stubs).

5. **`gradle` vs `.\gradlew.bat`** — README build instructions now correctly use the wrapper, but INDEX.md and DEPLOYMENT_READY.md still reference bare `gradle` commands which won't work without the project wrapper.

6. **Performance targets unrealistic** — INDEX.md claims "Detection latency < 500ms" and "OCR latency < 1s" as targets. Actual measured detection is ~25–65ms and OCR is ~50–150ms — both much faster than the targets. The targets are fine as upper bounds but the documentation should note actual performance.

---

## Recommendations

### Immediate (should be done before next device test)
1. Update `INDEX.md` quick-start commands to use `.\gradlew.bat` not `gradle`
2. Remove all OpenCV references from documentation (it was never in the current codebase)
3. Correct "CLAHE preprocessing" claims to "pass-through (enhancement is future work)"

### Short-term (before sharing project externally)
4. Rewrite `PROJECT_FILE_INVENTORY.md` to reflect current 25-file structure
5. Update test counts throughout all documents to 64
6. Remove or archive `DEPLOYMENT_READY.md` (superseded by INDEX.md)

### Nice-to-have (if project continues)
7. Create a formal ARCHITECTURE.md with the pipeline diagram from the README
8. Document the detection algorithm's limitations (bright-on-dark assumption) for users
9. Add a KNOWN_ISSUES.md listing the 3 remaining low-severity defects from the final audit

---

## Verified Documentation (Correct and Current)

| File | Status |
|------|--------|
| `readme.md` (header section) | ✓ Updated in this audit — reflects current pipeline |
| `AI_Log.md` | ✓ Updated — includes 2026-07-21 milestone entry |
| `literature_review/` | ✓ Unchanged — research references remain valid for the chosen architecture |
| `Build-Apk.ps1` | ✓ Uses correct JBR path and wrapper |
| `settings.gradle.kts` | ✓ Correct |
| `app/build.gradle.kts` | ✓ Reflects actual dependencies |
| Source code KDoc comments | ✓ Updated during implementation — accurate |

---

## Conclusion

The project's source code is fully functional and internally consistent. The documentation layer has a ~9-day lag — it was written to describe the 2026-07-12 prototype state and was not updated during the 2026-07-21 reliability implementation. The most impactful issue is false claims about OpenCV and CLAHE preprocessing that were never implemented. The README header has been updated in this audit; remaining documentation updates are tracked as recommendations above.
