# 📦 نمونه‌های ترافیک شبکه دانشگاه (Network Samples & Fixtures)

این پوشه شامل فایل‌های ضبط‌شده ترافیک زنده و پاسخ‌های واقعی سرور سامانه تغذیه دانشگاه علوم پزشکی بیرجند ([https://jeton.bums.ac.ir](https://jeton.bums.ac.ir)) است. این فایل‌ها جهت تست‌های واحد و تحلیل پروتکل وب‌فرم بدون نیاز به ارتباط با اینترنت مورد استفاده قرار می‌گیرند.

---

## 📑 لیست فایل‌های نمونه و شرح محتوا

### ۱. `har_entry0_resp.txt` (پاسخ جابه‌جایی هفته - Week Navigation Response)
- **عملیات شبیه‌سازی‌شده:** کلیک کاربر روی دکمه «هفته بعد» (`imgbtnNextWeek`).
- **فرمت خروجی:** پیام دلتای ASP.NET AJAX حاوی `UpdatePanel1`، فیلد `__VIEWSTATE` به‌روزشده، مقدار اعتبار ریالی دانشجو (`590,000 ریال`) و جدول منوی هفتگی ۷ روزه.
- **تست مرتبط:** متد `testParseHarEntry0_NextWeekNavigation` در کلاس `ReservationLiveHarUnitTest.kt`.

### ۲. `har_entry1_resp.txt` (پاسخ ثبت موفق رزرو غذا - Meal Reservation Success)
- **عملیات شبیه‌سازی‌شده:** ارسال فرم ذخیره رزرو وعده ناهار روز چهارشنبه با انتخاب غذای چلو جوجه کباب و سلف مرکزی.
- **فرمت خروجی:** بازگشت فریم دلتا با توکن‌های جدید ViewState و پیام توست موفقیت‌آمیز سرور سلف.
- **تست مرتبط:** متد `testParseHarEntry1_MealReservationSuccess` در کلاس `ReservationLiveHarUnitTest.kt`.

### ۳. `har_entry2_resp.txt` (پاسخ لغو موفق رزرو غذا - Meal Cancellation Success)
- **عملیات شبیه‌سازی‌شده:** ارسال فرم لغو رزرو غذا در یک روز رزروشده.
- **فرمت خروجی:** فریم دلتا با بازگشت اعتبار ریالی به حساب دانشجو و تغییر وضعیت روز به لغو‌شده.
- **تست مرتبط:** متد `testParseHarEntry2_MealCancellationSuccess` در کلاس `ReservationLiveHarUnitTest.kt`.

### ۴. `MyProfile_page_sanitized.html` (HTML صفحه‌ی «پروفایل من» بدون اطلاعات شخصی)
- **عملیات شبیه‌سازی‌شده:** `GET /MyCullinan/MyProfile.aspx` پس از ورود موفق به سامانه.
- **محتوا و ساختار:** HTML واقعی پاسخ سرور با همان ساختار DOM، جدول‌های دو ستونی `fieldset` و شناسه‌های ثابت (`cphMain_lblName`، `cphMain_lblIdNumber`، `cphMain_lblNationalCode`، نوار کناری `#divUsername`، `#lblIdNumber`، `#lblCredit` با نمونه `123,000- ريال ` و مقدار خالی `...`)؛ تمامی مقادیر شخصی با داده‌های ساختگی استاندارد و توکن `__VIEWSTATE` با `VIEWSTATE_PLACEHOLDER` جایگزین شده‌اند.
- **کاربرد در تست:** تست واحد `ProfileParser` و `PageClassifier.TYPE_PROFILE` (استخراج ۲۱ فیلد پروفایل و ۵ فیلد نوار کناری، نگاشت `...` ← `null`، علامت `-` پسوندی اعتبار و نرمال‌سازی کاراکتر عربی `ي`). برای جزئیات کامل سند [مهندسی معکوس پروفایل من](../02-reverse-engineering/my-profile-and-user-image.md) را ببینید.

### ۵. `MyProfile_flow_sanitized.txt` (جریان کامل درخواست‌ها و پاسخ‌های پروفایل من و خروج)
- **عملیات شبیه‌سازی‌شده:** ضبط TLS-decrypted مرورگر Chrome (PCAPdroid) از توالی ورود، دریافت `Default.aspx`، `UserImage.ashx`، `GET /MyCullinan/MyProfile.aspx` و `POST /MyCullinan/MyProfile.aspx` برای خروج (`ctl00$logout`).
- **محتوای پاک‌سازی‌شده:** هدرها، کدهای وضعیت، اندازه‌های پاسخ (`25802` بایت HTML و `5660` بایت `image/jpg`) و فیلدهای بدنه‌ی ۵تایی خروج، بدون هرگونه کوکی، کلید TLS یا ViewState واقعی.
- **کاربرد در تست:** پیاده‌سازی سناریوهای `MockWebServer` برای `ProfileRepository`، `UserImage.ashx` و خروج با بدنه‌ی کوچک.

---

## 🔬 نحوه استفاده در کدهای تست پروژه

کلاس `ReservationLiveHarUnitTest.kt` فایل‌های این پوشه را به عنوان اولویت اول بارگذاری می‌کند:

```kotlin
val candidates = listOf(
    File("docs/05-samples-and-fixtures/har_entry${index}_resp.txt"),
    File("../docs/05-samples-and-fixtures/har_entry${index}_resp.txt"),
    File("app/src/test/resources/har_entry${index}_resp.txt")
)
```

> 🔒 **حریم خصوصی فیکسچرها:** تمامی فایل‌های موجود در این پوشه پیش از ثبت در مخزن به‌طور کامل پاک‌سازی (Sanitize) شده‌اند و **فاقد هرگونه نام واقعی، کد ملی، شماره دانشجویی، تلفن، ایمیل، تصویر چهره یا کوکی نشست** هستند. هنگام افزودن هر فیکسچر جدید، از پاک‌سازی کامل مقادیر شخصی و `VIEWSTATE` اطمینان حاصل کنید.

این ساختار باعث می‌شود آزمون‌ها با نهایت سرعت روی JVM محلی و در سرورهای GitHub Actions بدون وابستگی به شبکه بیرونی اجرا شوند.

