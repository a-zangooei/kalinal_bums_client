# Resilient Cryptographic Release Signature Audit Plan

A targeted, fail-closed solution to resolve the certificate fingerprint extraction failure in `.github/workflows/manual-build.yml`, standardizing SHA-256 extraction across `apksigner` and `keytool`.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user-confirmed decisions govern this implementation:

- **Confirmed Decision 1 (AAB Extraction Mechanism)**: Use `keytool` with `-printcert -jarfile` combined with a direct `META-INF/*.RSA` pipeline fallback and lowercase hex normalization to extract the AAB certificate fingerprint reliably.
- **Confirmed Decision 2 (Diagnostic Log Verbosity)**: Print sanitized stdout and stderr output on any signature failure to facilitate clear troubleshooting without leaking secrets, keystore paths, or passwords.
- **Confirmed Decision 3 (Strict Fail-Closed Enforcement)**: The build must exit with code 1 if:
  - An APK or AAB fails signature extraction.
  - An extracted fingerprint is not exactly 64 hexadecimal characters.
  - Any split APK or the AAB has a fingerprint that differs from the master Universal APK fingerprint.
  - No conditions will allow an empty or unverified fingerprint to print a success message.

---

## 1. Root Cause Analysis

### Previous Implementation
In `.github/workflows/manual-build.yml`:
```bash
MASTER_FINGERPRINT=$($APKSIGNER verify --print-certs "$UNIVERSAL_APK" | grep "Signer #1 certificate SHA-256 digest:" | awk '{print $NF}' | tr '[:upper:]' '[:lower:]')
```

### Why it Failed
1. **Stdout vs. Stderr Redirection**: `apksigner` in certain Android build-tools revisions or runner environments emits certificate details or diagnostic warnings to stderr instead of stdout. Piping without `2>&1` caused the subshell to capture an empty string.
2. **Brittle String Pattern Matching**: Grepping strictly for `"Signer #1 certificate SHA-256 digest:"` fails if build-tools formats the output slightly differently (e.g., `"Certificate SHA-256 digest:"`, `"Signer #1 (v1/v2/v3):"`, or localized/reformatted prefixes).
3. **Format Standardization**: Without stripping colons, spaces, and validating against `^[0-9a-f]{64}$`, empty strings or mismatched formatting bypassed regex checks.
4. **Permissive AAB Check**: The previous check `if [ -n "$AAB_FINGERPRINT" ] ...` allowed an empty AAB fingerprint to be silently skipped.

---

## 2. Resilient Extraction Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        APK SIGNATURE AUDIT                             │
│                                                                        │
│  RAW_OUT=$($APKSIGNER verify --print-certs "$APK" 2>&1)                │
│                                                                        │
│  1. Check exit code == 0 (else fail with sanitized diagnostic log)     │
│  2. Extract SHA-256 line -> Strip colons, whitespace -> Lowercase      │
│  3. Fallback: Regex match 64 hex characters or 32 colon-hex pairs       │
│  4. Assert string matches: ^[0-9a-f]{64}$ (else fail build)           │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Master Fingerprint
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     SPLIT APKS & AAB AUDIT (FAIL-CLOSED)               │
│                                                                        │
│  • ARM64, ARMv7, x86_64 splits must match Master Fingerprint           │
│  • AAB extracted via keytool -printcert -jarfile 2>&1                   │
│  • AAB fingerprint must match Master Fingerprint                       │
│  • Any mismatch or empty value -> Exit code 1                          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Implementation Details

### Step 1: Encapsulate Robust Extraction Functions in `manual-build.yml`

