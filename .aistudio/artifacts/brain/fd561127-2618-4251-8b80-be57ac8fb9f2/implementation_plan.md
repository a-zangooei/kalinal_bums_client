# In-App Update Checker, Keystore Decoupling & Docs-to-Wiki Pipeline

Comprehensive architecture and implementation plan for adding dynamic in-app update notifications via the GitHub Releases API, removing hardcoded keystore aliases, enforcing strict `v*` tag conventions, and automatically synchronizing repository documentation to GitHub Wiki.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed through interactive clarification:
> - **In-App Update Check Trigger:** Both automatic check on app launch (silent background query with non-blocking dialog if update is available) AND manual "بررسی به‌روزرسانی" button in the TopAppBar / About dialog.
> - **Customizable Release Notes:** In-app updater parses a dedicated markdown section header (e.g. `### 📝 تغییرات این نسخه` or `### 📢 پیام این نسخه`) from the GitHub Release body. You can edit this text directly in GitHub Web UI after release, and the app will instantly reflect your edits!
> - **Keystore Alias Secret:** `RELEASE_KEY_ALIAS` is read strictly from GitHub Secrets and environment variables (`KEY_ALIAS`), removing the hardcoded fallback from `app/build.gradle.kts`.
> - **Release Trigger Convention:** Strict `v*` prefix (e.g., `v1.0.0`) enforced across `.github/workflows/release.yml` and ignored in `.github/workflows/ci.yml`.
> - **Documentation Sync:** Dedicated GitHub Actions workflow (`.github/workflows/wiki-sync.yml`) automatically synchronizes the `/docs` directory to GitHub Wiki whenever documentation is updated on `main`.

---

### 1. Overview & Core Concept

1. **In-App Update System:**
   - Queries GitHub's public REST endpoint: `https://api.github.com/repos/a-zangooei/kalinal_bums_client/releases/latest`.
   - Compares the installed version (from `BuildConfig.VERSION_NAME` and `versionCode`) against the latest release tag.
   - Extracts the direct `.apk` asset download URL (`browser_download_url`) and the Persian release notes snippet.
   - Presents a clean Material 3 dialog with the version badge, customizable changelog, "دانلود و نصب مستقیم" button (launches Android browser or system download manager), and a "بعداً" dismiss button.
2. **Keystore Alias Decoupling:**
   - Modifies `app/build.gradle.kts` to read `keyAlias = System.getenv("KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS") ?: ""` and fails gracefully if not provided during release builds.
   - Enforces `RELEASE_KEY_ALIAS` validation in `release.yml`.
3. **Docs-to-Wiki Synchronization:**
   - Adds a lightweight workflow that clones `https://github.com/a-zangooei/kalinal_bums_client.wiki.git` using `GITHUB_TOKEN` with write permissions, copies markdown files from `/docs` (and flattens or creates home page navigation), and pushes changes automatically.

---

### 2. User Experience & Visual Design

```
┌────────────────────────────────────────────────────────┐
│ 🔔 نسخه جدید کالینان در دسترس است!                   │
│                                                        │
│ نسخه فعلی: ۱.۰.۰   ◄──►   نسخه جدید: ۱.۰.۱ 🏷️          │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │ 📝 تغییرات این نسخه (قابل ویرایش در گیت‌هاب):        │ │
│ │ • فعال‌سازی رزرو صبحانه و شام                        │ │
│ │ • حذف روزهای تعطیل پنجشنبه و جمعه                  │ │
│ │ • ارتقای امنیت و رفع انقضای سشن                     │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│  [ بعداً ]              [ 📥 دانلود مستقیم فایل نصبی ] │
└────────────────────────────────────────────────────────┘
```

- **Update Dialog (Compose):**
  - Appears smoothly with entrance animation if an update is found on launch.
  - Can also be summoned anytime by tapping **«درباره برنامه و به‌روزرسانی»** in the TopBar menu.
  - Displays formatted changelog text, file size (if provided in release metadata), and single-tap download action.

---

### 3. Technical Architecture & Data Strategy

```
┌─────────────────────────┐          ┌─────────────────────────┐
│     MainActivity.kt     │          │    MenuScreen / TopBar  │
│  (Launch Update Check)  │          │    (Manual Check Tap)   │
└────────────┬────────────┘          └────────────┬────────────┘
             │                                    │
             ▼                                    ▼
┌──────────────────────────────────────────────────────────────┐
│                   UpdateViewModel / Repository               │
│  • Calls https://api.github.com/repos/.../releases/latest    │
│  • Compares semantic version (v1.0.1 > v1.0.0)               │
│  • Extracts APK asset URL & "### 📝 تغییرات" section         │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                    UpdateDialog.kt (Compose)                 │
│  • Shows release notes, version tag, and direct APK download │
└──────────────────────────────────────────────────────────────┘
```

#### GitHub Release Template Specification:
In `.github/workflows/release.yml`, the generated release body will always contain:
```markdown
### 📝 تغییرات این نسخه
• به‌روزرسانی‌های امنیتی و رفع اشکالات سامانه تغذیه

---
### 📲 دانلود مستقیم فایل نصبی
...
```
You can edit the lines under `### 📝 تغییرات این نسخه` at any time directly in the GitHub Release editor, and the app will parse and show your updated text to users!

---

### 4. Implementation Steps (Execution Plan)

1. **Keystore Secrets & Release Hardening:**
   - Update `app/build.gradle.kts`:
     ```kotlin
     keyAlias = System.getenv("KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS") ?: ""
     ```
   - Update `.github/workflows/release.yml`:
     - Validate `RELEASE_KEY_ALIAS` secret explicitly.
     - Set `KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}`.
     - Restrict trigger to `push: tags: ['v*']`.
   - Update `.github/workflows/ci.yml`:
     - Restrict `tags-ignore: ['v*']`.

2. **In-App Update Engine:**
   - Create `com.example.data.model.AppUpdateInfo`:
     - `tagName: String`
     - `versionName: String`
     - `changelog: String`
     - `downloadUrl: String`
     - `publishedAt: String`
     - `isUpdateAvailable: Boolean`
   - Create `com.example.data.repository.UpdateRepository`:
     - Queries GitHub Releases API using OkHttp.
     - Parses JSON response with regex/JSON parser.
     - Extracts custom changelog snippet after `### 📝 تغییرات این نسخه`.
     - Compares versions cleanly.
   - Create `com.example.ui.components.UpdateDialog`:
     - Material 3 Dialog with Persian typography, changelog container, and download action.
   - Wire update check into `MainActivity` (launch check) and TopBar menu (manual check).

3. **Automated Docs-to-Wiki Workflow (`.github/workflows/wiki-sync.yml`):**
   - Triggers on push to `main` when files in `docs/**` or `docs/README.md` change.
   - Clones repository wiki: `https://x-access-token:${{ secrets.GITHUB_TOKEN }}@github.com/${{ github.repository }}.wiki.git`.
   - Syncs docs markdown files to root wiki pages with clean index.
   - Commits and pushes automatically.

4. **Verification & Testing:**
   - Add unit tests for `UpdateRepository` version comparison and changelog section parser in `app/src/test/java/com/example/`.
   - Run `gradle :app:testDebugUnitTest`.
   - Run `compile_applet` to verify compilation.
