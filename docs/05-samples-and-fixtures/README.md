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

این ساختار باعث می‌شود آزمون‌ها با نهایت سرعت روی JVM محلی و در سرورهای GitHub Actions بدون وابستگی به شبکه بیرونی اجرا شوند.