```bash
extract_apk_fingerprint() {
  local target_apk="$1"
  local raw_output
  raw_output=$("$APKSIGNER" verify --print-certs "$target_apk" 2>&1)
  local status=$?

  if [ $status -ne 0 ]; then
    echo "::error::apksigner verification failed for $target_apk (exit code $status)"
    echo "--- apksigner diagnostic output ---"
    echo "$raw_output"
    echo "-----------------------------------"
    return 1
  fi

  # Primary extraction: search for SHA-256 digest line
  local fp
  fp=$(echo "$raw_output" | grep -i "SHA-256" | grep -i "digest" | sed -E 's/.*SHA-256.*digest:?[[:space:]]*//I' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')

  # Fallback extraction: look for 64 contiguous hex chars on a line with SHA-256
  if ! echo "$fp" | grep -qE '^[0-9a-f]{64}$'; then
    fp=$(echo "$raw_output" | grep -i "SHA-256" | grep -oE '[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){31}|[0-9a-fA-F]{64}' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]' | head -n 1)
  fi

  if ! echo "$fp" | grep -qE '^[0-9a-f]{64}$'; then
    echo "::error::Failed to extract a valid 64-hex SHA-256 fingerprint from $target_apk"
    echo "--- apksigner diagnostic output ---"
    echo "$raw_output"
    echo "-----------------------------------"
    return 1
  fi

  echo "$fp"
  return 0
}
```

### Step 2: Encapsulate Robust AAB Fingerprint Extraction

```bash
extract_aab_fingerprint() {
  local aab_file="$1"
  local raw_output
  raw_output=$(keytool -printcert -jarfile "$aab_file" 2>&1)
  local status=$?

  # If -jarfile doesn't find cert, fall back to extracting META-INF cert stream
  if [ $status -ne 0 ] || ! echo "$raw_output" | grep -qi "SHA256"; then
    raw_output=$(unzip -p "$aab_file" 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' 2>/dev/null | keytool -printcert 2>&1)
    status=$?
  fi

  if [ $status -ne 0 ]; then
    echo "::error::keytool verification failed for AAB: $aab_file (exit code $status)"
    echo "--- keytool diagnostic output ---"
    echo "$raw_output"
    echo "---------------------------------"
    return 1
  fi

  local fp
  fp=$(echo "$raw_output" | grep -i "SHA256:" | sed -E 's/.*SHA256:[[:space:]]*//I' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')

  if ! echo "$fp" | grep -qE '^[0-9a-f]{64}$'; then
    fp=$(echo "$raw_output" | grep -i "SHA256" | grep -oE '[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){31}|[0-9a-fA-F]{64}' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]' | head -n 1)
  fi

  if ! echo "$fp" | grep -qE '^[0-9a-f]{64}$'; then
    echo "::error::Failed to extract a valid 64-hex SHA-256 fingerprint from AAB: $aab_file"
    echo "--- keytool diagnostic output ---"
    echo "$raw_output"
    echo "---------------------------------"
    return 1
  fi

  echo "$fp"
  return 0
}
```

### Step 3: Enforce Strict Equality Across All Outputs

```bash
MASTER_FINGERPRINT=$(extract_apk_fingerprint "$UNIVERSAL_APK") || exit 1
echo "Master Release Certificate SHA-256: $MASTER_FINGERPRINT"

# Audit each extracted split APK
for APK in $(find extracted_arm64 extracted_armv7 extracted_x86_64 -name "*.apk"); do
  CURRENT_FP=$(extract_apk_fingerprint "$APK") || exit 1
  if [ "$CURRENT_FP" != "$MASTER_FINGERPRINT" ]; then
    echo "::error::Certificate fingerprint mismatch in $APK! Expected: $MASTER_FINGERPRINT, Got: $CURRENT_FP"
    exit 1
  fi
  echo "Verified signature match for $APK"
done

# Audit AAB
AAB_FP=$(extract_aab_fingerprint "dist/Kalinal-AppBundle-AllDevices.aab") || exit 1
if [ "$AAB_FP" != "$MASTER_FINGERPRINT" ]; then
  echo "::error::AAB Certificate fingerprint mismatch! Expected: $MASTER_FINGERPRINT, Got: $AAB_FP"
  exit 1
fi
echo "Verified AAB certificate fingerprint matches master release key ($AAB_FP)."
```

---

## 4. Verification Plan

1. Verify that `extract_apk_fingerprint` correctly parses simulated and real `apksigner` outputs in local bash tests.
2. Run `compile_applet` to confirm overall project integrity.
3. Review the Git diff in `.github/workflows/manual-build.yml`.
