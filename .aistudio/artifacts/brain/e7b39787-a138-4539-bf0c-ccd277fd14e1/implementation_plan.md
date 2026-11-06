# CI Workflow Multi-Device APK Discovery & Phase 1.1 Mobile Testing Plan

A targeted solution to resolve the artifact copying failure in architecture-specific GitHub Actions jobs (`ARMv7`, `ARM64`, `x86_64`) followed by a structured approach to validate and debug mobile runtime behavior.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed by the user and guide this implementation:

- **Confirmed Decision 1 (Execution Priority)**: Fix the CI workflow first to ensure all 6 matrix jobs pass and publish valid artifacts, then immediately proceed to mobile runtime testing and debugging (Phase 1.1).
- **Confirmed Decision 2 (APK Discovery Strategy)**: Use robust dynamic auto-detection in `.github/workflows/android-ci.yml` using `find` to discover any generated APK in `app/build/outputs/apk/debug/`, validating that an APK exists before copying to `build-output/Kalinal-${{ matrix.target.id }}-debug.apk`.

---

## 1. Overview & Problem Analysis

In GitHub Actions run `42125120768`, Gradle successfully compiled and packaged debug APKs for all target architectures:
- Universal APK: **Success**
- Android App Bundle (AAB): **Success**
- Unit & Robolectric Tests: **Success**
- ARMv7 (32-bit): **Failed at copy step**
- ARM64 (64-bit): **Failed at copy step**
- x86_64: **Failed at copy step**

### Root Cause
When `-Pandroid.injected.build.abi=<abi>` or architecture filters are supplied, Android Gradle Plugin (AGP) generates names reflecting the ABI (e.g., `app-arm64-v8a-debug.apk` or subdirectories) rather than the standard unconstrained filename `app-debug.apk`. The workflow failed with `cp: cannot stat 'app/build/outputs/apk/debug/app-debug.apk': No such file or directory`.

---

## 2. Key Product Decisions & Trade-Offs

### Dynamic Auto-Detection vs. Hardcoded Naming Conventions
- **Chosen Approach**: Scan `app/build/outputs/apk/debug/` recursively for `*.apk` files using `find`, verify at least one file was created, and copy the first matching binary to the target artifact directory.
- **Why**: Protects against variations in AGP naming across minor Gradle versions, ABI naming conventions (e.g. `armeabi-v7a` vs `armv7`), and directory nesting.
- **Fail-Safe**: If no APK is found, the script outputs a clear GitHub Actions error annotation (`::error::No APK found in app/build/outputs/apk/debug`) and exits with code 1, preventing empty or corrupted artifacts.

---

## 3. Technical Architecture & CI Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│                      GitHub Actions                         │
│                    (Push / PR Trigger)                      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Job 1: 🧪 test (JDK 21)                     │
│  - RSA Crypto Unit Tests                                    │
│  - ASP.NET AJAX Delta Stream Parser Tests                   │
│  - Reservation HTML Parser Tests                            │
│  - Robolectric Tests (@Config sdk = 36)                     │
└──────────────┬──────────────────────────────┬───────────────┘
               │ (needs: test)                │ (needs: test)
               ▼                              ▼
┌───────────────────────────────┐ ┌───────────────────────────┐
│ Job 2: 📱 build-multi-device  │ │   Job 3: 📦 build-bundle  │
│  Matrix Strategy (4 targets): │ │  - bundleDebug            │
│   • universal                 │ │  - bundletool splits      │
│   • arm64-v8a                 │ │  - Universal APK bundle   │
│   • armeabi-v7a               │ └─────────────┬─────────────┘
│   • x86_64                    │               │
│                               │               │
│  [Dynamic APK Discovery]:     │               │
│   find outputs/apk/debug      │               │
│   cp to Kalinal-<target>.apk  │               │
└──────────────┬────────────────┘               │
               │                                │
               ▼                                ▼
┌─────────────────────────────────────────────────────────────┐
│                     GitHub Artifacts                        │
│  • Kalinal-universal-APK                                    │
│  • Kalinal-arm64-v8a-APK                                    │
│  • Kalinal-armeabi-v7a-APK                                  │
│  • Kalinal-x86_64-APK                                       │
│  • Kalinal-AppBundle-and-Splits                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Implementation Steps

### Step 1: Update `.github/workflows/android-ci.yml`
Replace the rigid `cp` command in the `build-multi-device` job with:
```bash
- name: Build APK for ${{ matrix.target.name }}
  run: |
    if [ "${{ matrix.target.abi }}" = "universal" ]; then
      ./gradlew assembleDebug --no-daemon --stacktrace
    else
      ./gradlew assembleDebug -Pandroid.injected.build.abi=${{ matrix.target.abi }} --no-daemon --stacktrace
    fi

    mkdir -p build-output
    APK_PATH=$(find app/build/outputs/apk -type f -name "*.apk" | head -n 1)
    if [ -z "$APK_PATH" ]; then
      echo "::error::No APK file generated for target ${{ matrix.target.id }}"
      exit 1
    fi
    echo "Found APK at $APK_PATH, copying to build-output/Kalinal-${{ matrix.target.id }}-debug.apk"
    cp "$APK_PATH" "build-output/Kalinal-${{ matrix.target.id }}-debug.apk"
```

### Step 2: Verification & Local Build Check
- Run `compile_applet` to confirm Gradle build configuration integrity.
- Verify the workflow syntax and parameter references.

### Step 3: Git Diff & Push Instructions
- Provide the exact diff and Git instructions so the user can push the fix to `github.com/a-zangooei/kalinal_byms_client`.

### Step 4: Phase 1.1 Mobile Runtime Testing Roadmap
Once the CI pipeline is green across all 6 jobs:
- Test real authentication on physical devices (network timeout handling, captive portals, intranet routing).
- Validate ASP.NET session continuity (`ASP.NET_SessionId`) between captcha retrieval and RSA login POST.
- Inspect weekly schedule rendering, day tabs, and meal reservation badges under varying network latencies.
