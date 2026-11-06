# Reliable Android CI & Manual Multi-Device Build Architecture

A diagnostic analysis and architectural plan to separate lightweight automated CI checks from an on-demand, manual multi-device APK and AAB build pipeline for the Kalinal Android client.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed by the user and govern this implementation plan:

- **Confirmed Decision 1 (Workflow Separation)**: Split the CI/CD pipeline into **two independent workflow files**:
  1. `.github/workflows/ci.yml`: Automated, lightweight verification triggered on every `push` and `pull_request` (tests and reports only; no APK or AAB builds).
  2. `.github/workflows/manual-build.yml`: Dedicated on-demand build workflow triggered exclusively via `workflow_dispatch` to package Universal APK, architecture-specific APKs (ARM64, ARMv7, x86_64), and the Android App Bundle.
- **Confirmed Decision 2 (Failure Root Cause in Run 42188336707)**:
  - Error: `find: ‘app/build/outputs/apk’: No such file or directory` -> `Error: No APK file generated in app/build/outputs/apk for target armeabi-v7a`.
  - Cause: In AGP 8.x, passing the internal IDE property `-Pandroid.injected.build.abi` without Android Studio IDE flags causes Gradle to package the APK into `app/build/intermediates/apk/debug/` rather than creating the standard `app/build/outputs/apk/debug/` directory structure.
  - Solution: Use official Android App Bundle + `bundletool` device extraction and broader output search (`find app/build -type f -name "*.apk"`), or standard ABI-split packaging, ensuring guaranteed artifact discovery.
- **Confirmed Decision 3 (Manual Build Scope)**: A single manual run packages all 4 APK targets plus the Android App Bundle in one execution, publishing distinct, clearly labeled artifacts.

---

## 1. Diagnostic Findings & Root Cause Analysis

### Investigation of Run 42188336707

| Job | Status | Log Evidence / Observed Behavior | Root Cause |
|---|---|---|---|
| **🧪 test (JDK 21)** | **SUCCESS** | All Unit and Robolectric tests passed on JDK 21 | Upgrading to JDK 21 resolved the SDK 36 class version incompatibility. |
| **📱 Universal APK** | **SUCCESS** | Standard `./gradlew assembleDebug` created `app/build/outputs/apk/debug/app-debug.apk` | Unconstrained debug builds follow the standard output path. |
| **📦 Android App Bundle** | **SUCCESS** | `./gradlew bundleDebug` + `bundletool` generated `all-splits.apks` and `bundle.aab` | Official bundle tooling operates reliably across all runners. |
| **📱 ARM64-v8a** | **FAILED** | `find: ‘app/build/outputs/apk’: No such file or directory` | AGP internal injection property bypasses standard `outputs/apk` directory creation. |
| **📱 ARMv7 (armeabi-v7a)**| **FAILED** | `find: ‘app/build/outputs/apk’: No such file or directory` | Same as ARM64. |
| **📱 x86_64** | **FAILED** | `find: ‘app/build/outputs/apk’: No such file or directory` | Same as ARM64. |

### Technical Analysis of `-Pandroid.injected.build.abi`
In modern Android Gradle Plugin versions, `android.injected.build.abi` is an internal private contract designed specifically for Android Studio's Instant Run / Deploy-to-Device flow. When invoked from standard CLI without Android Studio IDE flags:
1. AGP places the intermediate APK inside `app/build/intermediates/apk/debug/` instead of publishing to `app/build/outputs/apk/debug/`.
2. Furthermore, unless NDK native compilation or ABI packaging splits are declared, `-Pandroid.injected.build.abi` does not strip native libraries from pre-compiled AARs (`libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`).
3. To reliably produce true architecture-specific APKs, the standard Google-recommended mechanism is either **Google `bundletool` device extraction** from the AAB or **Gradle ABI splits**.

---

## 2. Recommended Workflow Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          TRIGGER STRATEGY                               │
├───────────────────────────────────┬─────────────────────────────────────┤
│  Automatic: push & pull_request   │  Manual: workflow_dispatch          │
│  (Every commit / PR branch)       │  (On-demand via GitHub Actions UI)  │
└─────────────────┬─────────────────┴──────────────────┬──────────────────┘
                  │                                    │
                  ▼                                    ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│     .github/workflows/ci.yml      │ │ .github/workflows/manual-build.yml│
