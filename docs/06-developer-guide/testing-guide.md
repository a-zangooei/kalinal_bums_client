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
   - تست وضعیت سوئیچ حالت موقت `fallbackClient` در کلاینت شبکه (`testHttpClientCompatibilityModeToggleAndFallbackClient`).
2. **`ReservationLiveHarUnitTest.kt`:**
   - تست پارس داده‌های واقعی ضبط‌شده در ترافیک زنده سرور دانشگاه (فایل‌های `docs/05-samples-and-fixtures/`).
   - اعتبارسنجی استخراج درست قیمت‌ها، روزها، گزینه‌های انتخابی غذا و نیم‌پرس‌ها.
3. **`UnreadMessagesUnitTest.kt`:**
   - تست طبقه‌بندی صفحه پیام‌های خوانده‌نشده و خوانده‌شده از روی ضبط واقعی ترافیک.
   - تست کشف کنترل‌های صفحه‌بندی پیام‌ها (`PagerStyle`) و فیلتر ردیف‌های واقعی دارای دکمه پیام.
   - تست استخراج متن پیام از تگ `cphMain_pMessage`، زنجیره ViewState و ریدایرکت‌های ۳۰۲ ورود.
4. **`MessageRetrievalUnitTest.kt`:**
   - تست پارامترهای دقیق `ScriptManager`، مختصات `.x` و `.y` دکمه تصویری پیام، زنجیره‌سازی ترتیبی `__VIEWSTATE` و `__EVENTVALIDATION` و مدیریت خطاهای پاسخ دلتا.
5. **`WeekNavigationUnitTest.kt`:**
   - تست ساخت فرم‌بادی ناوبری هفته (`imgbtnNextWeek`، `imgbtnLastWeek`، `imgbtnCurrentWeek`) همراه با حفظ ردیف‌های گرید و فیلدهای `__EVENTVALIDATION`.
   - تست زنجیره‌سازی متوالی حالت بین هفته‌ها، استخراج `#cphMain_alertBoxValue` و نمایش خطای توکن `error` در پاسخ دلتا.
6. **`LoggingHardeningUnitTest.kt` و `AppLoggerSanitizerUnitTest.kt`:**
   - تست پالایش خودکار گذرواژه‌ها، کوکی‌ها و داده‌های حساس در `AppLogger` و `LogSanitizer`.
7. **`UpdateRepositoryUnitTest.kt` و `KalinanUnitTests.kt`:**
   - تست منطق بررسی نسخه جدید، مدل‌های دامنه و متدهای کمکی رزرو.

---

## ۳. قواعد و استانداردهای الزامی برای افزودن آزمون‌های جدید

- ⚠️ **قانون اکید عدم اتصال به سرور واقعی:** هیچ تستی نباید به صورت خودکار به آدرس واقعی `https://jeton.bums.ac.ir` درخواست ارسال کند یا غذای واقعی رزرو/لغو کند.
- **استفاده از MockWebServer:** هرگونه تست ارتباط شبکه باید با شبیه‌ساز سرور محلی `okhttp3.mockwebserver.MockWebServer` پیاده‌سازی شود.
- **استفاده از Fixtures:** برای تست تغییر فرمت‌های ASP.NET، نمونه پاسخ‌ها را در پوشه `docs/05-samples-and-fixtures/` قرار داده و از آنجا لود نمایید.
- **تست در JVM محلی:** از نوشتن تست‌های وابسته به امولاتور فیزیکی (Instrumented Tests) که بیلد CI را کند می‌کنند پرهیز کنید؛ تمامی تست‌ها باید سریع و بر روی JVM محلی اجرا شوند.
