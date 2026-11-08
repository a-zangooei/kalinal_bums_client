# 🔑 امضای دیجیتال، نسخه‌گذاری پویا و سازگاری آپدیت (Signing & Versioning)

یکی از مهم‌ترین نیازمندی‌های یک اپلیکیشن تولیدی (Production-Ready)، تضمین قابلیت نصب آپدیت‌های بعدی بدون نیاز به حذف نسخه پیشین (Seamless App Update) است. این مستند به پیکربندی گواهی‌های امضا و نحوه نسخه‌گذاری می‌پردازد.

---

## ۱. سازگاری آپدیت در سیستم‌عامل اندروید

سیستم‌عامل اندروید هنگام نصب بسته به‌روزرسانی (Update)، دو پیش‌شرط قطعی را بررسی می‌کند:
1. **یکسان بودن اثرانگشت کلید امضا (Signing Certificate SHA-256):** اگر کلید امضا حتی یک بیت تغییر کند، اندروید خطای `INSTALL_FAILED_UPDATE_INCOMPATIBLE` صادر کرده و کاربر مجبور به حذف برنامه و از دست رفتن داده‌های خود می‌شود.
2. **بزرگ‌تر بودن کد نسخه (`versionCode`):** اگر کد نسخه مساوی یا کمتر باشد، اندروید خطای `INSTALL_FAILED_VERSION_DOWNGRADE` می‌دهد.

---

## ۲. راهکار نسخه‌گذاری پویا در پروژه (`app/build.gradle.kts`)

مقدار `versionCode` به صورت هوشمند از پارامترهای بیلد یا گیت دریافت می‌شود:

```kotlin
val buildVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull()
    ?: System.getenv("VERSION_CODE")?.toIntOrNull()
    ?: 1

val buildVersionName = project.findProperty("versionName")?.toString()
    ?: System.getenv("VERSION_NAME")
    ?: "1.0"
```

در خط لوله CI/CD:
```bash
VERSION_CODE=$(git rev-list --count HEAD 2>/dev/null || echo 1)
```
با این فرمول، با هر بار کامیت جدید در گیت، تعداد کل کامیت‌ها یک واحد افزایش یافته و در نتیجه `versionCode` همواره به صورت صعودی و یکتا رشد می‌کند.

---

## ۳. مدیریت کلیدهای امضا (Signing Configurations)

### ۱. کلید محیط توسعه و دیباگ (Debug Keystore)
- فایل `debug.keystore.base64` در ریشه مخزن کامیت شده است.
- در تمامی رانرهای CI، این فایل ثابت دیکود می‌شود تا تمام APK‌های دیباگی که برای تست یا بیلد روزمره تولید می‌شوند دارای اثرانگشت یکسان باشند:
  ```text
  SHA-256: B3:A6:68:16:3C:D3:0A:EE:84:00:60:F7:49:CA:9B:B6:7F:0E:43:B5:57:2B:AC:88:24:EA:5F:EF:66:DD:DE:EC
  ```
- در نتیجه، برنامه‌نویسان و تسترها می‌توانند بیلد کامیت جدید را مستقیماً روی بیلد کامیت قبلی نصب نمایند.

### ۲. کلید رسمی انتشار (Release Keystore)
- کلید اصلی انتشار با اعتبار ۲۵ ساله روی سیستم شخصی توسعه‌دهنده ایجاد شده و محتوای Base64 آن در بخش Secrets مخزن قرار می‌گیرد:
  - `RELEASE_KEYSTORE_BASE64`
  - `RELEASE_STORE_PASSWORD`
  - `RELEASE_KEY_ALIAS` (مقدار پیش‌فرض: `upload`)
  - `RELEASE_KEY_PASSWORD`
- در `app/build.gradle.kts`، نام مستعار کلید مستقیماً از متغیر محیطی خوانده می‌شود:
  ```kotlin
  keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
  ```
- بدین ترتیب هویت انتشار پایدار مانده و نسخه‌های رسمی همواره قابلیت ارتقای مستقیم روی گوشی کاربران را دارند.
