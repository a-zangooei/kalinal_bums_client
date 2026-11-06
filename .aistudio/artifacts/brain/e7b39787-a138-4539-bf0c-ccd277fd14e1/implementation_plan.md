# Permanent Android Release Signing & Verification Architecture

An end-to-end, cryptographically enforced release signing system for the Kalinal Android client, ensuring consistent, permanent signatures across all architecture variants, Universal APKs, and the Android App Bundle.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user-confirmed decisions govern this implementation:

- **Confirmed Decision 1 (Keystore Generation)**: The permanent keystore will be generated locally on the user's computer using a dedicated, secure PowerShell script. Private keys and passwords will never be committed to Git or exposed in logs.
- **Confirmed Decision 2 (Keystore Format & Longevity)**: PKCS12 format (`.p12` / `.jks`) with RSA 2048-bit keys, alias `upload`, and 25-year validity (10,000 days) to prevent certificate expiration across the app's entire lifecycle.
- **Confirmed Decision 3 (Build Variants)**: The manual pipeline (`.github/workflows/manual-build.yml`) will package pure **Release build type** artifacts (`assembleRelease` and `bundleRelease`), signed with the permanent release key. Routine CI (`.github/workflows/ci.yml`) remains purely for fast unit/Robolectric tests without requiring secrets.
- **Confirmed Decision 4 (Mandatory Automated Verification)**: Every generated artifact will be verified via `apksigner` and `keytool`. If any architecture variant or the AAB is unsigned or signed with a mismatched certificate fingerprint, the build fails immediately.

---

## 1. Overview & System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        LOCAL ONE-TIME GENERATION                       │
│  PowerShell script generates kalinal-release-key.p12 (PKCS12, 25 yrs)  │
│  User backs up keystore safely & uploads Base64 to GitHub Secrets      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        GITHUB REPOSITORY SECRETS                       │
│  • RELEASE_KEYSTORE_BASE64   • RELEASE_STORE_PASSWORD                  │
│  • RELEASE_KEY_ALIAS         • RELEASE_KEY_PASSWORD                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               .github/workflows/manual-build.yml (RELEASE)             │
│                                                                        │
│  1. Validate Secrets: Fail immediately if any secret is empty          │
│  2. Ephemeral Keystore: Decode to secure temp path ($RUNNER_TEMP)      │
│  3. Map Environment: KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD       │
│  4. Build Release: ./gradlew assembleRelease bundleRelease             │
│  5. Bundletool Splits: Generate signed architecture-specific APKs      │
│  6. Cryptographic Audit (apksigner):                                   │
│     - Verify SHA-256 certificate fingerprint across all APKs & AAB     │
│     - Fail build if any artifact fingerprint mismatches                │
│  7. Generate release-metadata.json (Version, SHA-256 fingerprint, git) │
│  8. Cleanup: Securely shred/remove temporary keystore file             │
│  9. Upload 5 verified release artifacts + metadata                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Key Product Decisions & Trade-Offs

### Safe Alignment with AI Studio Platform Rules
- **Rule**: Never modify `signingConfigs` (`release` and `debugConfig`) or `signingConfig` assignments in `app/build.gradle.kts`.
- **Existing Config in `app/build.gradle.kts`**:
  ```kotlin
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }
  ```
- **Architectural Solution**: By setting the keystore alias to `upload` in the local generation script and mapping `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in the CI runner environment, the existing release signing configuration works out of the box with zero disruption to AI Studio's internal build system.

### Fail-Closed Security Policy
- Release builds will **never** silently fall back to debug signing.
- If `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, or `RELEASE_KEY_PASSWORD` is not configured in the repository, the manual workflow aborts at Step 1 with a visible GitHub annotation:
  `::error::Required release signing secrets are missing. Release build aborted.`

---

## 3. Cryptographic Verification & Audit Strategy

The release workflow will execute an automated audit using `apksigner` and `keytool`:

1. **Fingerprint Extraction**:
   For the Universal APK, each architecture split (`arm64-v8a`, `armeabi-v7a`, `x86_64`), and the App Bundle (`.aab`):
   ```bash
   apksigner verify --print-certs "$APK_FILE"
   ```
2. **Strict Equality Check**:
   The workflow captures the SHA-256 digest of Signer #1 for every artifact:
   - If any artifact lacks a signature: **BUILD FAILED**.
   - If any artifact has a different SHA-256 digest than the master release fingerprint: **BUILD FAILED**.
3. **Release Metadata Publication**:
   A tamper-evident, non-sensitive JSON file (`release-metadata.json`) is packaged alongside artifacts containing:
   - `appName`: Kalinal
   - `applicationId`: `com.aistudio.cullinan.vfkzrw`
   - `versionName`: `1.0`
   - `versionCode`: `1`
   - `gitCommit`: `${{ github.sha }}`
   - `signingCertificateSha256`: `[SHA-256 Fingerprint]`
   - `buildTimestamp`: ISO-8601 UTC timestamp

---

## 4. Local Keystore Generation Procedure (PowerShell)

The user will run this script once on their local Windows computer:

```powershell
# Generate permanent 25-year PKCS12 keystore
keytool -genkeypair -v `
  -keystore kalinal-release-key.p12 `
  -storetype PKCS12 `
  -alias upload `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -dname "CN=Kalinal Mobile, OU=Release, O=Kalinal, L=Birjand, ST=South Khorasan, C=IR"

# Copy base64 string directly to clipboard for GitHub Secrets
[Convert]::ToBase64String([IO.File]::ReadAllBytes("kalinal-release-key.p12")) | Set-Clipboard
Write-Host "Keystore generated and base64 copied to clipboard!"
```

The user will configure 4 GitHub Secrets in repository Settings -> Secrets and variables -> Actions:
1. `RELEASE_KEYSTORE_BASE64`: Copied from clipboard.
2. `RELEASE_STORE_PASSWORD`: The password chosen during generation.
3. `RELEASE_KEY_ALIAS`: `upload`
4. `RELEASE_KEY_PASSWORD`: The key password (identical to store password in PKCS12).

---

## 5. File-by-File Changes

### 1. `.github/workflows/manual-build.yml`
- Switch build commands to release: `./gradlew assembleRelease bundleRelease`.
- Add validation step for required release secrets.
- Securely decode keystore to `${{ runner.temp }}/release.keystore` with `chmod 600`.
- Sign bundletool-extracted device splits using the release keystore.
- Add automated `apksigner` and `keytool` certificate fingerprint audit.
- Generate and upload `release-metadata.json`.
- Add post-build cleanup step (`rm -f ${{ runner.temp }}/release.keystore`) running on success and failure (`if: always()`).

### 2. `.github/workflows/ci.yml`
- Preserve lightweight test execution (`./gradlew testDebugUnitTest`).
- Confirm zero requirement for release secrets on routine pull requests and commits.

### 3. `CI_GUIDE.md` & Documentation
- Document the permanent release signing system, secret configuration, and PowerShell keystore generation steps.
- Explain the role of Play Protect and update compatibility (uninstalling old debug-signed app once to adopt the permanent release key).

---

## 6. Validation Plan

1. **Local Project Verification**: Run `compile_applet` to ensure the project compiles and signing configuration declarations are preserved.
2. **Missing Secrets Test**: Verify that the workflow halts if secrets are omitted.
3. **Full Signature Consistency Test**: Run the manual release build on GitHub Actions with configured secrets and confirm that all 5 published artifacts share the exact same SHA-256 certificate fingerprint in `release-metadata.json`.
