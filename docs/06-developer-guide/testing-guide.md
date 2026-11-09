# 🧪 راهنمای جامع آزمون‌های نرم‌افزار (Testing & Quality Assurance)

پروژه کالینان دارای مجموعه کاملی از آزمون‌های واحد و شبیه‌سازی‌شده برای تضمین پایداری کلاینت در ارتباط با سرور دانشگاه است. این سند نحوه اجرای آزمون‌ها و استانداردهای افزودن تست‌های جدید را شرح می‌دهد.

---

## ۱. نحوه اجرای آزمون‌ها با خط فرمان

برای اجرای کلیه آزمون‌های واحد پروژه:

```bash
# در لینوکس / مک‌اواس
./gradlew testDebugUnitTest

# در ویندوز (PowerShell / CMD)
.\gradlew.bat testDebugUnitTest
```

گزارش بصری HTML آزمون‌ها پس از اجرا در مسیر زیر در دسترس است:  
`app/build/reports/tests/testDebugUnitTest/index.html`

---

## ۲. کلاس‌های آزمون موجود و حیطه وظایف هر کدام

1. **`MockWebServerSecurityUnitTest.kt`:**
   - تست پاکسازی نشست کوکی‌ها در هنگام خروج (`testSessionCookieClearanceOnLogout`).
   - تست شناسایی انقضای نشست کاری با ریدایرکت سرور (`testDeltaParserDetectsSessionExpirationRedirect`).
   - تست مدیریت خطاهای ۵۰۰ سرور دانشگاه (`testMockWebServerHandles500InternalServerErrorGracefully`).
   - تست استریم باینری و دریافت تصویر کپچا (`testMockWebServerCaptchaFetchAndBinaryStream`).
   - تست سوئیچ خودکار به کلاینت سازگار SSL (`testHttpClientCompatibilityModeToggleAndFallbackClient`).
2. **`ReservationLiveHarUnitTest.kt`:**
   - تست پارس داده‌های واقعی ضبط‌شده در ترافیک زنده سرور دانشگاه (فایل‌های `docs/05-samples-and-fixtures/`).
   - اعتبارسنجی استخراج درست قیمت‌ها، روزها، گزینه‌های انتخابی غذا و نیم‌پرس‌ها.
3. **`KalinanUnitTests.kt`:**
   - تست‌های تکمیلی منطق رزرو، اعتبارسنجی فرم‌ها و متدهای تبدیل مدل داده.

---

## ۳. قواعد و استانداردهای الزامی برای افزودن آزمون‌های جدید

- ⚠️ **قانون اکید عدم اتصال به سرور واقعی:** هیچ تستی نباید به صورت خودکار به آدرس واقعی `https://jeton.bums.ac.ir` درخواست ارسال کند یا غذای واقعی رزرو/لغو کند.
- **استفاده از MockWebServer:** هرگونه تست ارتباط شبکه باید با شبیه‌ساز سرور محلی `okhttp3.mockwebserver.MockWebServer` پیاده‌سازی شود.
- **استفاده از Fixtures:** برای تست تغییر فرمت‌های ASP.NET، نمونه پاسخ‌ها را در پوشه `docs/05-samples-and-fixtures/` قرار داده و از آنجا لود نمایید.
- **تست در JVM محلی:** از نوشتن تست‌های وابسته به امولاتور فیزیکی (Instrumented Tests) که بیلد CI را کند می‌کنند پرهیز کنید؛ تمامی تست‌ها باید سریع و بر روی JVM محلی اجرا شوند.