│                                   │ │                                   │
│  • JDK 21 (Temurin)               │ │  • Select Ref / Branch            │
│  • Keystore & Secrets prep        │ │  • Run Tests (Quality Gate)       │
│  • ./gradlew testDebugUnitTest    │ │  • Build Universal APK            │
│  • Upload test-reports (always)   │ │  • Build ARM64-v8a APK            │
│                                   │ │  • Build ARMv7 APK                │
│  ⏱️ Duration: ~1.5 - 2 minutes    │ │  • Build x86_64 APK               │
│  🛑 NO APK or AAB generation      │ │  • Build Android App Bundle (AAB) │
│  🔄 Concurrency: cancel-in-prog   │ │  • Upload 5 distinct artifacts    │
└───────────────────────────────────┘ │  🔄 Concurrency: NO cancel        │
                                      └───────────────────────────────────┘
```

### Key Separation Characteristics

1. **Lightweight Automated CI (`.github/workflows/ci.yml`)**:
   - Executes strictly on `push: branches: ['**']` and `pull_request`.
   - Never consumes runner minutes compiling packaging variants on routine code commits.
   - Uploads unit test and Robolectric reports even on test failure (`if: always()`).
   - Concurrency group `ci-${{ github.ref }}` with `cancel-in-progress: true` cancels stale runs when new commits are pushed quickly.

2. **On-Demand Manual Pipeline (`.github/workflows/manual-build.yml`)**:
   - Executes strictly on `workflow_dispatch`.
   - Concurrency group `manual-build-${{ github.ref }}` with `cancel-in-progress: false` ensures a routine git push never terminates an active manual build.
   - Generates all 5 package variations with robust fallback search across both `app/build/outputs/` and `app/build/intermediates/`.

---

## 3. File-by-File Changes

### File 1: `.github/workflows/ci.yml` (New File)
- **Role**: Dedicated lightweight CI.
- **Triggers**: `push` (all branches), `pull_request` (all branches).
- **Steps**:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (JDK 21, Temurin, Gradle cache)
  3. Prepare dummy keystore (`debug.keystore.base64` decode) and `.env`
  4. `./gradlew testDebugUnitTest --no-daemon --stacktrace`
  5. `actions/upload-artifact@v4` for test reports (`app/build/reports/tests/testDebugUnitTest/`) with `if: always()`

### File 2: `.github/workflows/manual-build.yml` (New File)
- **Role**: Comprehensive manual release and device artifact builder.
- **Triggers**: `workflow_dispatch` only.
- **Jobs**:
  - `test-verification`: Runs unit tests first to ensure the selected ref builds cleanly.
  - `build-universal`: Runs `./gradlew assembleDebug`, searches `app/build` for the APK, and packages `Kalinal-Universal-debug.apk`.
  - `build-bundle-and-device-apks`: Runs `./gradlew bundleDebug`, uses official Google `bundletool` (v1.17.2) to extract:
    - `Kalinal-Universal-from-bundle.apk`
    - `Kalinal-arm64-v8a-standalone.apk`
    - `Kalinal-armeabi-v7a-standalone.apk`
    - `Kalinal-x86_64-standalone.apk`
    - `Kalinal-AppBundle.aab`
  - Uploads artifacts with 14-day retention.
  - Publishes a formatted Markdown summary to `$GITHUB_STEP_SUMMARY`.

### File 3: `.github/workflows/android-ci.yml` (Deletion/Replacement)
- The legacy single monolithic workflow file will be replaced by the two focused workflow files above to prevent dual-triggering or overlapping configurations.

---

## 4. Verification & Validation Plan

1. **Local Syntax & Compilation Verification**:
   - Verify workflow YAML structures and schema validation.
   - Run `compile_applet` to confirm no regressions in project dependencies or build configuration.
2. **Workflow Trigger Verification**:
   - Push a test commit to verify `.github/workflows/ci.yml` executes only the test suite and terminates quickly.
   - Verify that no APK jobs are triggered by git push.
3. **Manual Execution Verification**:
   - Trigger `.github/workflows/manual-build.yml` via GitHub Actions web UI (`Run workflow`).
   - Verify all 5 artifacts appear in the run's **Artifacts** panel:
     - `Kalinal-Universal-APK`
     - `Kalinal-arm64-v8a-APK`
     - `Kalinal-armeabi-v7a-APK`
     - `Kalinal-x86_64-APK`
     - `Kalinal-AppBundle-AAB`
4. **Binary Inspection**:
   - Unzip generated APKs and verify presence of `classes.dex`, `resources.arsc`, and appropriate native `.so` architectures.

---

## 5. Risks & Compatibility Safeguards

- **Keystore Missing Risk**: Runner initializes `debug.keystore` from `debug.keystore.base64` or generates one via `keytool` on the fly, preventing signing crashes.
- **Runner Disk Space**: Cleaning intermediate caches before bundletool extraction prevents runner out-of-memory or out-of-disk errors.
- **Secrets Security**: No API tokens, passwords, or credentials are logged or exposed.
- **Zero Application Code Changes**: Neither Kotlin sources, XML layouts, nor Gradle dependency versions are altered.
